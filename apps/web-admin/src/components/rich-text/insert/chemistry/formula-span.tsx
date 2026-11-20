"use client";

import { useEffect, useRef } from "react";
import { renderMath } from "@thi360/ui/rich-text";

export interface FormulaSpanProps {
  /** Nội dung LaTeX (không kèm `\(`…`\)`), ví dụ `\ce{H2O}`. */
  latex: string;
  /** `true` dựng cỡ công thức khối (`span.math-block`), mặc định cùng dòng. */
  display?: boolean;
  className?: string;
}

/**
 * Dựng một công thức bằng `renderMath` của trình soạn (KaTeX + mhchem), cùng đường dựng với node công thức trong vùng
 * soạn và `RichTextView`. LaTeX lỗi hiện mã nguồn trong khung `math-error` thay vì làm vỡ hộp.
 */
export function FormulaSpan({ latex, display = false, className }: FormulaSpanProps) {
  const hostRef = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const span = document.createElement("span");
    span.className = display ? "math-block" : "math-inline";
    span.dataset.latex = latex;
    host.replaceChildren(span);
    renderMath(host);
  }, [latex, display]);
  return <span ref={hostRef} className={className} />;
}
