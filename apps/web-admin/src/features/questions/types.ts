import type {
  KnowledgeRef,
  MultipleChoiceRule,
  PersonRef,
  QuestionStatus,
  QuestionType,
} from "@/features/content/types";

/**
 * Câu hỏi trong ngân hàng (PBI-03), dữ liệu mẫu. Khi có OpenAPI của backend, đổi các kiểu dưới đây thành bí danh
 * của `components["schemas"][...]` và sửa chỗ lệch ở `api.ts`.
 */

export interface QuestionSummary {
  id: string;
  /** Mã câu hỏi hiển thị, ví dụ `CH-000123`. */
  code: string;
  type: QuestionType;
  status: QuestionStatus;
  /** Đề bài dạng chữ thuần, đã bỏ định dạng, dùng cho danh sách. */
  contentPreview: string;
  subjectCode: string;
  gradeCode: string;
  difficultyCode: string;
  /** Điểm tối đa của câu. Câu Tự luận: bằng `body.maxPoints`. */
  points: number;
  knowledge: KnowledgeRef[];
  /** Năng lực (V-ACT), mã trong `ContentReference.competencies`; bỏ trống hoặc `null`: không gắn. */
  competencyCode?: string | null;
  tagCodes: string[];
  hasSolution: boolean;
  /** Phiên bản hiện hành, tính từ 1. */
  version: number;
  /** Số bài thi (đã công bố hoặc đang soạn) đang dùng câu này; khác 0 thì không xóa hẳn được. */
  usedInExamCount: number;
  /** Mã nguồn khi nhập từ tệp (dùng để chống trùng); `null` với câu soạn tay. */
  sourceCode: string | null;
  createdBy: PersonRef;
  createdAt: string;
  updatedAt: string;
}

export interface ChoiceOption {
  id: string;
  content: string;
  correct: boolean;
}

export interface TrueFalseStatement {
  id: string;
  content: string;
  answer: boolean;
}

export interface FillInBlank {
  id: string;
  /** Các đáp án được chấp nhận, so khớp không phân biệt hoa thường. */
  acceptedAnswers: string[];
}

export interface DragItem {
  id: string;
  content: string;
}

export interface DropZone {
  id: string;
  label: string;
  /** Mục kéo đúng vào ô này. */
  correctItemId: string;
}

/** Một tiêu chí của hướng dẫn chấm câu Tự luận. */
export interface EssayCriterion {
  id: string;
  description: string;
  /** Điểm tối đa của tiêu chí; tổng các tiêu chí bằng `maxPoints` của câu. */
  points: number;
}

/**
 * Phần riêng theo dạng câu.
 *
 * - Nhiều đáp án: `scoringRule` (mặc định của kỳ thi, sửa được trên câu).
 * - Đúng/Sai: `scoreTable[i]` là điểm khi đúng `i + 1` ý, độ dài bằng số ý, phần tử cuối bằng điểm của câu
 *   (THPTQG 4 ý: 0,1 / 0,25 / 0,5 / 1).
 * - Tự luận: chấm tay theo `rubric`; `wordLimit` là số chữ gợi ý (đoạn văn 200 chữ), `null` khi không giới hạn.
 */
export type QuestionBody =
  | { type: "SINGLE_CHOICE"; options: ChoiceOption[]; shuffleOptions: boolean }
  | { type: "MULTIPLE_CHOICE"; options: ChoiceOption[]; shuffleOptions: boolean; scoringRule: MultipleChoiceRule }
  | { type: "TRUE_FALSE"; statements: TrueFalseStatement[]; scoreTable: number[] }
  | { type: "FILL_IN"; blanks: FillInBlank[] }
  | { type: "DRAG_DROP"; items: DragItem[]; zones: DropZone[] }
  | { type: "QUESTION_SET"; passage: string; childIds: string[] }
  | {
      type: "ESSAY";
      maxPoints: number;
      rubric: EssayCriterion[];
      sampleAnswer: string | null;
      wordLimit: number | null;
    };

export interface QuestionDetail extends QuestionSummary {
  /** Đề bài. Mockup lưu chữ thuần; trình soạn thảo đầy đủ (canvas trang 07) chưa làm. */
  content: string;
  body: QuestionBody;
  solution: string | null;
  /** Câu con của câu chùm, đủ nội dung (rỗng với dạng khác). */
  children: QuestionDetail[];
}

export interface ListQuestionsQuery {
  q?: string;
  subjectCode?: string;
  gradeCode?: string;
  difficultyCode?: string;
  type?: QuestionType;
  status?: QuestionStatus;
  competencyCode?: string;
  /** Lọc theo MAPID (id nút kiến thức); câu khớp khi gắn một trong các nút. */
  knowledgeIds?: string[];
  tagCode?: string;
  createdById?: string;
  hasSolution?: boolean;
  /** Ngày cập nhật, `YYYY-MM-DD`, tính cả hai đầu. */
  updatedFrom?: string;
  updatedTo?: string;
  /** Ngày tạo, `YYYY-MM-DD`, tính cả hai đầu (bộ lọc "Ngày tạo từ / đến" của canvas `NganHangCauHoi`). */
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
  /** `field,asc|desc`, ví dụ `updatedAt,desc`. */
  sort?: string;
}

/** Số câu theo trạng thái cho thanh trạng thái của danh sách (cùng bộ lọc, bỏ điều kiện trạng thái). */
export interface QuestionStatusCounts {
  total: number;
  byStatus: Record<QuestionStatus, number>;
}

/** Lý do một câu bị bỏ qua khi đổi trạng thái hàng loạt. */
export type QuestionStatusSkipReason = "NOT_READY" | "ALREADY_IN_STATUS" | "NOT_FOUND";

export interface BulkQuestionStatusRequest {
  ids: string[];
}

export interface BulkQuestionStatusResult {
  succeeded: QuestionSummary[];
  skipped: Array<{ id: string; code: string; reason: QuestionStatusSkipReason }>;
}

/** Một bài thi đang dùng câu hỏi và ảnh hưởng tới nguồn sinh đề khi ngừng sử dụng câu (canvas `NgungCauHoi`). */
export interface QuestionUsageExam {
  examId: string;
  examCode: string;
  examName: string;
  examStatus: "DRAFT" | "PUBLISHED" | "DISCONTINUED";
  servingVersion: number | null;
  /** Môn và dạng câu của khối dùng câu này; mã tổ hợp khi khối nằm trong một lựa chọn của phần tự chọn. */
  subjectCode: string;
  questionType: QuestionType;
  optionCode: string | null;
  /** Số câu khối cần ở ô Lớp × Độ khó của câu này; `null` khi không xác định được. */
  needed: number | null;
  /** Số câu Sẵn sàng khác còn lại cho ô đó sau khi ngừng câu này. */
  available: number;
}

export interface QuestionRetireImpact {
  questionId: string;
  exams: QuestionUsageExam[];
  /** Câu đã có trong lịch sử thi hoặc bài thi: chỉ ngừng sử dụng, không xóa hẳn (UniNote PBI-03 mục 9). */
  hardDeleteBlocked: boolean;
}
