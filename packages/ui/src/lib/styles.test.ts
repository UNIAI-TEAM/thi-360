import { describe, expect, it } from "vitest";
import { deriveTokens, getContrastRatio, PRESET_PALETTES } from "../theme";
import { fieldBase, focusRing, focusRingInset } from "./styles";

describe("focusRing (VIS-7)", () => {
  it("dùng màu đặc của token, không giảm alpha", () => {
    expect(focusRing).toContain("focus-visible:ring-primary-text");
    expect(focusRing).not.toMatch(/focus-visible:ring-[\w-]+\/\d+/);
  });

  it("màu vòng (primary-text) đạt >= 3:1 trên nền trang, bề mặt và nền phụ, sáng và tối, mọi bộ màu", () => {
    for (const palette of PRESET_PALETTES) {
      for (const mode of ["light", "dark"] as const) {
        const tokens = deriveTokens({ primary: palette.primary, accent: palette.accent, mode });
        for (const background of [tokens.page, tokens.surface, tokens.subtle]) {
          expect(
            getContrastRatio(tokens.primaryText, background),
            `${palette.id} ${mode} primaryText/${background}`,
          ).toBeGreaterThanOrEqual(3);
        }
      }
    }
  });
});

/*
 * C-13 / S-04. `forced-colors: active` bỏ mọi `box-shadow`, nên vòng focus vẽ bằng bóng đổ phải kèm một viền
 * ngoài thật. `outline-hidden` của Tailwind 4 (đã đối chiếu CSS sinh ra của bản 4.3.5 đang cài) là
 * `outline-style: none` kèm `@media (forced-colors: active) { outline: 2px solid transparent; outline-offset: 2px }`;
 * `outline-none` thì không có nhánh forced-colors. jsdom không dựng được chế độ này nên kiểm ở mức lớp.
 */
describe("vòng focus ở chế độ tương phản cao (C-13, S-04)", () => {
  const classesOf = (value: string) => value.split(/\s+/);

  it("focusRing tắt viền ngoài bằng outline-hidden, không bằng outline-none", () => {
    expect(classesOf(focusRing)).toContain("focus-visible:outline-hidden");
    expect(focusRing).not.toContain("outline-none");
  });

  it("fieldBase tắt viền ngoài khi focus bằng outline-hidden, không bằng outline-none", () => {
    expect(classesOf(fieldBase)).toContain("focus:outline-hidden");
    expect(fieldBase).not.toContain("outline-none");
  });

  it("focusRingInset vẽ bằng outline thật và không tắt nó", () => {
    expect(classesOf(focusRingInset)).toEqual(
      expect.arrayContaining(["focus-visible:outline-3", "focus-visible:outline-primary-text"]),
    );
    expect(focusRingInset).not.toMatch(/outline-(none|hidden)/);
    expect(focusRingInset).not.toMatch(/ring-/);
  });
});

/*
 * S-10. Viền của ô nhập đang focus là dấu hiệu trạng thái (WCAG 1.4.11: >= 3:1 trên bề mặt). `primary-600` là
 * màu gốc của bộ màu, không được chỉnh theo nền; `primary-text` thì được suy ra để đọc được ở cả hai chế độ.
 */
describe("fieldBase: viền focus theo token đã chỉnh tương phản (S-10)", () => {
  it("dùng primary-text cho viền và quầng, không dùng primary-600", () => {
    const classes = fieldBase.split(/\s+/);
    expect(classes).toContain("focus:border-primary-text");
    expect(classes).toContain("focus:ring-primary-text/15");
    expect(fieldBase).not.toContain("primary-600");
  });

  it("viền focus (primary-text) đạt >= 3:1 trên bề mặt và nền phụ của ô bị khóa, sáng và tối, mọi bộ màu", () => {
    for (const palette of PRESET_PALETTES) {
      for (const mode of ["light", "dark"] as const) {
        const tokens = deriveTokens({ primary: palette.primary, accent: palette.accent, mode });
        for (const background of [tokens.surface, tokens.subtle]) {
          expect(
            getContrastRatio(tokens.primaryText, background),
            `${palette.id} ${mode} primaryText/${background}`,
          ).toBeGreaterThanOrEqual(3);
        }
      }
    }
  });

  it("primary-600 chưa chỉnh thì KHÔNG đạt 3:1 với ít nhất một bộ có sẵn (lý do đổi token)", () => {
    const failing: string[] = [];
    for (const palette of PRESET_PALETTES) {
      for (const mode of ["light", "dark"] as const) {
        const tokens = deriveTokens({ primary: palette.primary, accent: palette.accent, mode });
        if (getContrastRatio(tokens.primary600, tokens.surface) < 3) failing.push(`${palette.id} ${mode}`);
      }
    }
    expect(failing.length).toBeGreaterThan(0);
  });
});
