import { isFlexibleSet } from "../matrix";
import type { ExamSection } from "../types";
import type { BlockingIssue, FormBlockingRule } from "./blocking";
import { sectionSetCount, sectionShortAnswerCount, subjectFormLimits } from "./limits";

/** Mã lỗi chặn lưu của MT: ánh xạ tới khóa chữ báo lỗi tại `exams.subjectForm.matrix.errors`. */
export const MATRIX_ISSUE_CODES = {
  setMin: "SET_COUNT_MIN",
  setSizeMin: "SET_SIZE_MIN",
  setRange: "SET_COUNT_RANGE",
  shortAnswerMin: "SHORT_ANSWER_MIN",
  choiceBelowChildren: "CHOICE_BELOW_CHILDREN",
  structurePercentTotal: "STRUCTURE_PERCENT_TOTAL",
} as const;

/** Tên ô (`data-form-field`) của MT: đích của lỗi chặn lưu, của `?block=`, bấm lỗi kiểm tra và tóm tắt. */
export const MATRIX_FIELDS = {
  /** Cả khối Cấu trúc thành phần: đích mặc định (`navigation.ts`). */
  structure: "STRUCTURE",
  choice: "CHOICE",
  sets: "SETS",
  children: "CHILDREN",
  single: "SINGLE",
  fill: "FILL",
  setSource: "SET_SOURCE",
  scope: "SCOPE",
  topicQuotas: "TOPIC_QUOTAS",
  rowCount: (blockId: string) => `COUNT:${blockId}`,
  rowSets: (blockId: string) => `SETS:${blockId}`,
  rowSetSize: (blockId: string) => `SET_SIZE:${blockId}`,
} as const;

/** Lỗi chặn lưu tính từ bản nháp của một môn MT trên danh sách dòng động. */
export function matrixBlockingIssues(section: ExamSection, kindCode: string): BlockingIssue[] {
  const issues: BlockingIssue[] = [];
  const limits = subjectFormLimits(kindCode, section.subjectCode);
  const activeBlocks = section.blocks.filter((block) => !block.omitted);
  const fixedSetBlocks = activeBlocks.filter((block) => block.questionType === "QUESTION_SET" && !isFlexibleSet(block));

  for (const block of fixedSetBlocks) {
    const sets = block.setCount ?? 0;
    const size = block.setSize ?? 0;
    if (sets < 1) {
      issues.push({ code: MATRIX_ISSUE_CODES.setMin, sectionId: section.id, blockId: block.id, field: MATRIX_FIELDS.rowSets(block.id) });
    }
    if (size < 1) {
      issues.push({ code: MATRIX_ISSUE_CODES.setSizeMin, sectionId: section.id, blockId: block.id, field: MATRIX_FIELDS.rowSetSize(block.id) });
    }
  }

  if (limits && fixedSetBlocks.length > 0 && !issues.some((issue) => issue.code === MATRIX_ISSUE_CODES.setMin)) {
    const totalSets = sectionSetCount(section);
    if (totalSets > limits.setCount.max) {
      const target = fixedSetBlocks[0]!;
      issues.push({ code: MATRIX_ISSUE_CODES.setRange, sectionId: section.id, blockId: target.id, field: MATRIX_FIELDS.rowSets(target.id) });
    }
  }

  if (limits && limits.shortAnswerMin > 0 && sectionShortAnswerCount(section) < limits.shortAnswerMin) {
    const fillBlock = activeBlocks.find((block) => block.questionType === "SHORT_ANSWER");
    if (fillBlock) {
      issues.push({
        code: MATRIX_ISSUE_CODES.shortAnswerMin,
        sectionId: section.id,
        blockId: fillBlock.id,
        field: MATRIX_FIELDS.rowCount(fillBlock.id),
      });
    } else {
      issues.push({
        code: MATRIX_ISSUE_CODES.shortAnswerMin,
        sectionId: section.id,
        blockId: null,
        field: MATRIX_FIELDS.structure,
      });
    }
  }

  return issues;
}

/** Luật đăng ký ở `FORM_BLOCKING_RULES`: chỉ môn đang dùng MT. */
export const matrixBlockingRule: FormBlockingRule = (section, context) => (context.form === "MATRIX" ? matrixBlockingIssues(section, context.kindCode) : []);
