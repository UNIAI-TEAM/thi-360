import { describe, expect, it } from "vitest";
import { allocateSubjectCounts } from "../subject-choice";

const COUNTS = [17, 17, 16];

describe("allocateSubjectCounts (B10, D25)", () => {
  it("hai môn tự nhiên và một môn xã hội: môn tự nhiên 17, môn xã hội 16", () => {
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "SOCIAL"], COUNTS)).toEqual([[17, 17, 16]]);
    expect(allocateSubjectCounts(["NATURAL", "SOCIAL", "NATURAL"], COUNTS)).toEqual([[17, 16, 17]]);
  });

  it("một môn tự nhiên và hai môn xã hội: môn tự nhiên 16, môn xã hội 17", () => {
    expect(allocateSubjectCounts(["NATURAL", "SOCIAL", "SOCIAL"], COUNTS)).toEqual([[16, 17, 17]]);
  });

  it("ba môn cùng nhóm: ba cách chia, mỗi môn một lần nhận 16", () => {
    const expected = [
      [16, 17, 17],
      [17, 16, 17],
      [17, 17, 16],
    ];
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "NATURAL"], COUNTS)).toEqual(expected);
    expect(allocateSubjectCounts(["SOCIAL", "SOCIAL", "SOCIAL"], COUNTS)).toEqual(expected);
  });

  it("ba môn đều chưa đặt nhóm (null): một nhóm chung như máy chủ, ba cách chia (R3-1)", () => {
    expect(allocateSubjectCounts([null, null, null], COUNTS)).toEqual([
      [16, 17, 17],
      [17, 16, 17],
      [17, 17, 16],
    ]);
  });

  it("không chia được thì trả rỗng: thiếu nhóm, sai số môn, hai nhóm bằng nhau, số câu không khớp countsByPick", () => {
    expect(allocateSubjectCounts(["NATURAL", null, "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "SOCIAL", "SOCIAL"], [13, 13, 12, 12])).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "SOCIAL", "SOCIAL", "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts([], [])).toEqual([]);
  });
});
