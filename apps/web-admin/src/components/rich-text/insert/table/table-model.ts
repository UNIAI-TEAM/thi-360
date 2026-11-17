/**
 * Mô hình dữ liệu của hộp Bảng số liệu (spec 4.5): lưới ô + danh sách ô gộp, và các phép thuần
 * (thêm/xóa hàng cột, gộp/tách, dán vùng ô, nhận diện ô số) cùng bộ xuất/đọc `<figure class="data-table">`.
 * Xuất bằng chuỗi (không qua DOM) để ra đúng từng byte như bảng mẫu `data-table`.
 */

export const MAX_ROWS = 30;
export const MAX_COLS = 15;

/** `rich`: ô mang HTML nội tuyến (sub, sup, strong, em, công thức) — `text` là mã HTML của ô, sửa như mã nguồn. */
export interface TableCell {
  text: string;
  rich: boolean;
}

/** Vùng ô gộp: `row`/`col` là ô gốc (góc trên trái), các ô còn lại trong vùng bị che. */
export interface Merge {
  row: number;
  col: number;
  rowSpan: number;
  colSpan: number;
}

export interface TableDraft {
  title: string;
  unit: string;
  source: string;
  cells: TableCell[][];
  merges: Merge[];
  headerRow: boolean;
  headerCol: boolean;
  alignNumbers: boolean;
}

/** Hình chữ nhật ô đang chọn (đã chuẩn hóa: row1 <= row2, col1 <= col2). */
export interface CellRect {
  row1: number;
  col1: number;
  row2: number;
  col2: number;
}

/**
 * Chữ mở đầu dòng đơn vị và dòng nguồn trong HTML lưu (spec 3.2: `p.figure-note` là `Đơn vị: …`, `p.figure-source` là
 * `Nguồn: …`; spec 5: nhãn đến từ HTML đã lưu, không dịch). Đây là nội dung lưu, không đổi theo ngôn ngữ giao diện;
 * viết bằng mã thoát Unicode để mã nguồn không chứa chữ có dấu ngoài tệp dịch.
 */
export const UNIT_PREFIX = "\u0110\u01A1n v\u1ECB: ";
export const SOURCE_PREFIX = "Ngu\u1ED3n: ";

const emptyCell = (): TableCell => ({ text: "", rich: false });

export function createDraft(rows = 3, cols = 4): TableDraft {
  return {
    title: "",
    unit: "",
    source: "",
    cells: Array.from({ length: rows }, () => Array.from({ length: cols }, emptyCell)),
    merges: [],
    headerRow: true,
    headerCol: true,
    alignNumbers: true,
  };
}

export const rowCount = (draft: TableDraft) => draft.cells.length;
export const colCount = (draft: TableDraft) => draft.cells[0]?.length ?? 0;

function escapeText(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** Mã HTML của một ô. */
function cellHtml(cell: TableCell): string {
  return cell.rich ? cell.text.trim() : escapeText(cell.text.trim());
}

/** Số theo spec 4.5: chữ số, dấu trừ đầu, dấu phẩy thập phân, dấu chấm hoặc khoảng trắng ngăn nghìn, `%` cuối. */
const NUMBER_PATTERN = /^-?\d+(?:[ .]\d{3})*(?:,\d+)?%?$/;

export function isNumericText(text: string): boolean {
  return NUMBER_PATTERN.test(text.trim());
}

export function mergeAt(draft: TableDraft, row: number, col: number): Merge | undefined {
  return draft.merges.find(
    (merge) => row >= merge.row && row < merge.row + merge.rowSpan && col >= merge.col && col < merge.col + merge.colSpan,
  );
}

/** Ô bị che bởi một ô gộp (không phải ô gốc). */
export function isCovered(draft: TableDraft, row: number, col: number): boolean {
  const merge = mergeAt(draft, row, col);
  return merge !== undefined && (merge.row !== row || merge.col !== col);
}

/** Ô gốc chứa vị trí (row, col): chính nó khi không gộp. */
export function originOf(draft: TableDraft, row: number, col: number): { row: number; col: number } {
  const merge = mergeAt(draft, row, col);
  return merge ? { row: merge.row, col: merge.col } : { row, col };
}

/**
 * Cột nào gồm toàn ô số: xét mọi ô gốc không phải tiêu đề; ô gộp nhiều cột hoặc ô trống làm cột đó không đạt.
 */
export function numericColumns(draft: TableDraft): Set<number> {
  const cols = colCount(draft);
  const seen = new Array<boolean>(cols).fill(false);
  const ok = new Array<boolean>(cols).fill(true);
  const firstRow = draft.headerRow ? 1 : 0;
  const firstCol = draft.headerCol ? 1 : 0;
  for (let row = firstRow; row < rowCount(draft); row++) {
    for (let col = firstCol; col < cols; col++) {
      if (isCovered(draft, row, col)) continue;
      const merge = mergeAt(draft, row, col);
      const colSpan = merge?.colSpan ?? 1;
      const cell = draft.cells[row]![col]!;
      const numeric = colSpan === 1 && !cell.rich && isNumericText(cell.text);
      for (let covered = col; covered < col + colSpan; covered++) {
        seen[covered] = true;
        ok[covered] = ok[covered]! && numeric;
      }
    }
  }
  return new Set(seen.flatMap((wasSeen, col) => (wasSeen && ok[col] ? [col] : [])));
}

function spanAttributes(merge: Merge | undefined): string {
  let out = "";
  if (merge && merge.colSpan > 1) out += ` colspan="${merge.colSpan}"`;
  if (merge && merge.rowSpan > 1) out += ` rowspan="${merge.rowSpan}"`;
  return out;
}

/** Có ô nào (kể cả ô tiêu đề) có chữ không; ô bị che không tính — điều kiện để cho phép Chèn. */
export function hasContent(draft: TableDraft): boolean {
  return draft.cells.some((row, r) =>
    row.some((cell, c) => !isCovered(draft, r, c) && cell.text.trim().length > 0),
  );
}

/** Xuất `<figure class="data-table">…</figure>` đúng dạng bảng mẫu; innerHTML của node atom là phần bên trong figure. */
export function serializeTable(draft: TableDraft): string {
  const numericCols = draft.alignNumbers ? numericColumns(draft) : new Set<number>();
  const rows = rowCount(draft);
  const cols = colCount(draft);

  const renderRow = (row: number): string => {
    let html = "<tr>";
    for (let col = 0; col < cols; col++) {
      if (isCovered(draft, row, col)) continue;
      const merge = mergeAt(draft, row, col);
      const content = cellHtml(draft.cells[row]![col]!);
      if (draft.headerRow && row === 0) {
        html += `<th scope="col"${spanAttributes(merge)}>${content}</th>`;
      } else if (draft.headerCol && col === 0) {
        html += `<th scope="row"${spanAttributes(merge)}>${content}</th>`;
      } else {
        const numClass = numericCols.has(col) && (merge?.colSpan ?? 1) === 1 ? ' class="num"' : "";
        html += `<td${numClass}${spanAttributes(merge)}>${content}</td>`;
      }
    }
    return `${html}</tr>`;
  };

  let table = "<table>";
  const bodyStart = draft.headerRow ? 1 : 0;
  if (draft.headerRow) table += `<thead>${renderRow(0)}</thead>`;
  if (rows > bodyStart) {
    table += "<tbody>";
    for (let row = bodyStart; row < rows; row++) table += renderRow(row);
    table += "</tbody>";
  }
  table += "</table>";

  const title = draft.title.trim();
  const unit = draft.unit.trim();
  const source = draft.source.trim();
  return (
    `<figure class="data-table">` +
    (title ? `<figcaption>${escapeText(title)}</figcaption>` : "") +
    table +
    (unit ? `<p class="figure-note">${escapeText(`${UNIT_PREFIX}${unit}`)}</p>` : "") +
    (source ? `<p class="figure-source">${escapeText(`${SOURCE_PREFIX}${source}`)}</p>` : "") +
    `</figure>`
  );
}

/** Bỏ chữ mở đầu chuẩn (`UNIT_PREFIX`/`SOURCE_PREFIX`) khỏi ghi chú/nguồn đã lưu; không có chữ đó thì giữ nguyên chữ. */
function stripLabelPrefix(text: string, prefix: string): string {
  return (text.startsWith(prefix) ? text.slice(prefix.length) : text).trim();
}

function spanOf(element: Element, name: string): number {
  const value = Number.parseInt(element.getAttribute(name) ?? "", 10);
  return Number.isInteger(value) && value > 1 ? value : 1;
}

/** Đọc innerHTML của `figure.data-table` về bản nháp để sửa lại. Không có bảng thì trả bản nháp mặc định. */
export function parseTable(innerHtml: string): TableDraft {
  const template = document.createElement("template");
  template.innerHTML = innerHtml;
  const draft = createDraft(1, 1);
  let table: Element | null = null;
  for (const child of [...template.content.children]) {
    const tag = child.tagName.toLowerCase();
    if (tag === "figcaption") draft.title = child.textContent?.trim() ?? "";
    else if (tag === "table") table = child;
    else if (child.classList.contains("figure-note")) draft.unit = stripLabelPrefix(child.textContent ?? "", UNIT_PREFIX);
    else if (child.classList.contains("figure-source")) draft.source = stripLabelPrefix(child.textContent ?? "", SOURCE_PREFIX);
  }
  if (!table) return createDraft();

  const trs = [...table.querySelectorAll("tr")];
  const occupied: boolean[][] = [];
  const cells: TableCell[][] = trs.map(() => []);
  const merges: Merge[] = [];
  let columns = 0;
  trs.forEach((tr, row) => {
    occupied[row] ??= [];
    let col = 0;
    for (const cell of [...tr.children]) {
      while (occupied[row]![col]) col++;
      const colSpan = spanOf(cell, "colspan");
      const rowSpan = Math.min(spanOf(cell, "rowspan"), trs.length - row);
      for (let r = row; r < row + rowSpan; r++) {
        occupied[r] ??= [];
        for (let c = col; c < col + colSpan; c++) occupied[r]![c] = true;
      }
      const html = cell.innerHTML;
      const rich = html.includes("<");
      cells[row]![col] = { text: rich ? html.trim() : (cell.textContent ?? "").trim(), rich };
      if (colSpan > 1 || rowSpan > 1) merges.push({ row, col, rowSpan, colSpan });
      col += colSpan;
      columns = Math.max(columns, col);
    }
  });
  draft.cells = trs.map((_, row) => Array.from({ length: columns }, (_unused, col) => cells[row]![col] ?? emptyCell()));
  draft.merges = merges;

  draft.headerRow = trs.length > 0 && trs[0]!.parentElement?.tagName === "THEAD";
  const bodyRows = trs.slice(draft.headerRow ? 1 : 0);
  draft.headerCol =
    bodyRows.length > 0 &&
    bodyRows.every((tr) => tr.firstElementChild?.tagName === "TH" && tr.firstElementChild.getAttribute("scope") === "row");
  const hasNumClass = table.querySelector("td.num") !== null;
  draft.alignNumbers = hasNumClass || numericColumns(draft).size === 0;
  return draft;
}

function normalizeRect(rect: CellRect): CellRect {
  return {
    row1: Math.min(rect.row1, rect.row2),
    col1: Math.min(rect.col1, rect.col2),
    row2: Math.max(rect.row1, rect.row2),
    col2: Math.max(rect.col1, rect.col2),
  };
}

/** Mở rộng vùng chọn cho phủ trọn mọi ô gộp mà nó cắt qua. */
export function expandRect(draft: TableDraft, rect: CellRect): CellRect {
  let current = normalizeRect(rect);
  for (let changed = true; changed; ) {
    changed = false;
    for (const merge of draft.merges) {
      const intersects =
        merge.row <= current.row2 &&
        merge.row + merge.rowSpan - 1 >= current.row1 &&
        merge.col <= current.col2 &&
        merge.col + merge.colSpan - 1 >= current.col1;
      if (!intersects) continue;
      const next = {
        row1: Math.min(current.row1, merge.row),
        col1: Math.min(current.col1, merge.col),
        row2: Math.max(current.row2, merge.row + merge.rowSpan - 1),
        col2: Math.max(current.col2, merge.col + merge.colSpan - 1),
      };
      if (JSON.stringify(next) !== JSON.stringify(current)) {
        current = next;
        changed = true;
      }
    }
  }
  return current;
}

/**
 * Gộp được khi vùng chọn có hơn một ô và không vắt qua ranh giới tiêu đề (hàng đầu tách khỏi thân bằng `thead`,
 * cột đầu là `th scope="row"`).
 */
export function canMerge(draft: TableDraft, rect: CellRect): boolean {
  const { row1, col1, row2, col2 } = expandRect(draft, rect);
  if (row1 === row2 && col1 === col2) return false;
  if (draft.headerRow && row1 === 0 && row2 > 0) return false;
  if (draft.headerCol && col1 === 0 && col2 > 0) return false;
  return true;
}

/** Ô đang chọn là đúng một ô gộp (có thể tách). */
export function mergeInRect(draft: TableDraft, rect: CellRect): Merge | undefined {
  const { row1, col1, row2, col2 } = expandRect(draft, rect);
  return draft.merges.find((m) => m.row === row1 && m.col === col1 && m.row + m.rowSpan - 1 === row2 && m.col + m.colSpan - 1 === col2);
}

export function mergeCells(draft: TableDraft, rect: CellRect): TableDraft {
  if (!canMerge(draft, rect)) return draft;
  const { row1, col1, row2, col2 } = expandRect(draft, rect);
  const parts: TableCell[] = [];
  for (let row = row1; row <= row2; row++) {
    for (let col = col1; col <= col2; col++) {
      if (isCovered(draft, row, col)) continue;
      const cell = draft.cells[row]![col]!;
      if (cell.text.trim()) parts.push(cell);
    }
  }
  const rich = parts.some((part) => part.rich);
  const text = parts.map((part) => (rich && !part.rich ? escapeText(part.text.trim()) : part.text.trim())).join(" ");
  const cells = draft.cells.map((cellsRow, row) =>
    cellsRow.map((cell, col) => {
      if (row < row1 || row > row2 || col < col1 || col > col2) return cell;
      return row === row1 && col === col1 ? { text, rich } : emptyCell();
    }),
  );
  const merges = [
    ...draft.merges.filter((m) => m.row < row1 || m.row > row2 || m.col < col1 || m.col > col2),
    { row: row1, col: col1, rowSpan: row2 - row1 + 1, colSpan: col2 - col1 + 1 },
  ];
  return { ...draft, cells, merges };
}

export function unmergeCells(draft: TableDraft, rect: CellRect): TableDraft {
  const merge = mergeInRect(draft, rect);
  if (!merge) return draft;
  return { ...draft, merges: draft.merges.filter((m) => m !== merge) };
}

/** Thêm một hàng trống ngay sau `afterRow`. Ô gộp bị cắt ngang thì dài thêm một hàng. */
export function addRow(draft: TableDraft, afterRow: number): TableDraft {
  if (rowCount(draft) >= MAX_ROWS) return draft;
  const at = afterRow + 1;
  const cells = [...draft.cells];
  cells.splice(at, 0, Array.from({ length: colCount(draft) }, emptyCell));
  const merges = draft.merges.map((m) => {
    if (at <= m.row) return { ...m, row: m.row + 1 };
    if (at < m.row + m.rowSpan) return { ...m, rowSpan: m.rowSpan + 1 };
    return m;
  });
  return { ...draft, cells, merges };
}

export function addCol(draft: TableDraft, afterCol: number): TableDraft {
  if (colCount(draft) >= MAX_COLS) return draft;
  const at = afterCol + 1;
  const cells = draft.cells.map((row) => {
    const next = [...row];
    next.splice(at, 0, emptyCell());
    return next;
  });
  const merges = draft.merges.map((m) => {
    if (at <= m.col) return { ...m, col: m.col + 1 };
    if (at < m.col + m.colSpan) return { ...m, colSpan: m.colSpan + 1 };
    return m;
  });
  return { ...draft, cells, merges };
}

function shrunk(merge: Merge): Merge | null {
  return merge.rowSpan === 1 && merge.colSpan === 1 ? null : merge;
}

/**
 * Xóa hàng. Ô gộp bị cắt ngắn đi một hàng; nếu hàng bị xóa chính là hàng của ô gốc mà ô gộp vẫn còn thì ô gốc dời
 * xuống hàng kế (nay ở cùng chỉ số) và mang theo chữ của ô gốc cũ, để chữ không mất còn ô gộp thì ở lại.
 */
export function deleteRow(draft: TableDraft, row: number): TableDraft {
  if (rowCount(draft) <= 1) return draft;
  const cells = draft.cells.filter((_, index) => index !== row);
  const merges = draft.merges.flatMap((m) => {
    if (row < m.row) return [{ ...m, row: m.row - 1 }];
    if (row >= m.row + m.rowSpan) return [m];
    const next = shrunk({ ...m, rowSpan: m.rowSpan - 1 });
    if (!next) return [];
    if (row === m.row) cells[row] = cells[row]!.map((cell, col) => (col === m.col ? draft.cells[row]![m.col]! : cell));
    return [next];
  });
  return { ...draft, cells, merges };
}

/** Xóa cột; ô gốc dời sang cột kế cùng chữ khi cột bị xóa là cột của ô gốc mà ô gộp còn lại (như `deleteRow`). */
export function deleteCol(draft: TableDraft, col: number): TableDraft {
  if (colCount(draft) <= 1) return draft;
  const cells = draft.cells.map((row) => row.filter((_, index) => index !== col));
  const merges = draft.merges.flatMap((m) => {
    if (col < m.col) return [{ ...m, col: m.col - 1 }];
    if (col >= m.col + m.colSpan) return [m];
    const next = shrunk({ ...m, colSpan: m.colSpan - 1 });
    if (!next) return [];
    if (col === m.col) cells[m.row]![col] = draft.cells[m.row]![col]!;
    return [next];
  });
  return { ...draft, cells, merges };
}

/** Bật/tắt hàng đầu là tiêu đề. Bật thì tách các ô gộp vắt qua ranh giới `thead`/`tbody` (ô gốc ở hàng đầu, nhiều hàng). */
export function setHeaderRow(draft: TableDraft, on: boolean): TableDraft {
  const merges = on ? draft.merges.filter((m) => !(m.row === 0 && m.rowSpan > 1)) : draft.merges;
  return { ...draft, headerRow: on, merges };
}

/** Bật/tắt cột đầu là tiêu đề. Bật thì tách các ô gộp vắt qua ranh giới cột tiêu đề (ô gốc ở cột đầu, nhiều cột). */
export function setHeaderCol(draft: TableDraft, on: boolean): TableDraft {
  const merges = on ? draft.merges.filter((m) => !(m.col === 0 && m.colSpan > 1)) : draft.merges;
  return { ...draft, headerCol: on, merges };
}

export function setCellText(draft: TableDraft, row: number, col: number, text: string): TableDraft {
  const cells = draft.cells.map((cellsRow, r) =>
    r === row ? cellsRow.map((cell, c) => (c === col ? { ...cell, text } : cell)) : cellsRow,
  );
  return { ...draft, cells };
}

/**
 * Tách văn bản dán từ Excel/Google Sheets (cột cách nhau bằng tab, hàng bằng xuống dòng; ô nhiều dòng nằm trong
 * dấu nháy kép). Dòng cuối rỗng do xuống dòng kết thúc bị bỏ.
 */
export function parseClipboardGrid(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  const source = text.replace(/\u00a0/g, " ");
  for (let i = 0; i < source.length; i++) {
    const ch = source[i]!;
    if (quoted) {
      if (ch === '"' && source[i + 1] === '"') {
        field += '"';
        i++;
      } else if (ch === '"') quoted = false;
      else field += ch;
    } else if (ch === '"' && field === "") quoted = true;
    else if (ch === "\t") {
      row.push(field);
      field = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && source[i + 1] === "\n") i++;
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else field += ch;
  }
  if (field !== "" || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

export interface PasteResult {
  draft: TableDraft;
  /** Vùng dán vượt 30 hàng hoặc 15 cột nên phần thừa bị bỏ. */
  truncated: boolean;
}

/** Điền vùng ô dán từ ô (row, col): tự thêm hàng cột tới giới hạn, bỏ qua ô bị che bởi ô gộp. */
export function pasteGrid(draft: TableDraft, row: number, col: number, grid: string[][]): PasteResult {
  const needRows = row + grid.length;
  const needCols = col + Math.max(0, ...grid.map((line) => line.length));
  let next = draft;
  while (rowCount(next) < Math.min(needRows, MAX_ROWS)) next = addRow(next, rowCount(next) - 1);
  while (colCount(next) < Math.min(needCols, MAX_COLS)) next = addCol(next, colCount(next) - 1);
  const cells = next.cells.map((cellsRow) => [...cellsRow]);
  grid.forEach((line, dr) => {
    line.forEach((value, dc) => {
      const r = row + dr;
      const c = col + dc;
      if (r >= rowCount(next) || c >= colCount(next) || isCovered(next, r, c)) return;
      cells[r]![c] = { text: value.trim(), rich: false };
    });
  });
  return { draft: { ...next, cells }, truncated: needRows > MAX_ROWS || needCols > MAX_COLS };
}

const FIGURE_OPEN = '<figure class="data-table">';

/** innerHTML của `figure.data-table` (thuộc tính `html` của node atom); `null` khi bộ lọc đã bỏ cả khối. */
export function unwrapFigure(figureHtml: string): string | null {
  return figureHtml.startsWith(FIGURE_OPEN) && figureHtml.endsWith("</figure>")
    ? figureHtml.slice(FIGURE_OPEN.length, -"</figure>".length)
    : null;
}
