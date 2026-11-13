import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";
import { redirectToLogin } from "@/lib/auth-redirect";
import type { Session } from "./types";

export const SESSION_QUERY_KEY = ["session", "me"] as const;
export type SessionLoader = () => Promise<Session>;

export async function loadSession(): Promise<Session> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me");
  if (!response.ok || !data) {
    throw new ApiStatusError(response.status, error?.message ?? "");
  }
  return data;
}

export async function refreshSession(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/refresh");
  if (!response.ok) {
    throw new ApiStatusError(response.status, error?.message ?? "");
  }
}

export async function logout(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/logout");
  if (!response.ok && response.status !== 401) {
    throw new ApiStatusError(response.status, error?.message ?? "");
  }
  redirectToLogin({ returnHere: false });
}
