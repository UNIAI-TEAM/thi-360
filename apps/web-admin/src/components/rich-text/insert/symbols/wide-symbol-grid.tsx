"use client";

import { cn, focusRing, Tooltip } from "@thi360/ui";
import type { SymbolGridItem } from "../shared/symbol-grid";

export interface WideSymbolGridProps {
  items: SymbolGridItem[];
  /** Bấm một ô: chèn ký hiệu đó (hộp không đóng). `index` theo `items`. */
  onPick: (item: SymbolGridItem, index: number) => void;
  "aria-label": string;
}

/**
 * Lưới cho ký hiệu dài như đơn vị (`mol/L`, `kJ/mol`, `W/m²`): giống `SymbolGrid` nhưng nút cao 40px và rộng theo chữ
 * (tối thiểu 40px) để chữ không tràn ra ô bên cạnh. Dùng cùng token màu và `focusRing` với `SymbolGrid`.
 */
export function WideSymbolGrid({ items, onPick, "aria-label": ariaLabel }: WideSymbolGridProps) {
  return (
    <div role="group" aria-label={ariaLabel} className="flex flex-wrap gap-1">
      {items.map((item, index) => (
        <Tooltip key={index} content={item.label}>
          <button
            type="button"
            aria-label={item.label}
            className={cn(
              "inline-flex h-10 min-w-10 shrink-0 items-center justify-center rounded-md border border-border bg-surface px-2 text-body whitespace-nowrap text-text",
              "hover:border-border-strong hover:bg-subtle",
              focusRing,
            )}
            onClick={() => onPick(item, index)}
          >
            <span aria-hidden className="select-none">
              {item.glyph}
            </span>
          </button>
        </Tooltip>
      ))}
    </div>
  );
}
