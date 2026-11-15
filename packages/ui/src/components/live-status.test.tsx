import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LiveStatus } from "./live-status";

describe("LiveStatus", () => {
  it("là vùng status lịch sự, đọc trọn câu, có mặt trong DOM cả khi chưa có thông báo", () => {
    const { rerender } = render(<LiveStatus message="" />);
    const region = screen.getByRole("status");
    expect(region).toHaveAttribute("aria-live", "polite");
    expect(region).toHaveAttribute("aria-atomic", "true");
    expect(region).toBeEmptyDOMElement();
    expect(region).toHaveClass("sr-only");

    rerender(<LiveStatus message="5 tài khoản khớp bộ lọc" />);
    // Cùng một phần tử đổi nội dung: trình đọc màn hình mới đọc.
    expect(screen.getByRole("status")).toBe(region);
    expect(region).toHaveTextContent("5 tài khoản khớp bộ lọc");
  });

  it("assertive dùng role=alert cho lỗi cần biết ngay", () => {
    render(<LiveStatus message="Mất kết nối" politeness="assertive" />);
    const region = screen.getByRole("alert");
    expect(region).toHaveAttribute("aria-live", "assertive");
    expect(region).toHaveTextContent("Mất kết nối");
  });
});
