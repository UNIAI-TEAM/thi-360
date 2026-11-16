import type { PersonRef, QuestionType } from "@/features/content/types";
import type { ExamCheckStatus, ExamPart, ExamVersion, MatrixMode } from "./types";

/**
 * Kiểu của Kiểm tra cấu hình, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11), dữ liệu mẫu.
 * Khi có OpenAPI của backend, đổi thành bí danh của `components["schemas"][...]`.
 */

/* ---------- Nguồn câu hỏi ---------- */

/** Số câu Sẵn sàng khớp khối (môn, dạng, phạm vi MAPID, nhãn) trong một ô Lớp × Độ khó, trước khi xử lý trùng. */
export interface CellAvailability {
  gradeCode: string;
  difficultyCode: string;
  count: number;
}

/** Nguồn dùng chung giữa các nhóm kiến thức: cần thời gian xử lý `solveSeconds` mới xác nhận được đủ câu riêng. */
export interface SourceOverlap {
  nodeIds: string[];
  sharedCount: number;
  solveSeconds: number;
}

export interface BlockAvailability {
  blockId: string;
  cells: CellAvailability[];
  /** Câu chùm: số bộ nguyên vẹn khớp khối; dạng khác để `null`. */
  setCount: number | null;
  overlap: SourceOverlap | null;
  /** Số câu Sẵn sàng khớp khối theo từng năng lực (V-ACT); khối không dùng chiều năng lực bỏ trống. */
  competencies?: Array<{ code: string; count: number }>;
  /**
   * Mã các câu nguồn có quy tắc chấm khác quy tắc mong đợi của khối (bảng điểm Đúng/Sai, quy tắc nhiều đáp án). Bỏ
   * trống: khối không đặt quy tắc mong đợi.
   */
  scoringMismatch?: string[];
  /** Khối Tự luận: mã các câu nguồn chưa có tiêu chí chấm. */
  missingRubric?: string[];
}

/** Nguồn theo từng khối, khóa là id khối. */
export interface ExamAvailability {
  blocks: Record<string, BlockAvailability>;
}

/* ---------- Đường lựa chọn ---------- */

/** Một lựa chọn của phần tự chọn nằm trong đường; `name` là tên môn hoặc tên tổ hợp (dữ liệu danh mục). */
export interface PathChoice {
  partId: string;
  optionId: string;
  kind: "SUBJECT" | "COMBINATION" | "BRANCH";
  code: string;
  name: string;
}

/** Một đường lựa chọn = một cách ghép các phần tự chọn. Bài không có phần tự chọn có đúng một đường (`choices` rỗng). */
export interface ExamPathRef {
  pathId: string;
  choices: PathChoice[];
}

/* ---------- Kết quả kiểm tra ---------- */

export type PathCheckResult = "VALID" | "INVALID" | "UNCONFIRMED";
export type BlockCheckResult = "FOUND" | "NOT_FOUND" | "UNCONFIRMED";

/** Tám nhóm kiểm tra của UniNote PBI-04 mục 9.2. */
export const CHECK_GROUPS = [
  "INFO",
  "STRUCTURE",
  "MATRIX",
  "SOURCE",
  "QUESTION_SET",
  "OVERLAP",
  "POINTS",
  "DISPLAY",
] as const;
export type CheckGroup = (typeof CHECK_GROUPS)[number];

export type CheckIssueCode =
  | "INFO_MISSING"
  | "OPTION_EMPTY"
  | "PATH_TOTAL_MISMATCH"
  | "PART_TOTAL_MISMATCH"
  | "SECTION_TOTAL_MISMATCH"
  | "MATRIX_TOTAL_MISMATCH"
  | "MATRIX_NEGATIVE"
  | "PERCENT_TOTAL_INVALID"
  | "SET_SIZE_MISMATCH"
  | "CELL_SHORTAGE"
  | "DIMENSION_SHORTAGE"
  | "MATRIX_INFEASIBLE"
  | "SET_SHORTAGE"
  | "OVERLAP_UNCONFIRMED"
  | "YEARS_UNCONFIRMED"
  | "OPTION_UNCONFIRMED"
  | "PART_POINTS_MISMATCH"
  | "POINTS_TOTAL_MISMATCH"
  | "COMPETENCY_SHORTAGE"
  | "RUBRIC_MISSING"
  | "SCORING_MISMATCH";

/**
 * Mức của vấn đề. `ERROR`: lỗi xác định (Không hợp lệ). `UNCONFIRMED`: chưa tìm được phương án trong giới hạn xử lý.
 * `WARNING`: cần xem lại nhưng không chặn công bố (câu nguồn có quy tắc chấm khác khối).
 */
export type CheckIssueSeverity = "ERROR" | "UNCONFIRMED" | "WARNING";

/**
 * Một vấn đề. `ERROR` là lỗi xác định (Không hợp lệ); `UNCONFIRMED` là chưa tìm được phương án trong giới hạn xử lý.
 * Trường không áp dụng để `null`; chữ hiển thị dựng từ `code` và các trường này lúc hiển thị.
 */
export interface CheckIssue {
  key: string;
  code: CheckIssueCode;
  severity: CheckIssueSeverity;
  group: CheckGroup;
  partId: string | null;
  partName: string | null;
  sectionId: string | null;
  subjectCode: string | null;
  blockId: string | null;
  blockName: string | null;
  /** Lựa chọn (môn hoặc tổ hợp) chứa khối; `null` với phần cố định. */
  optionId: string | null;
  gradeCode: string | null;
  difficultyCode: string | null;
  dimension: "GRADE" | "DIFFICULTY" | "COMPETENCY" | null;
  /** Năng lực của chỉ tiêu (chiều năng lực, V-ACT). */
  competencyCode: string | null;
  required: number | null;
  available: number | null;
  timeLimitSeconds: number | null;
  /** Mã câu nguồn liên quan (quy tắc chấm khác khối, thiếu tiêu chí chấm); `null` khi không áp dụng. */
  questionCodes: string[] | null;
}

/** Số câu cần của một ô (ma trận kết hợp) sau khi quy đổi tỷ lệ. */
export interface RequiredCell {
  gradeCode: string;
  difficultyCode: string;
  required: number;
}

export interface RequiredDimension {
  code: string;
  required: number;
}

/** Chỉ tiêu nguyên của khối: `COMBINED` dùng `cells`, `SEPARATE` dùng `byGrade` + `byDifficulty`. */
export interface BlockRequirement {
  mode: MatrixMode;
  cells: RequiredCell[];
  byGrade: RequiredDimension[];
  byDifficulty: RequiredDimension[];
  /** Chiều năng lực (V-ACT), kiểm tra thêm bên cạnh Lớp × Độ khó; rỗng khi khối không dùng. */
  byCompetency: RequiredDimension[];
}

export interface BlockCheck {
  partId: string;
  partName: string;
  partOrder: number;
  sectionId: string;
  subjectCode: string;
  optionId: string | null;
  blockId: string;
  blockName: string;
  questionType: QuestionType;
  required: number;
  setCount: number | null;
  setSize: number | null;
  /** Tổng nguồn phù hợp (câu); câu chùm thêm `availableSets`. */
  available: number;
  availableSets: number | null;
  requirement: BlockRequirement | null;
  result: BlockCheckResult;
  issues: CheckIssue[];
}

export interface PathCheck extends ExamPathRef {
  result: PathCheckResult;
  partIds: string[];
  totalQuestions: number;
  /** Bài dùng thời lượng chung: thời lượng chung; không thì tổng thời lượng các phần của đường. */
  totalMinutes: number;
  totalPoints: number;
  blocks: BlockCheck[];
  /** Mọi vấn đề của đường (cấu trúc và của các khối). */
  issues: CheckIssue[];
}

export interface CheckGroupResult {
  group: CheckGroup;
  status: "PASS" | "FAIL" | "WARN";
  issues: CheckIssue[];
}

/** Kết quả thuần của `checkExam` (không có thời điểm, thời gian chạy). */
export interface ExamCheckOutcome {
  status: Extract<ExamCheckStatus, "VALID" | "INVALID">;
  /** Số vấn đề khác nhau trên mọi đường (khối dùng chung giữa các đường chỉ tính một lần). */
  issueCount: number;
  paths: PathCheck[];
  groups: CheckGroupResult[];
  partCount: number;
  totalPoints: number;
  /** Số câu in trên đề (phần tự chọn theo nhánh in mọi nhánh); bằng số câu học viên làm khi không có nhánh. */
  printedQuestions: number;
  /** Thang điểm của bài; `null` khi kỳ thi chưa đặt thang. */
  maxScore: number | null;
}

export interface ExamCheckResult extends ExamCheckOutcome {
  examId: string;
  checkedAt: string;
  durationSeconds: number;
  timeLimitSeconds: number;
}

/* ---------- Đề xem trước ---------- */

export interface PreviewOption {
  id: string;
  content: string;
  correct: boolean;
}

/** Một câu (câu đơn hoặc câu con của bộ) trong đề xem trước. `simulated`: câu của nguồn mô phỏng, không có nội dung. */
export interface PreviewQuestion {
  number: number;
  questionId: string;
  code: string;
  version: number | null;
  type: QuestionType;
  simulated: boolean;
  content: string | null;
  gradeCode: string;
  difficultyCode: string;
  knowledge: { nodeId: string; code: string; name: string } | null;
  points: number;
  options: PreviewOption[] | null;
  /** Đáp án dạng chữ (điền đáp án, đúng sai); chỉ hiện trong chế độ quản trị. */
  answer: string | null;
  solution: string | null;
  /** Câu Tự luận: tiêu chí chấm (chấm tay); dạng khác để `null`. */
  rubric: Array<{ id: string; description: string; points: number }> | null;
}

export type PreviewItem =
  | { kind: "QUESTION"; question: PreviewQuestion }
  | {
      kind: "SET";
      setId: string;
      code: string;
      version: number | null;
      simulated: boolean;
      passage: string | null;
      children: PreviewQuestion[];
    };

export interface PreviewAllocationCell {
  gradeCode: string;
  difficultyCode: string;
  /** Số câu yêu cầu của ô (ma trận kết hợp); ma trận riêng để `null`, yêu cầu nằm ở tổng dòng và cột. */
  required: number | null;
  generated: number;
}

export interface PreviewBlock {
  blockId: string;
  blockName: string;
  subjectCode: string;
  questionType: QuestionType;
  /** Khối Tự luận: chấm tay, điểm có sau khi người chấm xong. */
  manualGrading: boolean;
  pointsPerQuestion: number;
  firstNumber: number;
  lastNumber: number;
  questionCount: number;
  setCount: number | null;
  setSize: number | null;
  requirement: BlockRequirement;
  allocation: PreviewAllocationCell[];
  items: PreviewItem[];
}

export interface PreviewPart {
  partId: string;
  name: string;
  order: number;
  /** `null` khi bài dùng thời lượng chung. */
  durationMinutes: number | null;
  firstNumber: number;
  lastNumber: number;
  /** Tổng điểm các câu của phần trên đề đã sinh. */
  points: number;
  /** Điểm quy định của phần (BCA 25 / 60 / 15); `null` khi phần không chia điểm. */
  maxPoints: number | null;
  blocks: PreviewBlock[];
}

export type PreviewCheckCode = "NO_DUPLICATES" | "SETS_INTACT" | "FIXED_BLOCK_ORDER" | "TOTAL_POINTS";

export interface PreviewCheck {
  code: PreviewCheckCode;
  status: "PASS" | "FAIL" | "NOT_APPLICABLE";
  value: number | null;
}

export interface PreviewKnowledgeStat {
  nodeId: string;
  code: string;
  name: string;
  subjectCode: string;
  count: number;
}

export interface PreviewPaper {
  totalQuestions: number;
  totalPoints: number;
  /** Bài dùng thời lượng chung: thời lượng chung; không thì tổng thời lượng các phần. */
  totalMinutes: number;
  /** Bài dùng thời lượng chung (các phần không có thời lượng riêng). */
  sharedDuration: boolean;
  /** Có khối Tự luận: điểm phần tự luận có sau khi chấm tay. */
  hasManualGrading: boolean;
  parts: PreviewPart[];
  checks: PreviewCheck[];
  knowledgeStats: PreviewKnowledgeStat[];
}

interface PreviewBase {
  examId: string;
  path: ExamPathRef;
  /** Phiên bản đã công bố dùng để sinh; `null` là bản làm việc. */
  version: number | null;
  seed: number;
  generatedAt: string;
}

export type PreviewResult =
  | (PreviewBase & { status: "GENERATED"; paper: PreviewPaper })
  | (PreviewBase & { status: "FAILED"; reason: "INVALID" | "UNCONFIRMED"; issues: CheckIssue[] });

/* ---------- Phiên bản và công bố ---------- */

/** Một dòng lịch sử công bố: phiên bản kèm đường đã công bố và đường còn đang phục vụ. */
export interface ExamVersionRow extends ExamVersion {
  version: number;
  publishedPaths: ExamPathRef[];
  /** Đường lựa chọn mà lượt thi mới còn dùng phiên bản này (công bố một phần giữ bản cũ cho đường còn lại). */
  servingPaths: ExamPathRef[];
}

export type ExamChangeKind =
  | "OPTION_ADDED"
  | "OPTION_REMOVED"
  | "BLOCK_ADDED"
  | "BLOCK_REMOVED"
  | "BLOCK_CHANGED"
  | "MATRIX_CHANGED"
  | "ORDER_RULE_CHANGED"
  | "QUESTIONS_REPLACED";

/** Một thay đổi của bản làm việc so với bản đang phục vụ. `where`, `before`, `after` là chữ dữ liệu. */
export interface ExamChange {
  id: string;
  kind: ExamChangeKind;
  where: string;
  before: string | null;
  after: string | null;
  /** Khối cần mở để xem chỗ thay đổi (trang Cấu trúc và ma trận). */
  blockId: string | null;
  /** Cảnh báo kèm theo, ví dụ đường mới chưa xác nhận được khả năng sinh. */
  warning: "UNCONFIRMED" | "INVALID" | null;
}

export interface ExamWorkingCopy {
  editedBy: PersonRef;
  savedAt: string;
  baseVersion: number | null;
  note: string | null;
  check: { status: ExamCheckStatus; issueCount: number; checkedAt: string | null };
  /** Đường của bản làm việc chưa công bố (công bố một phần thì còn các đường chưa chọn). */
  unpublishedPaths: ExamPathRef[];
}

export interface ExamVersionList {
  examId: string;
  /** `null` khi không có bản sửa chưa công bố. */
  workingCopy: ExamWorkingCopy | null;
  changes: ExamChange[];
  items: ExamVersionRow[];
}

export interface ExamVersionDetail {
  examId: string;
  row: ExamVersionRow;
  /** Cấu hình đã chụp lúc công bố (chỉ đọc). */
  parts: ExamPart[];
}

export interface RunExamCheckRequest {
  examId: string;
  /** Chạy với giới hạn xử lý dài hơn cho đường "Chưa xác nhận được". */
  extendedTimeLimit?: boolean;
}

export interface GeneratePreviewRequest {
  examId: string;
  pathId: string;
  seed: number;
  version?: number | null;
}

export interface PublishExamRequest {
  examId: string;
  pathIds: string[];
  note: string | null;
}

export interface PublishExamResponse {
  examId: string;
  version: number;
  publishedPathIds: string[];
  /** Đường không công bố lần này, lượt thi mới vẫn dùng phiên bản cũ (số phiên bản cũ). */
  keptPaths: Array<{ pathId: string; version: number }>;
}
