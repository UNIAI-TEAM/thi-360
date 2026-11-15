import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "./dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "./drawer";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "./dropdown-menu";
import { Tooltip } from "./tooltip";

/*
 * U-13. Trước đây: `data-[state=open]:animate-* motion-reduce:animate-none`. CSS sinh ra (Tailwind 4.3.5 đang cài):
 *   .data-\[state\=open\]\:animate-dialog-in[data-state="open"] { animation: … }        độ ưu tiên (0,2,0)
 *   @media (prefers-reduced-motion: reduce) { .motion-reduce\:animate-none { animation: none } }   (0,1,0)
 * nên lệnh hủy không bao giờ thắng. Nay mọi lớp `animate-*` của lớp phủ nằm trong `motion-safe:`, tức
 *   @media (prefers-reduced-motion: no-preference) { .motion-safe\:data-…[data-state="open"] { animation: … } }
 * và khi người dùng bật giảm chuyển động thì không còn khai báo `animation` nào áp dụng.
 * jsdom không áp CSS của Tailwind, nên ở đây kiểm hợp đồng lớp: không lớp `animate-*` nào đứng ngoài `motion-safe:`.
 */
function expectAnimationsOptIn(element: Element | null) {
  expect(element).not.toBeNull();
  const animated = (element as Element).className.split(/\s+/).filter((name) => name.includes("animate-"));
  expect(animated.length).toBeGreaterThan(0);
  for (const name of animated) {
    expect(name, `${name} phải nằm trong motion-safe:`).toMatch(/^motion-safe:/);
  }
  expect((element as Element).className).not.toContain("motion-reduce:animate-none");
}

describe("lớp phủ tôn trọng prefers-reduced-motion (U-13)", () => {
  it("Dialog và nền mờ: hiệu ứng mở/đóng chỉ khai báo trong motion-safe", async () => {
    render(
      <Dialog open onOpenChange={() => {}}>
        <DialogContent closeLabel="Đóng">
          <DialogTitle>Tiêu đề</DialogTitle>
          <DialogDescription>Mô tả</DialogDescription>
        </DialogContent>
      </Dialog>,
    );
    const dialog = await screen.findByRole("dialog");
    expectAnimationsOptIn(dialog);
    expect(dialog).toHaveClass(
      "motion-safe:data-[state=open]:animate-dialog-in",
      "motion-safe:data-[state=closed]:animate-dialog-out",
    );
    const overlay = document.querySelector(".bg-overlay");
    expectAnimationsOptIn(overlay);
    expect(overlay).toHaveClass(
      "motion-safe:data-[state=open]:animate-fade-in",
      "motion-safe:data-[state=closed]:animate-fade-out",
    );
  });

  it("Drawer: hiệu ứng trượt chỉ khai báo trong motion-safe", async () => {
    render(
      <Drawer open onOpenChange={() => {}}>
        <DrawerContent closeLabel="Đóng">
          <DrawerTitle>Chi tiết</DrawerTitle>
          <DrawerDescription>Mô tả</DrawerDescription>
        </DrawerContent>
      </Drawer>,
    );
    const drawer = await screen.findByRole("dialog");
    expectAnimationsOptIn(drawer);
    expect(drawer).toHaveClass(
      "motion-safe:data-[state=open]:animate-drawer-in",
      "motion-safe:data-[state=closed]:animate-drawer-out",
    );
  });

  it("DropdownMenu: hiệu ứng hiện dần chỉ khai báo trong motion-safe", async () => {
    render(
      <DropdownMenu>
        <DropdownMenuTrigger>Thao tác</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Sửa</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Thao tác" }));
    const menu = await screen.findByRole("menu");
    expectAnimationsOptIn(menu);
    expect(menu).toHaveClass("motion-safe:data-[state=open]:animate-fade-in");
  });

  it("Tooltip: hiệu ứng hiện dần chỉ khai báo trong motion-safe", async () => {
    render(
      <Tooltip content="Sửa vai trò" delayDuration={0}>
        <button>Mở</button>
      </Tooltip>,
    );
    await userEvent.tab();
    const tooltip = await screen.findByRole("tooltip");
    const content = tooltip.closest("[data-state]");
    expectAnimationsOptIn(content);
    expect(content).toHaveClass("motion-safe:data-[state=delayed-open]:animate-fade-in");
  });
});
