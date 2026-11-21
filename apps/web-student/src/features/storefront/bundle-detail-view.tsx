import { Badge, cn, focusRing } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { Package } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { BundleBuyPanel } from "./bundle-buy-panel";
import { SectionHeading, SupportLine, YesNo } from "./exam-detail-view";
import { REVIEW_BUILD_MODE, rangeText } from "./format";
import { FormatChip } from "./product-card";
import type { BundleDetailItem, ProductDetail } from "./types";

const cardClass = "flex flex-col gap-4 rounded-xl border border-border bg-surface p-6";

function BundleExamRow({ item, index }: { item: BundleDetailItem; index: number }) {
  const t = useTranslations("bundle.detail");
  const detail = useTranslations("storefront.detail");
  const storefront = useTranslations("storefront");
  const locale = useLocale();

  const questions = rangeText(item.questionCount, locale);
  const duration = rangeText(item.durationMinutes, locale);
  const maxPoints = rangeText(item.maxPoints, locale);
  const subjectLabel = item.subjectName ? pickName(item.subjectName, locale) : "";
  const scopeLabel =
    item.scope === "PART" ? t("partScope") : item.scope === "SUBJECT" ? t("subjectScope") : item.scope;

  return (
    <li className="flex flex-col gap-3 rounded-lg border border-border p-4">
      <div className="flex flex-wrap items-start gap-3">
        <span
          aria-hidden
          className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-50 text-label font-bold text-primary-text tabular-nums"
        >
          {index + 1}
        </span>
        <div className="min-w-0 flex-1 basis-56">
          <div className="flex flex-wrap items-center gap-2">
            <Link
              href={`/exams/${encodeURIComponent(item.code)}`}
              data-exam-content
              className={cn("rounded-sm text-title font-semibold text-text hover:text-primary-text hover:underline", focusRing)}
            >
              {item.name}
            </Link>
            {item.buildMode === REVIEW_BUILD_MODE && (
              <span className="rounded-full bg-info-bg px-2.5 py-0.5 text-caption font-semibold text-info">
                {storefront("review")}
              </span>
            )}
          </div>
          <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-field-label text-text-muted">
            {subjectLabel && <span>{subjectLabel}</span>}
            {subjectLabel && scopeLabel && <span aria-hidden>·</span>}
            {scopeLabel && <span>{scopeLabel}</span>}
            {item.partCode && (
              <>
                <span aria-hidden>·</span>
                <span data-exam-content>{item.partCode}</span>
              </>
            )}
          </p>
        </div>
        <dl className="flex flex-wrap gap-x-4 gap-y-1 text-body text-text-secondary tabular-nums">
          {questions !== null && (
            <div className="flex items-baseline gap-1">
              <dt className="sr-only">{detail("columns.questions")}</dt>
              <dd>{storefront("units.questions", { value: questions, count: item.questionCount?.max ?? 0 })}</dd>
            </div>
          )}
          {duration !== null && (
            <div className="flex items-baseline gap-1">
              <dt className="sr-only">{detail("columns.duration")}</dt>
              <dd>{storefront("units.minutes", { value: duration, count: item.durationMinutes?.max ?? 0 })}</dd>
            </div>
          )}
          {maxPoints !== null && (
            <div className="flex items-baseline gap-1">
              <dt className="sr-only">{detail("columns.maxPoints")}</dt>
              <dd>{detail("how.maxScore", { score: maxPoints })}</dd>
            </div>
          )}
        </dl>
      </div>

      <div className="flex flex-col gap-1.5 border-t border-border pt-3 text-field-label text-text-secondary sm:pl-11">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <span className="font-medium text-text">
            {t("examRightsPrefix")} {storefront("units.attempts", { count: item.attemptsGranted })}
          </span>
          {item.resultView && (
            <span>
              {detail.has(`rights.resultViews.${item.resultView}`)
                ? detail(`rights.resultViews.${item.resultView}`)
                : item.resultView}
            </span>
          )}
          <span className="inline-flex items-center gap-1.5">
            <span>{detail("rights.practice")}:</span>
            <YesNo value={item.benefits.practice} />
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span>{detail("rights.strengths")}:</span>
            <YesNo value={item.benefits.strengthWeakness} />
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span>{detail("rights.ai")}:</span>
            <YesNo value={item.benefits.aiExplanation} />
          </span>
        </div>
        {item.benefits.note && (
          <p className="text-caption text-text-muted">
            {detail("rights.noteLabel")} <span data-exam-content>{item.benefits.note}</span>
          </p>
        )}
      </div>
    </li>
  );
}

/** Trang chi tiết bộ đề (AC-3, AC-4, AC-11, AC-12, AC-17). */
export function BundleDetailView({
  product,
  unavailable = false,
}: {
  product: ProductDetail;
  unavailable?: boolean;
}) {
  const t = useTranslations("bundle");
  const detail = useTranslations("storefront.detail");
  const storefront = useTranslations("storefront");
  const locale = useLocale();

  const items = product.items ?? [];
  const examKindLabel = pickName(product.examKind, locale) || product.examKind.code;
  const isUnavailable =
    unavailable || (product as ProductDetail & { saleState?: string }).saleState === "UNAVAILABLE";

  return (
    <div className="mx-auto flex w-full max-w-[1170px] flex-col gap-6 px-4 py-6 sm:px-6">
      <nav aria-label={detail("breadcrumbLabel")}>
        <ol className="flex flex-wrap items-center gap-2 text-field-label text-text-muted">
          <li>
            <Link href="/" className={cn("rounded-sm hover:underline", focusRing)}>
              {detail("breadcrumbHome")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li>
            <Link href="/#exams" className={cn("rounded-sm hover:underline", focusRing)}>
              {detail("breadcrumbCatalog")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li aria-current="page" className="text-text" data-exam-content>
            {product.name}
          </li>
        </ol>
      </nav>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-5">
          <section aria-labelledby="p-title" className="overflow-hidden rounded-xl border border-border bg-surface">
            <div className="flex aspect-[16/5] flex-col justify-between bg-[#065F46] bg-[linear-gradient(115deg,#065F46,#047857)] p-6 text-white forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
              <span className="self-start rounded-full border border-white/30 bg-black/20 px-3 py-0.5 text-caption font-semibold text-white forced-colors:border-[CanvasText] forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
                {product.examKind.code}
              </span>
              <div className="flex items-center gap-3">
                <Package aria-hidden className="size-8 shrink-0" />
                <span className="text-h2 font-bold">{storefront("format.BUNDLE")}</span>
              </div>
            </div>
            <div className="flex flex-col gap-3.5 p-6">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-primary-600 bg-primary-50 px-2.5 py-0.5 text-caption font-semibold text-primary-700">
                  {examKindLabel}
                </span>
                <FormatChip format={product.format} />
                <Badge>{t("detail.examCountValue", { count: items.length })}</Badge>
                {product.buildMode === REVIEW_BUILD_MODE && (
                  <span className="rounded-full bg-info-bg px-2.5 py-0.5 text-caption font-semibold text-info">
                    {storefront("review")}
                  </span>
                )}
                <span className="text-caption text-text-muted">
                  {detail("codeLabel")} <strong className="font-semibold text-text">{product.code}</strong>
                </span>
              </div>
              <h1 id="p-title" data-exam-content className="text-h1 font-bold text-text">
                {product.name}
              </h1>
              {product.summary && (
                <p data-exam-content className="text-title text-text-secondary">
                  {product.summary}
                </p>
              )}
              {product.description && (
                <p data-exam-content className="text-text-secondary">
                  {product.description}
                </p>
              )}
            </div>
          </section>

          <section aria-labelledby="b-items" className={cardClass}>
            <div className="flex flex-wrap items-center gap-3">
              <SectionHeading id="b-items">{t("detail.itemsTitle")}</SectionHeading>
              <Badge>{t("detail.examCountValue", { count: items.length })}</Badge>
            </div>
            <ol className="flex flex-col gap-3">
              {items.map((item, index) => (
                <BundleExamRow key={item.examId || item.code} item={item} index={index} />
              ))}
            </ol>
            <p className="text-caption text-text-muted">{t("detail.itemsNote")}</p>
          </section>

          <section aria-labelledby="b-how" className={cardClass}>
            <SectionHeading id="b-how">{t("detail.howTitle")}</SectionHeading>
            <ul className="flex list-disc flex-col gap-2 pl-5 text-text-secondary">
              <li>{t("detail.how1")}</li>
              <li>{t("detail.how2")}</li>
              <li>{t("detail.how3")}</li>
            </ul>
          </section>

          <section aria-labelledby="s-policy" className={cardClass}>
            <SectionHeading id="s-policy">{detail("policy.title")}</SectionHeading>
            <p className="text-text-secondary">{detail("policy.priceNote")}</p>
            <p className="text-text-secondary">{detail("policy.refund", { policy: detail("policy.refundPending") })}</p>
            <SupportLine />
          </section>
        </div>

        <BundleBuyPanel product={product} unavailable={isUnavailable} />
      </div>
    </div>
  );
}
