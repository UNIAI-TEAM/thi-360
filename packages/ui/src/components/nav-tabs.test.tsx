import { render, screen, waitFor, within } from "@testing-library/react";
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

  describe("thanh không đủ chỗ", () => {
    const ids = ["t1", "t2", "t3", "t4", "t5"];

    function Many({ current }: { current: string }) {
      return (
        <NavTabs aria-label="Bài thi">
          {ids.map((id) => (
            <NavTab key={id} href={`/${id}`} current={id === current}>
              {`Mục ${id}`}
            </NavTab>
          ))}
        </NavTabs>
      );
    }

    /** Thanh rộng 300px từ x=100, mỗi mục rộng 120px xếp liền nhau, trừ phần đã cuộn. */
    function layout(list: HTMLElement) {
      list.getBoundingClientRect = () => ({ left: 100, right: 400, top: 0, bottom: 40, width: 300, height: 40 }) as DOMRect;
      within(list)
        .getAllByRole("link")
        .forEach((link, index) => {
          Object.defineProperty(link, "offsetHeight", { configurable: true, value: 40 });
          link.getBoundingClientRect = () => {
            const left = 100 + index * 120 - list.scrollLeft;
            return { left, right: left + 120, top: 0, bottom: 40, width: 120, height: 40 } as DOMRect;
          };
        });
    }

    function listOf(nav: HTMLElement): HTMLElement {
      const list = nav.querySelector("ul");
      if (!list) throw new Error("thiếu danh sách");
      return list;
    }

    it("chuyển sang mục bị khuất ở cuối thì chỉ thanh cuộn tới, lộ thêm một phần mục kế bên", async () => {
      const { rerender } = render(<Many current="t1" />);
      const list = listOf(screen.getByRole("navigation", { name: "Bài thi" }));
      layout(list);
      rerender(<Many current="t5" />);
      // Mục t5 chiếm x=580..700: cần cuộn 700 + 40 - 400 = 340.
      await waitFor(() => expect(list.scrollLeft).toBe(340));
    });

    it("chuyển về mục bị khuất ở đầu thì cuộn ngược lại", async () => {
      const { rerender } = render(<Many current="t5" />);
      const list = listOf(screen.getByRole("navigation", { name: "Bài thi" }));
      layout(list);
      list.scrollLeft = 300;
      rerender(<Many current="t2" />);
      // Với scrollLeft=300, mục t2 chiếm x=-80..40, nằm trước mép đầu x=100.
      await waitFor(() => expect(list.scrollLeft).toBe(80));
    });

    it("mục đang mở đã hiện trọn thì không cuộn", async () => {
      const { rerender } = render(<Many current="t1" />);
      const list = listOf(screen.getByRole("navigation", { name: "Bài thi" }));
      layout(list);
      list.scrollLeft = 60;
      rerender(<Many current="t2" />);
      await waitFor(() => expect(screen.getByRole("link", { name: "Mục t2" })).toHaveAttribute("aria-current", "page"));
      expect(list.scrollLeft).toBe(60);
    });

    it("gắn thêm tab mới đang chọn sau lần dựng đầu vẫn tự cuộn tới tab đó (R1-13)", async () => {
      function Dynamic({ appendActive }: { appendActive: boolean }) {
        return (
          <NavTabs aria-label="Bài thi">
            {ids.map((id) => (
              <NavTab key={id} href={`/${id}`} current={!appendActive && id === "t1"}>
                {`Mục ${id}`}
              </NavTab>
            ))}
            {appendActive && (
              <NavTab href="/t6" current>
                Mục t6
              </NavTab>
            )}
          </NavTabs>
        );
      }
      const { rerender } = render(<Dynamic appendActive={false} />);
      const list = listOf(screen.getByRole("navigation", { name: "Bài thi" }));
      layout(list);

      rerender(<Dynamic appendActive />);
      const added = screen.getByRole("link", { name: "Mục t6" });
      Object.defineProperty(added, "offsetHeight", { configurable: true, value: 40 });
      added.getBoundingClientRect = () => ({ left: 700 - list.scrollLeft, right: 820 - list.scrollLeft, top: 0, bottom: 40, width: 120, height: 40 }) as DOMRect;

      // Mục t6 chiếm x=700..820: cần cuộn 820 + 40 - 400 = 460.
      await waitFor(() => expect(list.scrollLeft).toBe(460));
    });
  });
});
