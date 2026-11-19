"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Card, EmptyState, ListCard, StatusChip, Table, type TableCards } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";
import type { QuestionType } from "@/features/content/types";
import type { PreviewSubject, PreviewSubjectQuota } from "../review-types";
import { useLookup } from "./exam-review-shared";

interface QuotaRow {
  key: string;
  subject: PreviewSubject;
  quota: PreviewSubjectQuota;
}

/**
 * Đối chiếu chỉ tiêu cấp môn (B9) với đề đã sinh: mỗi dòng là một chỉ tiêu của một môn (dạng trả lời, khối lớp, độ
 * khó) kèm số câu yêu cầu và số câu đề sinh. Môn của `SUBJECT_CHOICE` chỉ gồm các môn đã chọn, số câu của môn theo B10.
 */
export function ExamPreviewSubjectQuotas({ subjects }: { subjects: readonly PreviewSubject[] }) {
  const t = useTranslations("exams.preview.subjectQuotas");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const lookup = useLookup();
  const rows = useMemo<QuotaRow[]>(
    () =>
      subjects.flatMap((subject) =>
        subject.quotas.map((quota) => ({ key: `${subject.sectionId}:${quota.kind}:${quota.code}`, subject, quota })),
      ),
    [subjects],
  );
  const mismatches = rows.filter(({ quota }) => quota.required !== quota.generated).length;

  const { columns, cards } = useMemo(() => {
    const quotaName = ({ quota }: QuotaRow) => {
      if (quota.kind === "ANSWER_TYPE") {
        return t("kind.ANSWER_TYPE", { name: tContent(`questionType.${quota.code as QuestionType}`) });
      }
      return t(`kind.${quota.kind}`, {
        name: quota.kind === "GRADE" ? lookup.grade(quota.code) : lookup.difficulty(quota.code),
      });
    };
    const subjectName = ({ subject }: QuotaRow) => lookup.subject(subject.subjectCode);
    const subjectCount = ({ subject }: QuotaRow) => t("questionCount", { count: subject.questionCount });
    const chip = ({ quota }: QuotaRow) => (
      <StatusChip tone={quota.required === quota.generated ? "success" : "danger"}>
        {t(quota.required === quota.generated ? "match" : "mismatch")}
      </StatusChip>
    );

    const columns: ColumnDef<QuotaRow>[] = [
      {
        id: "subject",
        header: t("columns.subject"),
        cell: ({ row }) => (
          <span className="flex min-w-32 flex-col">
            <span className="font-semibold text-text">{subjectName(row.original)}</span>
            <span className="text-caption text-text-muted">{subjectCount(row.original)}</span>
          </span>
        ),
      },
      { id: "quota", header: t("columns.quota"), cell: ({ row }) => quotaName(row.original) },
      {
        id: "required",
        header: () => <span className="block text-right">{t("columns.required")}</span>,
        cell: ({ row }) => <span className="block text-right">{format.number(row.original.quota.required)}</span>,
      },
      {
        id: "generated",
        header: () => <span className="block text-right">{t("columns.generated")}</span>,
        cell: ({ row }) => <span className="block text-right">{format.number(row.original.quota.generated)}</span>,
      },
      { id: "result", header: t("columns.result"), cell: ({ row }) => chip(row.original) },
    ];
    const cards: TableCards<QuotaRow> = {
      render: (row) => (
        <ListCard
          title={`${subjectName(row)} · ${quotaName(row)}`}
          caption={subjectCount(row)}
          status={chip(row)}
          meta={[
            { label: t("columns.required"), value: format.number(row.quota.required) },
            { label: t("columns.generated"), value: format.number(row.quota.generated) },
          ]}
        />
      ),
    };
    return { columns, cards };
  }, [t, tContent, format, lookup]);

  return (
    <Card padding="none" className="flex flex-col overflow-hidden">
      <div className="px-4 pt-4 pb-2">
        <h3 className="text-label font-semibold text-text">{t("title")}</h3>
        <p className="text-caption text-text-muted">
          {rows.length === 0 ? t("noneNote") : mismatches ? t("mismatchAll", { count: mismatches }) : t("matchAll")}
        </p>
      </div>
      <Table
        aria-label={t("tableLabel")}
        columns={columns}
        data={rows}
        getRowId={(row) => row.key}
        empty={<EmptyState headingLevel={4} title={t("empty")} />}
        cards={cards}
      />
    </Card>
  );
}
