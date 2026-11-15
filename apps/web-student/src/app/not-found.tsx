import type { Metadata } from "next";
import { Button, StatusPage } from "@thi360/ui";
import { SearchX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import Link from "next/link";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("notFound");
  const metadata = await getTranslations("metadata");
  // `not-found.tsx` cùng đoạn route với layout gốc nên mẫu tiêu đề của layout không áp dụng: ghép trực tiếp.
  return { title: metadata("titleTemplate", { title: t("title") }) };
}

export default async function NotFound() {
  const t = await getTranslations("notFound");
  const common = await getTranslations("common");
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
