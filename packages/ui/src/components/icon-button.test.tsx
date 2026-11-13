import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Pencil } from "lucide-react";
import { describe, expect, it, vi } from "vitest";
import { IconButton } from "./icon-button";

describe("IconButton", () => {
  it("tên truy cập lấy từ aria-label và gọi onClick", async () => {
    const onClick = vi.fn();
    render(<IconButton aria-label="Sửa vai trò Vận hành" icon={<Pencil />} onClick={onClick} />);
    await userEvent.click(screen.getByRole("button", { name: "Sửa vai trò Vận hành" }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("focus bàn phím hiện tooltip, mặc định là aria-label", async () => {
    render(<IconButton aria-label="Xóa vai trò Vận hành" icon={<Pencil />} />);
    await userEvent.tab();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Xóa vai trò Vận hành");
  });

  it("bị khóa: không gọi onClick nhưng vẫn focus được và tooltip nêu lý do", async () => {
    const onClick = vi.fn();
    render(
      <IconButton
        aria-label="Xóa vai trò Hệ thống"
        icon={<Pencil />}
        disabled
        tooltip="Vai trò hệ thống không xóa được"
        onClick={onClick}
      />,
    );
    const button = screen.getByRole("button", { name: "Xóa vai trò Hệ thống" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
    await userEvent.tab({ shift: true });
    await userEvent.tab();
    expect(button).toHaveFocus();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Vai trò hệ thống không xóa được");
  });

  it("icon giấu khỏi trình đọc màn hình", () => {
    render(<IconButton aria-label="Sửa" icon={<Pencil data-testid="icon" />} />);
    expect(screen.getByTestId("icon")).toHaveAttribute("aria-hidden", "true");
  });

  it("aria-disabled do bên gọi truyền cũng chặn onClick", async () => {
    const onClick = vi.fn();
    render(<IconButton aria-label="Sửa" icon={<Pencil />} aria-disabled onClick={onClick} />);
    const button = screen.getByRole("button", { name: "Sửa" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it("aria-disabled=false không khóa nút", async () => {
    const onClick = vi.fn();
    render(<IconButton aria-label="Sửa" icon={<Pencil />} aria-disabled={false} onClick={onClick} />);
    const button = screen.getByRole("button", { name: "Sửa" });
    expect(button).not.toHaveAttribute("aria-disabled");
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("bị khóa trong form: nút submit không gửi form, bấm thường vẫn gửi", async () => {
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
    const { rerender } = render(
      <form onSubmit={onSubmit}>
        <IconButton aria-label="Lưu" icon={<Pencil />} type="submit" disabled />
      </form>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Lưu" }));
    expect(fireEvent.click(screen.getByRole("button", { name: "Lưu" }))).toBe(false);
    expect(onSubmit).not.toHaveBeenCalled();

    rerender(
      <form onSubmit={onSubmit}>
        <IconButton aria-label="Lưu" icon={<Pencil />} type="submit" />
      </form>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Lưu" }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});
