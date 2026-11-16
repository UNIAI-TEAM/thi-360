/**
 * Lọc HTML phía trình duyệt, mirror `QuestionHtmlSanitizer` của backend (spec 4.2, UNI-2617).
 * Backend lọc lại khi lưu và trả 422 `QUESTION_CONTENT_UNSAFE` khi có vi phạm; lớp này giữ cho
 * HTML trình soạn sinh ra và HTML dán vào đã nằm trong danh sách cho phép ngay từ đầu.
 *
 * Khác backend: không tra được tài nguyên qua `QuestionAssetResolver` nên `img` chỉ được kiểm
 * `data-asset-id` là UUID hợp lệ; `src` phải rỗng hoặc là đường nội bộ (`/…`, không `//…`),
 * đúng như `attributeAllowed`/`isSafeInternalSrc` của backend.
 */

const ALLOWED_TAGS = new Set([
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
]);

const ALLOWED_TAGS_NO_IMG = new Set([...ALLOWED_TAGS].filter((tag) => tag !== "img"));

const MATH_CLASSES = new Set(["math-inline", "math-block"]);

/** jsoup Cleaner bỏ nội dung chữ/data của các thẻ bị loại này; giữ cho DOMParser gần tương đương. */
const DROP_SUBTREE_TAGS = new Set(["script", "style", "template", "iframe", "object", "embed"]);

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface SanitizeViolation {
  tag: string;
  attribute: string | null;
  value: string | null;
}

export interface SanitizeResult {
  /** HTML đã lọc, tương đương `Result.html` của backend (không viết lại `src` của `img`). */
  html: string;
  /** Các thẻ/thuộc tính bị bỏ, tương đương `Result.violations` của backend. */
  violations: SanitizeViolation[];
}

/** `attributeAllowed` của backend: `on*`/`style` cấm trên mọi thẻ, còn lại theo bảng của spec 4.2. */
function attributeAllowed(tag: string, name: string, value: string): boolean {
  if (name.startsWith("on") || name === "style") return false;
  switch (tag) {
    case "img":
      if (name === "src") {
        const trimmed = value.trim();
        return trimmed === "" || (trimmed.startsWith("/") && !trimmed.startsWith("//"));
      }
      return name === "alt" || name === "width" || name === "height" || name === "data-asset-id";
    case "span":
      return name === "class" && MATH_CLASSES.has(value.trim());
    case "td":
    case "th":
      return name === "colspan" || name === "rowspan";
    default:
      return false;
  }
}

function tagViolation(tag: string): SanitizeViolation {
  return { tag, attribute: null, value: null };
}

function attributeViolation(tag: string, name: string, value: string): SanitizeViolation {
  return { tag, attribute: name, value };
}

/**
 * Duyệt cây đã parse: ghi vi phạm, bỏ thuộc tính không được phép, mở bọc thẻ ngoài danh sách
 * (giữ phần tử con hợp lệ như jsoup Cleaner) và bỏ cả nhánh của các thẻ mang nội dung nguy hiểm.
 * `img` còn lại phải mang `data-asset-id` là UUID — tương đương `rewriteImages` của backend.
 */
function visit(element: Element, allowedTags: Set<string>, allowImages: boolean, violations: SanitizeViolation[]) {
  for (const child of [...element.childNodes]) {
    if (child.nodeType === Node.COMMENT_NODE) {
      child.remove();
      continue;
    }
    if (child.nodeType !== Node.ELEMENT_NODE) continue;

    const el = child as Element;
    const tag = el.tagName.toLowerCase();

    if (DROP_SUBTREE_TAGS.has(tag)) {
      violations.push(tagViolation(tag));
      el.remove();
      continue;
    }

    if (!allowedTags.has(tag)) {
      violations.push(tagViolation(tag));
      visit(el, allowedTags, allowImages, violations);
      el.replaceWith(...el.childNodes);
      continue;
    }

    for (const attribute of [...el.attributes]) {
      const name = attribute.name.toLowerCase();
      if (!attributeAllowed(tag, name, attribute.value)) {
        violations.push(attributeViolation(tag, name, attribute.value));
        el.removeAttribute(attribute.name);
      }
    }

    if (tag === "img" && allowImages) {
      const rawId = (el.getAttribute("data-asset-id") ?? "").trim();
      if (!UUID_PATTERN.test(rawId)) {
        violations.push(attributeViolation("img", "data-asset-id", rawId));
      }
    }

    visit(el, allowedTags, allowImages, violations);
  }
}

function parseDocument(html: string): Document {
  return new DOMParser().parseFromString(html ?? "", "text/html");
}

/**
 * Lọc một trường HTML. `allowImages=false` coi mọi `img` là vi phạm (mirror
 * `QuestionHtmlSanitizer.clean(html, false)`, dùng cho nội dung chỉ chữ như hướng dẫn làm bài).
 */
export function sanitizeQuestionHtml(html: string, { allowImages = true }: { allowImages?: boolean } = {}): SanitizeResult {
  const document = parseDocument(html);
  const violations: SanitizeViolation[] = [];

  // Thuộc tính trên html/head/body bị loại giống backend (phần tử vỏ không mang vào kết quả).
  for (const shell of [document.documentElement, document.head, document.body]) {
    if (!shell) continue;
    for (const attribute of [...shell.attributes]) {
      violations.push(attributeViolation(shell.tagName.toLowerCase(), attribute.name.toLowerCase(), attribute.value));
    }
  }

  const allowedTags = allowImages ? ALLOWED_TAGS : ALLOWED_TAGS_NO_IMG;
  visit(document.body, allowedTags, allowImages, violations);
  return { html: document.body.innerHTML, violations };
}

/** Chữ thuần của một đoạn HTML, mirror `QuestionHtmlSanitizer.textOf`. */
export function questionHtmlText(html: string): string {
  if (!html) return "";
  const document = parseDocument(html);
  for (const el of [...document.querySelectorAll("script, style, template")]) el.remove();
  return document.body.textContent ?? "";
}

/**
 * Kiểm tra cặp dấu `\( … \)` và `\[ … \]` trong chữ thuần của HTML — mirror `balanced` của
 * `QuestionBodyValidator` (quy tắc NO_GAPS). `true` khi có dấu mở hoặc đóng không khớp.
 */
export function hasUnbalancedMath(html: string): boolean {
  const text = questionHtmlText(html);
  const stack: string[] = [];
  for (let i = 0; i + 1 < text.length; i++) {
    if (text[i] !== "\\") continue;
    const delimiter = text[i + 1];
    if (delimiter === "(" || delimiter === "[") {
      stack.push(delimiter);
      i++;
    } else if (delimiter === ")" || delimiter === "]") {
      const open = stack.pop();
      if (open !== (delimiter === ")" ? "(" : "[")) return true;
      i++;
    }
  }
  return stack.length > 0;
}
