import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmptyState } from "./empty-state";

describe("EmptyState", () => {
  it("hiện tiêu đề, mô tả và nút hành động", () => {
    render(
      <EmptyState
        icon={<svg data-testid="icon" />}
        title="Chưa có lớp học"
        description="Tạo lớp đầu tiên để bắt đầu"
        action={<button>Tạo lớp</button>}
      />,
    );
    expect(screen.getByRole("heading", { name: "Chưa có lớp học" })).toBeInTheDocument();
    expect(screen.getByText("Tạo lớp đầu tiên để bắt đầu")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo lớp" })).toBeInTheDocument();
    expect(screen.getByTestId("icon").closest("[aria-hidden]")).not.toBeNull();
  });

  it("chỉ tiêu đề: không dựng mô tả hay nút", () => {
    render(<EmptyState title="Không có dữ liệu" />);
    expect(screen.getByRole("heading", { name: "Không có dữ liệu" })).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
