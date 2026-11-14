"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, EmptyState, SectionTitle, Skeleton } from "@thi360/ui";
import { FileText, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { StudentHeader } from "@/components/student-header";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { StudentUserMenu } from "./student-user-menu";

export function StudentHomeView({
  loadCurrentSession = loadSession,
}: {
  loadCurrentSession?: SessionLoader;
}) {
  const t = useTranslations("home");
  const common = useTranslations("common");

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader actions={sessionQuery.data && <StudentUserMenu session={sessionQuery.data} />} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-6 py-10">
        {sessionQuery.isLoading && (
          <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9 w-64" />
            <Skeleton className="h-5 w-80 max-w-full" />
            <Skeleton className="mt-4 h-48 w-full rounded-xl" />
          </div>
        )}

        {sessionQuery.isError && (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("errorTitle")}
            description={t("errorDescription")}
            action={<Button onClick={() => void sessionQuery.refetch()}>{common("retry")}</Button>}
          />
        )}

        {sessionQuery.data && (
          <>
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h1 font-bold text-text">
                {t("greeting", { name: sessionQuery.data.fullName.trim() || sessionQuery.data.username })}
              </h1>
              <p className="text-body text-text-muted">{t("intro")}</p>
            </div>
            <section aria-label={t("sectionTitle")} className="flex flex-col gap-4">
              <SectionTitle title={t("sectionTitle")} />
              <EmptyState
                icon={<FileText />}
                title={t("emptyTitle")}
                description={t("emptyDescription")}
              />
            </section>
          </>
        )}
      </main>
    </div>
  );
}
