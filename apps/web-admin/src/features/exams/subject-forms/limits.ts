import type { ExamSection } from "../types";
import { subjectFormFor, type SubjectFormEntry } from "./registry";

/**
 * Giới hạn R-12 phía web (Khoa học HSA, QDA), đọc từ bảng đăng ký. Máy chủ giữ bảng riêng (`SubjectFormLimits`); CR-2 đối
 * chiếu hai bảng khi nộp. Áp cho bài chuẩn: bài ôn không bao giờ tới đây vì không chọn MT/DS (R-1).
 */
export interface SubjectFormLimits {
  setCount: { min: number; max: number; size: number };
  shortAnswerMin: number;
}

export type LimitViolationCode = "SET_COUNT_RANGE" | "SET_SIZE" | "SHORT_ANSWER_MIN";

export interface LimitViolation {
  code: LimitViolationCode;
}

export function limitsOfEntry(entry: SubjectFormEntry | null): SubjectFormLimits | null {
  if (!entry?.setCount) return null;
  return { setCount: entry.setCount, shortAnswerMin: entry.shortAnswerMin ?? 0 };
}

/** Giới hạn của cặp (kể cả khi biểu mẫu của cặp đang tắt); `null` khi cặp không có giới hạn. */
export function subjectFormLimits(kindCode: string, subjectCode: string): SubjectFormLimits | null {
  return limitsOfEntry(subjectFormFor(kindCode, subjectCode));
}

/** Số chùm của môn: tổng `setCount` của các nhóm chùm (nhóm chưa khai số chùm tính 0). */
export function sectionSetCount(section: ExamSection): number {
  return section.blocks.filter((block) => block.questionType === "QUESTION_SET").reduce((total, block) => total + (block.setCount ?? 0), 0);
}

/** Số câu điền đáp án của môn: tổng số câu của các nhóm Trả lời ngắn. */
export function sectionShortAnswerCount(section: ExamSection): number {
  return section.blocks.filter((block) => block.questionType === "SHORT_ANSWER").reduce((total, block) => total + (block.questionCount ?? 0), 0);
}

/** Điểm vi phạm giới hạn của môn tính trên bản nháp: số chùm ngoài khoảng, cỡ chùm khác cỡ quy định, câu điền dưới mức tối thiểu. */
export function limitViolations(section: ExamSection, limits: SubjectFormLimits): LimitViolation[] {
  const violations: LimitViolation[] = [];
  const setCount = sectionSetCount(section);
  if (setCount < limits.setCount.min || setCount > limits.setCount.max) violations.push({ code: "SET_COUNT_RANGE" });
  if (section.blocks.some((block) => block.questionType === "QUESTION_SET" && block.setSize !== limits.setCount.size)) {
    violations.push({ code: "SET_SIZE" });
  }
  if (sectionShortAnswerCount(section) < limits.shortAnswerMin) violations.push({ code: "SHORT_ANSWER_MIN" });
  return violations;
}
