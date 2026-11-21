import { describe, expect, it } from "vitest";
import { FULL_DETAIL } from "@/test/storefront-fixtures";
import { formatLocaleNumber, isFullProductDetail, rangeText } from "./format";

describe("storefront/format", () => {
  it("R1-41: formatLocaleNumber và rangeText định dạng đúng số thập phân và >= 1000 theo vi / en", () => {
    expect(formatLocaleNumber(9.5, "vi")).toBe("9,5");
    expect(formatLocaleNumber(9.5, "en")).toBe("9.5");
    expect(formatLocaleNumber(1200, "vi")).toBe("1.200");
    expect(formatLocaleNumber(1200, "en")).toBe("1,200");

    expect(rangeText({ min: 9.5, max: 9.5 }, "vi")).toBe("9,5");
    expect(rangeText({ min: 9.5, max: 9.5 }, "en")).toBe("9.5");
    expect(rangeText({ min: 1000, max: 1200 }, "vi")).toBe("1.000–1.200");
    expect(rangeText({ min: 1000, max: 1200 }, "en")).toBe("1,000–1,200");
    expect(rangeText(null, "vi")).toBeNull();
  });

  it("R1-4: isFullProductDetail phân biệt chi tiết đầy đủ với stub 409 rút gọn", () => {
    expect(isFullProductDetail(FULL_DETAIL)).toBe(true);
    expect(isFullProductDetail({ code: "BO-1", name: "Bo 1" })).toBe(false);
    expect(isFullProductDetail({ ...FULL_DETAIL, examKind: {} })).toBe(false);
    expect(isFullProductDetail({ ...FULL_DETAIL, benefits: null })).toBe(false);
    expect(isFullProductDetail({ ...FULL_DETAIL, price: "299000" })).toBe(false);
    expect(isFullProductDetail({ ...FULL_DETAIL, listPrice: Number.NaN })).toBe(false);
    expect(isFullProductDetail(null)).toBe(false);
  });
});
