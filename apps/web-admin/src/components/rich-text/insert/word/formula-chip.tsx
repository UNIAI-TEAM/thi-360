"use client";

import { cn } from "@thi360/ui";
import katex from "katex";
import "katex/contrib/mhchem";
import "katex/dist/katex.min.css";
import { useLayoutEffect, useRef } from "react";
import "../../rich-text.css";

export interface FormulaChipProps {
  latex: string;
  className?: string;
}

/**
 * Một công thức dựng bằng KaTeX (+ mhchem) trong hộp "Dán từ Word". Nút DOM do KaTeX viết nên không có con React:
 * React không bao giờ đụng vào phần đã dựng. LaTeX lỗi hiện mã gốc trong khung `math-error` thay vì làm vỡ hộp.
 */
export function FormulaChip({ latex, className }: FormulaChipProps) {
  const ref = useRef<HTMLSpanElement>(null);
  useLayoutEffect(() => {
    const target = ref.current;
    if (!target) return;
    try {
      katex.render(latex, target, { displayMode: false, throwOnError: true });
      target.classList.remove("math-error");
    } catch {
      target.classList.add("math-error");
      target.textContent = latex;
    }
  }, [latex]);
  return <span ref={ref} data-latex={latex} className={cn("inline-block max-w-full align-middle", className)} />;
}
