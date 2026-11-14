import { useEffect, useState } from "react";

/**
 * Vòng focus vẽ vào trong cho vùng tự cuộn nằm sát mép thẻ `overflow-hidden`. Cùng chuỗi với `focusRingInset`
 * của UI kit (`packages/ui/src/lib/styles.ts`); kit chưa xuất hằng này qua `@thi360/ui` nên tạm chép ở đây.
 */
export const focusRingInset =
  "focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-primary-text";

/**
 * Theo dõi một khung cuộn ngang có đang tràn hay không, cùng cách làm với `Table` của UI kit (DESIGN.md mục 5.9):
 * chỉ khi tràn, khung mới cần là điểm dừng Tab và là vùng có tên để người dùng bàn phím cuộn bằng phím mũi tên.
 * Trả về hàm gán ref cho khung và cờ đang tràn.
 */
export function useInlineOverflow<TElement extends HTMLElement>() {
  const [scroller, setScroller] = useState<TElement | null>(null);
  const [overflows, setOverflows] = useState(false);

  useEffect(() => {
    if (!scroller || typeof ResizeObserver === "undefined") return;
    // Lệch 1px là do làm tròn khi thu phóng lẻ, chưa phải tràn thật.
    const observer = new ResizeObserver(() => {
      setOverflows(scroller.scrollWidth - scroller.clientWidth > 1);
    });
    observer.observe(scroller);
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild);
    return () => observer.disconnect();
  }, [scroller]);

  return [setScroller, overflows] as const;
}
