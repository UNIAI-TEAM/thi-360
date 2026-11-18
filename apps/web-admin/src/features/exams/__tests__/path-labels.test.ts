import { describe, expect, it } from "vitest";
import { combinationLabel } from "../check";

describe("nhãn mã tổ hợp của đường", () => {
  it("combinationLabel: tên theo ngôn ngữ từ cấu trúc, không lặp mã khi tên = mã hoặc đã mở đầu bằng mã", () => {
    const combination = {
      code: "QD02",
      examCode: "QDA",
      partId: "p3",
      subjectCodes: [],
      missingSubjects: [],
      nameVi: "QD02 · Hóa học – Sinh học",
      nameEn: "QD02 · Chemistry – Biology",
    };
    expect(combinationLabel("QD02", { combination, locale: "vi", fallback: "QD02" })).toBe("QD02 · Hóa học – Sinh học");
    expect(combinationLabel("QD02", { combination, locale: "en", fallback: "QD02" })).toBe("QD02 · Chemistry – Biology");
    expect(combinationLabel("QD02", { combination: { ...combination, nameVi: "Hóa học – Sinh học", nameEn: null }, locale: "vi" })).toBe(
      "QD02 · Hóa học – Sinh học",
    );
    expect(combinationLabel("QD02", { combination: { ...combination, nameEn: null }, locale: "en" })).toBe("QD02 · Hóa học – Sinh học");
    expect(combinationLabel("QD01", { locale: "vi", fallback: "QD01" })).toBe("QD01");
    expect(combinationLabel("QD01", { locale: "vi", fallback: "Tổ hợp một" })).toBe("QD01 · Tổ hợp một");
  });
});
