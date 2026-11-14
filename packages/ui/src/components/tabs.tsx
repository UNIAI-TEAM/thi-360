"use client";

import * as TabsPrimitive from "@radix-ui/react-tabs";
import { createContext, use, type ComponentProps } from "react";
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

export function TabsList({ className, ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  const variant = use(TabsVariantContext);
  return (
    <TabsPrimitive.List
      data-variant={variant}
      className={cn(
        variant === "segmented"
          ? "flex h-12 w-full items-center gap-1 rounded-lg bg-subtle p-1"
          : "inline-flex items-center gap-1",
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
          : "text-neutral hover:bg-subtle data-[state=active]:bg-primary-50 data-[state=active]:text-primary-text",
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
