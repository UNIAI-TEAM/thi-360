import { createTranslator } from "next-intl";
import { describe, expect, it } from "vitest";

import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

import { formatPromotionDateTime, isoToVietnamDatetimeLocal, vietnamDatetimeLocalToIso } from "../datetime";
import { resolvePromotionErrorMessage } from "../errors";
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

  it("isoToVietnamDatetimeLocal và vietnamDatetimeLocalToIso chuyển đổi đúng UTC+7, nhận giây và chặn ngày ảo", () => {
    expect(isoToVietnamDatetimeLocal("2026-11-20T01:00:00.000Z")).toBe("2026-11-20T08:00");
    expect(isoToVietnamDatetimeLocal(null)).toBe("");
    expect(isoToVietnamDatetimeLocal("not-a-date")).toBe("");

    expect(vietnamDatetimeLocalToIso("2026-11-20T08:00")).toBe("2026-11-20T01:00:00.000Z");
    expect(vietnamDatetimeLocalToIso("2026-11-20T02:15")).toBe("2026-11-19T19:15:00.000Z");
    expect(vietnamDatetimeLocalToIso("2026-11-20T08:00:00")).toBe("2026-11-20T01:00:00.000Z");
    expect(vietnamDatetimeLocalToIso("2026-11-20T08:00:45.250")).toBe("2026-11-20T01:00:45.250Z");

    // Chặn rollover dương lịch (31/02 -> null, 29/02 năm không nhuận -> null)
    expect(vietnamDatetimeLocalToIso("2026-02-31T10:00")).toBeNull();
    expect(vietnamDatetimeLocalToIso("2026-02-29T10:00")).toBeNull();
    expect(vietnamDatetimeLocalToIso("2024-02-29T10:00")).toBe("2024-02-29T03:00:00.000Z");
  });

  it("resolvePromotionErrorMessage chỉ xếp TypeError vào NETWORK, Error thường vào GENERIC", () => {
    const tErrors = createTranslator({ locale: "vi", messages: vi, namespace: "promotions.errors" });
    const translate = (key: string) =>
      (tErrors as unknown as (k: string) => string)(key);

    expect(resolvePromotionErrorMessage(new TypeError("Failed to fetch"), translate)).toBe(
      vi.promotions.errors.NETWORK,
    );
    expect(resolvePromotionErrorMessage(new Error("Unexpected client failure"), translate)).toBe(
      vi.promotions.errors.GENERIC,
    );
  });

  it("en.json dùng ICU plural đúng số ít / số nhiều cho danh sách, banner và bộ chọn", () => {
    const tEn = createTranslator({ locale: "en-GB", messages: en, namespace: "promotions" });
    const translate = (key: string, values?: Record<string, number>) =>
      (tEn as unknown as (k: string, v?: Record<string, number>) => string)(key, values);

    expect(translate("list.summaryCount", { total: 1 })).toBe("1 promotion");
    expect(translate("list.summaryCount", { total: 2 })).toBe("2 promotions");
    expect(translate("form.fields.classesSelected", { count: 1 })).toBe("Selected (1 class)");
    expect(translate("form.fields.classesSelected", { count: 3 })).toBe("Selected (3 classes)");
    expect(translate("form.fields.examsSelected", { count: 1 })).toBe("Selected (1 exam)");
    expect(translate("form.fields.examsSelected", { count: 2 })).toBe("Selected (2 exams)");
    expect(translate("form.fields.usersSelected", { count: 1 })).toBe("Selected (1 student)");
    expect(translate("form.fields.usersSelected", { count: 4 })).toBe("Selected (4 students)");
    expect(translate("audienceLabels.SELECTED", { classes: 1, users: 1 })).toBe(
      "Selected (1 class, 1 student)",
    );
    expect(translate("form.banner.inUseDescription", { count: 1 })).toContain("1 redemption.");
    expect(translate("form.banner.inUseDescription", { count: 5 })).toContain("5 redemptions.");
  });
});
