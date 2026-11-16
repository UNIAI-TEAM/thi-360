"use client";

import { cn } from "@thi360/ui";
import katex from "katex";
import "katex/dist/katex.min.css";
import { useEffect, useRef } from "react";
import { sanitizeQuestionHtml } from "./sanitize";
import "./rich-text.css";

export interface RichTextViewProps {
  /** HTML nội dung (đã lọc khi lưu); view vẫn lọc lại phòng dữ liệu cũ hoặc mock viết tay. */
  html: string;
  /** `false` coi mọi `img` là vi phạm và bỏ khỏi hiển thị (mirror hướng dẫn làm bài của bài thi). */
  allowImages?: boolean;
  className?: string;
}

const DELIMITER_PATTERN = /\\\(|\\\[|\\\)|\\\]/g;

/**
 * Dựng công thức trong phần tử đã gắn DOM: `<span class="math-inline|math-block">` được KaTeX
 * dựng từ nội dung trong cặp dấu; văn bản thuần còn sót `\( … \)`/`\[ … \]` (người soạn gõ tay
 * mà chưa tạo chip) cũng được dựng. Cặp dấu không khớp giữ nguyên chữ — cảnh báo NO_GAPS nằm
 * ở trình soạn, không phải nơi đọc.
 */
function renderMath(container: HTMLElement) {
  for (const span of [...container.querySelectorAll<HTMLElement>("span.math-inline, span.math-block")]) {
    const text = span.textContent ?? "";
    const displayMode = span.classList.contains("math-block");
    const open = displayMode ? "\\[" : "\\(";
    const close = displayMode ? "\\]" : "\\)";
    const latex =
      text.trim().startsWith(open) && text.trim().endsWith(close) ? text.trim().slice(2, -2) : text;
    try {
      katex.render(latex, span, { displayMode, throwOnError: false });
    } catch {
      // KaTeX không ném với throwOnError:false; giữ chữ gốc nếu có phiên bản khác.
    }
  }

  // Tách các cặp dấu trong text node thuần (ngoài span công thức).
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      new RegExp(DELIMITER_PATTERN.source).test(node.nodeValue ?? "") &&
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
    for (const match of text.matchAll(DELIMITER_PATTERN)) {
      const index = match.index;
      const delimiter = match[0];
      if (delimiter === "\\(" || delimiter === "\\[") {
        const close = delimiter === "\\(" ? "\\)" : "\\]";
        const end = text.indexOf(close, index + 2);
        if (end === -1) continue;
        const latex = text.slice(index + 2, end);
        if (index > cursor) fragment.append(text.slice(cursor, index));
        const span = document.createElement("span");
        span.className = delimiter === "\\(" ? "math-inline" : "math-block";
        try {
          katex.render(latex, span, { displayMode: delimiter === "\\[", throwOnError: false });
        } catch {
          span.textContent = text.slice(index, end + 2);
        }
        fragment.append(span);
        cursor = end + 2;
        changed = true;
      }
    }
    if (changed) {
      if (cursor < text.length) fragment.append(text.slice(cursor));
      textNode.replaceWith(fragment);
    }
  }
}

/**
 * Hiển thị HTML nội dung câu hỏi/bài thi: lọc theo danh sách cho phép của spec 4.2 rồi dựng
 * công thức bằng KaTeX. Không dùng cho nhập liệu — xem `RichTextEditor`.
 */
export function RichTextView({ html, allowImages = true, className }: RichTextViewProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const sanitized = sanitizeQuestionHtml(html, { allowImages }).html;

  useEffect(() => {
    if (rootRef.current) renderMath(rootRef.current);
  }, [sanitized]);

  return (
    <div
      ref={rootRef}
      className={cn("rich-text-body", className)}
      // eslint-disable-next-line react/no-danger -- nội dung đã qua sanitizeQuestionHtml (allowlist spec 4.2)
      dangerouslySetInnerHTML={{ __html: sanitized }}
    />
  );
}
