import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { redirect } from "next/navigation";

import { ExamEditorLayout } from "@/features/exams/components/exam-editor-layout";
import { ExamGeneralForm } from "@/features/exams/components/exam-general-form";
import { PermissionGate } from "@/features/session/permission-gate";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.create");
  return { title: `${t("title")} · Thi 360` };
}

/**
 * Tạo bài thi mới (`/exams/new`): dùng chung `ExamEditorLayout` (5 tab, 4 tab sau khóa) và `ExamGeneralForm` (`mode="create"`).
 * Tham số truy vấn cũ `exam=<id>` chuyển hướng thẳng trên máy chủ về `/exams/<id>` trước `PermissionGate` (`S1-3`, `AC-05`).
 */
export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<{ exam?: string | string[] }>;
}) {
  const resolved = searchParams ? await searchParams : undefined;
  const rawExam = Array.isArray(resolved?.exam) ? resolved.exam[0] : resolved?.exam;
  const exam = rawExam?.trim();
  if (exam) {
    redirect(`/exams/${encodeURIComponent(exam)}`);
  }

  return (
    <PermissionGate permission="EXAM.MODIFY">
      <ExamEditorLayout>
        <ExamGeneralForm mode="create" />
      </ExamEditorLayout>
    </PermissionGate>
  );
}
