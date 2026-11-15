"use client";

import { Button, buttonVariants, Card, cn, focusRing } from "@thi360/ui";
import { ArrowRight, Check, CircleX, Copy, Download, Upload } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { type RefObject, useId } from "react";
import type { ContentReference } from "@/features/content/types";
import type { ImportHistoryEntry } from "../types";
import { useErrorReportDownload } from "./use-issue-text";

/** Danh sách câu Nháp của ngân hàng, nơi các câu vừa nhập hiện ra. */
export const DRAFT_QUESTIONS_HREF = "/questions?status=DRAFT";

/** `<progress>` gốc tô bằng token, cùng cách với màn cấp tài khoản hàng loạt (mỗi phần tử giả một quy tắc riêng). */
const PROGRESS_BAR_CLASS = [
  "block h-2.5 w-full appearance-none overflow-clip rounded-full border-0 bg-subtle",
  "[&::-webkit-progress-bar]:bg-transparent",
  "[&::-webkit-progress-value]:rounded-full [&::-webkit-progress-value]:bg-primary-fill",
  "[&::-moz-progress-bar]:rounded-full [&::-moz-progress-bar]:bg-primary-fill",
].join(" ");

export interface ResultStepProps {
  result: ImportHistoryEntry;
  reference: ContentReference | undefined;
  headingRef: RefObject<HTMLHeadingElement | null>;
  onRestart: () => void;
  onAnnounce: (message: string) => void;
}

/** Bước 4: kết quả nhập (bản vẽ `NhapCauHoiKetQua`). */
export function ResultStep({ result, reference, headingRef, onRestart, onAnnounce }: ResultStepProps) {
  const t = useTranslations("questionImport");
  const format = useFormatter();
  const progressId = useId();
  const downloadErrors = useErrorReportDownload(reference);
  const imported = result.createdCount + result.newVersionCount;
  const total = imported + result.skippedDuplicateCount;

  return (
    <Card padding="lg" className="flex flex-col gap-5">
      <div className="flex items-start gap-3">
        <span aria-hidden className="flex size-11 shrink-0 items-center justify-center rounded-full bg-success-bg text-success">
          <Check className="size-6" strokeWidth={2.6} />
        </span>
        <div className="min-w-0">
          <h2 ref={headingRef} tabIndex={-1} className="text-h3 font-semibold text-text outline-hidden">
            {t("result.heading", { count: imported })}
          </h2>
          <p className="text-caption text-text-muted">
            {t("result.meta", {
              user: result.importedBy.name,
              time: format.dateTime(new Date(result.importedAt), "dateTime"),
              seconds: Math.max(1, Math.round(result.durationMs / 1000)),
            })}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={progressId} className="flex justify-between text-caption text-text-secondary">
          <span>{t("result.progressLabel")}</span>
          <span className="tabular-nums">{t("result.progressValue", { done: total, total })}</span>
        </label>
        <progress
          id={progressId}
          max={Math.max(1, total)}
          value={Math.max(1, total)}
          className={PROGRESS_BAR_CLASS}
        />
      </div>

      <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(14rem,100%),1fr))] gap-3">
        <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
          <dt className="flex items-center gap-1.5 text-caption font-semibold text-success">
            <Check aria-hidden className="size-4" />
            {t("result.created")}
          </dt>
          <dd className="flex flex-col">
            <span className="text-h3 font-semibold text-text tabular-nums">
              {t("result.createdValue", { count: result.createdCount })}
            </span>
            <span className="text-caption text-text-muted">
              {t("result.createdDetail", {
                singles: result.singleCount,
                sets: result.setCount,
                children: result.setChildCount,
              })}
            </span>
          </dd>
        </div>
        {result.newVersionCount > 0 ? (
          <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
            <dt className="flex items-center gap-1.5 text-caption font-semibold text-warning">
              <Copy aria-hidden className="size-4" />
              {t("result.versioned")}
            </dt>
            <dd className="flex flex-col">
              <span className="text-h3 font-semibold text-text tabular-nums">
                {t("result.versionedValue", { count: result.newVersionCount })}
              </span>
              <span className="text-caption text-text-muted">{t("result.versionedDetail")}</span>
            </dd>
          </div>
        ) : (
          <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
            <dt className="flex items-center gap-1.5 text-caption font-semibold text-warning">
              <Copy aria-hidden className="size-4" />
              {t("result.skipped")}
            </dt>
            <dd className="flex flex-col">
              <span className="text-h3 font-semibold text-text tabular-nums">
                {t("result.skippedValue", { count: result.skippedDuplicateCount })}
              </span>
              <span className="text-caption text-text-muted">{t("result.skippedDetail")}</span>
            </dd>
          </div>
        )}
        <div className="flex flex-col gap-1 rounded-lg border border-border p-4">
          <dt className="flex items-center gap-1.5 text-caption font-semibold text-danger">
            <CircleX aria-hidden className="size-4" />
            {t("result.errors")}
          </dt>
          <dd className="flex flex-col">
            <span className="text-h3 font-semibold text-text tabular-nums">
              {t("result.errorsValue", { count: result.errorRowCount })}
            </span>
            <span className="text-caption text-text-muted">{t("result.errorsDetail")}</span>
          </dd>
        </div>
      </dl>

      <section className="flex flex-col gap-2 rounded-lg bg-subtle p-4">
        <h3 className="text-label font-semibold text-text">{t("result.nextHeading")}</h3>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-body text-text-secondary">
          {result.missingKnowledgeCount > 0 && (
            <li>
              {t("result.missingKnowledge", { count: result.missingKnowledgeCount })}{" "}
              <Link href={DRAFT_QUESTIONS_HREF} className={cn("rounded-sm font-semibold text-primary-text hover:underline", focusRing)}>
                {t("result.viewDrafts")}
              </Link>
            </li>
          )}
          <li>{t("result.notForExam")}</li>
        </ul>
      </section>

      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onRestart}>
          <Upload />
          <span>{t("result.importAnother")}</span>
        </Button>
        {result.errors.length > 0 && (
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              downloadErrors(result.errors, result.fileName);
              onAnnounce(t("live.errorsDownloaded"));
            }}
          >
            <Download />
            <span>{t("result.downloadErrors")}</span>
          </Button>
        )}
        <Link href={DRAFT_QUESTIONS_HREF} className={buttonVariants({ variant: "primary" })}>
          <span>{t("result.viewImported")}</span>
          <ArrowRight aria-hidden />
        </Link>
      </div>
    </Card>
  );
}
