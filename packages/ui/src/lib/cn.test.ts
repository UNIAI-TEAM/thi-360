import { describe, expect, it } from "vitest";
import { cn } from "./cn";

describe("cn", () => {
  it("class viết sau thắng class cùng nhóm", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("giữ cả cỡ chữ token lẫn màu chữ token", () => {
    expect(cn("text-caption", "text-text-muted")).toBe("text-caption text-text-muted");
  });

  it("cỡ chữ token thắng cỡ chữ token trước đó", () => {
    expect(cn("text-body", "text-caption")).toBe("text-caption");
  });
});
