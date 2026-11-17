/**
 * Bộ đổi OMML (công thức Word) → LaTeX cho tập con của spec 4.10, tự viết, không thư viện: phân số `m:f`,
 * căn `m:rad`, mũ/chỉ số `m:sSup` `m:sSub` `m:sSubSup`, ngoặc `m:d`, hệ `m:eqArr`, tổng/tích/tích phân `m:nary`,
 * hàm `m:func`, ma trận `m:m`, dấu trên `m:acc` `m:bar`, giới hạn `m:limLow` `m:limUpp`, chữ `m:r`.
 *
 * Phần tử ngoài tập con (`m:sPre`, `m:groupChr`, `m:borderBox`…) ném `UnsupportedOmmlError`: cả công thức đó vào nhóm
 * "Cần gõ lại" chứ không đoán một phần. Đầu ra là LaTeX "thô" — chưa qua bộ tự sửa (`fixers.ts`).
 */
import {
  DELIMITERS,
  FUNCTION_NAMES,
  LATEX_ESCAPES,
  NARY_COMMANDS,
  SYMBOL_COMMANDS,
  TEXT_ESCAPES,
  functionCommand,
} from "./latex-symbols";

/** Công thức có phần tử ngoài tập con đã hỗ trợ. `element` là tên phần tử (ví dụ `m:sPre`). */
export class UnsupportedOmmlError extends Error {
  constructor(readonly element: string) {
    super(`Unsupported OMML element: ${element}`);
    this.name = "UnsupportedOmmlError";
  }
}

/** XML của công thức không đọc được (cắt cụt, thẻ lạ…). */
export class MalformedOmmlError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "MalformedOmmlError";
  }
}

export interface OmmlResult {
  /** LaTeX thô, chưa bọc `\( … \)`. */
  latex: string;
  /** `m:oMathPara` (công thức một dòng riêng) → `true`; `m:oMath` đứng trong dòng chữ → `false`. */
  display: boolean;
}

const OMML_NAMESPACE = "http://schemas.openxmlformats.org/officeDocument/2006/math";
/** Dấu `&` của chữ trong công thức (căn lề `m:eqArr`): giữ ký hiệu riêng tới khi biết nó nằm trong môi trường mảng. */
const ALIGN = "\uE000";

/** Phần tử OMML (tiền tố `m:`) hay không. */
function isMath(node: Element): boolean {
  return node.prefix === "m";
}

/**
 * Con phần tử toán (`m:`) của `parent`. Clipboard của Word chen thẻ HTML (`<i>`, `<span>`) vào giữa các phần tử OMML:
 * những thẻ không phải `m:` là trong suốt, lấy phần tử `m:` bên trong chúng.
 */
function mathChildren(parent: Element, localName?: string): Element[] {
  const found: Element[] = [];
  const visit = (element: Element) => {
    for (const child of element.children) {
      if (!isMath(child)) visit(child);
      else if (localName === undefined || child.localName === localName) found.push(child);
    }
  };
  visit(parent);
  return found;
}

function firstChild(parent: Element, localName: string): Element | undefined {
  return mathChildren(parent, localName)[0];
}

/** Giá trị `m:val` của phần tử thuộc tính con (`m:fPr/m:type`…); `undefined` khi không có phần tử hoặc thuộc tính. */
function propertyValue(parent: Element, propertiesName: string, name: string): string | undefined {
  const properties = firstChild(parent, propertiesName);
  const property = properties ? firstChild(properties, name) : undefined;
  if (!property) return undefined;
  const value = property.getAttribute("m:val");
  return value === null ? undefined : value;
}

/** Thuộc tính nhị phân của OMML: có phần tử mà không ghi giá trị cũng là bật. */
function flagValue(parent: Element, propertiesName: string, name: string): boolean {
  const properties = firstChild(parent, propertiesName);
  const property = properties ? firstChild(properties, name) : undefined;
  if (!property) return false;
  const value = property.getAttribute("m:val");
  return value === null || !["0", "false", "off"].includes(value.toLowerCase());
}

/** Nối các mảnh LaTeX; chèn một khoảng trắng khi lệnh `\name` đứng sát một chữ cái, tránh thành lệnh khác (`\alphax`). */
function cat(...parts: string[]): string {
  let out = "";
  for (const part of parts) {
    if (!part) continue;
    if (out && /\\[A-Za-z]+$/.test(out) && /^[A-Za-z]/.test(part)) out += " ";
    out += part;
  }
  return out;
}

/** Chuỗi `s` có phải một nhóm `{…}` ngoặc nhau từ đầu tới cuối không. */
function isBraceGroup(s: string): boolean {
  if (!s.startsWith("{") || !s.endsWith("}")) return false;
  let depth = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "\\") {
      i++;
      continue;
    }
    if (c === "{") depth++;
    else if (c === "}") {
      depth--;
      if (depth === 0 && i < s.length - 1) return false;
    }
  }
  return depth === 0;
}

/** `\left … \right …` bao trọn chuỗi (cặp `\left`/`\right` đầu tiên đóng ở cuối). */
function isDelimited(s: string): boolean {
  if (!s.startsWith("\\left")) return false;
  let depth = 0;
  const pattern = /\\(left|right)(?![A-Za-z])/g;
  for (let match = pattern.exec(s); match; match = pattern.exec(s)) {
    depth += match[1] === "left" ? 1 : -1;
    if (depth === 0) {
      // Còn dấu ngoặc đóng đi kèm `\right` (một ký tự, hoặc một lệnh như `\rangle`) rồi hết chuỗi.
      const tail = s.slice(match.index + match[0].length);
      return /^(?:\\[A-Za-z]+|\\.|.)$/.test(tail);
    }
  }
  return false;
}

/** Cơ sở của chỉ số/mũ: nguyên tử thì giữ, nhiều ký hiệu thì bọc `{…}`. */
function scriptBase(s: string): string {
  if (s === "") return "{}";
  if (/^(?:[A-Za-z0-9]|\\[A-Za-z]+)$/.test(s) || isBraceGroup(s) || isDelimited(s)) return s;
  return `{${s}}`;
}

/** Nội dung của chỉ số/mũ: một ký tự hoặc một lệnh thì để trần, còn lại bọc `{…}`. */
function scriptBody(s: string): string {
  return /^(?:[A-Za-z0-9]|\\[A-Za-z]+)$/.test(s) ? s : `{${s}}`;
}

function brace(s: string): string {
  return `{${s}}`;
}

/** Phần chữ thường giữa các ký tự lạ, bọc `\text{…}` (dấu tiếng Việt, ký tự KaTeX không dựng được trong chế độ toán). */
function textGroup(text: string): string {
  let out = "";
  for (const char of text) out += TEXT_ESCAPES[char] ?? char;
  return `\\text{${out}}`;
}

/** Chữ cái mà bộ đổi coi là một phần của từ (chữ Hy Lạp đã có lệnh riêng nên không tính). */
function isWordChar(char: string): boolean {
  return char !== "º" && /[\p{L}\p{M}]/u.test(char) && !(char in SYMBOL_COMMANDS);
}

/** Một từ: chữ Latin không dấu theo kiểu chữ của run; có chữ có dấu (tiếng Việt…) thì cả từ vào `\text{…}`. */
function wordToLatex(word: string, style: string): string {
  if (!/^[A-Za-z]+$/.test(word)) return textGroup(word);
  if (style === "p") return FUNCTION_NAMES.includes(word) ? functionCommand(word) : `\\mathrm{${word}}`;
  if (style === "b") return `\\mathbf{${word}}`;
  if (style === "bi") return `\\boldsymbol{${word}}`;
  return word;
}

/** Đổi chữ của một `m:r` sang LaTeX. `style`: `p` thẳng đứng, `b` đậm, `bi` đậm nghiêng, `i` nghiêng (mặc định), `nor` chữ thường. */
function runToLatex(text: string, style: string): string {
  if (style === "nor") return text.trim() === "" ? "" : textGroup(text);

  const chars = [...text];
  let out = "";
  for (let i = 0; i < chars.length; ) {
    const char = chars[i]!;
    if (isWordChar(char)) {
      let end = i + 1;
      while (end < chars.length && isWordChar(chars[end]!)) end++;
      out = cat(out, wordToLatex(chars.slice(i, end).join(""), style));
      i = end;
      continue;
    }
    i++;
    if (/\s/.test(char)) {
      if (out && !out.endsWith(" ")) out += " ";
    } else if (char === ",") {
      // Dấu phẩy thập phân kiểu Việt Nam "3,14": `{,}` để KaTeX không chèn khoảng trống sau dấu phẩy.
      out += /\d$/.test(out) && /\d/.test(chars[i] ?? "") ? "{,}" : ",";
    } else if (char === "&") {
      out += ALIGN;
    } else if (["\u2061", "\u2062", "\u2063", "\u200b"].includes(char)) {
      // Phép áp dụng hàm và ký tự vô hình của Word: không có nghĩa trong LaTeX.
    } else if (char in SYMBOL_COMMANDS) {
      out = cat(out, SYMBOL_COMMANDS[char]!);
    } else if (char in LATEX_ESCAPES) {
      out += LATEX_ESCAPES[char];
    } else if (char === '"') {
      out += "''";
    } else if (char === "º" || char === "˚") {
      // Ký hiệu độ giữ nguyên (chuẩn hóa về "°"): bộ tự sửa viết lại thành `^{\circ}` và đánh dấu chờ xác nhận.
      out += "°";
    } else if (/[\d!-~°]/.test(char)) {
      out += char;
    } else {
      out = cat(out, textGroup(char));
    }
  }
  return out.trim();
}

/** Mã dấu trên của `m:acc` → lệnh ngắn và lệnh rộng cho nhiều ký tự. */
const ACCENTS: Readonly<Record<string, { narrow: string; wide: string }>> = {
  "\u0302": { narrow: "\\hat", wide: "\\widehat" },
  "^": { narrow: "\\hat", wide: "\\widehat" },
  "\u0303": { narrow: "\\tilde", wide: "\\widetilde" },
  "~": { narrow: "\\tilde", wide: "\\widetilde" },
  "\u0304": { narrow: "\\bar", wide: "\\overline" },
  "\u00af": { narrow: "\\bar", wide: "\\overline" },
  "\u0305": { narrow: "\\bar", wide: "\\overline" },
  "\u203e": { narrow: "\\bar", wide: "\\overline" },
  "\u20d7": { narrow: "\\vec", wide: "\\overrightarrow" },
  "\u2192": { narrow: "\\vec", wide: "\\overrightarrow" },
  "\u0362": { narrow: "\\vec", wide: "\\overrightarrow" },
  "\u20d6": { narrow: "\\overleftarrow", wide: "\\overleftarrow" },
  "\u2190": { narrow: "\\overleftarrow", wide: "\\overleftarrow" },
  "\u20e1": { narrow: "\\overleftrightarrow", wide: "\\overleftrightarrow" },
  "\u2194": { narrow: "\\overleftrightarrow", wide: "\\overleftrightarrow" },
  "\u0307": { narrow: "\\dot", wide: "\\dot" },
  "\u0308": { narrow: "\\ddot", wide: "\\ddot" },
  "\u030c": { narrow: "\\check", wide: "\\check" },
  "\u02c7": { narrow: "\\check", wide: "\\check" },
  "\u0301": { narrow: "\\acute", wide: "\\acute" },
  "\u00b4": { narrow: "\\acute", wide: "\\acute" },
  "\u0300": { narrow: "\\grave", wide: "\\grave" },
  "`": { narrow: "\\grave", wide: "\\grave" },
  "\u0306": { narrow: "\\breve", wide: "\\breve" },
};

/** Ma trận bọc ngoặc `m:d` → môi trường có sẵn của KaTeX. */
const MATRIX_ENVIRONMENTS: Readonly<Record<string, string>> = {
  "(": "pmatrix",
  "[": "bmatrix",
  "{": "Bmatrix",
  "|": "vmatrix",
  "‖": "Vmatrix",
};

/** Chữ thuần (liên tiếp các `m:t`) của một phần tử chỉ gồm các `m:r`; `null` nếu có phần tử toán khác. */
function plainText(element: Element): string | null {
  let text = "";
  for (const child of mathChildren(element)) {
    if (child.localName === "r") text += runText(child);
    else if (child.localName.endsWith("Pr")) continue;
    else return null;
  }
  return text.replace(/\s+/g, "").replace(/[\u2061\u200b]/g, "");
}

function convChildren(element: Element): string {
  let out = "";
  for (const child of mathChildren(element)) {
    if (child.localName.endsWith("Pr")) continue;
    out = cat(out, convElement(child));
  }
  return out;
}

function convArgument(parent: Element, localName: string): string {
  const child = firstChild(parent, localName);
  return child ? convChildren(child).trim() : "";
}

/** Chữ của một `m:r`: `m:t` (OMML chuẩn) hoặc chữ nằm thẳng trong `m:r` (cách Word ghi vào clipboard HTML). */
function runText(run: Element): string {
  const parts: string[] = [];
  const visit = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      parts.push(node.nodeValue ?? "");
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const element = node as Element;
      if (isMath(element) && element.localName.endsWith("Pr")) return;
      if (!isMath(element) || element.localName === "t") for (const child of element.childNodes) visit(child);
    }
  };
  for (const child of run.childNodes) visit(child);
  return parts.join("");
}

function convRun(run: Element): string {
  const text = runText(run);
  if (text === "") return "";
  const properties = firstChild(run, "rPr");
  const normal = properties ? flagValue(run, "rPr", "nor") : false;
  const style = normal ? "nor" : (propertyValue(run, "rPr", "sty") ?? "i");
  return runToLatex(text, style);
}

function convDelimiters(element: Element): string {
  const begin = propertyValue(element, "dPr", "begChr") ?? "(";
  const end = propertyValue(element, "dPr", "endChr") ?? ")";
  const separator = propertyValue(element, "dPr", "sepChr") ?? "|";
  const cells = mathChildren(element, "e");

  // Một ngoặc chỉ bao một ma trận → pmatrix/bmatrix/…: gọn hơn `\left( \begin{matrix}…\right)`.
  if (cells.length === 1) {
    const only = mathChildren(cells[0]!).filter((child) => !child.localName.endsWith("Pr"));
    const environment = MATRIX_ENVIRONMENTS[begin];
    const closing = begin === "(" ? ")" : begin === "[" ? "]" : begin === "{" ? "}" : begin;
    if (only.length === 1 && only[0]!.localName === "m" && environment && (end === closing || (begin === "|" && end === "|"))) {
      return convMatrix(only[0]!, environment);
    }
  }

  const body = cells
    .map((cell) => convChildren(cell).trim())
    .reduce((out, cell, index) => (index === 0 ? cell : `${out} ${DELIMITERS[separator] ?? separator} ${cell}`), "");
  return cat(`\\left${begin === "" ? "." : (DELIMITERS[begin] ?? begin)}`, body, `\\right${end === "" ? "." : (DELIMITERS[end] ?? end)}`);
}

function convMatrix(element: Element, environment = "matrix"): string {
  const rows = mathChildren(element, "mr").map((row) => mathChildren(row, "e").map((cell) => convChildren(cell).trim()).join(" & "));
  return `\\begin{${environment}} ${rows.join(" \\\\ ")} \\end{${environment}}`;
}

function convEquationArray(element: Element): string {
  const rows = mathChildren(element, "e").map((row) => convChildren(row).trim());
  const aligned = rows.some((row) => row.includes(ALIGN));
  const body = rows.join(" \\\\ ").replaceAll(ALIGN, "&");
  return aligned ? `\\begin{aligned} ${body} \\end{aligned}` : `\\begin{array}{l} ${body} \\end{array}`;
}

function convNary(element: Element): string {
  const character = propertyValue(element, "naryPr", "chr") ?? "∫";
  const entry = NARY_COMMANDS[character];
  if (!entry) throw new UnsupportedOmmlError(`m:nary(${character})`);
  const sub = flagValue(element, "naryPr", "subHide") ? "" : convArgument(element, "sub");
  const sup = flagValue(element, "naryPr", "supHide") ? "" : convArgument(element, "sup");
  const operand = convArgument(element, "e");
  const location = propertyValue(element, "naryPr", "limLoc");
  // Word mặc định: tổng/tích đặt cận trên/dưới, tích phân đặt cận bên cạnh; chỉ ghi lệnh khi khác mặc định của KaTeX.
  const modifier = entry.integral && location === "undOvr" ? "\\limits" : !entry.integral && location === "subSup" ? "\\nolimits" : "";
  const head = `${entry.command}${modifier}${sub ? `_${scriptBody(sub)}` : ""}${sup ? `^${scriptBody(sup)}` : ""}`;
  return operand ? `${head} ${operand}` : head;
}

function convFunction(element: Element): string {
  const nameElement = firstChild(element, "fName");
  const operand = convArgument(element, "e");
  let head = "";
  if (nameElement) {
    const text = plainText(nameElement);
    if (text !== null && /^[A-Za-z]+$/.test(text)) {
      head = FUNCTION_NAMES.includes(text) ? functionCommand(text) : `\\operatorname{${text}}`;
    } else {
      head = convChildren(nameElement).trim();
    }
  }
  return operand ? `${head} ${operand}` : head;
}

function convLimit(element: Element, below: boolean): string {
  const base = firstChild(element, "e");
  const limit = convArgument(element, "lim");
  const baseText = base ? plainText(base) : null;
  if (below && baseText !== null && FUNCTION_NAMES.includes(baseText)) {
    return `${functionCommand(baseText)}_${scriptBody(limit)}`;
  }
  const baseLatex = base ? convChildren(base).trim() : "";
  return `${below ? "\\underset" : "\\overset"}${brace(limit)}${brace(baseLatex)}`;
}

function convAccent(element: Element): string {
  const character = propertyValue(element, "accPr", "chr") ?? "\u0302";
  const accent = ACCENTS[character];
  if (!accent) throw new UnsupportedOmmlError(`m:acc(${character})`);
  const base = convArgument(element, "e");
  const single = /^(?:[A-Za-z0-9]|\\[A-Za-z]+)$/.test(base);
  return `${single ? accent.narrow : accent.wide}${brace(base)}`;
}

function convElement(element: Element): string {
  switch (element.localName) {
    case "r":
      return convRun(element);
    case "e":
    case "num":
    case "den":
    case "deg":
    case "sub":
    case "sup":
    case "lim":
    case "fName":
      return convChildren(element);
    case "f": {
      const numerator = convArgument(element, "num");
      const denominator = convArgument(element, "den");
      const type = propertyValue(element, "fPr", "type");
      if (type === "noBar") return `\\binom${brace(numerator)}${brace(denominator)}`;
      if (type === "lin") return `${brace(numerator)}/${brace(denominator)}`;
      return `\\frac${brace(numerator)}${brace(denominator)}`;
    }
    case "rad": {
      const radicand = convArgument(element, "e");
      const degree = flagValue(element, "radPr", "degHide") ? "" : convArgument(element, "deg");
      return degree ? `\\sqrt[${degree}]${brace(radicand)}` : `\\sqrt${brace(radicand)}`;
    }
    case "sSup":
      return `${scriptBase(convArgument(element, "e"))}^${scriptBody(convArgument(element, "sup"))}`;
    case "sSub":
      return `${scriptBase(convArgument(element, "e"))}_${scriptBody(convArgument(element, "sub"))}`;
    case "sSubSup":
      return `${scriptBase(convArgument(element, "e"))}_${scriptBody(convArgument(element, "sub"))}^${scriptBody(convArgument(element, "sup"))}`;
    case "d":
      return convDelimiters(element);
    case "eqArr":
      return convEquationArray(element);
    case "nary":
      return convNary(element);
    case "func":
      return convFunction(element);
    case "m":
      return convMatrix(element);
    case "acc":
      return convAccent(element);
    case "bar":
      return propertyValue(element, "barPr", "pos") === "bot"
        ? `\\underline${brace(convArgument(element, "e"))}`
        : `\\overline${brace(convArgument(element, "e"))}`;
    case "limLow":
      return convLimit(element, true);
    case "limUpp":
      return convLimit(element, false);
    default:
      throw new UnsupportedOmmlError(`m:${element.localName}`);
  }
}

/** Khai báo tiền tố cho XML: `m:` là OMML, các tiền tố khác của Word (`w:`, `o:`…) chỉ cần khai báo để XML hợp lệ. */
function wrapXml(omml: string): string {
  const prefixes = new Set<string>();
  for (const match of omml.matchAll(/<\/?([A-Za-z][\w-]*):/g)) prefixes.add(match[1]!);
  prefixes.delete("m");
  const declarations = [`xmlns:m="${OMML_NAMESPACE}"`, ...[...prefixes].map((prefix) => `xmlns:${prefix}="urn:thi360:${prefix}"`)];
  // Thực thể HTML mà XML không có (Word ghi `&nbsp;` trong chú thích): đổi sang ký tự số.
  const body = omml.replaceAll("&nbsp;", "&#160;");
  return `<root ${declarations.join(" ")}>${body}</root>`;
}

/**
 * Đổi một đoạn OMML (nội dung chú thích `<!--[if gte msEquation 12]>…<![endif]-->`) sang LaTeX.
 * @throws UnsupportedOmmlError phần tử ngoài tập con; MalformedOmmlError khi XML hỏng.
 */
export function ommlToLatex(omml: string): OmmlResult {
  // Clipboard là dữ liệu không tin cậy: OMML thật của Word không có khai báo DTD/thực thể (tránh nổ thực thể khi đọc XML).
  if (/<!(?:DOCTYPE|ENTITY)/i.test(omml)) throw new MalformedOmmlError("OMML must not declare a DTD or entities");
  const xml = new DOMParser().parseFromString(wrapXml(omml), "application/xml");
  if (xml.getElementsByTagName("parsererror").length > 0) throw new MalformedOmmlError("OMML is not well-formed XML");
  const root = xml.documentElement;

  const paragraphs = mathChildren(root, "oMathPara");
  const display = paragraphs.length > 0;
  const formulas = display ? paragraphs.flatMap((paragraph) => mathChildren(paragraph, "oMath")) : mathChildren(root, "oMath");
  if (formulas.length === 0) {
    // OMML trần không có `m:oMath` bọc ngoài (hiếm): đọc thẳng các phần tử toán ở gốc.
    const bare = convChildren(root).trim();
    if (!bare) throw new MalformedOmmlError("OMML has no formula");
    return { latex: bare.replaceAll(ALIGN, "\\&"), display: false };
  }
  const parts = formulas.map((formula) => convChildren(formula).trim()).filter((part) => part !== "");
  if (parts.length === 0) throw new MalformedOmmlError("OMML formula is empty");
  const latex = parts.length === 1 ? parts[0]! : `\\begin{gathered} ${parts.join(" \\\\ ")} \\end{gathered}`;
  return { latex: latex.replaceAll(ALIGN, "\\&"), display };
}
