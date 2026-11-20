import { isFlexibleSet } from "../matrix";
import type { ExamSection } from "../types";
import type { BlockingIssue, FormBlockingRule } from "./blocking";
import { subjectFormLimits } from "./limits";
import { roleBlocks } from "./matrix-form-model";

/**
 * Luật chặn lưu của biểu mẫu MT (FTM R-12, R-3, R-15; CR-2). Tính trên bản nháp của **mọi** môn dùng MT (không trên màn đang mở), cộng
 * các mục nhập thô mà màn ghi vào kho nhập thô (`raw-input-store.ts`): đăng ký ở `blocking.ts` bằng đúng một phần tử.
 *
 * - Từ bản nháp: "Ít nhất 1 chùm" (chùm cỡ cố định không về 0 ở MT), số chùm ngoài 1–2 và câu điền dưới mức tối thiểu của Khoa học HSA,
 *   QDA (giới hạn R-12 phía web, đọc từ `limits.ts`).
 * - Từ nhập thô (màn ghi, `blocking.ts` ghép sau luật): `CHOICE_BELOW_CHILDREN` (Trắc nghiệm nhỏ hơn câu con của chùm, nhóm đơn không
 *   thể âm) và `STRUCTURE_PERCENT_TOTAL` (Cấu trúc câu V-ACT nhập % chưa đủ 100%, không quy ra số).
 */

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
