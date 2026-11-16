import type { StatusTone } from "@thi360/ui";
import type { GradingStatus } from "./types";

/** Màu chip trạng thái lượt thi. Chữ lấy từ `grading.status.<STATUS>`. */
export const GRADING_STATUS_TONE: Record<GradingStatus, StatusTone> = {
  GRADING: "info",
  SCORED: "success",
};

/** Đường dẫn màn chấm của một lượt thi. */
export function gradingAttemptHref(attemptId: string): string {
  return `/grading/${encodeURIComponent(attemptId)}`;
}
