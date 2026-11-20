/**
 * Dựng HTML hợp đồng (spec 3.1) từ các khối nội bộ của `parseWordHtml`: chỗ giữ công thức đổi thành
 * `<span class="math-inline">\( … \)</span>` (hoặc `span.math-block` khi cả đoạn cấp đầu chỉ có một công thức dòng riêng),
 * rồi đi qua đúng bộ lọc và schema của trình soạn nên khớp từng byte với dạng mà trình soạn xuất ra.
 */
import { stripTrailingEmptyParagraph, sanitizeQuestionHtml } from "@thi360/ui/rich-text";
import { Editor } from "@tiptap/core";
import { createRichTextExtensions } from "../../extensions";
import { FORMULA_ATTRIBUTE, RETYPE_ATTRIBUTE } from "./parse";

export interface ResolvedFormula {
  latex: string;
  /** Công thức dòng riêng (`m:oMathPara`). */
  display: boolean;
}

/** `null`: bỏ chỗ này (công thức đã "Bỏ" hoặc chưa xử lý). */
export type FormulaResolver = (id: string) => ResolvedFormula | null;

/** Các khối cấp đầu của HTML nội bộ. */
export function topLevelBlocks(html: string): Element[] {
  return [...new DOMParser().parseFromString(html, "text/html").body.children];
}

const PLACEHOLDER = `[${FORMULA_ATTRIBUTE}], [${RETYPE_ATTRIBUTE}]`;

/** Thay giữ chỗ công thức bằng span công thức của hợp đồng; trả HTML chưa chuẩn hóa. */
export function materializeBlocks(blocks: Element[], resolve: FormulaResolver): string {
  const host = document.createElement("div");
  for (const block of blocks) {
    const copy = block.cloneNode(true) as Element;
    let displayOnly: ResolvedFormula | null = null;
    for (const placeholder of [...(copy.matches(PLACEHOLDER) ? [copy] : []), ...copy.querySelectorAll(PLACEHOLDER)]) {
      const id = placeholder.getAttribute(FORMULA_ATTRIBUTE) ?? placeholder.getAttribute(RETYPE_ATTRIBUTE) ?? "";
      const formula = resolve(id);
      if (!formula) {
        placeholder.remove();
        continue;
      }
      const span = document.createElement("span");
      span.className = "math-inline";
      span.textContent = `\\(${formula.latex}\\)`;
      placeholder.replaceWith(span);
      if (formula.display) displayOnly = formula;
    }
    // Đoạn cấp đầu chỉ chứa một công thức dòng riêng → `span.math-block` ở cấp đầu (như node `mathBlock`).
    const only = copy.localName === "p" && copy.childNodes.length === 1 && copy.firstElementChild?.classList.contains("math-inline");
    if (only && displayOnly) {
      const span = document.createElement("span");
      span.className = "math-block";
      span.textContent = `\\[${displayOnly.latex}\\]`;
      host.append(span);
    } else {
      host.append(copy);
    }
  }
  return host.innerHTML;
}

/** HTML theo đúng dạng chuẩn mà trình soạn phát ra (qua schema TipTap rồi bộ lọc), đoạn rỗng cuối đã bỏ. */
export function normalizeEditorHtml(html: string, allowImages = true): string {
  const editor = new Editor({
    extensions: createRichTextExtensions({ allowImages }),
    content: sanitizeQuestionHtml(html, { allowImages }).html,
  });
  try {
    return stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML(), { allowImages }).html);
  } finally {
    editor.destroy();
  }
}
