export type FormulaDisplay = "inline" | "block";
export type FormulaOrigin = "catalogue" | "repo-sample";
export type FormulaKind = "curriculum" | "syntax";
export type RejectedArea = "math" | "science";
export type ExemptionApp = "android" | "ios" | "both";

export interface FormulaEntry {
  latex: string;
  display: FormulaDisplay;
  origin: FormulaOrigin;
  group: string | null;
  kind: FormulaKind | null;
  rejectedArea: RejectedArea | null;
  katexRenders: boolean;
  sheetKey: string | null;
}

export interface CatalogueCounts {
  groups: number;
  formulas: number;
  curriculumFormulas: number;
  syntaxFormulas: number;
  mhchemFormulas: number;
  blockOnly: number;
  rejected: number;
}

export interface FormulasFile {
  katexVersion: string;
  mhchem: true;
  catalogue: {
    path: "docs/specs/fixtures/formula-catalogue.json";
    date: string;
    counts: CatalogueCounts;
  };
  entries: FormulaEntry[];
}

export interface ExemptionRow {
  latex: string;
  display: FormulaDisplay;
  app: ExemptionApp;
  reason: string;
  beyondCurriculum: boolean;
  origin: FormulaOrigin;
  group: string | null;
  kind: FormulaKind | null;
  approvalRef: string;
}

export interface FormulaExemptionsFile {
  version: 1;
  rows: ExemptionRow[];
}

export interface WebRendersManifest {
  katexVersion: string;
  htmlSha256: Record<string, string>;
}

export interface ReferenceIndexFile {
  version: 1;
  sources: {
    richTextSamples: {
      path: string;
      validCount: number;
      invalidCount: number;
    };
    questionSamples: {
      path: string;
      sampleCount: number;
    };
    formulaCatalogue: {
      path: string;
      groupCount: number;
      formulaCount: number;
      rejectedCount: number;
    };
  };
  artifacts: {
    formulas: string;
    formulaExemptions: string;
    webRendersManifest: string;
    attempt50: string;
    essays: string;
    wordCount: string;
  };
}

export interface WordCountCase {
  id: string;
  description: string;
  html: string;
  expected: number;
}

export interface WordCountFile {
  version: 1;
  cases: WordCountCase[];
}

export interface CatalogueFormula {
  latex: string;
  blockOnly?: boolean;
}

export interface CatalogueGroup {
  id: string;
  kind: FormulaKind;
  topic: string;
  source: string;
  formulas: CatalogueFormula[];
}

export interface CataloguePart {
  title: string;
  groups: CatalogueGroup[];
}

export interface CatalogueRejected {
  latex: string;
  note: string;
  area: RejectedArea;
  error: string;
  inlineOnly?: boolean;
}

export interface FormulaCatalogue {
  note: string;
  date: string;
  katexVersion: string;
  counts: CatalogueCounts;
  parts: CataloguePart[];
  rejected: CatalogueRejected[];
}

export interface ApprovalTableRow {
  code: string;
  group: string | null;
  kind: string | null;
  latex: string;
  display: string;
  app: string;
  reason: string;
  beyondCurriculum: string;
  approvalQuote: string;
  approver: string;
  date: string;
}

export interface DraftFormulaEntry {
  latex: string;
  display: FormulaDisplay;
  origin: FormulaOrigin;
  group: string | null;
  kind: FormulaKind | null;
  rejectedArea: RejectedArea | null;
}
