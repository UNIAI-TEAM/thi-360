"use client";

import { Card, cn, focusRing } from "@thi360/ui";
import { CircleCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, type ReactNode } from "react";
import type { CreateExamSummary } from "../create-exam-schema";

export interface CreateExamIssue {
  /** Đường dẫn trường (`parts.2.combinations.0.subjectCounts`), khớp `data-field` của ô nhập. */
  path: string;
  field: string;
  message: string;
}

/** Đưa con trỏ tới ô nhập của một lỗi: tìm khung có `data-field`, focus phần tử nhập đầu tiên bên trong. */
export function focusField(path: string): void {
  const container = document.querySelector(`[data-field="${CSS.escape(path)}"]`);
  if (!container) return;
  const target = container.matches("input,button,select,textarea")
    ? container
    : container.querySelector("input:not([type=hidden]),button,select,textarea");
  container.scrollIntoView?.({ block: "center", behavior: "smooth" });
  if (target instanceof HTMLElement) target.focus({ preventScroll: true });
}

/**
 * Tóm tắt luôn hiển thị (bản vẽ TaoBaiThi; UniNote PBI-04 mục 3): tổng số câu, tổng thời gian, số phần, số đường lựa
 * chọn và các mục cần xử lý, mỗi mục đưa tới ô nhập tương ứng. Màn rộng dính bên phải; màn hẹp nằm dưới biểu mẫu.
 */
export function CreateExamSummaryPanel({
  summary,
  pathText,
  issues,
  actions,
}: {
  summary: CreateExamSummary;
  pathText: string;
  issues: readonly CreateExamIssue[];
  actions: ReactNode;
}) {
  const t = useTranslations("exams.create.summary");
  const tParts = useTranslations("exams.create.parts");
  const headingId = useId();
  const issuesHeadingId = useId();

  return (
    <aside aria-labelledby={headingId} className="@4xl:sticky @4xl:top-4">
      <Card padding="md" className="flex flex-col gap-4">
        <h2 id={headingId} className="text-title font-semibold text-text">
          {t("title")}
        </h2>
        <dl className="grid grid-cols-2 gap-3 text-body">
          <SummaryItem label={t("totalQuestions")} value={tParts("questionsValue", { count: summary.totalQuestions })} />
          <SummaryItem label={t("totalMinutes")} value={t("minutesValue", { count: summary.totalMinutes })} />
          <SummaryItem label={t("partCount")} value={String(summary.partCount)} />
          <SummaryItem label={t("pathCount")} value={String(summary.pathCount)} detail={pathText} />
        </dl>

        <section aria-labelledby={issuesHeadingId} className="flex flex-col gap-2 border-t border-border pt-3">
          <h3 id={issuesHeadingId} className="flex items-center gap-2 text-body font-semibold text-text">
            {t("issuesTitle")}
            <span
              className={cn(
                "inline-flex h-5.5 min-w-5.5 items-center justify-center rounded-full px-1.5 text-caption font-semibold tabular-nums",
                issues.length > 0 ? "bg-danger-bg text-danger" : "bg-primary-50 text-primary-text",
              )}
            >
              {issues.length}
            </span>
          </h3>
          {issues.length === 0 ? (
            <p className="flex items-center gap-2 text-caption text-success">
              <CircleCheck aria-hidden className="size-4" />
              {t("noIssues")}
            </p>
          ) : (
            <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto">
              {issues.map((issue) => (
                <li key={issue.path}>
                  <button
                    type="button"
                    onClick={() => focusField(issue.path)}
                    className={cn(
                      "flex w-full flex-col items-start gap-0.5 rounded-md px-2 py-1.5 text-left hover:bg-subtle",
                      focusRing,
                    )}
                  >
                    <span className="text-caption font-semibold text-text">{issue.field}</span>
                    <span className="text-caption text-danger">{issue.message}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="flex flex-col gap-2 border-t border-border pt-3">{actions}</div>
        <p className="text-caption text-text-muted">{t("draftNote")}</p>
      </Card>
    </aside>
  );
}

function SummaryItem({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className="m-0 flex flex-col">
        <span className="text-h3 font-bold text-text tabular-nums">{value}</span>
        {detail && <span className="text-caption text-text-muted">{detail}</span>}
      </dd>
    </div>
  );
}
