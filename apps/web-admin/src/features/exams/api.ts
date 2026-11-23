import type { components } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import {
  mapAvailableCounts,
  mapExamDetail,
  mapExamQuotas,
  mapExamSummary,
  mapExamTemplate,
  mapParts,
  mapSectionStates,
  mapStructureSummary,
  toApiExamQuotas,
  toApiParts,
} from "./api-mapping";
import { toExamsApiError } from "./errors";
import type {
  ApplyExamTemplateRequest,
  AvailableQuestionCounts,
  AvailableQuestionsQuery,
  CreateExamRequest,
  DiscontinueExamRequest,
  DuplicateExamRequest,
  ExamDetail,
  ExamListPage,
  ExamStructure,
  ExamStructureSummary,
  ExamTemplate,
  ListExamsQuery,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamStructureRequest,
} from "./types";

const idempotencyKey = () => globalThis.crypto.randomUUID();

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
    sectionStates: mapSectionStates(data.sectionStates),
    examQuotas: mapExamQuotas(data.examQuotas),
  };
}

export async function getExamStructureSummary(id: string): Promise<ExamStructureSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}/structure/summary", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapStructureSummary(data);
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
    body: {
      expectedRevision: request.expectedRevision,
      parts: toApiParts(request.parts),
      ...(request.examQuotas ? { examQuotas: toApiExamQuotas(request.examQuotas) } : {}),
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

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
      buildMode: request.buildMode ?? undefined,
      description: request.description ?? undefined,
      instructions: request.instructions ?? undefined,
      durationMinutes: request.durationMinutes ?? undefined,
      maxScore: request.maxScore ?? undefined,
      ...(request.overview?.length ? { overview: request.overview } : {}),
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
      // `PATCH` chỉ khai các trường không bắt buộc ở kiểu cục bộ dù OpenAPI đánh dấu `code`/`name`/`kind`/`scope` required.
      name: request.name as string,
      code: request.code as string,
      kind: request.kind as string,
      scope: request.scope as NonNullable<UpdateExamInfoRequest["scope"]>,
      partCode: request.partCode ?? undefined,
      subjectCode: request.subjectCode ?? undefined,
      buildMode: request.buildMode ?? undefined,
      // Chuỗi trống hoặc `null` xóa mô tả và hướng dẫn; `undefined` giữ nguyên giá trị đang lưu.
      description: request.description === undefined ? undefined : (request.description ?? ""),
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

export async function discontinueExam({
  examId,
  expectedRevision,
  idempotencyKey: key,
}: DiscontinueExamRequest): Promise<ExamDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/discontinue", {
    params: {
      path: { examId },
      header: { "Idempotency-Key": key },
    },
    body: { expectedRevision },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapExamDetail(data);
}

type ApiAvailableQuestionsQuery = components["schemas"]["AvailableQuestionsQuery"];

export type AudioCountQuery = AvailableQuestionsQuery & Pick<ApiAvailableQuestionsQuery, "audioRequirement">;

interface AudioSubjectCountQuery {
  subjectCode: string;
  groups: AudioCountQuery[];
}

function toCountBody(query: AudioCountQuery): ApiAvailableQuestionsQuery {
  return {
    subjectCode: query.subjectCode,
    questionType: query.questionType ?? undefined,
    answerTypes: query.answerTypes.length ? query.answerTypes : undefined,
    childAnswerType: query.childAnswerType ?? undefined,
    knowledgeScope: query.knowledgeScope
      ? {
          nodeIds: query.knowledgeScope.nodeIds,
          competencyCodes: query.knowledgeScope.competencyCodes,
          ...(query.knowledgeScope.nodeGroups?.length ? { nodeGroups: query.knowledgeScope.nodeGroups } : {}),
          ...(query.knowledgeScope.everyChildInScope ? { everyChildInScope: true } : {}),
          ...(query.knowledgeScope.everyChildSameNode ? { everyChildSameNode: true } : {}),
          ...(query.knowledgeScope.excludedNodeIds?.length ? { excludedNodeIds: query.knowledgeScope.excludedNodeIds } : {}),
        }
      : undefined,
    tagCodes: query.tagCodes?.length ? query.tagCodes : undefined,
    competencyCodes: query.competencyCodes?.length ? query.competencyCodes : undefined,
    requiredPointsPerQuestion: query.requiredPointsPerQuestion ?? undefined,
    itemShape: query.itemShape ?? undefined,
    answerFormats: query.answerFormats?.length
      ? (query.answerFormats as ApiAvailableQuestionsQuery["answerFormats"])
      : undefined,
    childConditions: query.childConditions?.length ? query.childConditions : undefined,
    audioRequirement: query.audioRequirement === "REQUIRED" || query.audioRequirement === "FORBIDDEN" ? query.audioRequirement : undefined,
  };
}

export async function countAvailableQuestions(query: AudioCountQuery): Promise<AvailableQuestionCounts> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/available-questions/count", {
    body: toCountBody(query),
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapAvailableCounts(data);
}

export async function countSubjectAvailableQuestions(
  query: AudioSubjectCountQuery,
): Promise<AvailableQuestionCounts> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/available-questions/count-subject", {
    body: { subjectCode: query.subjectCode, groups: query.groups.map(toCountBody) },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapAvailableCounts(data);
}
