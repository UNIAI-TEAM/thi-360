import type { components } from "@thi360/api-client";
import type { PersonRef, QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import type { DiscontinueExamRequest, DisplaySegment, ExamCheckStatus, ExamPart, ExamSectionState, ExamVersion, MatrixMode } from "./types";

/**
 * Kiểu của Kiểm tra cấu hình, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11).
 */

type Schema = components["schemas"];

export type { DiscontinueExamRequest };

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

/** Mã vấn đề của kiểm tra cấu hình và xem trước. */
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
  "TEMPLATE_NOT_PUBLISHABLE",
  "STRUCTURE_COUNT_LOCKED",
  "TOPIC_QUOTA_MAPPING_MISSING",
  "AUDIO_SHORTAGE",
  "POINTS_SHORTAGE",
  "BLOCK_COUNT_LOCKED",
  "OMITTED_BLOCK_LOCKED",
  "SCALE_INCLUDES_EXCLUDED_PART",
  "DISPLAY_SEGMENT_MISMATCH",
  "SECTION_SET_COUNT_RANGE",
  "SHORT_ANSWER_MIN",
  "STORED_ID_COLLISION",
  "MAPID_RULE_CONFLICT",
  "MAPID_RULE_GROUP_MISSING",
  "STATEMENT_POINTS_UNEVEN",
] as const;
export type CheckIssueCode = (typeof CHECK_ISSUE_CODES)[number];

interface TopicQuotaRef {
  name: string;
  enforcement: "REQUIRED" | "REFERENCE";
  nodeIds: string[];
  difficultyCodes: string[];
}

/** Chỉ tiêu chủ đề của bài theo id: dựng từ cấu trúc đã chuyển đổi bằng `buildTopicQuotaIndex`. */
export type TopicQuotaIndex = Map<string, TopicQuotaRef>;

export type CheckIssueSeverity = "ERROR" | "UNCONFIRMED" | "WARNING";

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
  optionId: string | null;
  gradeCode: string | null;
  difficultyCode: string | null;
  dimension: "GRADE" | "DIFFICULTY" | "COMPETENCY" | "COUNT" | "SET_COUNT" | "SET_SIZE" | "TYPE" | "COMMANDS" | "POINTS" | null;
  competencyCode: string | null;
  answerType: QuestionType | null;
  subjectCount: number | null;
  required: number | null;
  available: number | null;
  excludedPoints?: number | null;
  timeLimitSeconds: number | null;
  questionCodes: string[] | null;
  affectedCodes?: string[] | null;
  slotIndex?: number | null;
  childPosition?: number | null;
  segmentCode?: string | null;
  ruleId?: string | null;
  ruleIds?: string[] | null;
  conflictKind?: string | null;
  nodeIds?: string[] | null;
  requiredSets?: number | null;
  availableSets?: number | null;
  availableSingles?: number | null;
}

interface RequiredCell {
  gradeCode: string;
  difficultyCode: string;
  required: number;
}

interface RequiredDimension {
  code: string;
  required: number;
}

export interface BlockRequirement {
  mode: MatrixMode;
  cells: RequiredCell[];
  byGrade: RequiredDimension[];
  byDifficulty: RequiredDimension[];
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
  required: number;
  setCount: number | null;
  setSize: number | null;
  available: number;
  availableSets: number | null;
  requirement: BlockRequirement | null;
  result: BlockCheckResult;
  issues: CheckIssue[];
}

export interface PathSelectionCheck {
  subjectCodes: string[];
  counts: number[];
  result: PathCheckResult;
  issues: CheckIssue[];
}

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
  totalMinutes: number;
  totalPoints: number;
  blocks: BlockCheck[];
  subjects: SubjectCheck[];
  selections: PathSelectionCheck[];
  issues: CheckIssue[];
}

export interface CheckGroupResult {
  group: CheckGroup;
  status: "PASS" | "FAIL" | "WARN";
  issues: CheckIssue[];
}

export interface ListingCheckIssue {
  key: string;
  code: string;
  severity: "ERROR" | "WARNING";
  field: string | null;
  minPriceVnd: number | null;
}

export interface ListingCheckGroup {
  status: "PASS" | "WARN" | "FAIL";
  issues: ListingCheckIssue[];
}

export type CheckInvalidReason = "STRUCTURE_CHANGED" | "CONFIG_CHANGED" | "SOURCE_CHANGED" | "RULES_CHANGED";

export interface ExamCheckResult {
  examId: string;
  status: Extract<ExamCheckStatus, "VALID" | "INVALID">;
  issueCount: number;
  paths: PathCheck[];
  groups: CheckGroupResult[];
  partCount: number;
  totalPoints: number;
  printedQuestions: number;
  maxScore: number | null;
  checkedAt: string;
  durationSeconds: number;
  timeLimitSeconds: number;
  stale: boolean;
  invalidReason: CheckInvalidReason | null;
  sectionStates?: ExamSectionState[] | null;
  listingCheck?: ListingCheckGroup | null;
}

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

interface PreviewOption {
  id: string;
  content: string;
  correct: boolean;
}

export type PreviewAttachment = Required<Schema["ExamPreviewAttachment"]>;

export type PreviewDisplaySegment = DisplaySegment;

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
  answer: string | null;
  solution: string | null;
  rubric: Array<{ id: string; description: string; points: number }> | null;
  body: QuestionBody | null;
  originalNumber: number | null;
  optionOrder: string[] | null;
  originalOptionOrder: string[] | null;
  assignedTopicQuotaIds: string[];
  attachments?: PreviewAttachment[];
}

export type PreviewItem =
  | { kind: "QUESTION"; question: PreviewQuestion; originalOrder?: number | null }
  | {
      kind: "SET";
      setId: string;
      code: string;
      version: number | null;
      simulated: boolean;
      passage: string | null;
      blankNumberStart?: number;
      children: PreviewQuestion[];
      originalOrder?: number | null;
      attachments?: PreviewAttachment[];
    };

export interface PreviewAllocationCell {
  gradeCode: string;
  difficultyCode: string;
  required: number | null;
  generated: number;
}

export interface PreviewBlock {
  blockId: string;
  blockName: string;
  subjectCode: string;
  questionType: QuestionType | null;
  manualGrading: boolean;
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
  durationMinutes: number | null;
  firstNumber: number;
  lastNumber: number;
  points: number;
  maxPoints: number | null;
  displaySegments?: PreviewDisplaySegment[];
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

export interface PreviewSubjectQuota {
  kind: "ANSWER_TYPE" | "GRADE" | "DIFFICULTY";
  code: string;
  required: number;
  generated: number;
}

export interface PreviewSubject {
  sectionId: string;
  subjectCode: string;
  questionCount: number;
  quotas: PreviewSubjectQuota[];
}

export interface PreviewPaper {
  totalQuestions: number;
  totalPoints: number;
  totalMinutes: number;
  sharedDuration: boolean;
  hasManualGrading: boolean;
  parts: PreviewPart[];
  subjects: PreviewSubject[];
  checks: PreviewCheck[];
  knowledgeStats: PreviewKnowledgeStat[];
}

interface PreviewBase {
  examId: string;
  path: ExamPathRef;
  version: number | null;
  seed: number;
  generatedAt: string;
  expiresAt: string | null;
}

export interface PreviewCompliance {
  criterion: string;
  scope: string;
  required: number | null;
  actual: number;
  enforcement: "REQUIRED" | "REFERENCE";
  basis: "QUESTION" | "PROMPT" | "POINTS";
  independent: boolean;
  note: string | null;
  originalRequired: number | null;
  topicName: string | null;
}

export type PreviewTarget =
  | {
      kind: "SECTION";
      sectionId: string;
      sectionQuestionCount?: number;
    }
  | { kind: "PATH"; pathId: string; subjectCodes?: string[] }
  | { kind: "EXAM" };

export type PreviewResult =
  | (PreviewBase & {
      status: "GENERATED";
      paper: PreviewPaper;
      previewId: string | null;
      compliance: PreviewCompliance[];
      droppedQuestions: PreviewQuestion[];
    })
  | (PreviewBase & { status: "FAILED"; reason: "INVALID" | "UNCONFIRMED"; issues: CheckIssue[] });

/* ---------- Phiên bản và công bố ---------- */

export interface ExamVersionRow extends ExamVersion {
  version: number;
  publishedPaths: ExamPathRef[];
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

export interface ExamChange {
  id: string;
  kind: ExamChangeKind;
  where: string;
  before: string | null;
  after: string | null;
  blockId: string | null;
  warning: "UNCONFIRMED" | "INVALID" | null;
}

export interface ExamWorkingCopy {
  editedBy: PersonRef;
  savedAt: string;
  baseVersion: number | null;
  note: string | null;
  check: { status: ExamCheckStatus; issueCount: number; checkedAt: string | null };
  unpublishedPaths: ExamPathRef[];
}

export interface ExamVersionList {
  examId: string;
  workingCopy: ExamWorkingCopy | null;
  changes: ExamChange[];
  items: ExamVersionRow[];
}

export interface ExamVersionDetail {
  examId: string;
  row: ExamVersionRow;
  parts: ExamPart[];
}

export interface RunExamCheckRequest {
  examId: string;
  scope?: "EXAM" | "SECTION" | "PART";
  sectionId?: string;
  partId?: string;
}

export interface GeneratePreviewRequest {
  examId: string;
  target: PreviewTarget;
  seed: number;
  version?: number | null;
  basePreviewId?: string;
  regeneratePartIds?: string[];
}

export interface PublishExamRequest {
  examId: string;
  pathIds: string[];
  note: string | null;
  expectedRevision: number;
  idempotencyKey?: string;
}

export interface PublishExamResponse {
  examId: string;
  version: number;
  publishedPathIds: string[];
  keptPaths: Array<{ pathId: string; version: number }>;
}
