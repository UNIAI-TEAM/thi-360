import type { components } from "@thi360/api-client";
import { beforeEach } from "vitest";
import { resetIdempotencyKeys } from "@/features/content/idempotency";
import { resetCatalogLookup } from "@/features/content/catalog-lookup";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference, KnowledgeNode, QuestionStatus } from "@/features/content/types";
import { fakeApi, ok, fail, withRealApi, type ApiCall, type ContentReferenceResponse } from "@/test/fake-api";
import type { QuestionDetail, QuestionSummary } from "../types";
import type { SaveChildQuestionRequest } from "../editor-types";
import { asWireBody, fromWireBody, toWireBody, type ApiQuestionBody, type ChildClassificationIds } from "../wire-body";
import questionsFixture from "@/features/grading/mock/questions.json";

/**
 * Dữ liệu và "máy chủ giả" cho test của nhánh API thật (plan B7): test đưa vào bộ câu hỏi kiểu web
 * (lấy sẵn từ `grading/mock/questions.json`, JSON thuần), bộ dựng đổi sang đúng hình
 * dạng phản hồi OpenAPI rồi đăng ký với `fakeApi`. Lệnh ghi cập nhật bản đồ nội bộ nên các lần đọc
 * sau nhìn thấy thay đổi như máy chủ thật.
 */

type Schemas = components["schemas"];
type ApiSummary = Schemas["QuestionSummary"];
type ApiDetail = Schemas["QuestionDetail"];
type ApiReadiness = Schemas["QuestionReadiness"];
type ApiVersionItem = Schemas["QuestionVersionItem"];
type ApiSnapshot = Schemas["QuestionSnapshot"];
type ApiBulkResult = Schemas["BulkQuestionStatusResult"];
type ApiUsage = Schemas["QuestionbankQuestionUsage"];
type ApiSourceRef = Schemas["QuestionbankExamSourceRef"];
type ApiRetireImpact = Schemas["QuestionRetireImpact"];
type ApiExam = Schemas["ExamDetailResponse"];
type ApiUpdateResponse = Schemas["UpdateQuestionResponse"];
type ApiKnowledgeNode = Schemas["KnowledgeNode"];
type ApiExamKind = Schemas["ExamKindOption"];

/**
 * Câu mẫu của lane Chấm bài còn cờ đảo cũ; bỏ chúng khi nạp vì lượt thi luôn tự đảo.
 */
function withoutShuffleFlag<T>(value: T): T {
  if (Array.isArray(value)) return value.map(withoutShuffleFlag) as T;
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).flatMap(([key, entry]) =>
        ["shuffleOptions", "shuffleLeft", "shuffleRight"].includes(key) ? [] : [[key, withoutShuffleFlag(entry)]],
      ),
    ) as T;
  }
  return value;
}

export const fixtureQuestions = withoutShuffleFlag(questionsFixture) as unknown as { questions: QuestionDetail[] };

export function fixtureQuestion(id: string): QuestionDetail {
  const question = fixtureQuestions.questions.find((entry) => entry.id === id);
  if (!question) throw new Error(`fixture question ${id} not found`);
  return structuredClone(question);
}

/** Bản sao toàn bộ câu mẫu (mỗi gọi một bản mới). */
export function fixtureAll(): QuestionDetail[] {
  return structuredClone(fixtureQuestions.questions);
}

/** `revision` của câu mẫu; dữ liệu cũ thiếu trường thì coi là `version` (tối thiểu 1). */
const revisionOf = (question: QuestionSummary): number => question.revision ?? Math.max(question.version ?? 0, 1);

/** `KnowledgeRef[]` của web -> `KnowledgeRef[]` của API (`nodeId` giữ nguyên). */
const apiKnowledge = (refs: QuestionSummary["knowledge"]) =>
  refs.map((ref) => ({ nodeId: ref.nodeId, code: ref.code, name: ref.name, primary: ref.primary }));

/** `QuestionSummary` của API: `version` là bản phục vụ (0 khi chưa Sẵn sàng), `workingVersion` là bản Nháp. */
export function apiSummary(web: QuestionSummary, overrides: Partial<ApiSummary> = {}): ApiSummary {
  return {
    id: web.id,
    code: web.code,
    type: web.type,
    status: web.status,
    contentPreview: web.contentPreview,
    subjectCode: web.subjectCode,
    gradeCode: web.gradeCode,
    difficultyCode: web.difficultyCode,
    points: web.points,
    knowledge: apiKnowledge(web.knowledge),
    ...(web.competencyCode != null && { competencyCode: web.competencyCode }),
    ...(web.taskTypeCode != null && { taskTypeCode: web.taskTypeCode }),
    tagCodes: [...web.tagCodes],
    hasSolution: web.hasSolution,
    version: web.status === "DRAFT" ? 0 : web.version,
    workingVersion: web.status === "DRAFT" ? web.version : (web.workingVersion ?? undefined),
    revision: revisionOf(web),
    usedInExamCount: web.usedInExamCount,
    ...(web.sourceCode != null && { sourceCode: web.sourceCode }),
    createdBy: web.createdBy,
    createdAt: web.createdAt,
    updatedAt: web.updatedAt,
    ...overrides,
  };
}

/** Ids phân loại giả của máy chủ khi lưu (map ngược được bởi `stubCatalogOptions`). */
const TEST_IDS: ChildClassificationIds = {
  gradeItemId: (code) => `item-${code}`,
  levelItemId: (code) => `item-${code}`,
};

const toChildRequests = (children: QuestionDetail[]): SaveChildQuestionRequest[] =>
  children.map((child) => ({
    id: child.id,
    type: child.type as SaveChildQuestionRequest["type"],
    content: child.content,
    body: child.body,
    solution: child.solution,
    points: child.points,
    gradeCode: child.gradeCode,
    difficultyCode: child.difficultyCode,
    knowledgeIds: child.knowledge.map((ref) => ref.nodeId),
  }));

/** `item-<mã>` (id giá trị danh mục do `stubCatalogOptions` sinh) -> mã. */
const codeOfItem = (itemId: string | null | undefined): string | null => (itemId ? itemId.replace(/^item-/, "") : null);

const KNOWLEDGE_BY_ID = new Map<string, { code: string; name: string }>();
const indexKnowledgeTree = (nodes: KnowledgeNode[]): void => {
  for (const node of nodes) {
    KNOWLEDGE_BY_ID.set(node.id, { code: node.code, name: node.name });
    indexKnowledgeTree(node.children);
  }
};
indexKnowledgeTree((referenceFixture as ContentReference).knowledgeTree);

/**
 * Thân yêu cầu tạo/sửa (dạng wire) -> thân kiểu web và câu con, dựng như máy chủ lưu rồi trả lại. Câu con giữ id đã có,
 * câu con mới nhận id từ `nextChildId`; phân loại riêng và kiến thức của câu con đọc từ `gradeItemId`, `levelItemId`,
 * `knowledgeItemIds`, thiếu thì kế thừa của bộ.
 */
function applyWireBody(target: QuestionDetail, rawBody: unknown, nextChildId: () => string): void {
  const wire = asWireBody(rawBody as ApiQuestionBody);
  if (wire.type !== "QUESTION_SET") {
    target.body = fromWireBody(wire);
    target.children = [];
    return;
  }
  const previous = new Map(target.children.map((child) => [child.id, child]));
  target.children = (wire.children ?? []).map((child, index) => {
    const id = child.id ?? nextChildId();
    const base = previous.get(id) ?? structuredClone({ ...target, children: [] });
    return {
      ...base,
      id,
      code: base.code === target.code ? `${target.code}-${index + 1}` : base.code,
      type: child.type,
      content: child.content ?? "",
      body: child.body ? fromWireBody(child.body) : base.body,
      solution: child.solution ?? null,
      points: child.points ?? 1,
      gradeCode: codeOfItem(child.gradeItemId) ?? target.gradeCode,
      difficultyCode: codeOfItem(child.levelItemId) ?? target.difficultyCode,
      knowledge: child.knowledgeItemIds
        ? child.knowledgeItemIds.map((nodeId) => ({
            nodeId,
            code: KNOWLEDGE_BY_ID.get(nodeId)?.code ?? nodeId,
            name: KNOWLEDGE_BY_ID.get(nodeId)?.name ?? nodeId,
            primary: false,
          }))
        : target.knowledge,
      children: [],
    };
  });
  target.body = fromWireBody(wire, target.children.map((child) => child.id));
}

/** `QuestionDetail` của API (body dạng wire; câu con trong `children`, tối một tầng). */
export function apiDetail(web: QuestionDetail): ApiDetail {
  return {
    attachmentIds: [...web.attachmentIds],
    body: toWireBody(web.body, toChildRequests(web.children), TEST_IDS) as ApiDetail["body"],
    children: web.children.map(apiDetail),
    commonMistakes: web.commonMistakes ? [web.commonMistakes] : [],
    content: web.content,
    ...(web.solution != null && { solution: web.solution }),
    summary: apiSummary(web),
  };
}

export const apiPage = (
  items: ApiSummary[],
  overrides: Partial<Schemas["PageResponseQuestionSummary"]> = {},
): Schemas["PageResponseQuestionSummary"] => ({
  items,
  page: 0,
  size: 20,
  totalItems: items.length,
  totalPages: 1,
  ...overrides,
});

export const apiCounts = (draft: number, ready: number, retired: number): Schemas["QuestionStatusCounts"] => ({
  draft,
  ready,
  retired,
});

export const apiReadiness = (
  ready: boolean,
  errors: { code: string; path?: string; message?: string }[] = [],
  warnings: { code: string; path?: string; message?: string }[] = [],
): ApiReadiness => ({ ready, errors, warnings });

/** Bản chụp phiên bản từ câu kiểu web (id danh mục giả dạng `item-<code>`, khớp `stubCatalogOptions`). */
export function apiSnapshot(web: QuestionDetail): ApiSnapshot {
  return {
    attachmentIds: [...web.attachmentIds],
    body: toWireBody(web.body, toChildRequests(web.children), TEST_IDS) as unknown as ApiSnapshot["body"],
    commonMistakes: web.commonMistakes ? [web.commonMistakes] : [],
    ...(web.competencyCode ? { competencyItemId: `item-${web.competencyCode}` } : {}),
    content: web.content,
    ...(web.gradeCode ? { gradeItemId: `item-${web.gradeCode}` } : {}),
    knowledge: web.knowledge.map((ref) => ({ itemId: ref.nodeId, primary: ref.primary })) as unknown as ApiSnapshot["knowledge"],
    ...(web.difficultyCode ? { levelItemId: `item-${web.difficultyCode}` } : {}),
    points: web.points,
    ...(web.solution != null && { solution: web.solution }),
    ...(web.sourceCode != null && { sourceCode: web.sourceCode }),
    ...(web.subjectCode ? { subjectItemId: `item-${web.subjectCode}` } : {}),
    tagItemIds: web.tagCodes.map((code) => `item-${code}`),
    ...(web.taskTypeCode != null && { taskTypeItemId: `item-${web.taskTypeCode}` }),
  };
}

export const apiVersionItem = (
  versionNo: number,
  state: "WORKING" | "CURRENT" | "SUPERSEDED" | "RETIRED",
  snapshot: ApiSnapshot,
  overrides: Partial<ApiVersionItem> = {},
): ApiVersionItem => ({
  versionNo,
  state,
  snapshot,
  savedAt: `2026-11-0${Math.min(versionNo, 9)}T09:00:00+07:00`,
  savedBy: { id: "u-test", name: "Tester" },
  ...overrides,
});

/**
 * `GET /catalog/options` cho mọi nhóm: mỗi mã của `reference.json` (môn, khối, mức độ, dạng bài, nhãn) có id `item-<mã>`
 * (khớp `apiSnapshot`/`toWireBody`), cộng `extra` theo nhóm.
 */
export function stubCatalogOptions(extra: Record<string, { id: string; code: string }[]> = {}): void {
  const reference = referenceFixture as ContentReference;
  const seeded = [
    ...reference.subjects,
    ...reference.grades,
    ...reference.difficulties,
    ...reference.taskTypes,
    ...reference.tags,
    ...reference.competencies,
  ].map((entry) => ({ id: `item-${entry.code}`, code: entry.code }));
  fakeApi.get("/api/v1/catalog/options", (call) => {
    const group = String((call.options?.params as { query?: { group?: string } } | undefined)?.query?.group ?? "");
    return ok(
      [...seeded, ...(extra[group] ?? [])].map((item) => ({ id: item.id, code: item.code, nameVi: item.code, displayOrder: 0, value: 0 })),
    );
  });
}

interface ExamRef {
  id: string;
  code: string;
  name: string;
  status?: "DRAFT" | "PUBLISHED" | "DISCONTINUED";
  servingVersion?: number | null;
}

export interface UsageSpec {
  /** Id bài thi đang lấy câu làm nguồn (`sourceBlocks`/`affectedBlocks`). */
  examIds: string[];
  /** Dòng nguồn đầy đủ (mã/tên/trạng thái bài, phần, lựa chọn, khối); có thì thay cho `examIds`. */
  blocks?: ApiSourceRef[];
  /** Dòng sổ đã dùng (kind ATTEMPT/PREVIEW/PUBLISH…) */
  ledger?: { examId: string; kind: string; versionNo: number }[];
  /** Thông tin bài thi theo id (thiếu thì dùng id làm mã). */
  exams?: ExamRef[];
}

const apiExam = (ref: ExamRef): ApiExam =>
  ({
    attemptCount: 0,
    check: { issueCount: 0, status: "UNCHECKED" },
    code: ref.code,
    combinationCount: 0,
    hasEnglishOption: false,
    hasEssay: false,
    hasUnpublishedChanges: false,
    id: ref.id,
    kind: "HSA",
    name: ref.name,
    parts: [],
    practiceScoring: "RAW",
    questionCount: 0,
    revision: 1,
    scope: "FULL",
    servingVersion: ref.servingVersion ?? undefined,
    status: ref.status ?? "PUBLISHED",
    timing: "SHARED",
    updatedAt: "2026-11-01T00:00:00+07:00",
    versions: [],
    yearsConfirmed: true,
  }) as ApiExam;

/**
 * "Máy chủ" câu hỏi trên `fakeApi`: GET danh sách/chi tiết/readiness, lệnh ghi cập nhật bản đồ
 * (Sẵn sàng/Ngừng, hàng loạt, nhân bản, khôi phục, sửa — kể cả 412 khi `expectedRevision` lệch).
 */
export interface QuestionServerOptions {
  /** Readiness theo id; thiếu thì `ready: true` (câu ĐÃ `READY` luôn sẵn sàng). */
  readiness?: Record<string, ApiReadiness>;
  /** Nơi đang dùng / ảnh hưởng khi ngừng, theo id câu. */
  usage?: Record<string, UsageSpec>;
  /** Readiness tính theo `evaluateReadiness` của web cho câu Nháp (mặc định: câu chưa READY coi là đủ). */
  defaultReady?: boolean;
}

export interface QuestionServer {
  questions: Map<string, QuestionDetail>;
  /** Id câu do `POST /questions` hoặc `/duplicate` tạo trong phiên test. */
  createdIds: string[];
  nextCode: number;
  /** Đặt danh sách phiên bản cho một câu (ghi đè bản mặc định). */
  setVersions(id: string, items: ApiVersionItem[]): void;
  /** Đổi readiness của một câu giữa chừng. */
  setReadiness(id: string, readiness: ApiReadiness): void;
  /** Đặt nơi đang dùng và sổ lượt dùng của một câu. */
  setUsage(id: string, spec: UsageSpec): void;
}

export function installQuestionsApi(
  seed: QuestionDetail[] = fixtureAll(),
  options: QuestionServerOptions = {},
): QuestionServer {
  resetIdempotencyKeys();
  resetCatalogLookup();

  const questions = new Map<string, QuestionDetail>(seed.map((question) => [question.id, question]));
  const usage: Record<string, UsageSpec> = { ...options.usage };
  const server = { questions, createdIds: [] as string[], nextCode: 9000 };

  const summaryFor = (id: string): ApiSummary => apiSummary(questions.get(id)!);
  const detailFor = (id: string): ApiDetail => apiDetail(questions.get(id)!);

  const readinessFor = (question: QuestionDetail): ApiReadiness => {
    const custom = options.readiness?.[question.id];
    if (custom) return custom;
    if (question.status === "READY" || options.defaultReady !== false) return { ready: true, errors: [], warnings: [] };
    return { ready: false, errors: [{ code: "NO_CORRECT_OPTION", path: "body.options[0].correct" }], warnings: [] };
  };

  const filterList = (query: Record<string, unknown>): ApiSummary[] => {
    const text = (value: unknown) => (typeof value === "string" ? value.toLowerCase() : "");
    const match = (question: QuestionDetail): boolean => {
      const q = text(query.keyword);
      if (q && !`${question.code} ${question.contentPreview}`.toLowerCase().includes(q)) return false;
      for (const key of ["subjectCode", "gradeCode", "difficultyCode", "type", "taskTypeCode", "competencyCode", "status"] as const) {
        if (query[key] !== undefined && query[key] !== null && question[key] !== query[key]) return false;
      }
      if (query.tagCodes && !question.tagCodes.includes((query.tagCodes as string[])[0] ?? "")) return false;
      if (query.createdBy !== undefined && question.createdBy.id !== query.createdBy) return false;
      if (query.hasSolution !== undefined && question.hasSolution !== query.hasSolution) return false;
      const knowledgeIds = query.knowledgeIds;
      if (Array.isArray(knowledgeIds) && knowledgeIds.length > 0) {
        const ids = knowledgeIds.map((id) => String(id));
        // MAPID khớp nút đã chọn hoặc hậu duệ của nó (máy chủ lọc theo tiền tố).
        const hit = question.knowledge.some((ref) =>
          ids.some(
            (id) =>
              ref.nodeId === id ||
              ref.nodeId.startsWith(`${id}-`) ||
              ref.code === id ||
              ref.code.startsWith(id.replace(/^kn-/, "")),
          ),
        );
        if (!hit) return false;
      }
      const day = (iso: string) => iso.slice(0, 10);
      if (query.createdFrom !== undefined && day(question.createdAt) < String(query.createdFrom)) return false;
      if (query.createdTo !== undefined && day(question.createdAt) > String(query.createdTo)) return false;
      return true;
    };
    let items = [...questions.values()].filter(match).map((question) => apiSummary(question));
    const sort = typeof query.sort === "string" ? query.sort : "";
    if (sort.includes("createdAt")) {
      items = items.sort((a, b) => (sort.endsWith("desc") ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)));
    }
    const page = typeof query.page === "number" ? query.page : 0;
    const size = typeof query.size === "number" ? query.size : items.length;
    const paged = items.slice(page * size, page * size + size);
    return items.length === paged.length && page === 0 ? items : paged;
  };

  fakeApi.get("/api/v1/admin/questions", (call) => {
    const query = (call.options?.params as { query?: Record<string, unknown> } | undefined)?.query ?? {};
    const status = query.status;
    const filtered = filterList({ ...query, status });
    const page = typeof query.page === "number" ? query.page : 0;
    const size = typeof query.size === "number" ? query.size : filtered.length || 20;
    const all = filterList({ ...query, status, page: undefined, size: undefined });
    return ok({
      items: filtered,
      page,
      size,
      totalItems: all.length,
      totalPages: Math.max(1, Math.ceil(all.length / (size || 1))),
    });
  });

  fakeApi.get("/api/v1/admin/questions/status-counts", (call) => {
    const query = (call.options?.params as { query?: Record<string, unknown> } | undefined)?.query ?? {};
    const counts = { draft: 0, ready: 0, retired: 0 };
    for (const item of filterList(query)) counts[item.status.toLowerCase() as keyof typeof counts] += 1;
    return ok(counts);
  });

  fakeApi.get("/api/v1/admin/questions/authors", () =>
    ok([...new Map([...questions.values()].map((question) => [question.createdBy.id, question.createdBy])).values()]),
  );

  fakeApi.get("/api/v1/admin/questions/{id}", (call) => {
    const id = pathId(call);
    const question = questions.get(id);
    return question ? ok(detailFor(id)) : fail(404, { code: "NOT_FOUND" });
  });

  fakeApi.get("/api/v1/admin/questions/{id}/readiness", (call) => {
    const question = questions.get(pathId(call));
    return question ? ok(readinessFor(question)) : fail(404, { code: "NOT_FOUND" });
  });

  const changeStatus = (target: QuestionStatus) => (call: ApiCall) => {
    const question = questions.get(pathId(call));
    if (!question) return fail(404, { code: "NOT_FOUND" });
    const expected = bodyOf(call).expectedRevision;
    if (typeof expected === "number" && expected !== revisionOf(question)) {
      return fail(412, { code: "QUESTION_REVISION_CONFLICT", message: "revision conflict" });
    }
    if (target === "READY" && question.status === "READY") return fail(409, { code: "QUESTION_ALREADY_READY" });
    if (target === "RETIRED" && question.status === "RETIRED") return fail(409, { code: "QUESTION_ALREADY_RETIRED" });
    if (target === "READY" && !readinessFor(question).ready) return fail(422, { code: "QUESTION_NOT_READY" });
    question.status = target;
    question.revision = revisionOf(question) + 1;
    if (target === "READY") {
      question.version = Math.max(question.version, 1);
      question.workingVersion = null;
    }
    return ok(summaryFor(question.id));
  };
  fakeApi.post("/api/v1/admin/questions/{id}/ready", changeStatus("READY"));
  fakeApi.post("/api/v1/admin/questions/{id}/retire", changeStatus("RETIRED"));

  const bulk = (target: QuestionStatus) => (call: ApiCall) => {
    const ids = (bodyOf(call).ids as string[] | undefined) ?? [];
    const updated: ApiSummary[] = [];
    const skipped: ApiBulkResult["skipped"] = [];
    for (const id of new Set(ids)) {
      const question = questions.get(id);
      if (!question) {
        skipped.push({ questionId: id, reason: "NOT_FOUND" });
        continue;
      }
      if (question.status === target) {
        skipped.push({ questionId: id, reason: "ALREADY_IN_STATUS" });
        continue;
      }
      if (target === "READY" && !readinessFor(question).ready) {
        skipped.push({ questionId: id, reason: "NOT_READY" });
        continue;
      }
      question.status = target;
      question.revision = revisionOf(question) + 1;
      updated.push(summaryFor(id));
    }
    return ok({ updated, skipped } satisfies ApiBulkResult);
  };
  fakeApi.post("/api/v1/admin/questions/bulk/ready", bulk("READY"));
  fakeApi.post("/api/v1/admin/questions/bulk/retire", bulk("RETIRED"));

  fakeApi.post("/api/v1/admin/questions", (call) => {
    const body = bodyOf(call);
    const id = `q-new-${server.nextCode++}`;
    const detail: QuestionDetail = {
      id,
      code: `CH-${String(server.nextCode).padStart(6, "0")}`,
      type: (body.type as QuestionDetail["type"]) ?? "SINGLE_CHOICE",
      status: "DRAFT",
      contentPreview: String(body.content ?? "").replace(/<[^>]+>/g, " "),
      subjectCode: String(body.subjectCode ?? ""),
      gradeCode: String(body.gradeCode ?? ""),
      difficultyCode: String(body.difficultyCode ?? ""),
      points: typeof body.points === "number" ? body.points : 1,
      knowledge: (body.knowledge as QuestionDetail["knowledge"] | undefined) ?? [],
      competencyCode: (body.competencyCode as string | undefined) ?? null,
      taskTypeCode: (body.taskTypeCode as string | undefined) ?? null,
      tagCodes: (body.tagCodes as string[] | undefined) ?? [],
      hasSolution: Boolean(body.solution),
      version: 1,
      revision: 1,
      usedInExamCount: 0,
      sourceCode: (body.sourceCode as string | undefined) ?? null,
      createdBy: { id: "u-test", name: "Tester" },
      createdAt: "2026-11-17T00:00:00+07:00",
      updatedAt: "2026-11-17T00:00:00+07:00",
      content: String(body.content ?? ""),
      body: fromWireBody(asWireBody(body.body as ApiQuestionBody)),
      solution: (body.solution as string | undefined) ?? null,
      commonMistakes: ((body.commonMistakes as string[] | undefined) ?? []).join("") || null,
      attachmentIds: (body.attachmentIds as string[] | undefined) ?? [],
      children: [],
    };
    applyWireBody(detail, body.body, () => `${id}-c${server.nextCode++}`);
    questions.set(id, detail);
    server.createdIds.push(id);
    return ok(apiDetail(detail));
  });

  fakeApi.put("/api/v1/admin/questions/{id}", (call) => {
    const question = questions.get(pathId(call));
    if (!question) return fail(404, { code: "NOT_FOUND" });
    const body = bodyOf(call);
    if (body.expectedRevision !== revisionOf(question)) {
      return fail(412, { code: "QUESTION_REVISION_CONFLICT", message: "revision conflict" });
    }
    if (body.type !== question.type) return fail(422, { code: "QUESTION_TYPE_IMMUTABLE" });
    const isDraft = question.status === "DRAFT";
    if (!isDraft && !question.workingVersion) servingSnapshots.set(question.id, apiSnapshot(question));
    question.subjectCode = String(body.subjectCode ?? question.subjectCode);
    question.gradeCode = String(body.gradeCode ?? question.gradeCode);
    question.difficultyCode = String(body.difficultyCode ?? question.difficultyCode);
    // Máy chủ coi trường vắng là xóa giá trị (yêu cầu sửa gửi đủ trạng thái cuối).
    question.competencyCode = (body.competencyCode as string | undefined) ?? null;
    question.taskTypeCode = (body.taskTypeCode as string | undefined) ?? null;
    question.points = typeof body.points === "number" ? body.points : question.points;
    question.knowledge = (body.knowledge as QuestionDetail["knowledge"] | undefined) ?? question.knowledge;
    question.tagCodes = (body.tagCodes as string[] | undefined) ?? question.tagCodes;
    question.content = String(body.content ?? question.content);
    question.solution = (body.solution as string | undefined) ?? null;
    question.hasSolution = Boolean(question.solution);
    question.commonMistakes = ((body.commonMistakes as string[] | undefined) ?? []).join("") || null;
    question.attachmentIds = (body.attachmentIds as string[] | undefined) ?? question.attachmentIds;
    applyWireBody(question, body.body, () => `${question.id}-c${server.nextCode++}`);
    question.revision = revisionOf(question) + 1;
    question.updatedAt = "2026-11-17T01:00:00+07:00";
    let versionCreated = false;
    if (isDraft) {
      question.version = Math.max(question.version, 1);
    } else {
      // Sửa câu đã Sẵn sàng/Ngừng: tạo bản Nháp mới trên bản hiện hành.
      question.workingVersion = question.version + 1;
      versionCreated = true;
    }
    const response: ApiUpdateResponse = { question: apiDetail(question), versionCreated };
    return ok(response);
  });

  fakeApi.post("/api/v1/admin/questions/{id}/duplicate", (call) => {
    const source = questions.get(pathId(call));
    if (!source) return fail(404, { code: "NOT_FOUND" });
    const id = `q-dup-${server.nextCode++}`;
    const copy = structuredClone(source);
    copy.id = id;
    copy.code = `CH-${String(server.nextCode).padStart(6, "0")}`;
    copy.status = "DRAFT";
    copy.version = 1;
    copy.workingVersion = null;
    copy.revision = 1;
    copy.usedInExamCount = 0;
    copy.children = copy.children.map((child) => ({ ...child, id: `${id}-${child.id}` }));
    if (copy.body.type === "QUESTION_SET") copy.body.childIds = copy.children.map((child) => child.id);
    questions.set(id, copy);
    server.createdIds.push(id);
    return ok(apiDetail(copy));
  });

  fakeApi.get("/api/v1/admin/questions/{id}/versions", (call) => {
    const question = questions.get(pathId(call));
    if (!question) return fail(404, { code: "NOT_FOUND" });
    const custom = versionSpecs.get(question.id);
    const items = custom ?? defaultVersions(question, servingSnapshots.get(question.id));
    return ok({ versions: items });
  });

  fakeApi.post("/api/v1/admin/questions/{id}/versions/{versionNo}/restore", (call) => {
    const question = questions.get(pathId(call));
    if (!question) return fail(404, { code: "NOT_FOUND" });
    const body = bodyOf(call);
    if (body.expectedRevision !== revisionOf(question)) {
      return fail(412, { code: "QUESTION_REVISION_CONFLICT" });
    }
    const wanted = (call.options?.params as { path?: { versionNo?: number } } | undefined)?.path?.versionNo;
    const target = defaultVersions(question, servingSnapshots.get(question.id)).find((item) => item.versionNo === wanted);
    if (!target) return fail(404, { code: "QUESTION_VERSION_NOT_FOUND" });
    const isDraft = question.status === "DRAFT";
    if (!isDraft && !question.workingVersion) servingSnapshots.set(question.id, apiSnapshot(question));
    question.content = target.snapshot.content ?? question.content;
    if (!isDraft) question.workingVersion = Math.max(question.version, question.workingVersion ?? 0) + 1;
    question.revision = revisionOf(question) + 1;
    return ok({ question: apiDetail(question), versionCreated: !isDraft } satisfies ApiUpdateResponse);
  });

  const usageOf = (id: string, kind: "usage" | "retire-impact") => {
    const spec = usage[id] ?? { examIds: [], ledger: [] };
    const refs: ApiSourceRef[] = spec.blocks ?? spec.examIds.map((examId) => ({ examId }));
    if (kind === "usage") {
      return {
        sourceBlocks: refs,
        usedIn: (spec.ledger ?? []).map((ref) => ({ examId: ref.examId, firstAt: "2026-11-01T00:00:00+07:00", kind: ref.kind, versionNo: ref.versionNo })),
      } satisfies ApiUsage;
    }
    return {
      affectedBlocks: refs,
      usedIn: (spec.ledger ?? []).map((ref) => ({ examId: ref.examId, firstAt: "2026-11-01T00:00:00+07:00", kind: ref.kind, versionNo: ref.versionNo })),
    } satisfies ApiRetireImpact;
  };
  fakeApi.get("/api/v1/admin/questions/{id}/usage", (call) => ok(usageOf(pathId(call), "usage")));
  fakeApi.get("/api/v1/admin/questions/{id}/retire-impact", (call) => ok(usageOf(pathId(call), "retire-impact")));

  fakeApi.get("/api/v1/admin/exams/{id}", (call) => {
    const id = pathId(call);
    const found = Object.values(usage).flatMap((spec) => spec.exams ?? []).find((exam) => exam.id === id);
    return ok(apiExam(found ?? { id, code: id, name: "" }));
  });

  // `POST /question-assets`: trả id UUID; tên/dung lượng để trống để client rơi về `file.name`/`file.size`.
  fakeApi.post("/api/v1/admin/question-assets", () => ok({ id: crypto.randomUUID() }, { status: 201 }));

  const versionSpecs = new Map<string, ApiVersionItem[]>();
  /** Nội dung đang phục vụ của câu đã có bản Nháp mới (bản chụp lấy trước khi sửa). */
  const servingSnapshots = new Map<string, ApiSnapshot>();
  return Object.assign(server, {
    setVersions(id: string, items: ApiVersionItem[]) {
      versionSpecs.set(id, items);
    },
    setReadiness(id: string, readiness: ApiReadiness) {
      options.readiness = { ...options.readiness, [id]: readiness };
    },
    setUsage(id: string, spec: UsageSpec) {
      usage[id] = spec;
    },
  });
}

function pathId(call: ApiCall): string {
  const params = call.options?.params as { path?: Record<string, string> } | undefined;
  return params?.path?.id ?? "";
}

function bodyOf(call: ApiCall): Record<string, unknown> {
  return (call.options?.body as Record<string, unknown> | undefined) ?? {};
}

/**
 * Câu Nháp chưa từng Sẵn sàng: bản đang sửa là `version`; ngược lại bản phục vụ là `version` (bản chụp `serving` nếu có,
 * tức nội dung trước khi sửa), mọi bản cũ hơn đã thay thế, và `workingVersion` là bản Nháp đang sửa (nội dung hiện tại).
 */
function defaultVersions(question: QuestionDetail, serving?: ApiSnapshot): ApiVersionItem[] {
  const snapshot = apiSnapshot(question);
  const kept = serving ?? snapshot;
  const items: ApiVersionItem[] = [];
  if (question.status === "DRAFT") items.push(apiVersionItem(question.version, "WORKING", snapshot));
  else {
    if (question.workingVersion) items.push(apiVersionItem(question.workingVersion, "WORKING", snapshot));
    items.push(apiVersionItem(question.version, question.status === "RETIRED" ? "RETIRED" : "CURRENT", kept));
  }
  for (let versionNo = question.version - 1; versionNo >= 1; versionNo -= 1) {
    items.push(apiVersionItem(versionNo, "SUPERSEDED", kept));
  }
  return items;
}

/** Đăng ký nhanh `GET /content-reference`. */
export function stubContentReference(reference: ContentReferenceResponse): void {
  fakeApi.get("/api/v1/admin/content-reference", ok(reference));
}

/** Cây kiến thức kiểu web (`null` cho trường tùy chọn) -> kiểu của API. */
function apiKnowledgeNode(node: KnowledgeNode): ApiKnowledgeNode {
  return {
    id: node.id,
    code: node.code,
    name: node.name,
    level: node.level,
    active: node.active,
    ...(node.subjectCode ? { subjectCode: node.subjectCode } : {}),
    ...(node.gradeCode ? { gradeCode: node.gradeCode } : {}),
    children: node.children.map(apiKnowledgeNode),
  };
}

/**
 * Tên danh mục theo ngôn ngữ của yêu cầu (máy chủ thật nhận `Accept-Language` từ proxy): mục có `nameEn` đổi `name` sang
 * tiếng Anh khi `locale` là `en`, bỏ `nameEn`.
 */
function localizeNames(value: unknown, locale: "vi" | "en"): unknown {
  if (Array.isArray(value)) return value.map((item) => localizeNames(item, locale));
  if (typeof value !== "object" || value === null) return value;
  const { nameEn, ...rest } = value as Record<string, unknown>;
  const localized = Object.fromEntries(Object.entries(rest).map(([key, child]) => [key, localizeNames(child, locale)]));
  if (locale === "en" && typeof nameEn === "string") localized.name = nameEn;
  return localized;
}

/**
 * `GET /content-reference` dựng từ `src/test/fixtures/content-reference.json`: cùng môn, khối,
 * mức độ, nhãn, dạng bài và cây kiến thức (mã MAPID `1D4-…`) mà các câu mẫu tham chiếu.
 */
export function apiReferenceFromFixture(locale: "vi" | "en" = "vi"): ContentReferenceResponse {
  const reference = localizeNames(referenceFixture, locale) as unknown as ContentReference;
  return {
    subjects: reference.subjects,
    grades: reference.grades,
    difficulties: reference.difficulties,
    taskTypes: reference.taskTypes,
    questionTypes: reference.questionTypes,
    tags: reference.tags,
    combinations: reference.combinations,
    competencies: reference.competencies.map((competency) => ({
      code: competency.code,
      name: competency.name,
      examKindCodes: competency.examKindCodes,
      ...(competency.subjectCode ? { subjectCode: competency.subjectCode } : {}),
    })),
    examKinds: reference.examKinds.map((kind) => ({
      code: kind.code,
      itemId: `item-${kind.code}`,
      name: kind.name,
      practiceScoring: kind.practiceScoring,
      profileSet: true,
      scopes: [...kind.scopes],
      templateBy: kind.templateBy,
      timing: kind.timing,
      ...(kind.maxScore != null ? { maxScore: kind.maxScore } : {}),
      ...(kind.referenceMax != null ? { referenceMax: kind.referenceMax } : {}),
      multipleChoiceRule: kind.scoringDefaults.multipleChoiceRule,
      // Kiểu sinh của `JsonNode` là `Record<string, never>`; thân thật là JSON `{method, table, statementPoints}`.
      trueFalseScoring: {
        method: kind.scoringDefaults.trueFalse.method,
        table: kind.scoringDefaults.trueFalse.table,
        statementPoints: kind.scoringDefaults.trueFalse.statementPoints,
      } as unknown as ApiExamKind["trueFalseScoring"],
    })),
    knowledgeTree: reference.knowledgeTree.map(apiKnowledgeNode),
  };
}

/**
 * Cài cho một file test của Ngân hàng câu hỏi: cờ dữ liệu mẫu tắt, "máy chủ" câu hỏi nạp lại bộ câu mẫu trước mỗi test,
 * danh mục tham chiếu và `catalog/options` từ `reference.json`. Trả về kho đọc/ghi trực tiếp vào bản đồ của máy chủ giả
 * (cùng tên hàm với kho dữ liệu mẫu cũ để test đối chiếu trạng thái sau khi lưu).
 */
export function withQuestionsServer(options: QuestionServerOptions = {}) {
  withRealApi();
  let current: QuestionServer | undefined;
  beforeEach(() => {
    resetCatalogLookup();
    resetIdempotencyKeys();
    current = installQuestionsApi(fixtureAll(), { ...options });
    stubContentReference(apiReferenceFromFixture());
    stubCatalogOptions();
  });
  const server = (): QuestionServer => {
    if (!current) throw new Error("useQuestionsServer: no test is running");
    return current;
  };
  const require = (id: string): QuestionDetail => {
    const question = server().questions.get(id);
    if (!question) throw new Error(`question ${id} not found`);
    return question;
  };
  return {
    server,
    require,
    get: (id: string): QuestionDetail | undefined => server().questions.get(id),
    all: (): QuestionDetail[] => [...server().questions.values()],
    insert(question: QuestionDetail): void {
      server().questions.set(question.id, question);
    },
    update(id: string, change: (question: QuestionDetail) => QuestionDetail): void {
      server().questions.set(id, change(require(id)));
    },
  };
}
