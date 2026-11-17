import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { PaletteGrid } from "../insert/math/palette-grid";
import { renderWithProviders } from "@/test/render";

/** jsdom không có bố cục: đặt kích thước tự nhiên của công thức và của ô để thử phép thu nhỏ. */
function stubLayout(glyph: { width: number; height: number }, box: { width: number; height: number }) {
  vi.spyOn(HTMLElement.prototype, "offsetWidth", "get").mockReturnValue(glyph.width);
  vi.spyOn(HTMLElement.prototype, "offsetHeight", "get").mockReturnValue(glyph.height);
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(box.width);
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(box.height);
}

function renderGrid() {
  renderWithProviders(<PaletteGrid items={["limit"]} onPick={() => undefined} label="Group" />, { locale: "en", messages: en });
  const button = screen.getByRole("button", { name: "Limit" });
  return button.querySelector<HTMLElement>("span > span > span")?.parentElement as HTMLElement;
}

afterEach(() => vi.restoreAllMocks());

describe("palette grid glyph fit", () => {
  it("shrinks a glyph wider than its cell so it fits, keeping the aspect ratio", () => {
    stubLayout({ width: 120, height: 30 }, { width: 60, height: 46 });
    const glyph = renderGrid();
    expect(glyph.style.transform).toBe("scale(0.5)");
  });

  it("shrinks a glyph taller than its cell by the height ratio", () => {
    stubLayout({ width: 40, height: 92 }, { width: 60, height: 46 });
    expect(renderGrid().style.transform).toBe("scale(0.5)");
  });

  it("never enlarges a glyph that already fits", () => {
    stubLayout({ width: 30, height: 20 }, { width: 60, height: 46 });
    expect(renderGrid().style.transform).toBe("");
  });
});
