import katex from "katex";
import "katex/dist/katex.min.css";
import "katex/contrib/mhchem";

/**
 * Dựng công thức vào một phần tử đã gắn DOM. LaTeX lỗi không làm vỡ trang: nội dung hiện
 * mã nguồn trong khung cảnh báo `math-error` (màu của DESIGN.md).
 */
function renderLatex(latex: string, displayMode: boolean, target: HTMLElement, fallbackText?: string) {
  try {
    katex.render(latex, target, { displayMode, throwOnError: true });
  } catch {
    target.classList.add("math-error");
    target.textContent = fallbackText ?? latex;
  }
}

/**
 * Dựng công thức trong phần tử đã gắn DOM: `<span class="math-inline|math-block">` được KaTeX
 * dựng từ nội dung trong cặp dấu (kể cả `\ce{…}` của mhchem); văn bản thuần còn sót
 * `\( … \)`/`\[ … \]` (người soạn gõ tay mà chưa tạo chip) cũng được dựng. Cặp dấu không khớp
 * giữ nguyên chữ — cảnh báo NO_GAPS nằm ở trình soạn, không phải nơi đọc.
 */
export function renderMath(container: HTMLElement) {
  for (const span of [...container.querySelectorAll<HTMLElement>("span.math-inline, span.math-block")]) {
    // LaTeX nguồn giữ trong `data-latex` ở lần dựng đầu: effect chạy lại (StrictMode, dựng lại) không đọc lại chữ đã render.
    const displayMode = span.classList.contains("math-block");
    let latex = span.dataset.latex;
    if (latex === undefined) {
      const text = span.textContent ?? "";
      const open = displayMode ? "\\[" : "\\(";
      const close = displayMode ? "\\]" : "\\)";
      latex = text.trim().startsWith(open) && text.trim().endsWith(close) ? text.trim().slice(2, -2) : text;
      span.dataset.latex = latex;
    }
    renderLatex(latex, displayMode, span, displayMode ? `\\[${latex}\\]` : `\\(${latex}\\)`);
  }

  // Tách các cặp dấu trong text node thuần (ngoài span công thức).
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      /\\\(|\\\[|\\\)|\\\]/.test(node.nodeValue ?? "") &&
      !(node.parentElement?.closest("span.math-inline, span.math-block"))
        ? NodeFilter.FILTER_ACCEPT
        : NodeFilter.FILTER_REJECT,
  });
  const textNodes: Text[] = [];
  while (walker.nextNode()) textNodes.push(walker.currentNode as Text);

  for (const textNode of textNodes) {
    const text = textNode.nodeValue ?? "";
    const fragment = document.createDocumentFragment();
    let cursor = 0;
    let changed = false;
    while (true) {
      const openInline = text.indexOf("\\(", cursor);
      const openBlock = text.indexOf("\\[", cursor);
      const index = openInline === -1 ? openBlock : openBlock === -1 ? openInline : Math.min(openInline, openBlock);
      if (index === -1) break;
      const block = text[index + 1] === "[";
      const close = block ? "\\]" : "\\)";
      const end = text.indexOf(close, index + 2);
      if (end === -1) break;
      const latex = text.slice(index + 2, end);
      if (index > cursor) fragment.append(text.slice(cursor, index));
      const span = document.createElement("span");
      span.className = block ? "math-block" : "math-inline";
      span.dataset.latex = latex;
      renderLatex(latex, block, span, text.slice(index, end + 2));
      fragment.append(span);
      cursor = end + 2;
      changed = true;
    }
    if (changed) {
      if (cursor < text.length) fragment.append(text.slice(cursor));
      textNode.replaceWith(fragment);
    }
  }
}
