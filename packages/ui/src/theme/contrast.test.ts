import { describe, expect, it } from "vitest";
import {
  getContrastRatio,
  getReadableOnColor,
  getReadablePrimary,
  getReadablePrimaryUi,
  type ThemeContrastInput,
} from "./contrast";

describe("contrast (port từ reap accessibleThemeColors.test.ts)", () => {
  it("falls back to readable semantic text when primary tones are too dark", () => {
    const theme: ThemeContrastInput = {
      mode: "dark",
      primary: {
        main: "#25105f",
        light: "#351a75",
        dark: "#140737",
      },
      background: {
        paper: "#1a1d22",
        default: "#0f1115",
      },
      text: {
        primary: "#e5e7eb",
        secondary: "#a5abb5",
      },
    };

    expect(getContrastRatio(getReadablePrimary(theme), theme.background.paper)).toBeGreaterThanOrEqual(
      4.5,
    );
    expect(
      getContrastRatio(getReadablePrimaryUi(theme), theme.background.paper),
    ).toBeGreaterThanOrEqual(3);
  });

  it("chooses readable text for a solid primary surface", () => {
    const theme: ThemeContrastInput = {
      mode: "light",
      primary: {
        main: "#f6e75a",
      },
      background: {
        paper: "#ffffff",
      },
      text: {
        primary: "#11181c",
      },
    };

    expect(
      getContrastRatio(getReadableOnColor(theme, theme.primary.main), theme.primary.main),
    ).toBeGreaterThanOrEqual(4.5);
  });
});
