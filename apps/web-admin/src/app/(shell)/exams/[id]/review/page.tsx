import { Skeleton } from "@thi360/ui";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ExamReviewView } from "@/features/exams/components/exam-review-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.review");
  return { title: `${t("title")} · Thi 360` };
}

/** Kiểm tra cấu hình và Đề xem trước (bản vẽ BaiThiKiemTra, BaiThiXemTruoc). */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  // Trang đọc tab, đường lựa chọn, phiên bản từ URL (useSearchParams) nên cần ranh giới Suspense.
  return (
    <Suspense fallback={<Skeleton className="h-96" />}>
      <ExamReviewView examId={id} />
    </Suspense>
  );
}
