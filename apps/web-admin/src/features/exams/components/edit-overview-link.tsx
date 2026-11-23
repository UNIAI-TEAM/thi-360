"use client";

import { Button } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { examTabHref } from "../editor-tabs";

/**
 * Đường dẫn về thông tin tổng quan đặt cạnh ô số câu chỉ đọc của bước 2: số câu của phần, mục và bài ôn chỉ sửa ở đó
 * (UNI-2753). Cùng đích và cùng chữ với nút ở đầu màn Cấu trúc.
 */
export function EditOverviewLink({ examId, className }: { examId: string; className?: string }) {
  const t = useTranslations("exams.structure.summary");
  return (
    <Button asChild variant="link" size="sm" className={className}>
      <Link href={examTabHref(examId, "general")}>{t("editOverview")}</Link>
    </Button>
  );
}
