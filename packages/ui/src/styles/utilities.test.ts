import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = readFileSync("src/styles/utilities.css", "utf8");
/** Bỏ chú thích để chỉ xét phần khai báo. */
const code = css.replace(/\/\*[\s\S]*?\*\//g, "");
const tokensCss = readFileSync("src/styles/tokens.css", "utf8");

describe("utilities.css", () => {
  it("không chứa mã màu: màu chỉ đi qua token", () => {
    expect(code).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    expect(code).not.toMatch(/\b(rgb|rgba|hsl|hsla|oklch|oklab)\(/i);
    for (const [, name] of code.matchAll(/var\((--color-[\w-]+)/g)) {
      expect(tokensCss, `thiếu token ${name}`).toContain(`${name}:`);
    }
  });

  it("token màu bóng cuộn có ở cả chế độ sáng và tối", () => {
    expect(tokensCss.match(/--color-scroll-shadow:/g)).toHaveLength(2);
    const dark = tokensCss.slice(tokensCss.indexOf('[data-theme="dark"]'));
    expect(dark).toContain("--color-scroll-shadow:");
  });

  it("hai app cùng nhập file này ngay sau tokens.css", () => {
    for (const app of ["web-admin", "web-student"]) {
      const globals = readFileSync(`../../apps/${app}/src/app/globals.css`, "utf8");
      expect(globals, app).toMatch(
        /@import "[^"]*packages\/ui\/src\/styles\/tokens\.css";\r?\n@import "[^"]*packages\/ui\/src\/styles\/utilities\.css";/,
      );
    }
  });
});

describe("scroll-shadows-y", () => {
  it("khai báo đúng một lần, bằng @utility", () => {
    expect(code.match(/@utility\s+scroll-shadows-y\s*\{/g)).toHaveLength(1);
    expect(code).not.toMatch(/\.scroll-shadows-y/);
  });

  it("vẽ bóng bằng phần tử giả dính mép, không nhận chuột, không chiếm chỗ", () => {
    expect(code).toMatch(/&::before,\s*&::after\s*\{[^}]*position:\s*sticky;[^}]*pointer-events:\s*none;/);
    expect(code).toMatch(/&::before\s*\{[^}]*inset-block-start:\s*0;[^}]*margin-block-end:\s*-0\.75rem;/);
    expect(code).toMatch(/&::after\s*\{[^}]*inset-block-end:\s*0;[^}]*margin-block-start:\s*-0\.75rem;/);
  });

  it("tầng 1: truy vấn trạng thái cuộn theo hai phía", () => {
    expect(code).toMatch(/container-type:\s*scroll-state;/);
    expect(code).toContain("@container scroll-state(scrollable: block-start)");
    expect(code).toContain("@container scroll-state(scrollable: block-end)");
  });

  it("tầng 2: hoạt ảnh theo vị trí cuộn khi chưa có truy vấn trạng thái cuộn", () => {
    const start = code.indexOf("@supports not (container-type: scroll-state)");
    expect(start).toBeGreaterThanOrEqual(0);
    const block = code.slice(start, code.indexOf("@supports not ((container-type", start));
    // `animation` viết tắt đặt lại `animation-timeline`, nên dòng thời gian phải khai báo sau.
    expect(block.indexOf("animation-timeline: scroll()")).toBeGreaterThan(
      block.indexOf("animation: scroll-shadows-show"),
    );
    expect(code).toMatch(/@keyframes scroll-shadows-show\s*\{\s*to\s*\{\s*opacity:\s*1;/);
  });

  it("tầng 3: nền `local` trên màu nền của vùng cuộn khi chưa có cả hai", () => {
    const start = code.indexOf(
      "@supports not ((container-type: scroll-state) or (animation-timeline: scroll()))",
    );
    expect(start).toBeGreaterThanOrEqual(0);
    const block = code.slice(start);
    expect(block.match(/no-repeat\s+local/g)).toHaveLength(2);
    expect(block).toContain("var(--scroll-shadows-surface, var(--color-surface))");
  });

  it("tắt chuyển tiếp độ mờ khi người dùng chọn giảm chuyển động", () => {
    expect(code).toMatch(
      /@media \(prefers-reduced-motion: reduce\)\s*\{\s*&::before,\s*&::after\s*\{\s*transition:\s*none;/,
    );
  });
});
