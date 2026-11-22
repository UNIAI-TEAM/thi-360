import { render, screen } from "@testing-library/react";
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { AUTH_BRAND_PANEL_CLASS, AuthBrandDecor, AuthPageStage } from "./auth-backdrop";

describe("AuthPageStage & AuthBrandDecor (UNI-2775)", () => {
  it("dựng lớp nền trang trí aria-hidden phía sau nội dung chính của trang xác thực", () => {
    render(
      <AuthPageStage>
        <h1>Đăng nhập</h1>
      </AuthPageStage>,
    );

    const main = screen.getByRole("main");
    expect(main).toContainElement(screen.getByRole("heading", { level: 1, name: "Đăng nhập" }));

    const backdrop = screen.getByTestId("auth-backdrop");
    expect(backdrop).toHaveAttribute("aria-hidden", "true");
    expect(backdrop.querySelectorAll("svg")).toHaveLength(2);
  });

  it("dựng lớp chuyển sắc và sóng SVG aria-hidden cho cột thương hiệu", () => {
    render(
      <section className={AUTH_BRAND_PANEL_CLASS}>
        <AuthBrandDecor />
        <h2>Thi 360</h2>
      </section>,
    );

    const decor = screen.getByTestId("auth-brand-decor");
    expect(decor).toHaveAttribute("aria-hidden", "true");
    expect(decor.querySelectorAll("svg path")).toHaveLength(2);
    expect(screen.getByRole("heading", { level: 2, name: "Thi 360" })).toBeInTheDocument();
  });

  it("chỉ dùng biến token ngữ nghĩa, không chứa mã màu hex hay rgb cứng trong auth-backdrop.tsx", () => {
    const source = readFileSync(path.resolve(import.meta.dirname, "auth-backdrop.tsx"), "utf8");
    expect(source).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(source).not.toMatch(/\brgba?\s*\(/);
    expect(source).not.toMatch(/\bhsla?\s*\(/);
    expect(source).toContain("var(--color-primary-");
    expect(source).toContain("var(--color-accent-500)");
  });
});
