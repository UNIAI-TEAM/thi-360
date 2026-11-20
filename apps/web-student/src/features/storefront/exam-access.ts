"use client";

import { useQuery } from "@tanstack/react-query";
import type { components } from "@thi360/api-client";
import type { OptionalSessionLoader } from "@/features/session/api";
import { useOptionalSession } from "@/features/session/use-optional-session";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

/** Một dòng quyền dùng của CM-4b `POST /api/v1/me/exam-access/query` (operationId `queryMyExamAccess`). */
export type ExamAccessRow = components["schemas"]["ExamAccessItem"];

/** `forbidden`: tài khoản quản trị (API trả 403); trang không có nút chính. */
export type ExamAccessResult = { kind: "rows"; rows: ExamAccessRow[] } | { kind: "forbidden" };
export type ExamAccessLoader = (codes: readonly string[]) => Promise<ExamAccessResult>;

export async function loadExamAccess(codes: readonly string[]): Promise<ExamAccessResult> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/exam-access/query", {
    body: { codes: [...codes] },
  });
  // Tài khoản quản trị nhận 403: đây là kết quả, không phải lỗi, nên không đi qua luồng 403 toàn cục của query-provider.
  if (response.status === 403) return { kind: "forbidden" };
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return { kind: "rows", rows: data.items };
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
