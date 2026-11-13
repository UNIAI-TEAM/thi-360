import { getContrastRatio, hexToRgb, pickOnColor, TEXT_CONTRAST_RATIO } from "./contrast";
import { normalizeHexColor, type ThemeMode } from "./palettes";

export const SCALE_STEPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900] as const;
export type ScaleStep = (typeof SCALE_STEPS)[number];
export type ColorScale = Record<ScaleStep, string>;

export interface OklchColor {
  L: number;
  C: number;
  H: number;
}

export interface OklabColor {
  L: number;
  a: number;
  b: number;
}

export interface DeriveTokensInput {
  primary: string;
  accent: string;
  mode?: ThemeMode;
}

export interface DerivedTokens {
  mode: ThemeMode;
  scale: ColorScale;
  primary50: string;
  primary100: string;
  primary200: string;
  primary500: string;
  primary600: string;
  primary700: string;
  primary800: string;
  primary900: string;
  onPrimary: string;
  primaryText: string;
  primaryFill: string;
  primaryFillHover: string;
  primaryFillActive: string;
  link: string;
  accent500: string;
  accent700: string;
  onAccent: string;
  borderPrimary: string;
  heroTint: string;
  amber500: string;
  yellow400: string;
  gold400: string;
  text: string;
  textSecondary: string;
  textMuted: string;
  textPlaceholder: string;
  page: string;
  surface: string;
  subtle: string;
  tableHead: string;
  border: string;
  borderStrong: string;
  overlay: string;
  success: string;
  successBg: string;
  warning: string;
  warningBg: string;
  danger: string;
  dangerSolid: string;
  dangerSolidHover: string;
  dangerBg: string;
  info: string;
  infoBg: string;
  neutral: string;
  neutralBg: string;
  focusRing: string;
  shadowPrimary: string;
  vars: Record<`--${string}`, string>;
}

const LIGHT_NEUTRAL_AND_STATUS = {
  amber500: "#f59e0b",
  yellow400: "#ffd836",
  gold400: "#fbbf24",
  text: "#11181c",
  textSecondary: "#3a3a42",
  textMuted: "#6b6b72",
  textPlaceholder: "#6b6b72",
  page: "#f7f8f9",
  surface: "#ffffff",
  subtle: "#f1f2f4",
  tableHead: "#fafafb",
  border: "#e8e8ec",
  borderStrong: "#d4d4d8",
  overlay: "rgb(17 24 28 / 0.5)",
  success: "#047857",
  successBg: "#ecfdf5",
  warning: "#b45309",
  warningBg: "#fffbeb",
  danger: "#b91c1c",
  dangerSolid: "#dc2626",
  dangerSolidHover: "#b91c1c",
  dangerBg: "#fef2f2",
  info: "#0e7490",
  infoBg: "#ecfeff",
  neutral: "#54545a",
  neutralBg: "#f1f2f4",
} as const;

const DARK_NEUTRAL_AND_STATUS = {
  amber500: "#f59e0b",
  yellow400: "#ffd836",
  gold400: "#fbbf24",
  text: "#e6eaed",
  textSecondary: "#c5ccd2",
  textMuted: "#a9b2ba",
  textPlaceholder: "#83909a",
  page: "#0f1418",
  surface: "#171d22",
  subtle: "#1e262c",
  tableHead: "#1a2126",
  border: "#2a3238",
  borderStrong: "#3a444c",
  overlay: "rgb(0 0 0 / 0.6)",
  success: "#34d399",
  successBg: "#0f2a21",
  warning: "#fbbf24",
  warningBg: "#2b2210",
  danger: "#f87171",
  dangerSolid: "#dc2626",
  dangerSolidHover: "#b91c1c",
  dangerBg: "#2e1517",
  info: "#22d3ee",
  infoBg: "#0f2a30",
  neutral: "#a9b2ba",
  neutralBg: "#1e262c",
} as const;

const THI360_GREEN_PRIMARY = "#059669";
const THI360_GREEN_ACCENT = "#f59223";

function rgbToHex(r: number, g: number, b: number): string {
  return (
    "#" +
    [r, g, b]
      .map((channel) =>
        Math.max(0, Math.min(255, Math.round(channel * 255)))
          .toString(16)
          .padStart(2, "0"),
      )
      .join("")
  );
}

function toLinear(c: number): number {
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function toSrgb(c: number): number {
  return c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055;
}

export function hexToOklab(hex: string): OklabColor {
  const [r255, g255, b255] = hexToRgb(hex);
  const r = toLinear(r255 / 255);
  const g = toLinear(g255 / 255);
  const b = toLinear(b255 / 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return {
    L: 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    a: 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    b: 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  };
}

export function hexToOklch(hex: string): OklchColor {
  const { L, a, b } = hexToOklab(hex);
  return {
    L,
    C: Math.hypot(a, b),
    H: Math.atan2(b, a),
  };
}

/** Khoảng cách màu cảm nhận trong không gian OKLab (nhân 100 để tương đương thang CIE ΔE). */
export function deltaEOklab(hexA: string, hexB: string): number {
  const a = hexToOklab(hexA);
  const b = hexToOklab(hexB);
  return 100 * Math.hypot(a.L - b.L, a.a - b.a, a.b - b.b);
}

function oklchToLinearRgb(L: number, C: number, H: number): [number, number, number] {
  const a = C * Math.cos(H);
  const b = C * Math.sin(H);
  const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
  const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
  const s_ = L - 0.0894841775 * a - 1.291485548 * b;
  const l = l_ ** 3;
  const m = m_ ** 3;
  const s = s_ ** 3;
  return [
    +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

function inSrgbGamut([r, g, b]: [number, number, number]): boolean {
  return (
    r >= -1e-6 && r <= 1 + 1e-6 && g >= -1e-6 && g <= 1 + 1e-6 && b >= -1e-6 && b <= 1 + 1e-6
  );
}

export function oklchToHex(L: number, C: number, H: number): string {
  if (L <= 0) return "#000000";
  if (L >= 1) return "#ffffff";
  let rgb = oklchToLinearRgb(L, C, H);
  if (!inSrgbGamut(rgb)) {
    let lo = 0;
    let hi = C;
    for (let i = 0; i < 24; i++) {
      const mid = (lo + hi) / 2;
      if (inSrgbGamut(oklchToLinearRgb(L, mid, H))) lo = mid;
      else hi = mid;
    }
    rgb = oklchToLinearRgb(L, lo, H);
  }
  return rgbToHex(
    toSrgb(Math.max(0, Math.min(1, rgb[0]))),
    toSrgb(Math.max(0, Math.min(1, rgb[1]))),
    toSrgb(Math.max(0, Math.min(1, rgb[2]))),
  );
}

/**
 * Sinh dải màu 50–900 trong không gian OKLCH với màu gốc ở bậc 600.
 */
export function buildScale(hex: string): ColorScale {
  const base = normalizeHexColor(hex);
  const { L, C, H } = hexToOklch(base);
  const Ltop = Math.max(0.978, L + (1 - L) * 0.92);
  const Lbot = Math.min(0.24, L * 0.6);

  return {
    50: oklchToHex(L + (Ltop - L) * 0.95, C * 0.16, H),
    100: oklchToHex(L + (Ltop - L) * 0.88, C * 0.32, H),
    200: oklchToHex(L + (Ltop - L) * 0.76, C * 0.52, H),
    300: oklchToHex(L + (Ltop - L) * 0.6, C * 0.72, H),
    400: oklchToHex(L + (Ltop - L) * 0.42, C * 0.88, H),
    500: oklchToHex(L + (Ltop - L) * 0.24, C, H),
    600: base,
    700: oklchToHex(L - (L - Lbot) * 0.28, C * 0.95, H),
    800: oklchToHex(L - (L - Lbot) * 0.58, C * 0.88, H),
    900: oklchToHex(L - (L - Lbot) * 0.92, C * 0.78, H),
  };
}

function ensureReadableDarkOn(
  candidates: readonly string[],
  backgrounds: readonly string[],
): string {
  const fits = (fg: string) =>
    backgrounds.every((bg) => getContrastRatio(fg, bg) >= TEXT_CONTRAST_RATIO);
  for (const candidate of candidates) {
    if (fits(candidate)) return candidate;
  }
  const seed = candidates[candidates.length - 1] ?? "#11181c";
  const { L, C, H } = hexToOklch(seed);
  for (let l = L - 0.03; l >= 0; l -= 0.03) {
    const darker = oklchToHex(Math.max(0, l), C, H);
    if (fits(darker)) return darker;
  }
  return "#11181c";
}

function ensureReadableLightOn(
  candidates: readonly string[],
  backgrounds: readonly string[],
): string {
  const fits = (fg: string) =>
    backgrounds.every((bg) => getContrastRatio(fg, bg) >= TEXT_CONTRAST_RATIO);
  for (const candidate of candidates) {
    if (fits(candidate)) return candidate;
  }
  const seed = candidates[0] ?? "#e6eaed";
  const { L, C, H } = hexToOklch(seed);
  for (let l = L + 0.03; l <= 1; l += 0.03) {
    const lighter = oklchToHex(Math.min(1, l), C * Math.max(0.2, 1 - (l - L) * 0.5), H);
    if (fits(lighter)) return lighter;
  }
  return "#e6eaed";
}

function toVarsRecord(tokens: Omit<DerivedTokens, "mode" | "scale" | "focusRing" | "vars">): Record<`--${string}`, string> {
  return {
    "--color-primary-50": tokens.primary50,
    "--color-primary-100": tokens.primary100,
    "--color-primary-200": tokens.primary200,
    "--color-primary-500": tokens.primary500,
    "--color-primary-600": tokens.primary600,
    "--color-primary-700": tokens.primary700,
    "--color-primary-800": tokens.primary800,
    "--color-primary-900": tokens.primary900,
    "--color-on-primary": tokens.onPrimary,
    "--color-primary-text": tokens.primaryText,
    "--color-primary-fill": tokens.primaryFill,
    "--color-primary-fill-hover": tokens.primaryFillHover,
    "--color-primary-fill-active": tokens.primaryFillActive,
    "--color-link": tokens.link,
    "--color-accent-500": tokens.accent500,
    "--color-accent-700": tokens.accent700,
    "--color-on-accent": tokens.onAccent,
    "--color-amber-500": tokens.amber500,
    "--color-yellow-400": tokens.yellow400,
    "--color-gold-400": tokens.gold400,
    "--color-hero-tint": tokens.heroTint,
    "--color-text": tokens.text,
    "--color-text-secondary": tokens.textSecondary,
    "--color-text-muted": tokens.textMuted,
    "--color-text-placeholder": tokens.textPlaceholder,
    "--color-page": tokens.page,
    "--color-surface": tokens.surface,
    "--color-subtle": tokens.subtle,
    "--color-table-head": tokens.tableHead,
    "--color-border": tokens.border,
    "--color-border-strong": tokens.borderStrong,
    "--color-border-primary": tokens.borderPrimary,
    "--color-overlay": tokens.overlay,
    "--color-success": tokens.success,
    "--color-success-bg": tokens.successBg,
    "--color-warning": tokens.warning,
    "--color-warning-bg": tokens.warningBg,
    "--color-danger": tokens.danger,
    "--color-danger-solid": tokens.dangerSolid,
    "--color-danger-solid-hover": tokens.dangerSolidHover,
    "--color-danger-bg": tokens.dangerBg,
    "--color-info": tokens.info,
    "--color-info-bg": tokens.infoBg,
    "--color-neutral": tokens.neutral,
    "--color-neutral-bg": tokens.neutralBg,
    "--shadow-primary": tokens.shadowPrimary,
  };
}

function deriveThi360Default(mode: ThemeMode): DerivedTokens {
  const scale: ColorScale = {
    50: "#ecfdf5",
    100: "#dcfce7",
    200: "#a7e3c2",
    300: "#6ee7b7",
    400: "#34d399",
    500: "#10b981",
    600: "#059669",
    700: "#047857",
    800: "#065f46",
    900: "#064e3b",
  };
  const base =
    mode === "light"
      ? {
          ...LIGHT_NEUTRAL_AND_STATUS,
          primary50: "#ecfdf5",
          primary100: "#dcfce7",
          primary200: "#a7e3c2",
          primary500: "#10b981",
          primary600: "#059669",
          primary700: "#047857",
          primary800: "#065f46",
          primary900: "#064e3b",
          onPrimary: "#ffffff",
          primaryText: "#047857",
          primaryFill: "#047857",
          primaryFillHover: "#065f46",
          primaryFillActive: "#064e3b",
          link: "#047857",
          accent500: "#f59223",
          accent700: "#b45309",
          onAccent: "#1a1a1f",
          borderPrimary: "#d4eee0",
          heroTint: "#f0fdf4",
          shadowPrimary: "0 8px 20px rgb(5 150 105 / 0.25)",
        }
      : {
          ...DARK_NEUTRAL_AND_STATUS,
          primary50: "#0a1f19",
          primary100: "#0c1f18",
          primary200: "#101f1a",
          primary500: "#10b981",
          primary600: "#059669",
          primary700: "#6ee7b7",
          primary800: "#6ee7b7",
          primary900: "#a7f3d0",
          onPrimary: "#ffffff",
          primaryText: "#10b981",
          primaryFill: "#047857",
          primaryFillHover: "#065f46",
          primaryFillActive: "#064e3b",
          link: "#10b981",
          accent500: "#f59223",
          accent700: "#b45309",
          onAccent: "#1a1a1f",
          borderPrimary: "#1f4a3a",
          heroTint: "#f0fdf4",
          shadowPrimary: "0 8px 20px rgb(5 150 105 / 0.25)",
        };

  return {
    mode,
    scale,
    ...base,
    focusRing: "rgb(5 150 105 / 0.15)",
    vars: toVarsRecord(base),
  };
}

/**
 * Suy ra toàn bộ token giao diện từ hai màu gốc (chính, nhấn) và chế độ sáng/tối (spec §13.1 C).
 * Bộ mặc định `thi360-green` tái tạo chính xác giá trị của `packages/ui/src/styles/tokens.css`.
 */
export function deriveTokens({
  primary,
  accent,
  mode = "light",
}: DeriveTokensInput): DerivedTokens {
  const normPrimary = normalizeHexColor(primary);
  const normAccent = normalizeHexColor(accent);

  if (normPrimary === THI360_GREEN_PRIMARY && normAccent === THI360_GREEN_ACCENT) {
    return deriveThi360Default(mode);
  }

  const scale = buildScale(normPrimary);
  const { C, H } = hexToOklch(normPrimary);
  const [pr, pg, pb] = hexToRgb(normPrimary);
  const shadowPrimary = `0 8px 20px rgb(${pr} ${pg} ${pb} / 0.25)`;
  const focusRing = `rgb(${pr} ${pg} ${pb} / 0.15)`;

  const primaryFill = scale[700];
  const onPrimary = pickOnColor(primaryFill);
  let primaryFillHover = scale[800];
  let primaryFillActive = scale[900];

  if (onPrimary !== "#ffffff") {
    if (getContrastRatio(onPrimary, primaryFillHover) < TEXT_CONTRAST_RATIO) {
      const fillOklch = hexToOklch(primaryFill);
      primaryFillHover = oklchToHex(Math.min(1, fillOklch.L + 0.05), fillOklch.C, fillOklch.H);
      primaryFillActive = oklchToHex(Math.min(1, fillOklch.L + 0.1), fillOklch.C, fillOklch.H);
    } else if (getContrastRatio(onPrimary, primaryFillActive) < TEXT_CONTRAST_RATIO) {
      const hoverOklch = hexToOklch(primaryFillHover);
      primaryFillActive = oklchToHex(
        Math.min(1, hoverOklch.L + 0.04),
        hoverOklch.C,
        hoverOklch.H,
      );
    }
  }

  const accent500 = normAccent;
  const onAccent = pickOnColor(accent500);
  const accentOklch = hexToOklch(accent500);
  const accentDarkCandidate = oklchToHex(
    Math.min(0.55, accentOklch.L * 0.72),
    accentOklch.C * 0.92,
    accentOklch.H,
  );

  if (mode === "light") {
    const neutrals = LIGHT_NEUTRAL_AND_STATUS;
    const link = ensureReadableDarkOn([scale[700], scale[800], scale[900]], [neutrals.surface]);
    const primaryText = ensureReadableDarkOn(
      [scale[700], scale[800], scale[900]],
      [scale[50], scale[100], neutrals.surface, neutrals.page, neutrals.subtle],
    );
    const primary700 = ensureReadableDarkOn(
      [scale[700], scale[800], scale[900]],
      [neutrals.surface, scale[50]],
    );
    const primary800 = ensureReadableDarkOn(
      [scale[800], scale[900]],
      [neutrals.surface, scale[50], scale[200]],
    );
    const primary900 = ensureReadableDarkOn([scale[900]], [neutrals.surface]);
    const accent700 = ensureReadableDarkOn([accentDarkCandidate], [neutrals.surface]);
    const borderPrimary = oklchToHex(0.93, C * 0.25, H);
    const heroTint = oklchToHex(0.985, C * 0.12, H);

    const base = {
      ...neutrals,
      primary50: scale[50],
      primary100: scale[100],
      primary200: scale[200],
      primary500: scale[500],
      primary600: scale[600],
      primary700,
      primary800,
      primary900,
      onPrimary,
      primaryText,
      primaryFill,
      primaryFillHover,
      primaryFillActive,
      link,
      accent500,
      accent700,
      onAccent,
      borderPrimary,
      heroTint,
      shadowPrimary,
    };

    return { mode, scale, ...base, focusRing, vars: toVarsRecord(base) };
  }

  const neutrals = DARK_NEUTRAL_AND_STATUS;
  const primary50 = oklchToHex(0.22, C * 0.35, H);
  const primary100 = oklchToHex(0.23, C * 0.38, H);
  const primary200 = oklchToHex(0.24, C * 0.4, H);
  const borderPrimary = oklchToHex(0.37, C * 0.45, H);
  const heroTint = oklchToHex(0.18, C * 0.2, H);
  const primaryText = ensureReadableLightOn(
    [scale[500], scale[400], scale[300], scale[200]],
    [neutrals.surface, neutrals.page, neutrals.subtle, primary50, primary100],
  );
  const primary700 = ensureReadableLightOn(
    [scale[300], scale[200], scale[100]],
    [neutrals.surface, primary50],
  );
  const primary800 = ensureReadableLightOn(
    [scale[300], scale[200], scale[100]],
    [neutrals.surface, primary50, primary200],
  );
  const primary900 = ensureReadableLightOn([scale[200], scale[100], scale[50]], [neutrals.surface]);
  const accent700 = ensureReadableLightOn(
    [
      oklchToHex(Math.max(0.76, accentOklch.L), accentOklch.C * 0.85, accentOklch.H),
      oklchToHex(0.86, accentOklch.C * 0.65, accentOklch.H),
    ],
    [neutrals.surface],
  );

  const base = {
    ...neutrals,
    primary50,
    primary100,
    primary200,
    primary500: scale[500],
    primary600: scale[600],
    primary700,
    primary800,
    primary900,
    onPrimary,
    primaryText,
    primaryFill,
    primaryFillHover,
    primaryFillActive,
    link: primaryText,
    accent500,
    accent700,
    onAccent,
    borderPrimary,
    heroTint,
    shadowPrimary,
  };

  return { mode, scale, ...base, focusRing, vars: toVarsRecord(base) };
}
