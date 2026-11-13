import { ApiStatusError } from "@/lib/api/query-client";
import { redirectToLogin } from "@/lib/auth-redirect";
import { isFakeSessionEnabled } from "@/lib/env";
import { FAKE_SESSION } from "./fake-session";
import type { Session } from "./types";

export const SESSION_QUERY_KEY = ["session", "me"] as const;
export type SessionLoader = () => Promise<Session>;

/**
 * Nguồn phiên của đợt T06: chỉ phiên giả khi `NEXT_PUBLIC_DEV_FAKE_SESSION=1` (bị bỏ qua ở
 * production). Lời gọi thật `GET /api/v1/me` qua `@thi360/api-client` được thêm ở T18 (UNI-2556).
 */
export async function loadSession(): Promise<Session> {
  if (isFakeSessionEnabled()) return FAKE_SESSION;
  throw new ApiStatusError(401);
}

/**
 * Đăng xuất rồi về trang đăng nhập. Lời gọi `POST /api/v1/auth/logout` qua `@thi360/api-client`
 * được nối ở T18 (UNI-2556).
 */
export async function logout(): Promise<void> {
  redirectToLogin({ returnHere: false });
}
