import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Chip } from "./chip";

describe("Chip", () => {
  it("chip lọc là nút bật/tắt, aria-pressed theo selected", async () => {
    const onClick = vi.fn();
    const { rerender } = render(<Chip onClick={onClick}>Đang hoạt động</Chip>);
    const chip = screen.getByRole("button", { name: "Đang hoạt động" });
    expect(chip).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(chip);
    expect(onClick).toHaveBeenCalledTimes(1);

    rerender(
      <Chip selected onClick={onClick}>
        Đang hoạt động
      </Chip>,
    );
    expect(screen.getByRole("button", { name: "Đang hoạt động" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("cao 32px (admin) mặc định, 40px với size md", () => {
    render(
      <>
        <Chip>Nhỏ</Chip>
        <Chip size="md">Lớn</Chip>
      </>,
    );
    expect(screen.getByRole("button", { name: "Nhỏ" })).toHaveClass("h-8");
    expect(screen.getByRole("button", { name: "Lớn" })).toHaveClass("h-10");
  });

  it("chip nhỏ chỉ hiển thị, không phải nút", () => {
    render(<Chip variant="tag">Toán</Chip>);
    expect(screen.getByText("Toán")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("chấm màu giấu khỏi trình đọc màn hình", () => {
    render(<Chip dotClassName="bg-accent-500">Cam</Chip>);
    expect(screen.getByRole("button", { name: "Cam" }).querySelector("[aria-hidden]")).not.toBeNull();
  });
});
