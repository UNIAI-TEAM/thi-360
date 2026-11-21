"use client";

import { cn } from "@thi360/ui";
import { formatScore } from "@thi360/ui/question";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { useLocale, useTranslations } from "next-intl";
import type { AttemptDisplaySegmentView } from "./segment-units";

export interface SegmentHeaderProps {
  segment: AttemptDisplaySegmentView;
  isSegmentStart?: boolean;
  positionOffset?: number;
  className?: string;
}

/**
 * Displays the active display segment title and declared metadata above the question card
 * without any per-segment timer or clock (BS 17.3, AC-9).
 */
export function SegmentHeader({
  segment,
  isSegmentStart = false,
  positionOffset = 0,
  className,
}: SegmentHeaderProps) {
  const t = useTranslations("attempt.segments");
  const locale = useLocale();
  const uiLocale = normalizeUiLocale(locale);

  const title = segment.name || segment.code || "";
  const hasRange = segment.fromPosition != null && segment.toPosition != null;
  const displayFrom = hasRange ? segment.fromPosition! + positionOffset : null;
  const displayTo = hasRange ? segment.toPosition! + positionOffset : null;
  const formattedMaxPoints =
    segment.maxPoints != null ? formatScore(segment.maxPoints, uiLocale) : null;

  return (
    <div
      data-segment-header=""
      data-segment-code={segment.code ?? ""}
      data-segment-start={isSegmentStart ? "true" : "false"}
      className={cn(
        "flex flex-wrap items-center justify-between gap-2.5 rounded-xl border border-border bg-surface px-4 py-3 shadow-xs",
        className,
      )}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <span aria-hidden className="h-5 w-1 shrink-0 rounded-full bg-primary-600" />
        <h2 data-exam-content className="min-w-0 text-title font-bold text-text">
          {title}
        </h2>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-caption font-medium text-text-secondary tabular-nums">
        {hasRange && (
          <span className="inline-flex h-6 items-center rounded-full border border-border bg-subtle px-2.5">
            {t("range", { from: displayFrom!, to: displayTo! })}
          </span>
        )}
        {segment.commandCount != null && (
          <span className="inline-flex h-6 items-center rounded-full border border-border bg-subtle px-2.5">
            {t("commands", { count: segment.commandCount })}
          </span>
        )}
        {formattedMaxPoints != null && (
          <span className="inline-flex h-6 items-center rounded-full border border-border bg-subtle px-2.5">
            {t("maxPoints", { points: formattedMaxPoints })}
          </span>
        )}
      </div>
    </div>
  );
}
