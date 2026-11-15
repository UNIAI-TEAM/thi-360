import type { ColumnDef } from "@tanstack/react-table";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Table, type TableProps } from "./table";

type Person = { id: string; name: string; age: number };

const people: Person[] = [
  { id: "a", name: "Cường", age: 30 },
  { id: "b", name: "An", age: 41 },
  { id: "c", name: "Bình", age: 25 },
];

const columns: ColumnDef<Person>[] = [
  { accessorKey: "name", header: "Họ tên" },
  { accessorKey: "age", header: "Tuổi" },
  { id: "actions", header: "Thao tác", enableSorting: false, cell: () => "…" },
];

function Harness(
  props: Partial<TableProps<Person>> & {
    withSorting?: boolean;
    withSelection?: boolean;
    initialSelection?: Record<string, boolean>;
  },
) {
  const { withSorting, withSelection, initialSelection, ...rest } = props;
  const [sortingState, setSortingState] = useState<{ id: string; desc: boolean }[]>([]);
  const [selectionState, setSelectionState] = useState<Record<string, boolean>>(initialSelection ?? {});
  return (
    <Table<Person>
      aria-label="Danh sách người"
      columns={columns}
      data={people}
      getRowId={(person) => person.id}
      empty={<p>Không có người nào</p>}
      sorting={withSorting ? { state: sortingState, onChange: setSortingState } : undefined}
      selection={
        withSelection
          ? {
              state: selectionState,
              onChange: setSelectionState,
              selectAllLabel: "Chọn tất cả",
              rowLabel: (index, person) => `Chọn dòng ${index + 1} ${person.name}`,
            }
          : undefined
      }
      {...rest}
    />
  );
}

function names() {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

describe("Table", () => {
  it("hiện hàng tiêu đề và mọi dòng theo thứ tự dữ liệu", () => {
    render(<Harness />);
    expect(screen.getByRole("table", { name: "Danh sách người" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Họ tên" })).toBeInTheDocument();
    expect(names()).toEqual(["Cường", "An", "Bình"]);
  });

  it("sắp xếp: bấm tiêu đề cột để tăng dần, bấm lần nữa để giảm dần, đặt aria-sort", async () => {
    render(<Harness withSorting />);
    await userEvent.click(screen.getByRole("button", { name: "Họ tên" }));
    expect(names()).toEqual(["An", "Bình", "Cường"]);
    expect(screen.getByRole("columnheader", { name: "Họ tên" })).toHaveAttribute("aria-sort", "ascending");

    await userEvent.click(screen.getByRole("button", { name: "Họ tên" }));
    expect(names()).toEqual(["Cường", "Bình", "An"]);
    expect(screen.getByRole("columnheader", { name: "Họ tên" })).toHaveAttribute("aria-sort", "descending");
  });

  it("cột không sắp xếp được thì tiêu đề không phải nút; bảng không bật sorting thì không cột nào là nút", () => {
    const { unmount } = render(<Harness withSorting />);
    expect(screen.queryByRole("button", { name: "Thao tác" })).toBeNull();
    unmount();
    render(<Harness />);
    expect(screen.queryByRole("button", { name: "Họ tên" })).toBeNull();
  });

  it("chọn dòng: tick từng dòng và ô chọn tất cả chuyển sang trạng thái một phần", async () => {
    render(<Harness withSelection />);
    const all = screen.getByRole("checkbox", { name: "Chọn tất cả" });
    expect(all).not.toBeChecked();

    await userEvent.click(screen.getByRole("checkbox", { name: /Chọn dòng 2/ }));
    expect(screen.getByRole("checkbox", { name: /Chọn dòng 2/ })).toBeChecked();
    expect(all).toBePartiallyChecked();
    expect(screen.getAllByRole("row")[2]).toHaveAttribute("data-state", "selected");

    await userEvent.click(all);
    expect(screen.getAllByRole("checkbox", { checked: true })).toHaveLength(4);
    await userEvent.click(all);
    expect(screen.queryAllByRole("checkbox", { checked: true })).toHaveLength(0);
  });

  it("chọn dòng với dòng đã chọn ở trang khác: ô chọn tất cả chỉ xét dòng đang hiện", async () => {
    render(<Harness withSelection initialSelection={{ "trang-khac": true, cu: false }} />);
    const all = screen.getByRole("checkbox", { name: "Chọn tất cả" });
    expect(all).not.toBeChecked();
    expect(all).not.toBePartiallyChecked();

    await userEvent.click(screen.getByRole("checkbox", { name: /Chọn dòng 2/ }));
    expect(all).toBePartiallyChecked();

    await userEvent.click(all);
    expect(screen.getAllByRole("checkbox", { checked: true })).toHaveLength(4);
    await userEvent.click(all);
    expect(screen.queryAllByRole("checkbox", { checked: true })).toHaveLength(0);
    expect(all).not.toBePartiallyChecked();
  });

  it("chọn dòng: số dòng đã chọn bằng số dòng đang hiện nhưng khác dòng thì không coi là chọn hết", () => {
    render(<Harness withSelection initialSelection={{ a: true, "trang-khac-1": true, "trang-khac-2": true }} />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBePartiallyChecked();
  });

  it("chọn dòng: ô chọn tất cả bị khóa khi đang tải, có lỗi hoặc không có dòng", () => {
    const { rerender } = render(<Harness withSelection loading />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBeDisabled();
    rerender(<Harness withSelection error={<span>Lỗi</span>} />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBeDisabled();
    rerender(<Harness withSelection data={[]} />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBeDisabled();
    rerender(<Harness withSelection />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBeEnabled();
  });

  it("đang tải: hiện khung xương thay dữ liệu và đánh dấu aria-busy", () => {
    render(<Harness loading loadingRows={3} />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("Cường")).toBeNull();
    expect(screen.getAllByRole("row")).toHaveLength(1 + 3);
  });

  it("đang tải lại (refreshing): giữ nguyên các dòng đang hiện, không hiện khung xương, đánh dấu aria-busy", () => {
    const { rerender } = render(<Harness refreshing />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
    expect(names()).toEqual(["Cường", "An", "Bình"]);
    expect(screen.getAllByRole("row")).toHaveLength(1 + 3);

    rerender(<Harness />);
    expect(screen.getByRole("table")).not.toHaveAttribute("aria-busy");
    expect(names()).toEqual(["Cường", "An", "Bình"]);
  });

  it("refreshing cùng lúc với loading (lần tải đầu): khung xương thắng", () => {
    render(<Harness loading refreshing loadingRows={2} />);
    expect(screen.queryByText("Cường")).toBeNull();
    expect(screen.getAllByRole("row")).toHaveLength(1 + 2);
  });

  it("rỗng: hiện nội dung empty trong một ô trải hết cột", () => {
    render(<Harness data={[]} />);
    expect(screen.getByText("Không có người nào")).toBeInTheDocument();
    expect(screen.getByText("Không có người nào").closest("td")).toHaveAttribute("colspan", "3");
  });

  it("lỗi: hiện thông báo trong vùng alert, không hiện dữ liệu cũ", () => {
    render(<Harness error={<span>Không tải được danh sách</span>} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Không tải được danh sách");
    expect(screen.queryByText("Cường")).toBeNull();
  });

  it("thử lại sau lỗi: đang tải thì hiện khung xương, không hiện lại thông báo lỗi cũ", () => {
    render(<Harness loading error={<span>Không tải được danh sách</span>} />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getAllByRole("row")).toHaveLength(1 + 5);
  });

  it("bảng không bật sorting/selection render ổn định: không tự render lại liên tục (vòng lặp đặt lại trang)", async () => {
    let cellRenders = 0;
    const countingColumns: ColumnDef<Person>[] = [
      {
        accessorKey: "name",
        header: "Họ tên",
        cell: ({ getValue }) => {
          cellRenders += 1;
          return String(getValue());
        },
      },
    ];
    function Stable() {
      const [tick, setTick] = useState(0);
      return (
        <div>
          <button type="button" onClick={() => setTick(tick + 1)}>
            render lại {tick}
          </button>
          <Table<Person>
            aria-label="Danh sách người"
            columns={countingColumns}
            data={people}
            getRowId={(person) => person.id}
            empty={<p>Không có người nào</p>}
          />
        </div>
      );
    }
    render(<Stable />);
    await userEvent.click(screen.getByRole("button", { name: /render lại/ }));
    const settled = cellRenders;
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(cellRenders).toBe(settled);
    expect(cellRenders).toBeLessThan(people.length * 6);
  });
});

describe("Table: vùng cuộn ngang dùng được bằng bàn phím", () => {
  /** Thay `ResizeObserver` của jsdom (không bao giờ báo) bằng bản gọi tay được. */
  function installResizeObserver() {
    const instances: { callback: ResizeObserverCallback; targets: Element[]; disconnected: boolean }[] = [];
    class FakeResizeObserver {
      private readonly record: (typeof instances)[number];
      constructor(callback: ResizeObserverCallback) {
        this.record = { callback, targets: [], disconnected: false };
        instances.push(this.record);
      }
      observe(target: Element) {
        this.record.targets.push(target);
      }
      unobserve() {}
      disconnect() {
        this.record.disconnected = true;
      }
    }
    vi.stubGlobal("ResizeObserver", FakeResizeObserver);
    return instances;
  }

  /** Đặt bề rộng nội dung và bề rộng khung rồi báo cho observer, như khi trình duyệt đo lại. */
  function resize(
    scroller: HTMLElement,
    instances: ReturnType<typeof installResizeObserver>,
    size: { scrollWidth: number; clientWidth: number },
  ) {
    Object.defineProperty(scroller, "scrollWidth", { configurable: true, value: size.scrollWidth });
    Object.defineProperty(scroller, "clientWidth", { configurable: true, value: size.clientWidth });
    act(() => {
      for (const { callback, disconnected } of instances) {
        if (!disconnected) callback([], {} as ResizeObserver);
      }
    });
  }

  function scrollerOf(table: HTMLElement): HTMLElement {
    return table.parentElement as HTMLElement;
  }

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("bảng vừa khung: khung bọc không là điểm dừng Tab, không là vùng có tên", () => {
    const instances = installResizeObserver();
    render(<Harness />);
    const scroller = scrollerOf(screen.getByRole("table", { name: "Danh sách người" }));
    resize(scroller, instances, { scrollWidth: 800, clientWidth: 800 });

    expect(scroller).not.toHaveAttribute("tabindex");
    expect(scroller).not.toHaveAttribute("role");
    expect(scroller).not.toHaveAttribute("aria-label");
    expect(screen.queryByRole("region")).toBeNull();
  });

  it("bảng tràn ngang: khung bọc thành vùng mang tên bảng, focus được và có vòng focus", () => {
    const instances = installResizeObserver();
    render(<Harness />);
    const table = screen.getByRole("table", { name: "Danh sách người" });
    const scroller = scrollerOf(table);
    resize(scroller, instances, { scrollWidth: 1200, clientWidth: 800 });

    expect(screen.getByRole("region", { name: "Danh sách người" })).toBe(scroller);
    expect(scroller).toHaveAttribute("tabindex", "0");
    expect(scroller).toHaveClass(
      "overflow-x-auto",
      "focus-visible:outline-3",
      "focus-visible:-outline-offset-3",
      "focus-visible:outline-primary-text",
    );
    // Bảng vẫn giữ tên của mình để còn tìm được theo tên bảng.
    expect(table).toHaveAttribute("aria-label", "Danh sách người");

    scroller.focus();
    expect(scroller).toHaveFocus();
  });

  it("theo dõi cả khung bọc lẫn bảng, và bỏ điểm dừng khi hết tràn", () => {
    const instances = installResizeObserver();
    const { unmount } = render(<Harness />);
    const table = screen.getByRole("table");
    const scroller = scrollerOf(table);
    const own = instances.find((instance) => instance.targets.includes(scroller));
    expect(own?.targets).toEqual([scroller, table]);

    resize(scroller, instances, { scrollWidth: 1200, clientWidth: 800 });
    expect(scroller).toHaveAttribute("tabindex", "0");

    resize(scroller, instances, { scrollWidth: 1200, clientWidth: 1200 });
    expect(scroller).not.toHaveAttribute("tabindex");
    expect(screen.queryByRole("region")).toBeNull();

    unmount();
    expect(own?.disconnected).toBe(true);
  });

  it("lệch 1px do làm tròn không tính là tràn", () => {
    const instances = installResizeObserver();
    render(<Harness />);
    const scroller = scrollerOf(screen.getByRole("table"));
    resize(scroller, instances, { scrollWidth: 801, clientWidth: 800 });
    expect(scroller).not.toHaveAttribute("tabindex");
  });

  it("tràn ngang không đổi hành vi tải: aria-busy vẫn ở bảng, refreshing giữ dòng, loading hiện khung xương", () => {
    const instances = installResizeObserver();
    const { rerender } = render(<Harness refreshing />);
    const table = screen.getByRole("table");
    const scroller = scrollerOf(table);
    resize(scroller, instances, { scrollWidth: 1200, clientWidth: 800 });

    expect(table).toHaveAttribute("aria-busy", "true");
    expect(scroller).not.toHaveAttribute("aria-busy");
    expect(names()).toEqual(["Cường", "An", "Bình"]);
    expect(table.querySelector("tbody")).toHaveClass("opacity-60");

    rerender(<Harness loading loadingRows={2} />);
    expect(screen.getByRole("region", { name: "Danh sách người" })).toBe(scroller);
    expect(table).toHaveAttribute("aria-busy", "true");
    expect(screen.getAllByRole("row")).toHaveLength(1 + 2);
    expect(table.querySelector("tbody")).not.toHaveClass("opacity-60");
  });

  it("môi trường không có ResizeObserver: bảng vẫn hiện, khung bọc không là điểm dừng", () => {
    vi.stubGlobal("ResizeObserver", undefined);
    render(<Harness />);
    const scroller = scrollerOf(screen.getByRole("table", { name: "Danh sách người" }));
    expect(scroller).not.toHaveAttribute("tabindex");
    expect(names()).toEqual(["Cường", "An", "Bình"]);
  });
});
