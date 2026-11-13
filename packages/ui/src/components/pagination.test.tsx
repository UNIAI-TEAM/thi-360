import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Pagination, pageItems, type PaginationLabels } from "./pagination";

const labels: PaginationLabels = {
  nav: "Phân trang",
  previous: "Trang trước",
  next: "Trang sau",
  rowsPerPage: "Số dòng mỗi trang",
  page: (page) => `Trang ${page}`,
  range: ({ from, to, total }) => `${from}–${to} / ${total}`,
};

describe("Pagination", () => {
  it("hiện khoảng bản ghi và đánh dấu trang hiện tại", () => {
    render(<Pagination page={2} pageSize={20} total={95} onPageChange={() => {}} labels={labels} />);
    expect(screen.getByText("21–40 / 95")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Trang 2" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Trang 1" })).not.toHaveAttribute("aria-current");
  });

  it("bấm Trang sau, Trang trước và số trang thì báo trang mới", async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={2} pageSize={20} total={95} onPageChange={onPageChange} labels={labels} />);
    await userEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await userEvent.click(screen.getByRole("button", { name: "Trang trước" }));
    await userEvent.click(screen.getByRole("button", { name: "Trang 5" }));
    expect(onPageChange.mock.calls.map(([p]) => p)).toEqual([3, 1, 5]);
  });

  it("trang đầu khóa Trang trước, trang cuối khóa Trang sau", () => {
    const { rerender } = render(
      <Pagination page={1} pageSize={20} total={95} onPageChange={() => {}} labels={labels} />,
    );
    expect(screen.getByRole("button", { name: "Trang trước" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Trang sau" })).toBeEnabled();
    rerender(<Pagination page={5} pageSize={20} total={95} onPageChange={() => {}} labels={labels} />);
    expect(screen.getByRole("button", { name: "Trang sau" })).toBeDisabled();
  });

  it("không có dữ liệu: 0–0, chỉ một trang, cả hai nút khóa", () => {
    render(<Pagination page={1} pageSize={20} total={0} onPageChange={() => {}} labels={labels} />);
    expect(screen.getByText("0–0 / 0")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Trang trước" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Trang sau" })).toBeDisabled();
  });

  it("đổi số dòng mỗi trang", async () => {
    const onPageSizeChange = vi.fn();
    render(
      <Pagination
        page={1}
        pageSize={20}
        total={95}
        onPageChange={() => {}}
        pageSizeOptions={[10, 20, 50]}
        onPageSizeChange={onPageSizeChange}
        labels={labels}
      />,
    );
    await userEvent.click(screen.getByRole("combobox", { name: "Số dòng mỗi trang" }));
    await userEvent.click(await screen.findByRole("option", { name: "50" }));
    expect(onPageSizeChange).toHaveBeenCalledWith(50);
  });
});

describe("pageItems", () => {
  it("ít trang thì liệt kê hết", () => {
    expect(pageItems(2, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it("nhiều trang: có dấu … giữa trang đầu, cuối và quanh trang hiện tại", () => {
    expect(pageItems(10, 20)).toEqual([1, null, 9, 10, 11, null, 20]);
    expect(pageItems(1, 20)).toEqual([1, 2, 3, 4, null, 20]);
    expect(pageItems(20, 20)).toEqual([1, null, 17, 18, 19, 20]);
  });
});
