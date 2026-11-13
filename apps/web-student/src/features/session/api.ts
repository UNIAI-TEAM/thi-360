import type { components } from "@thi360/api-client";
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
