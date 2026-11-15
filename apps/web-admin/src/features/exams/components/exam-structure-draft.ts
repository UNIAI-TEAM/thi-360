import type { CombinationOption } from "@/features/content/types";
import { emptyMatrix, type SectionLocation, sectionGroups, splitEvenly } from "../matrix";
import type { ExamPart, ExamPartOption, ExamSection, QuestionBlock } from "../types";

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

/** Đang sửa gì: một phần (thông tin phần, nhánh và tổ hợp) hoặc một khối. */
export type StructureSelection = { kind: "part"; partId: string } | { kind: "block"; blockId: string };

export interface BlockContext {
  part: ExamPart;
  option: ExamPartOption | null;
  section: ExamSection;
  block: QuestionBlock;
  location: SectionLocation;
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
            location: { partId: part.id, optionId: group.optionId, sectionId: section.id },
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
  location: SectionLocation,
  update: (section: ExamSection) => ExamSection,
): ExamPart[] {
  const mapSections = (sections: ExamSection[]) =>
    sections.map((section) => (section.id === location.sectionId ? update(section) : section));
  return updatePart(parts, location.partId, (part) =>
    location.optionId === null
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
    pointsPerQuestion: 1,
    knowledgeScope: { nodeIds: [], match: "ANY", includeDescendants: false },
    tagCodes: [],
    matrix: emptyMatrix(),
    shuffle: "SHUFFLE",
  };
}

export function addBlock(parts: readonly ExamPart[], location: SectionLocation, block: QuestionBlock): ExamPart[] {
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
 * Đổi số câu của phần. Phần cố định chỉ có một môn thì số câu của môn đổi theo (hai số phải bằng nhau). Phần nhiều môn và
 * các tổ hợp giữ phân bổ đã khai báo; lệch thì báo lỗi để người dùng tự chia (không tự giảm câu, mục 5.3).
 */
export function setPartQuestionCount(part: ExamPart, questionCount: number): ExamPart {
  const syncSingle = (sections: ExamSection[]) =>
    sections.length === 1 ? [{ ...sections[0]!, questionCount }] : sections;
  return {
    ...part,
    questionCount,
    sections: syncSingle(part.sections),
    options: part.options.map((option) =>
      option.kind === "SUBJECT" ? { ...option, sections: syncSingle(option.sections) } : option,
    ),
  };
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
      blocks: [],
    })),
  };
}

export function attachCombination(parts: readonly ExamPart[], partId: string, combination: CombinationOption): ExamPart[] {
  return updatePart(parts, partId, (part) => ({
    ...part,
    options: [...part.options, buildOption("COMBINATION", combination.code, combination.subjectCodes, part.questionCount)],
  }));
}

/** Bật/tắt nhánh Tiếng Anh; nhánh luôn đứng đầu danh sách lựa chọn. */
export function setEnglishOption(parts: readonly ExamPart[], partId: string, enabled: boolean): ExamPart[] {
  return updatePart(parts, partId, (part) => {
    const without = part.options.filter((option) => !(option.kind === "SUBJECT" && option.code === ENGLISH_CODE));
    if (!enabled) return { ...part, options: without };
    if (without.length !== part.options.length) return part;
    return { ...part, options: [buildOption("SUBJECT", ENGLISH_CODE, [ENGLISH_CODE], part.questionCount), ...without] };
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
  return selection.kind === "part"
    ? parts.some((part) => part.id === selection.partId)
    : findBlock(parts, selection.blockId) !== null;
}

/** Mặc định mở khối đầu tiên của bài; bài chưa có khối thì mở phần đầu tiên. */
export function defaultSelection(parts: readonly ExamPart[]): StructureSelection | null {
  for (const part of parts) {
    if (part.mode === "FIXED") {
      const blockId = firstBlockId(part);
      if (blockId) return { kind: "block", blockId };
    }
  }
  return parts[0] ? { kind: "part", partId: parts[0].id } : null;
}
