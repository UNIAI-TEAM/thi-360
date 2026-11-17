/**
 * Mô hình và định dạng của hộp Sơ đồ lai (spec 3.2, 4.9): mỗi hàng một thế hệ — P (bên 1 × bên 2), G (giao tử hai
 * bên), F1…Fn (tỉ lệ kiểu gen, tùy chọn tỉ lệ kiểu hình). Đầu ra là innerHTML của `figure.cross` đúng từng byte như
 * mẫu `cross-diagram` của bảng mẫu; mở lại đọc ngược HTML đó về mô hình.
 *
 * Chữ trong ô là chữ thường; `_` và `^` tạo chỉ số dưới/trên: `F_1`, `X^A`, hoặc `_{…}`/`^{…}` cho nhiều ký tự.
 */

export type Sex = "none" | "male" | "female";

const SEX_SIGN: Record<Exclude<Sex, "none">, string> = { male: "♂", female: "♀" };
const SIGN_SEX: Record<string, Sex> = { "♂": "male", "♀": "female" };

export interface FilialGeneration {
  genotype: string;
  phenotype: string;
}

export interface CrossModel {
  sexA: Sex;
  genotypeA: string;
  sexB: Sex;
  genotypeB: string;
  gametesA: string;
  gametesB: string;
  /** F1, F2, … theo thứ tự; luôn có ít nhất F1. */
  filial: FilialGeneration[];
}

export function emptyCrossModel(): CrossModel {
  return {
    sexA: "male",
    genotypeA: "",
    sexB: "female",
    genotypeB: "",
    gametesA: "",
    gametesB: "",
    filial: [{ genotype: "", phenotype: "" }],
  };
}

/** Mã của một ô nhập: dùng làm `data-cell` để nút chỉ số tìm ô đang gõ. */
export type CellId =
  | "genotypeA"
  | "genotypeB"
  | "gametesA"
  | "gametesB"
  | `filial.${number}.genotype`
  | `filial.${number}.phenotype`;

export function getCell(model: CrossModel, id: CellId): string {
  if (id.startsWith("filial.")) {
    const [, index, field] = id.split(".");
    const generation = model.filial[Number(index)];
    return generation ? generation[field as keyof FilialGeneration] : "";
  }
  return model[id as "genotypeA" | "genotypeB" | "gametesA" | "gametesB"];
}

export function setCell(model: CrossModel, id: CellId, value: string): CrossModel {
  if (id.startsWith("filial.")) {
    const [, index, field] = id.split(".");
    return {
      ...model,
      filial: model.filial.map((generation, position) =>
        position === Number(index) ? { ...generation, [field as keyof FilialGeneration]: value } : generation,
      ),
    };
  }
  return { ...model, [id]: value };
}

/**
 * Lỗi của một ô: `parent` là ô kiểu gen bố/mẹ có `×` (dấu tách hai bên) hoặc ♂/♀ ở đầu (bị đọc thành giới tính);
 * `separator` là ô giao tử/thế hệ con có `;` (dấu tách hai phần). Những ký tự này làm mở lại đọc sai chỗ, nên hộp không
 * cho lưu để sơ đồ luôn đọc ngược được đúng như đã gõ.
 */
export type CellError = "parent" | "separator";

const PARENT_CELLS: CellId[] = ["genotypeA", "genotypeB"];

/** Các ô đang chứa ký tự không đọc ngược được (xem `CellError`). */
export function cellErrors(model: CrossModel): Partial<Record<CellId, CellError>> {
  const errors: Partial<Record<CellId, CellError>> = {};
  const ids: CellId[] = [
    ...PARENT_CELLS,
    "gametesA",
    "gametesB",
    ...model.filial.flatMap((_, index): CellId[] => [`filial.${index}.genotype`, `filial.${index}.phenotype`]),
  ];
  for (const id of ids) {
    const value = getCell(model, id);
    if (PARENT_CELLS.includes(id)) {
      if (value.includes("×") || value.trimStart().charAt(0) in SIGN_SEX) errors[id] = "parent";
    } else if (value.includes(";")) {
      errors[id] = "separator";
    }
  }
  return errors;
}

/** Ký hiệu chữ của một chỉ số `_x`/`^x`: số liền nhau hoặc một chữ cái, hoặc nhóm `{…}` không lồng. */
const SHORT_SCRIPT = /^(\d+|[A-Za-z])/;
const BRACE_SCRIPT = /^\{([^{}]+)\}/;

export type CellSegment = { kind: "text" | "sub" | "sup"; value: string };

/** Tách chữ của một ô thành các đoạn chữ thường / chỉ số dưới / chỉ số trên. */
export function cellSegments(text: string): CellSegment[] {
  const segments: CellSegment[] = [];
  let plain = "";
  let index = 0;
  while (index < text.length) {
    const char = text[index]!;
    if (char === "_" || char === "^") {
      const rest = text.slice(index + 1);
      const match = BRACE_SCRIPT.exec(rest) ?? SHORT_SCRIPT.exec(rest);
      if (match) {
        if (plain) segments.push({ kind: "text", value: plain });
        plain = "";
        segments.push({ kind: char === "_" ? "sub" : "sup", value: match[1]! });
        index += 1 + match[0].length;
        continue;
      }
    }
    plain += char;
    index += 1;
  }
  if (plain) segments.push({ kind: "text", value: plain });
  return segments;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function cellHtml(text: string): string {
  return cellSegments(text)
    .map((segment) =>
      segment.kind === "text" ? escapeHtml(segment.value) : `<${segment.kind}>${escapeHtml(segment.value)}</${segment.kind}>`,
    )
    .join("");
}

function side(sex: Sex, genotype: string): string {
  const trimmed = genotype.trim();
  if (!trimmed) return "";
  return sex === "none" ? trimmed : `${SEX_SIGN[sex]} ${trimmed}`;
}

/** Ghép hai phần của một ô bằng `separator`; thiếu phần sau thì chỉ còn phần trước, thiếu cả hai thì rỗng. */
function joinPair(first: string, second: string, separator: string): string {
  if (!second) return first;
  return `${first} ${separator} ${second}`.trim();
}

/** Nhãn của một hàng: `P`, `G` hoặc thế hệ con thứ `n`. */
export type RowLabel = "P" | "G" | { filial: number };

export interface CrossRow {
  label: RowLabel;
  /** Chữ của ô dạng gõ (còn `_`/`^`). */
  text: string;
}

/**
 * Các hàng của sơ đồ: P và G chỉ có khi có chữ; thế hệ con tính đến thế hệ cuối có chữ (thế hệ trống nằm giữa vẫn
 * giữ để số F đúng).
 */
export function crossRows(model: CrossModel): CrossRow[] {
  const rows: CrossRow[] = [];
  const parents = joinPair(side(model.sexA, model.genotypeA), side(model.sexB, model.genotypeB), "×");
  if (parents) rows.push({ label: "P", text: parents });
  const gametes = joinPair(model.gametesA.trim(), model.gametesB.trim(), ";");
  if (gametes) rows.push({ label: "G", text: gametes });
  let last = -1;
  const filialTexts = model.filial.map((generation, index) => {
    const text = joinPair(generation.genotype.trim(), generation.phenotype.trim(), ";");
    if (text) last = index;
    return text;
  });
  for (let index = 0; index <= last; index += 1) rows.push({ label: { filial: index + 1 }, text: filialTexts[index]! });
  return rows;
}

function labelHtml(label: RowLabel): string {
  return typeof label === "string" ? label : `F<sub>${label.filial}</sub>`;
}

/** innerHTML của `figure.cross`; chuỗi rỗng khi chưa có chữ nào. */
export function crossToHtml(model: CrossModel): string {
  const rows = crossRows(model);
  if (rows.length === 0) return "";
  const body = rows
    .map((row) => `<tr><th scope="row">${labelHtml(row.label)}</th><td>${cellHtml(row.text)}</td></tr>`)
    .join("");
  return `<table><tbody>${body}</tbody></table>`;
}

/** Chữ dạng gõ của một nút DOM trong ô: `sub`/`sup` thành `_x`/`^x`, thẻ khác chỉ giữ chữ bên trong. */
function nodesToText(nodes: ChildNode[]): string {
  let text = "";
  nodes.forEach((node, position) => {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent ?? "";
      return;
    }
    if (!(node instanceof HTMLElement)) return;
    const tag = node.tagName.toLowerCase();
    if (tag === "br") {
      text += " ";
      return;
    }
    if (tag !== "sub" && tag !== "sup") {
      text += nodesToText([...node.childNodes]);
      return;
    }
    const mark = tag === "sub" ? "_" : "^";
    const content = node.textContent ?? "";
    const next = nodes[position + 1]?.textContent ?? "";
    // Dạng gọn `_1` chỉ dùng khi đọc lại cho đúng: số kế tiếp không được dính vào chỉ số, nên có chữ số theo sau thì dùng `{}`.
    const short = /^[A-Za-z]$/.test(content) || (/^\d+$/.test(content) && !/^\d/.test(next));
    text += short ? `${mark}${content}` : `${mark}{${content}}`;
  });
  return text;
}

function splitOnce(text: string, separator: string): [string, string] {
  const index = text.indexOf(separator);
  if (index === -1) return [text.trim(), ""];
  return [text.slice(0, index).trim(), text.slice(index + separator.length).trim()];
}

function parseSide(text: string): { sex: Sex; genotype: string } {
  const first = text.trim().charAt(0);
  const sex = SIGN_SEX[first];
  return sex ? { sex, genotype: text.trim().slice(1).trim() } : { sex: "none", genotype: text.trim() };
}

const FILIAL_LABEL = /^F_?\{?(\d+)\}?$/;

/**
 * Đọc ngược innerHTML của `figure.cross` về mô hình. Hàng có nhãn không phải P, G, F<n> bị bỏ; thẻ lạ trong ô (đậm,
 * nghiêng, công thức) chỉ giữ chữ — hộp chỉ làm lại những gì nó tạo ra.
 */
export function htmlToCross(html: string): CrossModel {
  const model: CrossModel = { ...emptyCrossModel(), sexA: "none", sexB: "none" };
  const template = document.createElement("template");
  template.innerHTML = html;
  for (const row of template.content.querySelectorAll("tr")) {
    const label = nodesToText([...(row.querySelector("th")?.childNodes ?? [])]).trim();
    const cell = row.querySelector("td");
    if (!cell) continue;
    const text = nodesToText([...cell.childNodes]).replace(/\s+/g, " ").trim();
    if (label === "P") {
      const [first, second] = splitOnce(text, "×");
      const a = parseSide(first);
      const b = parseSide(second);
      model.sexA = a.sex;
      model.genotypeA = a.genotype;
      model.sexB = b.sex;
      model.genotypeB = b.genotype;
    } else if (label === "G") {
      [model.gametesA, model.gametesB] = splitOnce(text, ";");
    } else {
      const match = FILIAL_LABEL.exec(label);
      if (!match) continue;
      const index = Number(match[1]) - 1;
      if (index < 0) continue;
      while (model.filial.length <= index) model.filial.push({ genotype: "", phenotype: "" });
      const [genotype, phenotype] = splitOnce(text, ";");
      model.filial[index] = { genotype, phenotype };
    }
  }
  return model;
}
