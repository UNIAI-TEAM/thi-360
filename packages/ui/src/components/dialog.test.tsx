import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "./dialog";

function Example({ size }: { size?: "md" | "lg" }) {
  return (
    <Dialog>
      <DialogTrigger>Mở</DialogTrigger>
      <DialogContent closeLabel="Đóng" size={size}>
        <DialogHeader>
          <DialogTitle>Tạo vai trò</DialogTitle>
          <DialogDescription>Nhập tên vai trò</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <button>Lưu</button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

describe("Dialog", () => {
  it("mở khi bấm nút kích hoạt, có tiêu đề và mô tả làm tên truy cập", async () => {
    render(<Example />);
    expect(screen.queryByRole("dialog")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    const dialog = await screen.findByRole("dialog", { name: "Tạo vai trò" });
    expect(dialog).toHaveAccessibleDescription("Nhập tên vai trò");
  });

  it("đóng bằng nút × và bằng phím Esc", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    await userEvent.click(await screen.findByRole("button", { name: "Đóng" }));
    expect(screen.queryByRole("dialog")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("rộng 480px mặc định, 720px với size lg", async () => {
    const { unmount } = render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    expect(await screen.findByRole("dialog")).toHaveClass("max-w-[480px]");
    unmount();

    render(<Example size="lg" />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    expect(await screen.findByRole("dialog")).toHaveClass("max-w-[720px]");
  });
});
