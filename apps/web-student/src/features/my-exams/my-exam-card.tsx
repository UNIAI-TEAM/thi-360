"use client";

import { Button, StatusChip, type StatusTone } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { RefreshCw } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { REVIEW_BUILD_MODE, rangeText } from "@/features/storefront/format";
import { FormatChip } from "@/features/storefront/product-card";
import { readSupportContact } from "@/features/storefront/support";
import type { MyExamCardData } from "./api";

const STATUS_TONE: Record<Exclude<MyExamCardData["state"], "CHECKING" | "NONE">, StatusTone> = {
  IN_PROGRESS: "info",
  CAN_START: "success",
  EXHAUSTED: "neutral",
};

function formatSupportSummary(): string {
  const contact = readSupportContact();
  const channels = [contact.phone, contact.email].filter(Boolean).join(" · ");
  if (!channels) return contact.hours;
  return contact.hours ? `${channels} (${contact.hours})` : channels;
}

export function CheckingExamCard({
  card,
  onReload,
  reloading,
}: {
  card: MyExamCardData;
  onReload: () => void;
  reloading: boolean;
}) {
  const t = useTranslations("home.checking");
  const support = useTranslations("landing.support");
  const locale = useLocale();
  const examKindName = pickName(card.examKind, locale) || card.examKind.code;
  const contactText = formatSupportSummary();
  const orderCode = card.pendingOrderCode?.trim() ?? "";

  return (
    <article className="flex flex-wrap items-center gap-4 rounded-xl border border-warning border-l-4 bg-surface p-5">
      <div className="flex min-w-0 flex-[999_1_28rem] flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex min-h-6 items-center rounded-full bg-subtle px-2.5 py-0.5 text-caption font-medium text-text-secondary">
            {examKindName}
          </span>
          <StatusChip tone="warning">{t("status")}</StatusChip>
        </div>
        <h3 data-exam-content className="text-title font-semibold text-text">
          {card.name}
        </h3>
        <p className="text-body text-text-secondary">
          {orderCode
            ? contactText
              ? t("message", { orderCode, contact: contactText })
              : t("messageNoContact", { orderCode, pending: support("pending") })
            : contactText
              ? t("messageNoOrder", { contact: contactText })
              : t("messageNoOrderNoContact", { pending: support("pending") })}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        {orderCode && (
          <Button asChild variant="outline">
            <Link href={`/account/orders/${encodeURIComponent(orderCode)}`}>{t("viewOrder")}</Link>
          </Button>
        )}
        <Button type="button" variant="secondary" loading={reloading} onClick={onReload}>
          <RefreshCw aria-hidden />
          {t("reload")}
        </Button>
      </div>
    </article>
  );
}

export function MyExamCard({ card }: { card: MyExamCardData }) {
  const t = useTranslations("home");
  const storefront = useTranslations("storefront");
  const locale = useLocale();

  const examKindName = pickName(card.examKind, locale) || card.examKind.code;
  const questions = rangeText(card.questionCount);
  const minutes = rangeText(card.durationMinutes);
  const meta = [
    questions !== null && storefront("units.questions", { value: questions, count: card.questionCount?.max ?? 0 }),
    minutes !== null && t("card.minutesPerAttempt", { value: minutes, count: card.durationMinutes?.max ?? 0 }),
  ].filter(Boolean);

  const statusTone =
    card.state === "IN_PROGRESS" || card.state === "CAN_START" || card.state === "EXHAUSTED"
      ? STATUS_TONE[card.state]
      : "neutral";
  const statusLabel =
    card.state === "IN_PROGRESS" || card.state === "CAN_START" || card.state === "EXHAUSTED"
      ? t(`status.${card.state}`)
      : card.state;

  const hasAction = (action: MyExamCardData["actions"][number]) => card.actions.includes(action);
  const canContinue = hasAction("CONTINUE") && Boolean(card.activeAttemptId);
  const canStart = hasAction("START") && Boolean(card.entitlementId);
  const canViewResult = hasAction("VIEW_RESULT") && Boolean(card.resultAttemptId);
  const canViewHistory = hasAction("HISTORY");
  const isOnSale = card.saleState === "ON_SALE";
  const canRebuy = isOnSale && !card.hasPendingFulfillment && hasAction("REBUY");
  const canBuyMore = isOnSale && !card.hasPendingFulfillment && hasAction("BUY_MORE");

  return (
    <article className="flex flex-col gap-3.5 rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="inline-flex min-h-6 items-center rounded-full bg-subtle px-2.5 py-0.5 text-caption font-medium text-text-secondary">
          {examKindName}
        </span>
        <span className="inline-flex min-h-6 items-center rounded-full bg-primary-50 px-2.5 py-0.5 text-caption font-semibold text-primary-text">
          {t("card.purchased")}
        </span>
        {card.buildMode === REVIEW_BUILD_MODE && (
          <span className="inline-flex min-h-6 items-center rounded-full bg-info-bg px-2.5 py-0.5 text-caption font-semibold text-info">
            {storefront("review")}
          </span>
        )}
        <StatusChip tone={statusTone} className="ml-auto">
          {statusLabel}
        </StatusChip>
      </div>

      <div className="flex flex-col gap-1.5">
        <h3 data-exam-content className="line-clamp-2 text-title font-semibold text-text">
          {card.name}
        </h3>
        <div className="flex flex-wrap items-center gap-1.5">
          <FormatChip format={card.format} scopeLabel={card.scopeLabel ?? undefined} />
        </div>
        {meta.length > 0 && <p className="text-field-label text-text-muted">{meta.join(" · ")}</p>}
      </div>

      <div className="flex items-baseline justify-between rounded-lg bg-page px-3 py-2.5">
        <span className="text-field-label text-text-secondary">{t("card.attemptsLeft")}</span>
        <strong className="text-h3 font-bold text-text tabular-nums">{card.attemptsLeft}</strong>
      </div>

      <div className="mt-auto flex flex-col gap-2">
        {canContinue && (
          <Button asChild variant="exam" className="h-11 w-full">
            <Link href={`/attempts/${encodeURIComponent(card.activeAttemptId!)}`} prefetch={false}>
              {t("actions.continue")}
            </Link>
          </Button>
        )}
        {!canContinue && canStart && (
          <Button asChild variant="exam" className="h-11 w-full">
            <Link href={`/prepare/${encodeURIComponent(card.entitlementId!)}`} prefetch={false}>
              {t("actions.start")}
            </Link>
          </Button>
        )}
        {canRebuy && (
          <Button asChild variant="primary" className="h-11 w-full">
            <Link href={`/checkout/${encodeURIComponent(card.code)}`} prefetch={false}>
              {t("actions.rebuy")}
            </Link>
          </Button>
        )}

        {card.state === "IN_PROGRESS" && card.attemptsLeft === 0 && (
          <p className="text-caption text-text-muted">{t("notes.lastAttempt")}</p>
        )}
        {!isOnSale && card.state === "EXHAUSTED" && (
          <p className="text-caption text-text-muted">
            {canViewResult || canViewHistory
              ? t("notes.discontinuedExhaustedWithHistory")
              : t("notes.discontinuedExhausted")}
          </p>
        )}
        {!isOnSale && (card.state === "CAN_START" || card.state === "IN_PROGRESS") && (
          <p className="text-caption text-text-muted">{t("notes.discontinuedCanStart")}</p>
        )}

        {(canViewResult || canViewHistory) && (
          <div className="flex flex-wrap gap-2">
            {canViewResult && (
              <Button asChild variant="soft" className="h-11 flex-1">
                <Link href={`/attempts/${encodeURIComponent(card.resultAttemptId!)}/result`} prefetch={false}>
                  {t("actions.viewResult")}
                </Link>
              </Button>
            )}
            {canViewHistory && (
              <Button asChild variant="soft" className="h-11 flex-1">
                <Link href={`/history?examId=${encodeURIComponent(card.code)}`} prefetch={false}>
                  {t("actions.history")}
                </Link>
              </Button>
            )}
          </div>
        )}

        {card.hasPendingFulfillment && card.pendingOrderCode && (
          <p className="text-caption text-warning">
            {t("notes.pendingTopUp", { orderCode: card.pendingOrderCode })}
          </p>
        )}

        {canBuyMore && (
          <Button asChild variant="outline" className="h-11 w-full">
            <Link href={`/checkout/${encodeURIComponent(card.code)}`} prefetch={false}>
              {t("actions.buyMore")}
            </Link>
          </Button>
        )}
      </div>
    </article>
  );
}
