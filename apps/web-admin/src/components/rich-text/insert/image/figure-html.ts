import { FIGURE_WIDTHS } from "../../html-contract";

export type FigureWidth = "small" | "medium" | "full";

/** Hình đã tải lên: `id` đi vào `data-asset-id`, `url` vào `src` (máy chủ viết lại `src` từ id). */
export interface FigureAsset {
  id: string;
  url: string;
}

/**
 * Dữ liệu một khối `figure.image`. `captionHtml`/`sourceHtml` là innerHTML gốc của chú thích/nguồn: hộp chỉ sửa
 * chữ thuần, nên chỗ nào người dùng không đổi chữ thì giữ nguyên định dạng nội tuyến (công thức, chỉ số) có sẵn.
 */
export interface FigureState {
  asset: FigureAsset | null;
  alt: string;
  caption: string;
  captionHtml: string | null;
  source: string;
  sourceHtml: string | null;
  width: FigureWidth;
  zoom: boolean;
}

/**
 * Chữ mở đầu dòng nguồn trong HTML lưu (spec 3.2: `p.figure-source` là `Nguồn: …`). Đây là nội dung lưu, không đổi theo
 * ngôn ngữ giao diện; viết bằng mã thoát Unicode để mã nguồn không chứa chữ có dấu ngoài tệp dịch.
 */
export const FIGURE_SOURCE_PREFIX = "Ngu\u1ED3n: ";

export function isFigureWidth(value: unknown): value is FigureWidth {
  return typeof value === "string" && FIGURE_WIDTHS.has(value);
}

function element(tag: string, attributes: Record<string, string> = {}): HTMLElement {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) el.setAttribute(name, value);
  return el;
}

/** innerHTML của `figure.image`: `img` (src, alt, data-asset-id), `figcaption`, `p.figure-source` — hai phần sau tùy chọn. */
export function buildFigureHtml(state: FigureState): string {
  const holder = element("div");
  if (!state.asset) return holder.innerHTML;
  holder.append(element("img", { src: state.asset.url, alt: state.alt.trim(), "data-asset-id": state.asset.id }));
  const caption = state.caption.trim();
  if (caption) {
    const figcaption = element("figcaption");
    if (state.captionHtml !== null && state.caption === textOf(state.captionHtml)) figcaption.innerHTML = state.captionHtml;
    else figcaption.textContent = caption;
    holder.append(figcaption);
  }
  const source = state.source.trim();
  if (source) {
    const paragraph = element("p", { class: "figure-source" });
    if (state.sourceHtml !== null && `${FIGURE_SOURCE_PREFIX}${state.source}` === textOf(state.sourceHtml)) paragraph.innerHTML = state.sourceHtml;
    else paragraph.textContent = `${FIGURE_SOURCE_PREFIX}${source}`;
    holder.append(paragraph);
  }
  return holder.innerHTML;
}

function textOf(html: string): string {
  const holder = element("div");
  holder.innerHTML = html;
  return holder.textContent ?? "";
}

/** Đọc lại một khối `figure.image` đã lưu (attrs của node) để mở lại hộp. */
export function parseFigure(attrs: Record<string, unknown>): FigureState {
  const holder = element("div");
  holder.innerHTML = typeof attrs.html === "string" ? attrs.html : "";
  const img = holder.querySelector(":scope > img");
  const id = img?.getAttribute("data-asset-id") ?? "";
  const url = img?.getAttribute("src") ?? "";
  const figcaption = holder.querySelector(":scope > figcaption");
  const sourceParagraph = holder.querySelector(":scope > p.figure-source");
  const sourceText = sourceParagraph?.textContent ?? "";
  const source = sourceText.startsWith(FIGURE_SOURCE_PREFIX) ? sourceText.slice(FIGURE_SOURCE_PREFIX.length) : sourceText;
  return {
    asset: id && url ? { id, url } : null,
    alt: img?.getAttribute("alt") ?? "",
    caption: figcaption?.textContent ?? "",
    captionHtml: figcaption ? figcaption.innerHTML : null,
    source,
    // Chỉ giữ nguyên innerHTML khi nó bắt đầu bằng chữ mở đầu chuẩn; dạng khác thì dựng lại từ chữ thuần.
    sourceHtml: sourceParagraph && sourceText.startsWith(FIGURE_SOURCE_PREFIX) ? sourceParagraph.innerHTML : null,
    width: isFigureWidth(attrs.width) ? attrs.width : "medium",
    zoom: attrs.zoom !== "false",
  };
}
