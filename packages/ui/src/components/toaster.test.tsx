import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { notify, Toaster } from "./toaster";

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("Toaster", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("toast thành công hiện rồi tự ẩn sau 4 giây", async () => {
    render(<Toaster />);
    act(() => {
      notify.success("Đã lưu");
    });
    await advance(100);
    expect(screen.getByText("Đã lưu")).toBeInTheDocument();

    await advance(6000);
    expect(screen.queryByText("Đã lưu")).toBeNull();
  });

  it("toast lỗi không tự ẩn", async () => {
    render(<Toaster />);
    act(() => {
      notify.error("Không lưu được");
    });
    await advance(100);
    expect(screen.getByText("Không lưu được")).toBeInTheDocument();

    await advance(60_000);
    expect(screen.getByText("Không lưu được")).toBeInTheDocument();
  });
});
