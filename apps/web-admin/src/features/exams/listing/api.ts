import type { components } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { mapListingCheckIssues, toExamsApiError } from "../review-api";
import { RESULT_VIEWS, type ExamListing, type ListingTerms, type ResultView, type UpdateListingRequest, type UploadedCover } from "./types";

type ApiListing = components["schemas"]["ExamListing"];
type ApiListingSale = components["schemas"]["ExamListingSale"];

const DEFAULT_RESULT_VIEW: ResultView = "SCORE_AND_ANSWERS";

function resultViewOf(value: string | undefined): ResultView {
  return RESULT_VIEWS.find((candidate) => candidate === value) ?? DEFAULT_RESULT_VIEW;
}

function termsOf(source: ApiListing | ApiListingSale): ListingTerms {
  return {
    coverAssetId: source.coverAssetId ?? null,
    summary: source.summary ?? "",
    description: source.description ?? "",
    objectives: source.objectives ?? "",
    audience: source.audience ?? "",
    listPriceVnd: source.listPriceVnd ?? null,
    attemptsGranted: source.attemptsGranted ?? null,
    benefitAiExplanation: source.benefitAiExplanation ?? false,
    benefitStrengthWeakness: source.benefitStrengthWeakness ?? false,
    benefitPractice: source.benefitPractice ?? false,
    benefitNote: source.benefitNote ?? "",
    resultView: resultViewOf(source.resultView),
  };
}

export function mapListing(listing: ApiListing): ExamListing {
  const sale = listing.published?.listing;
  return {
    ...termsOf(listing),
    examId: listing.examId ?? "",
    revision: listing.revision ?? 0,
    hasUnpublishedChanges: listing.hasUnpublishedChanges ?? false,
    updatedAt: listing.updatedAt ?? null,
    coverUrl: listing.coverUrl ?? null,
    minPriceVnd: listing.minPriceVnd ?? null,
    checkIssues: mapListingCheckIssues(listing.checkIssues),
    published: sale
      ? {
          versionNo: listing.published?.versionNo ?? null,
          publishedAt: listing.published?.publishedAt ?? null,
          terms: termsOf(sale),
        }
      : null,
  };
}

export async function getExamListing(examId: string): Promise<ExamListing> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{examId}/listing", {
    params: { path: { examId } },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapListing(data);
}

export async function updateExamListing(examId: string, request: UpdateListingRequest): Promise<ExamListing> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/exams/{examId}/listing", {
    params: { path: { examId } },
    body: {
      expectedRevision: request.expectedRevision,
      coverAssetId: request.coverAssetId ?? undefined,
      summary: request.summary,
      description: request.description,
      objectives: request.objectives,
      audience: request.audience,
      listPriceVnd: request.listPriceVnd ?? undefined,
      attemptsGranted: request.attemptsGranted ?? undefined,
      benefitAiExplanation: request.benefitAiExplanation,
      benefitStrengthWeakness: request.benefitStrengthWeakness,
      benefitPractice: request.benefitPractice,
      benefitNote: request.benefitNote,
      resultView: request.resultView,
    },
  });
  if (!response.ok || !data) throw toExamsApiError(response.status, error);
  return mapListing(data);
}

/** Tải ảnh bìa lên (chỉ tạo ảnh; `PUT listing` mới gắn ảnh vào bài). */
export async function uploadExamCover(examId: string, file: File): Promise<UploadedCover> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/exams/{examId}/cover", {
    params: { path: { examId } },
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const formData = new FormData();
      formData.append("file", file);
      return formData;
    },
  });
  if (!response.ok || !data?.assetId) throw toExamsApiError(response.status, error);
  return { assetId: data.assetId, url: data.url ?? `/api/v1/admin/exams/${encodeURIComponent(examId)}/cover/${data.assetId}` };
}
