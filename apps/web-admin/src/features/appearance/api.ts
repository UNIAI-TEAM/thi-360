import { readCookieValue } from "@thi360/web-kit/client";
import { resolveTheme, THEME_COOKIE_NAME } from "@thi360/web-kit/preferences";
import { createApiClient, type components } from "@thi360/api-client";
import { THEME_STYLE_ELEMENT_ID, themeStyleTag, type AppearanceConfig } from "@thi360/ui";
import { ApiStatusError } from "@/lib/api/query-client";

export type AppearanceResponse = components["schemas"]["AppearanceResponse"];
export type UpdateAppearanceRequest = components["schemas"]["UpdateAppearanceRequest"];
export type ResetAppearanceRequest = components["schemas"]["ResetAppearanceRequest"];
export type AppearanceLoader = () => Promise<AppearanceResponse | null>;
export type AppearanceSaver = (payload: UpdateAppearanceRequest) => Promise<AppearanceResponse>;
export type AppearanceResetter = (payload: ResetAppearanceRequest) => Promise<AppearanceResponse>;

export const APPEARANCE_QUERY_KEY = ["appearance"] as const;

export class AppearanceApiError extends ApiStatusError {
  readonly code?: string;

  constructor(status: number, message = "", code?: string) {
    super(status, message);
    this.name = "AppearanceApiError";
    this.code = code;
  }
}

function readXsrfToken(): string | null {
  if (typeof document === "undefined") return null;
  for (const part of document.cookie.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith("thi360_xsrf=")) {
      return decodeURIComponent(trimmed.slice("thi360_xsrf=".length));
    }
  }
  return null;
}

function toAppearanceApiError(status: number, error: unknown): AppearanceApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as { code?: unknown; message?: unknown };
    const message = typeof body.message === "string" ? body.message : "";
    const code = typeof body.code === "string" ? body.code : undefined;
    return new AppearanceApiError(status, message, code);
  }
  return new AppearanceApiError(status);
}

function createAppearanceClient(withXsrf = false) {
  const xsrf = withXsrf ? readXsrfToken() : null;
  return createApiClient(undefined, {
    credentials: "include",
    ...(xsrf ? { headers: { "x-xsrf-token": xsrf } } : {}),
  });
}

export async function loadAppearance(): Promise<AppearanceResponse | null> {
  const client = createAppearanceClient();
  const { data, error, response } = await client.GET("/api/v1/appearance", {
    cache: "no-store",
  });
  if (response.status === 204) return null;
  if (!response.ok || !data) throw toAppearanceApiError(response.status, error);
  return data;
}

export async function saveAppearance(
  payload: UpdateAppearanceRequest,
): Promise<AppearanceResponse> {
  const client = createAppearanceClient(true);
  const { data, error, response } = await client.PUT("/api/v1/admin/appearance", {
    body: payload,
  });
  if (!response.ok || !data) throw toAppearanceApiError(response.status, error);
  return data;
}

export async function resetAppearance(
  payload: ResetAppearanceRequest,
): Promise<AppearanceResponse> {
  const client = createAppearanceClient(true);
  const { data, error, response } = await client.POST("/api/v1/admin/appearance/reset", {
    body: payload,
  });
  if (!response.ok || !data) throw toAppearanceApiError(response.status, error);
  return data;
}

export function applyAppearanceToDocument(config: AppearanceConfig): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const themeCookie = readCookieValue(document.cookie, THEME_COOKIE_NAME);
  root.dataset.theme = resolveTheme(config.mode, themeCookie);
  let styleEl = document.getElementById(THEME_STYLE_ELEMENT_ID);
  if (!(styleEl instanceof HTMLStyleElement)) {
    styleEl = document.createElement("style");
    styleEl.id = THEME_STYLE_ELEMENT_ID;
    document.head.appendChild(styleEl);
  }
  styleEl.textContent = themeStyleTag(config);
}
