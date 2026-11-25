import { createApiClient } from "@thi360/api-client";
import {
  buildMobileAppearanceTokens,
  findPresetPalette,
  isValidHexColor,
  type MobileAppearanceTokens,
} from "@thi360/ui";

export const APPEARANCE_UNAVAILABLE_BODY = {
  code: "APPEARANCE_UNAVAILABLE",
  message: "Appearance configuration is unavailable.",
} as const;

const APPEARANCE_FETCH_TIMEOUT_MS = 2000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function resolveBackendApiBaseUrl(): string {
  const configured = process.env.THI360_API_URL?.trim().replace(/\/+$/, "");
  if (configured) return configured;
  return process.env.NODE_ENV === "production" ? "" : "http://localhost:8080";
}

function parseMobileAppearancePayload(raw: unknown): MobileAppearanceTokens | null {
  if (!isRecord(raw)) return null;

  const { version, mode, palette } = raw;
  if (typeof version !== "number" || !Number.isSafeInteger(version) || version < 0) {
    return null;
  }
  if (mode !== "light" && mode !== "dark") {
    return null;
  }
  if (!isRecord(palette) || typeof palette.presetId !== "string") {
    return null;
  }

  if (palette.presetId === "custom") {
    if (!isRecord(palette.custom)) return null;
    const { primary, accent } = palette.custom;
    if (typeof primary !== "string" || typeof accent !== "string") return null;
    if (!isValidHexColor(primary) || !isValidHexColor(accent)) return null;
    return buildMobileAppearanceTokens({
      version,
      mode,
      presetId: "custom",
      primary,
      accent,
    });
  }

  const preset = findPresetPalette(palette.presetId);
  if (!preset) return null;

  return buildMobileAppearanceTokens({
    version,
    mode,
    presetId: preset.id,
    primary: preset.primary,
    accent: preset.accent,
  });
}

export function formatAppearanceEtag(version: number): string {
  return `"${version}"`;
}

export function matchesIfNoneMatch(header: string | null, version: number): boolean {
  if (!header || header.trim().length === 0) return false;

  const versionToken = String(version);
  const quoted = formatAppearanceEtag(version);

  for (const rawCandidate of header.split(",")) {
    let candidate = rawCandidate.trim();
    if (!candidate) continue;
    if (candidate === "*") return true;
    if (candidate.startsWith("W/") || candidate.startsWith("w/")) {
      candidate = candidate.slice(2).trim();
    }
    if (candidate === quoted || candidate === versionToken) return true;
  }

  return false;
}

export async function loadMobileAppearanceTokens(): Promise<MobileAppearanceTokens | null> {
  const apiBase = resolveBackendApiBaseUrl();
  if (!apiBase) return null;

  try {
    const client = createApiClient(apiBase, {
      fetch: (request) =>
        fetch(request, {
          cache: "no-store",
          signal: AbortSignal.timeout(APPEARANCE_FETCH_TIMEOUT_MS),
        }),
    });
    const { data, response } = await client.GET("/api/v1/appearance");
    if (!response.ok || data === undefined) return null;
    return parseMobileAppearancePayload(data);
  } catch {
    return null;
  }
}
