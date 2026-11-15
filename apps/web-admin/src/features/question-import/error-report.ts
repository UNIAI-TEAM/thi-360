import type { ImportErrorLine, ImportRowIssue, ImportRowResult } from "./types";

export interface IssueText {
  message: string;
  fix: string;
}

export interface ErrorReportLabels {
  colRow: string;
  colColumn: string;
  colSourceCode: string;
  colError: string;
  colFix: string;
}

/** Bảng của file lỗi: mỗi lỗi một dòng, kèm vị trí dòng và cột trong tệp gốc (bản vẽ `NhapCauHoiKetQua`). */
export function buildErrorReportTable(
  lines: readonly ImportErrorLine[],
  labels: ErrorReportLabels,
  describe: (issue: ImportRowIssue) => IssueText,
): string[][] {
  return [
    [labels.colRow, labels.colColumn, labels.colSourceCode, labels.colError, labels.colFix],
    ...lines.map((line) => {
      const text = describe(line.issue);
      return [String(line.rowNumber), line.issue.column ?? "", line.sourceCode ?? "", text.message, text.fix];
    }),
  ];
}

/** Tên file lỗi theo tên tệp gốc: `ngan-hang-toan-11.xlsx` thành `ngan-hang-toan-11-errors.csv`. */
export function errorReportFileName(sourceFileName: string): string {
  const base = sourceFileName.replace(/\.[^.]+$/, "").trim() || "import";
  return `${base}-errors.csv`;
}

/** Các lỗi chặn nhập của những dòng lỗi (cảnh báo không vào file lỗi). */
export function collectErrorLines(rows: readonly ImportRowResult[]): ImportErrorLine[] {
  return rows.flatMap((row) =>
    row.status === "ERROR"
      ? row.issues
          .filter((issue) => issue.severity === "error")
          .map((issue) => ({ rowNumber: row.rowNumber, sourceCode: row.sourceCode, issue }))
      : [],
  );
}
