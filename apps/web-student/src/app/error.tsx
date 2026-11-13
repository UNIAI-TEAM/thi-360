"use client";

import { Button, StatusPage } from "@thi360/ui";
import { ServerCrash } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string; traceId?: string };
  reset: () => void;
}) {
  const t = useTranslations("errorPage");
  const common = useTranslations("common");
  const traceId =
    "traceId" in error && typeof error.traceId === "string" && error.traceId.trim()
      ? error.traceId.trim()
      : error.digest;

  return (
    <StatusPage
      icon={ServerCrash}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={t("description")}
      meta={traceId ? t("traceLabel", { traceId }) : undefined}
      actions={
        <>
          <Button size="lg" onClick={reset}>
            {common("retry")}
          </Button>
          <Button asChild variant="secondary" size="lg">
            <Link href="/">{common("backHome")}</Link>
          </Button>
        </>
      }
    />
  );
}
