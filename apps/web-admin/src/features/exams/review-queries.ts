"use client";

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import { getErrorStatus } from "@/lib/api/query-client";
import { EXAM_REVIEW_QUERY_KEYS, EXAMS_QUERY_KEYS } from "./keys";
import {
  generatePreview,
  getCheckRun,
  getExamCheck,
  getExamVersion,
  listExamVersions,
  publishExam,
  runExamCheck,
  startDeepCheck,
} from "./review-api";
import type {
  ExamCheckResult,
  GeneratePreviewRequest,
  PublishExamRequest,
  RunExamCheckRequest,
} from "./review-types";

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

export function useExamCheck(examId: string, enabled = true) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.check(examId),
    queryFn: () => getExamCheck({ examId }),
    enabled,
  });
}

export function useRunExamCheck(examId: string) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateExam(examId, { includeCheck: false });
  return useMutation({
    mutationFn: (request: Omit<RunExamCheckRequest, "examId">) => runExamCheck({ examId, ...request }),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: EXAM_REVIEW_QUERY_KEYS.check(examId) });
    },
    onSuccess: async (result) => {
      queryClient.setQueryData(EXAM_REVIEW_QUERY_KEYS.check(examId), result);
      await invalidate();
    },
  });
}

export function useIdempotencyKey() {
  const current = useRef<{ signature: string; key: string } | null>(null);
  return {
    keyFor(signature: string): string {
      if (current.current?.signature !== signature) current.current = { signature, key: crypto.randomUUID() };
      return current.current.key;
    },
    release() {
      current.current = null;
    },
  };
}

const DEEP_CHECK_POLL_MS = 2000;

type DeepCheckPhase = "idle" | "running" | "failed";

export interface DeepCheck {
  phase: DeepCheckPhase;
  errorCode: string | null;
  start: () => void;
}

interface UseDeepCheckOptions {
  active?: boolean;
  onDone?: (result: ExamCheckResult) => void;
}

const trackedRunsByClient = new WeakMap<QueryClient, Map<string, string>>();

function trackedRunsOf(client: QueryClient): Map<string, string> {
  let runs = trackedRunsByClient.get(client);
  if (!runs) {
    runs = new Map();
    trackedRunsByClient.set(client, runs);
  }
  return runs;
}

export function useDeepCheck(
  examId: string,
  { active = true, onDone }: UseDeepCheckOptions = {},
): DeepCheck {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateExam(examId, { includeCheck: false });
  const trackedRuns = trackedRunsOf(queryClient);
  const [runId, setTrackedRun] = useState<string | null>(() => trackedRuns.get(examId) ?? null);
  const setRunId = (next: string | null) => {
    if (next === null) trackedRuns.delete(examId);
    else trackedRuns.set(examId, next);
    setTrackedRun(next);
  };
  const starter = useMutation({
    mutationFn: () => startDeepCheck(examId),
    onSuccess: (started) => setRunId(started.runId),
  });
  const run = useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.checkRun(examId, runId ?? ""),
    queryFn: () => getCheckRun(examId, runId!),
    enabled: (query) =>
      runId !== null && active && query.state.data?.state !== "DONE" && query.state.data?.state !== "FAILED",
    refetchInterval: (query) =>
      query.state.status === "error"
        ? false
        : query.state.data === undefined || query.state.data.state === "RUNNING"
          ? DEEP_CHECK_POLL_MS
          : false,
    retry: (failureCount, error) => getErrorStatus(error) !== 404 && failureCount < 2,
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
  const settled = run.data?.state === "DONE" || run.data?.state === "FAILED" || run.isError;
  useEffect(() => {
    if (settled) trackedRuns.delete(examId);
  }, [settled, examId, trackedRuns]);

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

export function useGeneratePreview(examId: string) {
  return useMutation({
    mutationFn: (request: Omit<GeneratePreviewRequest, "examId">) => generatePreview({ examId, ...request }),
  });
}

export function useExamVersions(examId: string, enabled = true) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.versions(examId),
    queryFn: () => listExamVersions({ examId }),
    enabled,
  });
}

export function useExamVersion(examId: string, version: number | null) {
  return useQuery({
    queryKey: EXAM_REVIEW_QUERY_KEYS.version(examId, version ?? 0),
    queryFn: () => getExamVersion({ examId, version: version ?? 0 }),
    enabled: version !== null,
  });
}

export function usePublishExam(examId: string) {
  const invalidate = useInvalidateExam(examId);
  return useMutation({
    mutationFn: (request: Omit<PublishExamRequest, "examId">) => publishExam({ examId, ...request }),
    onSuccess: () => invalidate(),
  });
}
