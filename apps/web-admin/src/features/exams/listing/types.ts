import type { ListingCheckIssue } from "../review-types";

/** Mức xem kết quả sau khi chấm (TM 6.1, C16). */
export const RESULT_VIEWS = ["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"] as const;
export type ResultView = (typeof RESULT_VIEWS)[number];

/** Các trường bán của một bản (bản làm việc hoặc bản đang bán), dùng chung để so hai bản. */
export interface ListingTerms {
  coverAssetId: string | null;
  summary: string;
  description: string;
  objectives: string;
  audience: string;
  /** `null`: chưa nhập giá. */
  listPriceVnd: number | null;
  /** `null`: chưa nhập số lượt. */
  attemptsGranted: number | null;
  benefitAiExplanation: boolean;
  benefitStrengthWeakness: boolean;
  benefitPractice: boolean;
  benefitNote: string;
  resultView: ResultView;
}

/** Bản đang phục vụ (`ExamListing.published`): chỉ đọc. */
export interface PublishedListing {
  versionNo: number | null;
  publishedAt: string | null;
  terms: ListingTerms;
}

/** Thông tin bán của bài thi: bản làm việc cộng bản đang bán (`GET /exams/{id}/listing`). */
export interface ExamListing extends ListingTerms {
  examId: string;
  /** Revision của bài thi; gửi lại làm `expectedRevision` khi lưu. */
  revision: number;
  hasUnpublishedChanges: boolean;
  updatedAt: string | null;
  /** Đường mở ảnh bìa bản nháp; `null` khi chưa có ảnh. */
  coverUrl: string | null;
  /** Mức giá thấp nhất công bố được (`THI360_VNPAY_MIN_AMOUNT`); `null` khi máy chủ không gửi. */
  minPriceVnd: number | null;
  /** Vấn đề của bản làm việc, tính lúc đọc. */
  checkIssues: ListingCheckIssue[];
  /** `null`: chưa công bố hoặc phiên bản đang phục vụ có trước thông tin bán. */
  published: PublishedListing | null;
}

/** Thân `PUT /exams/{id}/listing`. Chuỗi rỗng xóa trường; `null` xóa giá và số lượt. */
export interface UpdateListingRequest {
  expectedRevision: number;
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

export interface UploadedCover {
  assetId: string;
  url: string;
}
