import type {
  KnowledgeRef,
  MultipleChoiceRule,
  PersonRef,
  QuestionStatus,
  QuestionType,
  TrueFalseMethod,
} from "@/features/content/types";
import type { ApiPage } from "@/lib/api/page";
import type { ReadinessIssue } from "./readiness";

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
  /** Điểm của câu (mặc định 1, lớn hơn 0). Bộ câu hỏi và Điền khuyết: tổng điểm thành phần, chỉ đọc. */
  points: number;
  knowledge: KnowledgeRef[];
  /** Năng lực (V-ACT), mã trong `ContentReference.competencies`; bỏ trống hoặc `null`: không gắn. */
  competencyCode?: string | null;
  /** Dạng bài (`ContentReference.taskTypes`); `null`: chưa gắn. */
  taskTypeCode: string | null;
  tagCodes: string[];
  hasSolution: boolean;
  /** Số phiên bản nội dung đang hiển thị, tính từ 1 (bản đang phục vụ; câu chưa từng Sẵn sàng: bản Nháp). Không dùng làm khóa lạc quan: xem `revision`. */
  version: number;
  /** Số bản Nháp đang sửa của câu đã Sẵn sàng hoặc Ngừng sử dụng; `null`/bỏ trống: không có bản Nháp riêng. Lưu tiếp thì ghi đè bản này. */
  workingVersion?: number | null;
  /** Khóa lạc quan (D13): tăng ở mọi lần ghi, kể cả đổi trạng thái. Mọi lệnh ghi gửi lại làm `expectedRevision`. */
  revision: number;
  /** Số bài thi (đã công bố hoặc đang soạn) đang dùng câu này; khác 0 thì không xóa hẳn được. */
  usedInExamCount: number;
  /** Mã nguồn khi nhập từ tệp (dùng để chống trùng); `null` với câu soạn tay. */
  sourceCode: string | null;
  createdBy: PersonRef;
  createdAt: string;
  updatedAt: string;
}

/**
 * Dòng của danh sách: câu hỏi kèm gợi ý điều kiện Sẵn sàng đầu tiên còn thiếu (câu Nháp, Ngừng sử dụng), để danh sách
 * nêu ngay chỗ cần sửa như canvas `NganHangCauHoi`. Bên tạo dữ liệu khác có thể bỏ trống.
 */
export interface QuestionListItem extends QuestionSummary {
  readinessHint?: ReadinessIssue | null;
}

export type PageResponseQuestionSummary = ApiPage<QuestionListItem>;

export interface ChoiceOption {
  id: string;
  content: string;
  correct: boolean;
}

export interface TrueFalseStatement {
  id: string;
  content: string;
  answer: boolean;
  /** Điểm của ý khi `scoringMethod = PER_STATEMENT`; `null` khi chấm theo bảng. */
  points: number | null;
}

/** Ô trống của câu Điền khuyết; vị trí đánh dấu `[[id]]` trong ngữ liệu. */
export interface ClozeBlank {
  id: string;
  /** Đáp án chuẩn đứng đầu, sau là các đáp án được chấp nhận (khớp đúng sau chuẩn hóa, B7). */
  answers: string[];
  /** Điểm của ô, lớn hơn 0, mặc định 1. */
  points: number;
}

export interface MatchingItem {
  id: string;
  content: string;
}

export interface MatchingPair {
  leftId: string;
  rightId: string;
  points: number;
}

/** Một tiêu chí của hướng dẫn chấm câu Tự luận (tùy chọn, D20). */
export interface EssayCriterion {
  id: string;
  description: string;
  /** Điểm tối đa của tiêu chí; có tiêu chí thì tổng bằng điểm của câu. */
  points: number;
}

/** Dấu vị trí của ô trống trong ngữ liệu Điền khuyết: `[[b1]]`. */
export function clozeMarker(blankId: string): string {
  return `[[${blankId}]]`;
}

/**
 * Phần riêng theo dạng câu (spec 4.3, 9.2).
 *
 * - Một đáp án, Nhiều đáp án: `options`; Nhiều đáp án thêm `scoringRule`. Phương án luôn được đảo khi sinh đề, không có cờ.
 * - Trả lời ngắn: `answers` (đáp án chuẩn đứng đầu, tối đa 20), `inputHint` hướng dẫn nhập cho học viên.
 * - Điền khuyết: `passage` có các dấu `[[id]]`, `blanks` mỗi ô một điểm.
 * - Đúng/Sai: `scoringMethod`; `PER_STATEMENT` dùng `statements[].points`, `COUNT_TABLE` dùng `scoreTable` (độ dài bằng
 *   số ý, không giảm, phần tử cuối bằng điểm của câu; THPTQG 4 ý: 0,1 / 0,25 / 0,5 / 1).
 * - Ghép đôi: hai cột `left`, `right` (nhiều lựa chọn hơn mục cũng được), `pairs` nối mục trái với một lựa chọn phải.
 * - Tự luận: `gradingGuide` bắt buộc khi Sẵn sàng, `rubric` tùy chọn, `wordLimit` để trống hoặc lớn hơn 0.
 * - Bộ câu hỏi: ngữ liệu chung và id câu con; không nhận Bộ câu hỏi hay Điền khuyết làm câu con (D19).
 */
export type QuestionBody =
  | { type: "SINGLE_CHOICE"; options: ChoiceOption[] }
  | { type: "MULTIPLE_CHOICE"; options: ChoiceOption[]; scoringRule: MultipleChoiceRule }
  | { type: "SHORT_ANSWER"; answers: string[]; inputHint: string | null }
  | { type: "CLOZE"; passage: string; blanks: ClozeBlank[] }
  | { type: "QUESTION_SET"; passage: string; childIds: string[] }
  | { type: "TRUE_FALSE"; statements: TrueFalseStatement[]; scoringMethod: TrueFalseMethod; scoreTable: number[] }
  | {
      type: "MATCHING";
      left: MatchingItem[];
      right: MatchingItem[];
      pairs: MatchingPair[];
      /** Một lựa chọn dùng cho nhiều mục trái (`true`) hay một lần (`false`). */
      reuseRight: boolean;
      shuffleLeft: boolean;
      shuffleRight: boolean;
    }
  | {
      type: "ESSAY";
      /** Hướng dẫn chấm (HTML đã lọc); bắt buộc khi Sẵn sàng (D20). */
      gradingGuide: string;
      rubric: EssayCriterion[];
      sampleAnswer: string | null;
      wordLimit: number | null;
    };

export interface QuestionDetail extends QuestionSummary {
  /** Đề bài. Mockup lưu chữ thuần; trình soạn HTML là việc của FE-4. */
  content: string;
  body: QuestionBody;
  solution: string | null;
  /** Lỗi sai thường gặp (HTML đã lọc); `null`: không có. */
  commonMistakes: string | null;
  /** Tệp đính kèm của câu (id tài nguyên); rỗng: không có. */
  attachmentIds: string[];
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
  /** Dạng bài (`ContentReference.taskTypes`). */
  taskTypeCode?: string;
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
