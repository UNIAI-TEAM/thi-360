import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
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
});
