"use client";

import { RichTextView } from "@thi360/ui/rich-text";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

const MARKER = /\[\[([^\]]+)\]\]/;

interface Slot {
  blankId: string;
  element: HTMLElement;
}

/**
 * Ngữ liệu Điền khuyết dạng HTML với chỗ trống `[[id]]` nằm ngay trong dòng chữ: `RichTextView` dựng HTML và công thức,
 * rồi mỗi `[[id]]` trong chữ thuần được thay bằng một vị trí gắn `renderBlank` (ô nhập khi làm thử, nhãn đáp án khi
 * xem). Vị trí đánh số theo thứ tự xuất hiện. Theo dõi DOM vì `RichTextView` chỉ dựng nội dung sau khi hydrate và
 * dựng lại khi ngữ liệu đổi.
 */
export function ClozePassage({
  html,
  renderBlank,
  className,
}: {
  html: string;
  renderBlank: (blankId: string, ordinal: number) => ReactNode;
  className?: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [slots, setSlots] = useState<Slot[]>([]);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const scan = () => {
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      const texts: Text[] = [];
      while (walker.nextNode()) texts.push(walker.currentNode as Text);
      for (const node of texts) {
        if (!MARKER.test(node.data)) continue;
        const fragment = document.createDocumentFragment();
        // split với nhóm bắt: phần tử lẻ là id chỗ trống. Nhập tệp và kiểm tra sẵn sàng đều cắt khoảng trắng quanh id.
        node.data.split(MARKER).forEach((part, index) => {
          if (index % 2 === 0) {
            if (part) fragment.append(part);
            return;
          }
          const slot = document.createElement("span");
          slot.dataset.clozeSlot = part.trim();
          fragment.append(slot);
        });
        node.replaceWith(fragment);
      }
      const found = [...root.querySelectorAll<HTMLElement>("[data-cloze-slot]")];
      setSlots((current) =>
        current.length === found.length && current.every((slot, index) => slot.element === found[index])
          ? current
          : found.map((element) => ({ blankId: element.dataset.clozeSlot ?? "", element })),
      );
    };
    scan();
    const observer = new MutationObserver(scan);
    observer.observe(root, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={rootRef} className={className}>
      <RichTextView html={html} />
      {slots.map((slot, ordinal) => createPortal(renderBlank(slot.blankId, ordinal), slot.element, `${ordinal}-${slot.blankId}`))}
    </div>
  );
}
