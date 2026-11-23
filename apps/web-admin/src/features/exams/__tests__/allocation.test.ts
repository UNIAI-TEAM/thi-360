import { describe, expect, it } from "vitest";
import {
  applyTopicTotal,
  blockingMappingRows,
  canRequire,
  convertTopicEntry,
  newTopicQuota,
  percentTotalValid,
  ratePercent,
  topicEntryMode,
  topicQuotaView,
} from "../allocation";
import type { TopicQuota } from "../types";

const quota = (over: Partial<TopicQuota> = {}): TopicQuota => ({ ...newTopicQuota("q1", "Xác suất"), nodeIds: ["n1"], ...over });

describe("Chỉ tiêu chủ đề: quy đổi tỷ lệ và số câu (R-118)", () => {
  it("tỷ lệ 10 trên 30 câu: số câu 3, còn lại 27, giữ tỷ lệ gốc", () => {
    const rows = [quota({ count: 3, percent: 10 })];
    const view = topicQuotaView(rows, 30);
    expect(view.counts).toEqual([3]);
    expect(view.rest).toBe(27);
    expect(view.grandCount).toBe(30);
    expect(rows[0]).toMatchObject({ count: 3, percent: 10 });
  });

  it("số câu 12 trên 30: tỷ lệ 40% và không có percent", () => {
    const rows = [quota({ count: 12 })];
    const view = topicQuotaView(rows, 30);
    expect(view.rates).toEqual([40]);
    expect(topicEntryMode(rows)).toBe("COUNT");
    expect(rows[0]!.percent).toBeUndefined();
  });

  it("dòng Phần còn lại không âm; các chỉ tiêu cộng quá mẫu số thì báo vượt", () => {
    const view = topicQuotaView([quota({ count: 20 }), quota({ id: "q2", count: 12 })], 30);
    expect(view.rest).toBe(0);
    expect(view.over).toBe(2);
    expect(view.grandCount).toBe(32);
  });

  it("đổi tổng từ 30 xuống 20: dòng tỷ lệ tính lại số câu, dòng số câu giữ nguyên", () => {
    const rows = [quota({ count: 3, percent: 10 }), quota({ id: "q2", count: 6 })];
    expect(applyTopicTotal(rows, 20).map((row) => row.count)).toEqual([2, 6]);
    expect(topicQuotaView(rows, 20).counts).toEqual([2, 6]);
  });

  it("chuyển nhập theo tỷ lệ rồi về nhập theo số câu giữ đúng số câu", () => {
    const rows = [quota({ count: 3 }), quota({ id: "q2", count: 5 })];
    const asPercent = convertTopicEntry(rows, "PERCENT", 18);
    expect(topicEntryMode(asPercent)).toBe("PERCENT");
    expect(applyTopicTotal(asPercent, 18).map((row) => row.count)).toEqual([3, 5]);
    const back = convertTopicEntry(asPercent, "COUNT", 18);
    expect(back.every((row) => row.percent === undefined)).toBe(true);
  });
});

describe("Tổng tỷ lệ và tỷ lệ thực (R-118)", () => {
  it("tổng tỷ lệ khác 100% thì không hợp lệ", () => {
    expect(percentTotalValid([40, 30, 20])).toBe(false);
    expect(percentTotalValid([40, 30, 30])).toBe(true);
  });

  it("mẫu số không dương thì không có tỷ lệ", () => {
    expect(ratePercent(3, 0)).toBeNull();
  });
});

describe("Chờ mapping (D12) và mức bắt buộc (H7)", () => {
  it("chỉ dòng Bắt buộc chưa gắn mã bị chặn; dòng Tham khảo thì không", () => {
    const rows = [quota({ nodeIds: [] }), quota({ id: "q2", nodeIds: [], enforcement: "REFERENCE" }), quota({ id: "q3" })];
    expect(blockingMappingRows(rows).map((row) => row.id)).toEqual(["q1"]);
  });

  it("chỉ đơn vị câu được đặt bắt buộc", () => {
    expect(canRequire(undefined)).toBe(true);
    expect(canRequire("QUESTION")).toBe(true);
    expect(canRequire("PROMPT")).toBe(false);
    expect(canRequire("POINTS")).toBe(false);
  });
});
