import { render, screen } from "@testing-library/react";
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { AUTH_BRAND_PANEL_CLASS, AuthBrandDecor, AuthPageStage } from "./auth-backdrop";

describe("AuthPageStage & AuthBrandDecor (UNI-2775)", () => {
  it("dựng đủ các lớp nền 3D Option 2 aria-hidden (mesh aura, sàn studio, quỹ đạo elip nghiêng 3D, đá kính & pha lê đa diện)", () => {
    render(
      <AuthPageStage>
        <h1>Đăng nhập</h1>
      </AuthPageStage>,
    );

    const main = screen.getByRole("main");
    expect(main).toContainElement(screen.getByRole("heading", { level: 1, name: "Đăng nhập" }));

    const backdrop = screen.getByTestId("auth-backdrop");
    expect(backdrop).toHaveAttribute("aria-hidden", "true");
    expect(backdrop.querySelector('[data-layer="center-mesh-aura"]')).not.toBeNull();
    expect(backdrop.querySelector('[data-layer="studio-floor"]')).not.toBeNull();
    expect(backdrop.querySelector('[data-layer="orbital-sculpture-left"]')).not.toBeNull();
    expect(backdrop.querySelector('[data-layer="orbital-sculpture-right"]')).not.toBeNull();
    expect(backdrop.querySelectorAll('[data-layer^="orbital-sculpture-"] ellipse').length).toBeGreaterThanOrEqual(10);
    expect(backdrop.querySelectorAll('[data-crystal] polygon').length).toBeGreaterThanOrEqual(12);
  });

  it("dựng dải lụa đa lớp và đường viền sáng mảnh aria-hidden ở đáy cột thương hiệu", () => {
    render(
      <section className={AUTH_BRAND_PANEL_CLASS}>
        <AuthBrandDecor />
        <h2>Thi 360</h2>
      </section>,
    );

    const decor = screen.getByTestId("auth-brand-decor");
    expect(decor).toHaveAttribute("aria-hidden", "true");
    expect(decor.querySelectorAll("svg path").length).toBeGreaterThanOrEqual(6);
    expect(screen.getByRole("heading", { level: 2, name: "Thi 360" })).toBeInTheDocument();
  });

  it("chỉ dùng biến token ngữ nghĩa, không chứa mã màu hex, rgb hay hsl cứng trong auth-backdrop.tsx", () => {
    const source = readFileSync(path.resolve(import.meta.dirname, "auth-backdrop.tsx"), "utf8");
    expect(source).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(source).not.toMatch(/\brgba?\s*\(/);
    expect(source).not.toMatch(/\bhsla?\s*\(/);
    expect(source).toContain("var(--color-primary-");
    expect(source).toContain("var(--color-accent-500)");
    expect(source).toContain("var(--color-surface)");
    expect(source).toContain("var(--color-page)");
  });
});
