import { act } from "@testing-library/react";
import { vi } from "vitest";

/**
 * Giả bề rộng khung chứa bảng trong jsdom (jsdom không tính bố cục, `getBoundingClientRect` luôn trả 0, nên `Table`
 * luôn là bảng). Mọi phần tử báo cùng một bề rộng; `Table` có `cards` đo khung của nó và hiện danh sách thẻ khi hẹp
 * hơn ngưỡng. Trả về hàm đổi bề rộng và báo cho các `ResizeObserver` đang theo dõi.
 *
 * Gọi trước `render`. File test dùng hàm này cần `vi.unstubAllGlobals()` và `vi.restoreAllMocks()` trong `afterEach`.
 */
export function installContainerWidth(initial: number) {
  let width = initial;
  const callbacks: ResizeObserverCallback[] = [];
  vi.stubGlobal(
    "ResizeObserver",
    class {
      constructor(callback: ResizeObserverCallback) {
        callbacks.push(callback);
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    () => ({ width, height: 0, top: 0, left: 0, right: width, bottom: 0, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect,
  );
  return (next: number) => {
    width = next;
    act(() => {
      for (const callback of callbacks) callback([], {} as ResizeObserver);
    });
  };
}

/** Bề rộng nội dung của admin ở cửa sổ 390px (DESIGN.md mục 5.8). */
export const PHONE_CONTENT_WIDTH = 358;
