"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, notify, StatusPage } from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { loadSession, logoutSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";

export function StudentForbiddenView({
  loadCurrentSession = loadSession,
  navigate = (url: string) => window.location.assign(url),
}: {
  loadCurrentSession?: SessionLoader;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("forbidden");
  const common = useTranslations("common");
  const errors = useTranslations("errors");
  const [leaving, setLeaving] = useState(false);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
    retry: false,
  });

  async function signInAsAnother() {
    setLeaving(true);
    try {
      await logoutSession();
      navigate("/login");
    } catch {
      notify.error(errors("logoutFailed"));
    } finally {
      setLeaving(false);
    }
  }

  const description = sessionQuery.data
    ? t.rich("description", {
        username: sessionQuery.data.username,
        strong: (chunks) => <strong className="font-semibold text-text">{chunks}</strong>,
      })
    : t("descriptionAnonymous");

  const homeHref = sessionQuery.data?.homeUrl ?? "/";

  return (
    <StatusPage
      icon={Lock}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={description}
      actions={
        <>
          <Button asChild size="lg">
            <a href={homeHref}>{common("backHome")}</a>
          </Button>
          <Button variant="secondary" size="lg" loading={leaving} onClick={() => void signInAsAnother()}>
            {t("otherAccount")}
          </Button>
        </>
      }
    />
  );
}
