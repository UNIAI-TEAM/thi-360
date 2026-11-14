import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "./button";

describe("Button", () => {
  it("gọi onClick khi bấm và mặc định type=button", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Lưu</Button>);
    const button = screen.getByRole("button", { name: "Lưu" });
    expect(button).toHaveAttribute("type", "button");
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("mặc định cao 40px, cỡ lg cao 48px", () => {
    render(
      <>
        <Button>Mặc định</Button>
        <Button size="lg">Lớn</Button>
      </>,
    );
    expect(screen.getByRole("button", { name: "Mặc định" })).toHaveClass("h-10");
    expect(screen.getByRole("button", { name: "Lớn" })).toHaveClass("h-12");
  });

  it("đang xử lý: đặt aria-disabled và aria-busy, giữ nguyên opacity và chặn onClick/submit", async () => {
    const onClick = vi.fn();
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <Button type="submit" loading onClick={onClick}>
          Lưu
        </Button>
      </form>,
    );
    const button = screen.getByRole("button", { name: "Lưu" });
    expect(button).not.toBeDisabled();
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).toHaveAttribute("aria-busy", "true");
    expect(button.className).not.toContain("aria-disabled:opacity-50");
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("asChild đưa kiểu nút cho phần tử con", () => {
    render(
      <Button asChild variant="secondary">
        <a href="/ho-so">Hồ sơ</a>
      </Button>,
    );
    const link = screen.getByRole("link", { name: "Hồ sơ" });
    expect(link).toHaveAttribute("href", "/ho-so");
    expect(link).toHaveClass("h-10");
  });

  it("asChild + disabled: phần tử con aria-disabled, ra khỏi thứ tự Tab và không nhận bấm", async () => {
    const onClick = vi.fn();
    render(
      <>
        <Button asChild disabled>
          <a href="/ho-so" onClick={onClick}>
            Hồ sơ
          </a>
        </Button>
        <Button asChild disabled>
          <button type="button" onClick={onClick}>
            Nút
          </button>
        </Button>
      </>,
    );
    const link = screen.getByRole("link", { name: "Hồ sơ" });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(link).toHaveAttribute("tabindex", "-1");
    // fireEvent.click trả false khi sự kiện bị preventDefault: liên kết không điều hướng.
    expect(fireEvent.click(link)).toBe(false);
    await userEvent.click(screen.getByRole("button", { name: "Nút" }));
    expect(onClick).not.toHaveBeenCalled();
    await userEvent.tab();
    expect(link).not.toHaveFocus();
  });

  it("asChild không disabled: không thêm aria-disabled và vẫn nhận bấm", async () => {
    const onClick = vi.fn();
    render(
      <Button asChild>
        <button type="button" onClick={onClick}>
          Nút
        </button>
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Nút" });
    expect(button).not.toHaveAttribute("aria-disabled");
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
