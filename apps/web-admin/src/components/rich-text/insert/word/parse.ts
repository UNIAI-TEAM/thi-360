/**
 * Đọc HTML clipboard của Word (spec 4.10): nhận diện Word, đổi OMML trong chú thích `<!--[if gte msEquation 12]>`
 * sang LaTeX, làm sạch phần còn lại về các thẻ của danh sách cho phép, tách ảnh. Chạy trên `clipboardData` gốc *trước*
 * bước lọc HTML của trình soạn (bước đó bỏ chú thích, nơi Word đặt OMML).
 *
 * Kết quả là HTML nội bộ (`ParsedWord.html`): công thức là chỗ giữ `span[data-wf]`/`span[data-wr]`; bước
 * `materialize` mới dựng HTML hợp đồng.
 */
import { MalformedOmmlError, UnsupportedOmmlError, ommlToLatex } from "./omml";
import type { ParsedWord, WordFormulaItem, WordImage } from "./types";

/** Thuộc tính giữ chỗ cho công thức đã đổi được / cần gõ lại. */
export const FORMULA_ATTRIBUTE = "data-wf";
export const RETYPE_ATTRIBUTE = "data-wr";
/** Mốc tạm của ảnh thường trong lúc đọc (bỏ trước khi trả kết quả). */
const IMAGE_MARKER = "data-wimg";

/** Nhãn nhỏ của giữ chỗ: đoạn chữ quanh công thức/ảnh để người soạn nhận ra. */
const CONTEXT_LENGTH = 60;

/** Thẻ bỏ cả cây (đầu trang, kiểu dáng, XML của Office). */
const SKIPPED_TAGS = new Set(["head", "style", "script", "meta", "link", "title", "xml", "o:p", "template", "noscript"]);
/** Thẻ khối: kết thúc đoạn đang mở. */
const FLOW_TAGS = new Set(["div", "section", "article", "main", "header", "footer", "center", "address", "pre", "blockquote", "form", "body", "html"]);
const HEADING = /^h[1-6]$/;

const EQUATION_COMMENT = /^\[if gte msEquation \d+\]>([\s\S]*?)(?:<!\[endif\])?$/;
const OLE_SHAPE = /<o:OLEObject\b[^>]*>/gi;
/** MathType, Equation Editor 3.0, Microsoft Equation: đối tượng nhúng là công thức dạng ảnh. */
const EQUATION_PROGID = /equation|mathtype|dsmt/i;
/** Ảnh nhỏ nằm giữa dòng chữ (cao ≤ 36pt) coi là ảnh chụp công thức. */
const INLINE_FORMULA_MAX_HEIGHT_PX = 48;

interface Context {
  out: Document;
  formulas: WordFormulaItem[];
  images: WordImage[];
  /** `ShapeID` của đối tượng OLE → `ProgID`, để biết `<img v:shapes="…">` có phải công thức. */
  oleShapes: Record<string, string>;
  /** Công thức vừa gặp có ảnh dự phòng `<![if !msEquation]>…<![endif]>` ngay sau: bỏ nó. */
  equationFallbackPending: boolean;
  /** Chữ nhãn danh sách `<![if !supportLists]>…<![endif]>` của đoạn đang đọc. */
  listMarker: string;
  idCounter: number;
}

function nextId(ctx: Context, prefix: string): string {
  ctx.idCounter += 1;
  return `${prefix}${ctx.idCounter}`;
}

function collapseSpace(text: string): string {
  return text.replace(/[\s\u00a0]+/g, " ");
}

function styleOf(element: Element): string {
  return (element.getAttribute("style") ?? "").toLowerCase();
}

/** Mọi đối tượng OLE của tài liệu: `ShapeID` → `ProgID`. */
function collectOleShapes(document_: Document): Record<string, string> {
  const shapes: Record<string, string> = {};
  const walker = document_.createTreeWalker(document_, NodeFilter.SHOW_COMMENT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const data = (node as Comment).data;
    if (!data.includes("OLEObject")) continue;
    for (const match of data.matchAll(OLE_SHAPE)) {
      const progId = /ProgID\s*=\s*["']([^"']+)["']/i.exec(match[0])?.[1];
      const shapeId = /ShapeID\s*=\s*["']([^"']+)["']/i.exec(match[0])?.[1];
      if (progId && shapeId) shapes[shapeId] = progId;
    }
  }
  return shapes;
}

function pixelSize(image: Element, axis: "width" | "height"): number | null {
  const attribute = image.getAttribute(axis);
  if (attribute && /^\d+(?:\.\d+)?$/.test(attribute)) return Number(attribute);
  const match = new RegExp(`(?:^|;)\\s*${axis}\\s*:\\s*([\\d.]+)\\s*(pt|px)?`, "i").exec(image.getAttribute("style") ?? "");
  if (!match) return null;
  const value = Number(match[1]);
  return match[2]?.toLowerCase() === "pt" ? (value * 4) / 3 : value;
}

/** Ảnh `<img>` này là công thức (MathType/ảnh chụp công thức) hay ảnh thường. */
function isFormulaImage(image: Element, ctx: Context): boolean {
  const shape = image.getAttribute("v:shapes");
  const progId = shape ? ctx.oleShapes[shape] : undefined;
  if (progId && EQUATION_PROGID.test(progId)) return true;
  const height = pixelSize(image, "height");
  if (height === null || height > INLINE_FORMULA_MAX_HEIGHT_PX) return false;
  const block = image.closest("p, li, td, th, div");
  const surrounding = (block?.textContent ?? "").replace(/\s+/g, "");
  return surrounding.length > 0;
}

/** Chữ ngay trước `mark` trong đoạn chứa nó (hoặc chữ đầu đoạn nếu `mark` đứng đầu), cắt gọn. */
function contextBefore(mark: Element | null): string {
  const block = mark?.closest("p, li, td, th");
  if (!mark || !block) return "";
  const range = block.ownerDocument.createRange();
  range.setStart(block, 0);
  range.setEndBefore(mark);
  const before = collapseSpace(range.toString()).trim();
  if (before !== "") return before.length > CONTEXT_LENGTH ? `…${before.slice(-CONTEXT_LENGTH)}` : before;
  const text = collapseSpace(block.textContent ?? "").trim();
  return text.length > CONTEXT_LENGTH ? `${text.slice(0, CONTEXT_LENGTH)}…` : text;
}

/** Bỏ các nút anh em từ vị trí `index` tới chú thích `[endif]`; trả vị trí chú thích đó (hoặc cuối nếu không có) và chữ đã bỏ. */
function skipThroughEndif(siblings: readonly Node[], index: number): { next: number; text: string } {
  let text = "";
  let cursor = index + 1;
  for (; cursor < siblings.length; cursor++) {
    const node = siblings[cursor]!;
    if (node.nodeType === Node.COMMENT_NODE && (node as Comment).data.trim() === "[endif]") break;
    text += node.textContent ?? "";
  }
  return { next: Math.min(cursor, siblings.length - 1), text };
}

/**
 * Xử lý một chú thích điều kiện của Word. Trả vị trí nút cuối đã dùng (>= `index`). `emit` chèn giữ chỗ công thức
 * vào đúng chỗ (đoạn đang mở).
 */
function handleComment(siblings: readonly Node[], index: number, ctx: Context, emit: (placeholder: HTMLElement) => void): number {
  const data = (siblings[index] as Comment).data;
  const equation = EQUATION_COMMENT.exec(data);
  if (equation) {
    const placeholder = ctx.out.createElement("span");
    let item: WordFormulaItem;
    try {
      const { latex, display } = ommlToLatex(equation[1] ?? "");
      item = { kind: "formula", id: nextId(ctx, "w"), latex, display };
      placeholder.setAttribute(FORMULA_ATTRIBUTE, item.id);
    } catch (error) {
      if (!(error instanceof UnsupportedOmmlError) && !(error instanceof MalformedOmmlError)) throw error;
      item = {
        kind: "retype",
        id: nextId(ctx, "w"),
        cause: error instanceof UnsupportedOmmlError ? "unsupported" : "malformed",
        ...(error instanceof UnsupportedOmmlError ? { detail: error.element } : {}),
        context: "",
      };
      placeholder.setAttribute(RETYPE_ATTRIBUTE, item.id);
    }
    ctx.formulas.push(item);
    emit(placeholder);
    ctx.equationFallbackPending = true;
    return index;
  }
  if (/^\[if !msEquation\]$/.test(data.trim()) && ctx.equationFallbackPending) {
    ctx.equationFallbackPending = false;
    return skipThroughEndif(siblings, index).next;
  }
  if (/^\[if !supportLists\]$/.test(data.trim())) {
    const skipped = skipThroughEndif(siblings, index);
    ctx.listMarker = collapseSpace(skipped.text).trim();
    return skipped.next;
  }
  return index;
}

function appendText(dest: Element, text: string, ctx: Context) {
  const collapsed = collapseSpace(text);
  if (collapsed !== "") dest.append(ctx.out.createTextNode(collapsed));
}

/** Các thẻ định dạng suy ra từ `style` của `span` Word (không có `<b>` thật). */
function styleWrappers(element: Element): string[] {
  const style = styleOf(element);
  const tags: string[] = [];
  if (/font-weight\s*:\s*(?:bold|[6-9]00)/.test(style)) tags.push("strong");
  if (/font-style\s*:\s*italic/.test(style)) tags.push("em");
  if (/text-decoration[^;]*underline/.test(style) && !/text-decoration\s*:\s*none/.test(style)) tags.push("u");
  if (/text-decoration[^;]*line-through/.test(style)) tags.push("s");
  const align = /vertical-align\s*:\s*(super|sub)/.exec(style)?.[1];
  if (align) tags.push(align === "super" ? "sup" : "sub");
  return tags;
}

function convertInlineImage(image: Element, dest: Element, ctx: Context) {
  if (isFormulaImage(image, ctx)) {
    const item: WordFormulaItem = { kind: "retype", id: nextId(ctx, "w"), cause: "image", context: "" };
    ctx.formulas.push(item);
    const placeholder = ctx.out.createElement("span");
    placeholder.setAttribute(RETYPE_ATTRIBUTE, item.id);
    dest.append(placeholder);
    return;
  }
  const entry: WordImage = { id: nextId(ctx, "i"), alt: (image.getAttribute("alt") ?? "").trim(), context: "" };
  ctx.images.push(entry);
  // Mốc tạm để biết ảnh nằm sau chữ nào; `parseWordHtml` bỏ mốc sau khi lấy chữ quanh.
  const marker = ctx.out.createElement("span");
  marker.setAttribute(IMAGE_MARKER, entry.id);
  dest.append(marker);
}

function convertInline(nodes: readonly Node[], dest: Element, ctx: Context) {
  for (let index = 0; index < nodes.length; index++) {
    const node = nodes[index]!;
    if (node.nodeType === Node.TEXT_NODE) {
      appendText(dest, node.nodeValue ?? "", ctx);
    } else if (node.nodeType === Node.COMMENT_NODE) {
      index = handleComment(nodes, index, ctx, (placeholder) => dest.append(placeholder));
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      convertInlineElement(node as Element, dest, ctx);
    }
  }
}

function wrapAndConvert(element: Element, tags: string[], dest: Element, ctx: Context) {
  let target = dest;
  for (const tag of tags) {
    const wrapper = ctx.out.createElement(tag);
    target.append(wrapper);
    target = wrapper;
  }
  convertInline([...element.childNodes], target, ctx);
}

function convertInlineElement(element: Element, dest: Element, ctx: Context) {
  const tag = element.localName;
  if (SKIPPED_TAGS.has(tag)) return;
  if (tag.includes(":")) {
    // Thẻ có tiền tố (`v:shape`, `w:sdt`, `o:lock`…): bỏ; thẻ thông minh (`st1:place`…) giữ chữ bên trong.
    if (/^st\d+:/.test(tag)) wrapAndConvert(element, [], dest, ctx);
    return;
  }
  switch (tag) {
    case "br":
      dest.append(ctx.out.createElement("br"));
      return;
    case "b":
    case "strong":
      wrapAndConvert(element, ["strong"], dest, ctx);
      return;
    case "i":
    case "em":
    case "cite":
    case "var":
      wrapAndConvert(element, ["em"], dest, ctx);
      return;
    case "u":
    case "ins":
      wrapAndConvert(element, ["u"], dest, ctx);
      return;
    case "s":
    case "strike":
    case "del":
      wrapAndConvert(element, ["s"], dest, ctx);
      return;
    case "sub":
    case "sup":
      wrapAndConvert(element, [tag], dest, ctx);
      return;
    case "img":
      convertInlineImage(element, dest, ctx);
      return;
    case "span":
      wrapAndConvert(element, styleWrappers(element), dest, ctx);
      return;
    default:
      // `a`, `font`, `label`… giữ chữ, bỏ vỏ. Thẻ khối lọt vào dòng chữ thì cũng chỉ lấy chữ.
      wrapAndConvert(element, [], dest, ctx);
  }
}

interface ListFrame {
  level: number;
  ordered: boolean;
  list: Element;
}

/** Nhãn danh sách của Word: số → có thứ tự; chữ cái đơn ("a)", "A.") → giữ nhãn dạng chữ, không thành danh sách. */
function listKind(marker: string): "ordered" | "bullet" | "label" {
  if (/^\(?\d+[.)]?$/.test(marker)) return "ordered";
  if (/^\(?[A-Za-z][.)]$/.test(marker)) return "label";
  return "bullet";
}

function isListParagraph(element: Element): boolean {
  return /mso-list\s*:\s*(?!none)\S/.test(styleOf(element));
}

function listLevel(element: Element): number {
  return Number(/level(\d+)/i.exec(styleOf(element))?.[1] ?? 1);
}

function convertTable(table: Element, dest: Element, ctx: Context) {
  const out = ctx.out.createElement("table");
  const body = ctx.out.createElement("tbody");
  out.append(body);
  const rows: Element[] = [];
  for (const child of table.children) {
    if (child.localName === "tr") rows.push(child);
    else if (["thead", "tbody", "tfoot"].includes(child.localName)) rows.push(...[...child.children].filter((row) => row.localName === "tr"));
  }
  for (const row of rows) {
    const outRow = ctx.out.createElement("tr");
    for (const cell of row.children) {
      if (cell.localName !== "td" && cell.localName !== "th") continue;
      const outCell = ctx.out.createElement(cell.localName);
      for (const span of ["colspan", "rowspan"]) {
        const value = Number(cell.getAttribute(span));
        if (Number.isInteger(value) && value > 1) outCell.setAttribute(span, String(value));
      }
      convertContainer([...cell.childNodes], outCell, ctx);
      outRow.append(outCell);
    }
    if (outRow.children.length > 0) body.append(outRow);
  }
  if (body.children.length > 0) dest.append(out);
}

/** Một đoạn mang `mso-list`: thành mục danh sách (hoặc đoạn chữ có nhãn nếu nhãn là chữ cái). */
function convertListParagraph(element: Element, dest: Element, stack: ListFrame[], ctx: Context) {
  ctx.listMarker = "";
  const paragraph = ctx.out.createElement("p");
  convertInline([...element.childNodes], paragraph, ctx);
  const marker = ctx.listMarker;
  ctx.listMarker = "";

  if (listKind(marker) === "label") {
    stack.length = 0;
    paragraph.prepend(ctx.out.createTextNode(`${marker} `));
    dest.append(paragraph);
    return;
  }

  const ordered = listKind(marker) === "ordered";
  const level = listLevel(element);
  while (stack.length > 0 && stack[stack.length - 1]!.level > level) stack.pop();
  const top = stack[stack.length - 1];
  if (!top || top.level < level || top.ordered !== ordered) {
    if (top && top.level === level) stack.pop();
    const list = ctx.out.createElement(ordered ? "ol" : "ul");
    const parentList = stack[stack.length - 1];
    (parentList?.list.lastElementChild ?? dest).append(list);
    stack.push({ level, ordered, list });
  }
  const item = ctx.out.createElement("li");
  item.append(paragraph);
  stack[stack.length - 1]!.list.append(item);
}

function convertContainer(nodes: readonly Node[], dest: Element, ctx: Context) {
  let paragraph: Element | null = null;
  const stack: ListFrame[] = [];
  const openParagraph = (): Element => {
    if (!paragraph) {
      paragraph = ctx.out.createElement("p");
      dest.append(paragraph);
    }
    return paragraph;
  };

  for (let index = 0; index < nodes.length; index++) {
    const node = nodes[index]!;
    if (node.nodeType === Node.TEXT_NODE) {
      if (collapseSpace(node.nodeValue ?? "").trim() !== "") appendText(openParagraph(), node.nodeValue ?? "", ctx);
      else if (paragraph) appendText(paragraph, node.nodeValue ?? "", ctx);
      continue;
    }
    if (node.nodeType === Node.COMMENT_NODE) {
      index = handleComment(nodes, index, ctx, (placeholder) => openParagraph().append(placeholder));
      continue;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) continue;

    const element = node as Element;
    const tag = element.localName;
    if (SKIPPED_TAGS.has(tag)) continue;

    if (tag === "p" || HEADING.test(tag)) {
      paragraph = null;
      if (isListParagraph(element)) {
        convertListParagraph(element, dest, stack, ctx);
        continue;
      }
      stack.length = 0;
      const out = ctx.out.createElement("p");
      convertInline([...element.childNodes], out, ctx);
      dest.append(out);
    } else if (tag === "ul" || tag === "ol") {
      paragraph = null;
      stack.length = 0;
      const list = ctx.out.createElement(tag);
      for (const item of element.children) {
        if (item.localName !== "li") continue;
        const outItem = ctx.out.createElement("li");
        convertContainer([...item.childNodes], outItem, ctx);
        list.append(outItem);
      }
      dest.append(list);
    } else if (tag === "table") {
      paragraph = null;
      stack.length = 0;
      convertTable(element, dest, ctx);
    } else if (FLOW_TAGS.has(tag)) {
      paragraph = null;
      stack.length = 0;
      convertContainer([...element.childNodes], dest, ctx);
    } else if (tag.includes(":") && !/^st\d+:/.test(tag)) {
      continue;
    } else {
      // Thẻ dòng chữ ở cấp khối (Word Online đặt `span` trực tiếp trong thân).
      convertInlineElement(element, openParagraph(), ctx);
    }
  }
}

/** Điểm mốc trong đoạn không phải chữ: chặn việc gộp/cắt khoảng trắng đi xuyên qua công thức giữ chỗ, ảnh, ngắt dòng. */
const BOUNDARY = `[${FORMULA_ATTRIBUTE}], [${RETYPE_ATTRIBUTE}], [${IMAGE_MARKER}], br`;

/** Bỏ khoảng trắng đầu/cuối đoạn, gộp khoảng trắng thừa giữa các nút chữ liền nhau (không xuyên qua công thức/ảnh/br). */
export function trimBlock(block: Element) {
  const items: (Text | Element)[] = [];
  const walker = block.ownerDocument.createTreeWalker(block, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
    acceptNode: (node) => (node.nodeType === Node.TEXT_NODE || (node as Element).matches(BOUNDARY) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP),
  });
  for (let node = walker.nextNode(); node; node = walker.nextNode()) items.push(node as Text | Element);

  let trimNext = true;
  for (const item of items) {
    if (item.nodeType !== Node.TEXT_NODE) {
      trimNext = (item as Element).localName === "br";
      continue;
    }
    const text = item as Text;
    if (trimNext) text.data = text.data.replace(/^ /, "");
    if (text.data !== "") trimNext = text.data.endsWith(" ");
  }
  for (let index = items.length - 1; index >= 0; index--) {
    const item = items[index]!;
    if (item.nodeType !== Node.TEXT_NODE) break;
    const text = item as Text;
    if (text.data === "") continue;
    text.data = text.data.replace(/ $/, "");
    break;
  }
  for (const item of items) if (item.nodeType === Node.TEXT_NODE && (item as Text).data === "") item.remove();
}

/** Khối không còn chữ, công thức giữ chỗ hay ngắt dòng thì bỏ. */
function isEmptyBlock(block: Element): boolean {
  return block.textContent?.trim() === "" && block.querySelector(`[${FORMULA_ATTRIBUTE}], [${RETYPE_ATTRIBUTE}]`) === null;
}

function finalize(root: Element) {
  for (const block of [...root.querySelectorAll("p")]) {
    trimBlock(block);
    if (isEmptyBlock(block) && block.closest("td, th") === null) block.remove();
  }
  for (const wrapper of [...root.querySelectorAll("li")]) {
    if (wrapper.childElementCount === 0 && wrapper.textContent?.trim() === "") wrapper.remove();
  }
  for (const list of [...root.querySelectorAll("ul, ol")]) if (list.childElementCount === 0) list.remove();
}

/**
 * Đọc HTML clipboard của Word. Không kiểm tra `isWordHtml` (người gọi quyết định); chạy được trên HTML bất kỳ và chỉ
 * không tìm thấy công thức nếu không có chú thích OMML.
 */
export function parseWordHtml(html: string): ParsedWord {
  const input = new DOMParser().parseFromString(html, "text/html");
  const out = document.implementation.createHTMLDocument("");
  const ctx: Context = {
    out,
    formulas: [],
    images: [],
    oleShapes: collectOleShapes(input),
    equationFallbackPending: false,
    listMarker: "",
    idCounter: 0,
  };
  convertContainer([...input.body.childNodes], out.body, ctx);
  finalize(out.body);

  for (const item of ctx.formulas) {
    if (item.kind === "retype") item.context = contextBefore(out.body.querySelector(`[${RETYPE_ATTRIBUTE}="${item.id}"]`));
  }
  for (const image of ctx.images) {
    const marker = out.body.querySelector(`[${IMAGE_MARKER}="${image.id}"]`);
    image.context = contextBefore(marker);
    marker?.remove();
  }
  for (const block of out.body.querySelectorAll("p")) trimBlock(block);
  return { html: out.body.innerHTML, formulas: ctx.formulas, images: ctx.images };
}
