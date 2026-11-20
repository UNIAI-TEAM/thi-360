"use client";

import { Button, Card, EmptyState, NavTab, NavTabs, Skeleton, StatusChip } from "@thi360/ui";
import { ArrowLeft, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { getErrorStatus } from "@/lib/api/query-client";
import { EXAM_EDITOR_TABS, examTabFromPathname, examTabHref } from "../editor-tabs";
import { useExam } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail } from "../types";
import { ExamEditorActionsSlot } from "./exam-editor-actions";

/**
 * Khung chung của trang soạn bài thi `/exams/[id]/...`: tiêu đề, chip trạng thái, dải tóm tắt (`<dl>`), thanh
 * NavTabs bốn trang con (Thông tin chung · Cấu trúc và ma trận · Kiểm tra và xem trước · Phiên bản và công bố).
 * Trang con đọc bài thi bằng `useExam(id)` (cùng khóa truy vấn, không gọi lại).
 */
export function ExamEditorLayout({ examId, children }: { examId: string; children: ReactNode }) {
  const t = useTranslations("exams");
  const pathname = usePathname();
  const activeTab = examTabFromPathname(pathname);
  const examQuery = useExam(examId);
  const [actionsSlot, setActionsSlot] = useState<HTMLElement | null>(null);

  if (examQuery.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-80 max-w-full" />
        <Skeleton className="h-16" />
        <Skeleton className="h-10 w-full max-w-xl" />
        <Skeleton className="h-96" />
      </div>
    );
  }

  if (examQuery.isError) {
    const notFound = getErrorStatus(examQuery.error) === 404;
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={notFound ? t("editor.notFoundTitle") : t("editor.errorTitle")}
          description={notFound ? t("editor.notFoundDescription") : t("editor.errorDescription")}
          action={
            notFound ? (
              <Button asChild variant="secondary">
                <Link href="/exams">{t("editor.backToList")}</Link>
              </Button>
            ) : (
              <Button variant="secondary" onClick={() => void examQuery.refetch()}>
                {t("editor.retry")}
              </Button>
            )
          }
        />
      </Card>
    );
  }

  const exam = examQuery.data;
  return (
    <div className="flex flex-col gap-5">
      <ExamEditorHeader exam={exam} actionsRef={setActionsSlot} />
      <NavTabs aria-label={t("editor.tabsLabel")}>
        {EXAM_EDITOR_TABS.map((tab) => (
          <NavTab key={tab} asChild current={tab === activeTab}>
            <Link href={examTabHref(exam.id, tab)}>{t(`editor.tabs.${tab}`)}</Link>
          </NavTab>
        ))}
      </NavTabs>
      <ExamEditorActionsSlot.Provider value={actionsSlot}>
        <div key={activeTab}>{children}</div>
      </ExamEditorActionsSlot.Provider>
    </div>
  );
}

function ExamEditorHeader({ exam, actionsRef }: { exam: ExamDetail; actionsRef: (node: HTMLDivElement | null) => void }) {
  const t = useTranslations("exams");
  const format = useFormatter();
  return (
    <header className="flex flex-col gap-3">
      <Link
        href="/exams"
        className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label font-medium text-text-secondary hover:text-text"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("editor.backToList")}
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
          <h1 data-exam-content className="min-w-0 text-page-title font-bold wrap-break-word text-text">{exam.name}</h1>
          <StatusChip tone={EXAM_STATUS_TONE[exam.status]}>
            {exam.status === "PUBLISHED" && exam.servingVersion !== null
              ? t("status.servingVersion", { version: exam.servingVersion })
              : t(`status.${exam.status}`)}
          </StatusChip>
          {exam.hasUnpublishedChanges && <StatusChip tone="warning">{t("status.unpublishedChanges")}</StatusChip>}
          <StatusChip tone={EXAM_CHECK_TONE[exam.check.status]}>
            {exam.check.status === "INVALID"
              ? t("checkStatus.issues", { count: exam.check.issueCount })
              : t(`checkStatus.${exam.check.status}`)}
          </StatusChip>
        </div>
        <div ref={actionsRef} className="flex flex-wrap items-center justify-end gap-2 empty:hidden" />
      </div>
      <dl className="flex flex-wrap gap-x-6 gap-y-2 text-body">
        <SummaryItem label={t("summary.code")} value={exam.code} />
        <SummaryItem label={t("summary.kind")} value={t(`kind.${exam.kind}`)} />
        <SummaryItem label={t("summary.format")} value={t(`scope.${exam.scope}`)} />
        <SummaryItem label={t("summary.questions")} value={format.number(exam.totalQuestions)} />
        <SummaryItem label={t("summary.duration")} value={t("summary.minutes", { count: exam.durationMinutes })} />
        <SummaryItem label={t("summary.attempts")} value={format.number(exam.attemptCount)} />
      </dl>
    </header>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className="m-0 font-semibold text-text">{value}</dd>
    </div>
  );
}
