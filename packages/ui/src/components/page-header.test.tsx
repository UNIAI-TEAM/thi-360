import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PageHeader } from "./page-header";

describe("PageHeader", () => {
  it("tiêu đề là h1, mô tả và nút chính hiện cùng", () => {
    render(
      <PageHeader title="Tài khoản" description="Quản lý tài khoản" actions={<button>Tạo mới</button>} />,
    );
    expect(screen.getByRole("heading", { level: 1, name: "Tài khoản" })).toBeInTheDocument();
    expect(screen.getByText("Quản lý tài khoản")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo mới" })).toBeInTheDocument();
  });

  it("không có actions thì không dựng vùng nút", () => {
    render(<PageHeader title="Tổng quan" />);
    expect(screen.queryByRole("button")).toBeNull();
  });

  // UNI-2586: jsdom không dàn trang và không áp media query; kiểm lớp quyết định cách xuống dòng ở 360px.
  it("màn hẹp: nút xuống dưới tiêu đề và tự xuống dòng; từ sm nằm cùng hàng, căn phải", () => {
    render(
      <PageHeader
        title="Tài khoản"
        description="Quản lý tài khoản"
        actions={
          <>
            <button>Cấp hàng loạt</button>
            <button>Tạo tài khoản</button>
          </>
        }
      />,
    );
    const heading = screen.getByRole("heading", { level: 1, name: "Tài khoản" });
    const header = heading.closest("header") as HTMLElement;
    expect(header).toHaveClass("flex", "flex-col", "sm:flex-row", "sm:flex-wrap", "sm:items-center", "sm:justify-between");
    expect(header.className).not.toMatch(/(^|\s)items-center(\s|$)/);

    const actions = screen.getByRole("button", { name: "Tạo tài khoản" }).parentElement as HTMLElement;
    expect(actions).toHaveAttribute("data-slot", "page-actions");
    expect(actions).toHaveClass("flex", "flex-wrap", "max-w-full", "gap-3");
    expect(actions).toContainElement(screen.getByRole("button", { name: "Cấp hàng loạt" }));
    // Thứ tự DOM: tiêu đề trước, nút sau (nút ở dưới trên màn hẹp, bên phải trên màn rộng).
    expect(heading.compareDocumentPosition(actions) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    const titleBlock = heading.parentElement as HTMLElement;
    expect(titleBlock).toHaveClass("min-w-0", "max-w-full");
    expect(heading).toHaveClass("wrap-break-word");
    expect(screen.getByText("Quản lý tài khoản")).toHaveClass("wrap-break-word");
  });
});
