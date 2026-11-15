"use client";

import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { useEffect, type ComponentProps, type FocusEvent, type ReactNode } from "react";
import { cn } from "../lib/cn";

export type TooltipProps = {
  content: ReactNode;
  /** Phần tử kích hoạt; phải nhận được focus và rê chuột. */
  children: ReactNode;
  side?: ComponentProps<typeof TooltipPrimitive.Content>["side"];
  delayDuration?: number;
};

/** Phím đưa focus đi giữa các điều khiển: focus đến sau các phím này là người dùng tự đi tới, nên hiện tooltip. */
const NAVIGATION_KEYS = new Set(["Tab", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Home", "End"]);

/** Thao tác gần nhất là phím điều hướng (không phải bấm, chạm, Esc, Enter). Dùng chung cho mọi tooltip trên trang. */
let lastInputWasNavigationKey = false;
let listenersInstalled = false;

function installInputListeners() {
  if (listenersInstalled || typeof document === "undefined") return;
  listenersInstalled = true;
  document.addEventListener(
    "keydown",
    (event) => {
      lastInputWasNavigationKey = NAVIGATION_KEYS.has(event.key);
    },
    true,
  );
  document.addEventListener(
    "pointerdown",
    () => {
      lastInputWasNavigationKey = false;
    },
    true,
  );
}

/**
 * Tự mang Provider nên dùng được ở bất kỳ đâu. Hiện khi rê chuột, hoặc khi người dùng đưa focus tới bằng phím điều
 * hướng (Tab, mũi tên, Home/End).
 *
 * Không hiện khi focus do chương trình trả về (đóng hộp thoại, ngăn, menu thì focus về nút đã mở nó) hay sau khi bấm
 * hoặc chạm: lúc đó người dùng không tìm tới nút này, tooltip chỉ treo che nội dung (UNI-2588).
 */
export function Tooltip({ content, children, side = "top", delayDuration = 200 }: TooltipProps) {
  useEffect(installInputListeners, []);

  // Chặn lệnh mở theo focus của Radix (nó bỏ qua bước của mình khi sự kiện đã `preventDefault`).
  function onTriggerFocus(event: FocusEvent) {
    if (!lastInputWasNavigationKey) event.preventDefault();
  }

  return (
    <TooltipPrimitive.Provider delayDuration={delayDuration}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger asChild onFocus={onTriggerFocus}>
          {children}
        </TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content
            side={side}
            sideOffset={6}
            className={cn(
              "z-50 max-w-72 rounded-sm bg-text px-2.5 py-1.5 text-caption font-medium text-page shadow-md",
              // Chỉ chạy hiệu ứng trong `motion-safe:`: lớp `motion-reduce:animate-none` không thắng nổi bộ chọn `[data-state]` (U-13).
              "motion-safe:data-[state=delayed-open]:animate-fade-in",
            )}
          >
            {content}
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
