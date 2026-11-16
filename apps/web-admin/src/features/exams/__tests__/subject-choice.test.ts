import { describe, expect, it } from "vitest";
import { allocateSubjectCounts, pickAllocation } from "../subject-choice";
import { seededRandom } from "../review-preview";

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

  it("không chia được thì trả rỗng: thiếu nhóm, sai số môn, hai nhóm bằng nhau, số câu không khớp countsByPick", () => {
    expect(allocateSubjectCounts(["NATURAL", null, "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "SOCIAL", "SOCIAL"], [13, 13, 12, 12])).toEqual([]);
    expect(allocateSubjectCounts(["NATURAL", "NATURAL", "SOCIAL", "SOCIAL", "SOCIAL"], COUNTS)).toEqual([]);
    expect(allocateSubjectCounts([], [])).toEqual([]);
  });
});

describe("pickAllocation", () => {
  const all = allocateSubjectCounts(["NATURAL", "NATURAL", "NATURAL"], COUNTS);

  it("cùng hạt giống chọn cùng môn nhận 16; qua nhiều hạt giống mỗi môn đều có lúc nhận 16", () => {
    const lowIndexOf = (seed: number) => pickAllocation(all, seededRandom(seed))!.indexOf(16);
    expect(lowIndexOf(7)).toBe(lowIndexOf(7));
    expect(new Set(Array.from({ length: 30 }, (_, seed) => lowIndexOf(seed + 1)))).toEqual(new Set([0, 1, 2]));
  });

  it("chỉ có một cách chia thì dùng cách đó; không có cách nào thì null", () => {
    expect(pickAllocation([[17, 17, 16]], () => 0.99)).toEqual([17, 17, 16]);
    expect(pickAllocation([], () => 0.5)).toBeNull();
  });
});
