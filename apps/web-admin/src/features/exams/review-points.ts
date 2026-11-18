import { rendersReviewPanel } from "./allocation";
import type { ExamDetail, ExamPart } from "./types";

/** Điểm mỗi câu của bài ôn V-ACT khi chưa nhập thang (quy tắc V-ACT: 10 điểm một câu; bài không có thang thì không so tổng). */
export const REVIEW_VACT_POINTS_PER_QUESTION = 10;

/** Phần của bài mà điểm mỗi câu của bài ôn phụ thuộc: kỳ thi và thang (thô, hoặc tham chiếu khi chấm luyện tập tuyến tính). */
export type ReviewScoring = Pick<ExamDetail, "kind" | "maxScore" | "referenceMax" | "practiceScoring">;

/**
 * Thang mà kiểm tra so tổng điểm các nhóm với: bài `LINEAR` so với `referenceMax`, bài khác với `maxScore`. Bài `LINEAR`
 * chưa có `referenceMax` thì kiểm tra không so tổng nhưng vẫn đòi điểm mỗi câu khi bài có `maxScore`: dùng `maxScore`.
 */
export function reviewScale(exam: ReviewScoring): number | null {
  const target = exam.practiceScoring === "LINEAR" ? exam.referenceMax : exam.maxScore;
  return target ?? exam.maxScore ?? null;
}

/**
 * Điểm mỗi câu của bài ôn: thang chia số câu, để tổng các nhóm khớp thang mà không có ô nhập điểm (thang là mặc định của kỳ
 * thi hoặc số người dùng nhập ở bước 1). Giữ đúng số thực của phép chia, không làm tròn: làm tròn 4 chữ số làm tổng lệch
 * quá dung sai của kiểm tra (150 ÷ 7 làm tròn rồi nhân 7 ra 150,0002). Bài chưa có thang: V-ACT 10 điểm, kỳ thi khác để trống.
 */
export function reviewPointsPerQuestion(exam: ReviewScoring, count: number): number | null {
  if (!Number.isFinite(count) || count <= 0) return null;
  const scale = reviewScale(exam);
  if (scale !== null) return scale / count;
  return exam.kind === "VACT" ? REVIEW_VACT_POINTS_PER_QUESTION : null;
}

/**
 * Cấu trúc bài ôn kèm điểm mỗi câu của nhóm duy nhất (`reviewPointsPerQuestion`), tính lại theo thang và số câu hiện tại để
 * bài lưu luôn qua được kiểm tra tổng điểm, kể cả khi thang đổi sau lần lưu trước. Chỉ nhóm đã được thiết lập (đã chọn dạng
 * câu hoặc đã có điểm): bài vừa tạo chưa đụng tới giữ nguyên, không tự thành thay đổi chưa lưu.
 */
export function withReviewPoints(parts: readonly ExamPart[], exam: ReviewScoring): ExamPart[] {
  return parts.map((part) => {
    if (part.sections.length !== 1 || !rendersReviewPanel(part.sections[0]!, true)) return part;
    const section = part.sections[0]!;
    const block = section.blocks[0]!;
    if (block.questionType === null && block.requiredPointsPerQuestion === null) return part;
    const points = reviewPointsPerQuestion(exam, block.questionCount ?? section.questionCount ?? 0);
    if (points === null || points === block.requiredPointsPerQuestion) return part;
    return { ...part, sections: [{ ...section, blocks: [{ ...block, requiredPointsPerQuestion: points }] }] };
  });
}
