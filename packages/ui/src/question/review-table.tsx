import type { ReactNode } from "react";
import { cn } from "../lib/cn";

/**
 * Bảng kết quả của chế độ xem lại (Đúng/Sai, Ghép đôi, Điền khuyết, tiêu chí Tự luận).
 *
 * Từ `sm` trở lên là bảng thường. Dưới `sm` (điện thoại, 390px) mỗi hàng xếp chồng: mục, rồi từng ô kèm
 * nhãn cột ("Bạn chọn: …", "Điểm: …"), nên không còn cột nào bị cắt và không cần cuộn ngang (DESIGN.md mục
 * "Bảng rộng hơn khung"; TV1-1). Hàng tiêu đề chỉ còn cho trình đọc màn hình (`sr-only`).
 *
 * `display: block/flex` làm trình duyệt bỏ ngữ nghĩa bảng, nên mọi phần tử mang `role` tường minh.
 */
export function ReviewTable({ children }: { children: ReactNode }) {
  return (
    <div className="min-w-0 overflow-x-auto">
      <table role="table" className="w-full border-collapse text-left max-sm:block">
        {children}
      </table>
    </div>
  );
}

export function ReviewHead({ children }: { children: ReactNode }) {
  return (
    <thead role="rowgroup" className="max-sm:sr-only">
      <tr role="row" className="border-b border-border">
        {children}
      </tr>
    </thead>
  );
}

export function ReviewTh({ children, last = false }: { children: ReactNode; last?: boolean }) {
  return (
    <th role="columnheader" className={cn("py-2 text-caption font-semibold text-text-secondary", !last && "pr-4")}>
      {children}
    </th>
  );
}

export function ReviewBody({ children }: { children: ReactNode }) {
  return (
    <tbody role="rowgroup" className="max-sm:block">
      {children}
    </tbody>
  );
}

export function ReviewRow({ children }: { children: ReactNode }) {
  return (
    <tr
      role="row"
      className="border-b border-border/60 align-top last:border-0 max-sm:flex max-sm:flex-col max-sm:gap-1 max-sm:py-3"
    >
      {children}
    </tr>
  );
}

/**
 * Một ô. `label` là nhãn cột, chỉ nhìn thấy khi hàng xếp chồng (dưới `sm`); trình đọc màn hình đã có
 * tiêu đề cột nên nhãn `aria-hidden`. `last` bỏ khoảng đệm phải của cột cuối.
 */
export function ReviewCell({
  label,
  last = false,
  className,
  children,
}: {
  label?: string;
  last?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <td
      role="cell"
      className={cn(
        "py-2.5 text-label text-text max-sm:flex max-sm:items-baseline max-sm:gap-2 max-sm:py-0 max-sm:pr-0",
        !last && "pr-4",
        className,
      )}
    >
      {label && (
        <span aria-hidden className="shrink-0 text-caption font-semibold text-text-secondary sm:hidden">
          {label}:
        </span>
      )}
      {children}
    </td>
  );
}
