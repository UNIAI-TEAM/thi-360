import { createTranslator } from "next-intl";
import { describe, expect, it } from "vitest";

import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

import { formatPromotionDateTime } from "../datetime";
import { formatDiscountSummary, formatNumber, formatVnd } from "../format";

describe("promotion format & datetime locale handling (vi, en, en-GB)", () => {
  it("formatVnd định dạng chuẩn VND cho cả en và en-GB, không rơi về ký tự đ", () => {
    expect(formatVnd(50000, "en-GB")).toBe("50,000 VND");
    expect(formatVnd(50000, "en")).toBe("50,000 VND");
    expect(formatVnd(null, "en-GB")).toBe("0 VND");
    expect(formatVnd(undefined, "en-GB")).toBe("0 VND");
    expect(formatVnd(50000, "vi")).toBe("50.000 \u0111");
    expect(formatVnd(null, "vi")).toBe("0 \u0111");
  });

  it("formatNumber nhóm chữ số theo locale", () => {
    expect(formatNumber(1587400, "en-GB")).toBe("1,587,400");
    expect(formatNumber(1587400, "vi")).toBe("1.587.400");
  });

  it("formatDiscountSummary dùng định dạng tiền en-GB cho mức giảm cố định và trần giảm giá", () => {
    const tEn = createTranslator({ locale: "en-GB", messages: en, namespace: "promotions.discountLabels" });
    const tVi = createTranslator({ locale: "vi", messages: vi, namespace: "promotions.discountLabels" });

    expect(
      formatDiscountSummary("PERCENT", 30, 50000, "en-GB", (k, v) =>
        (tEn as unknown as (key: string, values?: Record<string, string | number>) => string)(k, v),
      ),
    ).toBe("-30% (up to 50,000 VND)");

    expect(
      formatDiscountSummary("AMOUNT", 26000, null, "en-GB", (k, v) =>
        (tEn as unknown as (key: string, values?: Record<string, string | number>) => string)(k, v),
      ),
    ).toBe("-26,000 VND");

    expect(
      formatDiscountSummary("PERCENT", 30, 50000, "vi", (k, v) =>
        (tVi as unknown as (key: string, values?: Record<string, string | number>) => string)(k, v),
      ),
    ).toBe("-30% (tối đa 50.000 đ)");
  });

  it("formatPromotionDateTime định dạng theo en-GB (ngày/tháng/năm, giờ:phút) ở múi giờ Việt Nam", () => {
    const iso = "2026-11-17T03:30:00Z";
    expect(formatPromotionDateTime(iso, "en-GB")).toBe("17/11/2026, 10:30");
    expect(formatPromotionDateTime(iso, "en")).toBe("17/11/2026, 10:30");
    expect(formatPromotionDateTime(iso, "vi")).toBe("10:30 17/11/2026");
  });
});
