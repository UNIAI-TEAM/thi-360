import { Skeleton } from "@thi360/ui";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { Suspense } from "react";
import { ExamReviewView } from "@/features/exams/components/exam-review-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.review");
  return { title: `${t("title")} · Thi360` };
}

/** Kiểm tra cấu hình và Đề xem trước (bản vẽ BaiThiKiemTra, BaiThiXemTruoc). */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  // Trang đọc tab, đường lựa chọn, phiên bản từ URL (useSearchParams) nên cần ranh giới Suspense.
  return (
    <Suspense fallback={<Skeleton className="h-96" />}>
      <ExamReviewView examId={id} />
    </Suspense>
  );
}
