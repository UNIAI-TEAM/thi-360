import type { KnowledgeRef, PersonRef, QuestionStatus, QuestionType } from "@/features/content/types";

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
  points: number;
  knowledge: KnowledgeRef[];
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

/** Phần riêng theo dạng câu. */
export type QuestionBody =
  | { type: "SINGLE_CHOICE"; options: ChoiceOption[]; shuffleOptions: boolean }
  | { type: "MULTIPLE_CHOICE"; options: ChoiceOption[]; shuffleOptions: boolean }
  | { type: "TRUE_FALSE"; statements: TrueFalseStatement[] }
  | { type: "FILL_IN"; blanks: FillInBlank[] }
  | { type: "DRAG_DROP"; items: DragItem[]; zones: DropZone[] }
  | { type: "QUESTION_SET"; passage: string; childIds: string[] };

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
  /** Lọc theo MAPID (id nút kiến thức); câu khớp khi gắn một trong các nút. */
  knowledgeIds?: string[];
  tagCode?: string;
  createdById?: string;
  hasSolution?: boolean;
  /** Ngày cập nhật, `YYYY-MM-DD`, tính cả hai đầu. */
  updatedFrom?: string;
  updatedTo?: string;
  page?: number;
  size?: number;
  /** `field,asc|desc`, ví dụ `updatedAt,desc`. */
  sort?: string;
}
