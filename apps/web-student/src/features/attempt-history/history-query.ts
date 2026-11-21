import { isUuid } from "@/features/attempt-result/format";
import type { AttemptHistoryQueryParams } from "./api";
import { HISTORY_PAGE_SIZE } from "./api";

export { isUuid };

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_HISTORY_PAGE = 10_000;

export const HISTORY_SCOPES = ["FULL", "PART", "SUBJECT"] as const;
export type HistoryScope = (typeof HISTORY_SCOPES)[number];

export interface HistoryFilterState {
  q: string;
  kind: string;
  scope: HistoryScope | "";
  examId: string;
  from: string;
  to: string;
  page: number;
}

export function isValidHistoryScope(value: string | null | undefined): value is HistoryScope {
  return value === "FULL" || value === "PART" || value === "SUBJECT";
}

export function isValidIsoDate(value: string | null | undefined): value is string {
  if (!value) return false;
  const trimmed = value.trim();
  if (!DATE_RE.test(trimmed)) return false;
  const [yStr, mStr, dStr] = trimmed.split("-");
  const y = Number(yStr);
  const m = Number(mStr);
  const d = Number(dStr);
  const dt = new Date(Date.UTC(y, m - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d;
}

export function parseHistorySearchParams(
  params: Pick<URLSearchParams, "get">,
): HistoryFilterState {
  const q = params.get("q")?.trim() ?? "";
  const kind = params.get("kind")?.trim() ?? "";
  const rawScope = params.get("scope")?.trim() ?? "";
  const scope: HistoryScope | "" = isValidHistoryScope(rawScope) ? rawScope : "";
  const rawExamId = params.get("examId")?.trim() ?? "";
  // Non-UUID codes from legacy links (e.g. product codes from /home) must NOT reach the backend
  // @RequestParam UUID endpoint as examId or trigger HTTP 400.
  const examId = isUuid(rawExamId) ? rawExamId : "";
  const rawFrom = params.get("from")?.trim() ?? "";
  const rawTo = params.get("to")?.trim() ?? "";
  const from = isValidIsoDate(rawFrom) ? rawFrom : "";
  const to = isValidIsoDate(rawTo) ? rawTo : "";
  const rawPageStr = params.get("page")?.trim() ?? "";
  let page = 1;
  if (/^\d+$/.test(rawPageStr)) {
    const parsed = Number(rawPageStr);
    if (Number.isSafeInteger(parsed) && parsed >= 1) {
      page = Math.min(parsed, MAX_HISTORY_PAGE);
    }
  }

  return {
    q,
    kind,
    scope,
    examId,
    from,
    to,
    page,
  };
}

export function toHistorySearchString(state: HistoryFilterState): string {
  const params = new URLSearchParams();
  const q = state.q.trim();
  const kind = state.kind.trim();
  const examId = state.examId.trim();
  const from = state.from.trim();
  const to = state.to.trim();
  if (q) params.set("q", q);
  if (kind) params.set("kind", kind);
  if (state.scope) params.set("scope", state.scope);
  if (isUuid(examId)) params.set("examId", examId);
  if (isValidIsoDate(from)) params.set("from", from);
  if (isValidIsoDate(to)) params.set("to", to);
  if (state.page > 1) params.set("page", String(Math.min(state.page, MAX_HISTORY_PAGE)));
  return params.toString();
}

export function hasActiveHistoryFilters(state: HistoryFilterState): boolean {
  return Boolean(state.q || state.kind || state.scope || state.examId || state.from || state.to);
}

export function toHistoryApiParams(state: HistoryFilterState): AttemptHistoryQueryParams {
  const q = state.q.trim();
  const kind = state.kind.trim();
  const examId = state.examId.trim();
  const from = state.from.trim();
  const to = state.to.trim();
  return {
    ...(q ? { q } : {}),
    ...(kind ? { kind } : {}),
    ...(state.scope ? { scope: state.scope } : {}),
    ...(isUuid(examId) ? { examId } : {}),
    ...(isValidIsoDate(from) ? { from } : {}),
    ...(isValidIsoDate(to) ? { to } : {}),
    page: Math.max(0, Math.min(state.page, MAX_HISTORY_PAGE) - 1),
    size: HISTORY_PAGE_SIZE,
  };
}
