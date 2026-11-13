export type ThemeMode = "light" | "dark";

export interface PresetPalette {
  readonly id: PresetId;
  readonly name: string;
  readonly primary: string;
  readonly accent: string;
}

export const PRESET_PALETTES = [
  { id: "thi360-green", name: "Thi 360 Xanh lá", primary: "#059669", accent: "#F59223" },
  { id: "emerald-gold", name: "Ngọc lục bảo & Vàng", primary: "#00695C", accent: "#FFD740" },
  { id: "charcoal-teal", name: "Xanh rêu & Xanh cổ vịt", primary: "#094A43", accent: "#009688" },
  { id: "navy-teal", name: "Xanh hải quân & Ngọc lam", primary: "#0F3C6E", accent: "#00B4A6" },
  { id: "blue-silver", name: "Xanh dương & Bạc", primary: "#0D47A1", accent: "#B0BEC5" },
  { id: "sky-orange", name: "Xanh da trời & Cam", primary: "#64B5F6", accent: "#FAA528" },
  { id: "indigo-amber", name: "Chàm & Hổ phách", primary: "#21255F", accent: "#FFB547" },
  { id: "indigo-coral", name: "Chàm & San hô", primary: "#283593", accent: "#FF7043" },
  { id: "slate-crimson", name: "Xám đá & Đỏ thẫm", primary: "#37474F", accent: "#D32F2F" },
] as const;

export type PresetId = (typeof PRESET_PALETTES)[number]["id"];

export const DEFAULT_PRESET_ID: PresetId = "thi360-green";

export const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

export interface CustomPaletteColors {
  primary: string;
  accent: string;
}

export interface AppearancePalette {
  presetId: PresetId | "custom";
  custom?: CustomPaletteColors;
}

export interface AppearanceConfig {
  palette: AppearancePalette;
  mode: ThemeMode;
  version?: number;
}

export const DEFAULT_APPEARANCE_CONFIG: AppearanceConfig = {
  palette: { presetId: DEFAULT_PRESET_ID },
  mode: "light",
  version: 0,
};

export function isValidHexColor(value: string): boolean {
  return HEX_COLOR_PATTERN.test(value.trim());
}

export function normalizeHexColor(value: string): string {
  return value.trim().toLowerCase();
}

export function findPresetPalette(id: string): PresetPalette | undefined {
  return PRESET_PALETTES.find((preset) => preset.id === id);
}

export function resolvePaletteColors(palette: AppearancePalette): {
  presetId: PresetId | "custom";
  primary: string;
  accent: string;
} {
  if (
    palette.presetId === "custom" &&
    palette.custom &&
    isValidHexColor(palette.custom.primary) &&
    isValidHexColor(palette.custom.accent)
  ) {
    return {
      presetId: "custom",
      primary: normalizeHexColor(palette.custom.primary),
      accent: normalizeHexColor(palette.custom.accent),
    };
  }
  const preset = findPresetPalette(palette.presetId) ?? PRESET_PALETTES[0];
  return {
    presetId: preset.id,
    primary: normalizeHexColor(preset.primary),
    accent: normalizeHexColor(preset.accent),
  };
}

export function parseAppearanceConfig(raw: unknown): AppearanceConfig {
  if (typeof raw !== "object" || raw === null) return DEFAULT_APPEARANCE_CONFIG;
  const candidate = raw as { mode?: unknown; version?: unknown; palette?: unknown };
  const mode: ThemeMode = candidate.mode === "dark" ? "dark" : "light";
  const version =
    typeof candidate.version === "number" && Number.isFinite(candidate.version)
      ? candidate.version
      : undefined;

  if (typeof candidate.palette !== "object" || candidate.palette === null) {
    return { ...DEFAULT_APPEARANCE_CONFIG, mode, ...(version !== undefined ? { version } : {}) };
  }

  const paletteObj = candidate.palette as { presetId?: unknown; custom?: unknown };
  const customObj =
    typeof paletteObj.custom === "object" && paletteObj.custom !== null
      ? (paletteObj.custom as { primary?: unknown; accent?: unknown })
      : null;

  const rawCustom =
    customObj &&
    typeof customObj.primary === "string" &&
    typeof customObj.accent === "string" &&
    isValidHexColor(customObj.primary) &&
    isValidHexColor(customObj.accent)
      ? {
          primary: customObj.primary.trim().toUpperCase(),
          accent: customObj.accent.trim().toUpperCase(),
        }
      : undefined;

  const rawPresetId =
    typeof paletteObj.presetId === "string" ? paletteObj.presetId : DEFAULT_PRESET_ID;
  if (rawPresetId === "custom" && rawCustom) {
    return {
      palette: { presetId: "custom", custom: rawCustom },
      mode,
      ...(version !== undefined ? { version } : {}),
    };
  }

  const preset = findPresetPalette(rawPresetId) ?? PRESET_PALETTES[0];
  return {
    palette: {
      presetId: preset.id,
      ...(rawCustom ? { custom: rawCustom } : {}),
    },
    mode,
    ...(version !== undefined ? { version } : {}),
  };
}
