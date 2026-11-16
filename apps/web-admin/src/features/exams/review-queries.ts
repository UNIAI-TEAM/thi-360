"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EXAMS_QUERY_KEYS } from "./api";
import { defaultExamReviewApiDeps, EXAM_REVIEW_QUERY_KEYS, type ExamReviewApiDeps } from "./review-api";
import type { GeneratePreviewRequest, PublishExamRequest, RunExamCheckRequest } from "./review-types";

/** Sau thao tác ghi: bài thi (chip trạng thái, dải tóm tắt), danh sách bài thi và dữ liệu kiểm tra, phiên bản. */
function useInvalidateExam(examId: string, { includeCheck = true }: { includeCheck?: boolean } = {}) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.detail(examId) }),
      queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.lists() }),
      includeCheck && queryClient.invalidateQueries({ queryKey: EXAM_REVIEW_QUERY_KEYS.check(examId) }),
      queryClient.invalidateQueries({ queryKey: EXAM_REVIEW_QUERY_KEYS.versions(examId) }),
    ]);
}

export function useExamCheck(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.check(examId),
    queryFn: () => deps.getExamCheck({ examId }),
  });
}

export function useRunExamCheck(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  const queryClient = useQueryClient();
  // Kết quả vừa chạy ghi thẳng vào bộ đệm, không tải lại khóa kiểm tra.
  const invalidate = useInvalidateExam(examId, { includeCheck: false });
  return useMutation({
    mutationFn: (request: Omit<RunExamCheckRequest, "examId">) => deps.runExamCheck({ examId, ...request }),
    onSuccess: async (result) => {
      queryClient.setQueryData(EXAM_REVIEW_QUERY_KEYS.check(examId), result);
      await invalidate();
    },
  });
}

/** Sinh đề là một lần chạy (mỗi lần một hạt giống), không phải dữ liệu đọc lại: dùng mutation, kết quả giữ ở màn. */
export function useGeneratePreview(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  return useMutation({
    mutationFn: (request: Omit<GeneratePreviewRequest, "examId">) => deps.generatePreview({ examId, ...request }),
  });
}

export function useExamVersions(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.versions(examId),
    queryFn: () => deps.listExamVersions({ examId }),
  });
}

export function useExamVersion(
  examId: string,
  version: number | null,
  deps: ExamReviewApiDeps = defaultExamReviewApiDeps,
) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.version(examId, version ?? 0),
    queryFn: () => deps.getExamVersion({ examId, version: version ?? 0 }),
    enabled: version !== null,
  });
}

export function usePublishExam(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  const invalidate = useInvalidateExam(examId);
  return useMutation({
    mutationFn: (request: Omit<PublishExamRequest, "examId">) => deps.publishExam({ examId, ...request }),
    onSuccess: () => invalidate(),
  });
}

export function useDiscontinueExam(examId: string, deps: ExamReviewApiDeps = defaultExamReviewApiDeps) {
  const invalidate = useInvalidateExam(examId);
  return useMutation({
    mutationFn: ({ expectedRevision }: { expectedRevision: number }) =>
      deps.discontinueExam({ examId, expectedRevision }),
    onSuccess: () => invalidate(),
  });
}
