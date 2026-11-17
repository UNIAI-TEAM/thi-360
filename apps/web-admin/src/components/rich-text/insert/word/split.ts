/**
 * Tách nội dung đã dán theo nhãn (spec 4.10): "A.", "B.", "C.", "D." đầu dòng thành phương án, "Lời giải" thành lời
 * giải, phần trước thành đề bài. Nhãn phải liên tiếp theo thứ tự A, B, C, D (chữ "B." giữa đề không bị nhận nhầm);
 * bốn phương án ghi chung một dòng ("A. 1  B. 2  C. 3  D. 4") cũng được tách.
 */
import { FORMULA_ATTRIBUTE, RETYPE_ATTRIBUTE, trimBlock } from "./parse";
import { OPTION_LABELS, type OptionLabel, type SplitFields } from "./types";

const SOLUTION_HEADER = /^\s*(?:lời giải|hướng dẫn giải)\b\s*[:.]?\s*/i;
const OPTION_START = /^\s*([A-D])\s*[.)]\s*/;

interface Segment {
  node: Node;
  start: number;
  end: number;
}

/** Chữ thẳng hàng của khối: công thức giữ chỗ là một ký tự thay thế, `br` là xuống dòng. */
function linearize(block: Element): { text: string; segments: Segment[] } {
  const segments: Segment[] = [];
  let text = "";
  const visit = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const value = node.nodeValue ?? "";
      segments.push({ node, start: text.length, end: text.length + value.length });
      text += value;
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const element = node as Element;
    if (element.hasAttribute(FORMULA_ATTRIBUTE) || element.hasAttribute(RETYPE_ATTRIBUTE)) {
      segments.push({ node, start: text.length, end: text.length + 1 });
      text += "\uFFFC";
    } else if (element.localName === "br") {
      segments.push({ node, start: text.length, end: text.length + 1 });
      text += "\n";
    } else {
      for (const child of element.childNodes) visit(child);
    }
  };
  visit(block);
  return { text, segments };
}

function setBoundary(range: Range, edge: "start" | "end", segments: Segment[], offset: number) {
  const set = edge === "start" ? range.setStart.bind(range) : range.setEnd.bind(range);
  const setBefore = edge === "start" ? range.setStartBefore.bind(range) : range.setEndBefore.bind(range);
  const setAfter = edge === "start" ? range.setStartAfter.bind(range) : range.setEndAfter.bind(range);
  for (const segment of segments) {
    if (offset < segment.start || offset > segment.end) continue;
    if (segment.node.nodeType === Node.TEXT_NODE) {
      set(segment.node, offset - segment.start);
    } else if (offset === segment.start) {
      setBefore(segment.node);
    } else {
      setAfter(segment.node);
    }
    return;
  }
}

/** Đoạn mới chứa phần của `block` từ ký tự `from` tới `to` (giữ định dạng). */
function extract(block: Element, segments: Segment[], from: number, to: number): Element {
  const range = block.ownerDocument.createRange();
  range.setStart(block, 0);
  range.setEnd(block, block.childNodes.length);
  setBoundary(range, "start", segments, from);
  setBoundary(range, "end", segments, to);
  const piece = block.ownerDocument.createElement("p");
  piece.append(range.cloneContents());
  // Cắt giữa một thẻ định dạng (`<strong>A.</strong> …`) để lại vỏ rỗng: bỏ.
  const placeholderSelector = `[${FORMULA_ATTRIBUTE}], [${RETYPE_ATTRIBUTE}], br`;
  for (const wrapper of [...piece.querySelectorAll("strong, em, u, s, sub, sup, span")]) {
    if (wrapper.textContent === "" && !wrapper.matches(placeholderSelector) && wrapper.querySelector(placeholderSelector) === null) wrapper.remove();
  }
  trimBlock(piece);
  return piece;
}

function nonEmpty(piece: Element): boolean {
  return piece.textContent?.trim() !== "" || piece.querySelector(`[${FORMULA_ATTRIBUTE}], [${RETYPE_ATTRIBUTE}]`) !== null;
}

/** Nhãn tiếp theo `label` ở đâu đó sau `from` trong `text` (đứng đầu hoặc sau khoảng trắng). */
function findLabel(text: string, label: OptionLabel, from: number): { start: number; end: number } | null {
  const pattern = new RegExp(`${label}\\s*[.)]\\s*`, "g");
  pattern.lastIndex = from;
  for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
    const before = text[match.index - 1];
    if (before === undefined || /\s/.test(before)) return { start: match.index, end: match.index + match[0].length };
  }
  return null;
}

/**
 * Tách các khối cấp đầu của HTML nội bộ thành đề bài, phương án A–D và lời giải. Không tìm thấy nhãn nào thì mọi thứ
 * ở đề bài và `found = false`.
 */
export function splitByLabels(blocks: Element[]): SplitFields {
  const result: SplitFields = { stem: [], options: [], solution: [], found: false };
  let section: "stem" | "option" | "solution" = "stem";

  const push = (piece: Element) => {
    if (section === "solution") result.solution.push(piece);
    else if (section === "option") result.options[result.options.length - 1]!.blocks.push(piece);
    else result.stem.push(piece);
  };

  for (const block of blocks) {
    if (block.localName !== "p") {
      push(block);
      continue;
    }
    const { text, segments } = linearize(block);

    const header = section === "solution" ? null : SOLUTION_HEADER.exec(text.normalize("NFC"));
    if (header) {
      section = "solution";
      result.found = true;
      const rest = extract(block, segments, header[0].length, text.length);
      if (nonEmpty(rest)) result.solution.push(rest);
      continue;
    }

    const expected = OPTION_LABELS[result.options.length];
    const start = section === "solution" || expected === undefined ? null : OPTION_START.exec(text);
    if (!expected || !start || start[1] !== expected) {
      push(block);
      continue;
    }

    // Tìm tiếp các nhãn kế tiếp trên cùng dòng, rồi cắt khối theo các nhãn đó.
    const marks: { label: OptionLabel; contentStart: number; labelStart: number }[] = [
      { label: expected, labelStart: 0, contentStart: start[0].length },
    ];
    for (let index = result.options.length + 1; index < OPTION_LABELS.length; index++) {
      const previous = marks[marks.length - 1]!;
      const found = findLabel(text, OPTION_LABELS[index]!, previous.contentStart);
      if (!found) break;
      marks.push({ label: OPTION_LABELS[index]!, labelStart: found.start, contentStart: found.end });
    }
    section = "option";
    result.found = true;
    marks.forEach((mark, index) => {
      const next = marks[index + 1];
      result.options.push({ label: mark.label, blocks: [extract(block, segments, mark.contentStart, next ? next.labelStart : text.length)] });
    });
  }
  return result;
}

/** Không tách: mọi khối vào đề bài. */
export function keepWhole(blocks: Element[]): SplitFields {
  return { stem: blocks, options: [], solution: [], found: false };
}
