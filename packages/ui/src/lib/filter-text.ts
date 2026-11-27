import { isValidElement, type ReactNode } from "react";

/**
 * Chuẩn hóa chữ để lọc: chữ thường, bỏ dấu tiếng Việt (tách tổ hợp NFD rồi bỏ dấu kết hợp; `đ` thành `d`), gộp
 * khoảng trắng. Nhờ vậy "van ban" khớp "Văn bản" và "DOC HIEU" khớp "Đọc hiểu".
 */
export function normalizeFilterText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[\u0111\u0110]/g, "d")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/** Mọi từ của câu lọc phải có trong chữ của mục (không cần đúng thứ tự). Câu lọc rỗng khớp mọi mục. */
export function matchesFilter(text: string, query: string): boolean {
  const haystack = normalizeFilterText(text);
  return normalizeFilterText(query)
    .split(" ")
    .every((word) => haystack.includes(word));
}

/** Chữ thuần của một nút React (nhãn có thẻ bọc), để lọc khi nơi gọi không truyền `textValue`. */
export function nodeText(node: ReactNode): string {
  if (node == null || typeof node === "boolean") return "";
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(nodeText).join("");
  if (isValidElement<{ children?: ReactNode }>(node)) return nodeText(node.props.children);
  return "";
}
