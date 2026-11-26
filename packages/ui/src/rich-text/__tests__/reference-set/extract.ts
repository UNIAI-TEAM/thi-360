import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import ts from "typescript";
import type { DraftFormulaEntry, FormulaCatalogue, FormulaDisplay } from "./types";

export interface ExtractedFormula {
  latex: string;
  display: FormulaDisplay;
}

const DELIMITER_RE = /\\\(|\\\[|\\\)|\\\]/;

function asciiCompare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function extractFormulasFromHtml(html: string): ExtractedFormula[] {
  const container = document.createElement("div");
  container.innerHTML = html;
  const extracted: ExtractedFormula[] = [];

  for (const span of [...container.querySelectorAll<HTMLElement>("span.math-inline, span.math-block")]) {
    const displayMode = span.classList.contains("math-block");
    let latex = span.dataset.latex;
    if (latex === undefined) {
      const text = span.textContent ?? "";
      const open = displayMode ? "\\[" : "\\(";
      const close = displayMode ? "\\]" : "\\)";
      const trimmed = text.trim();
      latex = trimmed.startsWith(open) && trimmed.endsWith(close) ? trimmed.slice(2, -2) : text;
    }
    const normalized = latex.trim();
    if (normalized.length > 0) {
      extracted.push({ latex: normalized, display: displayMode ? "block" : "inline" });
    }
  }

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      DELIMITER_RE.test(node.nodeValue ?? "") && !node.parentElement?.closest("span.math-inline, span.math-block")
        ? NodeFilter.FILTER_ACCEPT
        : NodeFilter.FILTER_REJECT,
  });

  while (walker.nextNode()) {
    const text = walker.currentNode.nodeValue ?? "";
    let cursor = 0;
    while (true) {
      const openInline = text.indexOf("\\(", cursor);
      const openBlock = text.indexOf("\\[", cursor);
      const index = openInline === -1 ? openBlock : openBlock === -1 ? openInline : Math.min(openInline, openBlock);
      if (index === -1) break;
      const block = text[index + 1] === "[";
      const close = block ? "\\]" : "\\)";
      const end = text.indexOf(close, index + 2);
      if (end === -1) break;
      const latex = text.slice(index + 2, end).trim();
      if (latex.length > 0) {
        extracted.push({ latex, display: block ? "block" : "inline" });
      }
      cursor = end + 2;
    }
  }

  return extracted;
}

function collectStrings(value: unknown, out: string[]) {
  if (typeof value === "string") {
    out.push(value);
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) collectStrings(item, out);
    return;
  }
  if (value && typeof value === "object") {
    for (const entry of Object.values(value)) collectStrings(entry, out);
  }
}

function listUiTestFiles(uiSrcDir: string): string[] {
  const results: string[] = [];
  const walk = (dir: string) => {
    const entries = readdirSync(dir, { withFileTypes: true }).sort((a, b) => asciiCompare(a.name, b.name));
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (/\.test\.tsx?$/.test(entry.name)) {
        const rel = path.relative(uiSrcDir, full).replace(/\\/g, "/");
        // Exclude the reference-set validator itself so synthetic mutation cases are not treated as repo samples.
        if (rel === "rich-text/__tests__/reference-set.test.ts") continue;
        results.push(full);
      }
    }
  };
  walk(uiSrcDir);
  return results.sort((a, b) =>
    asciiCompare(path.relative(uiSrcDir, a).replace(/\\/g, "/"), path.relative(uiSrcDir, b).replace(/\\/g, "/")),
  );
}

function extractFormulasFromTestSource(filePath: string, sourceText: string): ExtractedFormula[] {
  const scriptKind = filePath.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sourceFile = ts.createSourceFile(filePath, sourceText, ts.ScriptTarget.Latest, true, scriptKind);
  const extracted: ExtractedFormula[] = [];

  const visit = (node: ts.Node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === "formula" &&
      node.arguments.length === 1
    ) {
      const arg = node.arguments[0];
      if (arg && (ts.isStringLiteral(arg) || ts.isNoSubstitutionTemplateLiteral(arg))) {
        const latex = arg.text.trim();
        if (latex.length > 0) {
          extracted.push({ latex, display: "inline" });
        }
        return;
      }
    }

    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      const value = node.text;
      if (value.includes("math-inline") || value.includes("math-block") || DELIMITER_RE.test(value)) {
        extracted.push(...extractFormulasFromHtml(value));
      }
    }

    ts.forEachChild(node, visit);
  };

  visit(sourceFile);
  return extracted;
}

export function buildCatalogueDraftEntries(catalogue: FormulaCatalogue): DraftFormulaEntry[] {
  const entries: DraftFormulaEntry[] = [];

  for (const part of catalogue.parts) {
    for (const group of part.groups) {
      for (const formula of group.formulas) {
        const latex = formula.latex.trim();
        entries.push({
          latex,
          display: "block",
          origin: "catalogue",
          group: group.id,
          kind: group.kind,
          rejectedArea: null,
        });
        if (!formula.blockOnly) {
          entries.push({
            latex,
            display: "inline",
            origin: "catalogue",
            group: group.id,
            kind: group.kind,
            rejectedArea: null,
          });
        }
      }
    }
  }

  for (const rejected of catalogue.rejected) {
    const latex = rejected.latex.trim();
    entries.push({
      latex,
      display: "inline",
      origin: "catalogue",
      group: null,
      kind: null,
      rejectedArea: rejected.area,
    });
    entries.push({
      latex,
      display: "block",
      origin: "catalogue",
      group: null,
      kind: null,
      rejectedArea: rejected.area,
    });
  }

  return entries;
}

export function collectRepoSampleDraftEntries(repoRoot: string): DraftFormulaEntry[] {
  const seen = new Set<string>();
  const entries: DraftFormulaEntry[] = [];

  const pushUnique = (found: ExtractedFormula[]) => {
    for (const item of found) {
      const key = `${item.display}\u0000${item.latex}`;
      if (seen.has(key)) continue;
      seen.add(key);
      entries.push({
        latex: item.latex,
        display: item.display,
        origin: "repo-sample",
        group: null,
        kind: null,
        rejectedArea: null,
      });
    }
  };

  const richTextSamplesPath = path.join(repoRoot, "docs/specs/fixtures/rich-text-samples.json");
  const richTextFixture = JSON.parse(readFileSync(richTextSamplesPath, "utf8")) as {
    samples: { html: string }[];
  };
  for (const sample of richTextFixture.samples) {
    pushUnique(extractFormulasFromHtml(sample.html));
  }

  const questionSamplesPath = path.join(repoRoot, "apps/web-student/src/app/dev/question-kit/question-samples.json");
  const questionFixture = JSON.parse(readFileSync(questionSamplesPath, "utf8")) as {
    samples: unknown[];
  };
  const questionStrings: string[] = [];
  collectStrings(questionFixture.samples, questionStrings);
  for (const text of questionStrings) {
    if (text.includes("math-inline") || text.includes("math-block") || DELIMITER_RE.test(text)) {
      pushUnique(extractFormulasFromHtml(text));
    }
  }

  const uiSrcDir = path.join(repoRoot, "packages/ui/src");
  for (const testFile of listUiTestFiles(uiSrcDir)) {
    const source = readFileSync(testFile, "utf8");
    pushUnique(extractFormulasFromTestSource(testFile, source));
  }

  return entries;
}
