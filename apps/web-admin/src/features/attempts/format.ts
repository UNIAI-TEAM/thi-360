import { pickName } from "@thi360/web-kit/i18n";
import { isValidCalendarDate } from "@/features/grading/scoring";
import {
  ATTEMPT_EVENT_TYPES,
  ATTEMPT_STATES,
  type AdminAttemptDetailResponse,
  type AdminAttemptEventView,
  type AdminAttemptItemView,
  type AdminAttemptSummary,
  type AttemptChoiceSummary,
  type AttemptEventType,
  type AttemptState,
  type ExamKindCatalogOption,
} from "./types";

export { isValidCalendarDate };

const KNOWN_ATTEMPT_STATES = new Set<string>(ATTEMPT_STATES);
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

export function isKnownAttemptState(state: string | undefined): state is AttemptState {
  return Boolean(state && KNOWN_ATTEMPT_STATES.has(state));
}

export function isIsoTimestamp(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
    return false;
  }
  return !Number.isNaN(Date.parse(value));
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

const ESSAY_PREVIEW_MAX = 160;

const NAMED_HTML_ENTITIES: ReadonlyMap<string, string> = new Map([
  ["nbsp", " "],
  ["lt", "<"],
  ["gt", ">"],
  ["quot", "\""],
  ["apos", "'"],
  ["amp", "&"],
  ["mdash", "—"],
  ["ndash", "–"],
  ["hellip", "…"],
]);

function decodeNumericHtmlEntity(match: string, raw: string, radix: 10 | 16): string {
  const code = Number.parseInt(raw, radix);
  if (code === 0xa0) return " ";
  if (
    Number.isFinite(code) &&
    code >= 0x20 &&
    code <= 0x10ffff &&
    (code < 0x7f || code > 0x9f) &&
    (code < 0xd800 || code > 0xdfff)
  ) {
    return String.fromCodePoint(code);
  }
  return match;
}

function decodeHtmlEntity(match: string, entity: string): string {
  if (entity.startsWith("#x") || entity.startsWith("#X")) {
    return decodeNumericHtmlEntity(match, entity.slice(2), 16);
  }
  if (entity.startsWith("#")) {
    return decodeNumericHtmlEntity(match, entity.slice(1), 10);
  }
  return NAMED_HTML_ENTITIES.get(entity.toLowerCase()) ?? match;
}

export function stripHtmlToPlainText(html: string): string {
  return html
    .replace(/<[^>]+>/g, " ")
    .replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, decodeHtmlEntity)
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
      const optionId = typeof answer.optionId === "string" ? answer.optionId.trim() : "";
      return optionId ? { kind: "text", text: optionId, isExamContent: false } : { kind: "blank" };
    }
    case "MULTIPLE_CHOICE": {
      const ids = (answer.optionIds ?? []).filter(
        (id) => typeof id === "string" && id.trim().length > 0,
      );
      return ids.length > 0
        ? { kind: "text", text: ids.join(", "), isExamContent: false }
        : { kind: "blank" };
    }
    case "SHORT_ANSWER": {
      const text = typeof answer.text === "string" ? answer.text.trim() : "";
      return text ? { kind: "text", text, isExamContent: true } : { kind: "blank" };
    }
    case "CLOZE": {
      const entries = Object.entries(answer.blanks ?? {})
        .map(([key, val], idx) => {
          if (typeof val !== "string" || val.trim().length === 0) return null;
          const m = /^(?:b|blank[_-]?)?(\d+)$/i.exec(key.trim());
          const index = m ? Number(m[1]) : idx + 1;
          return { index: Number.isFinite(index) && index > 0 ? index : idx + 1, value: val.trim() };
        })
        .filter((entry): entry is { index: number; value: string } => entry !== null)
        .sort((a, b) => a.index - b.index);
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
        .filter(([, right]) => typeof right === "string" && right.trim().length > 0)
        .map(([left, right]) => `${left} → ${right}`);
      return pairs.length > 0
        ? { kind: "text", text: pairs.join(" · "), isExamContent: false }
        : { kind: "blank" };
    }
    case "ESSAY": {
      const plain = stripHtmlToPlainText(answer.html ?? "");
      const wordCount = typeof answer.wordCount === "number" ? answer.wordCount : 0;
      if (!plain && wordCount === 0) {
        return { kind: "blank" };
      }
      const codePoints = Array.from(plain);
      const preview =
        codePoints.length > ESSAY_PREVIEW_MAX
          ? `${codePoints.slice(0, ESSAY_PREVIEW_MAX - 1).join("").trimEnd()}…`
          : plain;
      return { kind: "essay", wordCount, preview };
    }
    default:
      return { kind: "blank" };
  }
}

export const KNOWN_EVENT_FIELD_KEYS = [
  "abortReason",
  "adminId",
  "assetId",
  "attemptId",
  "attemptNo",
  "closeReason",
  "closedAt",
  "comment",
  "compensated",
  "deadlineAt",
  "detail",
  "durationSec",
  "endReason",
  "entitlementId",
  "error",
  "essayGradeId",
  "examCode",
  "examId",
  "examVersionNo",
  "faultReason",
  "finalAnswers",
  "finalizedAt",
  "finishedAt",
  "fromState",
  "generationTries",
  "graderId",
  "gradingError",
  "grantId",
  "itemId",
  "message",
  "ord",
  "partCode",
  "partId",
  "path",
  "pendingReview",
  "points",
  "previousSessionId",
  "questionId",
  "reason",
  "reclaimed",
  "regrading",
  "reopenedReason",
  "seq",
  "sessionId",
  "startedAt",
  "state",
  "submittedAt",
  "toState",
  "totalScore",
  "tries",
  "versionNo",
] as const;

export type KnownEventFieldKey = (typeof KNOWN_EVENT_FIELD_KEYS)[number];

const KNOWN_EVENT_FIELD_KEY_SET: ReadonlySet<string> = new Set(KNOWN_EVENT_FIELD_KEYS);
const FREE_TEXT_EVENT_DATA_KEYS: ReadonlySet<string> = new Set([
  "reason",
  "error",
  "message",
  "detail",
  "path",
  "faultReason",
  "gradingError",
  "reopenedReason",
  "comment",
]);

export interface EventDetailEntry {
  key: string;
  labelKey?: KnownEventFieldKey;
  isExamContent: boolean;
  value: string;
}

export interface EventDetailInfo {
  partName?: string;
  entries: EventDetailEntry[];
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
  const entries: EventDetailEntry[] = [];
  if (event.data && typeof event.data === "object") {
    for (const [key, rawValue] of Object.entries(event.data)) {
      const str = stringifyPrimitive(rawValue);
      if (str !== null) {
        entries.push({
          key,
          labelKey: KNOWN_EVENT_FIELD_KEY_SET.has(key) ? (key as KnownEventFieldKey) : undefined,
          isExamContent: FREE_TEXT_EVENT_DATA_KEYS.has(key),
          value: str,
        });
      }
    }
  }
  return { partName, entries };
}
