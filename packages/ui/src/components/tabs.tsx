"use client";

import * as TabsPrimitive from "@radix-ui/react-tabs";
import { createContext, use, useEffect, useRef, type ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

/**
 * `segmented`: thanh nền xám, tab chọn nổi lên (chuyển chế độ). `content`: tab chọn nền xanh nhạt (chi tiết).
 * Cả hai kiểu đều gạch chân tab đang chọn, cùng hình với `NavTabs`.
 */
export type TabsVariant = "segmented" | "content";

const TabsVariantContext = createContext<TabsVariant>("content");

export type TabsProps = ComponentProps<typeof TabsPrimitive.Root> & { variant?: TabsVariant };

export function Tabs({ variant = "content", ...props }: TabsProps) {
  return (
    <TabsVariantContext value={variant}>
      <TabsPrimitive.Root {...props} />
    </TabsVariantContext>
  );
}

/**
 * Cuộn riêng thanh tab (không cuộn trang) để tab đang chọn hiện trọn, kèm một phần tab kế bên: bấm vào một tab
 * đang lộ một nửa thì tab sau nó cũng ló ra, người dùng chuột không có bánh xe ngang vẫn tới được mọi tab.
 * Tự tính `scrollLeft` thay cho `scrollIntoView`: trình duyệt chưa hỗ trợ `container: "nearest"` sẽ cuộn cả trang.
 */
function revealTab(list: HTMLElement, tab: HTMLElement) {
  const peek = tab.offsetHeight;
  const listBox = list.getBoundingClientRect();
  const tabBox = tab.getBoundingClientRect();
  const hiddenAtStart = tabBox.left - peek - listBox.left;
  const hiddenAtEnd = tabBox.right + peek - listBox.right;
  if (hiddenAtStart < 0) list.scrollLeft += hiddenAtStart;
  else if (hiddenAtEnd > 0) list.scrollLeft += hiddenAtEnd;
}

export function TabsList({ className, ref, ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  const variant = use(TabsVariantContext);
  const listRef = useRef<HTMLDivElement | null>(null);
  const scrolls = variant === "content";

  // Radix giữ giá trị đang chọn trong context riêng, nên theo dõi `data-state` của các tab: đúng cho cả chế độ
  // có kiểm soát lẫn không, và cho cả lần hiển thị đầu (tab mặc định có thể nằm ngoài phần đang thấy).
  useEffect(() => {
    const list = listRef.current;
    if (!scrolls || !list) return;
    const reveal = () => {
      const active = list.querySelector<HTMLElement>('[role="tab"][data-state="active"]');
      if (active) revealTab(list, active);
    };
    reveal();
    if (typeof MutationObserver === "undefined") return;
    const observer = new MutationObserver(reveal);
    observer.observe(list, { attributes: true, attributeFilter: ["data-state"], subtree: true });
    return () => observer.disconnect();
  }, [scrolls]);

  return (
    <TabsPrimitive.List
      ref={(node) => {
        listRef.current = node;
        if (typeof ref === "function") ref(node);
        else if (ref) ref.current = node;
      }}
      data-variant={variant}
      className={cn(
        variant === "segmented"
          ? "flex h-12 w-full items-center gap-1 rounded-lg bg-subtle p-1"
          : // Luôn một hàng, tự cuộn ngang khi không đủ chỗ (DESIGN.md mục 5.5) thay vì đẩy cả trang cuộn ngang.
            // Đệm 4px bù bằng lề âm: vùng cuộn cắt mọi thứ tràn ra ngoài, kể cả vòng focus 3px của tab.
            "-m-1 flex items-center gap-1 overflow-x-auto p-1 [scrollbar-width:thin]",
        className,
      )}
      {...props}
    />
  );
}

export function TabsTrigger({ className, ...props }: ComponentProps<typeof TabsPrimitive.Trigger>) {
  const variant = use(TabsVariantContext);
  return (
    <TabsPrimitive.Trigger
      className={cn(
        // Độ đậm chữ không đổi theo trạng thái: đổi độ đậm làm tab đổi bề rộng và đẩy các tab bên cạnh.
        "relative inline-flex h-10 items-center justify-center rounded-md px-4 text-label font-medium whitespace-nowrap transition-colors duration-150 motion-reduce:transition-none",
        "disabled:pointer-events-none disabled:opacity-50",
        // Gạch chân vẽ bằng viền của phần tử giả: không chỉ dựa vào màu (WCAG 1.4.1), không chiếm chỗ, và còn
        // thấy ở chế độ tương phản cao của hệ điều hành, nơi nền và bóng đổ bị bỏ. Viền chỉ có ở tab đang chọn
        // (không để sẵn viền trong suốt): chế độ tương phản cao tô lại cả viền trong suốt, tab nào cũng thành có gạch.
        "after:pointer-events-none after:absolute after:inset-x-3 after:bottom-1 data-[state=active]:after:border-b-2 data-[state=active]:after:border-current",
        variant === "segmented"
          ? "flex-1 text-text-muted data-[state=active]:bg-surface data-[state=active]:text-primary-text data-[state=active]:shadow-sm"
          : "shrink-0 text-neutral hover:bg-subtle data-[state=active]:bg-primary-50 data-[state=active]:text-primary-text",
        focusRing,
        className,
      )}
      {...props}
    />
  );
}

export function TabsContent({ className, ...props }: ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content className={cn("mt-4", focusRing, className)} {...props} />
  );
}
