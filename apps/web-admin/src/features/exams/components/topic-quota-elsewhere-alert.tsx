"use client";

import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { MAX_TOPIC_QUOTAS } from "../allocation";
import type { TopicQuotaServerError } from "./topic-quota-table";

/**
 * Lỗi 422 của chỉ tiêu chủ đề (H20) ở panel không có bảng chỉ tiêu chủ đề (tổng quan bài, phần, bài ôn): máy chủ không trả
 * vị trí nên chỉ nói lỗi gì và dẫn người dùng mở môn hoặc nhóm có chỉ tiêu. Mất khi bản nháp đổi (xem `topicError` ở màn).
 */
export function TopicQuotaElsewhereAlert({ error }: { error: TopicQuotaServerError | undefined }) {
  const t = useTranslations("exams.topicQuota");
  if (!error) return null;
  return (
    <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
      <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
      {error === "MAX_TOPIC_QUOTAS" ? t("elsewhereMax", { max: MAX_TOPIC_QUOTAS }) : t("elsewherePercent")}
    </p>
  );
}
