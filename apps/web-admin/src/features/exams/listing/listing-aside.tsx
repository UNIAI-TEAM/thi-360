"use client";

import { Badge, Card } from "@thi360/ui";
import { ArrowRight, CircleAlert, CircleCheck, Clock, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { reviewHref } from "../components/exam-review-shared";
import type { ListingCheckIssue } from "../review-types";
import type { ExamDetail } from "../types";
import { CoverFallback } from "./listing-cover";
import { useListingIssueText } from "./issue-text";
import type { ExamListing, ListingTerms } from "./types";

export interface ListingCardPreviewProps {
  exam: ExamDetail;
  /** Điều khoản đang nhập (chưa lưu cũng hiện). */
  terms: ListingTerms;
  coverUrl: string | null;
  /** Bản đang bán, để so với giá sau khi công bố. */
  listing: ExamListing;
}

/**
 * Xem trước thẻ như học viên thấy ở danh mục sau khi công bố (TM 6.4): đổi theo dữ liệu đang nhập, dùng giá gốc chưa tính
 * khuyến mãi. Thẻ chỉ để xem (`inert`); hai dòng dưới thẻ so bản đang bán với bản sau khi công bố.
 */
export function ListingCardPreview({ exam, terms, coverUrl, listing }: ListingCardPreviewProps) {
  const t = useTranslations("exams.listing.preview");
  const tExams = useTranslations("exams");
  const format = useFormatter();
  const money = (amount: number | null) => (amount === null ? t("noPrice") : t("money", { amount: format.number(amount) }));
  const summary = (price: number | null, attempts: number | null) =>
    t("compareValue", { price: money(price), hasAttempts: attempts === null ? "no" : "yes", attempts: attempts ?? 0 });
  const published = listing.published;

  return (
    <Card padding="lg" className="flex flex-col gap-3.5">
      <div>
        <h2 id="listing-preview-title" className="text-title font-bold text-text">
          {t("title")}
        </h2>
        <p className="text-caption text-text-muted">{t("description")}</p>
      </div>
      <div
        inert
        aria-hidden="true"
        data-testid="listing-card-preview"
        className="flex flex-col overflow-hidden rounded-xl border border-border bg-surface"
      >
        {coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- ảnh bản nháp lấy qua API quản trị cùng gốc
          <img src={coverUrl} alt="" className="aspect-video w-full object-cover" />
        ) : (
          <CoverFallback label={exam.code} className="p-4" />
        )}
        <div className="flex flex-col gap-2.5 p-4">
          <div className="flex flex-wrap gap-1.5">
            <Badge>{tExams(`kind.${exam.kind}`)}</Badge>
            <Badge>{tExams(`scope.${exam.scope}`)}</Badge>
          </div>
          <p data-exam-content className="text-label font-semibold uppercase text-text wrap-anywhere">
            {exam.name}
          </p>
          {terms.summary && (
            <p data-exam-content className="text-body text-text-secondary wrap-anywhere">
              {terms.summary}
            </p>
          )}
          <p className="flex items-center gap-1.5 text-body text-text-secondary tabular-nums">
            <Clock className="size-4" />
            {t("meta", {
              questions: exam.totalQuestions,
              minutes: exam.durationMinutes,
              hasAttempts: terms.attemptsGranted === null ? "no" : "yes",
              attempts: terms.attemptsGranted ?? 0,
            })}
          </p>
          <p className="text-h3 font-bold text-primary-text tabular-nums">{money(terms.listPriceVnd)}</p>
          <span className="flex h-11 items-center justify-center rounded-lg bg-primary-fill font-semibold text-on-primary">
            {t("buy")}
          </span>
          <span className="flex h-10 items-center justify-center rounded-lg border border-border font-semibold text-text-secondary">
            {t("detail")}
          </span>
        </div>
      </div>
      <dl className="m-0 grid grid-cols-2 gap-2.5 border-t border-border pt-3 tabular-nums">
        <div>
          <dt className="text-caption text-text-muted">
            {published ? t("serving", { hasVersion: published.versionNo === null ? "no" : "yes", version: published.versionNo ?? 0 }) : t("servingNone")}
          </dt>
          <dd className="m-0 font-semibold text-text">
            {published ? summary(published.terms.listPriceVnd, published.terms.attemptsGranted) : t("notPublished")}
          </dd>
        </div>
        <div>
          <dt className="text-caption text-text-muted">{t("afterPublish")}</dt>
          <dd className="m-0 font-semibold text-text">{summary(listing.listPriceVnd, listing.attemptsGranted)}</dd>
        </div>
      </dl>
    </Card>
  );
}

/**
 * Kiểm tra thông tin bán của bản làm việc đã lưu (`ExamListing.checkIssues`), kèm lối sang tab Kiểm tra. Lỗi chặn công
 * bố có icon lỗi, ảnh bìa chỉ là cảnh báo.
 */
export function ListingCheckSummary({ examId, issues }: { examId: string; issues: readonly ListingCheckIssue[] }) {
  const t = useTranslations("exams.listing.check");
  const issueText = useListingIssueText();
  const blocking = issues.filter((issue) => issue.severity === "ERROR");
  const hasCover = !issues.some((issue) => issue.code === "LISTING_COVER_MISSING");

  return (
    <Card padding="md" className="flex flex-col gap-2">
      <h3 className="text-label font-semibold text-text">{t("title")}</h3>
      <ul className="flex flex-col gap-1.5">
        {blocking.length === 0 && (
          <li className="flex items-start gap-2 text-body text-text">
            <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0 text-success" />
            {t("ready")}
          </li>
        )}
        {issues.map((issue) => (
          <li key={issue.key} className="flex items-start gap-2 text-body text-text">
            {issue.severity === "ERROR" ? (
              <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-danger" />
            ) : (
              <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            )}
            <span>
              <span className="sr-only">{t(issue.severity === "ERROR" ? "errorPrefix" : "warningPrefix")}</span>
              {issueText(issue)}
            </span>
          </li>
        ))}
        {hasCover && (
          <li className="flex items-start gap-2 text-body text-text">
            <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0 text-success" />
            {t("hasCover")}
          </li>
        )}
      </ul>
      <Link
        href={reviewHref(examId)}
        className="inline-flex min-h-11 w-fit items-center gap-1.5 rounded-sm font-semibold text-primary-text hover:underline"
      >
        {t("toReview")}
        <ArrowRight aria-hidden className="size-4" />
      </Link>
    </Card>
  );
}
