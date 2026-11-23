import {
  emptyMatrix,
  type MatrixAxes,
  type SectionLocation,
  sectionGroups as storedSectionGroups,
  sectionsPoints,
  splitEvenly,
  type StructureIssue,
  type StructureTotals,
  type StructureTotalsOptions,
  structureTotals,
  sum,
  type ValidateStructureOptions,
  validateStructure,
} from "../matrix";
import type { ExamPart, ExamPartOption, ExamSection, QuestionBlock, StructureCombination } from "../types";

let draftSequence = 0;
export function draftId(prefix: string): string {
  draftSequence += 1;
  return `${prefix}-draft-${Date.now().toString(36)}-${draftSequence}`;
}

export const ENGLISH_CODE = "ENGLISH";

export type StructureSelection =
  | { kind: "exam" }
  | { kind: "part"; partId: string }
  | { kind: "option"; partId: string; optionId: string }
  | { kind: "section"; sectionId: string }
  | { kind: "block"; blockId: string };

export type DraftLocation = SectionLocation & { pool?: boolean };

export function sectionGroups(part: ExamPart): Array<{ optionId: string | null; sections: ExamSection[]; pool?: boolean }> {
  if (part.codeSource) return [{ optionId: null, sections: part.subjectPool ?? [], pool: true }];
  return storedSectionGroups(part);
}

export interface SectionContext {
  part: ExamPart;
  option: ExamPartOption | null;
  section: ExamSection;
  location: DraftLocation;
}

export function findSection(parts: readonly ExamPart[], sectionId: string): SectionContext | null {
  for (const part of parts) {
    for (const group of sectionGroups(part)) {
      const section = group.sections.find((item) => item.id === sectionId);
      if (section) {
        return {
          part,
          option: part.options.find((option) => option.id === group.optionId) ?? null,
          section,
          location: { partId: part.id, optionId: group.optionId, sectionId, pool: group.pool },
        };
      }
    }
  }
  return null;
}

export interface BlockContext {
  part: ExamPart;
  option: ExamPartOption | null;
  section: ExamSection;
  block: QuestionBlock;
  location: DraftLocation;
  /** Vị trí của khối trong môn, tính từ 1. */
  position: number;
}

export function findBlock(parts: readonly ExamPart[], blockId: string): BlockContext | null {
  for (const part of parts) {
    for (const group of sectionGroups(part)) {
      for (const section of group.sections) {
        const index = section.blocks.findIndex((block) => block.id === blockId);
        if (index >= 0) {
          return {
            part,
            option: part.options.find((option) => option.id === group.optionId) ?? null,
            section,
            block: section.blocks[index]!,
            location: { partId: part.id, optionId: group.optionId, sectionId: section.id, pool: group.pool },
            position: index + 1,
          };
        }
      }
    }
  }
  return null;
}

export function updatePart(parts: readonly ExamPart[], partId: string, update: (part: ExamPart) => ExamPart): ExamPart[] {
  return parts.map((part) => (part.id === partId ? update(part) : part));
}

export function updateSection(
  parts: readonly ExamPart[],
  location: DraftLocation,
  update: (section: ExamSection) => ExamSection,
): ExamPart[] {
  const mapSections = (sections: ExamSection[]) =>
    sections.map((section) => (section.id === location.sectionId ? update(section) : section));
  return updatePart(parts, location.partId, (part) =>
    location.pool
      ? { ...part, subjectPool: (part.subjectPool ?? []).map((s) => (s.id === location.sectionId ? update(s) : s)) }
      : location.optionId === null
        ? { ...part, sections: mapSections(part.sections) }
        : {
            ...part,
            options: part.options.map((option) =>
              option.id === location.optionId ? { ...option, sections: mapSections(option.sections) } : option,
            ),
          },
  );
}

export function updateBlock(
  parts: readonly ExamPart[],
  blockId: string,
  update: (block: QuestionBlock) => QuestionBlock,
): ExamPart[] {
  const context = findBlock(parts, blockId);
  if (!context) return [...parts];
  return updateSection(parts, context.location, (section) => ({
    ...section,
    blocks: section.blocks.map((block) => (block.id === blockId ? update(block) : block)),
  }));
}

/** Khối mới để trống ở cuối môn: kết hợp theo số câu, không giới hạn kiến thức, khớp ít nhất một, không lấy mã con. */
export function newBlock(name: string): QuestionBlock {
  return {
    id: draftId("blk"),
    name,
    questionType: "SINGLE_CHOICE",
    questionCount: 0,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope: null,
    tagCodes: [],
    matrix: emptyMatrix(),
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
  };
}

export function addBlock(parts: readonly ExamPart[], location: DraftLocation, block: QuestionBlock): ExamPart[] {
  return updateSection(parts, location, (section) => ({ ...section, blocks: [...section.blocks, block] }));
}

export function removeBlock(parts: readonly ExamPart[], blockId: string): ExamPart[] {
  const context = findBlock(parts, blockId);
  if (!context) return [...parts];
  const name = context.block.name.trim();
  return updateSection(parts, context.location, (section) => ({
    ...section,
    blocks: section.blocks.filter((block) => block.id !== blockId),
    ...(name && section.mapidRules?.some((rule) => rule.groupId === blockId)
      ? { mapidRules: section.mapidRules.map((rule) => (rule.groupId === blockId ? { ...rule, deletedGroupName: name } : rule)) }
      : {}),
  }));
}

export function moveBlock(parts: readonly ExamPart[], blockId: string, offset: -1 | 1): ExamPart[] {
  const context = findBlock(parts, blockId);
  if (!context) return [...parts];
  return updateSection(parts, context.location, (section) => {
    const from = context.position - 1;
    const to = from + offset;
    if (to < 0 || to >= section.blocks.length) return section;
    const blocks = [...section.blocks];
    const [moved] = blocks.splice(from, 1);
    blocks.splice(to, 0, moved!);
    return { ...section, blocks };
  });
}

/** Số phương án học viên phải chọn của phần tự chọn (quy tắc tham gia `CHOICE`). */
export function setPartChoose(part: ExamPart, choose: number): ExamPart {
  return { ...part, participation: { mode: "CHOICE", choose } };
}

/** Gắn một lựa chọn vào phần tự chọn; số câu các môn chia đều theo phần dư lớn nhất (50 câu, 3 môn: 17–17–16). */
function buildOption(
  kind: ExamPartOption["kind"],
  code: string,
  subjectCodes: readonly string[],
  questionCount: number,
): ExamPartOption {
  const counts = splitEvenly(questionCount, subjectCodes.length);
  return {
    id: draftId("opt"),
    kind,
    code,
    sections: subjectCodes.map((subjectCode, index) => ({
      id: draftId("sec"),
      subjectCode,
      questionCount: counts[index] ?? 0,
      knowledgeScope: null,
      answerQuotas: null,
      matrix: null,
      choiceGroup: null,
      blocks: [],
    })),
    chooseSubjects: null,
    countsByPick: null,
  };
}

/** Số câu một lựa chọn phải có: số câu của phần trừ phần chung (THPTQG Tin học: 4 − 2 = 2). */
export function optionQuestionCount(part: ExamPart): number {
  return Math.max(part.questionCount - sum(part.sections.map((section) => section.questionCount ?? 0)), 0);
}

/** Bật/tắt nhánh Tiếng Anh; nhánh luôn đứng đầu danh sách lựa chọn. */
export function setEnglishOption(parts: readonly ExamPart[], partId: string, enabled: boolean): ExamPart[] {
  return updatePart(parts, partId, (part) => {
    const without = part.options.filter((option) => !(option.kind === "SUBJECT" && option.code === ENGLISH_CODE));
    if (!enabled) return { ...part, options: without };
    if (without.length !== part.options.length) return part;
    return { ...part, options: [buildOption("SUBJECT", ENGLISH_CODE, [ENGLISH_CODE], optionQuestionCount(part)), ...without] };
  });
}

export function removeOption(parts: readonly ExamPart[], partId: string, optionId: string): ExamPart[] {
  return updatePart(parts, partId, (part) => ({ ...part, options: part.options.filter((option) => option.id !== optionId) }));
}

/** Khối đầu tiên của phần (để mở khi chọn phần chưa có gì khác để xem), theo thứ tự hiển thị. */
export function firstBlockId(part: ExamPart): string | null {
  for (const group of sectionGroups(part)) {
    for (const section of group.sections) {
      if (section.blocks[0]) return section.blocks[0].id;
    }
  }
  return null;
}

export function selectionExists(parts: readonly ExamPart[], selection: StructureSelection): boolean {
  if (selection.kind === "exam") return true;
  if (selection.kind === "part") return parts.some((part) => part.id === selection.partId);
  if (selection.kind === "option")
    return parts.some(
      (part) => part.id === selection.partId && part.options.some((option) => option.id === selection.optionId),
    );
  if (selection.kind === "section") return findSection(parts, selection.sectionId) !== null;
  return findBlock(parts, selection.blockId) !== null;
}

export function selectionScope(parts: readonly ExamPart[], selection: StructureSelection): string {
  if (selection.kind === "exam") return "exam";
  if (selection.kind === "part") return `part:${selection.partId}`;
  if (selection.kind === "option") return `option:${selection.optionId}`;
  if (selection.kind === "section") return `section:${selection.sectionId}`;
  const context = findBlock(parts, selection.blockId);
  return context ? `section:${context.section.id}` : `block:${selection.blockId}`;
}

export function defaultSelection(parts: readonly ExamPart[]): StructureSelection | null {
  const first = parts[0];
  if (!first) return null;
  const all = first.codeSource ? (first.subjectPool ?? []) : first.sections;
  if (all.length === 1) return { kind: "section", sectionId: all[0]!.id };
  if (first.codeSource) {
    return all.length ? { kind: "section", sectionId: all[0]!.id } : { kind: "part", partId: first.id };
  }
  const science = first.options.find((option) => option.kind === "SUBJECT_CHOICE");
  if (science && first.options.length === 1 && first.sections.length === 0) {
    return { kind: "option", partId: first.id, optionId: science.id };
  }
  return all.length ? { kind: "section", sectionId: all[0]!.id } : { kind: "part", partId: first.id };
}

export function validateDraft(
  parts: readonly ExamPart[],
  axes: MatrixAxes,
  options: ValidateStructureOptions = {},
): StructureIssue[] {
  const plain = validateStructure(
    parts.filter((part) => !part.codeSource),
    axes,
    options,
  );
  const coded = parts
    .filter((part) => part.codeSource)
    .map((part): ExamPart => ({ ...part, mode: "FIXED", sections: part.subjectPool ?? [], options: [] }));
  const codedIssues = validateStructure(coded, axes, options).filter(
    (issue) => issue.code !== "SECTIONS_TOTAL" && issue.code !== "PART_POINTS",
  );
  return [...plain, ...codedIssues];
}

export interface DraftTotalsOptions extends StructureTotalsOptions {
  /** Mã bài thi của các phần theo mã (`GET structure`): điểm của phần theo mã lấy theo các mã này. */
  combinations?: readonly StructureCombination[];
}

function codedPartPoints(part: ExamPart, combinations: readonly StructureCombination[]): number {
  const pool = part.subjectPool ?? [];
  const common = sectionsPoints(part.sections);
  const first = combinations.find((item) => item.partId === part.id || item.partId === null);
  if (!first) return common;
  const selected = pool.filter((section) => first.subjectCodes.includes(section.subjectCode));
  return common + sectionsPoints(selected);
}

export function draftTotals(parts: readonly ExamPart[], options: DraftTotalsOptions = {}): StructureTotals {
  const plain = structureTotals(
    parts.filter((part) => !part.codeSource),
    options,
  );
  const coded = parts.filter((part) => part.codeSource);
  const codedQuestions = sum(coded.map((part) => part.questionCount));
  const codedPoints = sum(coded.map((part) => codedPartPoints(part, options.combinations ?? [])));
  return {
    ...plain,
    partCount: parts.length,
    totalQuestions: plain.totalQuestions + codedQuestions,
    printedQuestions: plain.printedQuestions + sum(coded.map((part) => Math.max(part.questionCountOnPaper, part.questionCount))),
    totalMinutes: options.sharedDuration ?? plain.totalMinutes + sum(coded.map((part) => part.durationMinutes ?? 0)),
    totalPoints: Math.round((plain.totalPoints + codedPoints) * 1e9) / 1e9,
    allocated: plain.allocated + codedQuestions,
  };
}

/** Chuyên gia xác nhận số câu từng môn của một lựa chọn (bỏ cờ "Chờ chuyên gia xác nhận"). */
export function confirmOption(parts: readonly ExamPart[], partId: string, optionId: string): ExamPart[] {
  return updatePart(parts, partId, (part) => ({
    ...part,
    options: part.options.map((option) => (option.id === optionId ? { ...option, expertConfirmed: true } : option)),
  }));
}

/** Đổi điểm tối đa của phần (BCA 25 / 60 / 15); `null`: phần không chia điểm riêng. */
export function setPartPoints(parts: readonly ExamPart[], partId: string, maxPoints: number | null): ExamPart[] {
  return updatePart(parts, partId, (part) => ({ ...part, maxPoints }));
}

/** Thêm một nhánh nội dung (THPTQG Tin học: Khoa học máy tính, Tin học ứng dụng) với một môn, đủ số câu còn lại. */
export function addBranch(
  parts: readonly ExamPart[],
  partId: string,
  branch: { name: string; subjectCode: string },
): ExamPart[] {
  return updatePart(parts, partId, (part) => {
    const used = new Set(part.options.map((option) => option.code));
    let index = part.options.filter((option) => option.kind === "BRANCH").length + 1;
    while (used.has(`BR-${index}`)) index += 1;
    const option: ExamPartOption = {
      ...buildOption("BRANCH", `BR-${index}`, [branch.subjectCode], optionQuestionCount(part)),
      name: branch.name.trim(),
    };
    return { ...part, options: [...part.options, option] };
  });
}
