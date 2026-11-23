"use client";

import { Slot } from "@radix-ui/react-slot";
import { isValidElement, useEffect, useRef, type ComponentProps, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { followActiveTab } from "../lib/reveal-tab";
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
  const listRef = useRef<HTMLUListElement | null>(null);

  // Cùng việc với `TabsList`: mục đang mở (kể cả lần hiển thị đầu và khi chuyển trang) cuộn vào tầm nhìn ở 390px.
  useEffect(() => {
    const list = listRef.current;
    if (!list) return;
    return followActiveTab(list, '[aria-current="page"]', "aria-current");
  }, []);

  return (
    <nav className={cn("min-w-0", className)} {...props}>
      {/* Một hàng, cuộn ngang khi không đủ chỗ; không xuống dòng để thứ tự và mục đang chọn luôn dễ theo dõi. */}
      {/* Cùng mẫu với `TabsList`: đệm 4px bù bằng lề âm để vùng cuộn không cắt vòng focus; thanh cuộn mảnh báo còn mục bị che. */}
      <ul ref={listRef} className="-m-1 flex list-none items-center gap-1 overflow-x-auto p-1 [scrollbar-width:thin]">
        {children}
      </ul>
    </nav>
  );
}

export type NavTabProps = {
  /** Mục của trang đang mở. */
  current?: boolean;
  /** Vô hiệu hóa mục điều hướng: hiển thị dạng chữ tĩnh không nhận click/phím và bỏ qua `asChild`/`href`. */
  disabled?: boolean;
  /** Bọc liên kết của ứng dụng (ví dụ `<Link>` của Next) thay cho thẻ `<a>` mặc định. */
  asChild?: boolean;
  className?: string;
  children: ReactNode;
} & Omit<ComponentProps<"a">, "children" | "className" | "aria-current">;

function extractDisabledSpanAttributes(props: Record<string, unknown>): HTMLAttributes<HTMLSpanElement> {
  const safe: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(props)) {
    if (
      key === "id" ||
      key === "title" ||
      key === "lang" ||
      key === "dir" ||
      key === "style" ||
      (key.startsWith("data-") && key !== "data-disabled") ||
      (key.startsWith("aria-") && key !== "aria-current" && key !== "aria-disabled")
    ) {
      safe[key] = value;
    }
  }
  return safe as HTMLAttributes<HTMLSpanElement>;
}

export function NavTab({
  current = false,
  disabled = false,
  asChild = false,
  className,
  children,
  ...props
}: NavTabProps) {
  if (disabled) {
    const content =
      asChild && isValidElement<{ children?: ReactNode }>(children) ? children.props.children : children;
    return (
      <li className="shrink-0">
        <span
          {...extractDisabledSpanAttributes(props)}
          aria-disabled="true"
          data-disabled=""
          className={cn(
            "relative inline-flex h-10 cursor-not-allowed items-center justify-center rounded-md px-4 text-label font-medium whitespace-nowrap text-text-muted/60 select-none",
            className,
          )}
        >
          {content}
        </span>
      </li>
    );
  }

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
          // tương phản cao của hệ điều hành, nơi nền và bóng đổ bị bỏ. Viền chỉ tồn tại ở mục đang mở: chế độ
          // đó tô lại cả viền trong suốt, nên một viền trong suốt ở mục thường sẽ hiện thành gạch chân.
          "after:pointer-events-none after:absolute after:inset-x-3 after:bottom-1 aria-[current=page]:after:border-b-2 aria-[current=page]:after:border-current",
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
