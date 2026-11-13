import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Card } from "./card";

describe("Card", () => {
  it("bo 12px, viền, nền bề mặt; đệm 24px mặc định", () => {
    render(<Card>Nội dung</Card>);
    expect(screen.getByText("Nội dung")).toHaveClass("rounded-lg", "border", "bg-surface", "p-6");
  });

  it("padding none bỏ đệm để bảng chạy sát mép", () => {
    render(<Card padding="none">Bảng</Card>);
    const card = screen.getByText("Bảng");
    expect(card).not.toHaveClass("p-6");
    expect(card).not.toHaveClass("p-4");
  });
});
