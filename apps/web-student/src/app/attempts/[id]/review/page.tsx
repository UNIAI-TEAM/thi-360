import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { AttemptReviewView } from "@/features/attempt-review/review-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("review");
  return {
    title: t("pageTitle"),
  };
}

export default async function AttemptReviewPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense>
      <AttemptReviewView attemptId={id} />
    </Suspense>
  );
}
