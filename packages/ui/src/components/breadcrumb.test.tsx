import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Breadcrumb } from "./breadcrumb";

const items = [
  { label: "Người dùng", href: "/nguoi-dung" },
  { label: "Tài khoản", href: "/tai-khoan" },
  { label: "Nguyễn Văn An" },
];

describe("Breadcrumb", () => {
  it("các mục trước là liên kết, mục cuối là trang hiện tại và không là liên kết", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={items} />);
    expect(screen.getByRole("navigation", { name: "Đường dẫn" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Người dùng" })).toHaveAttribute("href", "/nguoi-dung");
    expect(screen.getByRole("link", { name: "Tài khoản" })).toHaveAttribute("href", "/tai-khoan");
    expect(screen.queryByRole("link", { name: "Nguyễn Văn An" })).toBeNull();
    expect(screen.getByText("Nguyễn Văn An")).toHaveAttribute("aria-current", "page");
  });

  it("mục cuối có href vẫn không là liên kết", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={[{ label: "Gốc", href: "/" }, { label: "Cuối", href: "/cuoi" }]} />);
    expect(screen.queryByRole("link", { name: "Cuối" })).toBeNull();
  });

  it("dùng thành phần liên kết do router cung cấp", () => {
    render(
      <Breadcrumb
        aria-label="Đường dẫn"
        items={items}
        linkComponent={({ href, children }) => <a href={href} data-router="1">{children}</a>}
      />,
    );
    expect(screen.getByRole("link", { name: "Người dùng" })).toHaveAttribute("data-router", "1");
  });

  it("dấu phân cách giấu khỏi trình đọc màn hình nên chỉ đếm các mục thật", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={items} />);
    expect(screen.getAllByRole("listitem")).toHaveLength(items.length);
  });

  it("không có mục nào: không hiện vùng điều hướng rỗng", () => {
    const { container } = render(<Breadcrumb aria-label="Vị trí hiện tại" items={[]} />);
    expect(screen.queryByRole("navigation")).toBeNull();
    expect(container).toBeEmptyDOMElement();
  });
});
