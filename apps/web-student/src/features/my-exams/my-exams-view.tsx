"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, EmptyState, SectionTitle, Skeleton } from "@thi360/ui";
import { APP_TIME_ZONE } from "@thi360/web-kit/preferences";
import { Compass, FileText, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import type { StudentSession } from "@/features/session/api";
import { loadMyExams, MY_EXAMS_QUERY_KEY, type MyExamsLoader } from "./api";
import { CheckingExamCard, MyExamCard } from "./my-exam-card";

function formatTimeHm(timestampMs: number, locale: string): string {
  const intlLocale = locale === "en" ? "en-GB" : "vi-VN";
  return new Intl.DateTimeFormat(intlLocale, {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: APP_TIME_ZONE,
  }).format(new Date(timestampMs));
}

export function MyExamsView({
  session,
  loadExams = loadMyExams,
}: {
  session: StudentSession;
  loadExams?: MyExamsLoader;
}) {
  const t = useTranslations("home");
  const common = useTranslations("common");
  const locale = useLocale();

  const examsQuery = useQuery({
    queryKey: MY_EXAMS_QUERY_KEY,
    queryFn: loadExams,
  });

  const displayName = session.fullName.trim() || session.username;
  const items = examsQuery.data?.items;
  const checkingCards = items?.filter((item) => item.state === "CHECKING") ?? [];
  const ownedCards = items?.filter((item) => item.state !== "CHECKING") ?? [];

  return (
    <div className="flex flex-col gap-7">
      <div className="flex flex-col gap-1.5">
        <h1 className="text-h1 font-bold text-text">{t("pageTitle")}</h1>
        <p className="text-body text-text-muted">{t("subtitle", { name: displayName })}</p>
      </div>

      {examsQuery.isLoading && !examsQuery.data && (
        <div
          role="status"
          aria-busy="true"
          aria-label={common("loading")}
          className="grid grid-cols-[repeat(auto-fit,minmax(min(20rem,100%),1fr))] gap-5"
        >
          {[0, 1, 2].map((index) => (
            <div
              key={index}
              className="flex h-64 flex-col gap-3 rounded-xl border border-border bg-surface p-5"
            >
              <div className="flex gap-2">
                <Skeleton className="h-6 w-14 rounded-full" />
                <Skeleton className="h-6 w-16 rounded-full" />
              </div>
              <Skeleton className="h-5 w-4/5" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-11 w-full rounded-lg" />
              <Skeleton className="mt-auto h-11 w-full rounded-lg" />
            </div>
          ))}
        </div>
      )}

      {examsQuery.isError && !examsQuery.data && (
        <div
          role="alert"
          className="flex flex-col items-center gap-3 rounded-xl border border-border bg-surface px-6 py-12 text-center"
        >
          <span
            aria-hidden
            className="grid size-12 place-items-center rounded-xl bg-danger-bg text-danger [&_svg]:size-6"
          >
            <TriangleAlert />
          </span>
          <h2 className="text-title font-semibold text-text">{t("errorTitle")}</h2>
          <p className="max-w-sm text-body text-text-muted">{t("errorDescription")}</p>
          <Button type="button" variant="secondary" onClick={() => void examsQuery.refetch()}>
            {common("retry")}
          </Button>
        </div>
      )}

      {examsQuery.isError && examsQuery.data && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3.5"
        >
          <div className="flex min-w-0 flex-1 basis-72 items-start gap-2.5 text-warning">
            <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
            <p className="text-body">
              <strong className="font-semibold">{t("staleTitle")}</strong>{" "}
              <span className="text-text-secondary">
                {examsQuery.dataUpdatedAt > 0
                  ? t("staleDescription", { time: formatTimeHm(examsQuery.dataUpdatedAt, locale) })
                  : t("staleDescriptionNoTime")}
              </span>
            </p>
          </div>
          <Button
            type="button"
            variant="secondary"
            loading={examsQuery.isFetching}
            onClick={() => void examsQuery.refetch()}
          >
            {common("retry")}
          </Button>
        </div>
      )}

      {items && items.length === 0 && (
        <div className="rounded-xl border border-border bg-surface">
          <EmptyState
            icon={<FileText />}
            title={t("emptyTitle")}
            description={t("emptyDescription")}
            action={
              <Button asChild className="h-11">
                <Link href="/#exams">{t("exploreExams")}</Link>
              </Button>
            }
          />
        </div>
      )}

      {items && items.length > 0 && (
        <section aria-label={t("sectionTitle")} className="flex flex-col gap-5">
          <SectionTitle
            title={t("sectionTitle")}
            count={t("examCount", { count: items.length })}
            className="flex-wrap"
            action={
              <Button asChild variant="outline" className="h-11">
                <Link href="/#exams">
                  <Compass aria-hidden />
                  {t("exploreExams")}
                </Link>
              </Button>
            }
          />

          {checkingCards.length > 0 && (
            <div className="flex flex-col gap-3">
              <h3 className="text-overline font-semibold tracking-overline text-warning uppercase">
                {t("checking.sectionTitle")}
              </h3>
              <div className="flex flex-col gap-3">
                {checkingCards.map((card) => (
                  <CheckingExamCard
                    key={card.pendingOrderCode ?? card.code}
                    card={card}
                    onReload={() => void examsQuery.refetch()}
                    reloading={examsQuery.isFetching}
                  />
                ))}
              </div>
            </div>
          )}

          {ownedCards.length > 0 && (
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(20rem,100%),1fr))] gap-5">
              {ownedCards.map((card) => (
                <MyExamCard key={card.entitlementId ?? card.code} card={card} />
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
