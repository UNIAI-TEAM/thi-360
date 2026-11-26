import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { expect, test } from "@playwright/test";

interface FormulaEntryRef {
  sheetKey: string | null;
}

interface FormulasJsonRef {
  entries: FormulaEntryRef[];
}

const REPO_ROOT = path.resolve(import.meta.dirname, "../..");
const FORMULAS_PATH = path.join(REPO_ROOT, "contracts/mobile/rich-text/formulas.json");
const WEB_RENDERS_DIR = path.join(REPO_ROOT, "contracts/mobile/rich-text/web-renders");
const SHEETS_HTML_DIR = path.join(REPO_ROOT, "e2e/test-results/formula-sheets/html");

function listPngRelativePaths(rootDir: string): string[] {
  const results: string[] = [];
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(".png")) {
        results.push(path.relative(rootDir, full).replace(/\\/g, "/"));
      }
    }
  };
  walk(rootDir);
  return results.sort();
}

test("captures web reference PNGs for every renderable block sheetKey", async ({ page }) => {
  const formulas = JSON.parse(readFileSync(FORMULAS_PATH, "utf8")) as FormulasJsonRef;
  const sheets = new Map<string, string[]>();
  const expectedSheetKeys: string[] = [];

  for (const entry of formulas.entries) {
    if (entry.sheetKey === null) continue;
    expectedSheetKeys.push(entry.sheetKey);
    const [sheetName] = entry.sheetKey.split("/");
    if (!sheetName) {
      throw new Error(`Invalid sheetKey: ${entry.sheetKey}`);
    }
    const list = sheets.get(sheetName) ?? [];
    list.push(entry.sheetKey);
    sheets.set(sheetName, list);
  }

  expect(sheets.size).toBe(226);

  // Clean old rendered sheet subdirectories while keeping manifest.json and README.md intact.
  mkdirSync(WEB_RENDERS_DIR, { recursive: true });
  for (const entry of readdirSync(WEB_RENDERS_DIR, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      rmSync(path.join(WEB_RENDERS_DIR, entry.name), { recursive: true, force: true });
    }
  }

  for (const [sheetName, keys] of sheets) {
    const htmlPath = path.join(SHEETS_HTML_DIR, `${sheetName}.html`);
    expect(existsSync(htmlPath), `Missing generated sheet HTML: ${htmlPath}`).toBe(true);

    await page.goto(pathToFileURL(htmlPath).href, { waitUntil: "load" });
    await page.evaluate(async () => {
      await document.fonts.ready;
    });

    await expect(page.locator(".math-error"), `math-error found on sheet ${sheetName}`).toHaveCount(0);

    const sheetOutDir = path.join(WEB_RENDERS_DIR, sheetName);
    mkdirSync(sheetOutDir, { recursive: true });

    const tiles = page.locator("[data-sheet-key]");
    await expect(tiles, `Tile count mismatch on sheet ${sheetName}`).toHaveCount(keys.length);

    for (const sheetKey of keys) {
      const tile = page.locator(`[data-sheet-key="${sheetKey}"]`);
      const box = await tile.boundingBox();
      expect(box, `Missing boundingBox for ${sheetKey}`).not.toBeNull();
      expect(Math.round(box!.width), `Expected 390px tile width for ${sheetKey}`).toBe(390);
      expect(box!.height, `Expected positive height for ${sheetKey}`).toBeGreaterThan(0);

      const fileName = `${sheetKey.split("/")[1]!}.png`;
      await tile.screenshot({
        path: path.join(sheetOutDir, fileName),
        animations: "disabled",
      });
    }
  }

  const sheetDirs = readdirSync(WEB_RENDERS_DIR, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
  expect(sheetDirs).toHaveLength(226);

  const actualPngs = listPngRelativePaths(WEB_RENDERS_DIR);
  const expectedPngs = expectedSheetKeys.map((key) => `${key}.png`).sort();
  expect(actualPngs).toEqual(expectedPngs);
});
