import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { notify, toast, Toaster } from "./toaster";

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
    act(() => {
      toast.dismiss();
    });
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

  it("truyền containerAriaLabel và closeButtonAriaLabel cho sonner", async () => {
    render(
      <Toaster
        containerAriaLabel="Thông báo"
        closeButtonAriaLabel="Đóng thông báo"
      />,
    );
    act(() => {
      notify.success("Đã lưu");
    });
    await advance(100);
    expect(screen.getByRole("region", { name: /Thông báo/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đóng thông báo" })).toBeInTheDocument();
  });

  /*
   * S-24. sonner chỉ đọc `--error-*` (và `--success-*`…) khi toast mang `data-rich-colors="true"`:
   *   [data-rich-colors='true'][data-sonner-toast][data-type='error'] { background: var(--error-bg); … }
   * (sonner 2.0.8, styles.css). jsdom không áp CSS của sonner, nên kiểm hai vế của hợp đồng đó: thuộc tính trên
   * toast và giá trị biến trên vùng chứa.
   */
  it("toast lỗi mang màu danger của token: bật rich colors và nối biến của sonner vào token", async () => {
    render(<Toaster />);
    act(() => {
      notify.error("Không lưu được");
    });
    await advance(100);
    const toastElement = screen.getByText("Không lưu được").closest("[data-sonner-toast]") as HTMLElement;
    expect(toastElement).toHaveAttribute("data-type", "error");
    expect(toastElement).toHaveAttribute("data-rich-colors", "true");

    const container = toastElement.closest("[data-sonner-toaster]") as HTMLElement;
    expect(container.style.getPropertyValue("--error-bg")).toBe("var(--color-danger-bg)");
    expect(container.style.getPropertyValue("--error-text")).toBe("var(--color-danger)");
    expect(container.style.getPropertyValue("--error-border")).toBe("var(--color-danger)");
  });

  it("mọi loại toast có màu đều lấy từ token, không rơi về màu viết sẵn của sonner", async () => {
    render(<Toaster />);
    act(() => {
      notify.info("Đang xử lý");
    });
    await advance(100);
    const container = screen.getByText("Đang xử lý").closest("[data-sonner-toaster]") as HTMLElement;
    for (const [type, token] of [
      ["success", "success"],
      ["info", "info"],
      ["warning", "warning"],
      ["error", "danger"],
    ] as const) {
      expect(container.style.getPropertyValue(`--${type}-bg`)).toBe(`var(--color-${token}-bg)`);
      expect(container.style.getPropertyValue(`--${type}-text`)).toBe(`var(--color-${token})`);
      expect(container.style.getPropertyValue(`--${type}-border`)).toBe(`var(--color-${token})`);
    }
  });

  it("nút đóng toast rộng 24×24, vùng chạm nới ra 44×44 trên thiết bị trỏ thô", async () => {
    render(<Toaster />);
    act(() => {
      notify.error("Không lưu được");
    });
    await advance(100);
    const close = screen.getByRole("button", { name: "Đóng thông báo" });
    // `!`: CSS của sonner không nằm trong layer nên chỉ lớp important mới thắng được 20×20 mặc định.
    expect(close).toHaveClass("size-6!");
    expect(close).toHaveClass("pointer-coarse:after:absolute", "pointer-coarse:after:-inset-2.5");
  });

  it("giữ nguyên API: classNames của nơi gọi được gộp, richColors tắt được", async () => {
    render(
      <Toaster
        richColors={false}
        toastOptions={{ classNames: { closeButton: "opacity-80", toast: "shadow-md" } }}
      />,
    );
    act(() => {
      notify.error("Không lưu được");
    });
    await advance(100);
    const toastElement = screen.getByText("Không lưu được").closest("[data-sonner-toast]") as HTMLElement;
    expect(toastElement).toHaveAttribute("data-rich-colors", "false");
    expect(toastElement).toHaveClass("shadow-md");
    const close = screen.getByRole("button", { name: "Đóng thông báo" });
    expect(close).toHaveClass("size-6!", "opacity-80");
    // Nhãn mặc định của nút đóng vẫn có khi nơi gọi chỉ truyền classNames.
    expect(close).toHaveAccessibleName("Đóng thông báo");
  });
});
