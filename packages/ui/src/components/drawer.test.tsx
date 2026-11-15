import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
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

  it("mặc định bám mép phải, bo góc cạnh trong (trái), trượt từ phải; đệm 16px dưới sm, 24px từ sm", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Chi tiết" }));
    const drawer = await screen.findByRole("dialog");
    expect(drawer).toHaveClass(
      "right-0",
      "rounded-l-xl",
      "motion-safe:data-[state=open]:animate-drawer-in",
      "motion-safe:data-[state=closed]:animate-drawer-out",
      "p-4",
      "sm:p-6",
    );
    expect(drawer).not.toHaveClass("left-0", "rounded-r-xl", "p-6");
  });

  it("side=\"left\": bám mép trái, bo góc cạnh trong (phải), trượt từ trái và chỉ trong motion-safe", async () => {
    render(
      <Drawer open onOpenChange={() => {}}>
        <DrawerContent side="left" closeLabel="Đóng">
          <DrawerTitle>Menu điều hướng</DrawerTitle>
          <DrawerDescription>Các mục quản trị</DrawerDescription>
        </DrawerContent>
      </Drawer>,
    );
    const drawer = await screen.findByRole("dialog", { name: "Menu điều hướng" });
    expect(drawer).toHaveClass(
      "left-0",
      "rounded-r-xl",
      "motion-safe:data-[state=open]:animate-drawer-in-left",
      "motion-safe:data-[state=closed]:animate-drawer-out-left",
    );
    expect(drawer).not.toHaveClass("right-0", "rounded-l-xl");
    const animated = drawer.className.split(/\s+/).filter((name) => name.includes("animate-"));
    expect(animated).toHaveLength(2);
    for (const name of animated) expect(name).toMatch(/^motion-safe:/);
    // Nút × vẫn ở cạnh trong (phải) và bấm được.
    expect(screen.getByRole("button", { name: "Đóng" })).toHaveClass("absolute", "right-4");
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

  // C-12: jsdom không có layout; kiểm cấu trúc quyết định việc tiêu đề và nút × không cuộn mất.
  function Tall({ withFooter = false }: { withFooter?: boolean }) {
    return (
      <Drawer open onOpenChange={() => {}}>
        <DrawerContent closeLabel="Đóng" className="max-w-[640px]" data-testid="drawer">
          <DrawerHeader>
            <DrawerTitle>Chi tiết giá trị</DrawerTitle>
            <DrawerDescription>Mô tả</DrawerDescription>
          </DrawerHeader>
          <p>Nội dung dài</p>
          {withFooter && (
            <DrawerFooter>
              <button>Lưu</button>
            </DrawerFooter>
          )}
        </DrawerContent>
      </Drawer>
    );
  }

  it("khung drawer không cuộn; nội dung cuộn trong vùng bên trong, tiêu đề dính mép trên", async () => {
    render(<Tall />);
    const drawer = await screen.findByRole("dialog");
    expect(drawer).toHaveClass("flex", "flex-col", "inset-y-0");
    expect(drawer.className.split(/\s+/).filter((name) => /^overflow-/.test(name))).toEqual([]);

    const title = screen.getByText("Chi tiết giá trị");
    const scrollRegion = title.closest("div.overflow-y-auto") as HTMLElement;
    expect(scrollRegion).not.toBeNull();
    expect(scrollRegion.parentElement).toBe(drawer);
    expect(scrollRegion).toHaveClass("min-h-0", "flex-1", "flex-col", "gap-4");
    expect(scrollRegion.contains(screen.getByText("Nội dung dài"))).toBe(true);
    // Không có padding/margin dọc: nội dung cuộn không lộ ra phía trên tiêu đề dính.
    expect(scrollRegion.className.split(/\s+/).filter((name) => /^-?m[ytb]-|^p[ytb]-/.test(name))).toEqual([]);

    const header = title.parentElement as HTMLElement;
    expect(header).toHaveClass("sticky", "top-0", "bg-surface", "pb-2", "pr-10");
  });

  it("nút × nằm ngoài vùng cuộn, là con trực tiếp của drawer và xếp trên tiêu đề/chân dính", async () => {
    render(<Tall withFooter />);
    const drawer = await screen.findByRole("dialog");
    const close = screen.getByRole("button", { name: "Đóng" });
    const header = screen.getByText("Chi tiết giá trị").parentElement as HTMLElement;
    const footer = screen.getByRole("button", { name: "Lưu" }).parentElement as HTMLElement;
    const scrollRegion = header.closest("div.overflow-y-auto") as HTMLElement;

    const z = (element: Element) => {
      const match = /(?:^|\s)z-(\d+)(?:\s|$)/.exec(element.className);
      return match ? Number(match[1]) : 0;
    };
    expect(close).toHaveClass("absolute");
    expect(close.parentElement).toBe(drawer);
    expect(scrollRegion.contains(close)).toBe(false);
    expect(scrollRegion.nextElementSibling).toBe(close);
    expect(z(close)).toBeGreaterThan(z(header));
    expect(z(close)).toBeGreaterThan(z(footer));
  });

  it("DrawerFooter dính mép dưới và bị đẩy xuống đáy khi nội dung ngắn", async () => {
    render(<Tall withFooter />);
    await screen.findByRole("dialog");
    const footer = screen.getByRole("button", { name: "Lưu" }).parentElement as HTMLElement;
    expect(footer).toHaveClass("sticky", "bottom-0", "mt-auto", "bg-surface", "justify-end", "flex-wrap");
    expect(footer.closest("div.overflow-y-auto")).not.toBeNull();
  });

  it("giữ nguyên API: className và thuộc tính của DrawerContent vẫn tới phần tử drawer", async () => {
    render(<Tall />);
    const drawer = await screen.findByTestId("drawer");
    expect(drawer).toHaveAttribute("role", "dialog");
    expect(drawer).toHaveClass("max-w-[640px]");
    expect(drawer).not.toHaveClass("max-w-[520px]");
  });
});
