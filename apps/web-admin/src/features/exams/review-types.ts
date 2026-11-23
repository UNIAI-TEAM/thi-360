import type { components } from "@thi360/api-client";
import type { PersonRef, QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import type viMessages from "@/messages/vi.json";
import type {
  DiscontinueExamRequest,
  DisplaySegment,
  ExamCheckStatus,
  ExamCheckSummary,
  ExamPart,
  ExamPartOption,
  ExamSectionState,
  ExamVersion,
  MatrixMode,
  QuotaBasis,
  QuotaEnforcement,
} from "./types";

type Schema = components["schemas"];

export type { DiscontinueExamRequest };

export type PathChoice = Required<Omit<Schema["ExamPreviewPathChoice"], "kind">> & { kind: ExamPartOption["kind"] };

export interface ExamPathRef {
  pathId: string;
  choices: PathChoice[];
}

export type PathCheckResult = "VALID" | "INVALID" | "UNCONFIRMED";
export type BlockCheckResult = "FOUND" | "NOT_FOUND" | "UNCONFIRMED";

export const CHECK_GROUPS = ["INFO", "STRUCTURE", "MATRIX", "SOURCE", "QUESTION_SET", "OVERLAP", "POINTS", "DISPLAY"] as const;
export type CheckGroup = (typeof CHECK_GROUPS)[number];

export type CheckIssueCode = keyof (typeof viMessages)["exams"]["review"]["issue"] | "PREVIEW_SUBJECTS_INVALID";

export type TopicQuotaIndex = Map<
  string,
  { name: string; enforcement: QuotaEnforcement; nodeIds: string[]; difficultyCodes: string[] }
>;

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

type RequiredDimension = Required<Schema["ExamPreviewRequirementDimension"]>;

export interface BlockRequirement {
  mode: MatrixMode;
  cells: Required<Schema["ExamPreviewRequirementCell"]>[];
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

export type StartDeepCheckResponse = Required<Pick<Schema["ExamCheckStarted"], "runId">>;

export type PreviewAttachment = Required<Schema["ExamPreviewAttachment"]>;
export type PreviewDisplaySegment = DisplaySegment;
export type PreviewKnowledgeStat = Required<Schema["ExamPreviewKnowledgeStat"]>;

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
  knowledge: Required<Schema["ExamPreviewKnowledgeRef"]> | null;
  points: number;
  options: Required<Schema["ExamPreviewOption"]>[] | null;
  answer: string | null;
  solution: string | null;
  rubric: Required<Schema["ExamPreviewRubricItem"]>[] | null;
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
  enforcement: QuotaEnforcement;
  basis: QuotaBasis;
  independent: boolean;
  note: string | null;
  originalRequired: number | null;
  topicName: string | null;
}

export type PreviewTarget =
  | { kind: "SECTION"; sectionId: string; sectionQuestionCount?: number }
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
  check: ExamCheckSummary;
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

export type PublishExamResponse = Required<Omit<Schema["ExamPublishResponse"], "keptPaths">> & {
  keptPaths: Required<Schema["ExamPublishKeptPath"]>[];
};
