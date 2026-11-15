import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import {
  CardList,
  CardListItem,
  ListCard,
  ListCardTag,
  ListCardTitleButton,
  ListCardTitleLink,
} from "./list-card";

describe("CardList / ListCard", () => {
  it("là danh sách có vai trò rõ, mỗi thẻ đủ tiêu đề, dòng phụ, chip, thông tin thêm", () => {
    render(
      <CardList aria-label="Tài khoản">
        <CardListItem>
          <ListCard
            title="nguyenvana"
            caption="Nguyễn Văn A"
            status={<span>Hoạt động</span>}
            tags={<ListCardTag>Học viên</ListCardTag>}
            meta={[{ label: "Mã", value: "HV001" }, false, undefined, { label: "Lớp", value: "3" }]}
          >
            <p>Lý do</p>
          </ListCard>
        </CardListItem>
      </CardList>,
    );
    const list = screen.getByRole("list", { name: "Tài khoản" });
    const item = within(list).getByRole("listitem");
    expect(item).toHaveTextContent("nguyenvana");
    expect(item).toHaveTextContent("Nguyễn Văn A");
    expect(item).toHaveTextContent("Hoạt động");
    expect(item).toHaveTextContent("Học viên");
    expect(item).toHaveTextContent("Lý do");
    expect(item).toHaveTextContent("Mã: HV001 · Lớp: 3");
  });

  it("không vẽ hàng chip và dòng thông tin thêm khi không có", () => {
    const { container } = render(<ListCard title="Tên" />);
    expect(container.querySelectorAll("p")).toHaveLength(0);
    expect(container.textContent).toBe("Tên");
  });

  it("thao tác nằm trên vùng bấm của tiêu đề", () => {
    render(<ListCard title="Tên" actions={<button type="button">Thao tác</button>} />);
    expect(screen.getByRole("button", { name: "Thao tác" }).parentElement).toHaveClass("relative", "z-1");
  });

  it("liên kết tiêu đề phủ cả thẻ, tên truy cập chỉ là tiêu đề, dùng được liên kết của router", () => {
    function RouterLink({ href, className, children }: { href: string; className?: string; children: ReactNode }) {
      return (
        <a href={href} className={className} data-router="">
          {children}
        </a>
      );
    }
    render(
      <ListCard
        title={
          <ListCardTitleLink href="/users/1" linkComponent={RouterLink}>
            nguyenvana
          </ListCardTitleLink>
        }
      />,
    );
    const link = screen.getByRole("link", { name: "nguyenvana" });
    expect(link).toHaveAttribute("href", "/users/1");
    expect(link).toHaveAttribute("data-router");
    expect(link).toHaveClass("after:absolute", "after:inset-0");
  });

  it("nút tiêu đề phủ cả thẻ và gọi onClick", async () => {
    const onClick = vi.fn();
    render(<ListCard title={<ListCardTitleButton onClick={onClick}>Giá trị</ListCardTitleButton>} />);
    const button = screen.getByRole("button", { name: "Giá trị" });
    expect(button).toHaveClass("after:absolute", "after:inset-0");
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
