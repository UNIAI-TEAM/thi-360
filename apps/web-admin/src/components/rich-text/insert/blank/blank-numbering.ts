import type { Node as PMNode } from "@tiptap/pm/model";
import { BLANK_NODE } from "../../extensions/names";

/**
 * Chỗ trống đánh số `<span class="blank" data-blank="n"></span>` (spec 3.2): `data-blank` = n trỏ tới câu con thứ n
 * theo thứ tự hiện tại (3.5). Dạng chuẩn của bảng mẫu là chuỗi dưới đây nên đổi số bằng thay chuỗi, không dựng lại DOM:
 * mọi byte còn lại của HTML giữ nguyên.
 */
const BLANK_SPAN = /<span class="blank" data-blank="(\d+)"><\/span>/g;

/** Số `data-blank` của các chỗ trống trong HTML, theo thứ tự xuất hiện. */
export function blankNumbersOfHtml(html: string): number[] {
  return Array.from(html.matchAll(BLANK_SPAN), (match) => Number(match[1]));
}

/** Số `data-blank` của các chỗ trống trong tài liệu của trình soạn, bỏ node ở `exceptPos`. */
export function blankNumbersOfDoc(doc: PMNode, exceptPos?: number): number[] {
  const numbers: number[] = [];
  doc.descendants((node, pos) => {
    if (node.type.name === BLANK_NODE && pos !== exceptPos && typeof node.attrs.blank === "number") numbers.push(node.attrs.blank);
    return true;
  });
  return numbers;
}

/** Câu con (đánh số từ 1) đầu tiên trong `childCount` câu chưa có chỗ trống; `null` khi không còn câu nào. */
export function firstChildWithoutBlank(childCount: number, usedNumbers: readonly number[]): number | null {
  const used = new Set(usedNumbers);
  for (let number = 1; number <= childCount; number += 1) {
    if (!used.has(number)) return number;
  }
  return null;
}

/**
 * Đổi thứ tự câu con: câu ở vị trí `from` (tính từ 0) chuyển tới `to`; chỗ trống của nó đi theo, chỗ trống của các câu
 * con bị dịch chỗ được đánh lại (spec 3.5).
 */
export function renumberBlanksAfterMove(html: string, from: number, to: number): string {
  return html.replace(BLANK_SPAN, (span, value: string) => {
    const position = Number(value) - 1;
    let next = position;
    if (position === from) next = to;
    else if (from < to && position > from && position <= to) next = position - 1;
    else if (from > to && position >= to && position < from) next = position + 1;
    return next === position ? span : `<span class="blank" data-blank="${next + 1}"></span>`;
  });
}

/**
 * Xóa câu con ở vị trí `removed` (tính từ 0): chỗ trống của nó bị xóa, số lớn hơn giảm 1 (spec 3.5). Chỉ chỗ trống bị
 * xóa; chữ quanh nó giữ nguyên.
 */
export function renumberBlanksAfterRemove(html: string, removed: number): string {
  return html.replace(BLANK_SPAN, (span, value: string) => {
    const number = Number(value);
    if (number === removed + 1) return "";
    return number > removed + 1 ? `<span class="blank" data-blank="${number - 1}"></span>` : span;
  });
}
