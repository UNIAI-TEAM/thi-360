import { render, screen } from "@testing-library/react";
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
});
