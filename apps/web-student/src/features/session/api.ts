import type { components } from "@thi360/api-client";
import { clearAllAttemptDrafts } from "@/features/attempt-draft";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type StudentSession = components["schemas"]["MeResponse"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type LoginResponse = components["schemas"]["LoginResponse"];
export type ChangePasswordRequest = components["schemas"]["ChangePasswordRequest"];

export const SESSION_QUERY_KEY = ["session", "me"] as const;
export type SessionLoader = () => Promise<StudentSession>;

export async function loadSession(): Promise<StudentSession> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me");
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

/**
 * Phiên tùy chọn cho trang công khai (`/`, `/exams/<code>`): khách nhận `null`, không phải lỗi. `query-provider.tsx`
 * đưa mọi 401 từ truy vấn về `/login`, nên 401 của khách phải được hấp thụ ở đây, trước khi thành lỗi của truy vấn.
 * Cookie truy cập hết hạn nhưng phiên còn (refresh token): thử `refreshSession()` đúng một lần rồi gọi lại `/me`;
 * chỉ khi làm mới cũng thất bại mới coi là khách. Lỗi khác (5xx, mạng) vẫn là lỗi.
 */
export const OPTIONAL_SESSION_QUERY_KEY = ["session", "optional"] as const;
export type OptionalSessionLoader = () => Promise<StudentSession | null>;

export async function loadOptionalSession(): Promise<StudentSession | null> {
  const client = createBrowserApiClient();
  let { data, error, response } = await client.GET("/api/v1/me");
  if (response.status === 401) {
    try {
      await refreshSession();
    } catch {
      // Làm mới cũng 401 (khách, hoặc refresh token đã hết): coi là khách, không đưa về /login.
      return null;
    }
    ({ data, error, response } = await client.GET("/api/v1/me"));
    if (response.status === 401) return null;
  }
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function loginWithPassword(body: LoginRequest): Promise<LoginResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/auth/login", { body });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function changeAccountPassword(body: ChangePasswordRequest): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/change-password", { body });
  if (!response.ok) {
    throw toApiStatusError(response.status, error);
  }
}

export async function logoutSession(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/logout");
  if (!response.ok && response.status !== 401) {
    throw toApiStatusError(response.status, error);
  }
  try {
    await clearAllAttemptDrafts();
  } catch {
    // Best-effort draft cleanup on explicit logout (AC-24).
  }
}

export async function refreshSession(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/refresh");
  if (!response.ok) {
    throw toApiStatusError(response.status, error);
  }
}

export async function clearSessionBestEffort(): Promise<void> {
  try {
    const client = createBrowserApiClient();
    await client.POST("/api/v1/auth/logout");
  } catch {
    // Xóa cookie tối đa trước khi chuyển về trang đăng nhập
  }
}
