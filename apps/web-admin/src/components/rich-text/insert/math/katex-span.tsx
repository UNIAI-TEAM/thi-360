"use client";

import { useLayoutEffect, useRef } from "react";
import katex from "katex";
import { paletteGlyphLatex } from "./math-palette";

export interface KatexSpanProps {
  latex: string;
  displayMode?: boolean;
  className?: string;
}

/**
 * Dựng một công thức bằng KaTeX vào phần tử của chính nó (không `dangerouslySetInnerHTML`). Ô giữ chỗ của mathlive
 * hiện thành ô vuông rỗng; mã chưa dựng được hiện nguyên chữ.
 */
export function KatexSpan({ latex, displayMode = false, className }: KatexSpanProps) {
  const ref = useRef<HTMLSpanElement>(null);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const source = paletteGlyphLatex(latex);
    try {
      katex.render(source, element, { displayMode, throwOnError: true, strict: "ignore" });
    } catch {
      element.textContent = latex;
    }
  }, [latex, displayMode]);
  return <span ref={ref} className={className} />;
}
