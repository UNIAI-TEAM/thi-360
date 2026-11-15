import type { StatusTone } from "@thi360/ui";
import type { QuestionStatus } from "./types";

/** Màu chip trạng thái câu hỏi. Chữ lấy từ `content.questionStatus.<STATUS>`. */
export const QUESTION_STATUS_TONE: Record<QuestionStatus, StatusTone> = {
  DRAFT: "neutral",
  READY: "success",
  RETIRED: "warning",
};
