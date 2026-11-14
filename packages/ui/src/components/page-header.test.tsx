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
});
