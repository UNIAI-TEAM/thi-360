import { createBrowserApiClient } from "@/lib/api/client";
import { toExamsApiError } from "../review-api";
import type {
  PreviewGradeRequest,
  PreviewGradeResponse,
  PreviewTrialPaperResponse,
  PreviewTrialResponse,
} from "./types";

export const PREVIEW_TRIAL_QUERY_KEYS = {
  paper: (examId: string, previewId: string) => ["exams", examId, "preview", previewId, "trial-paper"] as const,
};

export interface PreviewTrialApiDeps {
  extendPreviewForTrial: (args: { examId: string; previewId: string }) => Promise<PreviewTrialResponse>;
  getPreviewTrialPaper: (args: {
    examId: string;
    previewId: string;
    signal?: AbortSignal;
  }) => Promise<PreviewTrialPaperResponse>;
  gradePreviewTrial: (args: {
    examId: string;
    previewId: string;
    answers: NonNullable<PreviewGradeRequest["answers"]>;
  }) => Promise<PreviewGradeResponse>;
}

export async function extendPreviewForTrial({
  examId,
  previewId,
}: {
  examId: string;
  previewId: string;
}): Promise<PreviewTrialResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/exams/{id}/preview/{previewId}/trial",
    {
      params: { path: { id: examId, previewId } },
    },
  );
  if (error || !data) {
    throw toExamsApiError(response.status, error);
  }
  return data;
}

export async function getPreviewTrialPaper({
  examId,
  previewId,
  signal,
}: {
  examId: string;
  previewId: string;
  signal?: AbortSignal;
}): Promise<PreviewTrialPaperResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/exams/{id}/preview/{previewId}/paper",
    {
      params: { path: { id: examId, previewId } },
      signal,
    },
  );
  if (error || !data) {
    throw toExamsApiError(response.status, error);
  }
  return data;
}

export async function gradePreviewTrial({
  examId,
  previewId,
  answers,
}: {
  examId: string;
  previewId: string;
  answers: NonNullable<PreviewGradeRequest["answers"]>;
}): Promise<PreviewGradeResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/exams/{id}/preview/{previewId}/grade",
    {
      params: { path: { id: examId, previewId } },
      body: { answers },
    },
  );
  if (error || !data) {
    throw toExamsApiError(response.status, error);
  }
  return data;
}

export const defaultPreviewTrialApiDeps: PreviewTrialApiDeps = {
  extendPreviewForTrial,
  getPreviewTrialPaper,
  gradePreviewTrial,
};

export function trialPageHref(
  examId: string,
  previewId: string,
  expiresAt?: string | null,
): string {
  const base = `/exams/${encodeURIComponent(examId)}/preview/${encodeURIComponent(previewId)}/try`;
  if (!expiresAt) return base;
  return `${base}?expiresAt=${encodeURIComponent(expiresAt)}`;
}

export function reviewPreviewHref(examId: string): string {
  return `/exams/${encodeURIComponent(examId)}/review?tab=preview`;
}
