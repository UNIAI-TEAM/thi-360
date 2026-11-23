import { sectionGroups } from "../matrix";
import type { ExamPart, ExamSection, QuestionBlock } from "../types";

/** Số dòng thay đổi hiện trong hộp thoại chuyển mục; phần còn lại gộp thành "và N thay đổi khác". */
export const MAX_CHANGE_LINES = 5;

type PartNumberField = "questionCount" | "questionCountRequired" | "questionCountOnPaper" | "durationMinutes" | "maxPoints";

/** Một thay đổi chưa lưu, đã gắn tên hiển thị (phần, môn, nhóm); chữ do hộp thoại dựng từ bảng thông báo. */
export type StructureChange =
  | { kind: "partRenamed"; from: string; to: string }
  | { kind: "partNumber"; part: string; field: PartNumberField; from: number | null; to: number | null }
  | { kind: "partSettings"; part: string }
  | { kind: "sectionCount"; subject: string; from: number | null; to: number | null }
  | { kind: "sectionSettings"; subject: string }
  | { kind: "blockAdded"; subject: string; name: string }
  | { kind: "blockRemoved"; subject: string; name: string }
  | { kind: "blockRenamed"; from: string; to: string }
  | { kind: "blockCount"; name: string; from: number | null; to: number | null }
  | { kind: "blockSettings"; name: string }
  | { kind: "blocksReordered"; subject: string }
  | { kind: "partAdded"; name: string }
  | { kind: "partRemoved"; name: string }
  | { kind: "sectionAdded"; subject: string }
  | { kind: "sectionRemoved"; subject: string }
  /** Bản nháp khác bản đã lưu ở chỗ không có dòng riêng (ví dụ thứ tự phần, mục): danh sách không bao giờ rỗng khi có thay đổi. */
  | { kind: "structureChanged" };

const PART_NUMBER_FIELDS: readonly PartNumberField[] = [
  "questionCount",
  "questionCountRequired",
  "questionCountOnPaper",
  "durationMinutes",
  "maxPoints",
];

function sectionsOf(part: ExamPart): ExamSection[] {
  return [...sectionGroups(part).flatMap((group) => group.sections), ...(part.subjectPool ?? [])];
}

function omit(value: object, keys: readonly string[]): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).filter(([key]) => !keys.includes(key)));
}

/** Phần của `part` ngoài các trường đã so riêng và ngoài môn: lệch thì là "đổi thiết lập phần". */
function partRest(part: ExamPart): string {
  return JSON.stringify({
    ...omit(part, [...PART_NUMBER_FIELDS, "name", "sections", "subjectPool", "options"]),
    options: part.options.map((option) => omit(option, ["sections"])),
  });
}

const sectionRest = (section: ExamSection): string => JSON.stringify(omit(section, ["blocks", "questionCount"]));
const blockRest = (block: QuestionBlock): string => JSON.stringify(omit(block, ["name", "questionCount"]));

const sameOrder = (before: readonly { id: string }[], after: readonly { id: string }[]): boolean =>
  before.length === after.length && before.every((item, index) => item.id === after[index]?.id);

/**
 * Các thay đổi của bản nháp so với bản đã lưu, theo thứ tự phần → môn → nhóm. Phần và môn có ở cả hai bản (cùng `id`)
 * được so từng trường; phần, môn, nhóm thêm hoặc xóa và nhóm đổi thứ tự được báo riêng. `subjectName` đổi mã môn thành
 * tên theo ngôn ngữ đang chọn.
 */
export function describeStructureChanges(
  baseline: readonly ExamPart[],
  draft: readonly ExamPart[],
  subjectName: (code: string) => string,
): StructureChange[] {
  const changes: StructureChange[] = [];
  for (const part of draft) {
    const before = baseline.find((item) => item.id === part.id);
    if (!before) {
      changes.push({ kind: "partAdded", name: part.name });
      continue;
    }
    if (before.name !== part.name) changes.push({ kind: "partRenamed", from: before.name, to: part.name });
    for (const field of PART_NUMBER_FIELDS) {
      const from = before[field] ?? null;
      const to = part[field] ?? null;
      if (from !== to) changes.push({ kind: "partNumber", part: part.name, field, from, to });
    }
    if (partRest(before) !== partRest(part)) changes.push({ kind: "partSettings", part: part.name });

    const beforeSections = sectionsOf(before);
    const sections = sectionsOf(part);
    for (const section of sections) {
      const old = beforeSections.find((item) => item.id === section.id);
      const subject = subjectName(section.subjectCode);
      if (!old) {
        changes.push({ kind: "sectionAdded", subject });
        continue;
      }
      if (old.questionCount !== section.questionCount) {
        changes.push({ kind: "sectionCount", subject, from: old.questionCount, to: section.questionCount });
      }
      if (sectionRest(old) !== sectionRest(section)) changes.push({ kind: "sectionSettings", subject });
      for (const block of section.blocks) {
        const oldBlock = old.blocks.find((item) => item.id === block.id);
        if (!oldBlock) {
          changes.push({ kind: "blockAdded", subject, name: block.name });
          continue;
        }
        if (oldBlock.name !== block.name) changes.push({ kind: "blockRenamed", from: oldBlock.name, to: block.name });
        if (oldBlock.questionCount !== block.questionCount) {
          changes.push({ kind: "blockCount", name: block.name, from: oldBlock.questionCount, to: block.questionCount });
        }
        if (blockRest(oldBlock) !== blockRest(block)) changes.push({ kind: "blockSettings", name: block.name });
      }
      for (const oldBlock of old.blocks) {
        if (!section.blocks.some((item) => item.id === oldBlock.id)) {
          changes.push({ kind: "blockRemoved", subject, name: oldBlock.name });
        }
      }
      // Cùng tập nhóm mà khác thứ tự: chỉ đổi thứ tự, không có trường nào khác để báo.
      const kept = section.blocks.filter((block) => old.blocks.some((item) => item.id === block.id));
      const keptBefore = old.blocks.filter((block) => section.blocks.some((item) => item.id === block.id));
      if (!sameOrder(keptBefore, kept)) changes.push({ kind: "blocksReordered", subject });
    }
    for (const old of beforeSections) {
      if (!sections.some((item) => item.id === old.id)) changes.push({ kind: "sectionRemoved", subject: subjectName(old.subjectCode) });
    }
  }
  for (const before of baseline) {
    if (!draft.some((part) => part.id === before.id)) changes.push({ kind: "partRemoved", name: before.name });
  }
  if (changes.length === 0 && JSON.stringify(baseline) !== JSON.stringify(draft)) changes.push({ kind: "structureChanged" });
  return changes;
}
