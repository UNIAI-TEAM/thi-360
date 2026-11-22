import { act } from "@testing-library/react";
import { vi } from "vitest";

/**
 * Giả bề rộng khung chứa bảng trong jsdom để kiểm tra `useCardMode` (gọi trước `render`, khôi phục trong `afterEach`).
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

export const PHONE_CONTENT_WIDTH = 358;
