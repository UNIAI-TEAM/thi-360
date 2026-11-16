import { beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { MockApiError } from "@/lib/mock/content-mock";
import {
  commitImport,
  downloadQuestionTemplate,
  listImportHistory,
  uploadQuestionFile,
  validateImport,
} from "../api";
import { importHistoryStore } from "../api.mock";
import { buildErrorReportTable, collectErrorLines, errorReportFileName } from "../error-report";
import { checkImportFile, parseCsv, readFileText, sheetFromTable } from "../files";
import type { ColumnMapping, ImportUpload } from "../types";

const BOM = String.fromCharCode(0xfeff);

function file(name: string, content = "x"): File {
  return new File([content], name);
}

describe("đọc tệp", () => {
  it("kiểm tra loại và cỡ tệp", () => {
    expect(checkImportFile({ name: "a.xlsx", size: 10 })).toBeNull();
    expect(checkImportFile({ name: "a.CSV", size: 10 })).toBeNull();
    expect(checkImportFile({ name: "a.xlsm", size: 10 })).toBe("IMPORT_MACRO_NOT_ALLOWED");
    expect(checkImportFile({ name: "a.pdf", size: 10 })).toBe("IMPORT_INVALID_FILE");
    expect(checkImportFile({ name: "a.xlsx", size: 0 })).toBe("IMPORT_EMPTY_FILE");
    expect(checkImportFile({ name: "a.xlsx", size: 6 * 1024 * 1024 })).toBe("IMPORT_FILE_TOO_LARGE");
  });

  it("đọc CSV có ngoặc kép, xuống dòng trong ô, BOM và dấu chấm phẩy", () => {
    expect(parseCsv(BOM + 'a,b\r\n"x, y","say ""hi"""\n')).toEqual([
      ["a", "b"],
      ["x, y", 'say "hi"'],
    ]);
    expect(parseCsv('a;b\n1;"2\n3"')).toEqual([
      ["a", "b"],
      ["1", "2\n3"],
    ]);
  });

  it("bảng thành cột và dòng, bỏ dòng trống, số dòng tính theo tệp", () => {
    const sheet = sheetFromTable([["A", "B"], ["1", "2"], ["", ""], ["3", ""]]);
    expect(sheet.columns).toEqual([
      { key: "c0", header: "A", example: "1" },
      { key: "c1", header: "B", example: "2" },
    ]);
    expect(sheet.rows.map((row) => row.rowNumber)).toEqual([2, 4]);
  });

  it("file lỗi có dòng tên cột và mỗi lỗi một dòng", () => {
    const table = buildErrorReportTable(
      [{ rowNumber: 5, sourceCode: "X-1", issue: { code: "REQUIRED", severity: "error", field: "correctAnswer", column: "Đáp án" } }],
      { colRow: "Row", colColumn: "Column", colSourceCode: "Source", colError: "Error", colFix: "Fix" },
      () => ({ message: "Missing", fix: "Fill it" }),
    );
    expect(table).toEqual([
      ["Row", "Column", "Source", "Error", "Fix"],
      ["5", "Đáp án", "X-1", "Missing", "Fill it"],
    ]);
    expect(errorReportFileName("ngan-hang-toan-11.xlsx")).toBe("ngan-hang-toan-11-errors.csv");
  });
});

describe("API nhập câu hỏi (dữ liệu mẫu)", () => {
  beforeEach(() => {
    questionStore.reset();
    importHistoryStore.reset();
    window.history.replaceState(null, "", "/questions/import");
  });

  async function uploadSample(): Promise<ImportUpload> {
    return uploadQuestionFile(file("ngan-hang-toan-11.xlsx"));
  }

  it("tệp .xlsx đọc thành bảng mẫu kèm ánh xạ gợi ý", async () => {
    const upload = await uploadSample();
    expect(upload).toMatchObject({ fileName: "ngan-hang-toan-11.xlsx", fileType: "XLSX", sheetName: "Câu hỏi", rowCount: 26 });
    expect(upload.columns).toHaveLength(16);
    expect(Object.values(upload.suggestedMapping)).toContain("UNMAPPED");
  });

  it("từ chối tệp sai loại với mã lỗi", async () => {
    await expect(uploadQuestionFile(file("a.pdf"))).rejects.toMatchObject({ status: 422, code: "IMPORT_INVALID_FILE" });
    await expect(uploadQuestionFile(file("a.csv", "Mã nguồn,Nội dung\n"))).rejects.toMatchObject({
      code: "IMPORT_EMPTY_FILE",
    });
  });

  it("tệp mẫu tải về đọc lại được và nhập trọn vẹn", async () => {
    const template = await downloadQuestionTemplate();
    expect(template.filename).toBe("mau-nhap-cau-hoi.csv");
    const text = await readFileText(template.blob);
    const upload = await uploadQuestionFile(file(template.filename, text.replace(BOM, "")));
    expect(upload).toMatchObject({ fileType: "CSV", sheetName: null, rowCount: 8 });
    const validation = await validateImport({ uploadId: upload.id, mapping: upload.suggestedMapping });
    expect(validation.summary).toMatchObject({ total: 8, valid: 8, error: 0, duplicate: 0, validSets: 1, validSetChildren: 2 });
    const result = await commitImport({
      uploadId: upload.id,
      mapping: upload.suggestedMapping,
      duplicatePolicy: "SKIP",
      skipErrors: false,
    });
    expect(result.createdCount).toBe(6);
    // Câu Tự luận có tiêu chí chấm: điểm của câu bằng tổng điểm tiêu chí.
    const essay = questionStore.all().find((question) => question.sourceCode === "MAU-0008");
    expect(essay).toMatchObject({ type: "ESSAY", points: 2 });
    expect(essay?.body).toMatchObject({ type: "ESSAY" });
    expect(essay?.points).toBe(2);
    if (essay?.body.type === "ESSAY") expect(essay.body.rubric.map((criterion) => criterion.points)).toEqual([0.25, 0.25, 1, 0.5]);
  });

  it("nhập câu hợp lệ thành Nháp, bỏ qua câu trùng, ghi lịch sử", async () => {
    const before = questionStore.all().length;
    const upload = await uploadSample();
    const result = await commitImport({
      uploadId: upload.id,
      mapping: upload.suggestedMapping,
      duplicatePolicy: "SKIP",
      skipErrors: true,
    });
    // 15 dòng hợp lệ = 12 câu đơn (gồm 1 câu Tự luận chưa có tiêu chí chấm, UNI-2599) + 1 câu chùm có 2 câu con.
    expect(result).toMatchObject({
      createdCount: 13,
      singleCount: 12,
      setCount: 1,
      setChildCount: 2,
      skippedDuplicateCount: 3,
      newVersionCount: 0,
      errorRowCount: 8,
      missingKnowledgeCount: 13,
    });
    expect(result.errors).toHaveLength(8);
    expect(questionStore.all().length).toBe(before + 13);
    const essay = questionStore.all().find((question) => question.sourceCode === "TOAN11-0016");
    expect(essay).toMatchObject({ type: "ESSAY", status: "DRAFT", points: 1 });
    expect(essay?.body).toMatchObject({ type: "ESSAY", rubric: [] });
    expect(essay?.points).toBe(1);
    const imported = questionStore.all().filter((question) => question.sourceCode?.startsWith("TOAN11-"));
    expect(imported.length).toBeGreaterThan(0);
    expect(imported.every((question) => question.status === "DRAFT" && question.version === 1)).toBe(true);
    const set = questionStore.all().find((question) => question.sourceCode === "CH-01");
    expect(set?.type).toBe("QUESTION_SET");
    expect(set?.children).toHaveLength(2);
    expect(set?.body).toMatchObject({ type: "QUESTION_SET", childIds: set?.children.map((child) => child.id) });
    const history = await listImportHistory();
    expect(history[0]?.id).toBe(result.id);
    expect(collectErrorLines([])).toEqual([]);
  });

  it("chọn tạo phiên bản mới thì câu trùng lên phiên bản, về Nháp", async () => {
    const upload = await uploadSample();
    const mapping: ColumnMapping = upload.suggestedMapping;
    const result = await commitImport({ uploadId: upload.id, mapping, duplicatePolicy: "NEW_VERSION", skipErrors: true });
    expect(result.newVersionCount).toBe(3);
    const updated = questionStore.get("q-0006");
    expect(updated).toMatchObject({ status: "DRAFT", sourceCode: "NGUON-0006", code: "CH-000006" });
    expect(updated?.version).toBeGreaterThan(1);
  });

  it("không bỏ qua dòng lỗi thì máy chủ từ chối 422 IMPORT_HAS_ERRORS", async () => {
    const upload = await uploadSample();
    const promise = commitImport({
      uploadId: upload.id,
      mapping: upload.suggestedMapping,
      duplicatePolicy: "SKIP",
      skipErrors: false,
    });
    await expect(promise).rejects.toBeInstanceOf(MockApiError);
    await expect(promise).rejects.toMatchObject({ status: 422, code: "IMPORT_HAS_ERRORS" });
  });

  it("lịch sử theo ?mockState", async () => {
    window.history.replaceState(null, "", "/questions/import?mockState=empty");
    expect(await listImportHistory()).toEqual([]);
    window.history.replaceState(null, "", "/questions/import?mockState=error");
    await expect(listImportHistory()).rejects.toMatchObject({ status: 500 });
  });
});
