import type { StatusTone } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { APP_TIME_ZONE, normalizeUiLocale } from "@thi360/web-kit/preferences";
import type {
  AttemptChoiceSummary,
  AttemptResultResponse,
  AttemptResultVactSubsection,
  CatalogOptionResponse,
} from "./api";

const DATE_TIME_PARTS = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: APP_TIME_ZONE,
});

const VI_SCORE_FORMAT = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });
const EN_SCORE_FORMAT = new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 });

/** Formats an ISO timestamp as "HH:mm, dd/MM/yyyy" in Asia/Ho_Chi_Minh. */
export function formatAttemptDateTime(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  const parts = Object.fromEntries(DATE_TIME_PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  return `${parts.hour}:${parts.minute}, ${parts.day}/${parts.month}/${parts.year}`;
}

/** Formats numeric scores with locale separators, max 2 decimal digits, and no trailing zero padding. */
export function formatScoreValue(value: number, locale: string): string {
  const formatter = normalizeUiLocale(locale) === "en" ? EN_SCORE_FORMAT : VI_SCORE_FORMAT;
  return formatter.format(Number(value.toFixed(2)));
}

/**
 * Genuine 0 (or any total score) is visible ONLY when the attempt is SCORED or currently regrading
 * with an existing score snapshot (AC-4, AC-19).
 */
export function shouldShowAttemptScore(
  state: AttemptResultResponse["state"] | undefined,
  regrading: boolean | undefined,
  displayScore: number | null | undefined,
  rawScore?: number | null | undefined,
): boolean {
  if (displayScore == null && rawScore == null) return false;
  return state === "SCORED" || (state === "PENDING_REVIEW" && regrading === true);
}

export function resolveAttemptDisplayScore(
  result: Pick<AttemptResultResponse, "state" | "regrading" | "displayScore" | "rawScore" | "scaleMax" | "rawMaxScore">,
): { score: number; max: number } | null {
  if (!shouldShowAttemptScore(result.state, result.regrading, result.displayScore, result.rawScore)) {
    return null;
  }
  const score = result.displayScore ?? result.rawScore;
  if (score == null) return null;
  const max = result.scaleMax ?? result.rawMaxScore ?? 0;
  return { score, max };
}

export type DurationParts =
  | { kind: "zero" }
  | { kind: "seconds"; seconds: number }
  | { kind: "minutes"; minutes: number }
  | { kind: "hoursMinutes"; hours: number; minutes: number };

export function computeActualDuration(
  startedAt: string | null | undefined,
  finishedAt: string | null | undefined,
): DurationParts | null {
  if (!startedAt || !finishedAt) return null;
  const startMs = Date.parse(startedAt);
  const endMs = Date.parse(finishedAt);
  if (Number.isNaN(startMs) || Number.isNaN(endMs)) return null;
  const totalSeconds = Math.max(0, Math.round((endMs - startMs) / 1000));
  if (totalSeconds === 0) return { kind: "zero" };
  if (totalSeconds < 60) return { kind: "seconds", seconds: totalSeconds };
  const totalMinutes = Math.floor(totalSeconds / 60);
  if (totalMinutes < 60) return { kind: "minutes", minutes: totalMinutes };
  return { kind: "hoursMinutes", hours: Math.floor(totalMinutes / 60), minutes: totalMinutes % 60 };
}

export function buildCatalogMap(options: readonly CatalogOptionResponse[]): Map<string, CatalogOptionResponse> {
  const map = new Map<string, CatalogOptionResponse>();
  for (const item of options) {
    if (item.code) {
      map.set(item.code, item);
    }
  }
  return map;
}

export function resolveCatalogLabel(
  code: string | null | undefined,
  catalogMap: ReadonlyMap<string, CatalogOptionResponse>,
  locale: string,
): string {
  if (!code) return "";
  const option = catalogMap.get(code);
  if (!option) return code;
  return pickName(option, locale) || code;
}

const KNOWN_CHOICE_OPTION_CODES = new Set(["SCIENCE", "ENGLISH", "KH", "TA", "CS", "ICT"]);
export type KnownChoiceOptionCode = "SCIENCE" | "ENGLISH" | "KH" | "TA" | "CS" | "ICT";

export function isKnownChoiceOptionCode(code: string): code is KnownChoiceOptionCode {
  return KNOWN_CHOICE_OPTION_CODES.has(code);
}

export function formatAttemptChoices(
  choices: Record<string, AttemptChoiceSummary> | undefined,
  subjectMap: ReadonlyMap<string, CatalogOptionResponse>,
  locale: string,
  translateOptionCode: (code: KnownChoiceOptionCode) => string,
): string[] {
  if (!choices) return [];
  const formatted: string[] = [];
  for (const summary of Object.values(choices)) {
    if (!summary?.optionCode) continue;
    const optionCode = summary.optionCode.trim();
    const optionLabel = isKnownChoiceOptionCode(optionCode)
      ? translateOptionCode(optionCode)
      : optionCode;
    const subjectLabels = (summary.subjectCodes ?? [])
      .map((code) => resolveCatalogLabel(code, subjectMap, locale))
      .filter(Boolean);
    if (subjectLabels.length > 0) {
      formatted.push(`${optionLabel}: ${subjectLabels.join(", ")}`);
    } else {
      formatted.push(optionLabel);
    }
  }
  return formatted;
}

const KNOWN_VACT_COMPONENT_CODES = new Set(["VIETNAMESE", "ANH", "TOAN", "SCIENTIFIC_THINKING"]);
export type KnownVactComponentCode = "VIETNAMESE" | "ANH" | "TOAN" | "SCIENTIFIC_THINKING";

export function isKnownVactComponentCode(code: string | undefined): code is KnownVactComponentCode {
  return Boolean(code && KNOWN_VACT_COMPONENT_CODES.has(code));
}

export function isLogicDataSubsection(
  sub: AttemptResultVactSubsection,
  index: number,
  totalCount: number,
): boolean {
  return sub.questionCount === 12 || (sub.questionCount !== 18 && totalCount === 2 && index === 0);
}

export function resolveResultStatusTone(
  state: AttemptResultResponse["state"] | undefined,
  regrading: boolean | undefined,
): StatusTone {
  if (regrading) return "warning";
  switch (state) {
    case "SCORED":
      return "success";
    case "PENDING_REVIEW":
      return "warning";
    case "GRADING":
      return "info";
    case "GRADING_ERROR":
      return "danger";
    default:
      return "neutral";
  }
}

export function isKnownResultState(
  state: AttemptResultResponse["state"] | undefined,
): state is "GRADING" | "PENDING_REVIEW" | "SCORED" | "GRADING_ERROR" {
  return state === "GRADING" || state === "PENDING_REVIEW" || state === "SCORED" || state === "GRADING_ERROR";
}

export function isKnownScope(scope: string | undefined): scope is "FULL" | "PART" | "SUBJECT" {
  return scope === "FULL" || scope === "PART" || scope === "SUBJECT";
}

export function isKnownEndReason(
  reason: AttemptResultResponse["endReason"] | undefined,
): reason is "SUBMITTED" | "TIME_UP" | "CHOICE_TIMEOUT" | "FAULT_CLOSED" {
  return (
    reason === "SUBMITTED" ||
    reason === "TIME_UP" ||
    reason === "CHOICE_TIMEOUT" ||
    reason === "FAULT_CLOSED"
  );
}
