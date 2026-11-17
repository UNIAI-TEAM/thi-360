"use client";

import { Card, cn, focusRing } from "@thi360/ui";
import { CircleCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { ExamTiming } from "../types";

export interface CreateExamIssue {
  /** Đường dẫn trường, khớp `data-field` của ô nhập (`kind`, `sharedDuration`, `p:1.questionCount`). */
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

export interface CreateExamSummaryData {
  kindLabel: string;
  scopeLabel: string;
  totalQuestions: number | null;
  totalMinutes: number | null;
  totalPoints: number | null;
  /** Tên mẫu mặc định đã nạp; `null` khi không có mẫu (bài ôn chưa có mẫu, môn chưa có mẫu). */
  templateName: string | null;
}

/**
 * Tóm tắt của bước 1 (TaoBaiThi2B): kỳ thi, phạm vi, tổng số câu, thời lượng (ghi "chung" khi dùng một đồng hồ),
 * điểm tối đa, mẫu mặc định và các mục cần xử lý, mỗi mục đưa tới ô nhập tương ứng. Nằm ngay dưới bảng tổng quan,
 * trước nút Hủy / Lưu và tiếp tục.
 */
export function CreateExamSummaryPanel({
  data,
  timing,
  issues,
}: {
  data: CreateExamSummaryData;
  timing: ExamTiming;
  issues: readonly CreateExamIssue[];
}) {
  const t = useTranslations("exams.create.summary");
  const headingId = useId();
  const issuesHeadingId = useId();
  const number = (value: number | null) => (value === null ? "—" : String(value));

  return (
    <section aria-labelledby={headingId} className="flex-[1_1_300px] sticky top-4">
      <Card padding="md" className="flex flex-col gap-4">
        <h2 id={headingId} className="text-title font-semibold text-text">
          {t("title")}
        </h2>
        <dl className="grid grid-cols-2 gap-3 text-body">
          <SummaryItem label={t("kind")} value={data.kindLabel} />
          <SummaryItem label={t("scope")} value={data.scopeLabel} />
          <SummaryItem label={t("totalQuestions")} value={number(data.totalQuestions)} />
          <SummaryItem
            label={t("totalMinutes")}
            value={data.totalMinutes === null ? "—" : t("minutesValue", { count: data.totalMinutes })}
            detail={timing === "SHARED" ? t("sharedTiming") : undefined}
          />
          <SummaryItem label={t("maxScore")} value={number(data.totalPoints)} />
          <SummaryItem label={t("template")} value={data.templateName ?? t("noTemplate")} small />
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

        <p className="text-caption text-text-muted">{t("draftNote")}</p>
      </Card>
    </section>
  );
}

function SummaryItem({ label, value, detail, small }: { label: string; value: string; detail?: string; small?: boolean }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className="m-0 flex flex-col">
        <span className={cn("font-bold text-text tabular-nums", small ? "text-body" : "text-h3")}>{value}</span>
        {detail && <span className="text-caption text-text-muted">{detail}</span>}
      </dd>
    </div>
  );
}
