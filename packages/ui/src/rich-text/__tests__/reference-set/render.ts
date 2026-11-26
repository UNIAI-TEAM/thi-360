import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { renderMath } from "../../math-render";
import type { DraftFormulaEntry, FormulaDisplay, FormulaEntry } from "./types";

const require = createRequire(import.meta.url);

export interface EvaluatedFormula {
  katexRenders: boolean;
  renderedHtml: string;
}

export function getInstalledKatexVersion(): string {
  const pkgPath = require.resolve("katex/package.json");
  const pkg = JSON.parse(readFileSync(pkgPath, "utf8")) as { version: string };
  return pkg.version;
}

export function getKatexCssHref(): string {
  return pathToFileURL(require.resolve("katex/dist/katex.min.css")).href;
}

export function sha256Hex(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function waitForMhchemRender(span: HTMLElement): Promise<void> {
  const deadline = Date.now() + 5_000;
  while (span.querySelector("[aria-busy='true']") !== null) {
    if (Date.now() > deadline) {
      throw new Error("Timed out waiting for mhchem deferred render in math-render.ts");
    }
    await new Promise<void>((resolve) => setTimeout(resolve, 5));
  }
}

export async function evaluateFormulaWithRenderMath(
  latex: string,
  display: FormulaDisplay,
): Promise<EvaluatedFormula> {
  const host = document.createElement("div");
  const span = document.createElement("span");
  span.className = display === "block" ? "math-block" : "math-inline";
  span.textContent = display === "block" ? `\\[${latex}\\]` : `\\(${latex}\\)`;
  host.append(span);
  // deferUntilMhchem checks target.isConnected before re-rendering once mhchem finishes loading.
  document.body.append(host);
  try {
    renderMath(host);
    if (span.querySelector("[aria-busy='true']") !== null) {
      await waitForMhchemRender(span);
    }
    return {
      katexRenders: !span.classList.contains("math-error"),
      renderedHtml: span.outerHTML,
    };
  } finally {
    host.remove();
  }
}

export function assignSheetKeys(
  entries: readonly Omit<FormulaEntry, "sheetKey">[],
): FormulaEntry[] {
  const counters = new Map<string, number>();
  return entries.map((entry) => {
    let sheetKey: string | null = null;
    if (entry.display === "block" && entry.katexRenders) {
      const sheetName = entry.origin === "repo-sample" ? "repo-sample" : entry.group;
      if (sheetName !== null) {
        const next = (counters.get(sheetName) ?? 0) + 1;
        counters.set(sheetName, next);
        sheetKey = `${sheetName}/${String(next).padStart(3, "0")}`;
      }
    }
    return { ...entry, sheetKey };
  });
}

export async function evaluateAllDraftEntries(
  drafts: readonly DraftFormulaEntry[],
): Promise<{ entries: FormulaEntry[]; renderedHtmlBySheetKey: Map<string, string> }> {
  const evaluated: { entry: Omit<FormulaEntry, "sheetKey">; renderedHtml: string }[] = [];
  for (const draft of drafts) {
    const result = await evaluateFormulaWithRenderMath(draft.latex, draft.display);
    evaluated.push({
      entry: { ...draft, katexRenders: result.katexRenders },
      renderedHtml: result.renderedHtml,
    });
  }

  const entries = assignSheetKeys(evaluated.map((item) => item.entry));
  const renderedHtmlBySheetKey = new Map<string, string>();
  entries.forEach((entry, index) => {
    if (entry.sheetKey !== null) {
      renderedHtmlBySheetKey.set(entry.sheetKey, evaluated[index]!.renderedHtml);
    }
  });
  return { entries, renderedHtmlBySheetKey };
}

export interface SheetTile {
  sheetKey: string;
  renderedHtml: string;
}

function escapeHtmlAttr(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function buildSheetHtmlDocument(
  sheetName: string,
  tiles: readonly SheetTile[],
  katexCssHref: string,
): string {
  const tileMarkup = tiles
    .map(
      (tile) =>
        `  <div class="formula-tile rich-text-body" data-sheet-key="${escapeHtmlAttr(tile.sheetKey)}">${tile.renderedHtml}</div>`,
    )
    .join("\n");

  return `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <title>${escapeHtmlAttr(sheetName)}</title>
  <link rel="stylesheet" href="${escapeHtmlAttr(katexCssHref)}">
  <style>
    :root { color-scheme: light; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 0;
      background: #ffffff;
      color: #11181c;
      font-family: "Be Vietnam Pro", system-ui, sans-serif;
      font-size: 16px;
      line-height: 1.5;
    }
    .formula-tile {
      width: 390px;
      padding: 12px 16px;
      background: #ffffff;
      color: #11181c;
    }
    .rich-text-body .math-block {
      display: block;
      margin: 0;
      text-align: center;
    }
    .rich-text-body .katex-display {
      margin: 0;
    }
  </style>
</head>
<body>
${tileMarkup}
</body>
</html>
`;
}
