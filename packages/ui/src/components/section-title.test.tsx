import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SectionTitle } from "./section-title";

describe("SectionTitle", () => {
  it("hiện tiêu đề, huy hiệu đếm và liên kết", () => {
    render(<SectionTitle title="Đề thi mới" count="3 đề" action={<a href="/de">Xem tất cả →</a>} />);
    expect(screen.getByRole("heading", { name: "Đề thi mới" })).toBeInTheDocument();
    expect(screen.getByText("3 đề")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem tất cả →" })).toHaveAttribute("href", "/de");
  });

  it("thanh dọc đổi màu theo nhóm và giấu khỏi trình đọc màn hình", () => {
    const { container } = render(<SectionTitle title="Nhóm" barClassName="bg-accent-500" />);
    const bar = container.querySelector("[aria-hidden]");
    expect(bar).toHaveClass("bg-accent-500");
    expect(bar).not.toHaveClass("bg-primary-600");
  });
});
