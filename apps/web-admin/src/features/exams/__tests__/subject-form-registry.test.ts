import { describe, expect, it } from "vitest";
import { subjectFormLimits } from "../subject-forms/limits";
import { listSubjectFormPairs, patchSubjectFormEntry, subjectFormFor } from "../subject-forms/registry";

/**
 * FTM R-1 (AC-1, AC-2, AC-3, AC-55): bảng 75 cặp viết tay theo đặc tả, không sinh từ chính bảng đăng ký.
 * M = Ma trận môn (MATRIX), D = Danh sách dòng (ROWS).
 */
const TABLE: ReadonlyArray<readonly [string, string, "M" | "D"]> = [
  ["HSA", "TOAN", "M"], ["HSA", "VAN", "D"], ["HSA", "LY", "M"], ["HSA", "HOA", "M"],
  ["HSA", "SINH", "M"], ["HSA", "SU", "M"], ["HSA", "DIA", "M"], ["HSA", "ANH", "D"],
  ["QDA", "TOAN", "M"], ["QDA", "VAN", "D"], ["QDA", "LY", "M"], ["QDA", "HOA", "M"],
  ["QDA", "SINH", "M"], ["QDA", "SU", "M"], ["QDA", "DIA", "M"], ["QDA", "ANH", "D"],
  ["BCA", "VAN", "D"], ["BCA", "TOAN", "M"], ["BCA", "SU", "M"], ["BCA", "ANH", "D"],
  ["BCA", "LY", "M"], ["BCA", "HOA", "M"], ["BCA", "SINH", "M"], ["BCA", "DIA", "M"],
  ["VACT", "VIETNAMESE", "M"], ["VACT", "ANH", "D"], ["VACT", "TOAN", "M"], ["VACT", "SCIENTIFIC_THINKING", "D"],
  ["SP2E", "TOAN", "D"], ["SP2E", "VAN", "D"], ["SP2E", "LY", "D"], ["SP2E", "HOA", "D"], ["SP2E", "SINH", "D"],
  ["SP2E", "DIA", "D"], ["SP2E", "SU", "D"], ["SP2E", "ECON_LAW", "D"], ["SP2E", "ANH", "D"],
  ["SPT", "TOAN", "D"], ["SPT", "LY", "D"], ["SPT", "HOA", "D"], ["SPT", "SINH", "D"], ["SPT", "SU", "D"],
  ["SPT", "DIA", "D"], ["SPT", "ECON_LAW", "D"], ["SPT", "TECH_INDUSTRY", "D"], ["SPT", "TECH_AGRICULTURE", "D"],
  ["SPT", "VAN", "D"], ["SPT", "ANH", "D"], ["SPT", "INFORMATICS", "D"],
  ["THPTQG", "TOAN", "D"], ["THPTQG", "LY", "D"], ["THPTQG", "HOA", "D"], ["THPTQG", "SINH", "D"], ["THPTQG", "SU", "D"],
  ["THPTQG", "DIA", "D"], ["THPTQG", "ECON_LAW", "D"], ["THPTQG", "TECH_INDUSTRY", "D"], ["THPTQG", "TECH_AGRICULTURE", "D"],
  ["THPTQG", "VAN", "D"], ["THPTQG", "ANH", "D"], ["THPTQG", "INFORMATICS", "D"],
  ["HSCA", "TOAN", "D"], ["HSCA", "LY", "D"], ["HSCA", "HOA", "D"], ["HSCA", "SINH", "D"], ["HSCA", "VAN", "D"], ["HSCA", "ANH", "D"],
  ["VSAT", "TOAN", "D"], ["VSAT", "LY", "D"], ["VSAT", "HOA", "D"], ["VSAT", "SINH", "D"], ["VSAT", "SU", "D"],
  ["VSAT", "DIA", "D"], ["VSAT", "VAN", "D"], ["VSAT", "ANH", "D"],
];

describe("Bảng đăng ký biểu mẫu theo môn (FTM R-1)", () => {
  it("bảng viết tay có đúng 75 cặp, không trùng", () => {
    expect(TABLE).toHaveLength(75);
    expect(new Set(TABLE.map(([kind, subject]) => `${kind}:${subject}`)).size).toBe(75);
  });

  it("subjectFormFor trả đúng dạng cho đúng 75 cặp canonical; chỉ MT của QDA, BCA, V-ACT (lát CR-2a, CR-2a′) đang bật", () => {
    for (const [kind, subject, form] of TABLE) {
      const entry = subjectFormFor(kind, subject);
      expect(entry, `${kind}:${subject}`).not.toBeNull();
      expect(entry!.form, `${kind}:${subject}`).toBe(form === "M" ? "MATRIX" : "ROWS");
      expect(entry!.enabled, `${kind}:${subject} enabled`).toBe(form === "M" && (kind === "QDA" || kind === "BCA" || kind === "VACT"));
    }
    const listed = listSubjectFormPairs().map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`);
    expect(listed.toSorted()).toEqual(TABLE.map(([kind, subject]) => `${kind}:${subject}`).toSorted());
  });

  it("cấu hình theo kỳ: nhập %, mức áp dụng, bảng kết hợp (R-1, R-8, R-9, R-10)", () => {
    const at = (kind: string, subject: string) => subjectFormFor(kind, subject)!;
    expect(at("HSA", "TOAN")).toMatchObject({ percentEntry: "NONE", enforcement: "FIXED_REQUIRED", combinedMatrix: "NONE", mapidFlow: "HSA_RULES", setSource: false });
    expect(at("QDA", "LY")).toMatchObject({ percentEntry: "NONE", enforcement: "DECLARED_REQUIRED", mapidFlow: "SCOPE_AND_QUOTA", setSource: true });
    expect(at("BCA", "TOAN")).toMatchObject({ enforcement: "FIXED_REFERENCE", setSource: true });
    expect(at("VACT", "TOAN")).toMatchObject({ percentEntry: "TWO_COLUMNS", enforcement: "REFERENCE_WITH_APPLY", gradeMixedColumn: true, flexibleSets: true });
    expect(at("VACT", "VIETNAMESE")).toMatchObject({ gradeOptional: true, flexibleSets: false, gradeMixedColumn: false });
    expect(at("VACT", "ANH")).toMatchObject({ form: "ROWS", percentEntry: "COUNT_OR_PERCENT" });
    expect(at("THPTQG", "LY").enforcement).toBe("DECLARED_REQUIRED");
    expect(at("THPTQG", "LY").percentEntry).toBe("NONE");
    for (const kind of ["SP2E", "SPT", "HSCA"]) expect(at(kind, "TOAN")).toMatchObject({ percentEntry: "COUNT_OR_PERCENT", combinedMatrix: "NONE" });
    expect(at("VSAT", "TOAN")).toMatchObject({ percentEntry: "COUNT_OR_PERCENT", combinedMatrix: "PART_LEVEL" });
    expect(TABLE.filter(([kind]) => kind !== "VSAT").every(([kind, subject]) => at(kind, subject).combinedMatrix === "NONE")).toBe(true);
  });

  it("thêm, tách dòng chỉ ở H-SCA Anh và V-ACT Tư duy khoa học; THPTQG Văn chỉ đọc (R-7)", () => {
    const rowsEditable = TABLE.filter(([kind, subject]) => subjectFormFor(kind, subject)!.rowsEditable === "ADD_AND_SPLIT").map(([kind, subject]) => `${kind}:${subject}`);
    expect(rowsEditable.toSorted()).toEqual(["HSCA:ANH", "VACT:SCIENTIFIC_THINKING"]);
    const notices = TABLE.filter(([kind, subject]) => subjectFormFor(kind, subject)!.readOnlyNotice !== undefined).map(([kind, subject]) => `${kind}:${subject}`);
    expect(notices).toEqual(["THPTQG:VAN"]);
  });

  it("ẩn khối lớp đúng bảng R-11, cả hai mã Kinh tế - Pháp luật", () => {
    const hidden = TABLE.filter(([kind, subject]) => subjectFormFor(kind, subject)!.hideGrade).map(([kind, subject]) => `${kind}:${subject}`);
    expect(hidden.toSorted()).toEqual(
      [
        "HSA:ANH",
        ...["SP2E", "SPT", "THPTQG"].flatMap((kind) => ["SU", "DIA", "ECON_LAW", "ANH"].map((subject) => `${kind}:${subject}`)),
        "VSAT:DIA", "VSAT:ANH", "VSAT:VAN",
      ].toSorted(),
    );
    expect(subjectFormFor("SPT", "GDKTPL")!.hideGrade).toBe(true);
    expect(subjectFormFor("SP2E", "GDKTPL")!.hideGrade).toBe(true);
  });

  it("giới hạn R-12 chỉ ở Khoa học HSA và QDA: Lý, Hóa, Sinh ít nhất 1 câu điền; Sử, Địa được 0", () => {
    for (const kind of ["HSA", "QDA"]) {
      for (const subject of ["LY", "HOA", "SINH"]) expect(subjectFormLimits(kind, subject)).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 1 });
      for (const subject of ["SU", "DIA"]) expect(subjectFormLimits(kind, subject)).toEqual({ setCount: { min: 1, max: 2, size: 3 }, shortAnswerMin: 0 });
      expect(subjectFormLimits(kind, "TOAN")).toBeNull();
    }
    expect(subjectFormLimits("BCA", "LY")).toBeNull();
    expect(subjectFormLimits("HSCA", "LY")).toBeNull();
  });

  it("aliasesOnlyWhereCanonical: bí danh tra cùng mục ở kỳ có mã canonical, ở kỳ khác trả null (AC-2, AC-55)", () => {
    for (const kind of ["SPT", "THPTQG"]) {
      expect(subjectFormFor(kind, "TIN")).toBe(subjectFormFor(kind, "INFORMATICS"));
      expect(subjectFormFor(kind, "GDKTPL")).toBe(subjectFormFor(kind, "ECON_LAW"));
      expect(subjectFormFor(kind, "CNCN")).toBe(subjectFormFor(kind, "TECH_INDUSTRY"));
      expect(subjectFormFor(kind, "CNNN")).toBe(subjectFormFor(kind, "TECH_AGRICULTURE"));
      expect(subjectFormFor(kind, "GDKTPL")).not.toBeNull();
    }
    expect(subjectFormFor("SP2E", "GDKTPL")).toBe(subjectFormFor("SP2E", "ECON_LAW"));
    expect(subjectFormFor("HSA", "TIN")).toBeNull();
    expect(subjectFormFor("SP2E", "CNCN")).toBeNull();
    expect(subjectFormFor("SP2E", "TIN")).toBeNull();
    expect(subjectFormFor("VSAT", "GDKTPL")).toBeNull();
  });

  it("unknownAndLegacyCodes: mã lạ, kỳ lạ, mã hệ thống cũ trả null; đổi tên hiển thị không đổi kết quả (AC-3)", () => {
    expect(subjectFormFor("TEST_NEW", "TOAN")).toBeNull();
    expect(subjectFormFor("HSA", "NEW_SUBJECT")).toBeNull();
    expect(subjectFormFor("HSA", "MATH")).toBeNull();
    expect(subjectFormFor("QDA", "LITERATURE")).toBeNull();
    expect(subjectFormFor("HSA", "toan")).toBeNull();
    expect(subjectFormFor("", "")).toBeNull();
  });

  it("bí danh không đổi mã của người gọi và không trả mã đã đổi", () => {
    const entry = subjectFormFor("SPT", "GDKTPL")!;
    expect(Object.values(entry)).not.toContain("ECON_LAW");
  });

  it("chỗ cắm kiểm thử bật một mục rồi trả lại như cũ; không đụng mục khác", () => {
    const restore = patchSubjectFormEntry("HSA", "LY", { enabled: true });
    expect(subjectFormFor("HSA", "LY")!.enabled).toBe(true);
    expect(subjectFormFor("HSA", "HOA")!.enabled).toBe(false);
    restore();
    expect(subjectFormFor("HSA", "LY")!.enabled).toBe(false);
  });
});
