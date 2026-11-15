import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
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
