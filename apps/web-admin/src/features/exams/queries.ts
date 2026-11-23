"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import {
  applyExamTemplate,
  countAvailableQuestions,
  countSubjectAvailableQuestions,
  createExam,
  discontinueExam,
  duplicateExam,
  getExam,
  getExamStructure,
  getExamStructureSummary,
  listExams,
  listExamTemplates,
  suggestExamCode,
  updateExamInfo,
  updateExamStructure,
} from "./api";
import { EXAM_REVIEW_QUERY_KEYS, EXAMS_QUERY_KEYS } from "./keys";
import { useTemplateLabel } from "./template-name";
import type {
  ApplyExamTemplateRequest,
  AvailableQuestionsQuery,
  CreateExamRequest,
  DiscontinueExamRequest,
  ListExamsQuery,
  SubjectAvailableQuestionsQuery,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamStructureRequest,
} from "./types";

export function useExamList(query: ListExamsQuery) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.list(query),
    queryFn: ({ signal }) => listExams(query, signal),
    placeholderData: keepPreviousWhileSameScope(EXAMS_QUERY_KEYS.list(query)),
  });
}

export function useExam(id: string) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.detail(id),
    queryFn: () => getExam(id),
  });
}

/** Mẫu cấu trúc; mẫu chưa có tên lấy tên theo ngôn ngữ giao diện (`useTemplateLabel`). */
export function useExamTemplates() {
  const label = useTemplateLabel();
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.templates(),
    queryFn: () => listExamTemplates(),
    staleTime: 5 * 60_000,
    select: (templates) =>
      templates.map((template) => (template.name ? template : { ...template, name: label(template) })),
  });
}

/** Bản làm việc của cấu trúc; không tự tải lại khi quay lại cửa sổ để không đè bản nháp đang sửa. */
export function useExamStructure(id: string, enabledOrUnused?: boolean, maybeEnabled?: boolean) {
  const enabled = typeof enabledOrUnused === "boolean" ? enabledOrUnused : (maybeEnabled ?? true);
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.structure(id),
    queryFn: () => getExamStructure(id),
    refetchOnWindowFocus: false,
    enabled,
  });
}

export function useExamStructureSummary(id: string) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.structureSummary(id),
    queryFn: () => getExamStructureSummary(id),
    retry: false,
  });
}

export function useSuggestedExamCode(query: SuggestExamCodeQuery, enabled: boolean) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.suggestedCode(query),
    queryFn: () => suggestExamCode(query),
    enabled,
  });
}

export function useAvailableQuestions(query: AvailableQuestionsQuery | null) {
  return useQuery({
    queryKey: query ? EXAMS_QUERY_KEYS.available(query) : ["exams", "available-questions", null],
    queryFn: () =>
      query ? countAvailableQuestions(query) : Promise.resolve({ total: 0, cells: [], byAnswerType: [] }),
    enabled: query !== null,
    placeholderData: (previous) => previous,
  });
}

/** Đếm nguồn cho cả môn: hợp nguồn của các nhóm trong môn, mỗi câu đếm một lần (soạn chỉ tiêu cấp môn). */
export function useSubjectAvailableQuestions(query: SubjectAvailableQuestionsQuery | null) {
  return useQuery({
    queryKey: query ? EXAMS_QUERY_KEYS.subjectAvailable(query) : ["exams", "available-questions", "subject", null],
    queryFn: () =>
      query ? countSubjectAvailableQuestions(query) : Promise.resolve({ total: 0, cells: [], byAnswerType: [] }),
    enabled: query !== null,
    placeholderData: (previous) => previous,
  });
}

/** Mọi thao tác ghi làm mới cả nhóm `exams` (danh sách, chi tiết, cấu trúc, kết quả kiểm tra, phiên bản). */
function useInvalidateExams() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all });
}

export function useCreateExam() {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: CreateExamRequest) => createExam(request),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateExamInfo(id: string) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: UpdateExamInfoRequest) => updateExamInfo(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateExamStructure(id: string) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: UpdateExamStructureRequest) => updateExamStructure(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useApplyExamTemplate(id: string) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: ApplyExamTemplateRequest) => applyExamTemplate(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useDuplicateExam() {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => duplicateExam(id, { name }),
    onSuccess: () => invalidate(),
  });
}

export function useDiscontinueExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: DiscontinueExamRequest) => discontinueExam(request),
    onSuccess: async (_data, { examId }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.detail(examId) }),
        queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.lists() }),
        queryClient.invalidateQueries({ queryKey: EXAM_REVIEW_QUERY_KEYS.check(examId) }),
        queryClient.invalidateQueries({ queryKey: EXAM_REVIEW_QUERY_KEYS.versions(examId) }),
        queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all }),
      ]);
    },
  });
}
