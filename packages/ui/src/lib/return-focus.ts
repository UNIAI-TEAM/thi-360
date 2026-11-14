"use client";

import { createContext, useContext, useEffect, useId, useState } from "react";

/**
 * Trả focus về nơi đã mở hộp thoại/drawer khi đóng (WCAG 2.4.3).
 *
 * Vì sao phải tự làm: phần nội dung modal của Radix luôn `preventDefault()` sự kiện đóng rồi focus
 * `Dialog.Trigger` của chính nó (`@radix-ui/react-dialog` 1.3.1, `DialogContentModal`). Hộp thoại của kit được
 * mở bằng state `open`, không có Trigger, nên focus rơi về `<body>`. Handler `onCloseAutoFocus` của nơi gọi
 * chạy TRƯỚC handler của Radix, và Radix bỏ qua phần của mình khi sự kiện đã bị chặn; ta dựa vào đó.
 *
 * Danh sách ứng viên được chụp lúc mở, thử lần lượt lúc đóng, phần tử đầu tiên còn trong trang và nhận được
 * focus sẽ thắng:
 *   1. phần tử đang giữ focus lúc mở (nút đã bấm);
 *   2. nếu đó là một mục menu (mục menu biến mất cùng menu): nút mở menu, lần theo `aria-labelledby` của
 *      `role="menu"`, qua cả menu con;
 *   3. các phần tử cha của nó, từ gần ra xa: nút mở đã bị xóa (xóa dòng vừa xác nhận) thì focus về vùng chứa
 *      gần nhất nhận được focus (hộp thoại bên ngoài, vùng cuộn của bảng, `<main tabindex="-1">`);
 *   4. nếu nơi mở nằm trong một hộp thoại/drawer khác của kit: các ứng viên của hộp thoại đó (hộp thoại A mở
 *      hộp thoại B rồi tự đóng thì đóng B vẫn về nơi đã mở A).
 * Không ứng viên nào dùng được thì để nguyên hành vi của Radix (focus Trigger nếu có).
 */
export type ReturnFocusCandidates = readonly HTMLElement[];

const NO_CANDIDATES: ReturnFocusCandidates = [];

/** Thuộc tính đánh dấu phần nội dung của một lớp phủ có ghi nhớ nơi trả focus. */
export const RETURN_FOCUS_ATTRIBUTE = "data-return-focus";

/** Menu con lồng nhau hiếm khi quá vài cấp; chặn vòng lặp nếu `aria-labelledby` trỏ vòng. */
const MAX_MENU_DEPTH = 8;

/** Ứng viên của các lớp phủ đang dựng, theo giá trị thuộc tính `data-return-focus`. */
const openOverlays = new Map<string, ReturnFocusCandidates>();

export function captureReturnFocus(): ReturnFocusCandidates {
  if (typeof document === "undefined") return NO_CANDIDATES;
  const opener = document.activeElement;
  if (!(opener instanceof HTMLElement) || opener === document.body) return NO_CANDIDATES;

  const candidates: HTMLElement[] = [opener];
  let outermost = opener;
  for (let depth = 0; depth < MAX_MENU_DEPTH; depth += 1) {
    const triggerId = outermost.closest('[role="menu"]')?.getAttribute("aria-labelledby");
    const trigger = triggerId ? document.getElementById(triggerId) : null;
    if (!trigger || candidates.includes(trigger)) break;
    candidates.push(trigger);
    outermost = trigger;
  }

  for (
    let ancestor = outermost.parentElement;
    ancestor && ancestor !== document.body;
    ancestor = ancestor.parentElement
  ) {
    candidates.push(ancestor);
  }

  const overlayId = outermost.closest(`[${RETURN_FOCUS_ATTRIBUTE}]`)?.getAttribute(RETURN_FOCUS_ATTRIBUTE);
  for (const inherited of (overlayId ? openOverlays.get(overlayId) : undefined) ?? NO_CANDIDATES) {
    if (!candidates.includes(inherited)) candidates.push(inherited);
  }
  return candidates;
}

/**
 * Trả về `true` khi đã đặt được focus. Không giành focus khi nó đang nằm ở một nơi khác có chủ đích (nơi gọi tự
 * chuyển focus, hoặc một hộp thoại khác vừa mở thay cho hộp thoại này): chỉ nhận khi focus đang ở `<body>`
 * hoặc ở một vùng chứa ứng viên.
 */
export function restoreReturnFocus(candidates: ReturnFocusCandidates): boolean {
  if (typeof document === "undefined") return false;
  const active = document.activeElement;
  const heldBy = active instanceof HTMLElement && active !== document.body ? active : null;
  for (const candidate of candidates) {
    if (!candidate.isConnected) continue;
    if (heldBy && !heldBy.contains(candidate)) return false;
    candidate.focus();
    if (document.activeElement === candidate) return true;
  }
  return false;
}

export const ReturnFocusContext = createContext<ReturnFocusCandidates>(NO_CANDIDATES);

/**
 * Dùng ở gốc (Root) của hộp thoại/drawer. Chụp nơi mở ngay trong lần render làm `open` thành `true`: lúc đó
 * DOM chưa đổi, mục menu vừa bấm vẫn còn và vẫn giữ focus. Hộp thoại không điều khiển (`open` bỏ trống) chỉ mở
 * được bằng Trigger của Radix và Radix tự trả focus về Trigger đó, nên không cần chụp.
 */
export function useReturnFocusRoot(open: boolean | undefined): ReturnFocusCandidates {
  const [candidates, setCandidates] = useState<ReturnFocusCandidates>(() =>
    open ? captureReturnFocus() : NO_CANDIDATES,
  );
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setCandidates(captureReturnFocus());
  }
  return candidates;
}

/**
 * Dùng ở phần nội dung (Content): trả về các thuộc tính gắn lên `DialogPrimitive.Content`. Nơi gọi vẫn ghi đè
 * được bằng `onCloseAutoFocus` của mình kèm `event.preventDefault()`.
 */
export function useReturnFocusContent(onCloseAutoFocus?: (event: Event) => void) {
  const candidates = useContext(ReturnFocusContext);
  const id = useId();
  useEffect(() => {
    openOverlays.set(id, candidates);
    return () => {
      openOverlays.delete(id);
    };
  }, [id, candidates]);

  return {
    [RETURN_FOCUS_ATTRIBUTE]: id,
    onCloseAutoFocus: (event: Event) => {
      onCloseAutoFocus?.(event);
      if (event.defaultPrevented) return;
      if (restoreReturnFocus(candidates)) event.preventDefault();
    },
  };
}
