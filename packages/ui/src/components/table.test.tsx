import type { ColumnDef } from "@tanstack/react-table";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
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

function Harness(props: Partial<TableProps<Person>> & { withSorting?: boolean; withSelection?: boolean }) {
  const { withSorting, withSelection, ...rest } = props;
  const [sortingState, setSortingState] = useState<{ id: string; desc: boolean }[]>([]);
  const [selectionState, setSelectionState] = useState({});
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
              rowLabel: (index) => `Chọn dòng ${index + 1}`,
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

    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn dòng 2" }));
    expect(screen.getByRole("checkbox", { name: "Chọn dòng 2" })).toBeChecked();
    expect(all).toBePartiallyChecked();
    expect(screen.getAllByRole("row")[2]).toHaveAttribute("data-state", "selected");

    await userEvent.click(all);
    expect(screen.getAllByRole("checkbox", { checked: true })).toHaveLength(4);
    await userEvent.click(all);
    expect(screen.queryAllByRole("checkbox", { checked: true })).toHaveLength(0);
  });

  it("đang tải: hiện khung xương thay dữ liệu và đánh dấu aria-busy", () => {
    render(<Harness loading loadingRows={3} />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("Cường")).toBeNull();
    expect(screen.getAllByRole("row")).toHaveLength(1 + 3);
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
});
