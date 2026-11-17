import { getContentReference } from "@/features/content/api";
import { createBrowserApiClient } from "@/lib/api/client";
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
  ExamListPage,
  ExamSectionState,
  ExamStructure,
  ExamStructureSummary,
  ExamTemplate,
  ListExamsQuery,
  SubjectAvailableQuestionsQuery,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamOverviewRequest,
  UpdateExamStructureRequest,
} from "./types";

/**
 * Nhánh API thật của Bài thi (`/api/v1/admin/exams/**`, `/exam-templates`). Lỗi nghiệp vụ ném `ExamsApiError`
 * ({status, code, fieldErrors}): 409 `EXAM_CODE_TAKEN`, `EXAM_SCOPE_LOCKED`,
 * `EXAM_STRUCTURE_REPLACE_CONFIRM`; 412 `EXAM_REVISION_CONFLICT`; 422 `EXAM_STRUCTURE_INVALID`... Kiểm tra, xem trước,
 * phiên bản và công bố nằm ở `review-api.ts`.
 */

const idempotencyKey = () => globalThis.crypto.randomUUID();

/** `signal` hủy lần đọc đang chờ khi truy vấn đổi (React Query hủy truy vấn cũ). Mọi bộ lọc và phân trang do máy chủ làm. */
export async function listExams(query: ListExamsQuery, signal?: AbortSignal): Promise<ExamListPage> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams", {
    params: {
      query: {
        q: query.q,
        kind: query.kind,
        subject: query.subjectCode,
        scope: query.scope,
        status: query.status,
        checkStatus: query.checkStatus,
        hasUnpublishedChanges: query.hasUnpublishedChanges || undefined,
        sort: query.sort,
        page: query.page ?? 0,
        size: query.size ?? 20,
      },
    },
    signal,
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    content: data.items.map(mapExamSummary),
    page: data.page,
    size: data.size,
    totalElements: data.totalItems,
    totalPages: data.totalPages,
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
  return {
    examId: data.examId,
    revision: data.revision,
    parts: mapParts(data.parts),
    combinations: (data.combinations ?? []).map((item) => ({
      code: item.code ?? "",
      examCode: item.examCode ?? null,
      partId: item.partId ?? null,
      subjectCodes: item.subjectCodes ?? [],
      missingSubjects: item.missingSubjects ?? [],
      nameVi: item.nameVi ?? null,
      nameEn: item.nameEn ?? null,
    })),
    sectionStates: (data.sectionStates ?? []).map((item) => ({
      partId: item.partId ?? null,
      sectionId: item.sectionId ?? null,
      subjectCode: item.subjectCode ?? null,
      state:
        item.state === "CHECKED" || item.state === "NEEDS_CHECK"
          ? (item.state as ExamSectionState["state"])
          : "INCOMPLETE",
    })),
  };
}

/**
 * Tổng hợp cấu trúc vùng (d) (spec 15.2). BE-5 chưa triển khai: trả `null` để màn ẩn bảng tổng hợp thay vì báo lỗi.
 */
export async function getExamStructureSummary(id: string): Promise<ExamStructureSummary | null> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}/structure/summary", {
    params: { path: { id } },
  });
  if (response.status === 501) return null;
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return {
    rows: (data.parts ?? []).map((row) => ({
      name: row.name ?? null,
      partId: row.partId ?? null,
      sectionId: row.sectionId ?? null,
      blockId: row.blockId ?? null,
      target: row.target ?? null,
      allocated: row.allocated ?? null,
      difference: row.difference ?? null,
      essayCount: row.essayCount ?? null,
      objectiveCount: row.objectiveCount ?? null,
      setCount: row.setCount ?? null,
      commandCount: row.commandCount ?? null,
      childCount: row.childCount ?? null,
      maxPoints: row.maxPoints ?? null,
    })),
  };
}

/**
 * Lưu tổng quan từ hộp thoại "Sửa thông tin tổng quan" (PUT `/exams/{id}/overview`). BE-5 chưa triển khai: nút vẫn
 * hiện nhưng lưu trả lỗi máy chủ.
 */
export async function updateExamOverview(id: string, request: UpdateExamOverviewRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/exams/{id}/overview", {
    params: { path: { id } },
    body: {
      expectedRevision: request.expectedRevision,
      overview: request.rows.map((row) => ({
        partId: row.partId ?? undefined,
        sectionId: row.sectionId ?? undefined,
        partCode: row.partCode ?? undefined,
        subjectCode: row.subjectCode ?? undefined,
        questionCount: row.questionCount ?? undefined,
        durationMinutes: row.durationMinutes ?? undefined,
        maxPoints: row.maxPoints ?? undefined,
      })),
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
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
  const created = request.existing ?? (await postExam(request));
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

async function postExam(request: CreateExamRequest): Promise<ExamDetail> {
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
  return mapExamDetail(data);
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
      overviewPolicy: request.overviewPolicy ?? undefined,
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
      ? { nodeIds: query.knowledgeScope.nodeIds, competencyCodes: query.knowledgeScope.competencyCodes }
      : undefined,
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
