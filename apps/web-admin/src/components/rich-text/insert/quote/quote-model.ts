/**
 * Mô hình dữ liệu của hộp Đoạn trích (spec 4.7): bản nháp của `blockquote.quote` và bộ xuất/đọc HTML.
 * Xuất bằng chuỗi để ra đúng từng byte như bảng mẫu `quote-*`.
 */

export type QuoteKind = "poem" | "prose" | "source";

export interface QuoteDraft {
  kind: QuoteKind;
  /** Nội dung gõ trong ô: dòng trống tách khổ (thơ) hoặc đoạn (văn xuôi, tư liệu). */
  content: string;
  /** Thơ: mỗi dòng thành `br`; tắt thì mỗi khổ là một dòng liền. */
  keepBreaks: boolean;
  /** Thơ: `data-line-numbers="5"`. */
  numberLines: boolean;
  author: string;
  work: string;
  source: string;
  /** `footer` viết trong ngoặc đơn như bảng mẫu `quote-poem`; mẫu `quote-footer-then-source` không có ngoặc. */
  footerParens: boolean;
}

export function createQuoteDraft(): QuoteDraft {
  return {
    kind: "poem",
    content: "",
    keepBreaks: true,
    numberLines: true,
    author: "",
    work: "",
    source: "",
    footerParens: true,
  };
}

export function hasQuoteContent(draft: QuoteDraft): boolean {
  return draft.content.trim().length > 0;
}

function escapeText(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** Chia nội dung thành khổ/đoạn theo dòng trống; mỗi phần là danh sách dòng đã bỏ khoảng trắng hai đầu. */
function splitBlocks(content: string): string[][] {
  const blocks: string[][] = [];
  let current: string[] = [];
  for (const raw of content.replace(/\r\n?/g, "\n").split("\n")) {
    const line = raw.trim();
    if (line === "") {
      if (current.length > 0) blocks.push(current);
      current = [];
    } else {
      current.push(line);
    }
  }
  if (current.length > 0) blocks.push(current);
  return blocks;
}

/** Thuộc tính của node atom `quote` (`kind`, `lineNumbers`). Văn xuôi và tư liệu không đánh số dòng. */
export function quoteAttributes(draft: QuoteDraft): { kind: QuoteKind; lineNumbers: "0" | "5" } {
  return { kind: draft.kind, lineNumbers: draft.kind === "poem" && draft.numberLines ? "5" : "0" };
}

/** innerHTML của khối: các `p`, rồi `footer` và `p.figure-source` nếu có. */
export function serializeQuoteInner(draft: QuoteDraft): string {
  const breakLines = draft.kind === "poem" && draft.keepBreaks;
  const paragraphs = splitBlocks(draft.content)
    .map((lines) => `<p>${lines.map(escapeText).join(breakLines ? "<br>" : " ")}</p>`)
    .join("");

  const author = draft.author.trim();
  const work = draft.work.trim();
  const credit = [author ? escapeText(author) : "", work ? `<cite>${escapeText(work)}</cite>` : ""].filter(Boolean).join(", ");
  const footer = credit ? `<footer>${draft.footerParens ? `(${credit})` : credit}</footer>` : "";

  const source = draft.source.trim();
  return paragraphs + footer + (source ? `<p class="figure-source">${escapeText(source)}</p>` : "");
}

/** Cả thẻ ngoài, dùng để lọc và xem trước. */
export function serializeQuote(draft: QuoteDraft): string {
  const { kind, lineNumbers } = quoteAttributes(draft);
  return `<blockquote class="quote" data-kind="${kind}" data-line-numbers="${lineNumbers}">${serializeQuoteInner(draft)}</blockquote>`;
}

/** innerHTML của `blockquote.quote` sau khi lọc; `null` khi bộ lọc đã bỏ cả khối. */
export function unwrapQuote(quoteHtml: string): string | null {
  const match = /^<blockquote [^>]*>([\s\S]*)<\/blockquote>$/.exec(quoteHtml);
  return match ? match[1]! : null;
}

function isKind(value: string): value is QuoteKind {
  return value === "poem" || value === "prose" || value === "source";
}

/** Chữ của một khổ: `br` thành xuống dòng, thẻ nội tuyến chỉ giữ chữ. */
function blockText(element: Element): string {
  let text = "";
  for (const node of [...element.childNodes]) {
    text += node.nodeName === "BR" ? "\n" : (node.textContent ?? "");
  }
  return text;
}

/** Đọc thuộc tính và innerHTML của node atom `quote` về bản nháp để sửa lại. */
export function parseQuote(attrs: Readonly<Record<string, unknown>>): QuoteDraft {
  const draft = createQuoteDraft();
  const kind = String(attrs.kind ?? "");
  draft.kind = isKind(kind) ? kind : "prose";
  draft.numberLines = draft.kind === "poem" && String(attrs.lineNumbers ?? "") === "5";
  draft.footerParens = true;

  const template = document.createElement("template");
  template.innerHTML = String(attrs.html ?? "");
  const blocks: string[] = [];
  for (const child of [...template.content.children]) {
    const tag = child.tagName.toLowerCase();
    if (tag === "p" && child.classList.contains("figure-source")) {
      draft.source = child.textContent?.trim() ?? "";
    } else if (tag === "p") {
      blocks.push(blockText(child));
    } else if (tag === "footer") {
      const cite = child.querySelector("cite");
      draft.work = cite?.textContent?.trim() ?? "";
      const rest = child.cloneNode(true) as HTMLElement;
      rest.querySelector("cite")?.remove();
      const text = (rest.textContent ?? "").trim();
      draft.footerParens = text.startsWith("(") || text.endsWith(")");
      draft.author = text.replace(/^\(/, "").replace(/\)$/, "").replace(/,\s*$/, "").trim();
    }
  }
  draft.content = blocks.join("\n\n");
  return draft;
}
