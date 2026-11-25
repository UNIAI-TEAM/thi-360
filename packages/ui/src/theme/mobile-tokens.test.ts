import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  buildMobileAppearanceTokens,
  DEFAULT_PRESET_ID,
  deriveTokens,
  getContrastRatio,
  PRESET_PALETTES,
  TEXT_CONTRAST_RATIO,
  toMobilePalette,
  UI_CONTRAST_RATIO,
  type MobilePalette,
  type PresetId,
  type ThemeMode,
} from "./index";

const LOWERCASE_HEX = /^#[0-9a-f]{6}$/;

const PALETTE_KEYS = [
  "primaryFill",
  "onPrimary",
  "primaryText",
  "primaryContainer",
  "primaryContainerStrong",
  "accent",
  "onAccent",
  "bgPage",
  "bgSurface",
  "bgSubtle",
  "text",
  "textSecondary",
  "textMuted",
  "border",
  "borderStrong",
  "dangerText",
  "dangerBg",
] as const satisfies readonly (keyof MobilePalette)[];

const NEUTRAL_CSS_MAPPING = [
  ["bgPage", "--color-page"],
  ["bgSurface", "--color-surface"],
  ["bgSubtle", "--color-subtle"],
  ["text", "--color-text"],
  ["textSecondary", "--color-text-secondary"],
  ["textMuted", "--color-text-muted"],
  ["border", "--color-border"],
  ["borderStrong", "--color-border-strong"],
  ["dangerText", "--color-danger"],
  ["dangerBg", "--color-danger-bg"],
] as const satisfies readonly (readonly [keyof MobilePalette, string])[];

const TEXT_CONTRAST_PAIRS: readonly (readonly [keyof MobilePalette, keyof MobilePalette])[] = [
  ["primaryText", "primaryContainer"],
  ["primaryText", "primaryContainerStrong"],
  ["primaryText", "bgSurface"],
  ["primaryText", "bgPage"],
  ["primaryText", "bgSubtle"],
  ["onPrimary", "primaryFill"],
  ["onAccent", "accent"],
  ["text", "bgSurface"],
  ["text", "bgPage"],
  ["textSecondary", "bgSurface"],
  ["textSecondary", "bgSubtle"],
  ["textMuted", "bgSurface"],
  ["textMuted", "bgSubtle"],
  ["dangerText", "bgSurface"],
  ["dangerText", "dangerBg"],
];

const UI_CONTRAST_PAIRS: readonly (readonly [keyof MobilePalette, keyof MobilePalette])[] = [
  ["primaryFill", "bgSurface"],
  ["primaryFill", "bgPage"],
  ["primaryFill", "bgSubtle"],
];

const MODES: readonly ThemeMode[] = ["light", "dark"];

const ALL_CONFIGS: readonly { id: PresetId | "custom"; primary: string; accent: string }[] = [
  ...PRESET_PALETTES.map((preset) => ({
    id: preset.id,
    primary: preset.primary,
    accent: preset.accent,
  })),
  { id: "custom", primary: "#1d4ed8", accent: "#d97706" },
];

const tokensCss = readFileSync("src/styles/tokens.css", "utf8");

function parseCssBlock(selector: string): Map<string, string> {
  const start = tokensCss.indexOf(selector);
  expect(start, `missing selector ${selector}`).toBeGreaterThanOrEqual(0);
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

function cssToken(name: string, mode: ThemeMode): string {
  const value = mode === "dark" ? (darkCssOverrides.get(name) ?? lightCssVars.get(name)) : lightCssVars.get(name);
  expect(value, `missing token ${name} for ${mode}`).toBeDefined();
  return value!;
}

describe("thương hiệu", () => {
  it("ánh xạ đủ 17 màu #RRGGBB và khớp toàn bộ vai màu thương hiệu với deriveTokens cho 9 bộ cố định và custom", () => {
    expect(PRESET_PALETTES).toHaveLength(9);
    expect(ALL_CONFIGS).toHaveLength(10);

    for (const config of ALL_CONFIGS) {
      const built = buildMobileAppearanceTokens({
        version: 4,
        mode: "dark",
        presetId: config.id,
        primary: config.primary,
        accent: config.accent,
      });

      expect(built.version).toBe(4);
      expect(built.mode).toBe("dark");
      expect(built.presetId).toBe(config.id);

      for (const mode of MODES) {
        const derived = deriveTokens({ primary: config.primary, accent: config.accent, mode });
        const palette = toMobilePalette(derived);

        expect(built[mode]).toEqual(palette);
        expect(Object.keys(palette)).toEqual([...PALETTE_KEYS]);

        for (const key of PALETTE_KEYS) {
          expect(palette[key], `${config.id} [${mode}] ${key}`).toMatch(LOWERCASE_HEX);
        }

        expect(palette.primaryFill, `${config.id} [${mode}] primaryFill`).toBe(derived.primaryFill);
        expect(palette.onPrimary, `${config.id} [${mode}] onPrimary`).toBe(derived.onPrimary);
        expect(palette.primaryText, `${config.id} [${mode}] primaryText`).toBe(derived.primaryText);
        expect(palette.primaryContainer, `${config.id} [${mode}] primaryContainer`).toBe(derived.primary50);
        expect(palette.primaryContainerStrong, `${config.id} [${mode}] primaryContainerStrong`).toBe(
          derived.primary100,
        );
        expect(palette.accent, `${config.id} [${mode}] accent`).toBe(derived.accent500);
        expect(palette.onAccent, `${config.id} [${mode}] onAccent`).toBe(derived.onAccent);
      }
    }
  });
});

describe("trung tính", () => {
  it.each(MODES)("khớp token trung tính và màu lỗi từ tokens.css và giữ nguyên qua mọi bộ màu ở chế độ %s", (mode) => {
    const expected = Object.fromEntries(
      NEUTRAL_CSS_MAPPING.map(([field, cssVar]) => [field, cssToken(cssVar, mode)]),
    );

    for (const config of ALL_CONFIGS) {
      const palette = toMobilePalette(
        deriveTokens({ primary: config.primary, accent: config.accent, mode }),
      );
      const actual = Object.fromEntries(
        NEUTRAL_CSS_MAPPING.map(([field]) => [field, palette[field]]),
      );
      expect(actual, `${config.id} [${mode}]`).toEqual(expected);
    }
  });
});

describe("tương phản", () => {
  it.each(MODES)("mọi cặp chữ đạt >= 4.5:1 và thành phần không phải chữ đạt >= 3:1 cho cả 10 cấu hình ở chế độ %s", (mode) => {
    const failures: string[] = [];

    for (const config of ALL_CONFIGS) {
      const palette = toMobilePalette(
        deriveTokens({ primary: config.primary, accent: config.accent, mode }),
      );

      for (const [fgKey, bgKey] of TEXT_CONTRAST_PAIRS) {
        const ratio = getContrastRatio(palette[fgKey], palette[bgKey]);
        if (ratio < TEXT_CONTRAST_RATIO) {
          failures.push(
            `${config.id} [${mode}] text ${fgKey}(${palette[fgKey]}) on ${bgKey}(${palette[bgKey]}) = ${ratio.toFixed(2)}:1`,
          );
        }
      }

      for (const [fgKey, bgKey] of UI_CONTRAST_PAIRS) {
        const ratio = getContrastRatio(palette[fgKey], palette[bgKey]);
        if (ratio < UI_CONTRAST_RATIO) {
          failures.push(
            `${config.id} [${mode}] ui ${fgKey}(${palette[fgKey]}) on ${bgKey}(${palette[bgKey]}) = ${ratio.toFixed(2)}:1`,
          );
        }
      }
    }

    expect(failures).toEqual([]);
  });
});

describe("hợp đồng", () => {
  it("tệp contracts/mobile/appearance/tokens-thi360-green.json khớp kết quả mặc định", () => {
    const rawContract = readFileSync(
      "../../contracts/mobile/appearance/tokens-thi360-green.json",
      "utf8",
    );
    const contract = JSON.parse(rawContract) as Record<string, unknown>;
    const defaultPreset = PRESET_PALETTES.find((preset) => preset.id === DEFAULT_PRESET_ID)!;
    const expected = buildMobileAppearanceTokens({
      version: 0,
      mode: "light",
      presetId: DEFAULT_PRESET_ID,
      primary: defaultPreset.primary,
      accent: defaultPreset.accent,
    });

    expect(contract).toEqual(expected);
    for (const mode of MODES) {
      for (const key of PALETTE_KEYS) {
        expect(expected[mode][key]).toMatch(LOWERCASE_HEX);
      }
    }
  });
});
