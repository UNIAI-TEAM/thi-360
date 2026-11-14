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

  it("tiêu đề mặc định cấp 2: đứng ngay dưới tiêu đề trang (h1) mà không nhảy cấp", () => {
    render(
      <>
        <h1>Tài khoản</h1>
        <EmptyState title="Không có tài khoản phù hợp" />
      </>,
    );
    expect(screen.getByRole("heading", { level: 2, name: "Không có tài khoản phù hợp" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3 })).toBeNull();
  });

  it.each([1, 2, 3, 4, 5, 6] as const)("headingLevel=%i đổi cấp tiêu đề, giữ nguyên kiểu chữ", (level) => {
    render(<EmptyState title="Không có dữ liệu" headingLevel={level} />);
    const heading = screen.getByRole("heading", { level, name: "Không có dữ liệu" });
    expect(heading.tagName).toBe(`H${level}`);
    expect(heading).toHaveClass("text-title", "font-semibold", "text-text");
  });

  it("chỉ tiêu đề: không dựng mô tả hay nút", () => {
    render(<EmptyState title="Không có dữ liệu" />);
    expect(screen.getByRole("heading", { name: "Không có dữ liệu" })).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
