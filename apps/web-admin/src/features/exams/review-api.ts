import type { ApiError, components, FieldError } from "@thi360/api-client";
import type { MultipleChoiceRule, PersonRef, TrueFalseMethod } from "@/features/content/types";
import { QUESTION_TYPES, type QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";
import { mapExamDetail, mapSectionStates } from "./api-mapping";
import type {
  BlockCheck,
  BlockCheckResult,
  BlockRequirement,
  CheckGroup,
  CheckGroupResult,
  CheckIssue,
  CheckIssueCode,
  CheckIssueSeverity,
  CheckInvalidReason,
  CheckRunState,
  DiscontinueExamRequest,
  ExamChange,
  ExamChangeKind,
  ExamCheckResult,
  ExamCheckRun,
  ExamPathRef,
  ExamVersionDetail,
  ExamVersionList,
  ExamVersionRow,
  ExamWorkingCopy,
  GeneratePreviewRequest,
  PathCheck,
  PathCheckResult,
  PathChoice,
  PathSelectionCheck,
  PreviewAllocationCell,
  PreviewBlock,
  PreviewCheck,
  PreviewCheckCode,
  PreviewCompliance,
  PreviewItem,
  PreviewPaper,
  PreviewPart,
  PreviewQuestion,
  PreviewResult,
  PreviewSubject,
  PreviewSubjectQuota,
  PublishExamRequest,
  PublishExamResponse,
  RunExamCheckRequest,
  StartDeepCheckResponse,
  SubjectCheck,
  TopicQuotaIndex,
} from "./review-types";
import { CHECK_GROUPS, CHECK_ISSUE_CODES } from "./review-types";
import type {
  AnswerQuotas,
  BlockMatrix,
  ChoiceGroup,
  ExamDetail,
  ExamPart,
  ExamPartOption,
  ExamSection,
  ExamVersion,
  QuestionBlock,
} from "./types";

/**
 * API của Kiểm tra cấu hình, Kiểm tra kỹ, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11): gọi API thật qua
 * `@thi360/api-client` và chuyển đổi sang kiểu của `review-types.ts` (`mapX` ở cuối file). Bản mẫu đã gỡ ở QA.
 */

export { EXAM_REVIEW_QUERY_KEYS } from "./review-keys";

/* ---------- Lỗi ---------- */

/** Lỗi của nhánh API thật: mang {status, code, fieldErrors} để màn hình xử lý chung. */
export class ExamsApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(status: number, message = "", code?: string, fieldErrors: readonly FieldError[] = []) {
    super(status, message);
    this.name = "ExamsApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toExamsApiError(status: number, error: unknown): ExamsApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    const code = typeof body.code === "string" ? body.code : undefined;
    const message = typeof body.message === "string" ? body.message : "";
    const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : [];
    return new ExamsApiError(status, message, code, fieldErrors);
  }
  return new ExamsApiError(status);
}

/**
 * Tạo bài từ mẫu đã xong (POST 201) nhưng lưu cấu trúc đã sửa thất bại: `exam` là bài Nháp đã có, mang cấu trúc của mẫu.
 * Mang trạng thái và mã của lỗi lưu cấu trúc; nơi gọi chuyển sang bài đó, không POST lại (sẽ trùng mã).
 */
export class ExamStructureSaveError extends ExamsApiError {
  readonly exam: ExamDetail;

  constructor(exam: ExamDetail, cause: unknown) {
    const base = cause instanceof ExamsApiError ? cause : undefined;
    super(base?.status ?? 0, base?.message, base?.code, base?.fieldErrors);
    this.name = "ExamStructureSaveError";
    this.exam = exam;
    this.cause = cause;
  }
}

/** Mã lỗi của backend (`ApiError.code`) trên lỗi của nhánh thật lẫn nhánh dữ liệu mẫu; `undefined` khi không có. */
export function errorCodeOf(error: unknown): string | undefined {
  if (!(error instanceof ApiStatusError)) return undefined;
  const { code } = error as ApiStatusError & { code?: unknown };
  return typeof code === "string" ? code : undefined;
}

/**
 * Lý do của 409 `CHECK_REQUIRED` khi công bố: backend gửi trong `fieldErrors[0]` ({field: "reason", code: lý do};
 * spec 5.2 — NO_CHECK, STRUCTURE_CHANGED, CONFIG_CHANGED, SOURCE_CHANGED, PATH_NOT_VALID, ...). `null` khi không phải
 * lỗi đó; `"UNKNOWN"` khi thiếu lý do.
 */
export function checkRequiredReason(error: unknown): string | null {
  if (!(error instanceof ApiStatusError) || error.status !== 409 || errorCodeOf(error) !== "CHECK_REQUIRED") {
    return null;
  }
  const { fieldErrors } = error as ApiStatusError & { fieldErrors?: readonly FieldError[] };
  return fieldErrors?.find((item) => item.field === "reason")?.code ?? "UNKNOWN";
}

/* ---------- Kiểm tra cấu hình ---------- */

async function getExamCheckReal({ examId }: { examId: string }): Promise<ExamCheckResult | null> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId } },
  });
  if (!response.ok) throw toExamsApiError(response.status, error);
  // Không có lần kiểm nào thì thân là `null` (nullable trên 200).
  return data == null ? null : mapCheckResult(data);
}

async function runExamCheckReal({ examId, scope, partId, sectionId }: RunExamCheckRequest): Promise<ExamCheckResult> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId } },
    body: {
      scope: scope ?? undefined,
      partId: partId ?? undefined,
      sectionId: sectionId ?? undefined,
    },
  });
  if (!response.ok || data === undefined || !isCheckResult(data)) throw toExamsApiError(response.status, error);
  return mapCheckResult(data);
}

async function startDeepCheckReal(examId: string): Promise<StartDeepCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId }, query: { deep: true } },
  });
  // Spec 5.4: `?deep=true` luôn chạy nền và trả 202 {runId}.
  if (!response.ok || data === undefined || !isCheckStarted(data) || data.runId === undefined) {
    throw toExamsApiError(response.status, error);
  }
  return { runId: data.runId };
}

async function getCheckRunReal(examId: string, runId: string): Promise<ExamCheckRun> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/check/runs/{runId}", {
    params: { path: { examId, runId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  const state: CheckRunState =
    data.state === "DONE" || data.state === "FAILED" ? data.state : "RUNNING";
  return {
    runId: data.runId ?? runId,
    state,
    result: data.result == null ? null : mapCheckResult(data.result),
    errorCode: data.errorCode ?? null,
  };
}

/* ---------- Đề xem trước ---------- */

async function generatePreviewReal({
  examId,
  target,
  seed,
  version,
  basePreviewId,
  regeneratePartIds,
}: GeneratePreviewRequest): Promise<PreviewResult> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{id}/preview", {
    params: { path: { id: examId } },
    body: {
      target,
      seed,
      ...(version !== null && version !== undefined ? { version } : {}),
      ...(basePreviewId !== undefined ? { basePreviewId, regeneratePartIds: regeneratePartIds ?? [] } : {}),
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapPreviewResult(examId, data);
}

/**
 * Chỉ tiêu chủ đề của bài theo id (R-122, H31): đọc phản hồi cấu trúc thô vì `types.ts` chưa nâng `topicQuotas`
 * (FE-3b). Dòng `REFERENCE` có `nodeIds` rỗng đang chờ mapping; bảng đối chiếu ghi "Chưa có dữ liệu đối chiếu".
 */
async function getTopicQuotaIndexReal(examId: string): Promise<TopicQuotaIndex> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}/structure", {
    params: { path: { id: examId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  const index: TopicQuotaIndex = new Map();
  const addQuotas = (items: readonly (Schema["ExamSection"] | Schema["QuestionBlock"])[] | undefined) => {
    for (const item of items ?? []) {
      for (const quota of item.topicQuotas ?? []) {
        if (!quota.id) continue;
        index.set(quota.id, {
          name: quota.name ?? "",
          enforcement: quota.enforcement === "REFERENCE" ? "REFERENCE" : "REQUIRED",
          nodeIds: quota.nodeIds ?? [],
          difficultyCodes: quota.difficultyCodes ?? [],
        });
      }
    }
  };
  const addSection = (section: Schema["ExamSection"]) => {
    addQuotas([section]);
    addQuotas(section.blocks ?? []);
  };
  for (const part of data.parts ?? []) {
    (part.sections ?? []).forEach(addSection);
    (part.subjectPool ?? []).forEach(addSection);
    for (const option of part.options ?? []) {
      (option.sections ?? []).forEach(addSection);
      addQuotas(option.blocks ?? []);
    }
  }
  return index;
}

/* ---------- Phiên bản và công bố ---------- */

async function listExamVersionsReal({ examId }: { examId: string }): Promise<ExamVersionList> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/versions", {
    params: { path: { examId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    examId: data.examId ?? examId,
    workingCopy: data.workingCopy ? mapWorkingCopy(data.workingCopy) : null,
    changes: (data.changes ?? []).map(mapChange),
    // `version` rỗng là bản làm việc; bản đó nằm ở `workingCopy`, không phải dòng lịch sử.
    items: (data.items ?? [])
      .filter((item): item is ApiVersionResponse & { version: number } => item.version !== undefined)
      .map(mapVersionRow),
  };
}

async function getExamVersionReal({
  examId,
  version,
}: {
  examId: string;
  version: number;
}): Promise<ExamVersionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/versions/{version}", {
    params: { path: { examId, version } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    examId: data.examId ?? examId,
    row: data.row ? mapVersionRow({ ...data.row, version }) : emptyVersionRow(version),
    parts: (data.parts ?? []).map(mapPart),
  };
}

async function publishExamReal({
  examId,
  pathIds,
  note,
  expectedRevision,
  idempotencyKey,
}: PublishExamRequest): Promise<PublishExamResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/publish", {
    params: {
      path: { examId },
      header: { "Idempotency-Key": idempotencyKey ?? crypto.randomUUID() },
    },
    body: { pathIds, ...(note !== null ? { note } : {}), expectedRevision },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    examId: data.examId ?? examId,
    version: data.version ?? 0,
    publishedPathIds: data.publishedPathIds ?? [],
    keptPaths: (data.keptPaths ?? []).map((path) => ({
      pathId: path.pathId ?? "",
      version: path.version ?? 0,
    })),
  };
}

async function discontinueExamReal({
  examId,
  expectedRevision,
  idempotencyKey,
}: DiscontinueExamRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/discontinue", {
    params: {
      path: { examId },
      header: { "Idempotency-Key": idempotencyKey ?? crypto.randomUUID() },
    },
    body: { expectedRevision },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

/* ---------- API thật (bản mẫu đã gỡ ở QA, UNI-2621) ---------- */

export const getExamCheck = getExamCheckReal;
export const runExamCheck = runExamCheckReal;
export const startDeepCheck = startDeepCheckReal;
export const getCheckRun = getCheckRunReal;
export const generatePreview = generatePreviewReal;
export const getTopicQuotaIndex = getTopicQuotaIndexReal;
export const listExamVersions = listExamVersionsReal;
export const getExamVersion = getExamVersionReal;
export const publishExam = publishExamReal;
export const discontinueExam = discontinueExamReal;

export interface ExamReviewApiDeps {
  getExamCheck: typeof getExamCheck;
  runExamCheck: typeof runExamCheck;
  startDeepCheck: typeof startDeepCheck;
  getCheckRun: typeof getCheckRun;
  generatePreview: typeof generatePreview;
  listExamVersions: typeof listExamVersions;
  getExamVersion: typeof getExamVersion;
  publishExam: typeof publishExam;
  discontinueExam: typeof discontinueExam;
}

export const defaultExamReviewApiDeps: ExamReviewApiDeps = {
  getExamCheck,
  runExamCheck,
  startDeepCheck,
  getCheckRun,
  generatePreview,
  listExamVersions,
  getExamVersion,
  publishExam,
  discontinueExam,
};

/* ---------- Chuyển đổi sang kiểu của review-types.ts ---------- */

type Schema = components["schemas"];
type ApiCheckResult = Schema["ExamCheckResult"];
type ApiCheckPath = Schema["ExamCheckPath"];
type ApiCheckBlock = Schema["ExamCheckBlock"];
type ApiCheckSubject = Schema["ExamCheckSubject"];
type ApiCheckSelection = Schema["ExamCheckSelection"];
type ApiCheckIssue = Schema["ExamCheckIssue"];
type ApiCheckGroup = Schema["ExamCheckGroup"];
type ApiCheckChoice = Schema["ExamCheckChoice"];
type ApiCheckRequirement = Schema["ExamCheckRequirement"];
type ApiVersionResponse = Schema["ExamVersionResponse"];
type ApiVersionPath = Schema["ExamVersionPath"];
type ApiVersionChoice = Schema["ExamVersionPathChoice"];
type ApiVersionChange = Schema["ExamVersionChange"];
type ApiWorkingCopy = Schema["ExamVersionWorkingCopy"];
type ApiPreviewResult = Schema["ExamPreviewResult"];
type ApiPreviewPaper = Schema["ExamPreviewPaper"];
type ApiPreviewPart = Schema["ExamPreviewPart"];
type ApiPreviewBlock = Schema["ExamPreviewBlock"];
type ApiPreviewBlockRequirement = Schema["ExamPreviewBlockRequirement"];
type ApiPreviewItem = Schema["ExamPreviewItem"];
type ApiPreviewQuestion = Schema["ExamPreviewQuestion"];
type ApiPreviewCheck = Schema["ExamPreviewCheck"];
type ApiPreviewSubjectAllocation = Schema["ExamPreviewSubjectAllocation"];
type ApiPreviewKnowledgeStat = Schema["ExamPreviewKnowledgeStat"];
type ApiPathRef = Schema["ExamPreviewPathRef"];
type ApiPart = Schema["ExamPart"];
type ApiPartOption = Schema["ExamPartOption"];
type ApiSection = Schema["ExamSection"];
type ApiBlock = Schema["QuestionBlock"];
type ApiMatrix = Schema["BlockMatrix"];
type ApiKnowledgeScope = Schema["KnowledgeScope"];
type ApiAnswerQuotas = Schema["AnswerQuotas"];
type ApiCheckSummary = Schema["ExamCheckSummary"];

const ISSUE_CODES: Record<string, true> = Object.fromEntries(CHECK_ISSUE_CODES.map((code) => [code, true]));
const GROUPS: Record<string, true> = Object.fromEntries(CHECK_GROUPS.map((group) => [group, true]));
const QUESTION_TYPE_SET: Record<string, true> = Object.fromEntries(QUESTION_TYPES.map((type) => [type, true]));
const CHOICE_KINDS: Record<string, true> = {
  SUBJECT: true,
  COMBINATION: true,
  BRANCH: true,
  SUBJECT_CHOICE: true,
};
const PATH_RESULTS: Record<string, true> = { VALID: true, INVALID: true, UNCONFIRMED: true };
const BLOCK_RESULTS: Record<string, true> = { FOUND: true, NOT_FOUND: true, UNCONFIRMED: true };
const CHANGE_KINDS: Record<string, true> = {
  OPTION_ADDED: true,
  OPTION_REMOVED: true,
  BLOCK_ADDED: true,
  BLOCK_REMOVED: true,
  BLOCK_CHANGED: true,
  MATRIX_CHANGED: true,
  ORDER_CHANGED: true,
  QUESTIONS_REPLACED: true,
};
const INVALID_REASONS: Record<string, true> = {
  STRUCTURE_CHANGED: true,
  CONFIG_CHANGED: true,
  SOURCE_CHANGED: true,
};
const PREVIEW_CHECK_CODES: Record<string, true> = {
  NO_DUPLICATES: true,
  SETS_INTACT: true,
  TOTAL_POINTS: true,
};
const VERSION_STATES: Record<string, true> = { WORKING: true, SERVING: true, RETIRED: true };

function isCheckResult(data: object): data is ApiCheckResult {
  return "status" in data;
}

function isCheckStarted(data: object): data is Schema["ExamCheckStarted"] {
  return "runId" in data && !("status" in data);
}

function questionTypeOf(value: string | undefined): QuestionType | null {
  return value !== undefined && QUESTION_TYPE_SET[value] ? (value as QuestionType) : null;
}


function issueKey(issue: Omit<CheckIssue, "key">): string {
  return [
    issue.code,
    issue.blockId ?? issue.sectionId ?? issue.partId ?? "exam",
    issue.optionId ?? "",
    issue.gradeCode ?? "",
    issue.difficultyCode ?? "",
    issue.dimension ?? "",
    issue.competencyCode ?? "",
    issue.answerType ?? "",
    issue.subjectCount ?? "",
  ].join("|");
}

function mapIssue(issue: ApiCheckIssue): CheckIssue {
  const mapped: Omit<CheckIssue, "key"> = {
    code: issue.code !== undefined && ISSUE_CODES[issue.code] ? (issue.code as CheckIssueCode) : "INFO_MISSING",
    severity:
      issue.severity === "UNCONFIRMED" || issue.severity === "WARNING"
        ? (issue.severity as CheckIssueSeverity)
        : "ERROR",
    group: issue.group !== undefined && GROUPS[issue.group] ? (issue.group as CheckGroup) : "INFO",
    partId: issue.partId ?? issue.location?.partId ?? null,
    partName: issue.partName ?? null,
    sectionId: issue.sectionId ?? issue.location?.sectionId ?? null,
    subjectCode: issue.subjectCode ?? null,
    blockId: issue.blockId ?? issue.location?.blockId ?? null,
    blockName: issue.blockName ?? null,
    optionId: issue.optionId ?? null,
    gradeCode: issue.gradeCode ?? null,
    difficultyCode: issue.difficultyCode ?? null,
    dimension:
      issue.dimension === "GRADE" || issue.dimension === "DIFFICULTY" || issue.dimension === "COMPETENCY"
        ? issue.dimension
        : null,
    competencyCode: issue.competencyCode ?? null,
    answerType: questionTypeOf(issue.answerType),
    subjectCount: issue.subjectCount ?? null,
    required: issue.required ?? null,
    available: issue.available ?? null,
    timeLimitSeconds: issue.timeLimitSeconds ?? null,
    questionCodes: issue.questionCodes ?? null,
    affectedCodes: issue.affectedCodes ?? null,
    slotIndex: issue.location?.slotIndex ?? null,
    childPosition: issue.location?.childPosition ?? null,
  };
  return { key: issue.key ?? issueKey(mapped), ...mapped };
}

function mapChoice(choice: ApiCheckChoice | ApiVersionChoice | Schema["ExamPreviewPathChoice"]): PathChoice {
  return {
    partId: choice.partId ?? "",
    optionId: choice.optionId ?? "",
    kind:
      choice.kind !== undefined && CHOICE_KINDS[choice.kind ?? ""]
        ? (choice.kind as PathChoice["kind"])
        : "SUBJECT",
    code: choice.code ?? "",
    name: choice.name ?? choice.code ?? "",
  };
}

function mapRequirement(requirement: ApiCheckRequirement | ApiPreviewBlockRequirement | undefined): BlockRequirement | null {
  if (!requirement) return null;
  const dimensions = (items: { code?: string; required?: number }[] | undefined) =>
    (items ?? []).map((item) => ({ code: item.code ?? "", required: item.required ?? 0 }));
  return {
    mode: requirement.mode === "SEPARATE" ? "SEPARATE" : "COMBINED",
    cells: (requirement.cells ?? []).map((cell) => ({
      gradeCode: cell.gradeCode ?? "",
      difficultyCode: cell.difficultyCode ?? "",
      required: cell.required ?? 0,
    })),
    byGrade: dimensions(requirement.byGrade),
    byDifficulty: dimensions(requirement.byDifficulty),
    byCompetency: dimensions(requirement.byCompetency),
  };
}

function mapCheckBlock(block: ApiCheckBlock): BlockCheck {
  return {
    partId: block.partId ?? "",
    partName: block.partName ?? "",
    partOrder: block.partOrder ?? 0,
    sectionId: block.sectionId ?? "",
    subjectCode: block.subjectCode ?? "",
    optionId: block.optionId ?? null,
    blockId: block.blockId ?? "",
    blockName: block.blockName ?? "",
    questionType: questionTypeOf(block.questionType),
    required: block.required ?? 0,
    setCount: block.setCount ?? null,
    setSize: block.setSize ?? null,
    available: block.available ?? 0,
    availableSets: block.availableSets ?? null,
    requirement: mapRequirement(block.requirement),
    result:
      block.result !== undefined && BLOCK_RESULTS[block.result]
        ? (block.result as BlockCheckResult)
        : "NOT_FOUND",
    issues: (block.issues ?? []).map(mapIssue),
  };
}

function mapCheckSubject(subject: ApiCheckSubject): SubjectCheck {
  const dimensions = (items: { code?: string; required?: number; achieved?: number }[] | undefined) =>
    (items ?? []).map((item) => ({ code: item.code ?? "", required: item.required ?? 0, achieved: item.achieved ?? 0 }));
  return {
    sectionId: subject.sectionId ?? "",
    subjectCode: subject.subjectCode ?? "",
    optionId: subject.optionId ?? null,
    questionCount: subject.questionCount ?? null,
    answerQuotas: (subject.answerQuotas ?? []).map((quota) => ({
      answerType: questionTypeOf(quota.answerType) ?? "SINGLE_CHOICE",
      required: quota.required ?? 0,
      achieved: quota.achieved ?? 0,
    })),
    byGrade: dimensions(subject.byGrade),
    byDifficulty: dimensions(subject.byDifficulty),
  };
}

function mapSelection(selection: ApiCheckSelection): PathSelectionCheck {
  return {
    subjectCodes: selection.subjectCodes ?? [],
    counts: selection.counts ?? [],
    result:
      selection.result !== undefined && PATH_RESULTS[selection.result]
        ? (selection.result as PathCheckResult)
        : "UNCONFIRMED",
    issues: (selection.issues ?? []).map(mapIssue),
  };
}

function mapCheckPath(path: ApiCheckPath): PathCheck {
  return {
    pathId: path.pathId ?? "",
    choices: (path.choices ?? []).map(mapChoice),
    result:
      path.result !== undefined && PATH_RESULTS[path.result]
        ? (path.result as PathCheckResult)
        : "UNCONFIRMED",
    partIds: path.partIds ?? [],
    totalQuestions: path.totalQuestions ?? 0,
    totalMinutes: path.totalMinutes ?? 0,
    totalPoints: path.totalPoints ?? 0,
    blocks: (path.blocks ?? []).map(mapCheckBlock),
    subjects: (path.subjects ?? []).map(mapCheckSubject),
    selections: (path.selections ?? []).map(mapSelection),
    issues: (path.issues ?? []).map(mapIssue),
  };
}

function mapGroup(group: ApiCheckGroup): CheckGroupResult {
  return {
    group: group.group !== undefined && GROUPS[group.group] ? (group.group as CheckGroup) : "INFO",
    status: group.status === "PASS" || group.status === "WARN" ? group.status : "FAIL",
    issues: (group.issues ?? []).map(mapIssue),
  };
}

function mapCheckResult(result: ApiCheckResult): ExamCheckResult {
  return {
    examId: result.examId ?? "",
    checkedAt: result.checkedAt ?? "",
    durationSeconds: result.durationSeconds ?? 0,
    timeLimitSeconds: result.timeLimitSeconds ?? 0,
    stale: result.stale ?? false,
    invalidReason:
      result.invalidReason !== undefined &&
      result.invalidReason !== null &&
      INVALID_REASONS[result.invalidReason]
        ? (result.invalidReason as CheckInvalidReason)
        : null,
    status: result.status === "VALID" ? "VALID" : "INVALID",
    issueCount: result.issueCount ?? 0,
    paths: (result.paths ?? []).map(mapCheckPath),
    groups: (result.groups ?? []).map(mapGroup),
    partCount: result.partCount ?? 0,
    totalPoints: result.totalPoints ?? 0,
    printedQuestions: result.printedQuestions ?? 0,
    maxScore: result.maxScore ?? null,
    sectionStates: mapSectionStates(result.sectionStates),
  };
}

/* ---------- Xem trước ---------- */

function mapPreviewCheck(check: ApiPreviewCheck): PreviewCheck {
  return {
    code:
      check.code !== undefined && PREVIEW_CHECK_CODES[check.code]
        ? (check.code as PreviewCheckCode)
        : "TOTAL_POINTS",
    status: check.status === "PASS" || check.status === "NOT_APPLICABLE" ? check.status : "FAIL",
    value: check.value ?? null,
  };
}

function mapPreviewQuestion(question: ApiPreviewQuestion): PreviewQuestion {
  return {
    number: question.number ?? 0,
    questionId: question.questionId ?? "",
    code: question.code ?? "",
    version: question.version ?? null,
    type: questionTypeOf(question.type) ?? "SINGLE_CHOICE",
    simulated: question.simulated ?? false,
    content: question.content ?? null,
    gradeCode: question.gradeCode ?? "",
    difficultyCode: question.difficultyCode ?? "",
    knowledge: question.knowledge
      ? {
          nodeId: question.knowledge.nodeId ?? "",
          code: question.knowledge.code ?? "",
          name: question.knowledge.name ?? "",
        }
      : null,
    points: question.points ?? 0,
    options: question.options
      ? question.options.map((option) => ({
          id: option.id ?? "",
          content: option.content ?? "",
          correct: option.correct ?? false,
        }))
      : null,
    answer: question.answer ?? null,
    solution: question.solution ?? null,
    rubric: question.rubric
      ? question.rubric.map((item) => ({
          id: item.id ?? "",
          description: item.description ?? "",
          points: item.points ?? 0,
        }))
      : null,
    // `body` của API là JsonNode; hình dạng JSON khớp `QuestionBody` của ngân hàng câu hỏi (spec 9.2).
    body: (question.body ?? null) as QuestionBody | null,
    originalNumber: question.originalNumber ?? null,
    optionOrder: question.optionOrder ?? null,
    originalOptionOrder: question.originalOptionOrder ?? null,
    assignedTopicQuotaIds: question.assignedTopicQuotaIds ?? [],
  };
}

function mapPreviewItem(item: ApiPreviewItem): PreviewItem {
  if (item.kind === "SET") {
    return {
      kind: "SET",
      setId: item.setId ?? "",
      code: item.code ?? "",
      version: item.version ?? null,
      simulated: item.simulated ?? false,
      passage: item.passage ?? null,
      children: (item.children ?? []).map(mapPreviewQuestion),
      originalOrder: item.originalOrder ?? null,
    };
  }
  return { kind: "QUESTION", question: mapPreviewQuestion(item.question ?? {}), originalOrder: item.originalOrder ?? null };
}

function emptyRequirement(): BlockRequirement {
  return { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] };
}

function mapPreviewBlock(block: ApiPreviewBlock): PreviewBlock {
  return {
    blockId: block.blockId ?? "",
    blockName: block.blockName ?? "",
    subjectCode: block.subjectCode ?? "",
    questionType: questionTypeOf(block.questionType),
    manualGrading: block.manualGrading ?? false,
    pointsEach: block.pointsEach ?? 0,
    firstNumber: block.firstNumber ?? 0,
    lastNumber: block.lastNumber ?? 0,
    questionCount: block.questionCount ?? 0,
    setCount: block.setCount ?? null,
    setSize: block.setSize ?? null,
    requirement: mapRequirement(block.requirement) ?? emptyRequirement(),
    allocation: (block.allocation ?? []).map(
      (cell): PreviewAllocationCell => ({
        gradeCode: cell.gradeCode ?? "",
        difficultyCode: cell.difficultyCode ?? "",
        required: cell.required ?? null,
        generated: cell.generated ?? 0,
      }),
    ),
    items: (block.items ?? []).map(mapPreviewItem),
  };
}

function mapPreviewPart(part: ApiPreviewPart): PreviewPart {
  return {
    partId: part.partId ?? "",
    name: part.name ?? "",
    order: part.order ?? 0,
    durationMinutes: part.durationMinutes ?? null,
    firstNumber: part.firstNumber ?? 0,
    lastNumber: part.lastNumber ?? 0,
    points: part.points ?? 0,
    maxPoints: part.maxPoints ?? null,
    blocks: (part.blocks ?? []).map(mapPreviewBlock),
  };
}

function mapPreviewSubject(subject: ApiPreviewSubjectAllocation): PreviewSubject {
  const quotas: PreviewSubjectQuota[] = [
    ...(subject.answerQuotas ?? []).map((quota) => ({
      kind: "ANSWER_TYPE" as const,
      code: quota.answerType ?? "",
      required: quota.required ?? 0,
      generated: quota.achieved ?? 0,
    })),
    ...(subject.byGrade ?? []).map((item) => ({
      kind: "GRADE" as const,
      code: item.code ?? "",
      required: item.required ?? 0,
      generated: item.achieved ?? 0,
    })),
    ...(subject.byDifficulty ?? []).map((item) => ({
      kind: "DIFFICULTY" as const,
      code: item.code ?? "",
      required: item.required ?? 0,
      generated: item.achieved ?? 0,
    })),
  ];
  return {
    sectionId: subject.sectionId ?? "",
    subjectCode: subject.subjectCode ?? "",
    questionCount: subject.questionCount ?? 0,
    quotas,
  };
}

function mapPreviewPaper(paper: ApiPreviewPaper): PreviewPaper {
  return {
    totalQuestions: paper.totalQuestions ?? 0,
    totalPoints: paper.totalPoints ?? 0,
    totalMinutes: paper.totalMinutes ?? 0,
    sharedDuration: paper.sharedDuration ?? false,
    hasManualGrading: paper.hasManualGrading ?? false,
    parts: (paper.parts ?? []).map(mapPreviewPart),
    subjects: (paper.subjects ?? []).map(mapPreviewSubject),
    checks: (paper.checks ?? []).map(mapPreviewCheck),
    knowledgeStats: (paper.knowledgeStats ?? []).map(
      (stat: ApiPreviewKnowledgeStat) => ({
        nodeId: stat.nodeId ?? "",
        code: stat.code ?? "",
        name: stat.name ?? "",
        subjectCode: stat.subjectCode ?? "",
        count: stat.count ?? 0,
      }),
    ),
  };
}

function mapPathRef(path: ApiPathRef | undefined): ExamPathRef {
  return { pathId: path?.pathId ?? "", choices: (path?.choices ?? []).map(mapChoice) };
}

function mapCompliance(row: Schema["ExamPreviewCompliance"]): PreviewCompliance {
  const basis = row.basis ?? "QUESTION";
  return {
    criterion: row.criterion ?? "",
    scope: row.scope ?? "",
    required: row.required ?? 0,
    actual: row.actual ?? 0,
    enforcement: row.enforcement === "REFERENCE" ? "REFERENCE" : "REQUIRED",
    basis: basis.startsWith("POINTS") ? "POINTS" : basis.startsWith("PROMPT") ? "PROMPT" : "QUESTION",
    independent: basis.endsWith("_INDEPENDENT"),
    note: row.note ?? null,
  };
}

function mapPreviewResult(examId: string, result: ApiPreviewResult): PreviewResult {
  const base = {
    examId: result.examId ?? examId,
    path: mapPathRef(result.path),
    version: result.version ?? null,
    seed: result.seed ?? 0,
    generatedAt: result.generatedAt ?? "",
  };
  if (result.status === "GENERATED" && result.paper) {
    return {
      ...base,
      status: "GENERATED",
      paper: mapPreviewPaper(result.paper),
      previewId: result.previewId ?? null,
      compliance: (result.compliance ?? []).map(mapCompliance),
      droppedQuestions: (result.droppedQuestions ?? []).map(mapPreviewQuestion),
    };
  }
  return {
    ...base,
    status: "FAILED",
    reason: result.reason === "INVALID" ? "INVALID" : "UNCONFIRMED",
    issues: (result.issues ?? []).map(mapIssue),
  };
}

/* ---------- Phiên bản ---------- */

function mapVersionPath(path: ApiVersionPath): ExamPathRef {
  return { pathId: path.pathId ?? "", choices: (path.choices ?? []).map(mapChoice) };
}

function mapVersionRow(row: ApiVersionResponse & { version: number }): ExamVersionRow {
  return {
    version: row.version,
    state: row.state !== undefined && VERSION_STATES[row.state]
      ? (row.state as ExamVersion["state"])
      : "RETIRED",
    publishedAt: row.publishedAt ?? null,
    publishedBy: row.publishedBy ? mapPerson(row.publishedBy) : null,
    attemptCount: row.attemptCount ?? 0,
    note: row.note ?? null,
    publishedPaths: (row.publishedPaths ?? []).map(mapVersionPath),
    servingPaths: (row.servingPaths ?? []).map(mapVersionPath),
  };
}

function emptyVersionRow(version: number): ExamVersionRow {
  return {
    version,
    state: "RETIRED",
    publishedAt: null,
    publishedBy: null,
    attemptCount: 0,
    note: null,
    publishedPaths: [],
    servingPaths: [],
  };
}

function mapPerson(person: { id: string; name?: string | null }): PersonRef {
  return { id: person.id, name: person.name ?? "" };
}

function mapChange(change: ApiVersionChange): ExamChange {
  return {
    id: change.id ?? "",
    kind:
      change.kind !== undefined && CHANGE_KINDS[change.kind]
        ? (change.kind as ExamChangeKind)
        : "BLOCK_CHANGED",
    where: change.where ?? "",
    before: change.before ?? null,
    after: change.after ?? null,
    blockId: change.blockId ?? null,
    warning:
      change.warning === "UNCONFIRMED" || change.warning === "INVALID" ? change.warning : null,
  };
}

function mapWorkingCopy(copy: ApiWorkingCopy): ExamWorkingCopy {
  const summary = copy.check;
  return {
    editedBy: copy.editedBy ? mapPerson(copy.editedBy) : { id: "", name: "" },
    savedAt: copy.savedAt ?? "",
    baseVersion: copy.baseVersion ?? null,
    note: copy.note ?? null,
    check: mapCheckSummary(summary),
    unpublishedPaths: (copy.unpublishedPaths ?? []).map(mapVersionPath),
  };
}

function mapCheckSummary(summary: ApiCheckSummary | undefined): ExamWorkingCopy["check"] {
  const status = summary?.status;
  return {
    status:
      status === "VALID" || status === "INVALID" || status === "NEEDS_RECHECK" ? status : "UNCHECKED",
    issueCount: summary?.issueCount ?? 0,
    checkedAt: summary?.checkedAt ?? null,
  };
}

/* ---------- Cấu trúc (chi tiết phiên bản, discontinue trả về) ---------- */

function mapKnowledgeScope(scope: ApiKnowledgeScope | undefined): ExamSection["knowledgeScope"] {
  if (!scope) return null;
  return {
    nodeIds: scope.nodeIds ?? [],
    ...(scope.competencyCodes !== undefined ? { competencyCodes: scope.competencyCodes } : {}),
  };
}

function mapMatrix(matrix: ApiMatrix | undefined): BlockMatrix | null {
  if (!matrix) return null;
  const values = (items: { code?: string; value?: number }[] | undefined) =>
    (items ?? []).map((item) => ({ code: item.code ?? "", value: item.value ?? 0 }));
  return {
    mode: matrix.mode === "SEPARATE" ? "SEPARATE" : "COMBINED",
    unit: matrix.unit === "PERCENT" ? "PERCENT" : "COUNT",
    cells: (matrix.cells ?? []).map((cell) => ({
      gradeCode: cell.gradeCode ?? "",
      difficultyCode: cell.difficultyCode ?? "",
      value: cell.value ?? 0,
    })),
    byGrade: values(matrix.byGrade),
    byDifficulty: values(matrix.byDifficulty),
    ...(matrix.byCompetency !== undefined ? { byCompetency: values(matrix.byCompetency) } : {}),
  };
}

function mapBlock(block: ApiBlock): QuestionBlock {
  return {
    id: block.id ?? "",
    name: block.name ?? "",
    questionType: questionTypeOf(block.questionType),
    questionCount: block.questionCount ?? null,
    setCount: block.setCount ?? null,
    setSize: block.setSize ?? null,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion ?? null,
    knowledgeScope: mapKnowledgeScope(block.knowledgeScope),
    tagCodes: block.tagCodes ?? [],
    matrix: mapMatrix(block.matrix),
    childAnswerType: questionTypeOf(block.childAnswerType),
    templateGroupCode: block.templateGroupCode ?? null,
    answerTypeLocked: block.answerTypeLocked ?? false,
    ...(block.expectedScoring !== undefined
      ? {
          expectedScoring: {
            ...(block.expectedScoring.trueFalseMethod !== undefined
              ? { trueFalseMethod: block.expectedScoring.trueFalseMethod as TrueFalseMethod }
              : {}),
            ...(block.expectedScoring.trueFalseTable !== undefined
              ? { trueFalseTable: block.expectedScoring.trueFalseTable }
              : {}),
            ...(block.expectedScoring.multipleChoiceRule !== undefined
              ? { multipleChoiceRule: block.expectedScoring.multipleChoiceRule as MultipleChoiceRule }
              : {}),
          },
        }
      : {}),
  };
}

function mapSection(section: ApiSection): ExamSection {
  return {
    id: section.id ?? "",
    subjectCode: section.subjectCode ?? "",
    questionCount: section.questionCount ?? null,
    knowledgeScope: mapKnowledgeScope(section.knowledgeScope),
    answerQuotas: mapAnswerQuotas(section.answerQuotas),
    matrix: mapMatrix(section.matrix),
    choiceGroup:
      section.choiceGroup === "NATURAL" || section.choiceGroup === "SOCIAL"
        ? (section.choiceGroup as ChoiceGroup)
        : null,
    blocks: (section.blocks ?? []).map(mapBlock),
  };
}

function mapAnswerQuotas(quotas: ApiAnswerQuotas | undefined): AnswerQuotas | null {
  if (!quotas) return null;
  return {
    unit: quotas.unit === "PERCENT" ? "PERCENT" : "COUNT",
    items: (quotas.items ?? [])
      .map((item) => ({ answerType: questionTypeOf(item.answerType), value: item.value ?? 0 }))
      .filter((item): item is { answerType: QuestionType; value: number } => item.answerType !== null),
  };
}

function mapOption(option: ApiPartOption): ExamPartOption {
  return {
    id: option.id ?? "",
    kind:
      option.kind !== undefined && CHOICE_KINDS[option.kind ?? ""]
        ? option.kind
        : "SUBJECT",
    code: option.code ?? "",
    ...(option.name !== undefined ? { name: option.name } : {}),
    sections: (option.sections ?? []).map(mapSection),
    chooseSubjects: option.chooseSubjects ?? null,
    countsByPick: option.countsByPick ?? null,
    ...(option.expertConfirmed !== undefined ? { expertConfirmed: option.expertConfirmed } : {}),
  };
}

function mapPart(part: ApiPart): ExamPart {
  return {
    id: part.id ?? "",
    order: part.order ?? 0,
    name: part.name ?? "",
    durationMinutes: part.durationMinutes ?? null,
    questionCount: part.questionCount ?? 0,
    questionCountOnPaper: part.questionCountOnPaper ?? part.questionCount ?? 0,
    questionCountRequired: part.questionCountRequired ?? part.questionCount ?? 0,
    participation: {
      mode: part.participation?.mode === "CHOICE" ? "CHOICE" : "ALL",
      choose: part.participation?.choose ?? null,
    },
    ...(part.maxPoints !== undefined ? { maxPoints: part.maxPoints } : {}),
    mode: part.options !== undefined && part.options.length > 0 ? "CHOICE" : "FIXED",
    sections: (part.sections ?? []).map(mapSection),
    options: (part.options ?? []).map(mapOption),
  };
}
