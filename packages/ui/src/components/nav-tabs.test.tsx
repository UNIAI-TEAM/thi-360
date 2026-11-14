import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NavTab, NavTabs } from "./nav-tabs";

function Example({ current }: { current: "roles" | "users" }) {
  return (
    <NavTabs aria-label="Phân quyền">
      <NavTab href="/rbac/roles" current={current === "roles"}>
        Vai trò
      </NavTab>
      <NavTab asChild current={current === "users"}>
        <a href="/rbac/users" data-app-link="true">
          Người dùng theo quyền
        </a>
      </NavTab>
    </NavTabs>
  );
}

describe("NavTabs", () => {
  it("là nhóm điều hướng gồm các liên kết, không dùng vai trò tablist/tab", () => {
    render(<Example current="roles" />);
    const nav = screen.getByRole("navigation", { name: "Phân quyền" });
    expect(within(nav).getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual([
      "/rbac/roles",
      "/rbac/users",
    ]);
    expect(screen.queryByRole("tablist")).toBeNull();
    expect(screen.queryByRole("tab")).toBeNull();
  });

  it("chỉ mục của trang đang mở mang aria-current=page", () => {
    const { rerender } = render(<Example current="roles" />);
    expect(screen.getByRole("link", { name: "Vai trò" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Người dùng theo quyền" })).not.toHaveAttribute("aria-current");

    rerender(<Example current="users" />);
    expect(screen.getByRole("link", { name: "Vai trò" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("link", { name: "Người dùng theo quyền" })).toHaveAttribute("aria-current", "page");
  });

  it("asChild giữ nguyên liên kết của ứng dụng và gắn trạng thái lên chính nó", () => {
    render(<Example current="users" />);
    const link = screen.getByRole("link", { name: "Người dùng theo quyền" });
    expect(link).toHaveAttribute("data-app-link", "true");
    expect(link).toHaveAttribute("aria-current", "page");
    expect(link.parentElement?.tagName).toBe("LI");
  });

  it("mục đang chọn và mục thường cùng độ đậm chữ, mục đang chọn có gạch chân bằng viền", () => {
    render(<Example current="roles" />);
    const current = screen.getByRole("link", { name: "Vai trò" });
    const other = screen.getByRole("link", { name: "Người dùng theo quyền" });
    for (const link of [current, other]) {
      expect(link).toHaveClass("font-medium");
      expect(link.className).not.toMatch(/font-semibold/);
      expect(link).toHaveClass("aria-[current=page]:after:border-b-2", "aria-[current=page]:after:border-current");
      // Không có viền (kể cả trong suốt) ở trạng thái thường: chế độ tương phản cao sẽ tô nó thành gạch chân.
      expect(link.className).not.toMatch(/(^|\s)after:border-(b-2|transparent)(\s|$)/);
    }
  });
});
