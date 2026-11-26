import { Skeleton } from "@thi360/ui";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { PreviewTrialView } from "@/features/exams/trial/preview-trial-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.trial");
  return { title: `${t("title")} · Thi360` };
}

export default async function ExamPreviewTrialPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string; previewId: string }>;
  searchParams: Promise<{ expiresAt?: string }>;
}) {
  const { id, previewId } = await params;
  const { expiresAt } = await searchParams;
  return (
    <Suspense fallback={<Skeleton className="h-96" />}>
      <PreviewTrialView
        key={`${id}:${previewId}`}
        examId={id}
        previewId={previewId}
        initialExpiresAt={expiresAt ?? null}
      />
    </Suspense>
  );
}
