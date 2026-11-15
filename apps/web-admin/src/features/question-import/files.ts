import type { ImportColumn, ImportFileType, ImportSheetRow } from "./types";

/** Giới hạn tệp nhập, cùng mức với cấp tài khoản hàng loạt (bản vẽ `CapBuoc1TaiTep`). */
/** Dấu BOM của UTF-8: Excel cần nó để đọc đúng tiếng Việt trong CSV. */
const BOM = String.fromCharCode(0xfeff);

export const MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024;
export const MAX_IMPORT_ROWS = 2000;

export const IMPORT_FILE_ERROR_CODES = [
  "IMPORT_INVALID_FILE",
  "IMPORT_MACRO_NOT_ALLOWED",
  "IMPORT_EMPTY_FILE",
  "IMPORT_FILE_TOO_LARGE",
  "IMPORT_ROW_LIMIT_EXCEEDED",
  "IMPORT_INVALID_HEADER",
] as const;
export type ImportFileErrorCode = (typeof IMPORT_FILE_ERROR_CODES)[number];

export function importFileType(name: string): ImportFileType | null {
  const lower = name.trim().toLowerCase();
  if (lower.endsWith(".xlsx")) return "XLSX";
  if (lower.endsWith(".csv")) return "CSV";
  return null;
}

/** Kiểm tra loại và cỡ tệp trước khi tải lên; máy chủ kiểm lại. */
export function checkImportFile(file: Pick<File, "name" | "size">): ImportFileErrorCode | null {
  if (file.name.trim().toLowerCase().endsWith(".xlsm")) return "IMPORT_MACRO_NOT_ALLOWED";
  if (!importFileType(file.name)) return "IMPORT_INVALID_FILE";
  if (file.size === 0) return "IMPORT_EMPTY_FILE";
  if (file.size > MAX_IMPORT_FILE_BYTES) return "IMPORT_FILE_TOO_LARGE";
  return null;
}

export function isImportFileErrorCode(code: string | undefined): code is ImportFileErrorCode {
  return (IMPORT_FILE_ERROR_CODES as readonly string[]).includes(code ?? "");
}

/**
 * Đọc CSV (RFC 4180): ô trong ngoặc kép, ngoặc kép kép, xuống dòng trong ô, CRLF, BOM đầu tệp. Dấu phân cách là dấu
 * phẩy, hoặc dấu chấm phẩy khi dòng đầu có nhiều dấu chấm phẩy hơn (Excel bản tiếng Việt lưu CSV bằng dấu chấm phẩy).
 */
export function parseCsv(text: string): string[][] {
  const source = text.startsWith(BOM) ? text.slice(1) : text;
  const firstLine = source.slice(0, source.search(/\r?\n/) === -1 ? source.length : source.search(/\r?\n/));
  const delimiter = (firstLine.match(/;/g)?.length ?? 0) > (firstLine.match(/,/g)?.length ?? 0) ? ";" : ",";
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]!;
    if (quoted) {
      if (char === '"') {
        if (source[index + 1] === '"') {
          cell += '"';
          index += 1;
        } else quoted = false;
      } else cell += char;
      continue;
    }
    if (char === '"' && cell === "") quoted = true;
    else if (char === delimiter) {
      row.push(cell);
      cell = "";
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && source[index + 1] === "\n") index += 1;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else cell += char;
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

/** Bảng (dòng 1 là tên cột) thành cột và dòng dữ liệu; bỏ dòng trống hoàn toàn. */
export function sheetFromTable(table: readonly (readonly string[])[]): {
  columns: ImportColumn[];
  rows: ImportSheetRow[];
} {
  const [header = [], ...body] = table;
  const keys = header.map((_, index) => `c${index}`);
  const rows: ImportSheetRow[] = [];
  body.forEach((values, index) => {
    if (values.every((value) => value.trim() === "")) return;
    rows.push({
      rowNumber: index + 2,
      cells: Object.fromEntries(keys.map((key, position) => [key, values[position] ?? ""])),
    });
  });
  const columns = header.map<ImportColumn>((title, index) => ({
    key: keys[index]!,
    header: title.trim(),
    example: rows[0]?.cells[keys[index]!]?.trim() ?? "",
  }));
  return { columns, rows };
}

function escapeCsvCell(value: string): string {
  const needsQuotes = ['"', ",", ";", "\r", "\n"].some((char) => value.includes(char));
  return needsQuotes ? `"${value.replaceAll('"', '""')}"` : value;
}

/** Bảng thành CSV có BOM để Excel mở đúng tiếng Việt. */
export function toCsvBlob(table: readonly (readonly string[])[]): Blob {
  const text = table.map((row) => row.map(escapeCsvCell).join(",")).join("\r\n");
  return new Blob([BOM + text], { type: "text/csv;charset=utf-8" });
}

export async function readFileText(file: Blob): Promise<string> {
  if (typeof file.text === "function") return file.text();
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error ?? new Error("read failed"));
    reader.readAsText(file);
  });
}

/** Lưu tệp sinh ra trong trình duyệt xuống máy. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.className = "sr-only";
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
