/**
 * Logic thuần của hộp Hóa học (spec 4.3): chuỗi "Gõ nhanh" chính là nội dung `\ce{…}` của mhchem, không có bộ chuyển
 * đổi riêng. Hộp chỉ làm hai việc trên chuỗi đó: ghép "Trên/Dưới mũi tên" vào mũi tên đầu tiên theo cú pháp
 * `->[trên][dưới]` và tách ngược điều kiện đó khi mở lại.
 */

/** Mũi tên của mhchem (`mhchem.js`, mẫu `'->'`): dạng dài viết trước dạng ngắn để khớp đủ ký tự. */
const ARROW_RE = /<=>>|<<=>|<-->|<=>|<->|->|<-|[\u2192\u27F6\u21CC]/;

export interface CeParts {
  /** Chuỗi gõ nhanh, không còn điều kiện của mũi tên đầu tiên. */
  source: string;
  /** Điều kiện trên mũi tên đầu tiên (`->[trên]`), rỗng nếu không có. */
  top: string;
  /** Điều kiện dưới mũi tên đầu tiên (`->[trên][dưới]`), rỗng nếu không có. */
  bottom: string;
}

/** Đọc nhóm `[…]` bắt đầu đúng tại `start`; ngoặc nhọn lồng nhau không kết thúc nhóm. `null` nếu không có nhóm. */
function readGroup(text: string, start: number): { content: string; end: number } | null {
  if (text[start] !== "[") return null;
  let depth = 0;
  for (let index = start + 1; index < text.length; index += 1) {
    const char = text[index];
    if (char === "{") depth += 1;
    else if (char === "}") depth -= 1;
    else if (char === "]" && depth <= 0) return { content: text.slice(start + 1, index), end: index + 1 };
  }
  return null;
}

/** Tách điều kiện của mũi tên đầu tiên (nếu có) khỏi chuỗi gõ nhanh. */
export function splitArrowConditions(text: string): CeParts {
  const arrow = ARROW_RE.exec(text);
  if (!arrow) return { source: text, top: "", bottom: "" };
  const arrowEnd = arrow.index + arrow[0].length;
  const first = readGroup(text, arrowEnd);
  if (!first) return { source: text, top: "", bottom: "" };
  const second = readGroup(text, first.end);
  const conditionsEnd = second ? second.end : first.end;
  return {
    source: text.slice(0, arrowEnd) + text.slice(conditionsEnd),
    top: first.content.trim(),
    bottom: second ? second.content.trim() : "",
  };
}

/**
 * Nội dung `\ce{…}` từ chuỗi gõ nhanh và hai ô điều kiện. Ô điều kiện có chữ thì thay điều kiện đã gõ tay trong chuỗi
 * (nếu có); cả hai ô trống thì chuỗi gõ nhanh giữ nguyên từng ký tự (chỉ cắt khoảng trắng hai đầu). Không có mũi tên
 * thì điều kiện không có chỗ gắn và bị bỏ.
 */
export function composeCe(source: string, top: string, bottom: string): string {
  const body = source.trim();
  const upper = top.trim();
  const lower = bottom.trim();
  if (!upper && !lower) return body;
  const arrow = ARROW_RE.exec(body);
  if (!arrow) return body;
  const typed = splitArrowConditions(body);
  const arrowEnd = arrow.index + arrow[0].length;
  const conditions = `[${upper || typed.top}]${lower || typed.bottom ? `[${lower || typed.bottom}]` : ""}`;
  return typed.source.slice(0, arrowEnd) + conditions + typed.source.slice(arrowEnd);
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
 * Tách công thức hóa học đã lưu về ba ô của hộp (spec 4.3, mở lại). Chỉ nhận đúng một `\ce{…}` mà ngoặc đóng khớp với
 * ngoặc mở nằm ở cuối chuỗi (`\ce{A}\text{…}`, `\ce{A} + \ce{B}` không phải), hoặc `\ce …`; LaTeX khác trả `null` để hộp
 * không đọc sai rồi ghi đè công thức.
 */
export function parseCeLatex(latex: string): CeParts | null {
  const text = latex.trim();
  let body: string;
  if (text.startsWith("\\ce{")) {
    if (closingBrace(text, 3) !== text.length - 1) return null;
    body = text.slice(4, -1);
  } else if (text.startsWith("\\ce ")) body = text.slice(4);
  else return null;
  return splitArrowConditions(body.trim());
}
