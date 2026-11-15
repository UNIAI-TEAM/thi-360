import { render, screen } from "@testing-library/react";
import { Lock } from "lucide-react";
import { describe, expect, it } from "vitest";
import { Button } from "./button";
import { StatusPage, type StatusPageProps } from "./status-page";

function Example(props: Partial<StatusPageProps>) {
  return (
    <StatusPage
      icon={Lock}
      eyebrow="MÃ LỖI 403"
      title="Bạn không có quyền truy cập trang này"
      description="Tài khoản hiện tại không được cấp quyền."
      actions={
        <>
          <Button asChild size="lg">
            <a href="/">Về trang chủ</a>
          </Button>
          <Button variant="secondary" size="lg">
            Đăng nhập bằng tài khoản khác
          </Button>
        </>
      }
      {...props}
    />
  );
}

describe("StatusPage", () => {
  it("đứng riêng (mặc định): tự là <main> của trang, cao trọn màn hình, có tiêu đề cấp 1", () => {
    render(<Example />);
    const main = screen.getByRole("main");
    expect(main).toHaveClass("min-h-screen", "bg-page");
    expect(main).toContainElement(screen.getByRole("heading", { level: 1, name: "Bạn không có quyền truy cập trang này" }));
    expect(screen.getByText("MÃ LỖI 403")).toBeInTheDocument();
    expect(screen.getByText("Tài khoản hiện tại không được cấp quyền.")).toBeInTheDocument();
  });

  it("embedded: không dựng <main> thứ hai bên trong <main> của khung, không ép chiều cao màn hình", () => {
    render(
      <main data-testid="shell-main">
        <Example embedded />
      </main>,
    );
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByRole("main")).toBe(screen.getByTestId("shell-main"));

    const wrapper = screen.getByTestId("shell-main").firstElementChild as HTMLElement;
    expect(wrapper.tagName).toBe("DIV");
    expect(wrapper).not.toHaveClass("min-h-screen");
    expect(wrapper).not.toHaveClass("bg-page");
    // Nội dung vẫn đủ: tiêu đề, mô tả, hai nút.
    expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" })).toBeInTheDocument();
  });

  it("hiện dòng meta khi có", () => {
    const { rerender } = render(<Example meta="Mã theo dõi: abc-123" />);
    expect(screen.getByText("Mã theo dõi: abc-123")).toBeInTheDocument();
    rerender(<Example />);
    expect(screen.queryByText("Mã theo dõi: abc-123")).toBeNull();
  });

  it("thẻ hẹp: hàng nút xếp chồng theo bề rộng của thẻ, nút rộng hết thẻ và nhãn dài được xuống dòng", () => {
    render(<Example />);
    const actions = screen.getByRole("link", { name: "Về trang chủ" }).parentElement as HTMLElement;
    expect(actions).toHaveClass(
      "flex",
      "flex-wrap",
      "@max-md:flex-col",
      "@max-md:items-stretch",
      "@max-md:[&>*]:h-auto",
      "@max-md:[&>*]:min-h-12",
      "@max-md:[&>*]:whitespace-normal",
    );
    // Điểm ngắt tính theo thẻ (vùng chứa), và thẻ giảm đệm ngang trên màn hình hẹp.
    const card = actions.parentElement as HTMLElement;
    expect(card).toHaveClass("@container", "px-6", "sm:px-10");
    expect(card).not.toHaveClass("px-10");
  });
});
