"use client";

import { cn } from "@thi360/ui";
import katex from "katex";
import "katex/dist/katex.min.css";
import "katex/contrib/mhchem";
import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { sanitizeQuestionHtml } from "../sanitize";
import "../rich-text.css";

export interface RichTextViewProps {
  /** HTML nội dung (đã lọc khi lưu); view vẫn lọc lại phòng dữ liệu cũ hoặc mock viết tay. */
  html: string;
  /** `false` coi mọi `img` là vi phạm và bỏ khỏi hiển thị (mirror hướng dẫn làm bài của bài thi). */
  allowImages?: boolean;
  /** Số bắt đầu của chỗ trống trong ngữ liệu câu chùm: chỗ trống n hiện start + n − 1. */
  blankNumberStart?: number;
  className?: string;
}

const subscribe = () => () => {};
const clientSnapshot = () => true;
const serverSnapshot = () => false;

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
function renderMath(container: HTMLElement) {
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

/** Chỗ trống hiện "(số) ____": số = blankNumberStart + data-blank − 1 (spec 4.8). */
function renderBlanks(container: HTMLElement, blankNumberStart: number) {
  for (const span of [...container.querySelectorAll<HTMLElement>("span.blank")]) {
    const n = Number.parseInt(span.getAttribute("data-blank") ?? "", 10);
    if (!Number.isInteger(n)) continue;
    span.textContent = `(${blankNumberStart + n - 1}) ____`;
  }
}

/**
 * Đoạn trích thơ `data-line-numbers="5"`: mỗi dòng (ngắt bằng `br`) thành một hàng lưới
 * `quote-line`, cột số bên trái chỉ điền ở dòng 5, 10, 15… Số đếm liên tục qua các khổ.
 */
function renderQuoteLineNumbers(container: HTMLElement) {
  for (const quote of [...container.querySelectorAll<HTMLElement>('blockquote.quote[data-line-numbers="5"]')]) {
    let lineNumber = 0;
    for (const paragraph of [...quote.querySelectorAll<HTMLElement>("p")]) {
      // Dòng thuộc về khổ hiện tại, tách tại mỗi <br>.
      const lines: Node[][] = [[]];
      for (const child of [...paragraph.childNodes]) {
        if (child.nodeType === Node.ELEMENT_NODE && (child as Element).tagName === "BR") {
          lines.push([]);
        } else {
          lines.at(-1)!.push(child);
        }
      }
      const rows = lines.map((lineNodes) => {
        lineNumber += 1;
        const row = document.createElement("span");
        row.className = "quote-line";
        const marker = document.createElement("span");
        marker.className = "quote-line-number";
        if (lineNumber % 5 === 0) marker.textContent = String(lineNumber);
        const content = document.createElement("span");
        content.className = "quote-line-text";
        content.append(...lineNodes);
        row.append(marker, content);
        return row;
      });
      paragraph.replaceChildren(...rows);
    }
  }
}

/**
 * Hiển thị HTML nội dung câu hỏi/bài thi (spec 3/5): lọc theo danh sách cho phép rồi dựng
 * công thức bằng KaTeX + mhchem, chỗ trống đánh số và số dòng của đoạn trích thơ.
 * Không dùng cho nhập liệu — xem `RichTextEditor`.
 */
export function RichTextView({ html, allowImages = true, blankNumberStart = 1, className }: RichTextViewProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const mounted = useSyncExternalStore(subscribe, clientSnapshot, serverSnapshot);
  const sanitized = mounted ? sanitizeQuestionHtml(html, { allowImages }).html : "";

  useEffect(() => {
    if (!rootRef.current) return;
    renderMath(rootRef.current);
    renderBlanks(rootRef.current, blankNumberStart);
    renderQuoteLineNumbers(rootRef.current);
  }, [sanitized, blankNumberStart]);

  // Cùng một đối tượng `__html` giữa các lần render: React 19 gán lại innerHTML nếu đối tượng mới, xóa công thức KaTeX
  // đã dựng và mọi thay đổi DOM của nơi dùng (effect bên dưới chỉ chạy lại khi `sanitized` đổi).
  const innerHtml = useMemo(() => ({ __html: sanitized }), [sanitized]);

  return <div ref={rootRef} className={cn("rich-text-body", className)} dangerouslySetInnerHTML={innerHtml} />;
}
