import type { components } from "@thi360/api-client";
import { QUESTION_TYPES, type PersonRef, type QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import { createBrowserApiClient } from "@/lib/api/client";
import { mapDisplaySegments, mapParts, mapSectionStates } from "./api-mapping";
import { checkRequiredReason, errorCodeOf, ExamsApiError, toExamsApiError } from "./errors";
import type {
  BlockCheck,
  BlockCheckResult,
  BlockRequirement,
  CheckGroup,
  CheckGroupResult,
  CheckInvalidReason,
  CheckIssue,
  CheckIssueCode,
  CheckIssueSeverity,
  CheckRunState,
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
  ListingCheckGroup,
  ListingCheckIssue,
  PathCheck,
  PathCheckResult,
  PathChoice,
  PathSelectionCheck,
  PreviewAllocationCell,
  PreviewAttachment,
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
} from "./review-types";
import { CHECK_GROUPS, CHECK_ISSUE_CODES } from "./review-types";
import type { ExamVersion } from "./types";

/**
 * API của Kiểm tra cấu hình, Kiểm tra kỹ, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11).
 */

export { checkRequiredReason, errorCodeOf, ExamsApiError, toExamsApiError };

/* ---------- Kiểm tra cấu hình ---------- */

export async function getExamCheck({ examId }: { examId: string }): Promise<ExamCheckResult | null> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId } },
  });
  if (!response.ok) throw toExamsApiError(response.status, error);
  return data == null ? null : mapCheckResult(data);
}

export async function runExamCheck({ examId, scope, partId, sectionId }: RunExamCheckRequest): Promise<ExamCheckResult> {
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

export async function startDeepCheck(examId: string): Promise<StartDeepCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId }, query: { deep: true } },
  });
  if (!response.ok || data === undefined || !isCheckStarted(data) || data.runId === undefined) {
    throw toExamsApiError(response.status, error);
  }
  return { runId: data.runId };
}

export async function getCheckRun(examId: string, runId: string): Promise<ExamCheckRun> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/check/runs/{runId}", {
    params: { path: { examId, runId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  const state: CheckRunState = data.state === "DONE" || data.state === "FAILED" ? data.state : "RUNNING";
  return {
    runId: data.runId ?? runId,
    state,
    result: data.result == null ? null : mapCheckResult(data.result),
    errorCode: data.errorCode ?? null,
  };
}

/* ---------- Đề xem trước ---------- */

export async function generatePreview({
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

/* ---------- Phiên bản và công bố ---------- */

export async function listExamVersions({ examId }: { examId: string }): Promise<ExamVersionList> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/versions", {
    params: { path: { examId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    examId: data.examId ?? examId,
    workingCopy: data.workingCopy ? mapWorkingCopy(data.workingCopy) : null,
    changes: (data.changes ?? []).map(mapChange),
    items: (data.items ?? [])
      .filter((item): item is ApiVersionResponse & { version: number } => item.version !== undefined)
      .map(mapVersionRow),
  };
}

export async function getExamVersion({
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
    parts: mapParts(data.parts),
  };
}

export async function publishExam({
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

/* ---------- Chuyển đổi sang kiểu của review-types.ts ---------- */

type Schema = components["schemas"];
type ApiCheckResult = Schema["ExamCheckResult"];
type ApiCheckPath = Schema["ExamCheckPath"];
type ApiCheckBlock = Schema["ExamCheckBlock"];
type ApiCheckSubject = Schema["ExamCheckSubject"];
type ApiCheckSelection = Schema["ExamCheckSelection"];
type ApiIssue = Schema["ExamCheckIssue"] | Schema["ExamPreviewIssue"];
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
type ApiCheckSummary = Schema["ExamCheckSummary"];
type ApiListingCheckGroup = Schema["ExamListingCheckGroup"];
type ApiListingCheckIssue = Schema["ExamListingCheckIssue"];

const INVALID_REASONS = ["STRUCTURE_CHANGED", "CONFIG_CHANGED", "SOURCE_CHANGED", "RULES_CHANGED"] as const;
const ISSUE_DIMENSIONS = ["GRADE", "DIFFICULTY", "COMPETENCY", "COUNT", "SET_COUNT", "SET_SIZE", "TYPE", "COMMANDS", "POINTS"] as const;
const CHOICE_KINDS = ["SUBJECT", "COMBINATION", "BRANCH", "SUBJECT_CHOICE"] as const;
const PATH_RESULTS = ["VALID", "INVALID", "UNCONFIRMED"] as const;
const BLOCK_RESULTS = ["FOUND", "NOT_FOUND", "UNCONFIRMED"] as const;
const CHANGE_KINDS = [
  "OPTION_ADDED",
  "OPTION_REMOVED",
  "BLOCK_ADDED",
  "BLOCK_REMOVED",
  "BLOCK_CHANGED",
  "MATRIX_CHANGED",
  "ORDER_CHANGED",
  "QUESTIONS_REPLACED",
] as const;
const PREVIEW_CHECK_CODES = ["NO_DUPLICATES", "SETS_INTACT", "TOTAL_POINTS"] as const;
const VERSION_STATES = ["WORKING", "SERVING", "RETIRED"] as const;

function oneOf<T extends string>(values: readonly T[], value: string | null | undefined, fallback: T): T {
  return value != null && (values as readonly string[]).includes(value) ? (value as T) : fallback;
}

function oneOfOrNull<T extends string>(values: readonly T[], value: string | null | undefined): T | null {
  return value != null && (values as readonly string[]).includes(value) ? (value as T) : null;
}

function isCheckResult(data: object): data is ApiCheckResult {
  return "status" in data;
}

function isCheckStarted(data: object): data is Schema["ExamCheckStarted"] {
  return "runId" in data && !("status" in data);
}

const questionTypeOf = (value: string | undefined): QuestionType | null => oneOfOrNull(QUESTION_TYPES, value);

function issueKey(issue: Omit<CheckIssue, "key">): string {
  return [
    issue.code,
    issue.blockId ?? issue.sectionId ?? issue.partId ?? "exam",
    issue.optionId ?? "",
    issue.gradeCode ?? "",
    issue.difficultyCode ?? "",
    issue.dimension ?? "",
    issue.segmentCode ?? "",
    issue.competencyCode ?? "",
    issue.answerType ?? "",
    issue.subjectCount ?? "",
  ].join("|");
}

export function mapIssue(issue: ApiIssue): CheckIssue {
  const mapped: Omit<CheckIssue, "key"> = {
    code: oneOf<CheckIssueCode>(CHECK_ISSUE_CODES, issue.code, "INFO_MISSING"),
    severity:
      issue.severity === "UNCONFIRMED" || issue.severity === "WARNING"
        ? (issue.severity as CheckIssueSeverity)
        : "ERROR",
    group: oneOf<CheckGroup>(CHECK_GROUPS, issue.group, "INFO"),
    partId: issue.partId ?? issue.location?.partId ?? null,
    partName: issue.partName ?? null,
    sectionId: issue.sectionId ?? issue.location?.sectionId ?? null,
    subjectCode: issue.subjectCode ?? null,
    blockId: issue.blockId ?? issue.location?.blockId ?? null,
    blockName: issue.blockName ?? null,
    optionId: issue.optionId ?? null,
    gradeCode: issue.gradeCode ?? null,
    difficultyCode: issue.difficultyCode ?? null,
    dimension: oneOfOrNull(ISSUE_DIMENSIONS, issue.dimension),
    competencyCode: issue.competencyCode ?? null,
    answerType: questionTypeOf(issue.answerType),
    subjectCount: issue.subjectCount ?? null,
    required: issue.required ?? null,
    available: issue.available ?? null,
    excludedPoints: "excludedPoints" in issue ? (issue.excludedPoints ?? null) : null,
    timeLimitSeconds: issue.timeLimitSeconds ?? null,
    questionCodes: issue.questionCodes ?? null,
    affectedCodes: "affectedCodes" in issue ? (issue.affectedCodes ?? null) : null,
    slotIndex: issue.location?.slotIndex ?? null,
    childPosition: issue.location?.childPosition ?? null,
    segmentCode: "segmentCode" in issue ? (issue.segmentCode ?? null) : null,
    ...(issue.ruleId ? { ruleId: issue.ruleId } : {}),
    ...(issue.nodeIds?.length ? { nodeIds: issue.nodeIds } : {}),
    ...("ruleIds" in issue && issue.ruleIds?.length ? { ruleIds: issue.ruleIds } : {}),
    ...("conflictKind" in issue && issue.conflictKind ? { conflictKind: issue.conflictKind } : {}),
    ...("requiredSets" in issue && issue.requiredSets != null ? { requiredSets: issue.requiredSets } : {}),
    ...("availableSets" in issue && issue.availableSets != null ? { availableSets: issue.availableSets } : {}),
    ...("availableSingles" in issue && issue.availableSingles != null ? { availableSingles: issue.availableSingles } : {}),
  };
  return { key: issue.key ?? issueKey(mapped), ...mapped };
}

function mapChoice(choice: ApiCheckChoice | ApiVersionChoice | Schema["ExamPreviewPathChoice"]): PathChoice {
  return {
    partId: choice.partId ?? "",
    optionId: choice.optionId ?? "",
    kind: oneOf<PathChoice["kind"]>(CHOICE_KINDS, choice.kind, "SUBJECT"),
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
    result: oneOf<BlockCheckResult>(BLOCK_RESULTS, block.result, "NOT_FOUND"),
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
    result: oneOf<PathCheckResult>(PATH_RESULTS, selection.result, "UNCONFIRMED"),
    issues: (selection.issues ?? []).map(mapIssue),
  };
}

function mapCheckPath(path: ApiCheckPath): PathCheck {
  return {
    pathId: path.pathId ?? "",
    choices: (path.choices ?? []).map(mapChoice),
    result: oneOf<PathCheckResult>(PATH_RESULTS, path.result, "UNCONFIRMED"),
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
    group: oneOf<CheckGroup>(CHECK_GROUPS, group.group, "INFO"),
    status: group.status === "PASS" || group.status === "WARN" ? group.status : "FAIL",
    issues: (group.issues ?? []).map(mapIssue),
  };
}

function mapListingIssue(issue: ApiListingCheckIssue): ListingCheckIssue {
  const code = issue.code ?? "";
  return {
    key: issue.key ?? `LISTING:${code}`,
    code,
    severity: issue.severity === "WARNING" ? "WARNING" : "ERROR",
    field: issue.field ?? null,
    minPriceVnd: issue.minPriceVnd ?? null,
  };
}

function mapListingCheck(group: ApiListingCheckGroup | undefined | null): ListingCheckGroup | null {
  if (!group) return null;
  return {
    status: group.status === "PASS" || group.status === "WARN" ? group.status : "FAIL",
    issues: (group.issues ?? []).map(mapListingIssue),
  };
}

/** Vấn đề tính riêng của `ExamListing.checkIssues` (không có nhóm bọc). */
export function mapListingCheckIssues(issues: readonly ApiListingCheckIssue[] | undefined): ListingCheckIssue[] {
  return (issues ?? []).map(mapListingIssue);
}

function mapCheckResult(result: ApiCheckResult): ExamCheckResult {
  return {
    examId: result.examId ?? "",
    checkedAt: result.checkedAt ?? "",
    durationSeconds: result.durationSeconds ?? 0,
    timeLimitSeconds: result.timeLimitSeconds ?? 0,
    stale: result.stale ?? false,
    invalidReason: oneOfOrNull<CheckInvalidReason>(INVALID_REASONS, result.invalidReason),
    status: result.status === "VALID" ? "VALID" : "INVALID",
    issueCount: result.issueCount ?? 0,
    paths: (result.paths ?? []).map(mapCheckPath),
    groups: (result.groups ?? []).map(mapGroup),
    listingCheck: mapListingCheck(result.listingCheck),
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
    code: oneOf<PreviewCheckCode>(PREVIEW_CHECK_CODES, check.code, "TOTAL_POINTS"),
    status: check.status === "PASS" || check.status === "NOT_APPLICABLE" ? check.status : "FAIL",
    value: check.value ?? null,
  };
}

function mapAttachments(attachments: Schema["ExamPreviewAttachment"][] | undefined): PreviewAttachment[] {
  return (attachments ?? []).map((attachment) => ({
    id: attachment.id ?? "",
    name: attachment.name ?? "",
    mime: attachment.mime ?? "",
  }));
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
    body: (question.body ?? null) as QuestionBody | null,
    originalNumber: question.originalNumber ?? null,
    optionOrder: question.optionOrder ?? null,
    originalOptionOrder: question.originalOptionOrder ?? null,
    assignedTopicQuotaIds: question.assignedTopicQuotaIds ?? [],
    attachments: mapAttachments(question.attachments),
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
      attachments: mapAttachments(item.attachments),
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
    displaySegments: mapDisplaySegments(part.displaySegments),
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
    required: row.required ?? null,
    actual: row.actual ?? 0,
    enforcement: row.enforcement === "REFERENCE" ? "REFERENCE" : "REQUIRED",
    basis: basis.startsWith("POINTS") ? "POINTS" : basis.startsWith("PROMPT") ? "PROMPT" : "QUESTION",
    independent: basis.endsWith("_INDEPENDENT"),
    note: row.note ?? null,
    originalRequired: row.originalRequired ?? null,
    topicName: row.topicName ?? null,
  };
}

function mapPreviewResult(examId: string, result: ApiPreviewResult): PreviewResult {
  const base = {
    examId: result.examId ?? examId,
    path: mapPathRef(result.path),
    version: result.version ?? null,
    seed: result.seed ?? 0,
    generatedAt: result.generatedAt ?? "",
    expiresAt: result.expiresAt ?? null,
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
    state: oneOf<ExamVersion["state"]>(VERSION_STATES, row.state, "RETIRED"),
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
    kind: oneOf<ExamChangeKind>(CHANGE_KINDS, change.kind, "BLOCK_CHANGED"),
    where: change.where ?? "",
    before: change.before ?? null,
    after: change.after ?? null,
    blockId: change.blockId ?? null,
    warning: change.warning === "UNCONFIRMED" || change.warning === "INVALID" ? change.warning : null,
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
    status: status === "VALID" || status === "INVALID" || status === "NEEDS_RECHECK" ? status : "UNCHECKED",
    issueCount: summary?.issueCount ?? 0,
    checkedAt: summary?.checkedAt ?? null,
  };
}
