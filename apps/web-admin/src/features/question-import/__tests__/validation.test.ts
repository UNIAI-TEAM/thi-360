import { describe, expect, it } from "vitest";
import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference } from "@/features/content/types";
import { sheetFromTable } from "../files";
import { checkMapping, normalizeHeader, suggestMapping } from "../mapping";
import dictionaryFixture from "../mock/import-dictionary.json";
import sampleSheet from "../mock/sample-sheet.json";
import templateFixture from "../mock/template.json";
import type { ColumnMapping, ImportDictionary, ImportRowResult } from "../types";
import { bracesBalanced, parseRubric, summarizeImport, validateImportRows } from "../validation";

const reference = referenceFixture as ContentReference;
const dictionary = dictionaryFixture as ImportDictionary;
const sheet = sheetFromTable(sampleSheet.rows);
const existingQuestions = [
  { id: "q-0004", code: "CH-000004", sourceCode: "NGUON-0004" },
  { id: "q-0006", code: "CH-000006", sourceCode: "NGUON-0006" },
  { id: "q-0008", code: "CH-000008", sourceCode: "NGUON-0008" },
];

function keyOf(header: string): string {
  const column = sheet.columns.find((candidate) => candidate.header === header);
  if (!column) throw new Error(`no column ${header}`);
  return column.key;
}

function run(mapping: ColumnMapping, rows = sheet.rows): ImportRowResult[] {
  return validateImportRows({ columns: sheet.columns, rows, mapping, reference, existingQuestions, dictionary });
}

const suggested = suggestMapping(sheet.columns, dictionary.fieldAliases, dictionary.ignoreHeaders);
const withKnowledge: ColumnMapping = { ...suggested, [keyOf("Mã KT")]: "knowledge" };

function row(results: ImportRowResult[], rowNumber: number): ImportRowResult {
  const found = results.find((result) => result.rowNumber === rowNumber);
  if (!found) throw new Error(`no row ${rowNumber}`);
  return found;
}

function codes(result: ImportRowResult, severity: "error" | "warning" = "error"): string[] {
  return result.issues.filter((issue) => issue.severity === severity).map((issue) => issue.code);
}

describe("ánh xạ cột", () => {
  it("chuẩn hóa tên cột không phân biệt dấu, hoa thường, ký tự phân cách", () => {
    expect(normalizeHeader("Mã nguồn")).toBe("ma nguon");
    expect(normalizeHeader("MA_NGUON")).toBe("ma nguon");
    expect(normalizeHeader("Kiến thức (MAPID)")).toBe("kien thuc mapid");
    expect(normalizeHeader("Đáp án")).toBe("dap an");
  });

  it("gợi ý theo bản vẽ: Mã KT chưa ánh xạ, Ghi chú nội bộ bỏ qua, các cột còn lại tự khớp", () => {
    expect(suggested[keyOf("Mã nguồn")]).toBe("sourceCode");
    expect(suggested[keyOf("Đáp án")]).toBe("correctAnswer");
    expect(suggested[keyOf("A")]).toBe("optionA");
    expect(suggested[keyOf("Lớp")]).toBe("grade");
    expect(suggested[keyOf("Mã KT")]).toBe("UNMAPPED");
    expect(suggested[keyOf("Ghi chú nội bộ")]).toBe("IGNORE");
    const check = checkMapping(sheet.columns, suggested);
    expect(check.valid).toBe(true);
    expect(check.unmappedCount).toBe(1);
    expect(check.ignoredCount).toBe(1);
    expect(check.mappedCount).toBe(14);
  });

  it("một trường ánh xạ từ hai cột là lỗi; thiếu trường bắt buộc là lỗi", () => {
    const twice = { ...suggested, [keyOf("Lời giải")]: "content" as const };
    const check = checkMapping(sheet.columns, twice);
    expect(check.valid).toBe(false);
    expect(check.duplicateFields).toEqual(["content"]);
    expect([...check.duplicateColumnKeys].sort()).toEqual([keyOf("Nội dung"), keyOf("Lời giải")].sort());

    const missing = { ...suggested, [keyOf("Mức độ")]: "IGNORE" as const };
    expect(checkMapping(sheet.columns, missing).missingRequired).toEqual(["difficulty"]);
  });

  it("tệp mẫu tự khớp đủ mọi cột", () => {
    const template = sheetFromTable(templateFixture.rows);
    const mapping = suggestMapping(template.columns, dictionary.fieldAliases, dictionary.ignoreHeaders);
    expect(Object.values(mapping)).not.toContain("UNMAPPED");
    expect(checkMapping(template.columns, mapping).valid).toBe(true);
  });
});

describe("validateImportRows", () => {
  // UNI-2599: dòng 19 (Tự luận) nay hợp lệ, nhập ở Nháp kèm cảnh báo chưa có tiêu chí chấm.
  it("bảng mẫu với ánh xạ gợi ý: 8 lỗi, 3 trùng, 15 hợp lệ (gồm 1 câu chùm, 2 câu con)", () => {
    const summary = summarizeImport(run(suggested));
    expect(summary).toEqual({ total: 26, valid: 15, error: 8, duplicate: 3, validSets: 1, validSetChildren: 2 });
  });

  it("câu hợp lệ đọc đủ phương án, đáp án, phân loại và nhãn", () => {
    const results = run(withKnowledge);
    const first = row(results, 2);
    expect(first.status).toBe("VALID");
    expect(first.draft.type).toBe("SINGLE_CHOICE");
    expect(first.draft.options.map((option) => [option.letter, option.correct])).toEqual([
      ["A", false],
      ["B", false],
      ["C", true],
      ["D", false],
    ]);
    expect(first.draft).toMatchObject({
      subjectCode: "MATH",
      gradeCode: "GRADE_11",
      difficultyCode: "COMPREHENSION",
      tagCodes: ["HSA_2025"],
    });
    expect(first.draft.knowledge).toEqual([
      { nodeId: "kn-1D4-2-3", code: "1D4-2-3", name: "Dạng vô định 0/0", primary: true },
    ]);

    expect(row(results, 3).draft.acceptedAnswers).toEqual(["10"]);
    expect(row(results, 4).draft.statements.map((statement) => statement.answer)).toEqual([true, false, true]);
    expect(row(results, 9).draft.options.filter((option) => option.correct).map((option) => option.letter)).toEqual([
      "A",
      "C",
    ]);
    expect(row(results, 23).draft.acceptedAnswers).toEqual(["0,5", "1/2"]);
  });

  it("nhận ra từng loại lỗi của bảng mẫu", () => {
    const results = run(withKnowledge);
    expect(codes(row(results, 5))).toEqual(["REQUIRED"]);
    expect(row(results, 5).issues[0]).toMatchObject({ field: "correctAnswer", column: "Đáp án" });
    expect(codes(row(results, 12))).toEqual(["DUPLICATE_IN_FILE"]);
    expect(row(results, 12).issues[0]).toMatchObject({ value: "TOAN11-0002", relatedRow: 3 });
    expect(codes(row(results, 13))).toEqual(["DIFFICULTY_UNKNOWN"]);
    expect(codes(row(results, 14))).toEqual(["KNOWLEDGE_UNKNOWN"]);
    expect(row(results, 14).issues[0]).toMatchObject({ value: "1D4-9-9", subjectCode: "MATH", column: "Mã KT" });
    expect(codes(row(results, 15))).toEqual(["SET_PASSAGE_MISSING"]);
    expect(codes(row(results, 16))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 17))).toEqual(["FORMULA_INVALID"]);
    expect(codes(row(results, 18))).toEqual(["SUBJECT_UNKNOWN"]);
    expect(codes(row(results, 22))).toEqual(["GRADE_UNKNOWN"]);
    expect(summarizeImport(results).error).toBe(9);
  });

  it("thiếu kiến thức và nhãn lạ chỉ là cảnh báo", () => {
    const results = run(withKnowledge);
    expect(row(results, 20).status).toBe("VALID");
    expect(codes(row(results, 20), "warning")).toEqual(["KNOWLEDGE_MISSING"]);
    expect(row(results, 25).status).toBe("VALID");
    expect(codes(row(results, 25), "warning")).toEqual(["TAG_UNKNOWN"]);
  });

  it("Mã nguồn trùng ngân hàng là trùng, kèm câu đã có", () => {
    const results = run(withKnowledge);
    expect(row(results, 10)).toMatchObject({ status: "DUPLICATE", duplicateOf: { id: "q-0004", code: "CH-000004" } });
    expect(row(results, 11).status).toBe("DUPLICATE");
    expect(row(results, 27).status).toBe("DUPLICATE");
  });

  it("câu con lấy phân loại và kiến thức của dòng ngữ liệu khi để trống", () => {
    const results = run(withKnowledge);
    const passage = row(results, 6);
    expect(passage).toMatchObject({ isSetPassage: true, status: "VALID" });
    const child = row(results, 7);
    expect(child).toMatchObject({ parentRowNumber: 6, status: "VALID" });
    expect(child.draft).toMatchObject({ subjectCode: "MATH", gradeCode: "GRADE_11", difficultyCode: "COMPREHENSION" });
    expect(child.draft.knowledge.map((ref) => ref.code)).toEqual(["1D4-3-1"]);
    // Câu con tự ghi Mức độ thì giữ giá trị của nó.
    expect(row(results, 8).draft.difficultyCode).toBe("RECOGNITION");
  });

  it("ngữ liệu lỗi kéo câu con lỗi theo; ngữ liệu không còn câu con hợp lệ là lỗi", () => {
    const header = sampleSheet.rows[0]!;
    const table = [
      header,
      ["S-1", "Câu chùm", "Ngữ liệu", "", "", "", "", "", "", "Toan", "11", "Nhận biết", "", "", "S", ""],
      ["S-2", "Trắc nghiệm 1 đáp án", "Câu con", "1", "2", "", "", "A", "", "", "", "", "", "", "S", ""],
      ["T-1", "Câu chùm", "Ngữ liệu 2", "", "", "", "", "", "", "Toán", "11", "Nhận biết", "", "", "T", ""],
      ["T-2", "Trắc nghiệm 1 đáp án", "Câu con lỗi", "1", "2", "", "", "", "", "", "", "", "", "", "T", ""],
    ];
    const local = sheetFromTable(table);
    const results = validateImportRows({
      columns: local.columns,
      rows: local.rows,
      mapping: suggestMapping(local.columns, dictionary.fieldAliases, dictionary.ignoreHeaders),
      reference,
      existingQuestions: [],
      dictionary,
    });
    expect(codes(row(results, 3))).toEqual(["SET_PASSAGE_INVALID"]);
    expect(codes(row(results, 4))).toEqual(["SET_NO_CHILDREN"]);
    expect(codes(row(results, 5))).toEqual(["REQUIRED"]);
  });

  it("ngữ liệu trùng ngân hàng thì câu con trùng theo", () => {
    const header = sampleSheet.rows[0]!;
    const local = sheetFromTable([
      header,
      ["OLD-SET", "Câu chùm", "Ngữ liệu", "", "", "", "", "", "", "Toán", "11", "Nhận biết", "", "", "S", ""],
      ["OLD-SET-1", "Trắc nghiệm 1 đáp án", "Câu con", "1", "2", "", "", "A", "", "", "", "", "", "", "S", ""],
    ]);
    const results = validateImportRows({
      columns: local.columns,
      rows: local.rows,
      mapping: suggestMapping(local.columns, dictionary.fieldAliases, dictionary.ignoreHeaders),
      reference,
      existingQuestions: [{ id: "q-set", code: "CH-000099", sourceCode: "old-set" }],
      dictionary,
    });
    expect(results.map((result) => result.status)).toEqual(["DUPLICATE", "DUPLICATE"]);
  });

  it("dạng Kéo thả chưa nhập được từ tệp; trắc nghiệm 1 đáp án không nhận hai đáp án", () => {
    const header = sampleSheet.rows[0]!;
    const local = sheetFromTable([
      header,
      ["K-1", "Kéo thả", "Kéo", "", "", "", "", "A", "", "Toán", "11", "Nhận biết", "", "", "", ""],
      ["K-2", "Trắc nghiệm 1 đáp án", "Hai đáp án", "1", "2", "3", "", "A, B", "", "Toán", "11", "Nhận biết", "", "", "", ""],
      ["K-3", "Trắc nghiệm 1 đáp án", "Một phương án", "1", "", "", "", "A", "", "Toán", "11", "Nhận biết", "", "", "", ""],
      ["K-4", "Đúng / Sai", "Thiếu ý", "a", "b", "", "", "Đ", "", "Toán", "11", "Nhận biết", "", "", "", ""],
    ]);
    const results = validateImportRows({
      columns: local.columns,
      rows: local.rows,
      mapping: suggestMapping(local.columns, dictionary.fieldAliases, dictionary.ignoreHeaders),
      reference,
      existingQuestions: [],
      dictionary,
    });
    expect(codes(row(results, 2))).toEqual(["TYPE_NOT_IMPORTABLE"]);
    expect(codes(row(results, 3))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 4))).toEqual(["OPTIONS_TOO_FEW"]);
    expect(codes(row(results, 5))).toEqual(["ANSWER_INVALID"]);
  });

  it("Tự luận: không có cột Tiêu chí chấm thì cảnh báo, cột Đáp án là bài làm mẫu", () => {
    const essay = row(run(withKnowledge), 19);
    expect(essay.status).toBe("VALID");
    expect(essay.draft.type).toBe("ESSAY");
    expect(codes(essay, "warning")).toEqual(["RUBRIC_MISSING"]);
    expect(essay.draft.rubric).toEqual([]);
    expect(essay.draft.sampleAnswer).toMatch(/^Xét f\(x\)/);
  });

  it("Tự luận: đọc cột Tiêu chí chấm; viết sai là lỗi", () => {
    const header = [...sampleSheet.rows[0]!, "Tiêu chí chấm"];
    const local = sheetFromTable([
      header,
      ["E-1", "Tự luận", "Viết đoạn văn", "", "", "", "", "", "", "Ngữ văn", "12", "Vận dụng", "", "", "", "", "Mở bài: 0,25; Thân bài (1)\nKết bài = 0,75"],
      ["E-2", "Tự luận", "Viết đoạn văn", "", "", "", "", "", "", "Ngữ văn", "12", "Vận dụng", "", "", "", "", "Mở bài; Thân bài: 1"],
    ]);
    const mapping = suggestMapping(local.columns, dictionary.fieldAliases, dictionary.ignoreHeaders);
    expect(Object.values(mapping)).toContain("rubric");
    const results = validateImportRows({ columns: local.columns, rows: local.rows, mapping, reference, existingQuestions: [], dictionary });
    expect(row(results, 2).status).toBe("VALID");
    expect(row(results, 2).draft.rubric).toEqual([
      { description: "Mở bài", points: 0.25 },
      { description: "Thân bài", points: 1 },
      { description: "Kết bài", points: 0.75 },
    ]);
    expect(codes(row(results, 3))).toEqual(["RUBRIC_INVALID"]);
    expect(row(results, 3).issues.find((issue) => issue.code === "RUBRIC_INVALID")).toMatchObject({
      field: "rubric",
      value: "Mở bài",
      column: "Tiêu chí chấm",
    });
  });

  it("đọc tiêu chí chấm: mô tả có dấu hai chấm, điểm 0 là sai", () => {
    expect(parseRubric("Ý 1: nêu luận điểm: 0,5")).toEqual({
      criteria: [{ description: "Ý 1: nêu luận điểm", points: 0.5 }],
      invalid: null,
    });
    expect(parseRubric("Ý 1: 0").invalid).toBe("Ý 1: 0");
    expect(parseRubric("")).toEqual({ criteria: [], invalid: null });
  });

  it("kiểm tra dấu ngoặc nhọn của công thức", () => {
    expect(bracesBalanced("$\\frac{1}{2}$")).toBe(true);
    expect(bracesBalanced("$\\frac{1}{x$")).toBe(false);
    expect(bracesBalanced("}{")).toBe(false);
    expect(bracesBalanced("\\{ a")).toBe(true);
  });
});
