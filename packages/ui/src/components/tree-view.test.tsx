import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { TreeView, type TreeViewItem } from "./tree-view";

const SAMPLE_ITEMS: TreeViewItem[] = [
  {
    id: "n-1",
    parentId: null,
    depth: 1,
    code: "0D1",
    name: "Mệnh đề. Tập hợp",
    hasChildren: true,
    leafCount: 4,
    refsSummary: "Toán · Lớp 10",
  },
  {
    id: "n-1-1",
    parentId: "n-1",
    depth: 2,
    code: "0D1-1",
    name: "Mệnh đề",
    hasChildren: true,
    leafCount: 2,
  },
  {
    id: "n-1-1-1",
    parentId: "n-1-1",
    depth: 3,
    code: "0D1-1-1",
    name: "Mệnh đề chứa biến",
    hasChildren: false,
    statusLabel: "Ngừng theo cấp trên",
    statusTone: "warning",
    inactive: true,
  },
  {
    id: "n-2",
    parentId: null,
    depth: 1,
    code: "0D2",
    name: "Bất phương trình",
    hasChildren: false,
    leafCount: 0,
    statusLabel: "Ngừng sử dụng",
    statusTone: "neutral",
    inactive: true,
  },
];

function TreeHarness({
  initialItems = SAMPLE_ITEMS,
  initialExpanded = [],
  onLazyLoad,
}: {
  initialItems?: TreeViewItem[];
  initialExpanded?: string[];
  onLazyLoad?: (item: TreeViewItem) => TreeViewItem[];
}) {
  const [items, setItems] = useState<TreeViewItem[]>(initialItems);
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(initialExpanded));
  const [selectedId, setSelectedId] = useState<string | null>(null);

  function handleToggle(item: TreeViewItem) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(item.id)) {
        next.delete(item.id);
      } else {
        next.add(item.id);
        if (onLazyLoad && !items.some((i) => i.parentId === item.id)) {
          const children = onLazyLoad(item);
          setItems((curr) => [...curr, ...children]);
        }
      }
      return next;
    });
  }

  return (
    <TreeView
      aria-label="Cây thử nghiệm"
      items={items}
      expandedIds={expanded}
      selectedId={selectedId}
      onToggleExpand={handleToggle}
      onSelect={(item) => setSelectedId(item.id)}
      labels={{
        expandNode: (name) => `Mở ${name}`,
        collapseNode: (name) => `Thu gọn ${name}`,
        loadingChildren: "Đang tải",
      }}
    />
  );
}

describe("TreeView", () => {
  it("render đúng role=tree, treeitem, aria-level, aria-expanded, aria-selected và chỉ hiện chip khi khác Hoạt động", () => {
    render(<TreeHarness initialExpanded={["n-1", "n-1-1"]} />);

    const tree = screen.getByRole("tree", { name: "Cây thử nghiệm" });
    const items = within(tree).getAllByRole("treeitem");
    expect(items).toHaveLength(4);

    expect(items[0]).toHaveAttribute("aria-level", "1");
    expect(items[0]).toHaveAttribute("aria-expanded", "true");
    expect(items[0]).toHaveAttribute("aria-setsize", "2");
    expect(items[0]).toHaveAttribute("aria-posinset", "1");
    expect(items[0]).toHaveAttribute("aria-selected", "false");
    expect(within(items[0]!).getByText("0D1")).toBeInTheDocument();
    expect(within(items[0]!).getByText("Toán · Lớp 10")).toBeInTheDocument();
    expect(within(items[0]!).getByText("4")).toBeInTheDocument();
    expect(within(items[0]!).queryByText("Hoạt động")).not.toBeInTheDocument();

    expect(items[2]).toHaveAttribute("aria-level", "3");
    expect(items[2]).not.toHaveAttribute("aria-expanded");
    expect(within(items[2]!).getByText("Ngừng theo cấp trên")).toBeInTheDocument();

    expect(items[3]).toHaveAttribute("aria-level", "1");
    expect(items[3]).toHaveAttribute("aria-setsize", "2");
    expect(items[3]).toHaveAttribute("aria-posinset", "2");
    expect(items[3]).not.toHaveAttribute("aria-expanded");
    expect(within(items[3]!).getByText("Ngừng sử dụng")).toBeInTheDocument();
  });

  it("nạp con lười theo parentId khi bấm nút mở rộng", async () => {
    const lazyMock = vi.fn((item: TreeViewItem) => [
      {
        id: `${item.id}-child`,
        parentId: item.id,
        depth: item.depth + 1,
        code: "0D1-1",
        name: "Bài con mới nạp",
        hasChildren: false,
      },
    ]);

    render(
      <TreeHarness
        initialItems={[SAMPLE_ITEMS[0]!, SAMPLE_ITEMS[3]!]}
        onLazyLoad={lazyMock}
      />,
    );

    expect(screen.queryByText("Bài con mới nạp")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Mở Mệnh đề. Tập hợp" }));

    expect(lazyMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Bài con mới nạp")).toBeInTheDocument();
  });

  it("hỗ trợ điều hướng bàn phím ArrowDown, ArrowUp, ArrowRight, ArrowLeft, Home, End và Enter", async () => {
    render(<TreeHarness />);

    const root1 = screen.getAllByRole("treeitem")[0]!;
    root1.focus();
    expect(root1).toHaveFocus();

    // ArrowRight mở root1
    await userEvent.keyboard("{ArrowRight}");
    expect(root1).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Mệnh đề")).toBeInTheDocument();

    // ArrowRight lần nữa chuyển focus vào con đầu tiên (n-1-1)
    await userEvent.keyboard("{ArrowRight}");
    const child1 = screen.getAllByRole("treeitem")[1]!;
    expect(child1).toHaveFocus();

    // Enter chọn item đang focus
    await userEvent.keyboard("{Enter}");
    expect(child1).toHaveAttribute("aria-selected", "true");

    // End nhảy tới item cuối cùng (0D2)
    await userEvent.keyboard("{End}");
    const last = screen.getAllByRole("treeitem").at(-1)!;
    expect(last).toHaveFocus();

    // Home nhảy về item đầu tiên (0D1)
    await userEvent.keyboard("{Home}");
    expect(root1).toHaveFocus();

    // ArrowDown xuống child1, rồi ArrowLeft thu gọn child1 hoặc quay về cha
    await userEvent.keyboard("{ArrowDown}");
    expect(child1).toHaveFocus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(root1).toHaveFocus();

    // ArrowLeft trên root1 đang mở sẽ thu gọn root1
    await userEvent.keyboard("{ArrowLeft}");
    expect(root1).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Mệnh đề")).not.toBeInTheDocument();

    // ArrowUp / ArrowDown di chuyển giữa các nút gốc
    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getAllByRole("treeitem")[1]).toHaveFocus();
    await userEvent.keyboard("{ArrowUp}");
    expect(root1).toHaveFocus();
  });

  it("không chặn Enter/Space khi focus đang ở nút thao tác bên trong renderTrailing", async () => {
    const onSelect = vi.fn();
    const onTrailingClick = vi.fn();

    render(
      <TreeView
        aria-label="Cây có nút thao tác"
        items={SAMPLE_ITEMS}
        expandedIds={new Set()}
        selectedId={null}
        onToggleExpand={vi.fn()}
        onSelect={onSelect}
        labels={{
          expandNode: (name) => `Mở ${name}`,
          collapseNode: (name) => `Thu gọn ${name}`,
        }}
        renderTrailing={(item) => (
          <button type="button" onClick={() => onTrailingClick(item.id)}>
            Sửa {item.code}
          </button>
        )}
      />,
    );

    const trailingBtn = screen.getByRole("button", { name: "Sửa 0D1" });
    trailingBtn.focus();
    expect(trailingBtn).toHaveFocus();

    await userEvent.keyboard("{Enter}");
    expect(onTrailingClick).toHaveBeenCalledTimes(1);
    expect(onTrailingClick).toHaveBeenCalledWith("n-1");
    expect(onSelect).not.toHaveBeenCalled();

    await userEvent.keyboard(" ");
    expect(onTrailingClick).toHaveBeenCalledTimes(2);
    expect(onSelect).not.toHaveBeenCalled();
  });
});
