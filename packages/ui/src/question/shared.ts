import type { KitQuestion, QuestionReview } from "./types";

/** Prop chung của mọi thành phần câu hỏi trong kit (`@thi360/ui/question`). */
export interface KitQuestionProps<Q extends KitQuestion = KitQuestion, V = unknown> {
  /** Câu hỏi đã ánh xạ sang hình dạng ngân hàng (BE 4.3); `displayNo` là số câu trong bài. */
  question: Q;
  /** `answer`: giá trị đang nhập; `review`: bài đã nộp. */
  mode: "answer" | "review";
  /** Đáp án theo định dạng `answer` của `attempt_item` (LT 4). */
  value?: V | null;
  onChange?: (value: V) => void;
  /** Cờ đánh dấu xem lại của câu (LT 7.3); hiện nút cờ khi có `onToggleFlag` hoặc `flagged`. */
  flagged?: boolean;
  onToggleFlag?: () => void;
  /** Kết quả chấm của câu; bắt buộc ở `mode="review"`. */
  review?: QuestionReview | null;
  /** Cấp thẻ tiêu đề "Câu n" (mặc định 2; trong Bộ câu hỏi con dùng 3). */
  headingLevel?: 1 | 2 | 3;
  className?: string;
}

/** Review của một phần tử sau khi hòa với `review.children[id]` của câu cha. */
export function resolveReview(review: QuestionReview | null | undefined): QuestionReview | null {
  return review ?? null;
}

/**
 * Review riêng của câu con `id` trong `review` của Bộ câu hỏi. Thiếu dòng của con thì không bịa kết quả
 * (không có chip "Bỏ trống"): chỉ giữ `showAnswers` của cha để không lộ đáp án.
 */
export function childReview(review: QuestionReview | null | undefined, id: string): QuestionReview | null {
  const direct = review?.children?.[id];
  if (direct) return direct;
  return review ? { showAnswers: review.showAnswers } : null;
}

/** Tập `correctAnswer` được phép hiện: mặc định hiện; tắt bằng `showAnswers: false`. */
export function answersVisible(review: QuestionReview | null | undefined): boolean {
  return review?.showAnswers !== false;
}
