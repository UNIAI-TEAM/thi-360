"use client";

import { useQuery } from "@tanstack/react-query";
import { readCookieValue, XSRF_COOKIE_NAME, XSRF_HEADER_NAME } from "@thi360/web-kit/client";
import type { OptionalSessionLoader } from "@/features/session/api";
import { useOptionalSession } from "@/features/session/use-optional-session";
import { ApiStatusError } from "@/lib/api/client";

export const EXAM_ACCESS_STATES = ["IN_PROGRESS", "CAN_START", "CHECKING", "EXHAUSTED", "NONE"] as const;
export type ExamAccessState = (typeof EXAM_ACCESS_STATES)[number];

/**
 * LOCAL STUB of CM-4b (UNI-2715) `POST /api/v1/me/exam-access/query` (operationId `queryMyExamAccess`), shape frozen
 * in CM-4b:2 (c7e19d75): request `{codes}` (0..48), response `{items: ExamAccessItem[]}`, 403 for MANAGE and other
 * non-EXAM accounts, 401 without a session. CM-4b is not merged, so `schema.d.ts` has no such path: types and the call
 * are hand-written here. After CM-4b merges and the api-client is regenerated, replace this stub with the generated
 * types and client (`components["schemas"]["ExamAccessItem"]`) and delete the hand-written fetch.
 */
export interface ExamAccessRow {
  code: string;
  state: ExamAccessState;
  attemptsLeft: number;
  entitlementId: string | null;
  activeAttemptId: string | null;
  hasPendingFulfillment: boolean;
  pendingOrderCode: string | null;
}

/** `forbidden`: tài khoản quản trị (API trả 403); trang không có nút chính. */
export type ExamAccessResult = { kind: "rows"; rows: ExamAccessRow[] } | { kind: "forbidden" };
export type ExamAccessLoader = (codes: readonly string[]) => Promise<ExamAccessResult>;

export const EXAM_ACCESS_ENDPOINT = "/api/v1/me/exam-access/query";

export async function loadExamAccess(codes: readonly string[]): Promise<ExamAccessResult> {
  const headers: Record<string, string> = { "Content-Type": "application/json", Accept: "application/json" };
  const xsrf = typeof document === "undefined" ? null : readCookieValue(document.cookie, XSRF_COOKIE_NAME);
  if (xsrf) headers[XSRF_HEADER_NAME] = xsrf;

  const response = await fetch(EXAM_ACCESS_ENDPOINT, {
    method: "POST",
    headers,
    credentials: "same-origin",
    body: JSON.stringify({ codes }),
  });
  // Tài khoản quản trị nhận 403: đây là kết quả, không phải lỗi, nên không đi qua luồng 403 toàn cục của query-provider.
  if (response.status === 403) return { kind: "forbidden" };
  if (!response.ok) throw new ApiStatusError(response.status);

  const body: { items: ExamAccessRow[] } = await response.json();
  return { kind: "rows", rows: body.items };
}

/** Điều nút mua cần biết về một sản phẩm (TM 11.3). */
export type AccessView =
  | { kind: "guest" }
  | { kind: "loading" }
  | { kind: "error" }
  | { kind: "admin" }
  | { kind: "ready"; row: ExamAccessRow };

const noAccess = (code: string): ExamAccessRow => ({
  code,
  state: "NONE",
  attemptsLeft: 0,
  entitlementId: null,
  activeAttemptId: null,
  hasPendingFulfillment: false,
  pendingOrderCode: null,
});

/**
 * Quyền của người xem với các mã sản phẩm trên màn hình. Khách và quản trị viên (biết từ phiên) không gọi
 * `exam-access`; học viên gọi một lần cho cả danh sách mã. Chưa biết quyền thì `loading`, không hiện nút Mua.
 */
export function useExamAccess(
  codes: readonly string[],
  loaders: { loadSession?: OptionalSessionLoader; loadAccess?: ExamAccessLoader } = {},
) {
  const { loadSession, loadAccess = loadExamAccess } = loaders;
  const session = useOptionalSession(loadSession);
  const isStudent = session.data?.access === "EXAM";
  const access = useQuery({
    queryKey: ["exam-access", ...codes],
    queryFn: () => loadAccess(codes),
    enabled: isStudent && codes.length > 0,
  });

  function view(code: string): AccessView {
    if (session.isLoading) return { kind: "loading" };
    if (session.isError) return { kind: "error" };
    if (!session.data) return { kind: "guest" };
    if (!isStudent) return { kind: "admin" };
    if (access.isError) return { kind: "error" };
    if (!access.data) return { kind: "loading" };
    if (access.data.kind === "forbidden") return { kind: "admin" };
    return { kind: "ready", row: access.data.rows.find((row) => row.code === code) ?? noAccess(code) };
  }

  function reload() {
    if (session.isError) void session.refetch();
    else void access.refetch();
  }

  return { view, reload };
}
