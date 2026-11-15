import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { usePrefersReducedMotion } from "./use-reduced-motion";

// C-17: hook đọc `prefers-reduced-motion` và cập nhật khi thiết lập đổi.
describe("usePrefersReducedMotion", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function mockReducedMotion(initial: boolean) {
    let matches = initial;
    const listeners = new Set<() => void>();
    vi.spyOn(window, "matchMedia").mockImplementation(
      (query: string) =>
        ({
          get matches() {
            return query.includes("prefers-reduced-motion") ? matches : false;
          },
          media: query,
          onchange: null,
          addEventListener: (_type: string, cb: () => void) => listeners.add(cb),
          removeEventListener: (_type: string, cb: () => void) => listeners.delete(cb),
          addListener: () => {},
          removeListener: () => {},
          dispatchEvent: () => false,
        }) as unknown as MediaQueryList,
    );
    return {
      set(next: boolean) {
        matches = next;
        for (const cb of listeners) cb();
      },
      listenerCount: () => listeners.size,
    };
  }

  it("trả về thiết lập hiện tại và đổi theo khi người dùng bật hoặc tắt", () => {
    const media = mockReducedMotion(false);
    const { result, unmount } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);

    act(() => media.set(true));
    expect(result.current).toBe(true);

    act(() => media.set(false));
    expect(result.current).toBe(false);

    unmount();
    expect(media.listenerCount()).toBe(0);
  });

  it("bật sẵn từ đầu", () => {
    mockReducedMotion(true);
    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(true);
  });
});
