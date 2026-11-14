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

  it("đang xử lý: nhãn vẫn nằm trong cây truy cập (không visibility:hidden), spinner chỉ để nhìn", () => {
    render(
      <Button loading>
        <span data-testid="label-text">Đăng nhập</span>
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Đăng nhập" });
    expect(button).toHaveAccessibleName("Đăng nhập");
    expect(button).toHaveAttribute("aria-busy", "true");
    // Nút vẫn nhận focus khi đang xử lý, nên trình đọc màn hình đọc lại được tên và trạng thái bận.
    expect(button).not.toHaveAttribute("disabled");
    button.focus();
    expect(button).toHaveFocus();

    const label = screen.getByTestId("label-text").parentElement as HTMLElement;
    expect(label).toHaveClass("opacity-0");
    expect(label).not.toHaveClass("invisible");
    expect(label).not.toHaveAttribute("aria-hidden");
    expect(button.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("đang xử lý với giảm chuyển động: spinner quay chậm lại, không đứng yên", () => {
    render(<Button loading>Lưu</Button>);
    const spinner = screen.getByRole("button", { name: "Lưu" }).querySelector("svg");
    expect(spinner).toHaveClass("animate-spin", "motion-reduce:animate-[spin_6s_linear_infinite]");
    expect(spinner).not.toHaveClass("motion-reduce:animate-none");
  });

  it("không đang xử lý: không có spinner, nhãn hiện bình thường, không aria-busy", () => {
    render(
      <Button>
        <span data-testid="label-text">Đăng nhập</span>
      </Button>,
    );
    const button = screen.getByRole("button", { name: "Đăng nhập" });
    expect(button).not.toHaveAttribute("aria-busy");
    expect(button.querySelector("svg")).toBeNull();
    expect(screen.getByTestId("label-text").parentElement).not.toHaveClass("opacity-0");
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

  it("asChild: icon và nhãn của phần tử con cùng cỡ icon, cùng khoảng cách với nút thường", () => {
    render(
      <>
        <Button asChild variant="secondary">
          <a href="/cap-hang-loat">
            <svg data-testid="link-icon" />
            Cấp hàng loạt
          </a>
        </Button>
        <Button>
          <svg data-testid="button-icon" />
          Tạo tài khoản
        </Button>
      </>,
    );
    const link = screen.getByRole("link", { name: "Cấp hàng loạt" });
    const button = screen.getByRole("button", { name: "Tạo tài khoản" });
    // Lớp cỡ icon nằm ở phần tử gốc của cả hai nhánh, nên áp cho mọi svg bên trong.
    for (const root of [link, button]) {
      expect(root).toHaveClass("[&_svg]:size-4", "[&_svg]:shrink-0");
    }
    // Khoảng cách icon–nhãn: ở liên kết là gap của chính nó, ở nút thường là gap của phần tử bọc nhãn.
    expect(screen.getByTestId("link-icon").parentElement).toBe(link);
    expect(link).toHaveClass("inline-flex", "items-center", "gap-2");
    expect(screen.getByTestId("button-icon").parentElement).toHaveClass("inline-flex", "items-center", "gap-2");
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
