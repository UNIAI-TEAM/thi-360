import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Breadcrumb } from "./breadcrumb";

const items = [
  { label: "Người dùng", href: "/nguoi-dung" },
  { label: "Tài khoản", href: "/users" },
  { label: "Nguyễn Văn An" },
];

describe("Breadcrumb", () => {
  it("các mục trước là liên kết, mục cuối là trang hiện tại và không là liên kết", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={items} />);
    expect(screen.getByRole("navigation", { name: "Đường dẫn" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Người dùng" })).toHaveAttribute("href", "/nguoi-dung");
    expect(screen.getByRole("link", { name: "Tài khoản" })).toHaveAttribute("href", "/users");
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

  it("luôn một dòng: không xuống dòng, vùng điều hướng co được trong hàng flex", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={items} className="mr-auto" />);
    const nav = screen.getByRole("navigation", { name: "Đường dẫn" });
    expect(nav).toHaveClass("min-w-0", "mr-auto");
    const list = screen.getByRole("list");
    expect(list).toHaveClass("flex", "min-w-0");
    expect(list.className).not.toMatch(/(^|\s)flex-wrap(\s|$)/);
    // Dấu phân cách không co, không bị ép mất.
    for (const separator of list.querySelectorAll("li[aria-hidden]")) {
      expect(separator).toHaveClass("shrink-0");
    }
  });

  it("thiếu chỗ thì cắt mục giữa trước, rồi mục đầu, trang hiện tại sau cùng; chữ đầy đủ vẫn trong DOM", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={items} />);
    const [first, middle, last] = screen.getAllByRole("listitem");
    expect(middle).toHaveClass("shrink-[10000]", "min-w-6");
    expect(first).toHaveClass("shrink-[100]", "min-w-6");
    expect(last).toHaveClass("shrink", "min-w-0");
    expect(last?.className).not.toMatch(/shrink-\[/);

    for (const text of [screen.getByRole("link", { name: "Người dùng" }), screen.getByRole("link", { name: "Tài khoản" }), screen.getByText("Nguyễn Văn An")]) {
      expect(text).toHaveClass("block", "truncate");
      // Không dựa vào title để lộ phần bị cắt (DESIGN.md mục 5.13).
      expect(text).not.toHaveAttribute("title");
    }
    expect(screen.getByRole("link", { name: "Tài khoản" })).toHaveTextContent("Tài khoản");
  });

  it("hai mục: mục đầu nhường chỗ trước trang hiện tại", () => {
    render(<Breadcrumb aria-label="Đường dẫn" items={[{ label: "Hệ thống" }, { label: "Tùy chỉnh giao diện" }]} />);
    const [first, last] = screen.getAllByRole("listitem");
    expect(first).toHaveClass("shrink-[100]");
    expect(last).toHaveClass("shrink", "min-w-0");
  });

  it("collapseOnNarrow: dưới sm chỉ còn trang hiện tại, các mục trước và dấu phân cách ẩn; mặc định không ẩn gì", () => {
    const { unmount } = render(<Breadcrumb aria-label="Đường dẫn" items={items} collapseOnNarrow />);
    const list = screen.getByRole("list");
    const [first, middle, last] = screen.getAllByRole("listitem");
    expect(first).toHaveClass("max-sm:hidden");
    expect(middle).toHaveClass("max-sm:hidden");
    expect(last).not.toHaveClass("max-sm:hidden");
    for (const separator of list.querySelectorAll("li[aria-hidden]")) {
      expect(separator).toHaveClass("max-sm:hidden");
    }
    unmount();

    render(<Breadcrumb aria-label="Đường dẫn" items={items} />);
    expect(screen.getByRole("list").innerHTML).not.toContain("max-sm:hidden");
  });

  it("không có mục nào: không hiện vùng điều hướng rỗng", () => {
    const { container } = render(<Breadcrumb aria-label="Vị trí hiện tại" items={[]} />);
    expect(screen.queryByRole("navigation")).toBeNull();
    expect(container).toBeEmptyDOMElement();
  });
});
