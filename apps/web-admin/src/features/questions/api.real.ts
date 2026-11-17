import { toContentApiError } from "@/features/content/api-error";
import { withIdempotencyKey } from "@/features/content/idempotency";
import { createBrowserApiClient } from "@/lib/api/client";
import {
  fromApiBulkResult,
  fromApiCounts,
  fromApiDetail,
  fromApiPage,
  fromApiSummary,
} from "./api-mapping";
import { fromApiReadiness } from "./readiness-mapping";
import type { PersonRef } from "@/features/content/types";
import type { QuestionReadiness } from "./readiness";
import type {
  BulkQuestionStatusRequest,
  BulkQuestionStatusResult,
  ListQuestionsQuery,
  PageResponseQuestionSummary,
  QuestionDetail,
  QuestionRetireImpact,
  QuestionStatusCounts,
  QuestionSummary,
} from "./types";
import { loadRetireImpact } from "./usage-real";

/** Nhánh API thật của Ngân hàng câu hỏi (`/api/v1/admin/questions`) (bản mẫu đã gỡ ở QA). */

/** `ListQuestionsQuery` của web -> tham số truy vấn của máy chủ (`updatedFrom/To` chưa có bên máy chủ, bỏ qua). */
function toFilterParams(query: ListQuestionsQuery) {
  return {
    ...(query.q ? { keyword: query.q } : {}),
    ...(query.subjectCode ? { subjectCode: query.subjectCode } : {}),
    ...(query.gradeCode ? { gradeCode: query.gradeCode } : {}),
    ...(query.difficultyCode ? { difficultyCode: query.difficultyCode } : {}),
    ...(query.type ? { type: query.type } : {}),
    ...(query.taskTypeCode ? { taskTypeCode: query.taskTypeCode } : {}),
    ...(query.competencyCode ? { competencyCode: query.competencyCode } : {}),
    ...(query.knowledgeIds?.length ? { knowledgeIds: query.knowledgeIds } : {}),
    ...(query.tagCode ? { tagCodes: [query.tagCode] } : {}),
    ...(query.createdById ? { createdBy: query.createdById } : {}),
    ...(query.createdFrom ? { createdFrom: query.createdFrom } : {}),
    ...(query.createdTo ? { createdTo: query.createdTo } : {}),
    ...(query.hasSolution !== undefined ? { hasSolution: query.hasSolution } : {}),
  };
}

export async function listQuestions(query: ListQuestionsQuery): Promise<PageResponseQuestionSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/questions", {
    params: {
      query: {
        ...toFilterParams(query),
        ...(query.status ? { status: query.status } : {}),
        ...(query.page !== undefined ? { page: query.page } : {}),
        ...(query.size !== undefined ? { size: query.size } : {}),
        ...(query.sort ? { sort: query.sort } : {}),
      },
    },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiPage(data);
}

/** Số câu theo trạng thái với cùng bộ lọc (không có `status`, trang, sắp xếp). */
export async function getQuestionStatusCounts(query: ListQuestionsQuery): Promise<QuestionStatusCounts> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/questions/status-counts", {
    params: { query: toFilterParams(query) },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiCounts(data);
}

export async function listQuestionAuthors(): Promise<PersonRef[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/questions/authors");
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return data;
}

export async function getQuestion(id: string): Promise<QuestionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}", { params: { path: { id } } });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiDetail(data);
}

export async function getQuestionReadiness(id: string): Promise<QuestionReadiness> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/questions/{id}/readiness", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiReadiness(data);
}

/** Chuyển sang Sẵn sàng. 409 đã Sẵn sàng; 422 `QUESTION_NOT_READY`; 412 khi `expectedRevision` lệch. */
export async function markQuestionReady({ id, expectedRevision }: { id: string; expectedRevision: number }): Promise<QuestionSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/questions/{id}/ready", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiSummary(data);
}

export async function retireQuestion({ id, expectedRevision }: { id: string; expectedRevision: number }): Promise<QuestionSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/questions/{id}/retire", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return fromApiSummary(data);
}

/** Mã câu của các id bị bỏ qua (máy chủ chỉ trả id); câu không đọc được thì giữ id. */
async function codesOf(ids: readonly string[]): Promise<Map<string, string>> {
  const found = await Promise.all(
    ids.map(async (id): Promise<[string, string] | null> => {
      try {
        return [id, (await getQuestion(id)).code];
      } catch {
        return null;
      }
    }),
  );
  return new Map(found.filter((entry): entry is [string, string] => entry !== null));
}

async function bulkChange(action: "ready" | "retire", { ids }: BulkQuestionStatusRequest): Promise<BulkQuestionStatusResult> {
  const unique = [...new Set(ids)];
  const client = createBrowserApiClient();
  const path = action === "ready" ? "/api/v1/admin/questions/bulk/ready" : "/api/v1/admin/questions/bulk/retire";
  const result = await withIdempotencyKey(`bulk-${action}:${[...unique].sort().join(",")}`, async (key) => {
    const { data, error, response } = await client.POST(path, {
      params: { header: { "Idempotency-Key": key } },
      body: { ids: unique },
    });
    if (!response.ok || !data) throw toContentApiError(response.status, error);
    return data;
  });
  return fromApiBulkResult(result, await codesOf(result.skipped.map((item) => item.questionId)));
}

export function bulkMarkQuestionsReady(request: BulkQuestionStatusRequest): Promise<BulkQuestionStatusResult> {
  return bulkChange("ready", request);
}

export function bulkRetireQuestions(request: BulkQuestionStatusRequest): Promise<BulkQuestionStatusResult> {
  return bulkChange("retire", request);
}

/** Bài thi đang dùng câu và ảnh hưởng khi ngừng sử dụng (`GET /questions/{id}/retire-impact`). */
export async function getQuestionRetireImpact(id: string): Promise<QuestionRetireImpact> {
  const question = await getQuestion(id);
  return loadRetireImpact(id, { subjectCode: question.subjectCode, type: question.type });
}
