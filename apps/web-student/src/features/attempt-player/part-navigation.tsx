"use client";

import { cn, focusRing } from "@thi360/ui";
import { Check, Lock } from "lucide-react";
import { useTranslations } from "next-intl";
import { type KeyboardEvent, useRef } from "react";
import type { AttemptPartStateView } from "./mappers";

export interface PartNavigationProps {
  parts: AttemptPartStateView[];
  currentPartOrd: number | null;
  navigationMode: "SEQUENTIAL" | "FREE" | string;
  choiceMap?: Record<number, { name: string }>;
  onSelectPart?: (partOrd: number) => void;
  disabled?: boolean;
}

type NormalizedPartState =
  | "PENDING"
  | "AWAITING_CHOICE"
  | "GENERATING"
  | "GENERATION_FAILED"
  | "OPEN"
  | "CLOSED"
  | "SKIPPED";

function normalizePartState(state?: string): NormalizedPartState {
  if (state === "GEN_FAILED" || state === "GENERATION_FAILED") {
    return "GENERATION_FAILED";
  }
  switch (state) {
    case "AWAITING_CHOICE":
    case "GENERATING":
    case "OPEN":
    case "CLOSED":
    case "SKIPPED":
      return state;
    case "PENDING":
    default:
      return "PENDING";
  }
}

export function PartNavigation({
  parts,
  currentPartOrd,
  navigationMode,
  choiceMap = {},
  onSelectPart,
  disabled = false,
}: PartNavigationProps) {
  const t = useTranslations("attempt.parts");
  const tabRefs = useRef<Record<number, HTMLButtonElement | null>>({});
  const isFree = navigationMode === "FREE";
  const sortedParts = [...parts].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));

  function resolvePartLabel(part: AttemptPartStateView, index: number): string {
    const ord = part.ord ?? index + 1;
    const chosenName = choiceMap[ord]?.name?.trim();
    if (chosenName) return chosenName;
    if (part.partName?.trim()) return part.partName.trim();
    return part.partCode?.trim() || `#${ord}`;
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, currentIndex: number) {
    if (!onSelectPart || disabled) return;
    let delta = 0;
    if (event.key === "ArrowRight") delta = 1;
    if (event.key === "ArrowLeft") delta = -1;
    if (delta === 0) return;

    const total = sortedParts.length;
    for (let step = 1; step <= total; step += 1) {
      const candidateIndex = (currentIndex + delta * step + total) % total;
      const candidate = sortedParts[candidateIndex];
      if (!candidate || typeof candidate.ord !== "number") continue;
      if (normalizePartState(candidate.state) === "OPEN") {
        event.preventDefault();
        onSelectPart(candidate.ord);
        tabRefs.current[candidate.ord]?.focus();
        return;
      }
    }
  }

  if (isFree) {
    return (
      <div
        role="tablist"
        aria-label={t("navLabel")}
        className="flex items-center gap-1.5 overflow-x-auto py-0.5"
      >
        {sortedParts.map((part, index) => {
          const ord = part.ord ?? index + 1;
          const normState = normalizePartState(part.state);
          const isCurrent = ord === currentPartOrd;
          const isOpen = normState === "OPEN";
          const isDisabled = disabled || !isOpen;
          const partLabel = resolvePartLabel(part, index);

          return (
            <button
              key={part.partId ?? ord}
              ref={(el) => {
                tabRefs.current[ord] = el;
              }}
              type="button"
              role="tab"
              aria-selected={isCurrent}
              disabled={isDisabled}
              tabIndex={isCurrent ? 0 : -1}
              onClick={() => {
                if (!isDisabled) {
                  onSelectPart?.(ord);
                }
              }}
              onKeyDown={(event) => handleTabKeyDown(event, index)}
              className={cn(
                "inline-flex shrink-0 items-center gap-1.5 rounded-lg border px-2.5 py-1 text-caption font-medium transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
                isCurrent
                  ? "relative border-primary-600 bg-primary-50 text-primary-800 after:pointer-events-none after:absolute after:inset-x-2 after:bottom-0 after:border-b-2 after:border-primary-600"
                  : isOpen
                    ? "border-border bg-surface text-text-secondary hover:border-border-strong hover:bg-subtle"
                    : "cursor-not-allowed border-border bg-subtle text-text-muted opacity-65",
                focusRing,
              )}
            >
              {normState === "CLOSED" ? (
                <Check className="size-3.5 text-success" aria-hidden="true" />
              ) : normState !== "OPEN" ? (
                <Lock className="size-3.5 text-text-muted" aria-hidden="true" />
              ) : null}
              <span data-exam-content="">{partLabel}</span>
              <span className="sr-only">{t(`states.${normState}`)}</span>
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <nav aria-label={t("navLabel")} className="overflow-x-auto py-0.5">
      <ol className="flex items-center gap-1.5">
        {sortedParts.map((part, index) => {
          const ord = part.ord ?? index + 1;
          const normState = normalizePartState(part.state);
          const isCurrent = ord === currentPartOrd;
          const isClosed = normState === "CLOSED" || normState === "SKIPPED";
          const partLabel = resolvePartLabel(part, index);

          return (
            <li
              key={part.partId ?? ord}
              aria-current={isCurrent ? "step" : undefined}
              className={cn(
                "inline-flex shrink-0 items-center gap-1.5 rounded-lg border px-2.5 py-1 text-caption font-medium",
                isCurrent
                  ? "border-primary-600 bg-primary-50 text-primary-800"
                  : isClosed
                    ? "border-success bg-success-bg text-success"
                    : "border-border bg-subtle text-text-muted",
              )}
            >
              {isClosed ? (
                <Check className="size-3.5 shrink-0" aria-hidden="true" />
              ) : !isCurrent ? (
                <Lock className="size-3.5 shrink-0" aria-hidden="true" />
              ) : null}
              <span data-exam-content="">{partLabel}</span>
              <span className="rounded bg-surface px-1.5 py-0.5 text-caption text-text-muted">
                {t(`states.${normState}`)}
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
