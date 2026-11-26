import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const HEX_COLOR_RE = /^#[0-9a-f]{6}$/;

const STATUS_COLOR_MAPPING = [
  ["success", "--color-success"],
  ["successBg", "--color-success-bg"],
  ["warning", "--color-warning"],
  ["warningBg", "--color-warning-bg"],
  ["info", "--color-info"],
  ["infoBg", "--color-info-bg"],
  ["dangerSolid", "--color-danger-solid"],
  ["onDanger", "--color-on-danger"],
] as const;

type StatusColorKey = (typeof STATUS_COLOR_MAPPING)[number][0];
type StatusColors = Record<StatusColorKey, string>;
type StatusColorsContract = { light: StatusColors; dark: StatusColors };

const EXPECTED_KEYS = STATUS_COLOR_MAPPING.map(([key]) => key);
const EXPECTED_MODES = ["light", "dark"] as const;

function parseCssBlock(cssText: string, selector: string): Map<string, string> {
  const start = cssText.indexOf(selector);
  if (start < 0) {
    throw new Error(`Missing CSS selector block: ${selector}`);
  }
  const open = cssText.indexOf("{", start);
  if (open < 0) {
    throw new Error(`Unopened CSS selector block: ${selector}`);
  }

  let depth = 0;
  let close = -1;
  for (let i = open; i < cssText.length; i += 1) {
    if (cssText[i] === "{") depth += 1;
    else if (cssText[i] === "}") {
      depth -= 1;
      if (depth === 0) {
        close = i;
        break;
      }
    }
  }
  if (close < 0) {
    throw new Error(`Unclosed CSS selector block: ${selector}`);
  }

  const body = cssText.slice(open + 1, close);
  const vars = new Map<string, string>();
  for (const match of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    vars.set(match[1]!, match[2]!.trim());
  }
  return vars;
}

function expectedFromTokensCss(cssText: string): StatusColorsContract {
  const lightVars = parseCssBlock(cssText, "@theme static");
  const darkVars = parseCssBlock(cssText, '[data-theme="dark"]');

  const buildMode = (mode: "light" | "dark"): StatusColors => {
    const result = {} as StatusColors;
    for (const [key, cssVar] of STATUS_COLOR_MAPPING) {
      const value = mode === "dark" ? (darkVars.get(cssVar) ?? lightVars.get(cssVar)) : lightVars.get(cssVar);
      if (!value) {
        throw new Error(`Missing token ${cssVar} for mode ${mode}`);
      }
      result[key] = value;
    }
    return result;
  };

  return {
    light: buildMode("light"),
    dark: buildMode("dark"),
  };
}

function validateStatusColorsContract(contract: unknown, cssText: string): string[] {
  const errors: string[] = [];
  if (typeof contract !== "object" || contract === null || Array.isArray(contract)) {
    return ["Contract must be a JSON object with light and dark modes."];
  }

  const record = contract as Record<string, unknown>;
  const topKeys = Object.keys(record).sort();
  if (topKeys.join(",") !== [...EXPECTED_MODES].sort().join(",")) {
    errors.push(`Top-level keys mismatch: expected [${EXPECTED_MODES.join(", ")}], got [${topKeys.join(", ")}]`);
    return errors;
  }

  const expected = expectedFromTokensCss(cssText);

  for (const mode of EXPECTED_MODES) {
    const modeObj = record[mode];
    if (typeof modeObj !== "object" || modeObj === null || Array.isArray(modeObj)) {
      errors.push(`${mode} must be an object`);
      continue;
    }

    const modeRecord = modeObj as Record<string, unknown>;
    const actualKeys = Object.keys(modeRecord);
    for (const key of EXPECTED_KEYS) {
      if (!(key in modeRecord)) {
        errors.push(`${mode}.${key} is missing`);
      }
    }
    for (const key of actualKeys) {
      if (!EXPECTED_KEYS.includes(key as StatusColorKey)) {
        errors.push(`${mode}.${key} is an unexpected extra key`);
      }
    }

    for (const key of EXPECTED_KEYS) {
      const val = modeRecord[key];
      if (typeof val !== "string" || !HEX_COLOR_RE.test(val)) {
        errors.push(`${mode}.${key} must match #rrggbb hex format, got ${JSON.stringify(val)}`);
        continue;
      }
      if (val !== expected[mode][key]) {
        errors.push(`${mode}.${key} drifted: contract=${val}, tokens.css=${expected[mode][key]}`);
      }
    }
  }

  return errors;
}

const tokensCss = readFileSync("src/styles/tokens.css", "utf8");
const rawContract = readFileSync("../../contracts/mobile/appearance/status-colors.json", "utf8");
const contract = JSON.parse(rawContract) as StatusColorsContract;

describe("contracts/mobile/appearance/status-colors.json", () => {
  it("matchesTokensCss", () => {
    expect(validateStatusColorsContract(contract, tokensCss)).toEqual([]);
    expect(Object.keys(contract)).toEqual(["light", "dark"]);
    expect(Object.keys(contract.light)).toEqual(EXPECTED_KEYS);
    expect(Object.keys(contract.dark)).toEqual(EXPECTED_KEYS);
    expect(contract.dark.dangerSolid).toBe(contract.light.dangerSolid);
    expect(contract.dark.dangerSolid).toBe("#dc2626");
    expect(contract.dark.onDanger).toBe(contract.light.onDanger);
    expect(contract.dark.onDanger).toBe("#ffffff");
  });

  it("failsOnDrift", () => {
    for (const [, cssVar] of STATUS_COLOR_MAPPING) {
      const mutatedLight = tokensCss.replace(new RegExp(`(${cssVar}:\\s*)#[0-9a-f]{6}`), "$1#123456");
      const errors = validateStatusColorsContract(contract, mutatedLight);
      expect(errors.length, `expected drift failure when light ${cssVar} changes`).toBeGreaterThan(0);
    }

    for (const cssVar of [
      "--color-success",
      "--color-success-bg",
      "--color-warning",
      "--color-warning-bg",
      "--color-info",
      "--color-info-bg",
    ]) {
      const darkIndex = tokensCss.indexOf('[data-theme="dark"]');
      const beforeDark = tokensCss.slice(0, darkIndex);
      const darkSection = tokensCss.slice(darkIndex);
      const mutatedDark =
        beforeDark + darkSection.replace(new RegExp(`(${cssVar}:\\s*)#[0-9a-f]{6}`), "$1#654321");
      const errors = validateStatusColorsContract(contract, mutatedDark);
      expect(errors.length, `expected drift failure when dark ${cssVar} changes`).toBeGreaterThan(0);
    }

    for (const darkAddedVar of ["--color-danger-solid", "--color-on-danger"]) {
      const darkIndex = tokensCss.indexOf('[data-theme="dark"]');
      const darkOpen = tokensCss.indexOf("{", darkIndex);
      const mutatedDarkAdded =
        tokensCss.slice(0, darkOpen + 1) + `\n  ${darkAddedVar}: #abcdef;` + tokensCss.slice(darkOpen + 1);
      const errors = validateStatusColorsContract(contract, mutatedDarkAdded);
      expect(errors.length, `expected drift failure when dark overrides ${darkAddedVar}`).toBeGreaterThan(0);
    }

    const missingKey = structuredClone(contract) as { light: Partial<StatusColors>; dark: StatusColors };
    delete missingKey.light.info;
    expect(validateStatusColorsContract(missingKey, tokensCss).join("\n")).toContain("light.info is missing");

    const extraKey = {
      light: { ...contract.light, extraTone: "#000000" },
      dark: { ...contract.dark },
    };
    expect(validateStatusColorsContract(extraKey, tokensCss).join("\n")).toContain("extraTone");

    const badFormat = {
      light: { ...contract.light, success: "#047" },
      dark: { ...contract.dark },
    };
    expect(validateStatusColorsContract(badFormat, tokensCss).join("\n")).toContain("hex format");
  });
});
