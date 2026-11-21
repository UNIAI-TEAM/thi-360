"use client";

import { useQuery } from "@tanstack/react-query";
import { Badge, Button, cn, EmptyState, focusRing, SectionTitle, Skeleton } from "@thi360/ui";
import { APP_TIME_ZONE } from "@thi360/web-kit/preferences";
import { Compass, FileText, Package, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import type { StudentSession } from "@/features/session/api";
import { coarseTextHitArea } from "@/lib/styles";
import { loadMyExams, MY_EXAMS_QUERY_KEY, type MyExamsLoader } from "./api";
import { groupMyExams } from "./group-my-exams";
import { CheckingExamCard, MyExamCard } from "./my-exam-card";

function formatTimeHm(timestampMs: number, locale: string): string {
  const intlLocale = locale === "en" ? "en-GB" : "vi-VN";
  return new Intl.DateTimeFormat(intlLocale, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
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
  const bundleT = useTranslations("bundle");
  const common = useTranslations("common");
  const locale = useLocale();

  const examsQuery = useQuery({
    queryKey: MY_EXAMS_QUERY_KEY(session.id),
    queryFn: loadExams,
  });

  const displayName = session.fullName.trim() || session.username;
  const items = examsQuery.data?.items;
  const checkingCards = items?.filter((item) => item.state === "CHECKING") ?? [];
  const ownedCards = items?.filter((item) => item.state !== "CHECKING") ?? [];
  const { bundleGroups, standaloneCards } = groupMyExams(ownedCards);

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
                    key={`${card.pendingOrderCode ?? "checking"}:${card.code}`}
                    card={card}
                    onReload={() => void examsQuery.refetch()}
                    reloading={examsQuery.isFetching}
                  />
                ))}
              </div>
            </div>
          )}

          {bundleGroups.length > 0 && (
            <div className="flex flex-col gap-5">
              {bundleGroups.map((group) => {
                const headingId = `bundle-group-${group.bundle.bundleId}`;
                const totalInBundle = group.cards.length + group.crossReferences.length;
                return (
                  <section
                    key={group.bundle.bundleId}
                    aria-labelledby={headingId}
                    className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-5"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <span
                          aria-hidden
                          className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-text"
                        >
                          <Package className="size-5" />
                        </span>
                        <div className="flex min-w-0 flex-col gap-0.5">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3
                              id={headingId}
                              tabIndex={-1}
                              data-exam-content
                              className="text-h3 font-bold wrap-break-word text-text focus:outline-hidden"
                            >
                              {group.bundle.name}
                            </h3>
                            <Badge>{t("examCount", { count: totalInBundle })}</Badge>
                          </div>
                          <span data-exam-content className="text-caption wrap-anywhere text-text-muted">
                            {group.bundle.code}
                          </span>
                        </div>
                      </div>
                      <Button asChild variant="link" className="min-h-11 px-1">
                        <Link href={`/exams/${encodeURIComponent(group.bundle.code)}`}>
                          {bundleT("home.viewBundle")}
                        </Link>
                      </Button>
                    </div>

                    {group.cards.length > 0 && (
                      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(20rem,100%),1fr))] gap-5">
                        {group.cards.map((card) => (
                          <MyExamCard key={card.entitlementId ?? card.code} card={card} headingLevel={4} />
                        ))}
                      </div>
                    )}

                    {group.crossReferences.length > 0 && (
                      <ul className="flex flex-col gap-1.5 rounded-lg border border-border bg-page px-4 py-3 text-field-label text-text-secondary">
                        {group.crossReferences.map((ref) => (
                          <li key={ref.examCode}>
                            {bundleT.rich("home.crossReference", {
                              examName: ref.examName,
                              bundleName: ref.primaryBundle.name,
                              examContent: (chunks) => (
                                <span data-exam-content className="font-semibold wrap-break-word text-text">
                                  {chunks}
                                </span>
                              ),
                              bundleLink: (chunks) => (
                                <a
                                  href={`#bundle-group-${ref.primaryBundle.bundleId}`}
                                  data-exam-content
                                  className={cn(
                                    "rounded-sm font-semibold wrap-break-word text-primary-text underline-offset-2 hover:underline",
                                    focusRing,
                                    coarseTextHitArea,
                                  )}
                                >
                                  {chunks}
                                </a>
                              ),
                            })}
                          </li>
                        ))}
                      </ul>
                    )}

                    <p className="text-caption text-text-muted">{bundleT("home.groupFootnote")}</p>
                  </section>
                );
              })}
            </div>
          )}

          {standaloneCards.length > 0 && (
            <div className="flex flex-col gap-4">
              {bundleGroups.length > 0 && (
                <SectionTitle
                  title={bundleT("home.standaloneHeading")}
                  count={t("examCount", { count: standaloneCards.length })}
                  className="flex-wrap"
                />
              )}
              <div className="grid grid-cols-[repeat(auto-fit,minmax(min(20rem,100%),1fr))] gap-5">
                {standaloneCards.map((card) => (
                  <MyExamCard key={card.entitlementId ?? card.code} card={card} />
                ))}
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
