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

  // VIS-2: jsdom không có layout; kiểm tra cấu trúc quyết định việc cuộn trên màn hình thấp.
  it("giới hạn chiều cao theo viewport, cuộn nội dung bên trong, tiêu đề/chân dính và nút × không cuộn", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveClass("max-h-[calc(100dvh-2rem)]", "flex-col");

    const scrollRegion = screen.getByText("Tạo vai trò").closest("div.overflow-y-auto");
    expect(scrollRegion).not.toBeNull();
    expect(scrollRegion).toHaveClass("min-h-0", "flex-1");
    expect(dialog.contains(scrollRegion)).toBe(true);

    const title = screen.getByText("Tạo vai trò");
    const footerButton = screen.getByRole("button", { name: "Lưu" });
    expect(scrollRegion?.contains(title)).toBe(true);
    expect(scrollRegion?.contains(footerButton)).toBe(true);
    expect(footerButton.parentElement).toHaveClass("sticky", "bottom-0", "bg-surface");
    expect(title.parentElement).toHaveClass("sticky", "top-0", "bg-surface");

    const close = screen.getByRole("button", { name: "Đóng" });
    expect(scrollRegion?.contains(close)).toBe(false);
  });

  // V2-2 / V2-4: jsdom không có layout; kiểm tra hợp đồng DOM/z-index quyết định việc nút × bấm được.
  it("nút × nằm trên tiêu đề/chân dính (z-index cao hơn, đứng sau trong DOM, định vị tuyệt đối) và tiêu đề chừa chỗ cho nó", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    const dialog = await screen.findByRole("dialog");
    const close = screen.getByRole("button", { name: "Đóng" });
    const header = screen.getByText("Tạo vai trò").parentElement as HTMLElement;
    const footer = screen.getByRole("button", { name: "Lưu" }).parentElement as HTMLElement;

    const z = (el: Element) => {
      const match = /(?:^|\s)z-(\d+)(?:\s|$)/.exec(el.className);
      return match ? Number(match[1]) : 0;
    };
    expect(close).toHaveClass("absolute");
    expect(z(close)).toBeGreaterThan(z(header));
    expect(z(close)).toBeGreaterThan(z(footer));
    // Cùng ngữ cảnh xếp chồng (dialog là fixed + z-50), × là con trực tiếp của dialog, sau vùng cuộn.
    expect(close.parentElement).toBe(dialog);
    const scrollRegion = header.closest("div.overflow-y-auto") as HTMLElement;
    expect(scrollRegion.nextElementSibling).toBe(close);
    // Tiêu đề chừa >= 40px (pr-10) bên phải cho nút × rộng 36px.
    expect(header).toHaveClass("pr-10");
  });

  it("vùng cuộn không có margin/padding dọc để nội dung cuộn không lộ ra phía trên tiêu đề dính", async () => {
    render(<Example />);
    await userEvent.click(screen.getByRole("button", { name: "Mở" }));
    await screen.findByRole("dialog");
    const scrollRegion = screen.getByText("Tạo vai trò").closest("div.overflow-y-auto") as HTMLElement;
    const classes = scrollRegion.className.split(/\s+/);
    expect(classes.filter((c) => /^-?m[ytb]-|^p[ytb]-/.test(c))).toEqual([]);
  });

  it("giữ nguyên API: className và thuộc tính của DialogContent vẫn tới phần tử dialog", async () => {
    render(
      <Dialog defaultOpen>
        <DialogContent closeLabel="Đóng" className="max-w-[920px]" data-testid="dlg">
          <DialogTitle>Tiêu đề</DialogTitle>
          <DialogDescription>Mô tả</DialogDescription>
        </DialogContent>
      </Dialog>,
    );
    const dialog = await screen.findByTestId("dlg");
    expect(dialog).toHaveClass("max-w-[920px]");
    expect(dialog).toHaveAttribute("role", "dialog");
  });
});
