import { toContentApiError } from "@/features/content/api-error";
import { getContentReference } from "@/features/content/api.real";
import { loadCodeToId } from "@/features/content/catalog-lookup";
import { withIdempotencyKey } from "@/features/content/idempotency";
import { createBrowserApiClient } from "@/lib/api/client";
import { fromApiDetail } from "./api-mapping";
import { getQuestion } from "./api.real";
import type {
  QuestionUsage,
  QuestionVersionList,
  RestoreQuestionVersionRequest,
  SaveQuestionRequest,
  UpdateQuestionRequest,
  UpdateQuestionResponse,
} from "./editor-types";
import type { QuestionDetail } from "./types";
import { loadQuestionUsage } from "./usage-real";
import { fromApiVersions, loadSnapshotLookups } from "./versions-mapping";
import { asApiBody, toWireBody, type ChildClassificationIds } from "./wire-body";

/** Nhánh API thật của soạn câu hỏi, nhân bản, lịch sử phiên bản, nơi đang dùng; giữ chữ ký của `editor-api.mock.ts`. */

/** Phân loại riêng của câu con gửi bằng id giá trị danh mục; chỉ tải danh mục khi có câu con ghi đè. */
async function childClassificationIds(request: SaveQuestionRequest): Promise<ChildClassificationIds> {
  const overridesGrade = request.children.some((child) => child.gradeCode);
  const overridesLevel = request.children.some((child) => child.difficultyCode);
  const [grades, levels] = await Promise.all([
    overridesGrade ? loadCodeToId("GRADE") : Promise.resolve(new Map<string, string>()),
    overridesLevel ? loadCodeToId("QUESTION_LEVEL") : Promise.resolve(new Map<string, string>()),
  ]);
  return {
    gradeItemId: (code) => grades.get(code) ?? null,
    levelItemId: (code) => levels.get(code) ?? null,
  };
}

/** Thân yêu cầu tạo hoặc sửa. `expectedRevision` đi cùng khi sửa. */
async function toSaveBody(request: SaveQuestionRequest) {
  const ids = await childClassificationIds(request);
  return {
    type: request.type,
    subjectCode: request.subjectCode,
    gradeCode: request.gradeCode,
    difficultyCode: request.difficultyCode,
    ...(request.taskTypeCode ? { taskTypeCode: request.taskTypeCode } : {}),
    ...(request.competencyCode ? { competencyCode: request.competencyCode } : {}),
    points: request.points,
    knowledge: request.knowledge,
    tagCodes: request.tagCodes,
    content: request.content,
    ...(request.solution ? { solution: request.solution } : {}),
    commonMistakes: request.commonMistakes ? [request.commonMistakes] : [],
    attachmentIds: request.attachmentIds,
    body: asApiBody(toWireBody(request.body, request.children, ids)),
  };
}

/**
 * Tạo câu hỏi (luôn Nháp). Khóa idempotency theo lần soạn (`saveIntentId`): bấm Lưu hai lần hoặc thử lại sau lỗi mạng
 * hay 5xx dùng lại khóa; thân yêu cầu đổi giữa hai lần thì khóa mới (`withIdempotencyKey`).
 */
export async function createQuestion(request: SaveQuestionRequest): Promise<QuestionDetail> {
  const body = await toSaveBody(request);
  const scope = `create:${request.saveIntentId ?? crypto.randomUUID()}`;
  const created = await withIdempotencyKey(
    scope,
    async (key) => {
      const client = createBrowserApiClient();
      const { data, error, response } = await client.POST("/api/v1/admin/questions", {
        params: { header: { "Idempotency-Key": key } },
        body,
      });
      if (!response.ok || !data) throw toContentApiError(response.status, error);
      return data;
    },
    JSON.stringify(body),
  );
  return fromApiDetail(created);
}

/** Sửa câu hỏi; 412 `QUESTION_REVISION_CONFLICT` khi `expectedRevision` lệch, 422 kèm lỗi theo trường. */
export async function updateQuestion(id: string, request: UpdateQuestionRequest): Promise<UpdateQuestionResponse> {
  const body = await toSaveBody(request);
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/questions/{id}", {
    params: { path: { id } },
    body: { ...body, expectedRevision: request.expectedRevision },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return { question: fromApiDetail(data.question), versionCreated: data.versionCreated };
}

export function duplicateQuestion(id: string): Promise<QuestionDetail> {
  return withIdempotencyKey(`duplicate:${id}`, async (key) => {
    const client = createBrowserApiClient();
    const { data, error, response } = await client.POST("/api/v1/admin/questions/{id}/duplicate", {
      params: { path: { id }, header: { "Idempotency-Key": key } },
    });
    if (!response.ok || !data) throw toContentApiError(response.status, error);
    return fromApiDetail(data);
  });
}

export async function getQuestionUsage(id: string): Promise<QuestionUsage> {
  const question = await getQuestion(id);
  return loadQuestionUsage(id, { subjectCode: question.subjectCode, type: question.type });
}

export async function listQuestionVersions(id: string): Promise<QuestionVersionList> {
  const client = createBrowserApiClient();
  const question = await getQuestion(id);
  const [versions, usage, reference] = await Promise.all([
    client.GET("/api/v1/admin/questions/{id}/versions", { params: { path: { id } } }),
    loadQuestionUsage(id, { subjectCode: question.subjectCode, type: question.type }),
    getContentReference(),
  ]);
  if (!versions.response.ok || !versions.data) throw toContentApiError(versions.response.status, versions.error);
  const lookups = await loadSnapshotLookups(reference.knowledgeTree);
  return fromApiVersions(id, versions.data.versions, { status: question.status, usage }, lookups);
}

/** Chép một phiên bản cũ thành bản đang sửa; xử lý như một lần lưu. */
export async function restoreQuestionVersion(id: string, request: RestoreQuestionVersionRequest): Promise<UpdateQuestionResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/questions/{id}/versions/{versionNo}/restore", {
    params: { path: { id, versionNo: request.version } },
    body: { expectedRevision: request.expectedRevision },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return { question: fromApiDetail(data.question), versionCreated: data.versionCreated };
}
