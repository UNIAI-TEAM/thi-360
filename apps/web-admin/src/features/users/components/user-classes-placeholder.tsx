"use client";

import { Card, EmptyState } from "@thi360/ui";
import { GraduationCap } from "lucide-react";
import { useTranslations } from "next-intl";

export interface UserClassesPlaceholderProps {
  userId: string;
}

/** Chỗ chờ cho module Lớp học (T10) gắn danh sách lớp học của tài khoản. */
export function UserClassesPlaceholder({ userId }: UserClassesPlaceholderProps) {
  const t = useTranslations("users.classesPlaceholder");
  return (
    <Card data-slot="user-classes-placeholder" data-user-id={userId}>
      <EmptyState icon={<GraduationCap />} title={t("title")} description={t("description")} />
    </Card>
  );
}
