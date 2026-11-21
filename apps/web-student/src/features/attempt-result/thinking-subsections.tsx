"use client";

import { cn, focusRing } from "@thi360/ui";
import { ChevronDown } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useId, useState } from "react";
import type { AttemptResultVactSubsection } from "./api";
import { formatScoreValue, isLogicDataSubsection } from "./format";

export function ThinkingSubsections({
  componentCode,
  subsections,
}: {
  componentCode: string;
  subsections?: AttemptResultVactSubsection[];
}) {
  const t = useTranslations("result.vact");
  const locale = useLocale();
  const [expanded, setExpanded] = useState(false);
  const panelId = useId();

  if (!subsections || subsections.length === 0) {
    return null;
  }

  return (
    <div className="mt-2 flex flex-col gap-2.5 border-t border-border pt-3">
      <button
        type="button"
        aria-expanded={expanded}
        aria-controls={panelId}
        data-component-code={componentCode}
        onClick={() => setExpanded((prev) => !prev)}
        className={cn(
          "inline-flex min-h-11 w-full items-center justify-between gap-2 rounded-lg border border-border bg-page px-3 py-2 text-left text-label font-semibold text-primary-text transition-colors duration-150 hover:border-border-strong hover:bg-subtle motion-reduce:transition-none",
          focusRing,
        )}
      >
        <span>{expanded ? t("collapseSubsections") : t("expandSubsections")}</span>
        <ChevronDown
          aria-hidden
          className={cn("size-4 shrink-0 transition-transform duration-150 motion-reduce:transition-none", expanded && "rotate-180")}
        />
      </button>

      {expanded && (
        <ul id={panelId} className="flex flex-col gap-2">
          {subsections.map((sub, index) => {
            const label = isLogicDataSubsection(sub, index, subsections.length)
              ? t("subsections.logicData")
              : t("subsections.scientificReasoning");
            const total = sub.totalCount ?? sub.questionCount ?? 0;
            const correct = sub.correctCount ?? 0;
            const rate = formatScoreValue(sub.correctRate ?? 0, locale);
            return (
              <li
                key={sub.sectionId ?? `${index}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-page px-3 py-2.5"
              >
                <div className="flex min-w-0 flex-col gap-0.5">
                  <span className="font-semibold text-text">{label}</span>
                  <span className="text-caption text-text-muted tabular-nums">
                    {t("subsectionQuestions", { count: sub.questionCount ?? total })}
                  </span>
                </div>
                <span className="text-label font-semibold text-text tabular-nums">
                  {t("correctRatio", { correct, total, rate })}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
