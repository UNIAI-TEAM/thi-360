/**
 * Logic thuần của hộp Hóa học (spec 4.3): chuỗi "Gõ nhanh" chính là nội dung `\ce{…}` của mhchem, không có bộ chuyển
 * đổi riêng. Điều kiện của mọi mũi tên nằm ngay trong chuỗi theo cú pháp `->[trên][dưới]`; hộp chỉ đọc chúng ra theo
 * từng mũi tên và ghi lại đúng nhóm `[…][…]` của mũi tên đó khi người soạn sửa ô Trên/Dưới.
 */

/** Mũi tên của mhchem (`mhchem.js`, mẫu `'->'`): dạng dài viết trước dạng ngắn để khớp đủ ký tự. */
const ARROW_SOURCE = "<=>>|<<=>|<-->|<=>|<->|->|<-|[\\u2192\\u27F6\\u21CC]";

/** Một mũi tên trong chuỗi gõ nhanh cùng điều kiện gõ ngay sau nó. */
export interface CeArrow {
  /** Mũi tên như đã gõ (`->`, `<=>`, `→`…). */
  arrow: string;
  /** Nội dung nhóm `[…]` thứ nhất (trên mũi tên), giữ nguyên từng ký tự; rỗng nếu không có. */
  top: string;
  /** Nội dung nhóm `[…]` thứ hai (dưới mũi tên); rỗng nếu không có. */
  bottom: string;
  /** Vị trí ngay sau mũi tên. */
  arrowEnd: number;
  /** Vị trí ngay sau nhóm điều kiện cuối (bằng `arrowEnd` khi không có điều kiện). */
  end: number;
}

/**
 * Đọc nhóm `[…]` bắt đầu đúng tại `start`; `]` trong ngoặc nhọn không kết thúc nhóm. Ngoặc nhọn chưa đóng (đang gõ dở
 * `\text{`) thì nhóm kết thúc ở `]` đầu tiên để chữ đang gõ không biến mất. `null` nếu không có nhóm.
 */
function readGroup(text: string, start: number): { content: string; end: number } | null {
  if (text[start] !== "[") return null;
  let depth = 0;
  let firstClose = -1;
  for (let index = start + 1; index < text.length; index += 1) {
    const char = text[index];
    if (char === "{") depth += 1;
    else if (char === "}") depth -= 1;
    else if (char === "]") {
      if (depth <= 0) return { content: text.slice(start + 1, index), end: index + 1 };
      if (firstClose === -1) firstClose = index;
    }
  }
  return firstClose === -1 ? null : { content: text.slice(start + 1, firstClose), end: firstClose + 1 };
}

/** Mọi mũi tên của chuỗi gõ nhanh theo thứ tự, kèm điều kiện `[trên][dưới]` gõ ngay sau từng mũi tên. */
export function findArrows(body: string): CeArrow[] {
  const pattern = new RegExp(ARROW_SOURCE, "g");
  const arrows: CeArrow[] = [];
  for (let match = pattern.exec(body); match; match = pattern.exec(body)) {
    const arrowEnd = match.index + match[0].length;
    const first = readGroup(body, arrowEnd);
    const second = first ? readGroup(body, first.end) : null;
    const end = second?.end ?? first?.end ?? arrowEnd;
    arrows.push({ arrow: match[0], top: first?.content ?? "", bottom: second?.content ?? "", arrowEnd, end });
    // Mũi tên nằm trong điều kiện (`->[A->B]`) không phải mũi tên của phương trình.
    pattern.lastIndex = end;
  }
  return arrows;
}

/** Nội dung một nhóm `[…]`: chữ có `]` làm đóng nhóm sớm thì bọc trong `{…}` để mhchem và `findArrows` đọc đủ. */
function group(text: string): string {
  return `[${readGroup(`[${text}]`, 0)?.content === text ? text : `{${text}}`}]`;
}

/**
 * Ghi điều kiện của mũi tên thứ `index` (đếm từ 0) vào chuỗi gõ nhanh, thay nhóm `[…][…]` đang có của nó; phần còn lại
 * giữ nguyên từng ký tự. Cả hai rỗng thì bỏ nhóm; chỉ có dưới thì `->[][dưới]`. Không có mũi tên đó thì trả nguyên chuỗi.
 */
export function setArrowConditions(body: string, index: number, top: string, bottom: string): string {
  const arrow = findArrows(body)[index];
  if (!arrow) return body;
  const conditions = top || bottom ? group(top) + (bottom ? group(bottom) : "") : "";
  return body.slice(0, arrow.arrowEnd) + conditions + body.slice(arrow.end);
}

/** Ký hiệu hiển thị của một mũi tên mhchem trong nhãn "Mũi tên 1 (→)". */
export function arrowGlyph(arrow: string): string {
  switch (arrow) {
    case "->":
      return "→";
    case "<-":
      return "←";
    case "<->":
      return "↔";
    case "<-->":
      return "⟷";
    case "<=>":
    case "<=>>":
    case "<<=>":
      return "⇌";
    default:
      return arrow;
  }
}

/** `\ce{body}` — `body` đã trim. */
export function ceLatex(body: string): string {
  return `\\ce{${body}}`;
}

/** `true` khi LaTeX của một công thức là hóa học (cùng điều kiện với `isChemistry` của registry). */
export function isCeLatex(latex: string): boolean {
  return latex.startsWith("\\ce{") || latex.startsWith("\\ce ");
}

/** Vị trí `}` đóng nhóm mở tại `open` (`text[open] === "{"`), bỏ qua ký tự sau `\`; `-1` nếu nhóm không đóng. */
function closingBrace(text: string, open: number): number {
  let depth = 0;
  for (let index = open; index < text.length; index += 1) {
    const char = text[index];
    if (char === "\\") index += 1;
    else if (char === "{") depth += 1;
    else if (char === "}" && --depth === 0) return index;
  }
  return -1;
}

/**
 * Chuỗi gõ nhanh của công thức hóa học đã lưu (spec 4.3, mở lại), kể cả điều kiện của mọi mũi tên. Chỉ nhận đúng một
 * `\ce{…}` mà ngoặc đóng khớp với ngoặc mở nằm ở cuối chuỗi (`\ce{A}\text{…}`, `\ce{A} + \ce{B}` không phải), hoặc
 * `\ce …`; LaTeX khác trả `null` để hộp không đọc sai rồi ghi đè công thức.
 */
export function parseCeLatex(latex: string): string | null {
  const text = latex.trim();
  if (text.startsWith("\\ce{")) {
    if (closingBrace(text, 3) !== text.length - 1) return null;
    return text.slice(4, -1).trim();
  }
  if (text.startsWith("\\ce ")) return text.slice(4).trim();
  return null;
}
