/**
 * Kiểu dữ liệu của bộ thành phần câu hỏi `@thi360/ui/question` (plan FS-4, LT 4, BE 4.3).
 *
 * `KitQuestion.body` theo `QuestionBody` của ngân hàng câu hỏi (spec BE 4.3): lane dùng tự ánh xạ
 * từ kiểu OpenAPI sang hình dạng này. `AnswerValue` đúng định dạng `answer` của `attempt_item`
 * (LT mục 4). `QuestionReview` là dữ liệu đã chuẩn hóa cho chế độ `review` (LT 10.2): lane ánh xạ
 * `outcome`, `earned_points`, `grading_detail` của `attempt_item` sang.
 */

export type QuestionType =
  | "SINGLE_CHOICE"
  | "MULTIPLE_CHOICE"
  | "SHORT_ANSWER"
  | "CLOZE"
  | "QUESTION_SET"
  | "TRUE_FALSE"
  | "MATCHING"
  | "ESSAY";

export type QuestionMode = "answer" | "review";

/** Kết quả của một câu hoặc một ý (LT 4 `attempt_item.outcome`). */
export type QuestionOutcome = "CORRECT" | "PARTIAL" | "WRONG" | "BLANK" | "PENDING_REVIEW";

export interface ChoiceOption {
  id: string;
  /** HTML nội dung phương án. */
  content: string;
  /** Chỉ điền khi màn được phép thấy đáp án (review, xem trước quản trị). */
  correct?: boolean;
}

export interface TrueFalseStatement {
  id: string;
  content: string;
  answer?: boolean;
  /** Điểm của ý khi `scoringMethod = "PER_STATEMENT"`; `null` khi chấm theo bảng. */
  points?: number | null;
}

/** Ô trống của câu Điền khuyết; vị trí đánh dấu `[[id]]` trong `passage`. */
export interface ClozeBlank {
  id: string;
  /** Đáp án chuẩn đứng đầu, sau là các đáp án được chấp nhận. */
  answers: string[];
  points?: number;
}

export interface MatchingItem {
  id: string;
  content: string;
}

export interface MatchingPair {
  leftId: string;
  rightId: string;
  points?: number;
}

/** Một tiêu chí của hướng dẫn chấm câu Tự luận (tùy chọn, BE D20). */
export interface EssayCriterion {
  id: string;
  description: string;
  points: number;
}

export type MultipleChoiceRule = "ALL_OR_NOTHING" | "PARTIAL";
export type TrueFalseMethod = "PER_STATEMENT" | "COUNT_TABLE";
export type AnswerFormat = "INTEGER" | "DECIMAL" | "FRACTION" | "TEXT" | "CHEM_FORMULA" | "CHEM_EQUATION";

export type QuestionBody =
  | {
      type: "SINGLE_CHOICE";
      options: ChoiceOption[];
      /** `false`: giữ thứ tự phương án như soạn; thiếu hoặc `true`: đảo (vẫn theo `optionOrder` đã nhận). */
      shuffleOptions?: boolean;
    }
  | {
      type: "MULTIPLE_CHOICE";
      options: ChoiceOption[];
      scoringRule?: MultipleChoiceRule;
      shuffleOptions?: boolean;
    }
  | { type: "SHORT_ANSWER"; answers: string[]; inputHint?: string | null; answerFormat?: AnswerFormat | null }
  | { type: "CLOZE"; passage: string; blanks: ClozeBlank[] }
  | {
      type: "QUESTION_SET";
      /** Ngữ liệu chung (HTML; `span.blank` cho câu con dạng điền chỗ trống). */
      passage: string;
      /** Số bắt đầu đánh chỗ trống của ngữ liệu, 1–200 (BE spec mục 3.4). */
      blankNumberStart?: number;
      childIds?: string[];
    }
  | { type: "TRUE_FALSE"; statements: TrueFalseStatement[]; scoringMethod?: TrueFalseMethod; scoreTable?: number[] }
  | {
      type: "MATCHING";
      left: MatchingItem[];
      right: MatchingItem[];
      /** Cặp đúng mỗi mục trái; chỉ điền khi được thấy đáp án. */
      pairs?: MatchingPair[];
      /** Một lựa chọn dùng cho nhiều mục (`true`) hay một lần (`false`). */
      reuseRight?: boolean;
    }
  | {
      type: "ESSAY";
      gradingGuide?: string | null;
      rubric?: EssayCriterion[];
      sampleAnswer?: string | null;
      /** Số chữ tối thiểu, chỉ nhắc không chặn (BS 6). */
      wordLimit?: number | null;
    };

/** Một câu (hoặc câu con) đủ để dựng thân câu hỏi. */
export interface KitQuestion {
  id: string;
  /** Số câu (`display_no`); Điền khuyết là số của vị trí đầu (BS 5). */
  displayNo: number;
  /** Điểm tối đa của câu; mặc định 1. */
  points?: number;
  /** Đề bài (HTML). CLOZE bỏ trống: thân câu là chính `passage`. */
  stem?: string;
  body: QuestionBody;
  /**
   * Thứ tự phương án/lựa chọn cột phải của lượt (`attempt_item.option_order`): danh sách id theo thứ
   * tự hiển thị. Chữ cái (A, B, …) gán theo thứ tự này (LT 4, F22). Thiếu: theo thứ tự mảng.
   */
  optionOrder?: string[];
}

/** `KitQuestion` đã thu hẹp `body` theo một thành viên của `QuestionBody`. */
export type KitQuestionOf<B extends QuestionBody> = Omit<KitQuestion, "body"> & { body: B };

/* Câu trả lời theo dạng câu — đúng nội dung `answer` của LT 4. */
export type SingleChoiceAnswer = { optionId: string | null };
export type MultipleChoiceAnswer = { optionIds: string[] };
export type ShortAnswerAnswer = { text: string };
export type ClozeAnswer = { blanks: Record<string, string> };
export type TrueFalseAnswer = { statements: Record<string, boolean | null> };
export type MatchingAnswer = { pairs: Record<string, string | null> };
export type EssayAnswer = { html: string; wordCount: number };
/** Câu trả lời câu chùm: mỗi câu con một `answer` riêng (lưu ở dòng câu con). */
export type QuestionSetAnswer = { children: Record<string, AnswerValue> };
export type AnswerValue =
  | SingleChoiceAnswer
  | MultipleChoiceAnswer
  | ShortAnswerAnswer
  | ClozeAnswer
  | TrueFalseAnswer
  | MatchingAnswer
  | EssayAnswer
  | QuestionSetAnswer;

/** Kết quả của một ý (ô trống, mệnh đề, cặp ghép) trong `grading_detail`. */
export interface PartResult {
  /** `blankId`, `statementId` hoặc `leftId`. */
  key: string;
  outcome: QuestionOutcome;
  /** `null` khi dạng chấm không chia điểm theo ý (bảng theo số ý đúng). */
  earned?: number | null;
  max?: number | null;
}

/** Kết quả chấm tay của câu Tự luận (`essay_grade`, LT 10.2, BS 6). */
export interface EssayGrading {
  /** `REGRADING`: giữ điểm cũ kèm nhãn "Đang chấm lại" (LT 10.1). */
  state: "PENDING" | "GRADED" | "REGRADING";
  wordCount?: number;
  criteria?: { description: string; earned: number | null; max: number }[];
  comment?: string | null;
}

/**
 * Dữ liệu chế độ `review` của một câu (LT 10.2), đã ánh xạ từ `attempt_item`.
 * Mọi trường điểm tùy chọn: không có thì không hiện số (không hiện "0").
 */
export interface QuestionReview {
  /** Kết quả của câu; thiếu thì không hiện chip kết quả (ví dụ câu con chưa có dòng chấm riêng trong `children`). */
  outcome?: QuestionOutcome | null;
  /** Điểm đạt của câu; `null`/`undefined` thì không hiện điểm. */
  earned?: number | null;
  /** Điểm tối đa; mặc định `question.points` ?? 1. */
  max?: number;
  /** `false` khi `result_view` chỉ cho điểm + câu trả lời, chưa cho đáp án (mặc định `true`). */
  showAnswers?: boolean;
  /** Lời giải (HTML), chỉ khi `result_view = FULL_SOLUTION`. */
  solutionHtml?: string | null;
  /** Kết quả từng ý theo `key` (ô trống, ý, mục trái). */
  gradingDetail?: Record<string, PartResult>;
  /** Kết quả từng câu con theo id (Bộ câu hỏi). */
  children?: Record<string, QuestionReview>;
  /** Câu Tự luận: số chữ, điểm từng tiêu chí, nhận xét của người chấm. */
  essay?: EssayGrading;
}
