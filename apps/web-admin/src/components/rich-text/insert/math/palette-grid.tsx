"use client";

import { cn, focusRing, Tooltip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { KatexSpan } from "./katex-span";
import { PALETTE_LATEX } from "./math-palette";

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
        return (
          <Tooltip key={id} content={t(id)}>
            <button
              type="button"
              aria-label={t(id)}
              disabled={disabled}
              onClick={() => onPick(latex)}
              className={cn(
                "flex h-14 min-w-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-surface px-1 text-body text-text",
                "hover:border-border-strong hover:bg-subtle disabled:pointer-events-none disabled:opacity-50",
                focusRing,
              )}
            >
              <KatexSpan latex={latex} className="pointer-events-none select-none text-[0.95rem]" />
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
}
