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

  it("aria-sort chỉ có ở cột đang sắp xếp: cột chưa sắp xếp và cột không sắp xếp được đều không mang thuộc tính", async () => {
    render(<Harness withSorting />);
    for (const header of screen.getAllByRole("columnheader")) {
      expect(header).not.toHaveAttribute("aria-sort");
    }

    await userEvent.click(screen.getByRole("button", { name: "Họ tên" }));
    expect(screen.getByRole("columnheader", { name: "Họ tên" })).toHaveAttribute("aria-sort", "ascending");
    expect(screen.getByRole("columnheader", { name: "Tuổi" })).not.toHaveAttribute("aria-sort");
    expect(screen.getByRole("columnheader", { name: "Thao tác" })).not.toHaveAttribute("aria-sort");
    expect(document.querySelectorAll("[aria-sort]")).toHaveLength(1);

    // Bấm lần thứ ba bỏ sắp xếp: thuộc tính được gỡ, không quay về "none".
    await userEvent.click(screen.getByRole("button", { name: "Họ tên" }));
    await userEvent.click(screen.getByRole("button", { name: "Họ tên" }));
    expect(document.querySelectorAll("[aria-sort]")).toHaveLength(0);
  });

  it("nút sắp xếp có vùng bấm phủ hết ô tiêu đề và vẫn có vòng focus", () => {
    render(<Harness withSorting />);
    const button = screen.getByRole("button", { name: "Họ tên" });
    expect(button).toHaveClass("after:absolute", "after:inset-0", "focus-visible:ring-3");
    expect(button.closest("th")).toHaveClass("relative");
    // Cột không sắp xếp được không cần mốc định vị.
    expect(screen.getByRole("columnheader", { name: "Thao tác" })).not.toHaveClass("relative");
  });

  it("cột ghim sắp xếp được vẫn là sticky (không bị relative ghi đè)", () => {
    render(<Harness withSorting stickyColumns={{ start: true }} />);
    const header = screen.getByRole("columnheader", { name: "Họ tên" });
    expect(header).toHaveClass("sticky");
    expect(header).not.toHaveClass("relative");
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
    // Vùng alert nằm trong ô; ô vẫn là ô của bảng, trải hết các cột.
    const cell = screen.getByRole("alert").parentElement as HTMLElement;
    expect(cell.tagName).toBe("TD");
    expect(cell).not.toHaveAttribute("role");
    expect(cell).toHaveAttribute("colspan", "3");
    expect(screen.getAllByRole("cell")).toContain(cell);
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

  it("bảng rỗng rộng hơn khung: thông báo rỗng dính mép đầu và rộng đúng phần nhìn thấy của khung", () => {
    const instances = installResizeObserver();
    render(
      <Table<Person>
        aria-label="Danh sách người"
        columns={columns}
        data={[]}
        getRowId={(person) => person.id}
        empty={<p>Không có người nào</p>}
      />,
    );
    const scroller = scrollerOf(screen.getByRole("table", { name: "Danh sách người" }));
    resize(scroller, instances, { scrollWidth: 900, clientWidth: 328 });

    expect(scroller.style.getPropertyValue("--table-frame-width")).toBe("328px");
    const wrapper = screen.getByText("Không có người nào").parentElement!;
    expect(wrapper).toHaveClass("sticky", "start-0", "w-[var(--table-frame-width,100%)]");
    expect(wrapper.closest("td")).toHaveClass("p-0");
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

describe("Table: ghim cột (stickyColumns)", () => {
  function headers() {
    return screen.getAllByRole("columnheader");
  }
  function cellsOfRow(rowIndex: number) {
    return within(screen.getAllByRole("row")[rowIndex] as HTMLElement).getAllByRole("cell");
  }

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("không bật: không ô nào ghim, khung cuộn không thành vùng truy vấn trạng thái cuộn", () => {
    render(<Harness withSelection />);
    for (const cell of [...headers(), ...cellsOfRow(1)]) {
      expect(cell).not.toHaveClass("sticky");
    }
    const scroller = screen.getByRole("table").parentElement as HTMLElement;
    expect(scroller).not.toHaveClass("[container-type:scroll-state]");
    expect(scroller).not.toHaveClass("isolate");
    expect(screen.getAllByRole("row")[1]).not.toHaveClass("bg-surface");
  });

  it("start: ghim cột dữ liệu đầu tiên vào mép đầu, ở cả tiêu đề lẫn thân bảng; cột khác không ghim", () => {
    render(<Harness stickyColumns={{ start: true }} />);
    expect(headers()[0]).toHaveClass("sticky", "z-1", "start-0", "bg-table-head");
    expect(headers()[1]).not.toHaveClass("sticky");
    expect(headers()[2]).not.toHaveClass("sticky");

    const cells = cellsOfRow(1);
    expect(cells[0]).toHaveClass("sticky", "z-1", "start-0", "bg-inherit");
    expect(cells[1]).not.toHaveClass("sticky");
    expect(cells[2]).not.toHaveClass("sticky");
  });

  it("start cùng cột chọn dòng: ghim cả hai, cột dữ liệu lệch đúng bằng bề rộng cố định của cột chọn dòng", () => {
    render(<Harness withSelection stickyColumns={{ start: true }} />);
    expect(headers()[0]).toHaveClass("sticky", "start-0", "w-13", "min-w-13", "max-w-13");
    expect(headers()[1]).toHaveClass("sticky", "start-13");
    expect(headers()[1]).not.toHaveClass("start-0");

    const cells = cellsOfRow(1);
    expect(cells[0]).toHaveClass("sticky", "start-0", "min-w-13", "max-w-13", "bg-inherit");
    expect(cells[1]).toHaveClass("sticky", "start-13", "bg-inherit");
    // Vạch kẻ chỉ ở mép trong của nhóm cột ghim, tức là ở cột dữ liệu, không ở cột chọn dòng.
    expect(cells[0]?.className).not.toContain("after:");
    expect(cells[1]).toHaveClass("after:end-0");
  });

  it("end: ghim cột cuối vào mép cuối", () => {
    render(<Harness withSelection stickyColumns={{ end: true }} />);
    expect(headers()[3]).toHaveClass("sticky", "z-1", "end-0", "bg-table-head");
    expect(cellsOfRow(1)[3]).toHaveClass("sticky", "z-1", "end-0", "bg-inherit");
    // Chỉ bật `end` thì cột chọn dòng và cột dữ liệu đầu không ghim.
    expect(headers()[0]).not.toHaveClass("sticky");
    expect(cellsOfRow(1)[1]).not.toHaveClass("sticky");
  });

  it("nền ô ghim đi theo dòng: dòng có nền đặc cùng các lớp hover/đang chọn, ô ghim thừa kế nền của dòng", async () => {
    render(<Harness withSelection stickyColumns={{ start: true, end: true }} />);
    const row = screen.getAllByRole("row")[2] as HTMLElement;
    expect(row).toHaveClass("bg-surface", "hover:bg-subtle", "data-[state=selected]:bg-primary-50");

    await userEvent.click(screen.getByRole("checkbox", { name: /Chọn dòng 2/ }));
    expect(row).toHaveAttribute("data-state", "selected");
    for (const index of [0, 1, 3]) {
      expect(cellsOfRow(2)[index]).toHaveClass("bg-inherit");
      // Ô ghim không tự đặt màu nền riêng theo trạng thái: chỉ có một nguồn là dòng.
      expect(cellsOfRow(2)[index]?.className).not.toMatch(/bg-(surface|subtle|primary-50)/);
    }
  });

  it("vạch kẻ mép cột ghim chỉ có khi còn cuộn được về phía đó; trình duyệt cũ dựa vào data-overflows của khung", () => {
    render(<Harness stickyColumns={{ start: true, end: true }} />);
    const start = cellsOfRow(1)[0] as HTMLElement;
    const end = cellsOfRow(1)[2] as HTMLElement;
    expect(start).toHaveClass(
      "after:absolute",
      "after:end-0",
      "[@container_scroll-state(scrollable:inline-start)]:after:border-e",
      "not-supports-[container-type:scroll-state]:group-data-[overflows]/table:after:border-e",
    );
    expect(end).toHaveClass(
      "after:absolute",
      "after:start-0",
      "[@container_scroll-state(scrollable:inline-end)]:after:border-s",
      "not-supports-[container-type:scroll-state]:group-data-[overflows]/table:after:border-s",
    );
    // Không có viền vô điều kiện: bảng vừa khung thì không hiện vạch nào.
    expect(start).not.toHaveClass("after:border-e");
    expect(end).not.toHaveClass("after:border-s");

    const scroller = screen.getByRole("table").parentElement as HTMLElement;
    expect(scroller).toHaveClass("group/table", "isolate", "[container-type:scroll-state]", "overflow-x-auto");
  });

  /** `ResizeObserver` gọi tay được, như ở nhóm test vùng cuộn phía trên. */
  function installResizeObserver() {
    const callbacks: ResizeObserverCallback[] = [];
    vi.stubGlobal(
      "ResizeObserver",
      class {
        constructor(callback: ResizeObserverCallback) {
          callbacks.push(callback);
        }
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    return callbacks;
  }

  it("chừa scroll-padding bằng bề rộng các cột ghim, để phần tử nhận focus không dừng dưới cột ghim", () => {
    const callbacks = installResizeObserver();
    const { rerender } = render(<Harness withSelection stickyColumns={{ start: true, end: true }} />);
    const scroller = screen.getByRole("table").parentElement as HTMLElement;
    expect(headers().map((header) => header.getAttribute("data-pinned"))).toEqual(["start", "start", null, "end"]);

    [52, 180, 120, 88].forEach((width, index) => {
      Object.defineProperty(headers()[index], "offsetWidth", { configurable: true, value: width });
    });
    act(() => callbacks.forEach((callback) => callback([], {} as ResizeObserver)));
    expect(scroller.style.getPropertyValue("scroll-padding-inline-start")).toBe("232px");
    expect(scroller.style.getPropertyValue("scroll-padding-inline-end")).toBe("88px");

    rerender(<Harness withSelection />);
    act(() => callbacks.forEach((callback) => callback([], {} as ResizeObserver)));
    expect(scroller.style.getPropertyValue("scroll-padding-inline-start")).toBe("");
    expect(scroller.style.getPropertyValue("scroll-padding-inline-end")).toBe("");
  });

  it("khung báo data-overflows chỉ khi bảng tràn ngang, và vẫn là vùng cuộn dùng được bằng bàn phím", () => {
    const callbacks = installResizeObserver();
    render(<Harness stickyColumns={{ start: true, end: true }} />);
    const scroller = screen.getByRole("table").parentElement as HTMLElement;
    expect(scroller).not.toHaveAttribute("data-overflows");

    Object.defineProperty(scroller, "scrollWidth", { configurable: true, value: 1105 });
    Object.defineProperty(scroller, "clientWidth", { configurable: true, value: 800 });
    act(() => callbacks.forEach((callback) => callback([], {} as ResizeObserver)));

    expect(scroller).toHaveAttribute("data-overflows", "true");
    expect(screen.getByRole("region", { name: "Danh sách người" })).toBe(scroller);
    expect(scroller).toHaveAttribute("tabindex", "0");
    expect(scroller).toHaveClass("focus-visible:outline-3");
  });

  it("đang tải: ô khung xương ghim cùng cột với tiêu đề, dòng có nền đặc", () => {
    render(<Harness withSelection loading loadingRows={2} stickyColumns={{ start: true, end: true }} />);
    const cells = cellsOfRow(1);
    expect(cells[0]).toHaveClass("sticky", "start-0", "bg-inherit");
    expect(cells[1]).toHaveClass("sticky", "start-13", "bg-inherit");
    expect(cells[2]).not.toHaveClass("sticky");
    expect(cells[3]).toHaveClass("sticky", "end-0", "bg-inherit");
    expect(screen.getAllByRole("row")[1]).toHaveClass("bg-surface");
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
  });

  it("đang tải lại, rỗng và lỗi vẫn như cũ khi bật ghim cột", () => {
    const { rerender } = render(<Harness refreshing stickyColumns={{ start: true, end: true }} />);
    expect(screen.getByRole("table").querySelector("tbody")).toHaveClass("opacity-60");
    expect(names()).toEqual(["Cường", "An", "Bình"]);

    rerender(<Harness data={[]} stickyColumns={{ start: true, end: true }} />);
    expect(screen.getByText("Không có người nào").closest("td")).toHaveAttribute("colspan", "3");
    expect(screen.getByText("Không có người nào").closest("td")).not.toHaveClass("sticky");
    expect(headers()[0]).toHaveClass("sticky");

    rerender(<Harness error={<span>Không tải được danh sách</span>} stickyColumns={{ start: true, end: true }} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Không tải được danh sách");
  });

  it("bảng chỉ có một cột dữ liệu: end không ghim đè lên cột start", () => {
    const single: ColumnDef<Person>[] = [{ accessorKey: "name", header: "Họ tên" }];
    render(<Harness columns={single} stickyColumns={{ start: true, end: true }} />);
    expect(headers()[0]).toHaveClass("sticky", "start-0");
    expect(headers()[0]).not.toHaveClass("end-0");
  });

  it("đường kẻ ngang nằm ở ô (bảng border-separate) để đi theo ô ghim; dòng cuối không có đường kẻ", () => {
    render(<Harness stickyColumns={{ start: true }} />);
    expect(screen.getByRole("table")).toHaveClass("border-separate", "border-spacing-0");
    expect(headers()[0]).toHaveClass("border-b", "border-border");
    expect(cellsOfRow(1)[0]).toHaveClass("border-b", "border-border", "group-last/row:border-b-0");
    expect(screen.getAllByRole("row")[1]).toHaveClass("group/row");
  });
});
