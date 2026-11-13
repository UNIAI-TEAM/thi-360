import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  buildScale,
  buildThemeVars,
  checkPaletteWarnings,
  DEFAULT_APPEARANCE_CONFIG,
  deriveTokens,
  formatContrastRatio,
  getContrastRatio,
  parseAppearanceConfig,
  PRESET_PALETTES,
  themeStyleTag,
} from "./index";

const tokensCss = readFileSync("src/styles/tokens.css", "utf8");

function parseCssBlock(selector: string): Map<string, string> {
  const start = tokensCss.indexOf(selector);
  expect(start).toBeGreaterThanOrEqual(0);
  const open = tokensCss.indexOf("{", start);
  const close = tokensCss.indexOf("}", open);
  const body = tokensCss.slice(open + 1, close);
  const vars = new Map<string, string>();
  for (const match of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    vars.set(match[1]!, match[2]!.trim());
  }
  return vars;
}

const lightCssVars = parseCssBlock("@theme static");
const darkCssOverrides = parseCssBlock('[data-theme="dark"]');

function seededRandomColors(count: number, initialSeed = 20261113): string[] {
  let seed = initialSeed >>> 0;
  const colors: string[] = [];
  for (let i = 0; i < count; i++) {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    const hex = (seed & 0xffffff).toString(16).padStart(6, "0");
    colors.push(`#${hex}`);
  }
  return colors;
}

describe("palettes & deriveTokens", () => {
  it("có đúng 9 bộ phối màu cố định theo spec §13.1 A", () => {
    expect(PRESET_PALETTES.map((p) => p.id)).toEqual([
      "thi360-green",
      "emerald-gold",
      "charcoal-teal",
      "navy-teal",
      "blue-silver",
      "sky-orange",
      "indigo-amber",
      "indigo-coral",
      "slate-crimson",
    ]);
  });

  it("deriveTokens(thi360-green) tái tạo chính xác mọi biến màu của tokens.css (sáng và tối)", () => {
    const thi360 = PRESET_PALETTES[0];
    const light = deriveTokens({ primary: thi360.primary, accent: thi360.accent, mode: "light" });
    const dark = deriveTokens({ primary: thi360.primary, accent: thi360.accent, mode: "dark" });

    expect(light.primaryText).toBe("#047857");
    expect(light.primaryFill).toBe("#047857");
    expect(light.onAccent).toBe("#1a1a1f");
    expect(light.danger).toBe("#b91c1c");
    expect(dark.primaryText).toBe("#10b981");

    for (const [name, value] of Object.entries(light.vars)) {
      if (lightCssVars.has(name)) {
        expect(value, `light var ${name}`).toBe(lightCssVars.get(name));
      }
    }

    for (const [name, value] of Object.entries(dark.vars)) {
      const expected = darkCssOverrides.get(name) ?? lightCssVars.get(name);
      if (expected !== undefined) {
        expect(value, `dark var ${name}`).toBe(expected);
      }
    }
  });

  it("buildScale(hex) giữ màu gốc tại bậc 600 và sinh đủ dải 50–900", () => {
    const scale = buildScale("#0F3C6E");
    expect(scale[600]).toBe("#0f3c6e");
    expect(Object.keys(scale).map(Number)).toEqual([50, 100, 200, 300, 400, 500, 600, 700, 800, 900]);
  });

  it("9 bộ cố định và 50 màu ngẫu nhiên có onPrimary, onAccent >= 4.5:1 và link >= 4.5:1 trên trắng", () => {
    const randomPrimaries = seededRandomColors(50, 1113);
    const randomAccents = seededRandomColors(50, 4242);

    const cases = [
      ...PRESET_PALETTES.map((p) => ({ primary: p.primary, accent: p.accent, label: p.id })),
      ...randomPrimaries.map((primary, idx) => ({
        primary,
        accent: randomAccents[idx]!,
        label: `random-${idx}:${primary}/${randomAccents[idx]}`,
      })),
    ];

    for (const item of cases) {
      const light = deriveTokens({ primary: item.primary, accent: item.accent, mode: "light" });
      expect(
        getContrastRatio(light.onPrimary, light.primaryFill),
        `${item.label} onPrimary/primaryFill`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.onPrimary, light.primaryFillHover),
        `${item.label} onPrimary/primaryFillHover`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.onPrimary, light.primaryFillActive),
        `${item.label} onPrimary/primaryFillActive`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.onAccent, light.accent500),
        `${item.label} onAccent/accent500`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.link, "#ffffff"),
        `${item.label} link/white`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.primaryText, light.primary50),
        `${item.label} primaryText/primary50`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(light.primaryText, light.primary100),
        `${item.label} primaryText/primary100`,
      ).toBeGreaterThanOrEqual(4.5);

      const dark = deriveTokens({ primary: item.primary, accent: item.accent, mode: "dark" });
      expect(
        getContrastRatio(dark.primaryText, dark.surface),
        `${item.label} dark primaryText/surface`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(dark.primaryText, dark.subtle),
        `${item.label} dark primaryText/subtle`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(dark.primaryText, dark.primary50),
        `${item.label} dark primaryText/primary50`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(dark.primaryText, dark.primary100),
        `${item.label} dark primaryText/primary100`,
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        getContrastRatio(dark.primaryText, dark.page),
        `${item.label} dark primaryText/page`,
      ).toBeGreaterThanOrEqual(4.5);
      for (const stepToken of [dark.primary700, dark.primary800, dark.primary900]) {
        expect(
          getContrastRatio(stepToken, dark.surface),
          `${item.label} dark primary700..900/surface`,
        ).toBeGreaterThanOrEqual(4.5);
        expect(
          getContrastRatio(stepToken, dark.primary50),
          `${item.label} dark primary700..900/primary50`,
        ).toBeGreaterThanOrEqual(4.5);
      }
    }
  });
});

describe("checks & vars", () => {
  it("cảnh báo chữ tối trên nút, gần màu lỗi ΔE và hai màu quá giống nhau", () => {
    const brightWarnings = checkPaletteWarnings({ primary: "#FDE047", accent: "#0F3C6E" });
    expect(brightWarnings).toEqual([{ code: "PRIMARY_USES_DARK_TEXT", target: "primary" }]);

    const dangerWarnings = checkPaletteWarnings({ primary: "#0F3C6E", accent: "#D32F2F" });
    expect(dangerWarnings).toEqual([{ code: "NEAR_DANGER_COLOR", target: "accent" }]);

    const similarWarnings = checkPaletteWarnings({ primary: "#059669", accent: "#009688" });
    expect(similarWarnings).toEqual([{ code: "PRIMARY_ACCENT_SIMILAR", target: "both" }]);

    const clean = checkPaletteWarnings({ primary: "#059669", accent: "#F59223" });
    expect(clean).toEqual([]);
  });

  it("buildThemeVars & themeStyleTag sinh biến CSS đúng và parseAppearanceConfig chịu lỗi", () => {
    const vars = buildThemeVars({
      palette: { presetId: "custom", custom: { primary: "#0F3C6E", accent: "#00B4A6" } },
      mode: "dark",
    });
    expect(vars["--color-primary-600"]).toBe("#0f3c6e");
    expect(vars["--color-accent-500"]).toBe("#00b4a6");

    const styleLight = themeStyleTag({
      palette: { presetId: "navy-teal" },
      mode: "light",
    });
    const styleDark = themeStyleTag({
      palette: { presetId: "navy-teal" },
      mode: "dark",
    });
    expect(styleLight).toContain(':root[data-theme="light"]');
    expect(styleLight).toContain(':root[data-theme="dark"]');
    expect(styleLight).toContain("--color-page: #f7f8f9;");
    expect(styleLight).toContain("--color-page: #0f1418;");
    expect(styleLight).toContain("--color-primary-600: #0f3c6e;");
    expect(styleLight).toBe(styleDark);
    expect(themeStyleTag({ palette: { presetId: "navy-teal" }, mode: "light" })).toBe(styleLight);

    expect(formatContrastRatio(5.48)).toBe("5,5:1");
    expect(formatContrastRatio(5.48, "vi")).toBe("5,5:1");
    expect(formatContrastRatio(5.48, "en")).toBe("5.5:1");

    expect(parseAppearanceConfig(null)).toEqual(DEFAULT_APPEARANCE_CONFIG);
    expect(
      parseAppearanceConfig({
        palette: { presetId: "custom", custom: { primary: "bad", accent: "#000000" } },
        mode: "dark",
        version: 3,
      }),
    ).toEqual({
      palette: { presetId: "thi360-green" },
      mode: "dark",
      version: 3,
    });
  });
});
