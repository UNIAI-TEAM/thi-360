"use client";

import { cn } from "@thi360/ui";
import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { renderMath } from "../math-render";
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
