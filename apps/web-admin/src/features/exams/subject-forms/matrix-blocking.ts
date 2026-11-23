import { isFlexibleSet } from "../matrix";
import type { ExamSection } from "../types";
import type { BlockingIssue, FormBlockingRule } from "./blocking";
import { subjectFormLimits } from "./limits";
import { roleBlocks } from "./matrix-form-model";

/** Mã lỗi của MT: cũng là khóa chữ báo lỗi tại ô ở `exams.subjectForm.matrix.errors`. */
export const MATRIX_ISSUE_CODES = {
  setMin: "SET_COUNT_MIN",
  setRange: "SET_COUNT_RANGE",
  shortAnswerMin: "SHORT_ANSWER_MIN",
  choiceBelowChildren: "CHOICE_BELOW_CHILDREN",
  structurePercentTotal: "STRUCTURE_PERCENT_TOTAL",
} as const;

/** Tên ô (`data-form-field`) của MT: đích của lỗi chặn lưu, của `?block=`, bấm lỗi kiểm tra và tóm tắt. */
export const MATRIX_FIELDS = {
  /** Cả khối Cấu trúc câu: đích mặc định (`navigation.ts`). */
  structure: "STRUCTURE",
  choice: "CHOICE",
  sets: "SETS",
  children: "CHILDREN",
  single: "SINGLE",
  fill: "FILL",
  setSource: "SET_SOURCE",
  scope: "SCOPE",
  topicQuotas: "TOPIC_QUOTAS",
} as const;

/** Lỗi chặn lưu tính từ bản nháp của một môn MT. */
export function matrixBlockingIssues(section: ExamSection, kindCode: string): BlockingIssue[] {
  const issues: BlockingIssue[] = [];
  const { set, fill } = roleBlocks(section);
  const limits = subjectFormLimits(kindCode, section.subjectCode);
  if (set && !isFlexibleSet(set)) {
    const sets = set.setCount ?? 0;
    if (sets < 1) issues.push({ code: MATRIX_ISSUE_CODES.setMin, sectionId: section.id, blockId: set.id, field: MATRIX_FIELDS.sets });
    else if (limits && sets > limits.setCount.max) issues.push({ code: MATRIX_ISSUE_CODES.setRange, sectionId: section.id, blockId: set.id, field: MATRIX_FIELDS.sets });
  }
  if (limits && fill && (fill.questionCount ?? 0) < limits.shortAnswerMin) {
    issues.push({ code: MATRIX_ISSUE_CODES.shortAnswerMin, sectionId: section.id, blockId: fill.id, field: MATRIX_FIELDS.fill });
  }
  return issues;
}

/** Luật đăng ký ở `FORM_BLOCKING_RULES`: chỉ môn đang dùng MT. */
export const matrixBlockingRule: FormBlockingRule = (section, context) => (context.form === "MATRIX" ? matrixBlockingIssues(section, context.kindCode) : []);
