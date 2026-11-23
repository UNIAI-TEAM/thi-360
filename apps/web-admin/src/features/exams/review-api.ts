import type { components } from "@thi360/api-client";
import { QUESTION_TYPES, type PersonRef, type QuestionType } from "@/features/content/types";
import type { QuestionBody } from "@/features/questions/types";
import { createBrowserApiClient } from "@/lib/api/client";
import { mapDisplaySegments, mapParts, mapSectionStates } from "./api-mapping";
import { checkRequiredReason, errorCodeOf, ExamsApiError, toExamsApiError } from "./errors";
import {
  CHECK_GROUPS,
  type BlockCheck,
  type BlockCheckResult,
  type BlockRequirement,
  type CheckGroup,
  type CheckGroupResult,
  type CheckInvalidReason,
  type CheckIssue,
  type CheckIssueCode,
  type CheckIssueSeverity,
  type CheckRunState,
  type ExamChange,
  type ExamChangeKind,
  type ExamCheckResult,
  type ExamCheckRun,
  type ExamPathRef,
  type ExamVersionDetail,
  type ExamVersionList,
  type ExamVersionRow,
  type ExamWorkingCopy,
  type GeneratePreviewRequest,
  type ListingCheckGroup,
  type ListingCheckIssue,
  type PathCheck,
  type PathCheckResult,
  type PathChoice,
  type PathSelectionCheck,
  type PreviewAllocationCell,
  type PreviewAttachment,
  type PreviewBlock,
  type PreviewCheck,
  type PreviewCheckCode,
  type PreviewCompliance,
  type PreviewItem,
  type PreviewPaper,
  type PreviewPart,
  type PreviewQuestion,
  type PreviewResult,
  type PreviewSubject,
  type PreviewSubjectQuota,
  type PublishExamRequest,
  type PublishExamResponse,
  type RunExamCheckRequest,
  type StartDeepCheckResponse,
  type SubjectCheck,
} from "./review-types";
import type { ExamVersion } from "./types";

export { checkRequiredReason, errorCodeOf, ExamsApiError, toExamsApiError };

type Schema = components["schemas"];

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
    body: { scope: scope ?? undefined, partId: partId ?? undefined, sectionId: sectionId ?? undefined },
  });
  if (!response.ok || data === undefined || !("status" in data)) throw toExamsApiError(response.status, error);
  return mapCheckResult(data);
}

export async function startDeepCheck(examId: string): Promise<StartDeepCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/check", {
    params: { path: { examId }, query: { deep: true } },
  });
  if (!response.ok || data === undefined || !("runId" in data) || "status" in data || data.runId === undefined) {
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
      .filter((item): item is Schema["ExamVersionResponse"] & { version: number } => item.version !== undefined)
      .map(mapVersionRow),
  };
}

export async function getExamVersion({ examId, version }: { examId: string; version: number }): Promise<ExamVersionDetail> {
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
    params: { path: { examId }, header: { "Idempotency-Key": idempotencyKey ?? crypto.randomUUID() } },
    body: { pathIds, ...(note !== null ? { note } : {}), expectedRevision },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    examId: data.examId ?? examId,
    version: data.version ?? 0,
    publishedPathIds: data.publishedPathIds ?? [],
    keptPaths: (data.keptPaths ?? []).map((path) => ({ pathId: path.pathId ?? "", version: path.version ?? 0 })),
  };
}

const ISSUE_DIMENSIONS = ["GRADE", "DIFFICULTY", "COMPETENCY", "COUNT", "SET_COUNT", "SET_SIZE", "TYPE", "COMMANDS", "POINTS"] as const;

const checkGroupOf = (value?: string): CheckGroup =>
  value != null && (CHECK_GROUPS as readonly string[]).includes(value) ? (value as CheckGroup) : "INFO";

const questionTypeOf = (value?: string): QuestionType | null =>
  value != null && (QUESTION_TYPES as readonly string[]).includes(value) ? (value as QuestionType) : null;

const dimensionOf = (value?: string): CheckIssue["dimension"] =>
  value != null && (ISSUE_DIMENSIONS as readonly string[]).includes(value) ? (value as CheckIssue["dimension"]) : null;

const pathResultOf = (value?: string): PathCheckResult =>
  value === "VALID" || value === "INVALID" ? value : "UNCONFIRMED";

const invalidReasonOf = (value?: string | null): CheckInvalidReason | null =>
  value === "STRUCTURE_CHANGED" ||
  value === "CONFIG_CHANGED" ||
  value === "SOURCE_CHANGED" ||
  value === "RULES_CHANGED"
    ? value
    : null;

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

export function mapIssue(issue: Schema["ExamCheckIssue"] | Schema["ExamPreviewIssue"]): CheckIssue {
  const mapped: Omit<CheckIssue, "key"> = {
    code: (issue.code || "INFO_MISSING") as CheckIssueCode,
    severity:
      issue.severity === "UNCONFIRMED" || issue.severity === "WARNING" ? (issue.severity as CheckIssueSeverity) : "ERROR",
    group: checkGroupOf(issue.group),
    partId: issue.partId ?? issue.location?.partId ?? null,
    partName: issue.partName ?? null,
    sectionId: issue.sectionId ?? issue.location?.sectionId ?? null,
    subjectCode: issue.subjectCode ?? null,
    blockId: issue.blockId ?? issue.location?.blockId ?? null,
    blockName: issue.blockName ?? null,
    optionId: issue.optionId ?? null,
    gradeCode: issue.gradeCode ?? null,
    difficultyCode: issue.difficultyCode ?? null,
    dimension: dimensionOf(issue.dimension),
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

function mapChoice(
  choice: Schema["ExamCheckChoice"] | Schema["ExamVersionPathChoice"] | Schema["ExamPreviewPathChoice"],
): PathChoice {
  return {
    partId: choice.partId ?? "",
    optionId: choice.optionId ?? "",
    kind: (choice.kind ?? "SUBJECT") as PathChoice["kind"],
    code: choice.code ?? "",
    name: choice.name ?? choice.code ?? "",
  };
}

function mapPathRef(path?: { pathId?: string; choices?: Schema["ExamPreviewPathChoice"][] }): ExamPathRef {
  return { pathId: path?.pathId ?? "", choices: (path?.choices ?? []).map(mapChoice) };
}

function mapRequirement(
  requirement?: Schema["ExamCheckRequirement"] | Schema["ExamPreviewBlockRequirement"],
): BlockRequirement | null {
  if (!requirement) return null;
  const dimensions = (items?: { code?: string; required?: number }[]) =>
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

function mapCheckBlock(block: Schema["ExamCheckBlock"]): BlockCheck {
  const result: BlockCheckResult =
    block.result === "FOUND" || block.result === "UNCONFIRMED" ? block.result : "NOT_FOUND";
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
    result,
    issues: (block.issues ?? []).map(mapIssue),
  };
}

function mapCheckSubject(subject: Schema["ExamCheckSubject"]): SubjectCheck {
  const dimensions = (items?: { code?: string; required?: number; achieved?: number }[]) =>
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

function mapSelection(selection: Schema["ExamCheckSelection"]): PathSelectionCheck {
  return {
    subjectCodes: selection.subjectCodes ?? [],
    counts: selection.counts ?? [],
    result: pathResultOf(selection.result),
    issues: (selection.issues ?? []).map(mapIssue),
  };
}

function mapCheckPath(path: Schema["ExamCheckPath"]): PathCheck {
  return {
    ...mapPathRef(path),
    result: pathResultOf(path.result),
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

function mapListingIssue(issue: Schema["ExamListingCheckIssue"]): ListingCheckIssue {
  const code = issue.code ?? "";
  return {
    key: issue.key ?? `LISTING:${code}`,
    code,
    severity: issue.severity === "WARNING" ? "WARNING" : "ERROR",
    field: issue.field ?? null,
    minPriceVnd: issue.minPriceVnd ?? null,
  };
}

function mapListingCheck(group?: Schema["ExamListingCheckGroup"] | null): ListingCheckGroup | null {
  if (!group) return null;
  return {
    status: group.status === "PASS" || group.status === "WARN" ? group.status : "FAIL",
    issues: (group.issues ?? []).map(mapListingIssue),
  };
}

export function mapListingCheckIssues(issues?: readonly Schema["ExamListingCheckIssue"][]): ListingCheckIssue[] {
  return (issues ?? []).map(mapListingIssue);
}

function mapCheckResult(result: Schema["ExamCheckResult"]): ExamCheckResult {
  return {
    examId: result.examId ?? "",
    checkedAt: result.checkedAt ?? "",
    durationSeconds: result.durationSeconds ?? 0,
    timeLimitSeconds: result.timeLimitSeconds ?? 0,
    stale: result.stale ?? false,
    invalidReason: invalidReasonOf(result.invalidReason),
    status: result.status === "VALID" ? "VALID" : "INVALID",
    issueCount: result.issueCount ?? 0,
    paths: (result.paths ?? []).map(mapCheckPath),
    groups: (result.groups ?? []).map((group): CheckGroupResult => ({
      group: checkGroupOf(group.group),
      status: group.status === "PASS" || group.status === "WARN" ? group.status : "FAIL",
      issues: (group.issues ?? []).map(mapIssue),
    })),
    listingCheck: mapListingCheck(result.listingCheck),
    partCount: result.partCount ?? 0,
    totalPoints: result.totalPoints ?? 0,
    printedQuestions: result.printedQuestions ?? 0,
    maxScore: result.maxScore ?? null,
    sectionStates: mapSectionStates(result.sectionStates),
  };
}

export function mapAttachments(
  attachments?: readonly { id?: string; name?: string; mime?: string }[],
): PreviewAttachment[] {
  return (attachments ?? []).map((att) => ({ id: att.id ?? "", name: att.name ?? "", mime: att.mime ?? "" }));
}

function mapPreviewQuestion(question: Schema["ExamPreviewQuestion"]): PreviewQuestion {
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
      ? question.options.map((opt) => ({ id: opt.id ?? "", content: opt.content ?? "", correct: opt.correct ?? false }))
      : null,
    answer: question.answer ?? null,
    solution: question.solution ?? null,
    rubric: question.rubric
      ? question.rubric.map((item) => ({ id: item.id ?? "", description: item.description ?? "", points: item.points ?? 0 }))
      : null,
    body: (question.body ?? null) as QuestionBody | null,
    originalNumber: question.originalNumber ?? null,
    optionOrder: question.optionOrder ?? null,
    originalOptionOrder: question.originalOptionOrder ?? null,
    assignedTopicQuotaIds: question.assignedTopicQuotaIds ?? [],
    attachments: mapAttachments(question.attachments),
  };
}

function mapPreviewItem(item: Schema["ExamPreviewItem"]): PreviewItem {
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

function mapPreviewBlock(block: Schema["ExamPreviewBlock"]): PreviewBlock {
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
    requirement: mapRequirement(block.requirement) ?? {
      mode: "COMBINED",
      cells: [],
      byGrade: [],
      byDifficulty: [],
      byCompetency: [],
    },
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

function mapPreviewPart(part: Schema["ExamPreviewPart"]): PreviewPart {
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

function mapPreviewSubject(subject: Schema["ExamPreviewSubjectAllocation"]): PreviewSubject {
  const dimQuotas = (kind: "GRADE" | "DIFFICULTY", items?: Schema["ExamPreviewSubjectDimensionRow"][]): PreviewSubjectQuota[] =>
    (items ?? []).map((item) => ({
      kind,
      code: item.code ?? "",
      required: item.required ?? 0,
      generated: item.achieved ?? 0,
    }));
  return {
    sectionId: subject.sectionId ?? "",
    subjectCode: subject.subjectCode ?? "",
    questionCount: subject.questionCount ?? 0,
    quotas: [
      ...(subject.answerQuotas ?? []).map((q): PreviewSubjectQuota => ({
        kind: "ANSWER_TYPE",
        code: q.answerType ?? "",
        required: q.required ?? 0,
        generated: q.achieved ?? 0,
      })),
      ...dimQuotas("GRADE", subject.byGrade),
      ...dimQuotas("DIFFICULTY", subject.byDifficulty),
    ],
  };
}

function mapPreviewPaper(paper: Schema["ExamPreviewPaper"]): PreviewPaper {
  return {
    totalQuestions: paper.totalQuestions ?? 0,
    totalPoints: paper.totalPoints ?? 0,
    totalMinutes: paper.totalMinutes ?? 0,
    sharedDuration: paper.sharedDuration ?? false,
    hasManualGrading: paper.hasManualGrading ?? false,
    parts: (paper.parts ?? []).map(mapPreviewPart),
    subjects: (paper.subjects ?? []).map(mapPreviewSubject),
    checks: (paper.checks ?? []).map((check): PreviewCheck => ({
      code: (check.code ?? "TOTAL_POINTS") as PreviewCheckCode,
      status: check.status === "PASS" || check.status === "NOT_APPLICABLE" ? check.status : "FAIL",
      value: check.value ?? null,
    })),
    knowledgeStats: (paper.knowledgeStats ?? []).map((stat) => ({
      nodeId: stat.nodeId ?? "",
      code: stat.code ?? "",
      name: stat.name ?? "",
      subjectCode: stat.subjectCode ?? "",
      count: stat.count ?? 0,
    })),
  };
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

function mapPreviewResult(examId: string, result: Schema["ExamPreviewResult"]): PreviewResult {
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

const mapPerson = (person?: { id: string; name?: string | null }): PersonRef =>
  person ? { id: person.id, name: person.name ?? "" } : { id: "", name: "" };

function mapVersionRow(row: Schema["ExamVersionResponse"] & { version: number }): ExamVersionRow {
  const state: ExamVersion["state"] =
    row.state === "WORKING" || row.state === "SERVING" ? row.state : "RETIRED";
  return {
    version: row.version,
    state,
    publishedAt: row.publishedAt ?? null,
    publishedBy: row.publishedBy ? mapPerson(row.publishedBy) : null,
    attemptCount: row.attemptCount ?? 0,
    note: row.note ?? null,
    publishedPaths: (row.publishedPaths ?? []).map(mapPathRef),
    servingPaths: (row.servingPaths ?? []).map(mapPathRef),
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

function mapChange(change: Schema["ExamVersionChange"]): ExamChange {
  return {
    id: change.id ?? "",
    kind: (change.kind ?? "BLOCK_CHANGED") as ExamChangeKind,
    where: change.where ?? "",
    before: change.before ?? null,
    after: change.after ?? null,
    blockId: change.blockId ?? null,
    warning: change.warning === "UNCONFIRMED" || change.warning === "INVALID" ? change.warning : null,
  };
}

function mapWorkingCopy(copy: Schema["ExamVersionWorkingCopy"]): ExamWorkingCopy {
  const status = copy.check?.status;
  return {
    editedBy: mapPerson(copy.editedBy),
    savedAt: copy.savedAt ?? "",
    baseVersion: copy.baseVersion ?? null,
    note: copy.note ?? null,
    check: {
      status: status === "VALID" || status === "INVALID" || status === "NEEDS_RECHECK" ? status : "UNCHECKED",
      issueCount: copy.check?.issueCount ?? 0,
      checkedAt: copy.check?.checkedAt ?? null,
    },
    unpublishedPaths: (copy.unpublishedPaths ?? []).map(mapPathRef),
  };
}
