import type { ImportDraftBlank, ImportDraftTrueFalseScoring } from "./types";

/**
 * Đọc các cột nhiều dòng của tệp nhập theo spec 4.6: danh sách mỗi mục một dòng trong ô, số thập phân viết dấu phẩy
 * hoặc dấu chấm. Hàm thuần, dùng cho `validation.ts`.
 */

/** Số thập phân viết `0,75` hoặc `0.75`; không đọc được thì `null`. */
export function parseDecimal(value: string): number | null {
  const text = value.trim();
  if (!/^\d+(?:[.,]\d+)?$/.test(text)) return null;
  return Number(text.replace(",", "."));
}

function lines(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

const LABEL_RE = /^([A-Za-z0-9]{1,3})\s*[.)]\s+(.*)$/u;

export interface LabeledItem {
  label: string;
  content: string;
}

/**
 * Cột danh sách mỗi dòng `A. nội dung` (hoặc `1. nội dung`, `a) nội dung`). Chỉ coi là có nhãn khi mọi dòng đều có
 * nhãn; ngược lại các dòng được đánh nhãn theo thứ tự (`A, B, C…` hoặc `1, 2, 3…`).
 */
export function parseLabeledLines(value: string, fallback: "letter" | "number"): LabeledItem[] {
  const rows = lines(value);
  const matches = rows.map((row) => LABEL_RE.exec(row));
  const labeled = rows.length > 0 && matches.every((match) => match !== null);
  return rows.map((row, index) => {
    const match = matches[index];
    if (labeled && match) return { label: match[1]!.toUpperCase(), content: match[2]!.trim() };
    return { label: fallback === "letter" ? String.fromCharCode(65 + (index % 26)) : String(index + 1), content: row };
  });
}

/** Đáp án Đúng/Sai: `PER_STATEMENT: 0,25 / 0,25` hoặc `COUNT_TABLE: 0,1 / 0,25 / 0,5 / 1`; sai cú pháp thì `null`. */
export function parseTrueFalseScoring(value: string): ImportDraftTrueFalseScoring | null {
  const match = /^\s*(PER_STATEMENT|COUNT_TABLE)\s*:\s*(.+)$/i.exec(value);
  if (!match) return null;
  const points = match[2]!.split("/").map((part) => parseDecimal(part));
  if (points.some((point) => point === null)) return null;
  return { method: match[1]!.toUpperCase() as ImportDraftTrueFalseScoring["method"], points: points as number[] };
}

export interface KeyedLine {
  key: string;
  value: string;
}

/** Các dòng `khóa = giá trị`; `invalid` là dòng đầu tiên không có dấu `=` hoặc thiếu khóa. */
export function parseKeyedLines(value: string): { entries: KeyedLine[]; invalid: string | null } {
  const entries: KeyedLine[] = [];
  for (const line of lines(value)) {
    const match = /^([^=]+?)\s*=\s*(.*)$/.exec(line);
    if (!match) return { entries, invalid: line };
    entries.push({ key: match[1]!.trim(), value: match[2]!.trim() });
  }
  return { entries, invalid: null };
}

/** Đáp án Điền khuyết: mỗi dòng `b1 = đáp án 1 | đáp án 2`; đáp án chuẩn đứng đầu. */
export function parseClozeAnswers(value: string): { blanks: Array<Pick<ImportDraftBlank, "id" | "answers">>; invalid: string | null } {
  const { entries, invalid } = parseKeyedLines(value);
  return {
    blanks: entries.map((entry) => ({
      id: entry.key,
      answers: entry.value
        .split("|")
        .map((answer) => answer.trim())
        .filter(Boolean),
    })),
    invalid,
  };
}

/** Các ô `[[b1]]`, `[[b2]]` trong ngữ liệu, theo thứ tự xuất hiện (có thể lặp). */
export function clozeMarkers(content: string): string[] {
  return Array.from(content.matchAll(/\[\[([^\]]+)\]\]/g), (match) => match[1]!.trim());
}
