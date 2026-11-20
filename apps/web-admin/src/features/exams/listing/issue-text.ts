"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback } from "react";
import type { ListingCheckIssue } from "../review-types";

/** Hàng của nhóm Thông tin bán theo trường của biểu mẫu: tên hàng, và vấn đề của trường đó. */
export const LISTING_CHECK_ROWS = [
  { id: "price", field: "listPriceVnd" },
  { id: "attempts", field: "attemptsGranted" },
  { id: "description", field: "description" },
  { id: "cover", field: "coverAssetId" },
] as const;

/**
 * Câu mô tả một vấn đề của nhóm Thông tin bán, dựng lúc hiển thị để đổi theo ngôn ngữ (DESIGN.md 5.13). Mức giá
 * tối thiểu lấy từ `minPriceVnd` của vấn đề (máy chủ trả, không viết cứng). Mã lạ dùng câu chung.
 */
export function useListingIssueText() {
  const t = useTranslations("exams.review.listing.issue");
  const format = useFormatter();
  return useCallback(
    (issue: Pick<ListingCheckIssue, "code" | "minPriceVnd">) => {
      if (!t.has(issue.code)) return t("UNKNOWN");
      return t(issue.code, {
        hasMin: issue.minPriceVnd === null ? "no" : "yes",
        min: issue.minPriceVnd === null ? "" : format.number(issue.minPriceVnd),
      });
    },
    [t, format],
  );
}
