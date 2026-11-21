import type { AttemptHistoryQueryParams } from "./api";
import { HISTORY_PAGE_SIZE } from "./api";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

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

export function isUuid(value: string | null | undefined): value is string {
  return Boolean(value && UUID_RE.test(value.trim()));
}

export function isValidHistoryScope(value: string | null | undefined): value is HistoryScope {
  return value === "FULL" || value === "PART" || value === "SUBJECT";
}

export function isValidIsoDate(value: string | null | undefined): value is string {
  return Boolean(value && DATE_RE.test(value.trim()));
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
  const rawPage = Number.parseInt(params.get("page") ?? "1", 10);
  const page = Number.isFinite(rawPage) && rawPage > 0 ? rawPage : 1;

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
  if (state.q.trim()) params.set("q", state.q.trim());
  if (state.kind) params.set("kind", state.kind);
  if (state.scope) params.set("scope", state.scope);
  if (isUuid(state.examId)) params.set("examId", state.examId);
  if (isValidIsoDate(state.from)) params.set("from", state.from);
  if (isValidIsoDate(state.to)) params.set("to", state.to);
  if (state.page > 1) params.set("page", String(state.page));
  return params.toString();
}

export function hasActiveHistoryFilters(state: HistoryFilterState): boolean {
  return Boolean(state.q || state.kind || state.scope || state.examId || state.from || state.to);
}

export function toHistoryApiParams(state: HistoryFilterState): AttemptHistoryQueryParams {
  return {
    ...(state.q.trim() ? { q: state.q.trim() } : {}),
    ...(state.kind ? { kind: state.kind } : {}),
    ...(state.scope ? { scope: state.scope } : {}),
    ...(isUuid(state.examId) ? { examId: state.examId } : {}),
    ...(isValidIsoDate(state.from) ? { from: state.from } : {}),
    ...(isValidIsoDate(state.to) ? { to: state.to } : {}),
    page: Math.max(0, state.page - 1),
    size: HISTORY_PAGE_SIZE,
  };
}
