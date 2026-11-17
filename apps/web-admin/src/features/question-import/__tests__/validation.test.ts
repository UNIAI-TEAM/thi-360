import { describe, expect, it } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { sheetFromTable } from "../files";
import { checkMapping, normalizeHeader, suggestMapping } from "../mapping";
import dictionaryFixture from "./fixtures/import-dictionary.json";
import sampleSheet from "./fixtures/sample-sheet.json";
import templateFixture from "./fixtures/template.json";
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
  // UNI-2599: dòng 19 (Tự luận) nay hợp lệ, nhập ở Nháp kèm cảnh báo chưa có hướng dẫn chấm.
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

  it("mã dạng cũ DRAG_DROP thành Ghép đôi (D12); trắc nghiệm 1 đáp án không nhận hai đáp án", () => {
    const header = sampleSheet.rows[0]!;
    const local = sheetFromTable([
      header,
      ["K-1", "DRAG_DROP", "Kéo", "", "", "", "", "A", "", "Toán", "11", "Nhận biết", "", "", "", ""],
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
    expect(row(results, 2).draft.type).toBe("MATCHING");
    expect(codes(row(results, 2))).toEqual(["OPTIONS_TOO_FEW", "OPTIONS_TOO_FEW", "ANSWER_INVALID"]);
    expect(codes(row(results, 3))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 4))).toEqual(["OPTIONS_TOO_FEW"]);
    expect(codes(row(results, 5))).toEqual(["ANSWER_INVALID"]);
  });

  it("Tự luận: không có cột Tiêu chí chấm thì cảnh báo, cột Đáp án là bài làm mẫu", () => {
    const essay = row(run(withKnowledge), 19);
    expect(essay.status).toBe("VALID");
    expect(essay.draft.type).toBe("ESSAY");
    expect(codes(essay, "warning")).toEqual(["GRADING_GUIDE_MISSING"]);
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

describe("nhập 8 dạng câu theo cột của spec 4.6", () => {
  const header = templateFixture.rows[0]!;
  const base: Record<string, string> = { "Môn": "Toán", "Lớp": "11", "Mức độ": "Nhận biết", "Kiến thức (MAPID)": "1D4-1-1" };

  /** Dòng của tệp mẫu theo tên cột; cột không nêu thì để trống (môn, lớp, mức độ có sẵn). */
  function line(cells: Record<string, string>): string[] {
    const merged = { ...base, ...cells };
    return header.map((name) => merged[name] ?? "");
  }

  function check(rows: string[][]): ImportRowResult[] {
    const local = sheetFromTable([header, ...rows]);
    const mapping = suggestMapping(local.columns, dictionary.fieldAliases, dictionary.ignoreHeaders);
    return validateImportRows({ columns: local.columns, rows: local.rows, mapping, reference, existingQuestions: [], dictionary });
  }

  it("tệp mẫu có đủ 8 dạng và nhập được trọn vẹn", () => {
    const results = check(templateFixture.rows.slice(1));
    expect(results.map((result) => result.status)).toEqual(Array(10).fill("VALID"));
    expect(summarizeImport(results)).toMatchObject({ total: 10, valid: 10, error: 0, validSets: 1, validSetChildren: 2 });
    expect(new Set(results.map((result) => result.draft.type))).toEqual(
      new Set(["SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER", "CLOZE", "TRUE_FALSE", "MATCHING", "ESSAY", "QUESTION_SET"]),
    );

    const [single, multiple, , cloze, trueFalse, matching, essay] = results;
    expect(single!.draft).toMatchObject({ points: 0.75, taskTypeCode: "CALCULATION", commonMistakes: "Quên chia cho n nên chọn 3." });
    expect(single!.draft.options.map((option) => [option.letter, option.correct])).toEqual([
      ["A", false],
      ["B", true],
      ["C", false],
      ["D", false],
    ]);
    expect(multiple!.draft.scoringRule).toBe("PARTIAL");
    expect(cloze!.draft.blanks).toEqual([
      { id: "b1", answers: ["Hà Nội"], points: 1 },
      { id: "b2", answers: ["Hồng", "sông Hồng"], points: 0.5 },
    ]);
    expect(trueFalse!.draft.trueFalseScoring).toEqual({ method: "COUNT_TABLE", points: [0.1, 0.25, 0.5, 1] });
    expect(trueFalse!.draft.statements.map((statement) => statement.answer)).toEqual([true, false, true, false]);
    expect(matching!.draft.matching).toMatchObject({
      reuseRight: false,
      pairs: [
        { leftKey: "1", rightKey: "A" },
        { leftKey: "2", rightKey: "B" },
        { leftKey: "3", rightKey: "C" },
      ],
    });
    expect(matching!.draft.matching?.right.map((item) => item.content)).toEqual(["sin x + C", "eˣ + C", "ln|x| + C", "x²/2 + C"]);
    expect(essay!.draft.rubric).toHaveLength(4);
  });

  it("Trắc nghiệm: tối đa 10 phương án, cách chấm Nhiều đáp án chỉ nhận ALL_OR_NOTHING hoặc PARTIAL", () => {
    const eleven = Array.from({ length: 11 }, (_, index) => `${String.fromCharCode(65 + index)}. phương án ${index}`).join("\n");
    const results = check([
      line({ "Dạng câu": "Trắc nghiệm 1 đáp án", "Nội dung": "q", "Lựa chọn": eleven, "Đáp án": "A" }),
      line({ "Dạng câu": "Trắc nghiệm nhiều đáp án", "Nội dung": "q", "Lựa chọn": "A. x\nB. y", "Đáp án": "A, B", "Cách chấm": "TỪNG_PHẦN" }),
      line({ "Dạng câu": "Trắc nghiệm nhiều đáp án", "Nội dung": "q", "Lựa chọn": "A. x\nB. y\nJ. z", "Đáp án": "A, J" }),
    ]);
    expect(codes(row(results, 2))).toEqual(["OPTIONS_TOO_MANY"]);
    expect(row(results, 2).issues.find((issue) => issue.code === "OPTIONS_TOO_MANY")).toMatchObject({ field: "options", max: 10 });
    expect(codes(row(results, 3))).toEqual(["SCORING_INVALID"]);
    expect(row(results, 4).status).toBe("VALID");
  });

  it("Đúng/Sai: tối đa 8 mệnh đề; cách chấm theo từng ý phải cộng đúng điểm câu, bảng không giảm và cuối bằng điểm câu", () => {
    const nine = Array.from({ length: 9 }, (_, index) => `${String.fromCharCode(97 + index)}. ý ${index}`).join("\n");
    const two = "a. một\nb. hai";
    const tf = (cells: Record<string, string>) => line({ "Dạng câu": "Đúng / Sai", "Nội dung": "q", "Lựa chọn": two, "Đáp án": "Đ, S", ...cells });
    const results = check([
      line({ "Dạng câu": "Đúng / Sai", "Nội dung": "q", "Lựa chọn": nine, "Đáp án": "Đ, Đ, Đ, Đ, Đ, Đ, Đ, Đ, Đ" }),
      tf({ "Điểm": "1", "Cách chấm": "PER_STATEMENT: 0,25 / 0,5" }),
      tf({ "Điểm": "1", "Cách chấm": "PER_STATEMENT: 0,5 / 0,5" }),
      tf({ "Điểm": "1", "Cách chấm": "COUNT_TABLE: 0,5 / 0,25" }),
      tf({ "Điểm": "1", "Cách chấm": "COUNT_TABLE: 0,5 / 0,9" }),
      tf({ "Điểm": "1", "Cách chấm": "COUNT_TABLE: 0,5 / 1" }),
      tf({ "Cách chấm": "COUNT_TABLE: 1" }),
      tf({ "Cách chấm": "THEO_Y: 1 / 1" }),
    ]);
    expect(codes(row(results, 2))).toEqual(["OPTIONS_TOO_MANY"]);
    expect(codes(row(results, 3))).toEqual(["SCORING_INVALID"]);
    expect(row(results, 4).draft.trueFalseScoring).toEqual({ method: "PER_STATEMENT", points: [0.5, 0.5] });
    expect(codes(row(results, 5))).toEqual(["SCORING_INVALID"]);
    expect(codes(row(results, 6))).toEqual(["SCORING_INVALID"]);
    expect(row(results, 7).status).toBe("VALID");
    expect(codes(row(results, 8))).toEqual(["SCORING_INVALID"]);
    expect(codes(row(results, 9))).toEqual(["SCORING_INVALID"]);
  });

  it("Điền khuyết: ô trùng hoặc quá 30 là lỗi, đáp án trỏ tới ô không có là lỗi, thiếu đáp án chỉ cảnh báo", () => {
    const cloze = (cells: Record<string, string>) => line({ "Dạng câu": "Điền khuyết", ...cells });
    const many = Array.from({ length: 31 }, (_, index) => `[[b${index}]]`).join(" ");
    const results = check([
      cloze({ "Nội dung": "A [[b1]] B [[b1]]", "Đáp án": "b1 = x" }),
      cloze({ "Nội dung": many, "Đáp án": "b0 = x" }),
      cloze({ "Nội dung": "A [[b1]] B [[b2]]", "Đáp án": "b1 = x\nb9 = y" }),
      cloze({ "Nội dung": "A [[b1]] B [[b2]]", "Đáp án": "b1 = x" }),
      cloze({ "Nội dung": "A [[b1]] B [[b2]]", "Đáp án": "b1 = x\nb2 = y", "Cách chấm": "b1 = 0\nb3 = 1" }),
      cloze({ "Nội dung": "A [[b1]]", "Đáp án": "b1 x" }),
      cloze({ "Nội dung": "Không có ô nào", "Đáp án": "" }),
    ]);
    expect(codes(row(results, 2))).toEqual(["CLOZE_MARKER_INVALID"]);
    expect(codes(row(results, 3))).toEqual(["CLOZE_MARKER_INVALID"]);
    expect(codes(row(results, 4))).toEqual(["ANSWER_INVALID"]);
    expect(row(results, 4).issues[0]).toMatchObject({ value: "b9" });
    expect(row(results, 5).status).toBe("VALID");
    expect(codes(row(results, 5), "warning")).toEqual(["ANSWER_MISSING"]);
    expect(row(results, 5).draft.blanks.map((blank) => blank.answers)).toEqual([["x"], []]);
    expect(codes(row(results, 6))).toEqual(["SCORING_INVALID", "SCORING_INVALID"]);
    expect(codes(row(results, 7))).toEqual(["ANSWER_INVALID"]);
    expect(row(results, 8).status).toBe("VALID");
    expect(codes(row(results, 8), "warning")).toEqual(["ANSWER_MISSING"]);
  });

  it("Điền khuyết không làm câu con của Bộ câu hỏi (D19)", () => {
    const results = check([
      line({ "Dạng câu": "Câu chùm", "Nội dung": "Ngữ liệu", "Mã chùm": "CH-1" }),
      line({ "Dạng câu": "Điền khuyết", "Nội dung": "A [[b1]]", "Đáp án": "b1 = x", "Mã chùm": "CH-1" }),
      line({ "Dạng câu": "Trắc nghiệm 1 đáp án", "Nội dung": "q", "Lựa chọn": "A. x\nB. y", "Đáp án": "A", "Mã chùm": "CH-1" }),
    ]);
    expect(codes(row(results, 3))).toEqual(["SET_CHILD_TYPE_INVALID"]);
    expect(row(results, 4).status).toBe("VALID");
    expect(row(results, 2).status).toBe("VALID");
  });

  it("Ghép đôi: hai cột khác số mục, cặp trỏ tới mục có thật, mỗi lựa chọn dùng một lần trừ khi ghi REUSE", () => {
    const match = (cells: Record<string, string>) =>
      line({
        "Dạng câu": "Ghép đôi",
        "Nội dung": "Ghép",
        "Lựa chọn": "1. hai\n2. ba",
        "Lựa chọn phải": "A. x\nB. y\nC. z",
        ...cells,
      });
    const results = check([
      match({ "Đáp án": "1 = A\n2 = B" }),
      match({ "Đáp án": "1 = A\n3 = B" }),
      match({ "Đáp án": "1 = D" }),
      match({ "Đáp án": "1 = A\n2 = A" }),
      match({ "Đáp án": "1 = A\n2 = A", "Cách chấm": "REUSE" }),
      match({ "Đáp án": "1 = A" }),
      match({ "Đáp án": "1 = A\n1 = B" }),
      match({ "Đáp án": "1 = A\n2 = B", "Cách chấm": "MỘT_LẦN" }),
      match({ "Lựa chọn phải": "", "Đáp án": "" }),
    ]);
    expect(row(results, 2).status).toBe("VALID");
    expect(row(results, 2).draft.matching?.reuseRight).toBe(false);
    expect(codes(row(results, 3))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 4))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 5))).toEqual(["ANSWER_INVALID"]);
    expect(row(results, 6).status).toBe("VALID");
    expect(row(results, 6).draft.matching?.reuseRight).toBe(true);
    expect(codes(row(results, 7), "warning")).toEqual(["ANSWER_MISSING"]);
    expect(row(results, 7).status).toBe("VALID");
    expect(codes(row(results, 8))).toEqual(["ANSWER_INVALID"]);
    expect(codes(row(results, 9))).toEqual(["SCORING_INVALID"]);
    expect(codes(row(results, 10))).toEqual(["OPTIONS_TOO_FEW"]);
    expect(row(results, 10).issues[0]).toMatchObject({ field: "matchRight" });
  });

  it("Ghép đôi: nhãn lặp ở cột Lựa chọn hoặc Lựa chọn phải là lỗi, không để hai mục chung một khóa", () => {
    const match = (cells: Record<string, string>) =>
      line({ "Dạng câu": "Ghép đôi", "Nội dung": "Ghép", "Lựa chọn": "1. hai\n2. ba", "Lựa chọn phải": "A. x\nB. y", ...cells });
    const results = check([
      match({ "Đáp án": "1 = A\n2 = B" }),
      match({ "Lựa chọn": "1. hai\n1. ba", "Đáp án": "1 = A" }),
      match({ "Lựa chọn phải": "A. x\nA. y", "Đáp án": "1 = A\n2 = A", "Cách chấm": "REUSE" }),
    ]);
    expect(row(results, 2).status).toBe("VALID");
    expect(codes(row(results, 3))).toEqual(["ENTRY_DUPLICATE"]);
    expect(row(results, 3).issues[0]).toMatchObject({ field: "options", value: "1" });
    expect(codes(row(results, 4))).toEqual(["ENTRY_DUPLICATE"]);
    expect(row(results, 4).issues[0]).toMatchObject({ field: "matchRight", value: "A" });
  });

  it("Điền khuyết: mã ô lặp ở cột Đáp án hoặc Cách chấm là lỗi, không để giá trị nào âm thầm thắng", () => {
    const cloze = (cells: Record<string, string>) => line({ "Dạng câu": "Điền khuyết", "Nội dung": "A [[b1]] B [[b2]]", ...cells });
    const results = check([
      cloze({ "Đáp án": "b1 = x\nb2 = y", "Cách chấm": "b1 = 1\nb2 = 1" }),
      cloze({ "Đáp án": "b1 = x\nb1 = z\nb2 = y" }),
      cloze({ "Đáp án": "b1 = x\nb2 = y", "Cách chấm": "b1 = 1\nb1 = 2\nb2 = 1" }),
    ]);
    expect(row(results, 2).status).toBe("VALID");
    expect(codes(row(results, 3))).toEqual(["ENTRY_DUPLICATE"]);
    expect(row(results, 3).issues[0]).toMatchObject({ field: "correctAnswer", value: "b1" });
    expect(codes(row(results, 4))).toEqual(["ENTRY_DUPLICATE"]);
    expect(row(results, 4).issues[0]).toMatchObject({ field: "scoring", value: "b1" });
  });

  it("Đúng/Sai: cách chấm toàn 0 khi cột Điểm trống không được tạo câu 0 điểm", () => {
    const tf = (cells: Record<string, string>) =>
      line({ "Dạng câu": "Đúng / Sai", "Nội dung": "q", "Lựa chọn": "a. một\nb. hai", "Đáp án": "Đ, S", ...cells });
    const results = check([
      tf({ "Cách chấm": "PER_STATEMENT: 0 / 0" }),
      tf({ "Cách chấm": "COUNT_TABLE: 0 / 0" }),
      tf({ "Cách chấm": "PER_STATEMENT: 0,5 / 0" }),
    ]);
    expect(codes(row(results, 2))).toEqual(["SCORING_INVALID"]);
    expect(codes(row(results, 3))).toEqual(["SCORING_INVALID"]);
    expect(row(results, 4).status).toBe("VALID");
    expect(row(results, 4).draft.trueFalseScoring).toEqual({ method: "PER_STATEMENT", points: [0.5, 0] });
  });

  it("Điểm, Dạng bài, công thức và số đáp án Trả lời ngắn", () => {
    const short = (cells: Record<string, string>) => line({ "Dạng câu": "Trả lời ngắn", "Nội dung": "q", ...cells });
    const twentyOne = Array.from({ length: 21 }, (_, index) => `đáp án ${index}`).join("\n");
    const results = check([
      short({ "Đáp án": "1", "Điểm": "0" }),
      short({ "Đáp án": "1", "Điểm": "0,75" }),
      short({ "Đáp án": "1", "Dạng bài": "Không có" }),
      short({ "Đáp án": "1", "Lựa chọn": "A. $\\frac{1}{2$" }),
      short({ "Đáp án": twentyOne }),
    ]);
    expect(codes(row(results, 2))).toEqual(["POINTS_INVALID"]);
    expect(row(results, 3).draft.points).toBe(0.75);
    expect(codes(row(results, 4))).toEqual(["TASK_TYPE_UNKNOWN"]);
    expect(codes(row(results, 5))).toEqual(["FORMULA_INVALID"]);
    expect(row(results, 5).issues[0]).toMatchObject({ field: "options" });
    expect(codes(row(results, 6))).toEqual(["ANSWER_INVALID"]);
  });
});
