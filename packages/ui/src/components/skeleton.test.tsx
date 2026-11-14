import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Skeleton } from "./skeleton";

describe("Skeleton", () => {
  it("giấu khỏi trình đọc màn hình và nhận class kích thước", () => {
    const { container } = render(<Skeleton className="h-4 w-32" />);
    const block = container.firstElementChild;
    expect(block).toHaveAttribute("aria-hidden", "true");
    expect(block).toHaveClass("h-4", "w-32", "animate-pulse");
  });
});
