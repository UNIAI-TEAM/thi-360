import type { AnswerFormat } from "../types";

export type SymbolGroup = "sign" | "charge" | "subscript" | "arrow";

export interface SymbolKey {
  id: string;
  label: string;
  insert: string;
  group: SymbolGroup;
}

/**
 * Ion, chemical, and reaction symbol palette whose `insert` strings match the plain ASCII
 * notation stored in the question bank and normalized by `ItemGrader.normalizeShortAnswer`
 * (BS 17.7, FS-7 AC-10/AC-11).
 */
export const DEFAULT_ION_SYMBOLS: readonly SymbolKey[] = [
  { id: "plus", label: "+", insert: "+", group: "sign" },
  { id: "minus", label: "\u2212", insert: "-", group: "sign" },
  { id: "lparen", label: "(", insert: "(", group: "sign" },
  { id: "rparen", label: ")", insert: ")", group: "sign" },
  { id: "charge-2plus", label: "\u00b2\u207a", insert: "2+", group: "charge" },
  { id: "charge-3plus", label: "\u00b3\u207a", insert: "3+", group: "charge" },
  { id: "charge-2minus", label: "\u00b2\u207b", insert: "2-", group: "charge" },
  { id: "charge-3minus", label: "\u00b3\u207b", insert: "3-", group: "charge" },
  { id: "sub-2", label: "\u2082", insert: "2", group: "subscript" },
  { id: "sub-3", label: "\u2083", insert: "3", group: "subscript" },
  { id: "sub-4", label: "\u2084", insert: "4", group: "subscript" },
  { id: "arrow-right", label: "\u2192", insert: "->", group: "arrow" },
] as const;

export function insertTextAtCursor(
  currentValue: string,
  selectionStart: number | null,
  selectionEnd: number | null,
  insertion: string,
): { nextValue: string; nextCursor: number } {
  const len = currentValue.length;
  const rawStart = selectionStart == null ? len : Math.max(0, Math.min(selectionStart, len));
  const rawEnd = selectionEnd == null ? rawStart : Math.max(0, Math.min(selectionEnd, len));
  const start = Math.min(rawStart, rawEnd);
  const end = Math.max(rawStart, rawEnd);
  const nextValue = currentValue.slice(0, start) + insertion + currentValue.slice(end);
  return {
    nextValue,
    nextCursor: start + insertion.length,
  };
}

const SYMBOL_HINT_PATTERN = /(?:\bion\b|cong thuc|ky hieu|ki hieu|formula|equation|symbol)/i;

function foldDiacritics(raw: string): string {
  return raw
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\u0111/gi, "d");
}

export function shouldShowSymbolInput(
  answerFormat?: AnswerFormat | null,
  inputHint?: string | null,
): boolean {
  if (
    answerFormat === "CHEM_FORMULA" ||
    answerFormat === "CHEM_EQUATION" ||
    answerFormat === "TEXT"
  ) {
    return true;
  }
  if (answerFormat === "INTEGER" || answerFormat === "DECIMAL" || answerFormat === "FRACTION") {
    return false;
  }
  if (!inputHint) return false;
  return SYMBOL_HINT_PATTERN.test(foldDiacritics(inputHint));
}
