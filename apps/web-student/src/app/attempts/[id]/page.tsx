import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AttemptPlayerPageClient } from "@/features/attempt-player";

type Props = { params: Promise<{ id: string }> };

function decodeAttemptId(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("attempt.player");
  return { title: t("pageTitle") };
}

export default async function AttemptPlayerPage({ params }: Props) {
  const { id } = await params;
  return <AttemptPlayerPageClient attemptId={decodeAttemptId(id)} />;
}
