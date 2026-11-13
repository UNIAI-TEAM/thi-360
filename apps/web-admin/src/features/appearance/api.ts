import {
  buildThemeVars,
  type AppearanceConfig,
  type AppearancePalette,
  type ThemeMode,
} from "@thi360/ui";
import { ApiStatusError } from "@/lib/api/query-client";

export interface AppearanceResponse {
  palette: AppearancePalette;
  mode: ThemeMode;
  version: number;
}

export interface UpdateAppearanceRequest {
  palette: AppearancePalette;
  mode: ThemeMode;
  version: number;
}

export interface ResetAppearanceRequest {
  version?: number;
}

export type AppearanceLoader = () => Promise<AppearanceResponse | null>;
export type AppearanceSaver = (payload: UpdateAppearanceRequest) => Promise<AppearanceResponse>;
export type AppearanceResetter = (payload?: ResetAppearanceRequest) => Promise<AppearanceResponse>;

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

async function parseError(response: Response): Promise<AppearanceApiError> {
  try {
    const body = (await response.json()) as { code?: unknown; message?: unknown };
    const message = typeof body.message === "string" ? body.message : "";
    const code = typeof body.code === "string" ? body.code : undefined;
    return new AppearanceApiError(response.status, message, code);
  } catch {
    return new AppearanceApiError(response.status);
  }
}

export async function loadAppearance(): Promise<AppearanceResponse | null> {
  const response = await fetch("/api/v1/appearance", {
    method: "GET",
    credentials: "include",
    cache: "no-store",
  });
  if (response.status === 204) return null;
  if (!response.ok) throw await parseError(response);
  return (await response.json()) as AppearanceResponse;
}

export async function saveAppearance(
  payload: UpdateAppearanceRequest,
): Promise<AppearanceResponse> {
  const headers = new Headers({ "Content-Type": "application/json" });
  const xsrf = readXsrfToken();
  if (xsrf) headers.set("x-xsrf-token", xsrf);

  const response = await fetch("/api/v1/admin/appearance", {
    method: "PUT",
    credentials: "include",
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw await parseError(response);
  return (await response.json()) as AppearanceResponse;
}

export async function resetAppearance(
  payload?: ResetAppearanceRequest,
): Promise<AppearanceResponse> {
  const headers = new Headers({ "Content-Type": "application/json" });
  const xsrf = readXsrfToken();
  if (xsrf) headers.set("x-xsrf-token", xsrf);

  const response = await fetch("/api/v1/admin/appearance/reset", {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify(payload ?? {}),
  });
  if (!response.ok) throw await parseError(response);
  return (await response.json()) as AppearanceResponse;
}

export function applyAppearanceToDocument(config: AppearanceConfig): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  root.dataset.theme = config.mode;
  for (const [name, value] of Object.entries(buildThemeVars(config))) {
    root.style.setProperty(name, value);
  }
}
