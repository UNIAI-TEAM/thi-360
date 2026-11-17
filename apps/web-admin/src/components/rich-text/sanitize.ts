/**
 * Lọc HTML phía trình duyệt, mirror `QuestionHtmlSanitizer` của backend (spec 3.2–3.3, UNI-2640).
 * Backend lọc lại khi lưu và trả 422 `QUESTION_CONTENT_UNSAFE` khi có vi phạm; lớp này giữ cho
 * HTML trình soạn sinh ra và HTML dán vào đã nằm trong danh sách cho phép ngay từ đầu.
 *
 * Hai lượt kiểm giống backend: danh sách phẳng (`attributeAllowed`) rồi lượt theo phần tử
 * (`checkElementStructure` trong `html-contract.ts`). Phần tử đã bị báo không báo tiếp con;
 * `figure`/`blockquote` vi phạm bị bỏ cả khối; `span.blank` và các thẻ sai vị trí/cấu trúc
 * bị bỏ cả phần tử; còn lại chỉ bỏ thuộc tính vi phạm.
 *
 * Khác backend: không tra được tài nguyên qua `QuestionAssetResolver` nên `img` chỉ được kiểm
 * `data-asset-id` là UUID hợp lệ; `src` phải rỗng hoặc là đường nội bộ (`/…`, không `//…`),
 * đúng như `attributeAllowed`/`isSafeInternalSrc` của backend.
 */

import {
  ALLOWED_TAGS,
  attributeAllowed,
  attributeViolation,
  checkElementStructure,
  DROP_SUBTREE_TAGS,
  tagViolation,
  UUID_PATTERN,
} from "./html-contract";

const ALLOWED_TAGS_NO_IMG = new Set([...ALLOWED_TAGS].filter((tag) => tag !== "img"));

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

/** `figure`/`blockquote`/`span.blank` vi phạm thì bỏ cả phần tử, không giữ vỏ hay thuộc tính đã sửa. */
function removesWholeElement(el: Element, tag: string): boolean {
  return tag === "figure" || tag === "blockquote" || el.matches("span.blank");
}

/**
 * Duyệt cây đã parse: ghi vi phạm, bỏ thuộc tính không được phép, mở bọc thẻ ngoài danh sách
 * (giữ phần tử con hợp lệ như jsoup Cleaner) và bỏ cả nhánh của các thẻ mang nội dung nguy hiểm.
 * `img` còn lại phải mang `data-asset-id` là UUID — tương đương `rewriteImages` của backend.
 */
function visit(element: Node, allowedTags: Set<string>, allowImages: boolean, violations: SanitizeViolation[]) {
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

    // Lượt phẳng: thuộc tính ngoài danh sách hoặc sai giá trị bị loại; phần tử có vi phạm
    // không bị kiểm tiếp ở lượt cấu trúc, và `figure`/`blockquote`/`span.blank` bị bỏ cả khối.
    let reported = false;
    for (const attribute of [...el.attributes]) {
      const name = attribute.name.toLowerCase();
      if (attributeAllowed(tag, name, attribute.value)) continue;
      violations.push(attributeViolation(tag, name, attribute.value));
      if (removesWholeElement(el, tag)) {
        el.remove();
        break;
      }
      el.removeAttribute(attribute.name);
      reported = true;
    }
    if (el.parentNode === null) continue;

    if (tag === "img" && allowImages) {
      const rawId = (el.getAttribute("data-asset-id") ?? "").trim();
      if (!UUID_PATTERN.test(rawId)) {
        violations.push(attributeViolation("img", "data-asset-id", rawId));
        reported = true;
      }
    }

    // Con được lọc trước, rồi mới kiểm cấu trúc của cha (figure.image thiếu img do con bị bỏ).
    visit(el, allowedTags, allowImages, violations);
    if (reported) continue;

    // Vi phạm thuộc tính theo ngữ cảnh (figure.cross mang data-width, span thiếu data-blank…):
    // bỏ thuộc tính — hoặc bỏ cả khối với figure/blockquote/span.blank.
    const attrViolation = checkElementStructure(el, element.nodeType !== Node.ELEMENT_NODE, "attr");
    if (attrViolation) {
      violations.push(attrViolation);
      if (removesWholeElement(el, tag)) {
        el.remove();
        continue;
      }
      if (attrViolation.attribute) el.removeAttribute(attrViolation.attribute);
      continue;
    }

    const structureViolation = checkElementStructure(el, element.nodeType !== Node.ELEMENT_NODE, "structure");
    if (structureViolation) {
      violations.push(structureViolation);
      el.remove();
    }
  }
}

/**
 * Parse đầu vào như `Jsoup.parseBodyFragment`: nội dung đi vào `template.content` nên các thẻ
 * bình thường của `<head>` (`script`, `style`, `title`…) ở đầu chuỗi không bị đẩy khỏi body như
 * khi parse tài liệu đầy đủ, và hàng/cell bảng lẻ giữ nguyên vị trí (ngữ cảnh lồng của template).
 */
function parseFragment(html: string): DocumentFragment {
  const template = document.createElement("template");
  template.innerHTML = html ?? "";
  return template.content;
}

/** Chữ HTML của fragment — gồm cả thẻ ở mọi mức lồng (innerHTML của DocumentFragment). */
function fragmentHtml(fragment: DocumentFragment): string {
  const host = document.createElement("div");
  host.append(fragment.cloneNode(true));
  return host.innerHTML;
}

/**
 * Lọc một trường HTML. `allowImages=false` coi mọi `img` là vi phạm (mirror
 * `QuestionHtmlSanitizer.clean(html, false)`, dùng cho nội dung chỉ chữ như hướng dẫn làm bài).
 */
export function sanitizeQuestionHtml(html: string, { allowImages = true }: { allowImages?: boolean } = {}): SanitizeResult {
  const fragment = parseFragment(html);
  const violations: SanitizeViolation[] = [];

  // Thuộc tính trên html/head/body bị loại giống backend (phần tử vỏ không mang vào kết quả):
  // parse tài liệu đầy đủ một lần nữa chỉ để đọc thuộc tính vỏ — backend báo chúng là vi phạm.
  const document_ = new DOMParser().parseFromString(html ?? "", "text/html");
  for (const shell of [document_.documentElement, document_.head, document_.body]) {
    if (!shell) continue;
    for (const attribute of [...shell.attributes]) {
      violations.push(attributeViolation(shell.tagName.toLowerCase(), attribute.name.toLowerCase(), attribute.value));
    }
  }

  const allowedTags = allowImages ? ALLOWED_TAGS : ALLOWED_TAGS_NO_IMG;
  visit(fragment, allowedTags, allowImages, violations);
  return { html: fragmentHtml(fragment), violations };
}

/** Chữ thuần của một đoạn HTML, mirror `QuestionHtmlSanitizer.textOf`. */
export function questionHtmlText(html: string): string {
  if (!html) return "";
  const fragment = parseFragment(html);
  for (const el of [...fragment.querySelectorAll("script, style, template")]) el.remove();
  const host = document.createElement("div");
  host.append(fragment);
  return host.textContent ?? "";
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
