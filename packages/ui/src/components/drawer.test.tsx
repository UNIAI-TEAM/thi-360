import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "./drawer";

function Example() {
  return (
    <Drawer>
      <DrawerTrigger>Chi tiết</DrawerTrigger>
      <DrawerContent closeLabel="Đóng">
        <DrawerHeader>
          <DrawerTitle>Nơi đang dùng</DrawerTitle>
          <DrawerDescription>3 lớp đang dùng giá trị này</DrawerDescription>
        </DrawerHeader>
      </DrawerContent>
    </Drawer>
  );
}

describe("Drawer", () => {
  it("mở từ nút kích hoạt, rộng tối đa 520px, có tên và mô tả", async () => {
    render(<Example />);
    expect(screen.queryByRole("dialog")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Chi tiết" }));
    const drawer = await screen.findByRole("dialog", { name: "Nơi đang dùng" });
    expect(drawer).toHaveAccessibleDescription("3 lớp đang dùng giá trị này");
    expect(drawer).toHaveClass("max-w-[520px]", "right-0");
  });

  it("đóng bằng nút × và bằng Esc", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Chi tiết" }));
    await userEvent.click(await screen.findByRole("button", { name: "Đóng" }));
    expect(screen.queryByRole("dialog")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Chi tiết" }));
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
