import { Slot } from "@radix-ui/react-slot";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

export type NavTabsProps = Omit<ComponentProps<"nav">, "aria-label"> & {
  /** Tên của nhóm điều hướng cho trình đọc màn hình. Bắt buộc vì không có chuỗi mặc định. */
  "aria-label": string;
};

/**
 * Thanh điều hướng trông như tab, dùng khi mỗi "tab" là một trang có đường dẫn riêng. Đây là các liên kết
 * trong `<nav>` (mục đang mở mang `aria-current="page"`), KHÔNG phải `role="tablist"`: mẫu tab của ARIA chỉ
 * dành cho việc đổi nội dung trong cùng một trang. Nhờ vậy mở được ở tab trình duyệt mới và nút Back đúng nghĩa.
 */
export function NavTabs({ className, children, ...props }: NavTabsProps) {
  return (
    <nav className={cn("min-w-0", className)} {...props}>
      {/* Một hàng, cuộn ngang khi không đủ chỗ; không xuống dòng để thứ tự và mục đang chọn luôn dễ theo dõi. */}
      <ul className="m-0 flex list-none items-center gap-1 overflow-x-auto p-0 pb-1">{children}</ul>
    </nav>
  );
}

export type NavTabProps = {
  /** Mục của trang đang mở. */
  current?: boolean;
  /** Bọc liên kết của ứng dụng (ví dụ `<Link>` của Next) thay cho thẻ `<a>` mặc định. */
  asChild?: boolean;
  className?: string;
  children: ReactNode;
} & Omit<ComponentProps<"a">, "children" | "className" | "aria-current">;

export function NavTab({ current = false, asChild = false, className, children, ...props }: NavTabProps) {
  const Comp = asChild ? Slot : "a";
  return (
    <li className="shrink-0">
      <Comp
        aria-current={current ? "page" : undefined}
        className={cn(
          // Độ đậm chữ không đổi theo trạng thái để mục không đổi bề rộng khi được chọn.
          "relative inline-flex h-10 items-center justify-center rounded-md px-4 text-label font-medium whitespace-nowrap text-neutral transition-colors duration-150 motion-reduce:transition-none",
          "hover:bg-subtle aria-[current=page]:bg-primary-50 aria-[current=page]:text-primary-text",
          // Gạch chân vẽ bằng viền của phần tử giả: không chỉ dựa vào màu (WCAG 1.4.1) và còn thấy ở chế độ
          // tương phản cao của hệ điều hành, nơi nền và bóng đổ bị bỏ.
          "after:pointer-events-none after:absolute after:inset-x-3 after:bottom-1 after:border-b-2 after:border-transparent aria-[current=page]:after:border-current",
          focusRing,
          className,
        )}
        {...props}
      >
        {children}
      </Comp>
    </li>
  );
}
