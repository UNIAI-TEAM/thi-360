import type { Attribute } from "@tiptap/core";
import type { DOMOutputSpec } from "@tiptap/pm/model";

/**
 * Thuộc tính `html`: toàn bộ innerHTML của khối (bảng, hình, đoạn trích). Node là atom nên
 * nội dung lưu nguyên dạng chuỗi đã lọc — không qua schema ProseMirror — nhờ đó xuất lại
 * byte-exact theo bảng mẫu. `rendered: false` để không rơi thành thuộc tính DOM.
 */
export const innerHtmlAttribute: Attribute = {
  default: "",
  parseHTML: (element) => element.innerHTML,
  rendered: false,
};

/** Phần `renderHTML` của một node atom: thẻ ngoài + thuộc tính ngoài, rồi gán lại innerHTML. */
export function renderAtomDom(
  tagName: "figure" | "blockquote",
  outerAttributes: Record<string, string | undefined>,
  innerHtml: string,
): DOMOutputSpec {
  const element = document.createElement(tagName);
  for (const [name, value] of Object.entries(outerAttributes)) {
    if (value !== undefined) element.setAttribute(name, value);
  }
  element.innerHTML = innerHtml;
  return element;
}
