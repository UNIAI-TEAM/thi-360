import { describe, expect, it } from "vitest";
import { pickName } from "./pick-name";

describe("pickName chọn tên theo ngôn ngữ", () => {
  it("giao diện en trả nameEn khi có", () => {
    expect(pickName({ nameVi: "Toán", nameEn: "Math" }, "en")).toBe("Math");
  });

  it("giao diện en trả nameVi khi nameEn vắng, rỗng hoặc chỉ có khoảng trắng", () => {
    expect(pickName({ nameVi: "Toán", nameEn: undefined }, "en")).toBe("Toán");
    expect(pickName({ nameVi: "Toán", nameEn: null }, "en")).toBe("Toán");
    expect(pickName({ nameVi: "Toán", nameEn: "" }, "en")).toBe("Toán");
    expect(pickName({ nameVi: "Toán", nameEn: "   " }, "en")).toBe("Toán");
  });

  it("giao diện vi luôn trả nameVi kể cả khi nameEn có", () => {
    expect(pickName({ nameVi: "Toán", nameEn: "Math" }, "vi")).toBe("Toán");
    expect(pickName({ nameVi: "Toán", nameEn: "Math" })).toBe("Toán");
  });

  it("trả chuỗi đã cắt khoảng trắng hai đầu", () => {
    expect(pickName({ nameVi: "  Toán  ", nameEn: " Math " }, "en")).toBe("Math");
    expect(pickName({ nameVi: "  Toán  ", nameEn: " Math " }, "vi")).toBe("Toán");
  });

  it("dự phòng nameEn khi nameVi vắng hoặc chỉ có khoảng trắng", () => {
    expect(pickName({ nameVi: null, nameEn: "Math" }, "vi")).toBe("Math");
    expect(pickName({ nameVi: "  ", nameEn: "Math" }, "en")).toBe("Math");
    expect(pickName({})).toBe("");
  });

  it("coi ngôn ngữ lạ như vi", () => {
    expect(pickName({ nameVi: "Toán", nameEn: "Math" }, "fr")).toBe("Toán");
  });
});
