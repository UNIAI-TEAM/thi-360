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

/** Dạng của câu con trong câu chùm: mọi dạng trừ câu chùm (Tự luận dùng cho đọc hiểu Ngữ văn THPTQG). */
export type ChildQuestionType = Exclude<QuestionType, "QUESTION_SET">;

export const CHILD_QUESTION_TYPES = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN",
  "DRAG_DROP",
  "ESSAY",
] as const satisfies readonly ChildQuestionType[];

/** Câu con gửi lên khi lưu câu chùm. Phân loại (môn, khối, mức độ, kiến thức, nhãn) lấy theo câu cha. */
export interface SaveChildQuestionRequest {
  /** Id câu con đã có; `null` với câu con mới. */
  id: string | null;
  type: ChildQuestionType;
  content: string;
  body: QuestionBody;
  solution: string | null;
  points: number;
}

/** Thân yêu cầu tạo hoặc sửa câu hỏi. Câu mới luôn ở trạng thái Nháp. */
export interface SaveQuestionRequest {
  type: QuestionType;
  subjectCode: string;
  gradeCode: string;
  difficultyCode: string;
  /** Năng lực (V-ACT); `null` hoặc bỏ trống: không gắn. Câu con lấy theo câu cha. */
  competencyCode?: string | null;
  /** Điểm của câu đơn. Câu chùm: máy chủ tính bằng tổng điểm câu con, giá trị gửi lên bị bỏ qua. */
  points: number;
  knowledge: KnowledgeRef[];
  tagCodes: string[];
  /** Đề bài; câu chùm: đoạn dẫn chung. */
  content: string;
  solution: string | null;
  body: QuestionBody;
  /** Câu con của câu chùm; rỗng với dạng khác. */
  children: SaveChildQuestionRequest[];
}

export interface UpdateQuestionRequest extends SaveQuestionRequest {
  /** Phiên bản đang sửa (khóa lạc quan); khác phiên bản hiện tại thì trả 409 `QUESTION_VERSION_CONFLICT`. */
  expectedVersion: number;
}

export interface UpdateQuestionResponse {
  question: QuestionDetail;
  /** `true` khi lần lưu tạo phiên bản mới (câu đã Sẵn sàng hoặc Ngừng sử dụng), `false` khi sửa tại chỗ bản Nháp. */
  versionCreated: boolean;
}

export interface RestoreQuestionVersionRequest {
  /** Phiên bản cần chép lại thành bản nháp. */
  version: number;
  expectedVersion: number;
}

/** Phần nội dung của một phiên bản câu hỏi (những gì so sánh được giữa hai phiên bản). */
export type QuestionSnapshot = Pick<
  QuestionDetail,
  | "subjectCode"
  | "gradeCode"
  | "difficultyCode"
  | "competencyCode"
  | "points"
  | "knowledge"
  | "tagCodes"
  | "content"
  | "body"
  | "solution"
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
  /** Số lượt thi đã dùng từng phiên bản của câu này trong bài thi, phiên bản mới trước. */
  attemptsByVersion: QuestionUsageVersionAttempts[];
}

export interface QuestionUsage {
  questionId: string;
  examCount: number;
  attemptCount: number;
  exams: QuestionUsageExam[];
}
