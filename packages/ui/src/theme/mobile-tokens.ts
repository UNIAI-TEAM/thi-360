import type { PresetId, ThemeMode } from "./palettes";
import { deriveTokens, type DerivedTokens } from "./scale";

export interface MobilePalette {
  primaryFill: string;
  onPrimary: string;
  primaryText: string;
  primaryContainer: string;
  primaryContainerStrong: string;
  accent: string;
  onAccent: string;
  bgPage: string;
  bgSurface: string;
  bgSubtle: string;
  text: string;
  textSecondary: string;
  textMuted: string;
  border: string;
  borderStrong: string;
  dangerText: string;
  dangerBg: string;
}

export type Palette = MobilePalette;

export interface MobileAppearanceTokens {
  version: number;
  mode: ThemeMode;
  presetId: PresetId | "custom";
  light: MobilePalette;
  dark: MobilePalette;
}

export interface MobileAppearanceTokensInput {
  version: number;
  mode: ThemeMode;
  presetId: PresetId | "custom";
  primary: string;
  accent: string;
}

export function toMobilePalette(tokens: DerivedTokens): MobilePalette {
  return {
    primaryFill: tokens.primaryFill,
    onPrimary: tokens.onPrimary,
    primaryText: tokens.primaryText,
    primaryContainer: tokens.primary50,
    primaryContainerStrong: tokens.primary100,
    accent: tokens.accent500,
    onAccent: tokens.onAccent,
    bgPage: tokens.page,
    bgSurface: tokens.surface,
    bgSubtle: tokens.subtle,
    text: tokens.text,
    textSecondary: tokens.textSecondary,
    textMuted: tokens.textMuted,
    border: tokens.border,
    borderStrong: tokens.borderStrong,
    dangerText: tokens.danger,
    dangerBg: tokens.dangerBg,
  };
}

export function buildMobileAppearanceTokens(
  input: MobileAppearanceTokensInput,
): MobileAppearanceTokens {
  return {
    version: input.version,
    mode: input.mode,
    presetId: input.presetId,
    light: toMobilePalette(
      deriveTokens({ primary: input.primary, accent: input.accent, mode: "light" }),
    ),
    dark: toMobilePalette(
      deriveTokens({ primary: input.primary, accent: input.accent, mode: "dark" }),
    ),
  };
}
