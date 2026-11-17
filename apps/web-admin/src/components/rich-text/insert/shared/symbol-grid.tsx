"use client";

import { cn, focusRing, Tooltip } from "@thi360/ui";

/** Một nút trong lưới ký hiệu (spec 7: nút 40×40, `aria-label` vi/en do nơi gọi truyền). */
export interface SymbolGridItem {
  /** Ký tự/chuỗi hiện trên nút (Unicode thường, dùng `sup`/`sub` bằng cách truyền chuỗi đã ghép). */
  glyph: React.ReactNode;
  /** Tên ký hiệu đọc cho trình đọc màn hình và tooltip. */
  label: string;
}

export interface SymbolGridProps {
  items: SymbolGridItem[];
  /** Bấm một ô: chèn ký hiệu đó (hộp không đóng — spec 4.4). `index` theo `items`. */
  onPick: (item: SymbolGridItem, index: number) => void;
  /** Nhãn của lưới cho `role="group"` (tên nhóm ký hiệu). */
  "aria-label": string;
  className?: string;
}

/** Lưới nút ký hiệu/nút công thức dùng chung cho các hộp chèn: ô vuông 40px, icon/chữ ở giữa. */
export function SymbolGrid({ items, onPick, "aria-label": ariaLabel, className }: SymbolGridProps) {
  return (
    <div role="group" aria-label={ariaLabel} className={cn("flex flex-wrap gap-1", className)}>
      {items.map((item, index) => (
        <Tooltip key={index} content={item.label}>
          <button
            type="button"
            aria-label={item.label}
            className={cn(
              "inline-flex size-10 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-body text-text",
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
