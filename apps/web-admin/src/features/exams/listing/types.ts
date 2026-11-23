import type { ListingCheckIssue } from "../review-types";

export const RESULT_VIEWS = ["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"] as const;
export type ResultView = (typeof RESULT_VIEWS)[number];

export interface ListingTerms {
  coverAssetId: string | null;
  summary: string;
  description: string;
  objectives: string;
  audience: string;
  listPriceVnd: number | null;
  attemptsGranted: number | null;
  benefitAiExplanation: boolean;
  benefitStrengthWeakness: boolean;
  benefitPractice: boolean;
  benefitNote: string;
  resultView: ResultView;
}

export interface PublishedListing {
  versionNo: number | null;
  publishedAt: string | null;
  terms: ListingTerms;
}

export interface ExamListing extends ListingTerms {
  examId: string;
  revision: number;
  hasUnpublishedChanges: boolean;
  updatedAt: string | null;
  coverUrl: string | null;
  minPriceVnd: number | null;
  checkIssues: ListingCheckIssue[];
  published: PublishedListing | null;
}

export interface UpdateListingRequest extends ListingTerms {
  expectedRevision: number;
}

export interface UploadedCover {
  assetId: string;
  url: string;
}
