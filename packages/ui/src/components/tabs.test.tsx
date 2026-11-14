import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { NavTab } from "./nav-tabs";
import { Tabs, TabsContent, TabsList, TabsTrigger, type TabsVariant } from "./tabs";

function Example({ variant }: { variant: TabsVariant }) {
  return (
    <Tabs variant={variant} defaultValue="roles">
      <TabsList aria-label="Chi tiết">
        <TabsTrigger value="roles">Vai trò</TabsTrigger>
        <TabsTrigger value="history">Lịch sử</TabsTrigger>
      </TabsList>
      <TabsContent value="roles">Nội dung vai trò</TabsContent>
      <TabsContent value="history">Nội dung lịch sử</TabsContent>
    </Tabs>
  );
}

describe.each<TabsVariant>(["segmented", "content"])("Tabs kiểu %s", (variant) => {
  it("chỉ hiện nội dung của tab đang chọn và đổi khi bấm tab khác", async () => {
    render(<Example variant={variant} />);
    expect(screen.getByRole("tab", { name: "Vai trò" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Nội dung vai trò")).toBeVisible();
    expect(screen.queryByText("Nội dung lịch sử")).toBeNull();

    await userEvent.click(screen.getByRole("tab", { name: "Lịch sử" }));
    expect(screen.getByRole("tab", { name: "Lịch sử" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Nội dung lịch sử")).toBeVisible();
    expect(screen.queryByText("Nội dung vai trò")).toBeNull();
  });

  it("đánh dấu kiểu trên danh sách tab", () => {
    render(<Example variant={variant} />);
    expect(screen.getByRole("tablist")).toHaveAttribute("data-variant", variant);
  });

  it("độ đậm chữ như nhau dù chọn hay không, để tab không đổi bề rộng khi được chọn", async () => {
    render(<Example variant={variant} />);
    const weightClasses = (name: string) =>
      screen
        .getByRole("tab", { name })
        .className.split(/\s+/)
        .filter((cls) => /(^|:)font-(thin|extralight|light|normal|medium|semibold|bold|extrabold|black)$/.test(cls));

    expect(weightClasses("Vai trò")).toEqual(["font-medium"]);
    expect(weightClasses("Lịch sử")).toEqual(["font-medium"]);

    await userEvent.click(screen.getByRole("tab", { name: "Lịch sử" }));
    expect(weightClasses("Vai trò")).toEqual(["font-medium"]);
    expect(weightClasses("Lịch sử")).toEqual(["font-medium"]);
  });

  it("tab đang chọn có gạch chân vẽ bằng viền của ::after, gắn theo data-state", async () => {
    render(<Example variant={variant} />);
    const active = screen.getByRole("tab", { name: "Vai trò" });
    const inactive = screen.getByRole("tab", { name: "Lịch sử" });

    for (const tab of [active, inactive]) {
      expect(tab).toHaveClass(
        "relative",
        "after:absolute",
        "after:inset-x-3",
        "after:bottom-1",
        "data-[state=active]:after:border-b-2",
        "data-[state=active]:after:border-current",
      );
      // Không vẽ chỉ báo bằng nền hay bóng đổ (chế độ tương phản cao bỏ cả hai), không để viền trong suốt
      // ở tab chưa chọn (chế độ đó tô lại viền trong suốt).
      expect(tab.className).not.toMatch(/after:(bg|shadow|ring)-/);
      expect(tab.className).not.toMatch(/(^|\s)after:border-/);
    }
    expect(active).toHaveAttribute("data-state", "active");
    expect(inactive).toHaveAttribute("data-state", "inactive");

    await userEvent.click(inactive);
    expect(inactive).toHaveAttribute("data-state", "active");
    expect(active).toHaveAttribute("data-state", "inactive");
  });
});

describe("Tabs", () => {
  it("kiểu phân đoạn cao 48px, kiểu nội dung không có thanh nền", () => {
    const { rerender } = render(<Example variant="segmented" />);
    expect(screen.getByRole("tablist")).toHaveClass("h-12", "bg-subtle");
    rerender(<Example variant="content" />);
    expect(screen.getByRole("tablist")).not.toHaveClass("bg-subtle");
  });

  it("chuyển tab bằng phím mũi tên", async () => {
    render(<Example variant="content" />);
    screen.getByRole("tab", { name: "Vai trò" }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Lịch sử" })).toHaveAttribute("aria-selected", "true");
  });

  describe("thanh tab nội dung không đủ chỗ", () => {
    /** Đặt hình chữ nhật cho thanh tab (rộng 300px từ x=100) và từng tab (rộng 120px, xếp liền nhau, trừ phần đã cuộn). */
    function layout(list: HTMLElement) {
      list.getBoundingClientRect = () => ({ left: 100, right: 400, top: 0, bottom: 40, width: 300, height: 40 }) as DOMRect;
      within(list)
        .getAllByRole("tab")
        .forEach((tab, index) => {
          Object.defineProperty(tab, "offsetHeight", { configurable: true, value: 40 });
          tab.getBoundingClientRect = () => {
            const left = 100 + index * 120 - list.scrollLeft;
            return { left, right: left + 120, top: 0, bottom: 40, width: 120, height: 40 } as DOMRect;
          };
        });
    }

    function Many({ value, onValueChange }: { value?: string; onValueChange?: (value: string) => void }) {
      return (
        <Tabs variant="content" defaultValue={value === undefined ? "t1" : undefined} value={value} onValueChange={onValueChange}>
          <TabsList aria-label="Chi tiết">
            {["t1", "t2", "t3", "t4", "t5"].map((id) => (
              <TabsTrigger key={id} value={id}>
                {`Tab ${id}`}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      );
    }

    it("luôn một hàng và tự cuộn ngang trong chính nó; tab không co lại, không xuống dòng", () => {
      render(<Many />);
      const list = screen.getByRole("tablist");
      expect(list).toHaveClass("flex", "overflow-x-auto");
      expect(list.className).not.toMatch(/(^|\s)flex-wrap(\s|$)/);
      // Đệm bù bằng lề âm để vùng cuộn không cắt vòng focus của tab.
      expect(list).toHaveClass("p-1", "-m-1");
      for (const tab of screen.getAllByRole("tab")) {
        expect(tab).toHaveClass("shrink-0", "whitespace-nowrap");
      }
    });

    it("kiểu phân đoạn giữ nguyên: các tab chia đều bề rộng, không thành vùng cuộn", () => {
      render(<Example variant="segmented" />);
      expect(screen.getByRole("tablist")).not.toHaveClass("overflow-x-auto");
      expect(screen.getByRole("tab", { name: "Vai trò" })).toHaveClass("flex-1");
      expect(screen.getByRole("tab", { name: "Vai trò" })).not.toHaveClass("shrink-0");
    });

    it("chọn tab bị khuất ở cuối: chỉ thanh tab cuộn tới, lộ thêm một phần tab kế bên", async () => {
      const scrollIntoView = vi.spyOn(Element.prototype, "scrollIntoView");
      render(<Many />);
      const list = screen.getByRole("tablist");
      layout(list);

      // Tab t3 chiếm x=340..460 trong khi thanh tab chỉ tới x=400.
      await userEvent.click(screen.getByRole("tab", { name: "Tab t3" }));
      await waitFor(() => expect(list.scrollLeft).toBe(100));
      // Không dùng scrollIntoView (trình duyệt cũ sẽ cuộn cả trang).
      expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it("chọn tab bị khuất ở đầu: cuộn ngược lại", async () => {
      render(<Many />);
      const list = screen.getByRole("tablist");
      layout(list);
      list.scrollLeft = 300;

      // Với scrollLeft=300, tab t2 chiếm x=-80..40, nằm trước mép đầu x=100.
      await userEvent.click(screen.getByRole("tab", { name: "Tab t2" }));
      await waitFor(() => expect(list.scrollLeft).toBe(80));
    });

    it("tab đang chọn đã hiện trọn kèm phần lộ của tab kế bên thì không cuộn", async () => {
      render(<Many />);
      const list = screen.getByRole("tablist");
      layout(list);
      list.scrollLeft = 60;

      // Với scrollLeft=60, tab t2 chiếm x=160..280: còn cách hai mép (x=100 và x=400) hơn 40px.
      await userEvent.click(screen.getByRole("tab", { name: "Tab t2" }));
      await waitFor(() => expect(screen.getByRole("tab", { name: "Tab t2" })).toHaveAttribute("data-state", "active"));
      expect(list.scrollLeft).toBe(60);
    });

    it("đổi tab từ bên ngoài (có kiểm soát) cũng cuộn tab đó vào tầm nhìn", async () => {
      const { rerender } = render(<Many value="t1" />);
      const list = screen.getByRole("tablist");
      layout(list);
      rerender(<Many value="t5" />);
      // Tab t5 chiếm x=580..700: cần cuộn 700 + 40 - 400 = 340.
      await waitFor(() => expect(list.scrollLeft).toBe(340));
    });

    it("phím mũi tên vẫn chuyển tab trong thanh cuộn", async () => {
      render(<Many />);
      screen.getByRole("tab", { name: "Tab t1" }).focus();
      await userEvent.keyboard("{ArrowRight}{ArrowRight}");
      expect(screen.getByRole("tab", { name: "Tab t3" })).toHaveAttribute("aria-selected", "true");
      expect(screen.getByRole("tab", { name: "Tab t3" })).toHaveFocus();
      await userEvent.keyboard("{End}");
      expect(screen.getByRole("tab", { name: "Tab t5" })).toHaveAttribute("aria-selected", "true");
    });

    it("ref truyền vào TabsList vẫn nhận được phần tử", () => {
      const ref = { current: null as HTMLDivElement | null };
      render(
        <Tabs defaultValue="a">
          <TabsList ref={ref}>
            <TabsTrigger value="a">A</TabsTrigger>
          </TabsList>
        </Tabs>,
      );
      expect(ref.current).toBe(screen.getByRole("tablist"));
    });
  });

  it("gạch chân cùng hình với NavTabs: cách mép 12px, sát đáy 4px, dày 2px, màu chữ hiện tại", () => {
    render(
      <>
        <Example variant="content" />
        <nav aria-label="Trang con">
          <ul>
            <NavTab current href="/a">
              Trang A
            </NavTab>
          </ul>
        </nav>
      </>,
    );
    const tab = screen.getByRole("tab", { name: "Vai trò" });
    const link = screen.getByRole("link", { name: "Trang A" });
    for (const shape of ["after:absolute", "after:inset-x-3", "after:bottom-1", "after:pointer-events-none"]) {
      expect(tab).toHaveClass(shape);
      expect(link).toHaveClass(shape);
    }
    // Cùng độ dày và cùng màu; mỗi bên gắn vào trạng thái của mình (data-state / aria-current).
    for (const element of [tab, link]) {
      expect(element.className).toMatch(/after:border-b-2(\s|$)/);
      expect(element.className).toMatch(/after:border-current(\s|$)/);
    }
  });
});
