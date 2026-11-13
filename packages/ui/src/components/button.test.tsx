import { render, screen } from "@testing-library/react";
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

  it("đang xử lý: khóa nút, không gọi onClick, giữ nhãn trong DOM để không đổi chiều rộng", async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Lưu
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Lưu" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
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
});
