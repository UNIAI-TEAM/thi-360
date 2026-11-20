import { describe, expect, it } from "vitest";
import { subjectFormLimits } from "../subject-forms/limits";
import { listSubjectFormPairs } from "../subject-forms/registry";

/**
 * Bảng giới hạn R-12 phía web (`limits.ts`, đọc từ `registry.ts`) khớp bảng máy chủ `SubjectFormLimits` (CR-4, bảng đã chốt ở checkpoint
 * `CR-4:1`, AD-74): `(setCountMin, setCountMax, setSize, shortAnswerMin)`. HSA, QDA × Lý, Hóa, Sinh = (1, 2, 3, 1); HSA, QDA × Sử, Địa = (1, 2, 3, 0);
 * mọi cặp khác không có luật. Chỉ mã canonical. Bảng viết tay từ checkpoint, không đọc từ mã máy chủ (không có đường nào để web đọc).
 */
const SERVER_TABLE: Record<string, [number, number, number, number]> = {
  "HSA:LY": [1, 2, 3, 1],
  "HSA:HOA": [1, 2, 3, 1],
  "HSA:SINH": [1, 2, 3, 1],
  "HSA:SU": [1, 2, 3, 0],
  "HSA:DIA": [1, 2, 3, 0],
  "QDA:LY": [1, 2, 3, 1],
  "QDA:HOA": [1, 2, 3, 1],
  "QDA:SINH": [1, 2, 3, 1],
  "QDA:SU": [1, 2, 3, 0],
  "QDA:DIA": [1, 2, 3, 0],
};

describe("Giới hạn R-12 phía web khớp bảng máy chủ (CR-4)", () => {
  it("serverLimitsTableMatchesWeb: đúng 10 cặp có giới hạn, cùng số chùm, cỡ chùm, câu điền tối thiểu; 65 cặp còn lại không có luật", () => {
    const seen: string[] = [];
    for (const { kindCode, subjectCode } of listSubjectFormPairs()) {
      const key = `${kindCode}:${subjectCode}`;
      const limits = subjectFormLimits(kindCode, subjectCode);
      const server = SERVER_TABLE[key];
      if (!server) {
        expect(limits, key).toBeNull();
        continue;
      }
      expect(limits, key).not.toBeNull();
      expect([limits!.setCount.min, limits!.setCount.max, limits!.setCount.size, limits!.shortAnswerMin], key).toEqual(server);
      seen.push(key);
    }
    expect(seen.toSorted()).toEqual(Object.keys(SERVER_TABLE).toSorted());
  });
});
