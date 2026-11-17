import { describe, expect, it } from "vitest";
import { usageLocation } from "../usage-location";

const names = {
  subject: (code: string) => ({ MATH: "Mathematics" })[code] ?? code,
  combination: (code: string) => ({ CA1: "CA1 · Physics, Chemistry" })[code],
};

describe("usageLocation (F-10)", () => {
  it("môn và tổ hợp lấy tên danh mục theo ngôn ngữ, phần và khối giữ tên của bài", () => {
    expect(
      usageLocation({ partName: "Phần 3", optionCode: "CA1", optionName: "CA1 · Vật lý, Hóa học", blockName: "Khối Lý" }, "MATH", names),
    ).toBe("Phần 3 › CA1 · Physics, Chemistry › Mathematics › Khối Lý");
  });

  it("mã tổ hợp không có trong danh mục: dùng tên lưu trên bài, rồi mã; không lựa chọn thì bỏ", () => {
    expect(usageLocation({ partName: "P", optionCode: "ZZ", optionName: "Tên bài", blockName: "B" }, "MATH", names)).toBe("P › Tên bài › Mathematics › B");
    expect(usageLocation({ partName: "P", optionCode: "ZZ", optionName: "", blockName: "B" }, "MATH", names)).toBe("P › ZZ › Mathematics › B");
    expect(usageLocation({ partName: "P", optionCode: null, optionName: "", blockName: "" }, "MATH", names)).toBe("P › Mathematics");
  });
});
