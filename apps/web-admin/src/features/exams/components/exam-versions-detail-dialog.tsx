"use client";

import { Badge, Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, Skeleton } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { sortedParts } from "../check";
import { useExamVersion } from "../review-queries";
import type { ExamPartOption, ExamSection } from "../types";
import { pathLabelCodes, useChoiceNameResolver, useLookup } from "./exam-review-shared";

/** Xem cấu hình của một phiên bản đã công bố, chỉ đọc (bản chụp lúc công bố). */
export function ExamVersionDetailDialog({
  examId,
  version,
  onOpenChange,
}: {
  examId: string;
  version: number | null;
  onOpenChange: (open: boolean) => void;
}) {
  const t = useTranslations("exams.versions.detail");
  return (
    <Dialog open={version !== null} onOpenChange={onOpenChange}>
      <DialogContent size="lg" closeLabel={t("close")} className="flex max-h-[90vh] flex-col overflow-hidden">
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("title", { version: version ?? 0 })}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        {version !== null && <DetailBody examId={examId} version={version} />}
      </DialogContent>
    </Dialog>
  );
}

function DetailBody({ examId, version }: { examId: string; version: number }) {
  const t = useTranslations("exams.versions");
  const format = useFormatter();
  const nameOf = useChoiceNameResolver();
  const query = useExamVersion(examId, version);

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-20" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (query.isError) {
    return <p className="text-body text-danger">{t("detail.error")}</p>;
  }
  const { row, parts } = query.data;
  const optionName = (option: ExamPartOption) =>
    option.kind === "BRANCH"
      ? (option.name ?? option.code)
      : option.kind === "COMBINATION"
        ? `${option.code} · ${option.name ?? nameOf(option.kind, option.code)}`
        : option.kind === "SUBJECT_CHOICE"
          ? (option.name ?? t("detail.subjectChoice"))
          : nameOf(option.kind, option.code);
  return (
    <div className="-mx-1 flex min-h-0 flex-col gap-4 overflow-y-auto px-1 scroll-shadows-y">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-lg bg-subtle px-3.5 py-3 text-body">
        <dt className="text-text-muted">{t("columns.publishedAt")}</dt>
        <dd className="m-0 text-text">{row.publishedAt ? format.dateTime(new Date(row.publishedAt), "dateTime") : "—"}</dd>
        <dt className="text-text-muted">{t("columns.publishedBy")}</dt>
        <dd className="m-0 text-text">{row.publishedBy?.name ?? "—"}</dd>
        <dt className="text-text-muted">{t("columns.paths")}</dt>
        <dd className="m-0 text-text">{pathLabelCodes(row.publishedPaths, t("singlePath"))}</dd>
        <dt className="text-text-muted">{t("columns.attempts")}</dt>
        <dd className="m-0 text-text tabular-nums">{format.number(row.attemptCount)}</dd>
        {row.note && (
          <>
            <dt className="text-text-muted">{t("detail.note")}</dt>
            <dd className="m-0 text-text">{row.note}</dd>
          </>
        )}
      </dl>
      <ol className="flex flex-col gap-3">
        {sortedParts(parts).map((part) => (
          <li key={part.id} className="flex flex-col gap-2 rounded-lg border border-border p-3">
            <h3 className="font-semibold text-text">
              {part.name}{" "}
              <span className="font-normal text-text-muted">
                ·{" "}
                {t("detail.partMeta", {
                  count: part.questionCount,
                  minutes: part.durationMinutes ?? 0,
                  hasMinutes: part.durationMinutes === null ? "no" : "yes",
                  points: part.maxPoints ?? 0,
                  hasPoints: part.maxPoints === null || part.maxPoints === undefined ? "no" : "yes",
                })}
              </span>
            </h3>
            {part.mode === "FIXED" ? (
              <SectionList sections={part.sections} />
            ) : (
              <ul className="flex flex-col gap-2">
                {part.sections.length > 0 && (
                  <li className="flex flex-col gap-1">
                    <span className="text-label font-semibold text-text-secondary">{t("detail.common")}</span>
                    <SectionList sections={part.sections} />
                  </li>
                )}
                {part.options.map((option) => (
                  <li key={option.id} className="flex flex-col gap-1">
                    <span className="text-label font-semibold text-text-secondary">{optionName(option)}</span>
                    <SectionList sections={option.sections} />
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}

function SectionList({ sections }: { sections: ExamSection[] }) {
  const t = useTranslations("exams.versions.detail");
  const types = useTranslations("content.questionType");
  const lookup = useLookup();
  return (
    <ul className="flex flex-col gap-1.5 ps-3">
      {sections.map((section) => (
        <li key={section.id} className="flex flex-col gap-1">
          <span className="text-body text-text">
            {t("section", { subject: lookup.subject(section.subjectCode), count: section.questionCount ?? 0 })}
          </span>
          <ul className="flex flex-wrap gap-1.5">
            {section.blocks.map((block) => (
              <li key={block.id}>
                <Badge>
                  {t("block", {
                    name: block.name,
                    type: block.questionType ? types(block.questionType) : "",
                    hasType: block.questionType ? "yes" : "no",
                    count: block.questionCount ?? 0,
                    mode: block.matrix?.mode ?? "COMBINED",
                    manual: block.questionType === "ESSAY" ? "yes" : "no",
                  })}
                </Badge>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}
