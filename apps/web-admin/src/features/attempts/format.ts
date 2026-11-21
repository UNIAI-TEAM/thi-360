import { pickName } from "@thi360/web-kit/i18n";
import { isValidCalendarDate } from "@/features/grading/scoring";
import {
  ATTEMPT_EVENT_TYPES,
  type AdminAttemptDetailResponse,
  type AdminAttemptEventView,
  type AdminAttemptItemView,
  type AdminAttemptSummary,
  type AttemptChoiceSummary,
  type AttemptEventType,
  type ExamKindCatalogOption,
} from "./types";

export { isValidCalendarDate };

const KNOWN_SCOPES = new Set(["FULL", "PART", "SUBJECT", "REVIEW"]);
const KNOWN_EVENT_TYPES = new Set<string>(ATTEMPT_EVENT_TYPES);
const KNOWN_END_REASONS = new Set(["SUBMITTED", "TIME_UP", "CHOICE_TIMEOUT", "FAULT_CLOSED"]);
const KNOWN_ABORT_REASONS = new Set([
  "GENERATION_FAILED",
  "GENERATION_TIMEOUT",
  "GENERATION_BUSY",
  "STALE_STARTING",
  "AUDIO_ASSET_MISSING",
  "AUDIO_ASSET_INVALID",
]);
const KNOWN_CLOSE_REASONS = new Set([
  "FINISHED",
  "TIME_UP",
  "CHOICE_TIMEOUT",
  "ATTEMPT_SUBMITTED",
  "FAULT_CLOSED",
]);
const KNOWN_TIMINGS = new Set(["PER_PART", "SHARED"]);
const KNOWN_NAVIGATION_MODES = new Set(["SEQUENTIAL", "FREE"]);
const KNOWN_RESULT_VIEWS = new Set(["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION", "FULL"]);

export function resolveExamKindLabel(
  code: string | undefined,
  examKinds: readonly ExamKindCatalogOption[] | undefined,
  uiLocale: "vi" | "en",
): string {
  if (!code) return "";
  const found = examKinds?.find((kind) => kind.code === code);
  if (!found) return code;
  return pickName({ nameVi: found.nameVi, nameEn: found.nameEn }, uiLocale) || code;
}

export function isKnownScope(scope: string | undefined): scope is "FULL" | "PART" | "SUBJECT" | "REVIEW" {
  return Boolean(scope && KNOWN_SCOPES.has(scope));
}

export function isKnownEventType(type: string | undefined): type is AttemptEventType {
  return Boolean(type && KNOWN_EVENT_TYPES.has(type));
}

export function isKnownEndReason(
  reason: string | undefined,
): reason is "SUBMITTED" | "TIME_UP" | "CHOICE_TIMEOUT" | "FAULT_CLOSED" {
  return Boolean(reason && KNOWN_END_REASONS.has(reason));
}

export function isKnownAbortReason(
  reason: string | undefined,
): reason is
  | "GENERATION_FAILED"
  | "GENERATION_TIMEOUT"
  | "GENERATION_BUSY"
  | "STALE_STARTING"
  | "AUDIO_ASSET_MISSING"
  | "AUDIO_ASSET_INVALID" {
  return Boolean(reason && KNOWN_ABORT_REASONS.has(reason));
}

export function isKnownCloseReason(
  reason: string | undefined,
): reason is "FINISHED" | "TIME_UP" | "CHOICE_TIMEOUT" | "ATTEMPT_SUBMITTED" | "FAULT_CLOSED" {
  return Boolean(reason && KNOWN_CLOSE_REASONS.has(reason));
}

export function isKnownTiming(timing: string | undefined): timing is "PER_PART" | "SHARED" {
  return Boolean(timing && KNOWN_TIMINGS.has(timing));
}

export function isKnownNavigationMode(mode: string | undefined): mode is "SEQUENTIAL" | "FREE" {
  return Boolean(mode && KNOWN_NAVIGATION_MODES.has(mode));
}

export function isKnownResultView(
  view: string | undefined,
): view is "SCORE" | "SCORE_AND_ANSWERS" | "FULL_SOLUTION" | "FULL" {
  return Boolean(view && KNOWN_RESULT_VIEWS.has(view));
}

export function hasAttemptScore(
  attempt: Pick<AdminAttemptSummary | AdminAttemptDetailResponse, "state" | "regrading" | "displayScore">,
): boolean {
  return (
    attempt.displayScore != null &&
    Number.isFinite(attempt.displayScore) &&
    (attempt.state === "SCORED" || Boolean(attempt.regrading))
  );
}

export function formatChoiceSummary(choice: AttemptChoiceSummary | undefined): string {
  if (!choice) return "";
  const parts: string[] = [];
  if (choice.optionCode) parts.push(choice.optionCode);
  if (choice.subjectCodes && choice.subjectCodes.length > 0) {
    const counts = choice.subjectCounts;
    const subjects = choice.subjectCodes.map((code) =>
      counts && typeof counts[code] === "number" ? `${code} (${counts[code]})` : code,
    );
    parts.push(subjects.join(", "));
  }
  return parts.join(" · ");
}

export function stripHtmlToPlainText(html: string): string {
  return html
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export type FormattedAnswerDisplay =
  | { kind: "blank" }
  | { kind: "notApplicable" }
  | { kind: "text"; text: string; isExamContent: boolean }
  | { kind: "cloze"; entries: Array<{ index: number; value: string }> }
  | { kind: "trueFalse"; entries: Array<{ key: string; value: boolean }> }
  | { kind: "essay"; wordCount: number; preview: string };

export function parseAttemptAnswerDisplay(item: AdminAttemptItemView): FormattedAnswerDisplay {
  if (item.questionType === "QUESTION_SET") {
    return { kind: "notApplicable" };
  }
  const answer = item.answer;
  if (!answer) {
    return { kind: "blank" };
  }

  switch (answer.type) {
    case "SINGLE_CHOICE": {
      const optionId = answer.optionId?.trim() ?? "";
      return optionId ? { kind: "text", text: optionId, isExamContent: false } : { kind: "blank" };
    }
    case "MULTIPLE_CHOICE": {
      const ids = (answer.optionIds ?? []).filter((id) => Boolean(id && id.trim()));
      return ids.length > 0
        ? { kind: "text", text: ids.join(", "), isExamContent: false }
        : { kind: "blank" };
    }
    case "SHORT_ANSWER": {
      const text = answer.text?.trim() ?? "";
      return text ? { kind: "text", text, isExamContent: true } : { kind: "blank" };
    }
    case "CLOZE": {
      const entries = Object.entries(answer.blanks ?? {})
        .filter(([, val]) => val.trim().length > 0)
        .map(([, val], idx) => ({ index: idx + 1, value: val.trim() }));
      return entries.length > 0 ? { kind: "cloze", entries } : { kind: "blank" };
    }
    case "TRUE_FALSE": {
      const entries = Object.entries(answer.statements ?? {})
        .filter(([, val]) => typeof val === "boolean")
        .map(([key, val]) => ({ key, value: val }));
      return entries.length > 0 ? { kind: "trueFalse", entries } : { kind: "blank" };
    }
    case "MATCHING": {
      const pairs = Object.entries(answer.pairs ?? {})
        .filter(([, right]) => Boolean(right && right.trim()))
        .map(([left, right]) => `${left} → ${right}`);
      return pairs.length > 0
        ? { kind: "text", text: pairs.join(" · "), isExamContent: false }
        : { kind: "blank" };
    }
    case "ESSAY": {
      const preview = stripHtmlToPlainText(answer.html ?? "");
      const wordCount = typeof answer.wordCount === "number" ? answer.wordCount : 0;
      if (!preview && wordCount === 0) {
        return { kind: "blank" };
      }
      return { kind: "essay", wordCount, preview };
    }
    default:
      return { kind: "blank" };
  }
}

export interface EventDetailInfo {
  partName?: string;
  entries: Array<{ key: string; value: string }>;
}

function stringifyPrimitive(value: unknown): string | null {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    const items = value.map(stringifyPrimitive).filter((v): v is string => v !== null);
    return items.length > 0 ? items.join(", ") : null;
  }
  return null;
}

export function extractEventDetailInfo(
  event: AdminAttemptEventView,
  partNameById: ReadonlyMap<string, string>,
): EventDetailInfo {
  const partName = event.partId ? partNameById.get(event.partId) : undefined;
  const entries: Array<{ key: string; value: string }> = [];
  if (event.data && typeof event.data === "object") {
    for (const [key, rawValue] of Object.entries(event.data)) {
      const str = stringifyPrimitive(rawValue);
      if (str !== null) {
        entries.push({ key, value: str });
      }
    }
  }
  return { partName, entries };
}
