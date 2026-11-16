"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useEffectEvent, useState } from "react";
import { EXAMS_QUERY_KEYS } from "./api";
import { defaultExamReviewApiDeps, EXAM_REVIEW_QUERY_KEYS, type ExamReviewApiDeps } from "./review-api";
import type { ExamCheckResult, GeneratePreviewRequest, PublishExamRequest, RunExamCheckRequest } from "./review-types";

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

/** Chu kỳ hỏi `getCheckRun` khi kiểm tra kỹ đang chạy nền (spec 5.4). */
export const DEEP_CHECK_POLL_MS = 2000;

export type DeepCheckPhase = "idle" | "running" | "failed";

export interface DeepCheck {
  phase: DeepCheckPhase;
  /** Mã lỗi của lần chạy nền (`CHECK_RUN_ABANDONED`); `null` khi chưa lỗi hoặc lỗi không có mã (mất kết nối). */
  errorCode: string | null;
  /** Bắt đầu một lần kiểm tra kỹ; gọi lại sau lỗi là thử lại bằng lần chạy mới. Bỏ qua khi đang chạy. */
  start: () => void;
}

export interface UseDeepCheckOptions {
  /** `false` khi màn bị che (đổi tab): ngừng hỏi, lần chạy vẫn còn đó và hỏi tiếp khi quay lại. */
  active?: boolean;
  /** Gọi một lần khi lần chạy xong, sau khi kết quả đã thay kết quả đang hiện. */
  onDone?: (result: ExamCheckResult) => void;
  deps?: ExamReviewApiDeps;
}

/**
 * Kiểm tra kỹ (spec 5.4): chạy nền tới 120 giây, proxy web cắt ở 60 giây nên `startDeepCheck` trả `runId` ngay, rồi
 * hỏi `getCheckRun` mỗi 2 giây tới khi xong. Hỏi dừng khi màn rời đi (gỡ khỏi cây hoặc `active = false`). Xong thì kết
 * quả ghi thẳng vào bộ đệm kiểm tra, thay kết quả đang hiện.
 */
export function useDeepCheck(
  examId: string,
  { active = true, onDone, deps = defaultExamReviewApiDeps }: UseDeepCheckOptions = {},
): DeepCheck {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateExam(examId, { includeCheck: false });
  const [runId, setRunId] = useState<string | null>(null);
  const starter = useMutation({
    mutationFn: () => deps.startDeepCheck(examId),
    onSuccess: (started) => setRunId(started.runId),
  });
  const run = useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.checkRun(examId, runId ?? ""),
    queryFn: () => deps.getCheckRun(examId, runId!),
    // Hết chạy (DONE/FAILED) thì tắt hẳn: lần chạy chỉ giữ 24 giờ, hỏi lại sau đó là 404 lên banner lỗi.
    enabled: (query) =>
      runId !== null && active && query.state.data?.state !== "DONE" && query.state.data?.state !== "FAILED",
    // Truy vấn lỗi thì dừng chu kỳ hỏi: TanStack vẫn giữ interval khi `status` là "error", thử lại bằng `start()`.
    refetchInterval: (query) =>
      query.state.status === "error"
        ? false
        : query.state.data === undefined || query.state.data.state === "RUNNING"
          ? DEEP_CHECK_POLL_MS
          : false,
    retry: 2,
    retryDelay: DEEP_CHECK_POLL_MS,
  });

  const finished = run.data?.state === "DONE" ? run.data.result : null;
  const finish = useEffectEvent((result: ExamCheckResult) => {
    queryClient.setQueryData(EXAM_REVIEW_QUERY_KEYS.check(examId), result);
    void invalidate();
    onDone?.(result);
  });
  useEffect(() => {
    if (finished) finish(finished);
  }, [finished]);

  const { mutate, reset } = starter;
  const phase: DeepCheckPhase =
    starter.isError || run.isError || run.data?.state === "FAILED"
      ? "failed"
      : run.data?.state !== "DONE" && (starter.isPending || runId !== null)
        ? "running"
        : "idle";
  const start = () => {
    if (phase === "running") return;
    setRunId(null);
    reset();
    mutate();
  };

  return { phase, errorCode: run.data?.state === "FAILED" ? run.data.errorCode : null, start };
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
