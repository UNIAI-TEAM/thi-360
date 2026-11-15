import type { StatusTone } from "@thi360/ui";
import type { ExamCheckStatus, ExamStatus } from "./types";

/** Màu chip; chữ lấy từ `exams.status.<STATUS>` và `exams.checkStatus.<STATUS>`. */
export const EXAM_STATUS_TONE: Record<ExamStatus, StatusTone> = {
  DRAFT: "neutral",
  PUBLISHED: "success",
  DISCONTINUED: "neutral",
};

export const EXAM_CHECK_TONE: Record<ExamCheckStatus, StatusTone> = {
  UNCHECKED: "neutral",
  VALID: "success",
  INVALID: "danger",
  NEEDS_RECHECK: "warning",
};
