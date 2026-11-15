import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColumnDef } from "@tanstack/react-table";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Table, useCardMode, type TableCards, type TableProps } from "./table";

type Person = { id: string; name: string; age: number };

const people: Person[] = [
  { id: "a", name: "Cường", age: 30 },
  { id: "b", name: "An", age: 41 },
];

const columns: ColumnDef<Person>[] = [
  { accessorKey: "name", header: "Họ tên" },
  { accessorKey: "age", header: () => <span>Tuổi</span>, meta: { label: "Tuổi" } },
  { id: "actions", header: "Thao tác", enableSorting: false, cell: () => "…" },
];

const cards: TableCards<Person> = {
  render: (person) => (
    <div>
      <a href={`/people/${person.id}`} className="after:absolute after:inset-0">
        {person.name}
      </a>
      <p>{person.age} tuổi</p>
    </div>
  ),
  sortLabels: { label: "Sắp xếp theo", ascending: "tăng dần", descending: "giảm dần" },
};

/** Đặt bề rộng khung đo (phần tử bọc ngoài cùng của `Table`) và báo cho các ResizeObserver. */
function installWidth(initial: number) {
  let width = initial;
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
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    () => ({ width, height: 0, top: 0, left: 0, right: width, bottom: 0, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect,
  );
  return (next: number) => {
    width = next;
    act(() => {
      for (const callback of callbacks) callback([], {} as ResizeObserver);
    });
  };
}

function Harness(props: Partial<TableProps<Person>> & { withSelection?: boolean; withSorting?: boolean }) {
  const { withSelection, withSorting, ...rest } = props;
  const [sorting, setSorting] = useState<{ id: string; desc: boolean }[]>([]);
  const [selection, setSelection] = useState<Record<string, boolean>>({});
  return (
    <Table<Person>
      aria-label="Danh sách người"
      columns={columns}
      data={people}
      getRowId={(person) => person.id}
      empty={<p>Không có người nào</p>}
      cards={cards}
      sorting={withSorting ? { state: sorting, onChange: setSorting } : undefined}
      selection={
        withSelection
          ? {
              state: selection,
              onChange: setSelection,
              selectAllLabel: "Chọn tất cả",
              rowLabel: (_index, person) => `Chọn ${person.name}`,
            }
          : undefined
      }
      {...rest}
    />
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("Table: danh sách thẻ khi khung hẹp", () => {
  it("chưa đo được bề rộng (máy chủ, jsdom) thì vẫn là bảng", () => {
    render(<Harness />);
    expect(screen.getByRole("table", { name: "Danh sách người" })).toBeInTheDocument();
    expect(screen.queryByRole("list")).toBeNull();
  });

  it("khung hẹp hơn 640px: một danh sách có tên, mỗi dòng một thẻ do render vẽ; nới khung thì về bảng", () => {
    const setWidth = installWidth(358);
    render(<Harness />);
    const list = screen.getByRole("list", { name: "Danh sách người" });
    expect(screen.queryByRole("table")).toBeNull();
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(within(items[0]!).getByRole("link", { name: "Cường" })).toHaveAttribute("href", "/people/a");
    expect(items[0]).toHaveClass("relative");
    expect(within(items[1]!).getByText("41 tuổi")).toBeInTheDocument();

    setWidth(900);
    expect(screen.getByRole("table", { name: "Danh sách người" })).toBeInTheDocument();
    expect(screen.queryByRole("list")).toBeNull();
  });

  it("ngưỡng đổi được bằng breakpoint", () => {
    installWidth(700);
    render(<Harness cards={{ ...cards, breakpoint: 800 }} />);
    expect(screen.getByRole("list", { name: "Danh sách người" })).toBeInTheDocument();
  });

  it("chọn dòng: ô chọn ở đầu thẻ, ô Chọn tất cả có nhãn nhìn thấy được ở đầu danh sách", async () => {
    installWidth(358);
    render(<Harness withSelection />);
    const selectAll = screen.getByRole("checkbox", { name: "Chọn tất cả" });
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn An" }));
    expect(selectAll).toHaveAttribute("data-state", "indeterminate");
    const items = screen.getAllByRole("listitem");
    expect(items[1]).toHaveAttribute("data-state", "selected");
    // Ô chọn nằm trên vùng bấm của liên kết phủ cả thẻ.
    expect(screen.getByRole("checkbox", { name: "Chọn An" }).parentElement).toHaveClass("relative", "z-1");
    await userEvent.click(selectAll);
    expect(screen.getByRole("checkbox", { name: "Chọn Cường" })).toHaveAttribute("data-state", "checked");
  });

  it("sắp xếp bằng ô chọn: mỗi cột sắp xếp được có tăng dần và giảm dần, tên cột lấy từ header hoặc meta.label", async () => {
    installWidth(358);
    render(<Harness withSorting />);
    const sort = screen.getByRole("combobox", { name: "Sắp xếp theo" });
    await userEvent.click(sort);
    const options = (await screen.findAllByRole("option")).map((option) => option.textContent);
    expect(options).toEqual(["Họ tên · tăng dần", "Họ tên · giảm dần", "Tuổi · tăng dần", "Tuổi · giảm dần"]);
    await userEvent.click(screen.getByRole("option", { name: "Tuổi · giảm dần" }));
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual(["An", "Cường"]);
    expect(screen.getByRole("combobox", { name: "Sắp xếp theo" })).toHaveTextContent("Tuổi · giảm dần");
  });

  it("đang tải: thẻ khung xương theo loadingRows; rỗng và lỗi hiện trong danh sách", () => {
    installWidth(358);
    const { rerender } = render(<Harness loading loadingRows={3} />);
    const list = screen.getByRole("list", { name: "Danh sách người" });
    expect(list).toHaveAttribute("aria-busy", "true");
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);

    rerender(<Harness data={[]} />);
    expect(screen.getByText("Không có người nào")).toBeInTheDocument();

    rerender(<Harness error={<p>Lỗi tải</p>} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Lỗi tải");
  });

  it("đang tải lại: giữ thẻ cũ, làm mờ và báo aria-busy", () => {
    installWidth(358);
    render(<Harness refreshing />);
    const list = screen.getByRole("list", { name: "Danh sách người" });
    expect(list).toHaveAttribute("aria-busy", "true");
    expect(list).toHaveClass("opacity-60");
    expect(within(list).getAllByRole("listitem")).toHaveLength(2);
  });
});

/** Bảng tự viết dùng chung cách đo với `Table`: phần tử đo luôn có mặt, chỉ phần bên trong đổi dạng. */
function HandWritten({ enabled = true, breakpoint = 416 }: { enabled?: boolean; breakpoint?: number }) {
  const [ref, cardMode] = useCardMode<HTMLElement>(enabled, breakpoint);
  return (
    <section ref={ref} aria-label="Khung">
      {cardMode ? <ul role="list" aria-label="Thẻ" /> : <table aria-label="Bảng" />}
    </section>
  );
}

describe("useCardMode: hook đo khung cho bảng tự viết", () => {
  it("chưa đo được bề rộng thì là bảng", () => {
    render(<HandWritten />);
    expect(screen.getByRole("table", { name: "Bảng" })).toBeInTheDocument();
  });

  it("hẹp hơn ngưỡng là thẻ, bằng hoặc rộng hơn ngưỡng là bảng, đổi theo ResizeObserver", () => {
    const setWidth = installWidth(415);
    render(<HandWritten />);
    expect(screen.getByRole("list", { name: "Thẻ" })).toBeInTheDocument();
    setWidth(416);
    expect(screen.getByRole("table", { name: "Bảng" })).toBeInTheDocument();
    setWidth(300);
    expect(screen.getByRole("list", { name: "Thẻ" })).toBeInTheDocument();
  });

  it("tắt (enabled = false) thì luôn là bảng", () => {
    installWidth(200);
    render(<HandWritten enabled={false} />);
    expect(screen.getByRole("table", { name: "Bảng" })).toBeInTheDocument();
  });
});
