/**
 * Hợp đồng HTML của nội dung câu hỏi (spec 3.2–3.3, UNI-2640): danh sách đóng của thẻ,
 * class, thuộc tính `data-*` và luật cấu trúc — giống hệt phía Java
 * (`QuestionHtmlSanitizer` + lượt kiểm theo phần tử). `sanitize.ts` dùng bảng này cho cả
 * lọc khi dán và lọc khi dựng/xuất.
 */

import type { SanitizeViolation } from "./sanitize";

/** Thẻ được phép trong mọi nội dung. `img` bị loại khi trường có `allowImages=false`. */
export const ALLOWED_TAGS = new Set([
  "p",
  "br",
  "strong",
  "em",
  "u",
  "s",
  "sub",
  "sup",
  "ul",
  "ol",
  "li",
  "table",
  "thead",
  "tbody",
  "tr",
  "th",
  "td",
  "span",
  "img",
  "figure",
  "figcaption",
  "blockquote",
  "footer",
  "cite",
]);

/** jsoup Cleaner bỏ nội dung chữ/data của các thẻ bị loại này; giữ cho DOMParser gần tương đương. */
export const DROP_SUBTREE_TAGS = new Set(["script", "style", "template", "iframe", "object", "embed"]);

export const MATH_CLASSES = new Set(["math-inline", "math-block"]);
export const FIGURE_CLASSES = new Set(["data-table", "image", "cross"]);
export const PARAGRAPH_CLASSES = new Set(["figure-note", "figure-source"]);
export const TD_CLASSES = new Set(["num"]);
export const FIGURE_WIDTHS = new Set(["small", "medium", "full"]);
export const FIGURE_ZOOMS = new Set(["true", "false"]);
export const QUOTE_KINDS = new Set(["poem", "prose", "source"]);
export const QUOTE_LINE_NUMBERS = new Set(["0", "5"]);
export const TH_SCOPES = new Set(["col", "row"]);
export const UNDERLINE_LABEL_PATTERN = /^[A-H]$/;
export const BLANK_NUMBER_PATTERN = /^(?:[1-9]|1[0-9]|20)$/;
const INTEGER_PATTERN = /^\d+$/;

export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Danh sách phẳng `attributeAllowed` của backend: `on*`/`style` cấm trên mọi thẻ, còn lại
 * theo thẻ. Chỉ nhìn được một thuộc tính một lần — luật cần ngữ cảnh (phụ thuộc `class`
 * hay vị trí cha/con) nằm trong `checkElementStructure`.
 */
export function attributeAllowed(tag: string, name: string, value: string): boolean {
  if (name.startsWith("on") || name === "style") return false;
  const trimmed = value.trim();
  switch (tag) {
    case "img":
      if (name === "src") {
        return trimmed === "" || (trimmed.startsWith("/") && !trimmed.startsWith("//"));
      }
      return name === "alt" || name === "width" || name === "height" || name === "data-asset-id";
    case "span":
      if (name === "class") return MATH_CLASSES.has(trimmed) || trimmed === "blank";
      // Giá trị `data-blank` không kiểm được ở lượt phẳng (cần biết `class`) → lượt cấu trúc.
      return name === "data-blank";
    case "u":
      return name === "data-label" && UNDERLINE_LABEL_PATTERN.test(trimmed);
    case "td":
      if (name === "class") return TD_CLASSES.has(trimmed);
      return (name === "colspan" || name === "rowspan") && INTEGER_PATTERN.test(trimmed);
    case "th":
      if (name === "scope") return TH_SCOPES.has(trimmed);
      return (name === "colspan" || name === "rowspan") && INTEGER_PATTERN.test(trimmed);
    case "p":
      return name === "class" && PARAGRAPH_CLASSES.has(trimmed);
    case "figure":
      if (name === "class") return FIGURE_CLASSES.has(trimmed);
      if (name === "data-width") return FIGURE_WIDTHS.has(trimmed);
      if (name === "data-zoom") return FIGURE_ZOOMS.has(trimmed);
      return false;
    case "blockquote":
      if (name === "class") return trimmed === "quote";
      if (name === "data-kind") return QUOTE_KINDS.has(trimmed);
      if (name === "data-line-numbers") return QUOTE_LINE_NUMBERS.has(trimmed);
      return false;
    default:
      return false;
  }
}

export function tagViolation(tag: string): SanitizeViolation {
  return { tag, attribute: null, value: null };
}

export function attributeViolation(tag: string, name: string, value: string): SanitizeViolation {
  return { tag, attribute: name, value };
}

function isElement(node: Node | null | undefined, tag?: string): node is Element {
  return node?.nodeType === Node.ELEMENT_NODE && (tag === undefined || (node as Element).tagName.toLowerCase() === tag);
}

function elementChildren(element: Element, tag?: string): Element[] {
  return [...element.children].filter((child) => tag === undefined || child.tagName.toLowerCase() === tag);
}

/**
 * Lượt kiểm theo phần tử của spec 3.3, chạy sau lượt phẳng: `el` chỉ còn thuộc tính hợp lệ
 * và con đã qua lọc. Trả vi phạm đầu tiên (hoặc `null`); gọi với `attr` để chỉ lấy vi phạm
 * thuộc tính, `structure` để chỉ lấy vi phạm cấu trúc.
 */
export function checkElementStructure(
  el: Element,
  parentIsRoot: boolean,
  kind: "attr" | "structure",
): SanitizeViolation | null {
  const tag = el.tagName.toLowerCase();
  const attr = kind === "attr" ? (name: string) => attributeViolation(tag, name, el.getAttribute(name) ?? "") : null;
  const structural = kind === "structure" ? () => tagViolation(tag) : null;

  switch (tag) {
    case "figure": {
      const figureClass = (el.getAttribute("class") ?? "").trim();
      // `data-width`/`data-zoom` chỉ thuộc figure.image (sai giá trị đã bị lượt phẳng loại).
      if (figureClass !== "image" && (el.hasAttribute("data-width") || el.hasAttribute("data-zoom"))) {
        return attr?.(el.hasAttribute("data-width") ? "data-width" : "data-zoom") ?? null;
      }
      // Chỉ đứng ở cấp đầu của nội dung — không trong td, li, figure, blockquote.
      if (!parentIsRoot) return structural?.() ?? null;
      const children = elementChildren(el);
      switch (figureClass) {
        case "image": {
          const [first, ...rest] = children;
          return isElement(first, "img") &&
            rest.every(
              (child, index) =>
                (index === 0 && child.tagName.toLowerCase() === "figcaption" && child.classList.length === 0) ||
                child.matches("p.figure-source"),
            )
            ? null
            : (structural?.() ?? null);
        }
        case "data-table": {
          const tables = children.filter((child) => child.tagName.toLowerCase() === "table");
          const legal =
            tables.length === 1 &&
            children.every(
              (child, index) =>
                child.tagName.toLowerCase() === "table" ||
                (index === 0 && child.tagName.toLowerCase() === "figcaption" && child.classList.length === 0) ||
                child.matches("p.figure-note") ||
                child.matches("p.figure-source"),
            );
          return legal ? null : (structural?.() ?? null);
        }
        case "cross":
          return children.length === 1 && isElement(children[0], "table") ? null : (structural?.() ?? null);
        default:
          return structural?.() ?? null;
      }
    }
    case "blockquote": {
      if ((el.getAttribute("data-kind") ?? "") !== "poem" && el.getAttribute("data-line-numbers") !== "0") {
        return attr?.("data-line-numbers") ?? null;
      }
      if (!parentIsRoot) return structural?.() ?? null;
      const children = elementChildren(el);
      const paragraphs = children.filter((child) => child.tagName.toLowerCase() === "p");
      const footers = children.filter((child) => child.tagName.toLowerCase() === "footer");
      const sources = children.filter((child) => child.matches("p.figure-source"));
      const plainCount = paragraphs.length - sources.length;
      const legal =
        plainCount >= 1 &&
        children.length === paragraphs.length + footers.length &&
        // Một hay nhiều `p` thường (không class), rồi `footer` tùy chọn, rồi `p.figure-source` tùy chọn.
        children.every(
          (child, index) =>
            (index < plainCount &&
              child.tagName.toLowerCase() === "p" &&
              child.classList.length === 0) ||
            (index === plainCount && child.tagName.toLowerCase() === "footer") ||
            child.matches("p.figure-source"),
        ) &&
        sources.every((source) => children.indexOf(source) === children.length - 1);
      return legal ? null : (structural?.() ?? null);
    }
    case "figcaption":
      return isElement(el.parentElement, "figure") ? null : (structural?.() ?? null);
    case "footer":
      return isElement(el.parentElement) && el.parentElement.matches("blockquote.quote")
        ? null
        : (structural?.() ?? null);
    case "cite":
      return isElement(el.parentElement, "footer") ? null : (structural?.() ?? null);
    case "p": {
      const pClass = (el.getAttribute("class") ?? "").trim();
      if (pClass !== "" && !(isElement(el.parentElement, "figure") || isElement(el.parentElement, "blockquote"))) {
        return attr?.("class") ?? null;
      }
      return null;
    }
    case "span": {
      const isBlank = (el.getAttribute("class") ?? "").trim() === "blank";
      if (!isBlank) {
        return el.hasAttribute("data-blank") ? (attr?.("data-blank") ?? null) : null;
      }
      // span.blank: data-blank bắt buộc, số nguyên 1–20, không có con (kể cả chữ).
      const value = el.getAttribute("data-blank");
      if (value === null || !BLANK_NUMBER_PATTERN.test(value.trim())) {
        return attr?.("data-blank") ?? null;
      }
      return el.childNodes.length === 0 ? null : (structural?.() ?? null);
    }
    default:
      return null;
  }
}

/**
 * Một đoạn `<p></p>` rỗng ở cuối: TipTap (TrailingNode) tự thêm sau khối atom cuối, trình
 * soạn phải bỏ đi trước khi phát `onChange` để không lưu đoạn thừa. Chỉ xử lý đúng một đoạn
 * rỗng cuối chuỗi (chấp nhận cả `<p>` kèm thuộc tính phòng trình duyệt tự thêm).
 */
const TRAILING_EMPTY_P_PATTERN = /<p(?:\s[^>]*)?>(?:\s|&nbsp;|<br\s*\/?>)*<\/p>\s*$/i;

export function stripTrailingEmptyParagraph(html: string): string {
  return html.replace(TRAILING_EMPTY_P_PATTERN, "");
}
