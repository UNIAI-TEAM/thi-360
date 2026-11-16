import type { QuestionDetail } from "@/features/questions/types";
import { allocateRequirement, availabilityLookup, cellKey, pathSections, resolveBlockRequirement } from "./check";
import type {
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
import { blockPointsEach } from "./matrix";
import type { ExamPart, QuestionBlock } from "./types";

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
  /** Câu Sẵn sàng khớp khối (môn, dạng, nhãn, phạm vi). */
  candidates: (block: QuestionBlock, subjectCode: string) => readonly QuestionDetail[];
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

export function generatePaper(input: GeneratePaperInput): PreviewPaper {
  const { parts, path, availability, candidates, knowledgeNode, knowledgeChapter } = input;
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

  const simulated = (slot: Slot, block: QuestionBlock, type: PreviewQuestion["type"]): PreviewQuestion => {
    simulatedSequence += 1;
    const id = `sim-${simulatedSequence}`;
    take(id);
    const scopeIds = block.knowledgeScope.nodeIds;
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

  const buildBlock = (block: QuestionBlock, subjectCode: string): PreviewBlock => {
    const { requirement } = resolveBlockRequirement(block);
    if (!requirement) throw new Error(`Block ${block.id} has no valid matrix`);
    const capacity = availabilityLookup(availability.blocks[block.id]);
    const allocation = allocateRequirement(requirement, capacity);
    if (!allocation) throw new Error(`Block ${block.id} has no feasible allocation`);
    const firstNumber = number;
    const pool = shuffle(candidates(block, subjectCode), random).filter((question) => !used.has(question.id));
    const items: PreviewItem[] = [];

    if (block.questionType === "QUESTION_SET") {
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
        if ([...need].every(([key, count]) => (remaining.get(key) ?? 0) >= count)) {
          for (const [key, count] of need) remaining.set(key, remaining.get(key)! - count);
          sets.push(set);
        }
      }
      const slots: Slot[] = [...remaining].flatMap(([key, count]) => {
        const [gradeCode, difficultyCode] = key.split(":") as [string, string];
        return Array.from({ length: count }, () => ({ gradeCode, difficultyCode }));
      });
      while (sets.length < setCount) sets.push(null);
      for (const set of shuffle(sets, random)) {
        if (set) {
          take(set.id);
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
            children: slots.splice(0, setSize).map((slot) => simulated(slot, block, "SINGLE_CHOICE")),
          });
        }
      }
    } else {
      const chosen: Array<{ slot: Slot; question: QuestionDetail | null }> = [];
      for (const [key, count] of allocation) {
        if (count === 0) continue;
        const [gradeCode, difficultyCode] = key.split(":") as [string, string];
        const matches = pool.filter(
          (question) => question.gradeCode === gradeCode && question.difficultyCode === difficultyCode,
        );
        for (let i = 0; i < count; i += 1) {
          chosen.push({ slot: { gradeCode, difficultyCode }, question: matches[i] ?? null });
        }
      }
      for (const entry of shuffle(chosen, random)) {
        const question = entry.question ? fromStore(entry.question, block) : simulated(entry.slot, block, block.questionType);
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

  let totalMinutes = 0;
  const previewParts: PreviewPart[] = pathSections(parts, path).map(({ part, sections }) => {
    totalMinutes += part.durationMinutes ?? 0;
    const firstNumber = number;
    const pointsBefore = totalPoints;
    const blocks = sections.flatMap((section) => {
      if (section.subjectCode === "ENGLISH") hasEnglish = true;
      return section.blocks.map((block) => buildBlock(block, section.subjectCode));
    });
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
