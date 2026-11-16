import type { QuestionDetail } from "@/features/questions/types";
import type { QuestionType } from "@/features/content/types";
import {
  allocateRequirement,
  availabilityLookup,
  cellKey,
  groupCounts,
  largestRemainder,
  pathSections,
  resolveBlockRequirement,
  resolveMatrixRequirement,
} from "./check";
import type {
  BlockRequirement,
  ExamAvailability,
  ExamPathRef,
  PreviewAllocationCell,
  PreviewBlock,
  PreviewCheck,
  PreviewItem,
  PreviewKnowledgeStat,
  PreviewOption,
  PreviewPaper,
  PreviewPart,
  PreviewQuestion,
} from "./review-types";
import { blockPointsEach, countOf, isSetBlock, sum } from "./matrix";
import type { ExamPart, ExamSection, QuestionBlock } from "./types";
import { effectiveScope } from "./mock/source-count";

/**
 * Bộ sinh đề xem trước (mô phỏng cơ chế sinh đề thật, UniNote PBI-04 mục 10, 12): cùng quy tắc với Kiểm tra cấu
 * hình (chỉ tiêu nguyên, phân bổ thỏa hai chiều), lấy nguyên bộ, không lặp câu trong đề. Hàm thuần; `seed` quyết định
 * câu được chọn và thứ tự, cùng `seed` cho cùng đề.
 *
 * Kho mẫu chỉ có vài chục câu, nên phần thiếu được điền bằng "câu mô phỏng" (`simulated`) của phần ngân hàng không nạp
 * vào dữ liệu mẫu; câu thật được ưu tiên.
 */

export interface KnowledgeInfo {
  nodeId: string;
  code: string;
  name: string;
}

export interface GeneratePaperInput {
  parts: readonly ExamPart[];
  path: ExamPathRef;
  availability: ExamAvailability;
  seed: number;
  /** Câu Sẵn sàng khớp nhóm (môn, dạng trả lời, nhãn, phạm vi MAPID có hiệu lực). */
  candidates: (block: QuestionBlock, section: ExamSection) => readonly QuestionDetail[];
  /** Mã lưới Lớp và Độ khó theo thứ tự danh mục. */
  grades: readonly string[];
  difficulties: readonly string[];
  /** Đường có `SUBJECT_CHOICE`: các môn muốn xem, đúng `chooseSubjects` môn của phương án. */
  subjectCodes?: readonly string[];
  knowledgeNode: (nodeId: string) => KnowledgeInfo | null;
  knowledgeChapter: (nodeId: string) => (KnowledgeInfo & { subjectCode: string }) | null;
  /** Thời lượng chung của bài (`timing = SHARED`); bỏ trống: cộng thời lượng các phần. */
  sharedDurationMinutes?: number | null;
}

/** Số ngẫu nhiên có hạt giống (mulberry32), để "Sinh lại" đổi đề còn cùng hạt giống thì cùng đề. */
export function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffle<T>(items: readonly T[], random: () => number): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [result[i], result[j]] = [result[j]!, result[i]!];
  }
  return result;
}

function optionsOf(question: QuestionDetail): PreviewOption[] | null {
  const body = question.body;
  if (body.type === "SINGLE_CHOICE" || body.type === "MULTIPLE_CHOICE") {
    return body.options.map((option) => ({ id: option.id, content: option.content, correct: option.correct }));
  }
  if (body.type === "TRUE_FALSE") {
    return body.statements.map((item) => ({ id: item.id, content: item.content, correct: item.answer }));
  }
  return null;
}

function rubricOf(question: QuestionDetail): PreviewQuestion["rubric"] {
  if (question.body.type !== "ESSAY") return null;
  return question.body.rubric.map((item) => ({ id: item.id, description: item.description, points: item.points }));
}

function answerOf(question: QuestionDetail): string | null {
  const { body } = question;
  if (body.type === "SHORT_ANSWER") return body.answers.join(" / ") || null;
  if (body.type === "CLOZE") return body.blanks.map((blank) => blank.answers.join(" / ")).join(" · ") || null;
  return null;
}

function roundPoints(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

interface Slot {
  gradeCode: string;
  difficultyCode: string;
}

/** Dạng trả lời còn chỉ tiêu nhiều nhất của môn, cho câu mô phỏng. */
function nextQuotaType(quotas: ReadonlyMap<QuestionType, number>): QuestionType | null {
  let best: QuestionType | null = null;
  for (const [type, left] of quotas) if (left > 0 && (best === null || left > (quotas.get(best) ?? 0))) best = type;
  return best;
}

export function generatePaper(input: GeneratePaperInput): PreviewPaper {
  const { parts, path, availability, candidates, knowledgeNode, knowledgeChapter, grades, difficulties } = input;
  const random = seededRandom(input.seed);
  const used = new Set<string>();
  let number = 1;
  let simulatedSequence = 0;
  let totalPoints = 0;
  let duplicates = 0;
  let hasSet = false;
  let hasEnglish = false;
  const chapterCounts = new Map<string, PreviewKnowledgeStat>();

  const take = (id: string) => {
    if (used.has(id)) duplicates += 1;
    used.add(id);
  };

  const countKnowledge = (nodeId: string | null) => {
    const chapter = nodeId ? knowledgeChapter(nodeId) : null;
    if (!chapter) return;
    const current = chapterCounts.get(chapter.nodeId);
    chapterCounts.set(chapter.nodeId, { ...chapter, count: (current?.count ?? 0) + 1 });
  };

  const fromStore = (question: QuestionDetail, block: QuestionBlock): PreviewQuestion => {
    take(question.id);
    const primary = question.knowledge.find((ref) => ref.primary) ?? question.knowledge[0] ?? null;
    countKnowledge(primary?.nodeId ?? null);
    const points = block.requiredPointsPerQuestion ?? question.points;
    totalPoints += points;
    return {
      number: number++,
      questionId: question.id,
      code: question.code,
      version: question.version,
      type: question.type,
      simulated: false,
      content: question.content,
      gradeCode: question.gradeCode,
      difficultyCode: question.difficultyCode,
      knowledge: primary ? { nodeId: primary.nodeId, code: primary.code, name: primary.name } : null,
      points,
      options: optionsOf(question),
      answer: answerOf(question),
      solution: question.solution,
      rubric: rubricOf(question),
      body: structuredClone(question.body),
    };
  };

  const simulated = (slot: Slot, block: QuestionBlock, section: ExamSection, type: PreviewQuestion["type"]): PreviewQuestion => {
    simulatedSequence += 1;
    const id = `sim-${simulatedSequence}`;
    take(id);
    const scopeIds = effectiveScope(block, section)?.nodeIds ?? [];
    const nodeId = scopeIds.length ? scopeIds[simulatedSequence % scopeIds.length]! : null;
    countKnowledge(nodeId);
    totalPoints += blockPointsEach(block);
    return {
      number: number++,
      questionId: id,
      code: `SIM-${String(simulatedSequence).padStart(5, "0")}`,
      version: null,
      type,
      simulated: true,
      content: null,
      gradeCode: slot.gradeCode,
      difficultyCode: slot.difficultyCode,
      knowledge: nodeId ? knowledgeNode(nodeId) : null,
      points: blockPointsEach(block),
      options: null,
      answer: null,
      solution: null,
      rubric: null,
      body: null,
    };
  };

  /** Chỉ tiêu dạng trả lời còn lại của môn (D22): chùm trừ theo từng câu con. Rỗng khi môn không khai. */
  const quotasOf = (section: ExamSection, count: number): Map<QuestionType, number> => {
    const quotas = new Map<QuestionType, number>();
    if (!section.answerQuotas) return quotas;
    const { items, unit } = section.answerQuotas;
    const counts = unit === "PERCENT" ? largestRemainder(count, items.map((item) => item.value)) : items.map((item) => item.value);
    items.forEach((item, index) => quotas.set(item.answerType, counts[index] ?? 0));
    return quotas;
  };

  /** Lấy `need` câu từ phân bổ còn lại của môn (chỉ tiêu lớp, độ khó cấp môn trải trên mọi nhóm). */
  const takeFromSection = (remaining: Map<string, number>, need: number): Map<string, number> => {
    const taken = new Map<string, number>();
    let left = need;
    for (const [key, value] of remaining) {
      if (left === 0) break;
      const take = Math.min(value, left);
      if (take > 0) {
        taken.set(key, take);
        remaining.set(key, value - take);
        left -= take;
      }
    }
    return taken;
  };

  const buildBlock = (
    block: QuestionBlock,
    section: ExamSection,
    count: number,
    sectionRemaining: Map<string, number> | null,
    quotas: Map<QuestionType, number>,
  ): PreviewBlock => {
    const subjectCode = section.subjectCode;
    const capacity = availabilityLookup(availability.blocks[block.id]);
    let allocation: Map<string, number> | null;
    if (block.matrix) {
      const { requirement: own } = resolveBlockRequirement(block, count);
      if (!own) throw new Error(`Block ${block.id} has no valid matrix`);
      allocation = allocateRequirement(own, capacity);
    } else if (sectionRemaining) {
      allocation = takeFromSection(sectionRemaining, count);
    } else {
      const free = new Map<string, number>();
      for (const grade of grades) {
        for (const difficulty of difficulties) free.set(cellKey(grade, difficulty), capacity(grade, difficulty));
      }
      allocation = takeFromSection(free, count);
    }
    if (!allocation) throw new Error(`Block ${block.id} has no feasible allocation`);
    const requirement: BlockRequirement = block.matrix
      ? resolveBlockRequirement(block, count).requirement!
      : {
          mode: "COMBINED",
          cells: [...allocation].map(([key, required]) => {
            const [gradeCode, difficultyCode] = key.split(":") as [string, string];
            return { gradeCode, difficultyCode, required };
          }),
          byGrade: [],
          byDifficulty: [],
          byCompetency: [],
        };
    const firstNumber = number;
    const pool = shuffle(candidates(block, section), random).filter((question) => !used.has(question.id));
    const items: PreviewItem[] = [];
    const spendQuota = (type: QuestionType) => quotas.set(type, (quotas.get(type) ?? 0) - 1);

    if (isSetBlock(block)) {
      hasSet = true;
      const remaining = new Map(allocation);
      const setCount = block.setCount ?? 0;
      const setSize = block.setSize ?? 0;
      const sets: Array<QuestionDetail | null> = [];
      for (const set of pool) {
        if (sets.length >= setCount) break;
        if (set.children.length !== setSize) continue;
        const need = new Map<string, number>();
        for (const child of set.children) {
          const key = cellKey(child.gradeCode, child.difficultyCode);
          need.set(key, (need.get(key) ?? 0) + 1);
        }
        if ([...need].every(([key, amount]) => (remaining.get(key) ?? 0) >= amount)) {
          for (const [key, amount] of need) remaining.set(key, remaining.get(key)! - amount);
          sets.push(set);
        }
      }
      const slots: Slot[] = [...remaining].flatMap(([key, amount]) => {
        const [gradeCode, difficultyCode] = key.split(":") as [string, string];
        return Array.from({ length: amount }, () => ({ gradeCode, difficultyCode }));
      });
      while (sets.length < setCount) sets.push(null);
      for (const set of shuffle(sets, random)) {
        if (set) {
          take(set.id);
          for (const child of set.children) spendQuota(child.type);
          items.push({
            kind: "SET",
            setId: set.id,
            code: set.code,
            version: set.version,
            simulated: false,
            passage: set.body.type === "QUESTION_SET" ? set.body.passage : set.content,
            children: set.children.map((child) => fromStore(child, block)),
          });
        } else {
          simulatedSequence += 1;
          const setId = `sim-set-${simulatedSequence}`;
          take(setId);
          items.push({
            kind: "SET",
            setId,
            code: `SIM-${String(simulatedSequence).padStart(5, "0")}`,
            version: null,
            simulated: true,
            passage: null,
            children: slots.splice(0, setSize).map((slot) => {
              const type = block.childAnswerType ?? nextQuotaType(quotas) ?? "SINGLE_CHOICE";
              spendQuota(type);
              return simulated(slot, block, section, type);
            }),
          });
        }
      }
    } else {
      const chosen: Array<{ slot: Slot; question: QuestionDetail | null }> = [];
      const taken = new Set<string>();
      for (const [key, amount] of allocation) {
        if (amount === 0) continue;
        const [gradeCode, difficultyCode] = key.split(":") as [string, string];
        for (let i = 0; i < amount; i += 1) {
          const matches = pool.filter(
            (question) =>
              !taken.has(question.id) && question.gradeCode === gradeCode && question.difficultyCode === difficultyCode,
          );
          // Chỉ tiêu dạng trả lời của môn: ưu tiên câu có dạng còn chỉ tiêu.
          const match = quotas.size ? (matches.find((question) => (quotas.get(question.type) ?? 0) > 0) ?? matches[0]) : matches[0];
          if (match) taken.add(match.id);
          chosen.push({ slot: { gradeCode, difficultyCode }, question: match ?? null });
        }
      }
      for (const entry of shuffle(chosen, random)) {
        const type = entry.question?.type ?? block.questionType ?? nextQuotaType(quotas) ?? "SINGLE_CHOICE";
        spendQuota(type);
        const question = entry.question ? fromStore(entry.question, block) : simulated(entry.slot, block, section, type);
        items.push({ kind: "QUESTION", question });
      }
    }

    const generated = new Map<string, number>();
    const questions = items.flatMap((item) => (item.kind === "SET" ? item.children : [item.question]));
    for (const question of questions) {
      const key = cellKey(question.gradeCode, question.difficultyCode);
      generated.set(key, (generated.get(key) ?? 0) + 1);
    }
    const cells: PreviewAllocationCell[] =
      requirement.mode === "COMBINED"
        ? requirement.cells.map((cell) => ({
            gradeCode: cell.gradeCode,
            difficultyCode: cell.difficultyCode,
            required: cell.required,
            generated: generated.get(cellKey(cell.gradeCode, cell.difficultyCode)) ?? 0,
          }))
        : requirement.byGrade.flatMap((grade) =>
            requirement.byDifficulty.map((difficulty) => ({
              gradeCode: grade.code,
              difficultyCode: difficulty.code,
              required: null,
              generated: generated.get(cellKey(grade.code, difficulty.code)) ?? 0,
            })),
          );

    return {
      blockId: block.id,
      blockName: block.name,
      subjectCode,
      questionType: block.questionType,
      manualGrading: block.questionType === "ESSAY",
      pointsEach: blockPointsEach(block),
      firstNumber,
      lastNumber: number - 1,
      questionCount: questions.length,
      setCount: block.setCount,
      setSize: block.setSize,
      requirement,
      allocation: cells,
      items,
    };
  };

  const buildSection = (section: ExamSection, count: number): PreviewBlock[] => {
    if (section.subjectCode === "ENGLISH") hasEnglish = true;
    const perGroup = groupCounts(section, count);
    let sectionRemaining: Map<string, number> | null = null;
    if (section.matrix) {
      const { requirement } = resolveMatrixRequirement(section.matrix, count);
      sectionRemaining = requirement ? allocateRequirement(requirement, availabilityLookup(availability.sections?.[section.id])) : null;
      if (!sectionRemaining) throw new Error(`Section ${section.id} has no feasible allocation`);
    }
    const quotas = quotasOf(section, count);
    return section.blocks.map((block, index) => buildBlock(block, section, perGroup[index]!, sectionRemaining, quotas));
  };

  /** Môn của một phần trên đường: môn cố định theo số câu của môn; `SUBJECT_CHOICE` chỉ các môn đã chọn, theo thứ tự cấu trúc (D25). */
  const sectionsToBuild = (part: ExamPart, optionId: string | null, sections: readonly ExamSection[]) => {
    const option = part.options.find((item) => item.id === optionId && item.kind === "SUBJECT_CHOICE");
    if (!option) return sections.map((section) => ({ section, count: section.questionCount ?? sum(section.blocks.map(countOf)) }));
    const chosen = option.sections.filter((section) => input.subjectCodes?.includes(section.subjectCode));
    return [
      ...part.sections.map((section) => ({ section, count: section.questionCount ?? sum(section.blocks.map(countOf)) })),
      ...chosen.map((section, index) => ({ section, count: option.countsByPick?.[index] ?? 0 })),
    ];
  };

  let totalMinutes = 0;
  const previewParts: PreviewPart[] = pathSections(parts, path).map(({ part, optionId, sections }) => {
    totalMinutes += part.durationMinutes ?? 0;
    const firstNumber = number;
    const pointsBefore = totalPoints;
    const blocks = sectionsToBuild(part, optionId, sections).flatMap(({ section, count }) => buildSection(section, count));
    return {
      partId: part.id,
      name: part.name,
      order: part.order,
      durationMinutes: part.durationMinutes,
      firstNumber,
      lastNumber: number - 1,
      points: roundPoints(totalPoints - pointsBefore),
      maxPoints: part.maxPoints ?? null,
      blocks,
    };
  });
  const sharedMinutes = input.sharedDurationMinutes ?? null;

  const checks: PreviewCheck[] = [
    { code: "NO_DUPLICATES", status: duplicates === 0 ? "PASS" : "FAIL", value: duplicates },
    { code: "SETS_INTACT", status: hasSet ? "PASS" : "NOT_APPLICABLE", value: null },
    { code: "FIXED_BLOCK_ORDER", status: hasEnglish ? "PASS" : "NOT_APPLICABLE", value: null },
    { code: "TOTAL_POINTS", status: "PASS", value: roundPoints(totalPoints) },
  ];

  return {
    totalQuestions: number - 1,
    totalPoints: roundPoints(totalPoints),
    totalMinutes: sharedMinutes ?? totalMinutes,
    sharedDuration: sharedMinutes !== null,
    hasManualGrading: previewParts.some((part) => part.blocks.some((block) => block.manualGrading)),
    parts: previewParts,
    checks,
    knowledgeStats: [...chapterCounts.values()].sort((a, b) => b.count - a.count || a.code.localeCompare(b.code)),
  };
}
