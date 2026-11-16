import { getContentReference } from "@/features/content/api";
import { createBrowserApiClient } from "@/lib/api/client";
import type { MockPage } from "@/lib/mock/content-mock";
import {
  mapAvailableCounts,
  mapExamDetail,
  mapExamSummary,
  mapExamTemplate,
  mapParts,
  toApiParts,
} from "./api-mapping";
import { partsFromInput } from "./create-parts";
import { ExamStructureSaveError, toExamsApiError } from "./review-api";
import type {
  ApplyExamTemplateRequest,
  AvailableQuestionCounts,
  AvailableQuestionsQuery,
  CreateExamRequest,
  DuplicateExamRequest,
  ExamDetail,
  ExamStructure,
  ExamSummary,
  ExamTemplate,
  ListExamsQuery,
  SubjectAvailableQuestionsQuery,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamStructureRequest,
} from "./types";

/**
 * Nhánh API thật của Bài thi (`/api/v1/admin/exams/**`, `/exam-templates`). Lỗi nghiệp vụ ném `ExamsApiError`
 * ({status, code, fieldErrors}) như `MockApiError`: 409 `EXAM_CODE_TAKEN`, `EXAM_SCOPE_LOCKED`,
 * `EXAM_STRUCTURE_REPLACE_CONFIRM`; 412 `EXAM_REVISION_CONFLICT`; 422 `EXAM_STRUCTURE_INVALID`... Kiểm tra, xem trước,
 * phiên bản và công bố nằm ở `review-api.ts`.
 */

/** Danh sách theo hai bộ lọc máy chủ không có (`checkStatus`, `hasUnpublishedChanges`): đọc hết rồi lọc và cắt trang. */
const FILTER_PAGE_SIZE = 100;

type Page<T> = MockPage<T>;

const idempotencyKey = () => globalThis.crypto.randomUUID();

const SERVER_FILTERS = (query: ListExamsQuery) => ({
  q: query.q,
  kind: query.kind,
  subject: query.subjectCode,
  scope: query.scope,
  status: query.status,
});

async function fetchSummaries(query: ListExamsQuery, page: number, size: number) {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams", {
    params: { query: { ...SERVER_FILTERS(query), sort: query.sort, page, size } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return data;
}

export async function listExams(query: ListExamsQuery): Promise<Page<ExamSummary>> {
  const page = query.page ?? 0;
  const size = query.size ?? 20;
  if (!query.checkStatus && !query.hasUnpublishedChanges) {
    const data = await fetchSummaries(query, page, size);
    return {
      content: data.items.map(mapExamSummary),
      page: data.page,
      size: data.size,
      totalElements: data.totalItems,
      totalPages: data.totalPages,
    };
  }
  const rows: ExamSummary[] = [];
  for (let next = 0, pages = 1; next < pages; next += 1) {
    const data = await fetchSummaries(query, next, FILTER_PAGE_SIZE);
    pages = data.totalPages;
    rows.push(...data.items.map(mapExamSummary));
  }
  const filtered = rows.filter(
    (row) =>
      (!query.checkStatus || row.check.status === query.checkStatus) &&
      (!query.hasUnpublishedChanges || row.hasUnpublishedChanges),
  );
  return {
    content: filtered.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
  };
}

export async function getExam(id: string): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}", { params: { path: { id } } });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

export async function listExamTemplates(): Promise<ExamTemplate[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exam-templates", { params: { query: {} } });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return data.map(mapExamTemplate);
}

export async function getExamStructure(id: string): Promise<ExamStructure> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}/structure", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return { examId: data.examId, revision: data.revision, parts: mapParts(data.parts) };
}

export async function suggestExamCode(query: SuggestExamCodeQuery): Promise<string> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/suggested-code", {
    params: {
      query: {
        kind: query.kind,
        scope: query.scope,
        partCode: query.partCode ?? undefined,
        subjectCode: query.subjectCode ?? undefined,
      },
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return data.code;
}

export async function updateExamStructure(id: string, request: UpdateExamStructureRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/exams/{id}/structure", {
    params: { path: { id } },
    body: { expectedRevision: request.expectedRevision, parts: toApiParts(request.parts) },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

/**
 * Tạo bài Nháp: máy chủ nạp mẫu mới nhất khớp kỳ thi, phạm vi, phần hoặc môn (`CreateExamRequest` của máy chủ không
 * nhận cấu trúc). Có `parts` (trang Tạo bài đã sửa cấu trúc so với mẫu) thì lưu tiếp cấu trúc đó bằng revision vừa
 * nhận; lỗi ở bước lưu cấu trúc thì bài đã tạo vẫn còn ở Nháp với cấu trúc của mẫu và ném `ExamStructureSaveError`
 * mang bài đó (nơi gọi chuyển sang bài, không POST lại).
 */
export async function createExam(request: CreateExamRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams", {
    params: { header: { "Idempotency-Key": idempotencyKey() } },
    body: {
      code: request.code,
      name: request.name,
      kind: request.kind,
      scope: request.scope,
      partCode: request.partCode ?? undefined,
      subjectCode: request.subjectCode ?? undefined,
      description: request.description ?? undefined,
      instructions: request.instructions ?? undefined,
      durationMinutes: request.durationMinutes ?? undefined,
      maxScore: request.maxScore ?? undefined,
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  const created = mapExamDetail(data);
  if (!request.parts) return created;
  try {
    const { combinations } = await getContentReference();
    return await updateExamStructure(created.id, {
      expectedRevision: created.revision,
      parts: partsFromInput(request.parts, combinations, request.subjectCode ?? null),
    });
  } catch (failure) {
    throw new ExamStructureSaveError(created, failure);
  }
}

export async function updateExamInfo(id: string, request: UpdateExamInfoRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/exams/{id}", {
    params: { path: { id } },
    body: {
      expectedRevision: request.expectedRevision,
      name: request.name,
      code: request.code,
      kind: request.kind,
      scope: request.scope,
      partCode: request.partCode ?? undefined,
      subjectCode: request.subjectCode ?? undefined,
      // Chuỗi trống xóa mô tả và hướng dẫn; thiếu trường thì giữ nguyên.
      description: request.description ?? "",
      instructions: request.instructions === undefined ? undefined : (request.instructions ?? ""),
      durationMinutes: request.durationMinutes ?? undefined,
      maxScore: request.maxScore ?? undefined,
      yearsConfirmed: request.yearsConfirmed,
      confirmReplace: request.confirmReplace,
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

export async function applyExamTemplate(id: string, request: ApplyExamTemplateRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{id}/structure/template", {
    params: { path: { id } },
    body: {
      templateCode: request.templateCode,
      confirmReplace: request.confirmReplace,
      expectedRevision: request.expectedRevision,
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

export async function duplicateExam(id: string, request: DuplicateExamRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{id}/duplicate", {
    params: { path: { id }, header: { "Idempotency-Key": idempotencyKey() } },
    body: { name: request.name },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

function toCountBody(query: AvailableQuestionsQuery) {
  return {
    subjectCode: query.subjectCode,
    questionType: query.questionType ?? undefined,
    answerTypes: query.answerTypes.length ? query.answerTypes : undefined,
    childAnswerType: query.childAnswerType ?? undefined,
    knowledgeScope: query.knowledgeScope
      ? { nodeIds: query.knowledgeScope.nodeIds, match: query.knowledgeScope.match, competencyCodes: query.knowledgeScope.competencyCodes }
      : undefined,
    taskTypeCode: query.taskTypeCode ?? undefined,
    tagCodes: query.tagCodes?.length ? query.tagCodes : undefined,
    competencyCodes: query.competencyCodes?.length ? query.competencyCodes : undefined,
    requiredPointsPerQuestion: query.requiredPointsPerQuestion ?? undefined,
  };
}

export async function countAvailableQuestions(query: AvailableQuestionsQuery): Promise<AvailableQuestionCounts> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/available-questions/count", {
    body: toCountBody(query),
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapAvailableCounts(data);
}

export async function countSubjectAvailableQuestions(
  query: SubjectAvailableQuestionsQuery,
): Promise<AvailableQuestionCounts> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/available-questions/count-subject", {
    body: { subjectCode: query.subjectCode, groups: query.groups.map(toCountBody) },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapAvailableCounts(data);
}
