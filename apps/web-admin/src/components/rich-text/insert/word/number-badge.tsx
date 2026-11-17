import { cn } from "@thi360/ui";

export type NumberBadgeTone = "warning" | "danger" | "neutral";

const TONE: Record<NumberBadgeTone, string> = {
  // `text-surface`: chữ trắng ở chế độ sáng, chữ tối ở chế độ tối, nên nền `warning`/`danger` (đổi theo chế độ) luôn đủ tương phản.
  warning: "bg-warning text-surface",
  danger: "bg-danger text-surface",
  neutral: "bg-subtle text-text-secondary",
};

/**
 * Số thứ tự của một chỗ cần xem lại (bản vẽ `SoanThaoDanTuWord`): cùng một số hiện cạnh công thức trong phần xem trước
 * và ở thẻ tương ứng trong "Chỗ cần xem lại", để người soạn nối hai bên. Trang trí: nghĩa nằm ở chữ của thẻ và của dấu.
 */
export function NumberBadge({ number, tone, className }: { number: number; tone: NumberBadgeTone; className?: string }) {
  return (
    <span
      aria-hidden
      data-number={number}
      className={cn("inline-flex size-5 shrink-0 items-center justify-center rounded-full align-middle text-[11px] leading-none font-bold", TONE[tone], className)}
    >
      {number}
    </span>
  );
}
