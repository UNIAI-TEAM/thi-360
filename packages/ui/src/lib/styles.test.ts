import { describe, expect, it } from "vitest";
import { deriveTokens, getContrastRatio, PRESET_PALETTES } from "../theme";
import { focusRing } from "./styles";

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
