import type { CombinationOption } from "@/features/content/types";
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

/**
 * Bản nháp của Cấu trúc và ma trận: các hàm cập nhật bất biến trên `ExamPart[]`. Màn chỉ lưu khi bấm Lưu
 * (`updateExamStructure`). Mã tạm của phần tử mới sinh ở trình duyệt; backend thay bằng mã thật khi lưu.
 */

let draftSequence = 0;
export function draftId(prefix: string): string {
  draftSequence += 1;
  return `${prefix}-draft-${Date.now().toString(36)}-${draftSequence}`;
}

export const ENGLISH_CODE = "ENGLISH";

/** Đang sửa gì: một phần (thông tin phần, lựa chọn), một phương án chọn môn (tổng quan Khoa học), một môn (chỉ tiêu cấp môn và các nhóm) hoặc một nhóm lấy câu. */
export type StructureSelection =
  | { kind: "part"; partId: string }
  | { kind: "option"; partId: string; optionId: string }
  | { kind: "section"; sectionId: string }
  | { kind: "block"; blockId: string };

/** Vị trí môn trong bản nháp; `pool`: môn nằm trong `subjectPool` của phần theo mã (R-24), không phải `sections`. */
export type DraftLocation = SectionLocation & { pool?: boolean };

/**
 * Nhóm môn theo vị trí lưu như `sectionGroups` của `matrix.ts`, thêm nhóm `subjectPool` của phần theo mã (`pool: true`).
 * Phần theo mã không có `sections` hay lựa chọn: các môn nằm trong `subjectPool`.
 */
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
  return updateSection(parts, context.location, (section) => ({
    ...section,
    blocks: section.blocks.filter((block) => block.id !== blockId),
  }));
}

/** Dời khối lên/xuống trong môn (thứ tự khối là thứ tự trong đề, UniNote mục 6.2). */
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

/**
 * Đổi số câu học viên phải làm của phần. Phần cố định chỉ có một môn thì số câu của môn đổi theo (hai số phải bằng nhau).
 * Phần nhiều môn và các tổ hợp giữ phân bổ đã khai báo; lệch thì báo lỗi để người dùng tự chia (không tự giảm câu, mục
 * 5.3). Số câu trên đề đi theo khi hai số đang bằng nhau (khi mọi câu đều bắt buộc, số câu trên đề là mặc định, mục 5.2).
 */
export function setPartQuestionCount(part: ExamPart, questionCount: number): ExamPart {
  const syncSingle = (sections: ExamSection[], count: number) =>
    sections.length === 1 ? [{ ...sections[0]!, questionCount: count }] : sections;
  const onPaper = part.questionCountOnPaper === part.questionCount ? questionCount : part.questionCountOnPaper;
  // Phần tự chọn có phần chung: giữ số câu phần chung, lựa chọn một môn nhận phần còn lại.
  if (part.mode === "CHOICE" && part.sections.length) {
    const next = { ...part, questionCount, questionCountRequired: questionCount, questionCountOnPaper: onPaper };
    const remaining = optionQuestionCount(next);
    return {
      ...next,
      options: part.options.map((option) =>
        option.kind !== "COMBINATION" ? { ...option, sections: syncSingle(option.sections, remaining) } : option,
      ),
    };
  }
  return {
    ...part,
    questionCount,
    questionCountRequired: questionCount,
    questionCountOnPaper: onPaper,
    sections: syncSingle(part.sections, questionCount),
    options: part.options.map((option) =>
      option.kind === "SUBJECT" ? { ...option, sections: syncSingle(option.sections, questionCount) } : option,
    ),
  };
}

/** Số câu in trên đề của phần (gồm cả nội dung lựa chọn nếu đề trình bày nhiều phương án). */
export function setPartOnPaper(part: ExamPart, questionCountOnPaper: number): ExamPart {
  return { ...part, questionCountOnPaper };
}

/** Số phương án học viên phải chọn của phần tự chọn (quy tắc tham gia `CHOICE`). */
export function setPartChoose(part: ExamPart, choose: number): ExamPart {
  return { ...part, participation: { mode: "CHOICE", choose } };
}

/** Gắn một lựa chọn vào phần tự chọn; số câu các môn chia đều theo phần dư lớn nhất (50 câu, 3 môn: 17–17–16). */
export function buildOption(
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
  return Math.max(part.questionCount - part.sections.reduce((total, section) => total + (section.questionCount ?? 0), 0), 0);
}

export function attachCombination(parts: readonly ExamPart[], partId: string, combination: CombinationOption): ExamPart[] {
  return updatePart(parts, partId, (part) => ({
    ...part,
    options: [
      ...part.options,
      buildOption("COMBINATION", combination.code, combination.subjectCodes, optionQuestionCount(part)),
    ],
  }));
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
  if (selection.kind === "part") return parts.some((part) => part.id === selection.partId);
  if (selection.kind === "option")
    return parts.some(
      (part) => part.id === selection.partId && part.options.some((option) => option.id === selection.optionId),
    );
  if (selection.kind === "section") return findSection(parts, selection.sectionId) !== null;
  return findBlock(parts, selection.blockId) !== null;
}

/**
 * Khóa "đang ở môn hay phần nào" của một mục: chuyển sang khóa khác khi còn thay đổi chưa lưu thì hỏi lại (R-05). Nhóm
 * thuộc môn chứa nó nên mở nhóm của cùng môn không phải chuyển môn.
 */
export function selectionScope(parts: readonly ExamPart[], selection: StructureSelection): string {
  if (selection.kind === "part") return `part:${selection.partId}`;
  if (selection.kind === "option") return `option:${selection.optionId}`;
  if (selection.kind === "section") return `section:${selection.sectionId}`;
  const context = findBlock(parts, selection.blockId);
  return context ? `section:${context.section.id}` : `block:${selection.blockId}`;
}

/**
 * Mục mở mặc định (bản vẽ `BaiThiBuoc2`): bài một môn → môn đó; bài FULL (nhiều môn, không chọn) → môn của phần đầu
 * tiên; phần đầu tiên theo mã → môn đầu của `subjectPool`; phần đầu tiên chỉ có phương án chọn môn (HSA Khoa học) →
 * phương án đó (tổng quan chọn môn). Không khớp → phần đầu tiên.
 */
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

/** Đổi số câu một môn. Môn của lựa chọn đã được chuyên gia xác nhận thì số câu mới phải xác nhận lại. */
export function setSectionCount(parts: readonly ExamPart[], location: DraftLocation, questionCount: number): ExamPart[] {
  const updated = updateSection(parts, location, (section) => ({ ...section, questionCount }));
  if (location.optionId === null) return updated;
  return updatePart(updated, location.partId, (part) => ({
    ...part,
    options: part.options.map((option) =>
      option.id === location.optionId && option.expertConfirmed === true ? { ...option, expertConfirmed: false } : option,
    ),
  }));
}

/**
 * Kiểm tra cấu trúc trên máy như `validateStructure`, nhưng với phần theo mã (QDA, BCA): các môn nằm trong
 * `subjectPool`; mỗi mã chỉ lấy một phần số câu của phần nên không so tổng môn với số câu của phần (R-24, lỗi này do
 * kiểm tra cấu hình báo theo từng mã).
 */
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

/**
 * Điểm của một phần theo mã, cùng cách tính với `structure/summary` của máy chủ: điểm các môn chung, cộng điểm các môn
 * của một mã (học viên chọn một mã). Các mã cho cùng số điểm thì đó là điểm của phần; lệch nhau thì máy chủ không có số
 * (hiện "–"), còn tổng trên màn hình lấy mã đầu tiên như phần tự chọn thường (`structureTotals`). Chưa có mã nào: chỉ
 * môn chung.
 */
function codedPartPoints(part: ExamPart, combinations: readonly StructureCombination[]): number {
  const pool = part.subjectPool ?? [];
  const common = sectionsPoints(part.sections);
  const first = combinations.find((item) => item.partId === part.id || item.partId === null);
  if (!first) return common;
  const selected = pool.filter((section) => first.subjectCodes.includes(section.subjectCode));
  return common + sectionsPoints(selected);
}

/**
 * Tổng bài từ bản nháp; phần theo mã tính số câu, thời lượng và điểm theo các mã của phần (như `structure/summary`), phân
 * bổ từng mã do máy chủ tính.
 */
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
