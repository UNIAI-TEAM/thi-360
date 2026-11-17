import { QUESTION_TYPES, type KnowledgeRef, type PersonRef, type QuestionStatus, type QuestionType } from "@/features/content/types";
import type { ExamStatus } from "@/features/exams/types";
import type { QuestionBody, QuestionDetail } from "./types";

/**
 * Kiểu dữ liệu của soạn câu hỏi, chi tiết, nơi đang dùng và lịch sử phiên bản (UNI-2593), dữ liệu mẫu.
 * Khi có OpenAPI của backend, đổi thành bí danh của `components["schemas"][...]` và sửa chỗ lệch ở `editor-api.ts`.
 */

/** Giá trị `?type=` hợp lệ của `/questions/new`. */
export function isQuestionType(value: string | undefined | null): value is QuestionType {
  return (QUESTION_TYPES as readonly string[]).includes(value ?? "");
}

/** Dạng của câu con trong Bộ câu hỏi (D19): không nhận Bộ câu hỏi hay Điền khuyết. */
export type ChildQuestionType = Exclude<QuestionType, "QUESTION_SET" | "CLOZE">;

export const CHILD_QUESTION_TYPES = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "SHORT_ANSWER",
  "TRUE_FALSE",
  "MATCHING",
  "ESSAY",
] as const satisfies readonly ChildQuestionType[];

/** Giá trị `type` có làm câu con của Bộ câu hỏi được không (D19). */
export function isChildQuestionType(type: QuestionType): type is ChildQuestionType {
  return (CHILD_QUESTION_TYPES as readonly string[]).includes(type);
}

/**
 * Câu con gửi lên khi lưu Bộ câu hỏi. Phân loại để `null` là kế thừa của bộ; có giá trị là ghi đè riêng của câu con
 * (khối lớp, độ khó, kiến thức; PBI-03 mục 10).
 */
export interface SaveChildQuestionRequest {
  /** Id câu con đã có; `null` với câu con mới. */
  id: string | null;
  type: ChildQuestionType;
  content: string;
  body: QuestionBody;
  solution: string | null;
  points: number;
  gradeCode: string | null;
  difficultyCode: string | null;
  knowledgeIds: string[] | null;
}

/** Thân yêu cầu tạo hoặc sửa câu hỏi. Câu mới luôn ở trạng thái Nháp. */
export interface SaveQuestionRequest {
  type: QuestionType;
  subjectCode: string;
  gradeCode: string;
  difficultyCode: string;
  /** Dạng bài (`ContentReference.taskTypes`); `null` hoặc bỏ trống: không gắn. */
  taskTypeCode?: string | null;
  /** Năng lực (V-ACT); `null` hoặc bỏ trống: không gắn. Câu con lấy theo câu cha. */
  competencyCode?: string | null;
  /** Điểm của câu đơn. Bộ câu hỏi và Điền khuyết: máy chủ tính bằng tổng điểm thành phần, giá trị gửi lên bị bỏ qua. */
  points: number;
  knowledge: KnowledgeRef[];
  tagCodes: string[];
  /** Đề bài; Bộ câu hỏi: ngữ liệu chung. */
  content: string;
  solution: string | null;
  /** Lỗi sai thường gặp; `null`: không có. */
  commonMistakes: string | null;
  /** Id tệp đính kèm đã tải lên. */
  attachmentIds: string[];
  body: QuestionBody;
  /** Câu con của Bộ câu hỏi; rỗng với dạng khác. */
  children: SaveChildQuestionRequest[];
  /**
   * Chỉ khi tạo: định danh lần soạn (một lần mở biểu mẫu "Tạo câu hỏi") để gom các lần bấm Lưu và thử lại vào cùng
   * một khóa idempotency. Không gửi lên máy chủ; bỏ trống thì mỗi lần gọi là một lệnh mới.
   */
  saveIntentId?: string;
}

export interface UpdateQuestionRequest extends SaveQuestionRequest {
  /** `revision` của câu lúc mở màn (D13); lệch thì 412 `QUESTION_REVISION_CONFLICT`. */
  expectedRevision: number;
}

export interface UpdateQuestionResponse {
  question: QuestionDetail;
  /** `true` khi lần lưu tạo bản làm việc mới (câu đã Sẵn sàng hoặc Ngừng sử dụng), `false` khi sửa tại chỗ bản Nháp. */
  versionCreated: boolean;
}

export interface RestoreQuestionVersionRequest {
  /** Phiên bản cần chép lại thành bản nháp. */
  version: number;
  expectedRevision: number;
}

/** Phần nội dung của một phiên bản câu hỏi (những gì so sánh được giữa hai phiên bản). */
export type QuestionSnapshot = Pick<
  QuestionDetail,
  | "subjectCode"
  | "gradeCode"
  | "difficultyCode"
  | "competencyCode"
  | "taskTypeCode"
  | "points"
  | "knowledge"
  | "tagCodes"
  | "content"
  | "body"
  | "solution"
  | "commonMistakes"
  | "attachmentIds"
  | "children"
>;

/**
 * Trạng thái của một phiên bản trong lịch sử (canvas `CauHoiPhienBan`):
 * - `WORKING`: bản Nháp đang sửa, chưa dùng cho lượt thi nào;
 * - `CURRENT`: bản Sẵn sàng hiện hành, dùng cho lượt thi mới;
 * - `SUPERSEDED`: đã được bản sau thay thế, lượt thi cũ vẫn giữ bản này;
 * - `RETIRED`: bản cuối của câu đã Ngừng sử dụng.
 */
export type QuestionVersionState = "WORKING" | "CURRENT" | "SUPERSEDED" | "RETIRED";

export interface QuestionVersion {
  version: number;
  state: QuestionVersionState;
  savedAt: string;
  savedBy: PersonRef;
  /** Số lượt thi đã dùng phiên bản này; khác 0 thì phiên bản không sửa được nữa. */
  attemptCount: number;
  snapshot: QuestionSnapshot;
}

export interface QuestionVersionList {
  questionId: string;
  /** Phiên bản mới nhất (bản đang sửa hoặc bản hiện hành). */
  latestVersion: number;
  status: QuestionStatus;
  examCount: number;
  attemptCount: number;
  /** Mới nhất trước. */
  versions: QuestionVersion[];
}

export interface QuestionUsageVersionAttempts {
  version: number;
  attemptCount: number;
}

/** Một vị trí lấy câu trong bài thi: phần, lựa chọn (tổ hợp, nếu có) và khối. Trường trống khi API không trả. */
export interface QuestionUsageBlock {
  partName: string;
  optionCode: string | null;
  optionName: string;
  blockName: string;
}

/** Một bài thi có khối câu hỏi lấy được câu này. */
export interface QuestionUsageExam {
  examId: string;
  examCode: string;
  examName: string;
  examStatus: ExamStatus;
  /** Tên phần thi, ví dụ "Phần 1". */
  partName: string;
  subjectCode: string;
  /** Tên khối câu hỏi lấy được câu này. */
  blockName: string;
  /** Mọi khối (phần, lựa chọn, khối) của bài lấy được câu này; thiếu khi dữ liệu không chia theo khối. */
  blocks?: QuestionUsageBlock[];
  /** Số lượt thi đã dùng từng phiên bản của câu này trong bài thi, phiên bản mới trước. */
  attemptsByVersion: QuestionUsageVersionAttempts[];
}

export interface QuestionUsage {
  questionId: string;
  examCount: number;
  attemptCount: number;
  exams: QuestionUsageExam[];
}
