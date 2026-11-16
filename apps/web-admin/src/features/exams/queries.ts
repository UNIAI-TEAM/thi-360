"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { useTemplateLabel } from "./template-name";
import { defaultExamsApiDeps, EXAMS_QUERY_KEYS, type ExamsApiDeps } from "./api";
import type {
  ApplyExamTemplateRequest,
  AvailableQuestionsQuery,
  CreateExamRequest,
  ListExamsQuery,
  SubjectAvailableQuestionsQuery,
  SuggestExamCodeQuery,
  UpdateExamInfoRequest,
  UpdateExamStructureRequest,
} from "./types";

export function useExamList(query: ListExamsQuery, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.list(query),
    queryFn: () => deps.listExams(query),
    placeholderData: keepPreviousWhileSameScope(EXAMS_QUERY_KEYS.list(query)),
  });
}

export function useExam(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.detail(id),
    queryFn: () => deps.getExam(id),
  });
}

/** Mẫu cấu trúc; mẫu chưa có tên (API thật) lấy tên theo ngôn ngữ giao diện (`useTemplateLabel`), đổi ngôn ngữ thì đổi ngay. */
export function useExamTemplates(deps: ExamsApiDeps = defaultExamsApiDeps) {
  const label = useTemplateLabel();
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.templates(),
    queryFn: () => deps.listExamTemplates(),
    staleTime: 5 * 60_000,
    select: (templates) =>
      templates.map((template) => (template.name ? template : { ...template, name: label(template) })),
  });
}

/** Bản làm việc của cấu trúc; không tự tải lại khi quay lại cửa sổ để không đè bản nháp đang sửa. */
export function useExamStructure(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.structure(id),
    queryFn: () => deps.getExamStructure(id),
    refetchOnWindowFocus: false,
  });
}

export function useSuggestedExamCode(query: SuggestExamCodeQuery, enabled: boolean, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.suggestedCode(query),
    queryFn: () => deps.suggestExamCode(query),
    enabled,
  });
}

export function useAvailableQuestions(query: AvailableQuestionsQuery | null, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: query ? EXAMS_QUERY_KEYS.available(query) : ["exams", "available-questions", null],
    queryFn: () =>
      query ? deps.countAvailableQuestions(query) : Promise.resolve({ total: 0, cells: [], byAnswerType: [] }),
    enabled: query !== null,
    placeholderData: (previous) => previous,
  });
}

/** Đếm nguồn cho cả môn: hợp nguồn của các nhóm trong môn, mỗi câu đếm một lần (soạn chỉ tiêu cấp môn). */
export function useSubjectAvailableQuestions(
  query: SubjectAvailableQuestionsQuery | null,
  deps: ExamsApiDeps = defaultExamsApiDeps,
) {
  return useQuery({
    queryKey: query ? EXAMS_QUERY_KEYS.subjectAvailable(query) : ["exams", "available-questions", "subject", null],
    queryFn: () =>
      query ? deps.countSubjectAvailableQuestions(query) : Promise.resolve({ total: 0, cells: [], byAnswerType: [] }),
    enabled: query !== null,
    placeholderData: (previous) => previous,
  });
}

/** Mọi thao tác ghi làm mới cả nhóm `exams` (danh sách, chi tiết, cấu trúc, kết quả kiểm tra của lane khác). */
function useInvalidateExams() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all });
}

export function useCreateExam(deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: CreateExamRequest) => deps.createExam(request),
    // Cũng khi lỗi: bài có thể đã tạo xong rồi mới lỗi ở bước lưu cấu trúc (`ExamStructureSaveError`).
    onSettled: () => invalidate(),
  });
}

export function useUpdateExamInfo(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: UpdateExamInfoRequest) => deps.updateExamInfo(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateExamStructure(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: UpdateExamStructureRequest) => deps.updateExamStructure(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useApplyExamTemplate(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: (request: ApplyExamTemplateRequest) => deps.applyExamTemplate(id, request),
    onSuccess: () => invalidate(),
  });
}

export function useDuplicateExam(deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => deps.duplicateExam(id, { name }),
    onSuccess: () => invalidate(),
  });
}

export function useDiscontinueExam(deps: ExamsApiDeps = defaultExamsApiDeps) {
  const invalidate = useInvalidateExams();
  return useMutation({
    mutationFn: ({ id, expectedRevision, idempotencyKey }: { id: string; expectedRevision: number; idempotencyKey?: string }) =>
      deps.discontinueExam(id, expectedRevision, idempotencyKey),
    onSuccess: () => invalidate(),
  });
}
