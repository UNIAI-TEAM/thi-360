import { describe, expect, it } from "vitest";
import { ContentApiError } from "@/features/content/api-error";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import {
  commitImport,
  downloadQuestionTemplate,
  importApiErrorCode,
  listImportHistory,
  uploadQuestionFile,
  validateImport,
} from "../api";
import { buildErrorReportTable, errorReportFileName } from "../error-report";
import { checkImportFile, parseCsv, readFileText, sheetFromTable } from "../files";
import type { ColumnMapping } from "../types";

withRealApi();

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

describe("API nhập câu hỏi (nhánh thật, client giả)", () => {
  const BASE = "/api/v1/admin/question-imports";
  const callsTo = (method: string, path: string) =>
    fakeApi.calls.filter((call) => call.method === method && call.path === path);

  const apiUpload = {
    id: "upl-1",
    fileName: "ngan-hang-toan-11.xlsx",
    fileType: "XLSX",
    sheetName: "Câu hỏi",
    rowCount: 26,
    columns: [
      { key: "c1", header: "Mã nguồn", example: "TOAN11-0001" },
      { key: "c2", header: "Nội dung", example: "Tính giới hạn" },
    ],
    suggestedMapping: { c1: "sourceCode", c2: "content", c9: "KHONG-BIET" },
  };

  const history = (overrides: Record<string, unknown> = {}) => ({
    id: "imp-1",
    importedAt: "2026-11-17T01:00:00+07:00",
    importedBy: { id: "u-lan", name: "Lan" },
    fileName: "ngan-hang-toan-11.xlsx",
    createdCount: 13,
    singleCount: 12,
    setCount: 1,
    setChildCount: 2,
    skippedDuplicateCount: 3,
    newVersionCount: 0,
    errorRowCount: 8,
    missingKnowledgeCount: 13,
    durationMs: 120,
    errors: [{ rowNumber: 4, sourceCode: "TOAN11-0004", issue: { code: "REQUIRED", severity: "error" } }],
    ...overrides,
  });

  const mapping: ColumnMapping = { c1: "sourceCode", c2: "content" };
  const commitParams = { uploadId: "upl-1", mapping, duplicatePolicy: "SKIP" as const, skipErrors: true };
  const idempotencyKeys = () =>
    callsTo("POST", `${BASE}/uploads/{id}/commit`).map(
      (call) => (call.options as { params: { header: Record<string, string> } }).params.header["Idempotency-Key"],
    );

  it("tải tệp: gửi multipart, đổi sang kiểu của web, giá trị ánh xạ lạ thành UNMAPPED", async () => {
    fakeApi.post(`${BASE}/uploads`, ok(apiUpload, { status: 201 }));
    const upload = await uploadQuestionFile(file("ngan-hang-toan-11.xlsx"));
    expect(upload).toMatchObject({ id: "upl-1", fileName: "ngan-hang-toan-11.xlsx", fileType: "XLSX", sheetName: "Câu hỏi", rowCount: 26 });
    expect(upload.columns).toEqual([
      { key: "c1", header: "Mã nguồn", example: "TOAN11-0001" },
      { key: "c2", header: "Nội dung", example: "Tính giới hạn" },
    ]);
    expect(upload.suggestedMapping).toEqual({ c1: "sourceCode", c2: "content", c9: "UNMAPPED" });

    const body = (fakeApi.calls[0]!.options as { bodySerializer: () => FormData }).bodySerializer();
    expect(body).toBeInstanceOf(FormData);
    expect((body.get("file") as File).name).toBe("ngan-hang-toan-11.xlsx");
  });

  it("tải tệp: lỗi 422 giữ mã nghiệp vụ, 413 không có thân lỗi thành IMPORT_FILE_TOO_LARGE", async () => {
    fakeApi.post(`${BASE}/uploads`, fail(422, { code: "IMPORT_INVALID_FILE" }));
    const rejected = uploadQuestionFile(file("a.pdf"));
    await expect(rejected).rejects.toBeInstanceOf(ContentApiError);
    await expect(rejected).rejects.toMatchObject({ status: 422, code: "IMPORT_INVALID_FILE" });
    await expect(importApiErrorCode(await rejected.catch((error: unknown) => error))).toBe("IMPORT_INVALID_FILE");

    fakeApi.post(`${BASE}/uploads`, fail(422, { code: "IMPORT_EMPTY_FILE" }));
    await expect(uploadQuestionFile(file("a.csv", "Mã nguồn,Nội dung\n"))).rejects.toMatchObject({ code: "IMPORT_EMPTY_FILE" });

    fakeApi.post(`${BASE}/uploads`, fail(413));
    await expect(uploadQuestionFile(file("lon.xlsx"))).rejects.toMatchObject({ status: 413, code: "IMPORT_FILE_TOO_LARGE" });

    fakeApi.post(`${BASE}/uploads`, fail(413, { code: "IMPORT_FILE_TOO_LARGE" }));
    await expect(uploadQuestionFile(file("lon.xlsx"))).rejects.toMatchObject({ status: 413, code: "IMPORT_FILE_TOO_LARGE" });
  });

  it("tệp mẫu: lấy tên tệp từ Content-Disposition, thiếu thì dùng tên mặc định", async () => {
    const blob = new Blob(["a,b"], { type: "text/csv" });
    fakeApi.get(`${BASE}/template`, ok(blob, { headers: { "content-disposition": 'attachment; filename="mau-nhap-cau-hoi.csv"' } }));
    const template = await downloadQuestionTemplate();
    expect(template.filename).toBe("mau-nhap-cau-hoi.csv");
    expect(await readFileText(template.blob)).toBe("a,b");
    expect((fakeApi.calls[0]!.options as { parseAs: string }).parseAs).toBe("blob");

    fakeApi.get(`${BASE}/template`, ok(blob));
    expect((await downloadQuestionTemplate()).filename).toBe("question-import-template.xlsx");
  });

  it("kiểm tra: gửi ánh xạ, đổi dòng và tổng hợp, điền mặc định cho trường thiếu", async () => {
    fakeApi.post(
      `${BASE}/uploads/{id}/validate`,
      ok({
        uploadId: "upl-1",
        summary: { total: 2, valid: 1, error: 1, duplicate: 0, validSets: 0, validSetChildren: 0 },
        rows: [
          {
            rowNumber: 2,
            sourceCode: "TOAN11-0001",
            status: "VALID",
            issues: [],
            draft: { type: "SINGLE_CHOICE", content: "<p>x</p>", options: [{ letter: "A", content: "1", correct: true }], points: 0.75 },
          },
          { rowNumber: 3, status: "ERROR", issues: [{ code: "REQUIRED", severity: "error", field: "content" }] },
        ],
      }),
    );
    const validation = await validateImport({ uploadId: "upl-1", mapping });
    const call = fakeApi.calls[0]!;
    expect(call.options).toMatchObject({ params: { path: { id: "upl-1" } }, body: { uploadId: "upl-1", mapping } });
    expect(validation.summary).toEqual({ total: 2, valid: 1, error: 1, duplicate: 0, validSets: 0, validSetChildren: 0 });
    expect(validation.rows[0]).toMatchObject({
      rowNumber: 2,
      sourceCode: "TOAN11-0001",
      status: "VALID",
      draft: { type: "SINGLE_CHOICE", points: 0.75, options: [{ letter: "A", content: "1", correct: true }], knowledge: [] },
      isSetPassage: false,
      parentRowNumber: null,
      duplicateOf: null,
    });
    expect(validation.rows[1]).toMatchObject({ status: "ERROR", sourceCode: null, issues: [{ code: "REQUIRED", severity: "error" }] });
  });

  it("nhập: gửi Idempotency-Key, đổi lịch sử sang kiểu của web", async () => {
    fakeApi.post(`${BASE}/uploads/{id}/commit`, ok(history(), { status: 201 }));
    const result = await commitImport(commitParams);
    expect(result).toMatchObject({
      id: "imp-1",
      createdCount: 13,
      skippedDuplicateCount: 3,
      errorRowCount: 8,
      importedBy: { id: "u-lan", name: "Lan" },
      errors: [{ rowNumber: 4, sourceCode: "TOAN11-0004", issue: { code: "REQUIRED" } }],
    });
    expect(fakeApi.calls[0]!.options).toMatchObject({
      params: { path: { id: "upl-1" } },
      body: { uploadId: "upl-1", mapping, duplicatePolicy: "SKIP", skipErrors: true },
    });
    expect(idempotencyKeys()).toHaveLength(1);
    expect(idempotencyKeys()[0]).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("nhập: thử lại cùng lệnh sau lỗi chưa rõ kết quả dùng lại khóa; sau thành công hoặc lỗi nghiệp vụ thì khóa mới", async () => {
    const post = `${BASE}/uploads/{id}/commit`;
    fakeApi.post(post, fail(503, { code: "UNAVAILABLE" }));
    await expect(commitImport(commitParams)).rejects.toMatchObject({ status: 503 });
    fakeApi.post(post, ok(history()));
    await commitImport(commitParams);
    const [first, retry] = idempotencyKeys();
    expect(retry).toBe(first);

    await commitImport(commitParams);
    expect(idempotencyKeys()[2]).not.toBe(first);

    fakeApi.post(post, fail(422, { code: "IMPORT_HAS_ERRORS" }));
    const different = { ...commitParams, skipErrors: false };
    await expect(commitImport(different)).rejects.toMatchObject({ status: 422, code: "IMPORT_HAS_ERRORS" });
    await expect(commitImport(different)).rejects.toMatchObject({ code: "IMPORT_HAS_ERRORS" });
    const keys = idempotencyKeys();
    expect(keys[3]).not.toBe(keys[4]);
  });

  it("lịch sử nhập: đổi từng dòng, lỗi 500 ném đúng trạng thái", async () => {
    fakeApi.get(BASE, ok([history(), history({ id: "imp-2", errors: undefined, importedBy: undefined })]));
    const entries = await listImportHistory();
    expect(entries.map((entry) => entry.id)).toEqual(["imp-1", "imp-2"]);
    expect(entries[1]).toMatchObject({ errors: [], importedBy: { id: "", name: "" } });

    fakeApi.get(BASE, ok([]));
    expect(await listImportHistory()).toEqual([]);
    fakeApi.get(BASE, fail(500, { code: "INTERNAL" }));
    await expect(listImportHistory()).rejects.toMatchObject({ status: 500 });
  });
});
