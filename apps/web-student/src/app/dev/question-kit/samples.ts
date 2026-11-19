import type { AnswerValue, KitQuestion, QuestionReview } from "@thi360/ui/question";
import raw from "./question-samples.json";

/** Một mẫu câu hỏi của trang thử: nội dung tiếng Việt nằm trong `question-samples.json`. */
export interface KitSample {
  key: string;
  question: KitQuestion;
  /** Câu con của dạng Bộ câu hỏi. */
  children?: KitQuestion[];
  /** Bài đã nộp, dùng ở chế độ xem lại. */
  submitted: AnswerValue;
  review: QuestionReview;
}

export interface KitChrome {
  title: string;
  intro: string;
  modeLabel: string;
  modeAnswer: string;
  modeReview: string;
  matchUiLabel: string;
  matchUiSelect: string;
  matchUiPick: string;
  valueLabel: string;
}

export const KIT_SAMPLES = raw.samples as unknown as KitSample[];
export const KIT_CHROME = raw.chrome as Record<"vi" | "en", KitChrome>;
