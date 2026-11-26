import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { DetailHeader } from "./detail-header";

describe("DetailHeader (UNI-2825)", () => {
  it("lối quay lại đứng trước tiêu đề; chip cạnh tiêu đề; dòng phụ và nút cùng hiện", () => {
    render(
      <DetailHeader
        back={{ href: "/promotions", label: "Danh sách khuyến mãi" }}
        title="trừ 100%"
        chips={<span>Đang chạy</span>}
        description="Khuyến mãi tự động"
        actions={<button>Sửa</button>}
      >
        <dl aria-label="Tóm tắt" />
      </DetailHeader>,
    );
    const back = screen.getByRole("link", { name: "Danh sách khuyến mãi" });
    const heading = screen.getByRole("heading", { level: 1, name: "trừ 100%" });
    expect(back).toHaveAttribute("href", "/promotions");
    expect(back.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(heading.parentElement).toContainElement(screen.getByText("Đang chạy"));
    expect(screen.getByText("Khuyến mãi tự động")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sửa" }).parentElement).toHaveAttribute("data-slot", "page-actions");
    expect(heading.closest("header")).toContainElement(screen.getByLabelText("Tóm tắt"));
  });

  it("không có lối quay lại, nút hay dòng phụ thì không dựng các vùng đó", () => {
    render(<DetailHeader title="Lô cấp tài khoản" />);
    expect(screen.queryByRole("link")).toBeNull();
    expect(document.querySelector('[data-slot="page-actions"]')).toBeNull();
  });

  it("gắn thuộc tính thêm lên h1 (data-exam-content)", () => {
    render(<DetailHeader title="Đề A" titleProps={{ "data-exam-content": "" }} />);
    expect(screen.getByRole("heading", { level: 1, name: "Đề A" })).toHaveAttribute("data-exam-content");
  });

  it("dùng thành phần liên kết của router khi được truyền", () => {
    function RouterLink({ href, className, children }: { href: string; className?: string; children: ReactNode }) {
      return (
        <a href={href} className={className} data-router="yes">
          {children}
        </a>
      );
    }
    render(<DetailHeader back={{ href: "/exams", label: "Danh sách bài thi" }} linkComponent={RouterLink} title="ttt" />);
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("data-router", "yes");
  });

  // jsdom không dàn trang: kiểm lớp quyết định bố cục (DESIGN.md mục 5.8).
  it("đầu trang không nằm trong thẻ; màn hẹp nút xuống dưới, từ sm nút cùng hàng bên phải", () => {
    render(<DetailHeader title="Lớp B" actions={<button>Xóa lớp</button>} />);
    const header = screen.getByRole("heading", { level: 1 }).closest("header") as HTMLElement;
    expect(header).toHaveAttribute("data-slot", "detail-header");
    expect(header.className).not.toMatch(/border|bg-surface|shadow/);
    const row = (screen.getByRole("button", { name: "Xóa lớp" }).parentElement as HTMLElement).parentElement as HTMLElement;
    expect(row).toHaveClass("flex-col", "sm:flex-row", "sm:flex-wrap", "sm:justify-between");
  });
});
