"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, EmptyState, Skeleton } from "@thi360/ui";
import { ShieldCheck, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import type { MyExamsLoader } from "@/features/my-exams/api";
import { MyExamsView } from "@/features/my-exams/my-exams-view";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { adminUrl } from "@/lib/env";

export function StudentHomeView({
  loadCurrentSession = loadSession,
  loadMyExams,
}: {
  loadCurrentSession?: SessionLoader;
  loadMyExams?: MyExamsLoader;
}) {
  const t = useTranslations("home");
  const common = useTranslations("common");

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1440px] flex-col gap-6 px-4 py-10 sm:px-6">
        {sessionQuery.isLoading && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-6">
            <div className="flex flex-col gap-1.5">
              <Skeleton className="h-9.5 w-64 max-w-full" />
              <Skeleton className="h-5.25 w-80 max-w-full" />
            </div>
            <Skeleton className="h-48 w-full rounded-xl" />
          </div>
        )}

        {sessionQuery.isError && (
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("errorLoadAccountTitle")}
            description={t("errorLoadAccountDescription")}
            action={
              <Button type="button" onClick={() => void sessionQuery.refetch()}>
                {common("retry")}
              </Button>
            }
          />
        )}

        {sessionQuery.data && sessionQuery.data.access === "MANAGE" && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<ShieldCheck />}
              title={t("adminNotice.title")}
              description={t("adminNotice.description")}
              action={
                <Button asChild className="h-11">
                  <a href={adminUrl()}>{t("adminNotice.cta")}</a>
                </Button>
              }
            />
          </div>
        )}

        {sessionQuery.data && sessionQuery.data.access === "EXAM" && (
          <MyExamsView session={sessionQuery.data} loadExams={loadMyExams} />
        )}
      </main>
    </div>
  );
}
