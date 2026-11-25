import type { components } from "@thi360/api-client";
import type {
  ExamBuildMode,
  ExamScope,
  ExamTiming,
  MultipleChoiceRule,
  PersonRef,
  PracticeScoring,
  QuestionType,
  TrueFalseMethod,
} from "@/features/content/types";
import type { ApiPage } from "@/lib/api/page";

export type { ExamBuildMode, ExamScope, ExamTiming, PracticeScoring } from "@/features/content/types";

type Schema = components["schemas"];

export type ExamKind = string;
export const EXAM_KINDS = ["HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG", "HSCA", "VSAT"] as const;
export type ExamStatus = "DRAFT" | "PUBLISHED" | "DISCONTINUED";
export type ExamCheckStatus = "UNCHECKED" | "VALID" | "INVALID" | "NEEDS_RECHECK";

export interface ExamCheckSummary {
  status: ExamCheckStatus;
  issueCount: number;
  checkedAt: string | null;
}

export interface ExamSummary {
  id: string;
  code: string;
  name: string;
  kind: ExamKind;
  scope: ExamScope;
  status: ExamStatus;
  revision: number;
  servingVersion: number | null;
  hasUnpublishedChanges: boolean;
  totalQuestions: number;
  durationMinutes: number;
  englishOption: boolean;
  combinationCount: number;
  partCode: string | null;
  subjectCode: string | null;
  timing: ExamTiming;
  maxScore: number | null;
  referenceMax: number | null;
  practiceScoring: PracticeScoring;
  hasEssay: boolean;
  branchCount?: number;
  check: ExamCheckSummary;
  attemptCount: number;
  updatedAt: string;
  updatedBy: PersonRef;
}

export interface KnowledgeScope {
  nodeIds: string[];
  competencyCodes?: string[];
  everyChildInScope?: boolean;
}

export interface BlockSlot {
  index: number;
  knowledgeScope?: KnowledgeScope | null;
  tagCodes?: string[];
}

export type ItemShape = Schema["ItemShape"];
export type DisplaySegment = Schema["DisplaySegment"];
export type ExamOverviewRow = Schema["ExamOverviewRow"];
export type DuplicateExamRequest = Schema["DuplicateExamRequest"];
export type MatrixCell = Required<Schema["MatrixCell"]>;
export type MatrixDimensionValue = Required<Schema["MatrixDimensionValue"]>;

export interface ChildCondition {
  from: number;
  to: number;
  nodeIds: string[];
  answerType?: QuestionType;
  itemShape?: ItemShape;
}

export type MatrixMode = "COMBINED" | "SEPARATE";
export type MatrixUnit = "COUNT" | "PERCENT";
export type QuotaEnforcement = "REQUIRED" | "REFERENCE";
export type QuotaBasis = "QUESTION" | "PROMPT" | "POINTS";

export interface BlockMatrix {
  mode: MatrixMode;
  unit: MatrixUnit;
  cells: MatrixCell[];
  byGrade: MatrixDimensionValue[];
  byDifficulty: MatrixDimensionValue[];
  byCompetency?: MatrixDimensionValue[];
  gradeEnforcement?: QuotaEnforcement;
  difficultyEnforcement?: QuotaEnforcement;
  basis?: QuotaBasis;
}

export interface TopicQuota {
  id: string;
  name: string;
  nodeIds: string[];
  count: number;
  percent?: number | null;
  gradeCodes?: string[];
  difficultyCodes?: string[];
  enforcement: QuotaEnforcement;
  basis?: QuotaBasis;
  groupIds?: string[];
}

export interface ExamQuota {
  id: string;
  name: string;
  dimension: "GRADE" | "DIFFICULTY";
  buckets: Array<{ codes: string[]; count?: number | null; percent?: number | null }>;
  basis: QuotaBasis;
  enforcement: QuotaEnforcement;
  subjectCodes?: string[];
  excludeSubjectCodes?: string[];
}

export interface BlockExpectedScoring {
  trueFalseMethod?: TrueFalseMethod;
  trueFalseTable?: number[];
  multipleChoiceRule?: MultipleChoiceRule;
}

export interface QuestionBlock {
  id: string;
  name: string;
  questionType: QuestionType | null;
  /** `null` = "lấy phần còn lại" của môn có số câu thay đổi (ví dụ HSA Khoa học). */
  questionCount: number | null;
  setCount: number | null;
  setSize: number | null;
  requiredPointsPerQuestion: number | null;
  knowledgeScope: KnowledgeScope | null;
  tagCodes: string[];
  matrix: BlockMatrix | null;
  childAnswerType: QuestionType | null;
  templateGroupCode: string | null;
  answerTypeLocked: boolean;
  expectedScoring?: BlockExpectedScoring;
  shuffleUnits?: boolean;
  shuffleEditable?: boolean;
  omitted?: boolean;
  slots?: BlockSlot[];
  childConditions?: ChildCondition[];
  itemShape?: ItemShape | null;
  answerFormats?: string[];
  /** Chỉ có ở phản hồi; không gửi lại khi lưu cấu trúc. */
  positionRange?: { from: number; to: number } | null;
  topicQuotas?: TopicQuota[];
}

export interface AnswerQuotas {
  unit: MatrixUnit;
  items: Array<{ answerType: QuestionType; value: number }>;
}

export type ChoiceGroup = "NATURAL" | "SOCIAL";
export type AudioRequirement = "ANY" | "REQUIRED" | "FORBIDDEN";
export type MapidMode = "ALL" | "MATRIX";
export type MapidRuleType = "PRESENT" | "COUNT" | "ONLY_IN_GROUP" | "SOURCE";
export type MapidComparator = "EXACT" | "AT_LEAST";

export interface MapidRule {
  id: string;
  type: MapidRuleType;
  groupId?: string | null;
  nodeIds: string[];
  comparator?: MapidComparator;
  count?: number | null;
  percent?: number | null;
  /** Tên nhóm đã bị xóa khỏi bản nháp; chỉ giữ ở client, không gửi lên máy chủ. */
  deletedGroupName?: string;
}

export interface ExamSection {
  id: string;
  subjectCode: string;
  questionCount: number | null;
  knowledgeScope: KnowledgeScope | null;
  answerQuotas: AnswerQuotas | null;
  matrix: BlockMatrix | null;
  choiceGroup: ChoiceGroup | null;
  blocks: QuestionBlock[];
  topicQuotas?: TopicQuota[];
  audioRequirement?: AudioRequirement;
  mapidMode?: MapidMode;
  mapidRules?: MapidRule[];
  mixBlocks?: boolean;
}

export interface ExamPartOption {
  id: string;
  kind: "SUBJECT" | "COMBINATION" | "BRANCH" | "SUBJECT_CHOICE";
  code: string;
  name?: string | null;
  sections: ExamSection[];
  chooseSubjects: number | null;
  countsByPick: number[] | null;
  expertConfirmed?: boolean;
  blocks?: QuestionBlock[];
}

export interface ExamPart {
  id: string;
  order: number;
  name: string;
  durationMinutes: number | null;
  questionCount: number;
  questionCountOnPaper: number;
  questionCountRequired: number;
  participation: { mode: "ALL" | "CHOICE"; choose: number | null };
  maxPoints?: number | null;
  mode: "FIXED" | "CHOICE";
  sections: ExamSection[];
  options: ExamPartOption[];
  subjectPool?: ExamSection[] | null;
  codeSource?: { source: string; branch: string | null } | null;
  displaySegments?: DisplaySegment[];
}

export interface ExamVersion {
  version: number | null;
  state: "WORKING" | "SERVING" | "RETIRED";
  publishedAt: string | null;
  publishedBy: PersonRef | null;
  attemptCount: number;
  note: string | null;
}

export interface ExamDetail extends ExamSummary {
  description: string | null;
  instructions: string | null;
  templateCode: string | null;
  buildMode: ExamBuildMode;
  structureYear: number | null;
  matrixYear: number | null;
  yearsConfirmed: boolean;
  parts: ExamPart[];
  versions: ExamVersion[];
}

export interface ListExamsQuery {
  q?: string;
  kind?: ExamKind;
  scope?: ExamScope;
  subjectCode?: string;
  status?: ExamStatus;
  checkStatus?: ExamCheckStatus;
  hasUnpublishedChanges?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export type ExamListPage = ApiPage<ExamSummary>;

export interface ExamTemplate {
  code: string;
  kind: ExamKind;
  scope: ExamScope;
  name: string;
  partCode?: string | null;
  subjectCode?: string | null;
  structureYear?: number | null;
  matrixYear?: number | null;
  timing: ExamTiming;
  durationMinutes: number | null;
  maxScore: number | null;
  buildMode?: ExamBuildMode | null;
  isDefault?: boolean | null;
  label?: string | null;
  summary?: string | null;
  expertNote?: string | null;
  publishable?: boolean | null;
  fixedCounts?: boolean | null;
  fixedBlockCounts?: boolean | null;
  omittedLocked?: boolean | null;
  parts: ExamTemplatePart[];
}

export interface TypeCount {
  questionType: QuestionType | "ANY";
  questionCount: number;
}

export interface ExamTemplatePart {
  partId?: string | null;
  order?: number | null;
  name: string;
  questionCount: number;
  durationMinutes: number | null;
  mode: "FIXED" | "CHOICE";
  subjectCode?: string | null;
  points?: number | null;
  maxPoints?: number | null;
  sections?: ExamTemplateSection[];
  options?: Array<{
    kind: ExamPartOption["kind"];
    code: string;
    name?: string | null;
    subjectCounts?: number[];
    expertConfirmed?: boolean;
    blocks?: ExamTemplateBlock[];
    subjectChoice?: TemplateSubjectChoice;
  }>;
  blocks?: ExamTemplateBlock[];
  displaySegments?: DisplaySegment[];
  questionTypes?: TypeCount[];
}

export interface ExamTemplateSection {
  sectionId?: string | null;
  omittedBlocks?: Array<{ id?: string | null; templateGroupCode?: string | null; name: string; questionCount: number | null }>;
  subjectCode: string;
  questionCount: number | null;
  blocks?: ExamTemplateBlock[];
  audioRequirement?: AudioRequirement;
  knowledgeScope?: KnowledgeScope | null;
  answerQuotas?: AnswerQuotas | null;
  matrix?: BlockMatrix | null;
  choiceGroup?: ChoiceGroup | null;
}

export interface TemplateSubjectChoice {
  sections: ExamTemplateSection[];
  chooseSubjects: number;
  countsByPick: number[];
}

export interface ExamTemplateBlock {
  name: string;
  questionType: QuestionType | null;
  questionCount: number | null;
  requiredPointsPerQuestion: number | null;
  setCount?: number | null;
  setSize?: number | null;
  competencyCode?: string | null;
  expectedScoring?: BlockExpectedScoring;
  childAnswerType?: QuestionType | null;
  templateGroupCode?: string | null;
  answerTypeLocked?: boolean;
  knowledgeScope?: KnowledgeScope | null;
  matrix?: BlockMatrix | null;
  childConditions?: ChildCondition[];
  itemShape?: ItemShape | null;
}

export interface CreateExamRequest {
  name: string;
  code: string;
  kind: ExamKind;
  scope: ExamScope;
  partCode?: string | null;
  subjectCode?: string | null;
  buildMode?: ExamBuildMode | null;
  description: string | null;
  instructions?: string | null;
  durationMinutes?: number | null;
  maxScore?: number | null;
  overview?: ExamOverviewRow[];
}

export interface UpdateExamInfoRequest {
  name?: string;
  code?: string;
  kind?: ExamKind;
  scope?: ExamScope;
  partCode?: string | null;
  subjectCode?: string | null;
  buildMode?: ExamBuildMode | null;
  description?: string | null;
  instructions?: string | null;
  durationMinutes?: number | null;
  maxScore?: number | null;
  yearsConfirmed?: boolean;
  confirmReplace?: boolean;
  expectedRevision: number;
}

export type StructureOverviewPolicy = "KEEP_EDITED" | "USE_TEMPLATE";

export interface ApplyExamTemplateRequest {
  templateCode: string;
  confirmReplace: boolean;
  overviewPolicy?: StructureOverviewPolicy | null;
  expectedRevision: number;
}

export interface ExamSectionState {
  partId: string | null;
  sectionId: string | null;
  subjectCode: string | null;
  state: "INCOMPLETE" | "CHECKED" | "NEEDS_CHECK";
}

export interface StructureCombination {
  code: string;
  examCode: string | null;
  partId: string | null;
  subjectCodes: string[];
  missingSubjects: string[];
  nameVi: string | null;
  nameEn: string | null;
}

export interface ExamStructure {
  examId: string;
  revision: number;
  parts: ExamPart[];
  combinations?: StructureCombination[];
  sectionStates?: ExamSectionState[] | null;
  examQuotas?: ExamQuota[];
}

export interface ExamStructureSummaryRow {
  name: string | null;
  partId: string | null;
  sectionId: string | null;
  blockId: string | null;
  target: number | null;
  allocated: number | null;
  difference: number | null;
  essayCount: number | null;
  objectiveCount: number | null;
  setCount: number | null;
  commandCount: number | null;
  childCount: number | null;
  maxPoints: number | null;
}

export interface ExamStructureSummary {
  rows: ExamStructureSummaryRow[];
}

export interface UpdateExamStructureRequest {
  examQuotas?: ExamQuota[];
  parts: ExamPart[];
  expectedRevision: number;
}

export interface DiscontinueExamRequest {
  examId: string;
  expectedRevision: number;
  idempotencyKey: string;
}

export interface SuggestExamCodeQuery {
  kind: ExamKind;
  scope: ExamScope;
  partCode?: string | null;
  subjectCode?: string | null;
}

export interface CountKnowledgeScope extends KnowledgeScope {
  excludedNodeIds?: string[];
  nodeGroups?: string[][];
  everyChildSameNode?: boolean;
}

export interface AvailableQuestionsQuery {
  subjectCode: string;
  questionType: QuestionType | null;
  answerTypes: QuestionType[];
  childAnswerType: QuestionType | null;
  knowledgeScope: CountKnowledgeScope | null;
  tagCodes?: string[];
  competencyCodes?: string[];
  requiredPointsPerQuestion?: number | null;
  itemShape?: ItemShape | null;
  answerFormats?: string[];
  childConditions?: ChildCondition[];
}

export interface SubjectAvailableQuestionsQuery {
  subjectCode: string;
  groups: AvailableQuestionsQuery[];
}

export interface AvailableQuestionCounts {
  total: number;
  cells: MatrixCell[];
  byAnswerType: Array<{ answerType: QuestionType; value: number }>;
  byCompetency?: MatrixDimensionValue[];
}
