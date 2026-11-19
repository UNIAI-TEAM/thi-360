import { describe, expect, it } from "vitest";
import { apportionByPercent } from "../matrix";
import { percentsAreComplete, percentsToCounts, percentToCount, wholeSetCount } from "../subject-forms/percent";

/** FTM R-9: quy đổi dùng lại `apportionByPercent`, ví dụ của đặc tả. */
describe("percent.ts quy đổi tỷ lệ (FTM R-9)", () => {
  it("25/50/25% của 30 câu ra 8/15/7 (phần thiếu cho dòng đứng trước khi bằng nhau)", () => {
    expect(percentsToCounts([25, 50, 25], 30)).toEqual([8, 15, 7]);
    expect(percentsToCounts([25, 50, 25], 40)).toEqual([10, 20, 10]);
  });

  it("trùng kết quả của apportionByPercent cho mọi bảng thử", () => {
    for (const [percents, total] of [[[40, 60], 30], [[33.3, 33.3, 33.4], 17], [[10, 20, 30, 40], 23]] as const) {
      expect(percentsToCounts(percents, total)).toEqual(apportionByPercent(percents, total).results);
    }
  });

  it("một dòng đứng riêng: 30% của 40 = 12, 40% của 25 = 10, 10% của 30 = 3", () => {
    expect(percentToCount(30, 40)).toBe(12);
    expect(percentToCount(40, 25)).toBe(10);
    expect(percentToCount(10, 30)).toBe(3);
  });

  it("bảng chia cả mục chỉ đủ khi tổng 100%", () => {
    expect(percentsAreComplete([40, 60])).toBe(true);
    expect(percentsAreComplete([40, 50])).toBe(false);
  });

  it("chùm nguyên: 10 câu không chia hết cho cỡ chùm 4, 12 câu chia được 3 chùm", () => {
    expect(wholeSetCount(10, 4)).toEqual({ ok: false });
    expect(wholeSetCount(12, 4)).toEqual({ ok: true, sets: 3 });
    expect(wholeSetCount(5, 0)).toEqual({ ok: false });
  });
});
