"use client";

import { Button, StatusPage } from "@thi360/ui";
import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

export default function NotFound() {
  const t = useTranslations("notFound");
  const common = useTranslations("common");
  return (
    <StatusPage
      icon={SearchX}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={t("description")}
      actions={
        <Button asChild size="lg">
          <Link href="/">{common("backHome")}</Link>
        </Button>
      }
    />
  );
}
