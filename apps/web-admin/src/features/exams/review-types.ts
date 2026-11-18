import type { PersonRef, QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import type { ExamCheckStatus, ExamPart, ExamSectionState, ExamVersion, MatrixMode } from "./types";

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

/** Số câu Sẵn sàng của cả môn (hợp nguồn các nhóm, mỗi câu một lần): theo ô Lớp × Độ khó và theo dạng trả lời. */
export interface SectionAvailability {
  sectionId: string;
  total: number;
  cells: CellAvailability[];
  byAnswerType: Array<{ answerType: QuestionType; count: number }>;
}

/** Nguồn theo từng khối (khóa là id khối) và theo từng môn (khóa là id môn trong cấu trúc). */
export interface ExamAvailability {
  blocks: Record<string, BlockAvailability>;
  /** Bỏ trống: mọi chỉ tiêu cấp môn coi như không có nguồn. */
  sections?: Record<string, SectionAvailability>;
}

/* ---------- Đường lựa chọn ---------- */

/** Một lựa chọn của phần tự chọn nằm trong đường; `name` là tên môn hoặc tên tổ hợp (dữ liệu danh mục). */
export interface PathChoice {
  partId: string;
  optionId: string;
  kind: "SUBJECT" | "COMBINATION" | "BRANCH" | "SUBJECT_CHOICE";
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

/** Mã vấn đề của kiểm tra cấu hình và xem trước. Mã do backend thêm (BE-5b:13, BE-5b:17) nằm cuối danh sách. */
export const CHECK_ISSUE_CODES = [
  "INFO_MISSING",
  "OPTION_EMPTY",
  "PATH_TOTAL_MISMATCH",
  "PART_TOTAL_MISMATCH",
  "SECTION_TOTAL_MISMATCH",
  "MATRIX_TOTAL_MISMATCH",
  "MATRIX_NEGATIVE",
  "PERCENT_TOTAL_INVALID",
  "SET_SIZE_MISMATCH",
  "CELL_SHORTAGE",
  "DIMENSION_SHORTAGE",
  "MATRIX_INFEASIBLE",
  "SET_SHORTAGE",
  "OVERLAP_UNCONFIRMED",
  "YEARS_UNCONFIRMED",
  "OPTION_UNCONFIRMED",
  "PART_POINTS_MISMATCH",
  "POINTS_TOTAL_MISMATCH",
  "COMPETENCY_SHORTAGE",
  "RUBRIC_MISSING",
  "SCORING_MISMATCH",
  "ANSWER_QUOTA_TOTAL_MISMATCH",
  "SUBJECT_MATRIX_TOTAL_MISMATCH",
  "GROUP_ANSWER_TYPE_MISSING",
  "SUBJECT_CHOICE_COUNTS_INVALID",
  "ANSWER_TYPE_SHORTAGE",
  "SUBJECT_QUOTA_INFEASIBLE",
  "PREVIEW_SUBJECTS_INVALID",
  "MATRIX_NON_INTEGER",
  "MATRIX_INCOMPLETE",
  "PART_EMPTY",
  "SHARED_PART_DURATION",
  "ANSWER_QUOTA_DUPLICATE",
  "ATTACHMENT_MISSING",
  "COMBINATION_LIST_CHANGED",
  "COMBINATION_SUBJECT_MISSING",
  "QUESTION_NOT_PLAYABLE",
  "REQUIRED_BASIS_UNSUPPORTED",
  "SECTION_INCOMPLETE",
  "SUBJECT_CHOICE_TRIM_IMPOSSIBLE",
  "TOPIC_QUOTA_INFEASIBLE",
  "TOPIC_QUOTA_NESTED",
  "ITEM_SHAPE_SHORTAGE",
  "ANSWER_FORMAT_SHORTAGE",
  "CHILD_CONDITION_SHORTAGE",
  "SLOT_SOURCE_SHORTAGE",
  "DISTRIBUTION_DEVIATION",
  "EXAM_QUOTA_DEVIATION",
  "MANUAL_GRADING_REQUIRED",
  "OMITTED_GROUPS_PRESENT",
] as const;
export type CheckIssueCode = (typeof CHECK_ISSUE_CODES)[number];

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
  /** Dạng trả lời của chỉ tiêu cấp môn (`ANSWER_TYPE_SHORTAGE`). */
  answerType: QuestionType | null;
  /** Số câu của môn trong trường hợp lỗi (môn của `SUBJECT_CHOICE` nhận 16 hoặc 17); `null` khi môn có số câu cố định. */
  subjectCount: number | null;
  required: number | null;
  available: number | null;
  timeLimitSeconds: number | null;
  /** Mã câu nguồn liên quan (quy tắc chấm khác khối, thiếu tiêu chí chấm); `null` khi không áp dụng. */
  questionCodes: string[] | null;
  /** Các mã bài thi bị lỗi ảnh hưởng (phần theo mã, R-24); `null` khi lỗi không gắn mã. */
  affectedCodes?: string[] | null;
  /** Vị trí chùm (từ 1) của lỗi nguồn theo chùm (`SLOT_SOURCE_SHORTAGE`); `null`: lỗi không gắn chùm. */
  slotIndex?: number | null;
  /** Vị trí câu con (từ 1) của lỗi điều kiện câu con (`CHILD_CONDITION_SHORTAGE`); `null`: không gắn vị trí. */
  childPosition?: number | null;
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
  questionType: QuestionType | null;
  /** Số câu của nhóm trong trường hợp đang kiểm (nhóm "lấy phần còn lại" nhận phần chênh của môn). */
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

/** Kết quả một cách chọn môn của đường có `SUBJECT_CHOICE`: các môn, số câu từng môn và kết luận. */
export interface PathSelectionCheck {
  subjectCodes: string[];
  counts: number[];
  result: PathCheckResult;
  issues: CheckIssue[];
}

/** Chỉ tiêu cấp môn yêu cầu và đạt, theo từng môn trên đường (kèm số câu của môn). */
export interface SubjectCheck {
  sectionId: string;
  subjectCode: string;
  optionId: string | null;
  questionCount: number | null;
  answerQuotas: Array<{ answerType: QuestionType; required: number; achieved: number }>;
  byGrade: Array<{ code: string; required: number; achieved: number }>;
  byDifficulty: Array<{ code: string; required: number; achieved: number }>;
}

export interface PathCheck extends ExamPathRef {
  result: PathCheckResult;
  partIds: string[];
  totalQuestions: number;
  /** Bài dùng thời lượng chung: thời lượng chung; không thì tổng thời lượng các phần của đường. */
  totalMinutes: number;
  totalPoints: number;
  blocks: BlockCheck[];
  /** Kết quả theo môn: chỉ tiêu cấp môn yêu cầu và đạt (đường có `SUBJECT_CHOICE`: theo từng cách chọn trong `selections`). */
  subjects: SubjectCheck[];
  /** Từng cách chọn môn của đường có `SUBJECT_CHOICE`; rỗng khi đường không có. */
  selections: PathSelectionCheck[];
  /** Mọi vấn đề của đường (cấu trúc và của các khối). */
  issues: CheckIssue[];
}

export interface CheckGroupResult {
  group: CheckGroup;
  status: "PASS" | "FAIL" | "WARN";
  issues: CheckIssue[];
}

/** Kết quả thuần của một lần kiểm tra (không có thời điểm, thời gian chạy). */
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

/** Lý do kết quả kiểm tra hết hiệu lực (D14): cấu trúc, cấu hình bài hoặc nguồn câu hỏi đã đổi. */
export type CheckInvalidReason = "STRUCTURE_CHANGED" | "CONFIG_CHANGED" | "SOURCE_CHANGED";

export interface ExamCheckResult extends ExamCheckOutcome {
  examId: string;
  checkedAt: string;
  durationSeconds: number;
  timeLimitSeconds: number;
  /** `true` khi bài đã đổi trong lúc kiểm tra: kết quả trả về nhưng không được lưu (spec 5.4). */
  stale: boolean;
  /** Kết quả đã lưu hết hiệu lực vì sao; `null` khi còn hiệu lực. */
  invalidReason: CheckInvalidReason | null;

  /** Trạng thái từng môn sau lần kiểm tra (H2); `null` khi BE-5 chưa triển khai — màn không hiện trạng thái. */
  sectionStates?: ExamSectionState[] | null;
}

/**
 * Lần kiểm tra kỹ chạy nền (spec 5.4: proxy web cắt ở 60 giây). `startDeepCheck` trả `runId`; web hỏi `getCheckRun` tới
 * khi `state` khác `RUNNING`. `DONE` có `result`; `FAILED` có `errorCode` (ví dụ `CHECK_RUN_ABANDONED`).
 */
export type CheckRunState = "RUNNING" | "DONE" | "FAILED";

export interface ExamCheckRun {
  runId: string;
  state: CheckRunState;
  result: ExamCheckResult | null;
  errorCode: string | null;
}

export interface StartDeepCheckResponse {
  runId: string;
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
  /** Đáp án dạng chữ (Trả lời ngắn, Điền khuyết, Đúng/Sai, Ghép đôi); chỉ hiện trong chế độ quản trị. */
  answer: string | null;
  solution: string | null;
  /** Câu Tự luận: tiêu chí chấm (chấm tay, tùy chọn); dạng khác để `null`. */
  rubric: Array<{ id: string; description: string; points: number }> | null;
  /** Phần thân đủ để dựng Điền khuyết, Ghép đôi, Đúng/Sai; có đáp án vì là chế độ quản trị. `null` với câu mô phỏng. */
  body: QuestionBody | null;
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
      /** Số bắt đầu đánh chỗ trống của ngữ liệu (spec UNI-2638 mục 3.4, 5); thiếu: 1. */
      blankNumberStart?: number;
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
  /** Dạng trả lời của nhóm; `null` khi nhóm lấy theo chỉ tiêu dạng trả lời của môn. */
  questionType: QuestionType | null;
  /** Khối Tự luận: chấm tay, điểm có sau khi người chấm xong. */
  manualGrading: boolean;
  /** Điểm mỗi câu của khối trên đề đã sinh (điểm lấy từ phiên bản câu, B5). */
  pointsEach: number;
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

export type PreviewCheckCode = "NO_DUPLICATES" | "SETS_INTACT" | "TOTAL_POINTS";

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

/** Một chỉ tiêu cấp môn trên đề đã sinh: số câu yêu cầu và số câu đề sinh ra. `code`: dạng trả lời, mã khối lớp hoặc mã độ khó. */
export interface PreviewSubjectQuota {
  kind: "ANSWER_TYPE" | "GRADE" | "DIFFICULTY";
  code: string;
  required: number;
  generated: number;
}

/** Một môn trên đề đã sinh. Phương án `SUBJECT_CHOICE` chỉ có các môn đã chọn, `questionCount` theo B10 (16 hoặc 17 câu). */
export interface PreviewSubject {
  sectionId: string;
  subjectCode: string;
  questionCount: number;
  /** Chỉ tiêu cấp môn của môn; rỗng khi môn không khai chỉ tiêu (mẫu cũ). */
  quotas: PreviewSubjectQuota[];
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
  subjects: PreviewSubject[];
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
  | "ORDER_CHANGED"
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
  /**
   * Phạm vi kiểm tra (spec 7.1): `EXAM` cả bài (mặc định, gồm đơn vị theo mã), `SECTION` một môn (`sectionId`), `PART`
   * một phần (`partId`, bỏ qua đơn vị theo mã của phần).
   */
  scope?: "EXAM" | "SECTION" | "PART";
  sectionId?: string;
  partId?: string;
}

export interface GeneratePreviewRequest {
  examId: string;
  pathId: string;
  seed: number;
  version?: number | null;
  /**
   * Đường có `SUBJECT_CHOICE`: đúng `chooseSubjects` môn thuộc phương án muốn xem; thiếu hoặc sai thì 422
   * `PREVIEW_SUBJECTS_INVALID`.
   */
  subjectCodes?: string[];
}

export interface PublishExamRequest {
  examId: string;
  pathIds: string[];
  note: string | null;
  /** `revision` của bài lúc mở màn (D13); lệch thì 412 `EXAM_REVISION_CONFLICT`. */
  expectedRevision: number;
  /** Khóa idempotency của lần bấm Công bố (spec 7.1); thử lại lần đó giữ cùng khóa. Bỏ trống: client tự sinh. */
  idempotencyKey?: string;
}

export interface PublishExamResponse {
  examId: string;
  version: number;
  publishedPathIds: string[];
  /** Đường không công bố lần này, lượt thi mới vẫn dùng phiên bản cũ (số phiên bản cũ). */
  keptPaths: Array<{ pathId: string; version: number }>;
}

export interface DiscontinueExamRequest {
  examId: string;
  /** `revision` của bài lúc mở màn (D13); lệch thì 412 `EXAM_REVISION_CONFLICT`. */
  expectedRevision: number;
  /** Khóa idempotency của lần xác nhận ngừng bán (spec 7.1); thử lại lần đó giữ cùng khóa. */
  idempotencyKey?: string;
}
