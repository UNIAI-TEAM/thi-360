import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useDebouncedSearch, useDebouncedValue } from "./use-debounced-value";

describe("useDebouncedSearch (C-10)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("gõ liên tục chỉ cho ra một từ khóa sau khi ngừng 300ms, đã bỏ khoảng trắng hai đầu", () => {
    const { result, rerender } = renderHook(({ input }) => useDebouncedSearch(input), {
      initialProps: { input: "" },
    });
    expect(result.current).toBe("");

    for (const input of ["h", "ha", "ham", " ham so "]) {
      rerender({ input });
      act(() => vi.advanceTimersByTime(100));
      expect(result.current).toBe("");
    }

    act(() => vi.advanceTimersByTime(300));
    expect(result.current).toBe("ham so");
  });

  it("xóa trống có hiệu lực ngay và từ khóa cũ không quay lại khi gõ tiếp", () => {
    const { result, rerender } = renderHook(({ input }) => useDebouncedSearch(input), {
      initialProps: { input: "toan" },
    });
    expect(result.current).toBe("toan");

    rerender({ input: "" });
    expect(result.current).toBe("");

    rerender({ input: "v" });
    expect(result.current).toBe("");
    act(() => vi.advanceTimersByTime(300));
    expect(result.current).toBe("v");
  });
});

describe("useDebouncedValue", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("chỉ đổi sau khi giá trị đứng yên đủ độ trễ", () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 200), {
      initialProps: { value: "a" },
    });
    rerender({ value: "b" });
    act(() => vi.advanceTimersByTime(199));
    expect(result.current).toBe("a");
    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe("b");
  });
});
