"use client";

import { cn, focusRing, Tooltip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useLayoutEffect, useRef } from "react";
import { KatexSpan } from "./katex-span";
import { PALETTE_GLYPH_LATEX, PALETTE_LATEX } from "./math-palette";

export interface PaletteGridProps {
  /** Mã các mẫu của tab đang chọn (`PaletteTab.items`). */
  items: readonly string[];
  /** Chèn một mẫu vào ô soạn trực quan. */
  onPick: (latex: string) => void;
  /** Tắt cả lưới khi ô soạn trực quan chưa sẵn sàng. */
  disabled?: boolean;
  /** Tên nhóm cho `role="group"`. */
  label: string;
}

/**
 * Mặt nút: công thức KaTeX thu nhỏ vừa ô (không bao giờ phóng to, không tràn) và căn giữa. Đo kích thước tự nhiên của
 * công thức rồi đặt `transform` thẳng lên phần tử, không qua state: đo lại khi ô hoặc công thức đổi cỡ (đổi số cột,
 * phông KaTeX nạp xong).
 */
function FitGlyph({ latex }: { latex: string }) {
  const boxRef = useRef<HTMLSpanElement>(null);
  const glyphRef = useRef<HTMLSpanElement>(null);
  useLayoutEffect(() => {
    const box = boxRef.current;
    const glyph = glyphRef.current;
    if (!box || !glyph) return;
    const fit = () => {
      const width = glyph.offsetWidth;
      const height = glyph.offsetHeight;
      if (!width || !height) return;
      const scale = Math.min(1, box.clientWidth / width, box.clientHeight / height);
      glyph.style.transform = scale < 1 ? `scale(${scale})` : "";
    };
    fit();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(fit);
    observer.observe(box);
    observer.observe(glyph);
    return () => observer.disconnect();
  }, [latex]);
  return (
    <span ref={boxRef} className="flex h-full w-full min-w-0 items-center justify-center overflow-hidden">
      <span ref={glyphRef} className="inline-block w-max shrink-0 whitespace-nowrap">
        <KatexSpan latex={latex} className="pointer-events-none select-none text-[0.95rem]" />
      </span>
    </span>
  );
}

/**
 * Lưới nút mẫu công thức theo bản vẽ `SoanThaoCongThucToan`: tám cột, nút cao 56px, mặt nút là chính công thức dựng
 * bằng KaTeX (ô giữ chỗ là ô vuông rỗng). Mỗi nút có `aria-label` vi/en từ `palette.<id>`.
 */
export function PaletteGrid({ items, onPick, disabled = false, label }: PaletteGridProps) {
  const t = useTranslations("richText.insertDialog.math.palette");
  return (
    <div role="group" aria-label={label} className="grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-8">
      {items.map((id) => {
        const latex = PALETTE_LATEX[id];
        if (latex === undefined) return null;
        const glyph = PALETTE_GLYPH_LATEX[id] ?? latex;
        return (
          <Tooltip key={id} content={t(id)}>
            <button
              type="button"
              aria-label={t(id)}
              disabled={disabled}
              onClick={() => onPick(latex)}
              className={cn(
                "flex h-14 min-w-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-surface p-1 text-body text-text",
                "hover:border-border-strong hover:bg-subtle disabled:pointer-events-none disabled:opacity-50",
                focusRing,
              )}
            >
              <FitGlyph latex={glyph} />
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
}
