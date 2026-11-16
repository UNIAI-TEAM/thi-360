"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  EmptyState,
  ListCard,
  ListCardTitleLink,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { FileText, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { EXAM_STATUS_TONE } from "@/features/exams/status";
import type { QuestionUsageExam } from "../editor-types";
import { useQuestionUsage } from "../editor-queries";

function examHref(exam: QuestionUsageExam): string {
  return `/exams/${encodeURIComponent(exam.examId)}`;
}

function usageFormatters(t: ReturnType<typeof useTranslations>, subjectName: (code: string) => string) {
  return {
    attemptsText: (exam: QuestionUsageExam) => {
      const used = exam.attemptsByVersion.filter((entry) => entry.attemptCount > 0);
      if (used.length === 0) return t("noAttempts");
      return used.map((entry) => t("versionAttempts", { version: entry.version, count: entry.attemptCount })).join(" · ");
    },
    location: (exam: QuestionUsageExam) =>
      [exam.partName, subjectName(exam.subjectCode), exam.blockName].filter(Boolean).join(" › "),
  };
}

/**
 * Trang con Nơi đang dùng (canvas `CauHoiPhienBan`, tab "Nơi đang dùng"): bài thi có khối câu hỏi lấy được câu
 * này, kèm số lượt thi đã dùng từng phiên bản. Bảng thành danh sách thẻ khi khung hẹp hơn 640px.
 */
export function QuestionUsageView({ questionId }: { questionId: string }) {
  const t = useTranslations("questions.usage");
  const tExamStatus = useTranslations("exams.status");
  const usageQuery = useQuestionUsage(questionId);
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  const usage = usageQuery.data;

  const columns = useMemo<ColumnDef<QuestionUsageExam>[]>(() => {
    const { attemptsText, location } = usageFormatters(t, lookup.subject);
    return [
      {
        id: "exam",
        header: t("columns.exam"),
        cell: ({ row }) => (
          <div className="flex min-w-56 flex-col">
            <Link href={examHref(row.original)} className="rounded-sm font-semibold text-primary-text hover:underline">
              {row.original.examName}
            </Link>
            <span className="text-caption text-text-muted">{row.original.examCode}</span>
          </div>
        ),
      },
      {
        id: "status",
        header: t("columns.status"),
        cell: ({ row }) => (
          <StatusChip tone={EXAM_STATUS_TONE[row.original.examStatus]}>
            {tExamStatus(row.original.examStatus)}
          </StatusChip>
        ),
      },
      {
        id: "location",
        header: t("columns.location"),
        cell: ({ row }) => <span className="block min-w-48 whitespace-normal">{location(row.original)}</span>,
      },
      {
        id: "attempts",
        header: t("columns.attempts"),
        cell: ({ row }) => <span className="block min-w-36 whitespace-normal">{attemptsText(row.original)}</span>,
      },
    ];
  }, [t, tExamStatus, lookup]);

  const cards = useMemo<TableCards<QuestionUsageExam>>(() => {
    const { attemptsText, location } = usageFormatters(t, lookup.subject);
    return {
      render: (exam) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={examHref(exam)}>
              {exam.examName}
            </ListCardTitleLink>
          }
          caption={exam.examCode}
          status={<StatusChip tone={EXAM_STATUS_TONE[exam.examStatus]}>{tExamStatus(exam.examStatus)}</StatusChip>}
          meta={[
            { label: t("columns.location"), value: location(exam) },
            { label: t("columns.attempts"), value: attemptsText(exam) },
          ]}
        />
      ),
    };
  }, [t, tExamStatus, lookup]);

  return (
    <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
      <section aria-labelledby="question-usage-title" className="flex flex-col gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="question-usage-title" className="text-h3 font-semibold text-text">
            {t("title")}
          </h2>
          <p className="text-body text-text-muted">{t("description")}</p>
        </div>
        {usage && usage.examCount > 0 && (
          <p className="text-body font-medium text-text">
            {t("summary", { exams: usage.examCount, attempts: usage.attemptCount })}
          </p>
        )}
        <Table
          aria-label={t("tableLabel")}
          columns={columns}
          data={usage?.exams ?? []}
          getRowId={(exam) => exam.examId}
          loading={usageQuery.isPending}
          loadingRows={3}
          cards={cards}
          error={
            usageQuery.isError ? (
              <EmptyState
                icon={<TriangleAlert />}
                headingLevel={3}
                title={t("errorTitle")}
                description={t("errorDescription")}
                action={
                  <Button variant="secondary" onClick={() => void usageQuery.refetch()}>
                    {t("retry")}
                  </Button>
                }
              />
            ) : undefined
          }
          empty={
            <EmptyState icon={<FileText />} headingLevel={3} title={t("emptyTitle")} description={t("emptyDescription")} />
          }
        />
      </section>
    </Card>
  );
}
