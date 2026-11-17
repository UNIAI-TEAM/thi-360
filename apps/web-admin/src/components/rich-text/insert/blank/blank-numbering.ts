import type { Node as PMNode } from "@tiptap/pm/model";
import { BLANK_NODE } from "../../extensions/names";

/**
 * Chỗ trống đánh số `span.blank[data-blank="n"]` (spec 3.2): `data-blank` = n trỏ tới câu con thứ n theo thứ tự hiện
 * tại (3.5). HTML đã lưu không nhất thiết đúng từng byte dạng chuẩn của bảng mẫu (thứ tự thuộc tính, khoảng trắng, nháy
 * đơn, lớp phụ) nên thẻ được đọc theo thuộc tính chứ không so chuỗi; chỉ thẻ chỗ trống bị đổi số mới được ghi lại đúng
 * dạng chuẩn `<span class="blank" data-blank="n"></span>`, mọi byte còn lại của HTML giữ nguyên.
 */
const SPAN_ELEMENT = /<span\b((?:[^>"']|"[^"]*"|'[^']*')*)>[^<]*<\/span>/gi;
const ATTRIBUTE = /([^\s"'<>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;

const blankSpan = (number: number) => `<span class="blank" data-blank="${number}"></span>`;

/** Số `data-blank` của thẻ `<span …>` có lớp `blank` và `data-blank` là số nguyên dương; không phải chỗ trống thì `null`. */
function blankNumberOfAttributes(attributes: string): number | null {
  let isBlank = false;
  let number: number | null = null;
  for (const [, name, quoted, single, bare] of attributes.matchAll(ATTRIBUTE)) {
    const value = quoted ?? single ?? bare ?? "";
    const key = name!.toLowerCase();
    if (key === "class") isBlank = value.split(/\s+/).includes("blank");
    else if (key === "data-blank" && /^\s*\d+\s*$/.test(value)) number = Number(value);
  }
  return isBlank ? number : null;
}

/** Thay từng chỗ trống bằng `replace(số, thẻ gốc)`; thẻ khác giữ nguyên. */
function mapBlanks(html: string, replace: (number: number, span: string) => string): string {
  return html.replace(SPAN_ELEMENT, (span, attributes: string) => {
    const number = blankNumberOfAttributes(attributes);
    return number === null ? span : replace(number, span);
  });
}

/** Số `data-blank` của các chỗ trống trong HTML, theo thứ tự xuất hiện. */
export function blankNumbersOfHtml(html: string): number[] {
  const numbers: number[] = [];
  mapBlanks(html, (number, span) => {
    numbers.push(number);
    return span;
  });
  return numbers;
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
  return mapBlanks(html, (number, span) => {
    const position = number - 1;
    let next = position;
    if (position === from) next = to;
    else if (from < to && position > from && position <= to) next = position - 1;
    else if (from > to && position >= to && position < from) next = position + 1;
    return next === position ? span : blankSpan(next + 1);
  });
}

/**
 * Xóa câu con ở vị trí `removed` (tính từ 0): chỗ trống của nó bị xóa, số lớn hơn giảm 1 (spec 3.5). Chỉ chỗ trống bị
 * xóa; chữ quanh nó giữ nguyên.
 */
export function renumberBlanksAfterRemove(html: string, removed: number): string {
  return mapBlanks(html, (number, span) => {
    if (number === removed + 1) return "";
    return number > removed + 1 ? blankSpan(number - 1) : span;
  });
}
