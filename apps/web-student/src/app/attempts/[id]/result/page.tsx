import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { AttemptResultView } from "@/features/attempt-result/result-view";

type Props = { params: Promise<{ id: string }> };

function decodeParam(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("result");
  return { title: t("pageTitle") };
}

export default async function AttemptResultPage({ params }: Props) {
  const { id } = await params;
  return (
    <Suspense fallback={<div aria-busy="true" className="min-h-screen bg-page" />}>
      <AttemptResultView attemptId={decodeParam(id)} />
    </Suspense>
  );
}
