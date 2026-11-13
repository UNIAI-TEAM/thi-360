/** Port luồng từ reap `fe-elearning/src/styles/accessibleThemeColors.ts` sang kiểu độc lập với MUI. */

export const WHITE_COLOR = "#ffffff";
export const TEXT_CONTRAST_RATIO = 4.5;
export const UI_CONTRAST_RATIO = 3;

export interface ThemeContrastInput {
  mode: "light" | "dark";
  primary: {
    main: string;
    light?: string;
    dark?: string;
    contrastText?: string;
  };
  background: {
    paper: string;
    default?: string;
  };
  text: {
    primary: string;
    secondary?: string;
  };
  common?: {
    white?: string;
    black?: string;
  };
}

export function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.trim().replace(/^#/, "");
  if (!/^[0-9A-Fa-f]{6}$/.test(clean)) {
    throw new Error(`Invalid hex color: ${hex}`);
  }
  return [
    parseInt(clean.slice(0, 2), 16),
    parseInt(clean.slice(2, 4), 16),
    parseInt(clean.slice(4, 6), 16),
  ];
}

function channelToLinear(channel255: number): number {
  const c = channel255 / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function relativeLuminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex);
  return 0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b);
}

export function getContrastRatio(foreground: string, background: string): number {
  const l1 = relativeLuminance(foreground);
  const l2 = relativeLuminance(background);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

export function safeContrastRatio(foreground: string, background: string): number {
  try {
    return getContrastRatio(foreground, background);
  } catch {
    return 0;
  }
}

function bestContrastColor(candidates: readonly string[], background: string): string {
  const first = candidates[0] ?? "#ffffff";
  return candidates.reduce((best, candidate) =>
    safeContrastRatio(candidate, background) > safeContrastRatio(best, background) ? candidate : best,
  first);
}

export function getReadablePrimary(
  theme: ThemeContrastInput,
  options: {
    background?: string;
    minContrast?: number;
  } = {},
): string {
  const background = options.background ?? theme.background.paper;
  const minContrast = options.minContrast ?? TEXT_CONTRAST_RATIO;
  const rawCandidates =
    theme.mode === "dark"
      ? [theme.primary.light, theme.primary.main, theme.primary.dark]
      : [theme.primary.dark, theme.primary.main, theme.primary.light];
  const primaryCandidates = rawCandidates.filter((c): c is string => typeof c === "string" && c.length > 0);
  const readablePrimary = primaryCandidates.find(
    (candidate) => safeContrastRatio(candidate, background) >= minContrast,
  );

  if (readablePrimary) return readablePrimary;

  const semanticFallbacks = [
    theme.text.primary,
    theme.common?.white ?? "#ffffff",
    theme.common?.black ?? "#000000",
  ];

  return bestContrastColor(semanticFallbacks, background);
}

export function getReadablePrimaryUi(theme: ThemeContrastInput, background?: string): string {
  return getReadablePrimary(theme, {
    background,
    minContrast: UI_CONTRAST_RATIO,
  });
}

export function getReadableOnColor(theme: ThemeContrastInput, background: string): string {
  const rawCandidates = [
    theme.primary.contrastText,
    theme.common?.white ?? "#ffffff",
    theme.common?.black ?? "#000000",
    theme.text.primary,
  ];
  const candidates = rawCandidates.filter((c): c is string => typeof c === "string" && c.length > 0);
  const configured = candidates.find(
    (candidate) => safeContrastRatio(candidate, background) >= TEXT_CONTRAST_RATIO,
  );

  return configured ?? bestContrastColor(candidates, background);
}

/**
 * Chọn chữ trên nền màu đặc theo spec §13.1 C: ưu tiên trắng (#ffffff) hoặc chữ tối (#11181c)
 * nếu đạt >= 4.5:1; ở dải độ sáng trung bình nơi #11181c chưa chạm 4.5:1, dùng #000000 (>= 4.58:1).
 */
export function pickOnColor(backgroundHex: string): string {
  const cWhite = safeContrastRatio("#ffffff", backgroundHex);
  const cInk = safeContrastRatio("#11181c", backgroundHex);
  const preferred = cWhite >= cInk ? "#ffffff" : "#11181c";
  if (safeContrastRatio(preferred, backgroundHex) >= TEXT_CONTRAST_RATIO) {
    return preferred;
  }
  const cBlack = safeContrastRatio("#000000", backgroundHex);
  return cBlack >= cWhite ? "#000000" : "#ffffff";
}

export function formatContrastRatio(ratio: number): string {
  return `${(Math.round(ratio * 10) / 10).toString().replace(".", ",")}:1`;
}
