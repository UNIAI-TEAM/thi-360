import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Badge } from "./badge";

describe("Badge", () => {
  it("hiện nội dung và cho thêm class", () => {
    render(<Badge className="ml-2">3 đề</Badge>);
    const badge = screen.getByText("3 đề");
    expect(badge).toHaveClass("ml-2", "rounded-full", "text-text-muted");
  });
});
