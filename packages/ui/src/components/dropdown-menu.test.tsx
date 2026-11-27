import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "./dropdown-menu";

function Example({ onSelect }: { onSelect: (name: string) => void }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger>Tài khoản</DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuItem onSelect={() => onSelect("profile")}>Hồ sơ</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem destructive onSelect={() => onSelect("logout")}>
          Đăng xuất
        </DropdownMenuItem>
        <DropdownMenuItem disabled onSelect={() => onSelect("disabled")}>
          Bị khóa
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

describe("DropdownMenu", () => {
  it("menu dài gọn trong 20rem (hoặc chỗ trống nếu ít hơn) và cuộn bên trong (UNI-2878)", async () => {
    render(<Example onSelect={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "Tài khoản" }));
    const menu = await screen.findByRole("menu");
    expect(menu).toHaveClass("overflow-y-auto", "max-h-[min(20rem,calc(var(--radix-dropdown-menu-content-available-height)-0.5rem))]");
  });

  it("mở khi bấm nút kích hoạt, chọn mục thì gọi onSelect và đóng menu", async () => {
    const onSelect = vi.fn();
    render(<Example onSelect={onSelect} />);
    expect(screen.queryByRole("menu")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Tài khoản" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Hồ sơ" }));
    expect(onSelect).toHaveBeenCalledWith("profile");
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("mục bị khóa không chọn được", async () => {
    const onSelect = vi.fn();
    render(<Example onSelect={onSelect} />);
    await userEvent.click(screen.getByRole("button", { name: "Tài khoản" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Bị khóa" }));
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("điều khiển bằng bàn phím: mũi tên xuống rồi Enter", async () => {
    const onSelect = vi.fn();
    render(<Example onSelect={onSelect} />);
    screen.getByRole("button", { name: "Tài khoản" }).focus();
    await userEvent.keyboard("{Enter}");
    await screen.findByRole("menu");
    await userEvent.keyboard("{ArrowDown}{Enter}");
    expect(onSelect).toHaveBeenCalledWith("logout");
  });

  it("mục nguy hiểm dùng màu danger", async () => {
    render(<Example onSelect={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: "Tài khoản" }));
    expect(await screen.findByRole("menuitem", { name: "Đăng xuất" })).toHaveClass("text-danger");
  });

  it("DropdownMenuRadioGroup và RadioItem đặt role menuitemradio và aria-checked theo giá trị đang chọn", async () => {
    const onValueChange = vi.fn();
    render(
      <DropdownMenu>
        <DropdownMenuTrigger>Ngôn ngữ</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuRadioGroup value="vi" onValueChange={onValueChange}>
            <DropdownMenuRadioItem value="vi">Tiếng Việt</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="en">English</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Ngôn ngữ" }));
    const viItem = await screen.findByRole("menuitemradio", { name: "Tiếng Việt" });
    const enItem = screen.getByRole("menuitemradio", { name: "English" });
    expect(viItem).toHaveAttribute("aria-checked", "true");
    expect(enItem).toHaveAttribute("aria-checked", "false");

    await userEvent.click(enItem);
    expect(onValueChange).toHaveBeenCalledWith("en");
  });

  // C-13 / S-04: nền tô sáng mất ở chế độ tương phản cao; mục đang focus phải có viền ngoài thật ở chế độ đó.
  it("mục menu không tắt hẳn viền ngoài: dùng focus:outline-hidden để còn viền ở chế độ tương phản cao", async () => {
    render(
      <DropdownMenu>
        <DropdownMenuTrigger>Ngôn ngữ</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Hồ sơ</DropdownMenuItem>
          <DropdownMenuRadioGroup value="vi">
            <DropdownMenuRadioItem value="vi">Tiếng Việt</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Ngôn ngữ" }));
    for (const item of [
      await screen.findByRole("menuitem", { name: "Hồ sơ" }),
      screen.getByRole("menuitemradio", { name: "Tiếng Việt" }),
    ]) {
      expect(item).toHaveClass("focus:outline-hidden");
      expect(item).not.toHaveClass("outline-none");
    }
  });

  // S-12: jsdom không áp media query; `pointer-coarse:h-11` biên dịch thành `@media (pointer: coarse) { height: 44px }`
  // đứng sau `.h-10` (đã đối chiếu CSS sinh ra của Tailwind 4.3.5 đang cài).
  it("mục menu cao 40px với chuột và 44px trên thiết bị trỏ thô (cảm ứng)", async () => {
    render(
      <DropdownMenu>
        <DropdownMenuTrigger>Ngôn ngữ</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Hồ sơ</DropdownMenuItem>
          <DropdownMenuRadioGroup value="vi">
            <DropdownMenuRadioItem value="vi">Tiếng Việt</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Ngôn ngữ" }));
    for (const item of [
      await screen.findByRole("menuitem", { name: "Hồ sơ" }),
      screen.getByRole("menuitemradio", { name: "Tiếng Việt" }),
    ]) {
      expect(item).toHaveClass("h-10", "pointer-coarse:h-11");
    }
  });
});
