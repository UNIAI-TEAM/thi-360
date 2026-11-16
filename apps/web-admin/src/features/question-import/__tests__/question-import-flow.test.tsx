import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { resetCatalogLookup } from "@/features/content/catalog-lookup";
import en from "@/messages/en.json";
import { apiContentReference, fail, fakeApi, ok, useRealApi } from "@/test/fake-api";
import { renderWithProviders } from "@/test/render";
import { QuestionImportView } from "../components/question-import-view";
import templateFixture from "../mock/template.json";

useRealApi();

const BASE = "/api/v1/admin/question-imports";

const apiHistory = {
  id: "imp-0",
  importedAt: "2026-11-10T01:00:00+07:00",
  importedBy: { id: "u-lan", name: "Lan" },
  fileName: "toan-10-chuong-1.xlsx",
  createdCount: 120,
  singleCount: 118,
  setCount: 2,
  setChildCount: 5,
  skippedDuplicateCount: 3,
  newVersionCount: 0,
  errorRowCount: 2,
  missingKnowledgeCount: 0,
  durationMs: 4000,
  errors: [],
};

const apiUpload = {
  id: "upl-1",
  fileName: "ngan-hang-toan-11.xlsx",
  fileType: "XLSX",
  sheetName: "Câu hỏi",
  rowCount: 7,
  columns: [
    { key: "c1", header: "Mã nguồn", example: "TOAN11-0001" },
    { key: "c2", header: "Dạng câu", example: "Trắc nghiệm 1 đáp án" },
    { key: "c3", header: "Nội dung", example: "Tính giới hạn" },
    { key: "c4", header: "Đáp án", example: "C" },
    { key: "c5", header: "Môn", example: "Toán" },
    { key: "c6", header: "Lớp", example: "11" },
    { key: "c7", header: "Mức độ", example: "Thông hiểu" },
    { key: "c8", header: "Ghi chú nội bộ", example: "Đã soát" },
  ],
  suggestedMapping: {
    c1: "sourceCode",
    c2: "type",
    c3: "content",
    c4: "correctAnswer",
    c5: "subject",
    c6: "grade",
    c7: "difficulty",
    c8: "UNMAPPED",
  },
};

const classification = { subjectCode: "MATH", gradeCode: "11", difficultyCode: "TH" };

/** Bảy dòng: 3 hợp lệ (đơn, tự luận thiếu hướng dẫn), 2 lỗi, 1 trùng, cộng Điền khuyết / Đúng-Sai / Ghép đôi hợp lệ. */
const apiValidation = {
  uploadId: "upl-1",
  summary: { total: 8, valid: 6, error: 1, duplicate: 1, validSets: 0, validSetChildren: 0 },
  rows: [
    {
      rowNumber: 2,
      sourceCode: "TOAN11-0001",
      status: "VALID",
      issues: [],
      draft: { type: "SINGLE_CHOICE", content: "Câu 1", options: [{ letter: "A", content: "0", correct: false }, { letter: "C", content: "2", correct: true }], ...classification },
    },
    {
      rowNumber: 3,
      sourceCode: "TOAN11-0002",
      status: "ERROR",
      issues: [{ code: "SUBJECT_UNKNOWN", severity: "error", field: "subject", value: "Toan", column: "Môn" }],
      draft: { type: "SINGLE_CHOICE", content: "Câu 2" },
    },
    {
      rowNumber: 4,
      sourceCode: "TOAN11-0004",
      status: "DUPLICATE",
      issues: [],
      duplicateOf: { id: "q-0004", code: "CH-000004" },
      draft: { type: "SINGLE_CHOICE", content: "Câu 4", ...classification },
    },
    {
      rowNumber: 5,
      sourceCode: "MAU-0004",
      status: "VALID",
      issues: [],
      draft: {
        type: "CLOZE",
        content: "Thủ đô là [1], sông là [2]",
        body: { blanks: [{ id: "b1", answers: ["Hà Nội"], points: 1 }, { id: "b2", answers: ["Hồng", "sông Hồng"], points: 0.5 }] },
        ...classification,
      },
    },
    {
      rowNumber: 6,
      sourceCode: "MAU-0005",
      status: "VALID",
      issues: [],
      draft: {
        type: "TRUE_FALSE",
        content: "Xét các ý",
        statements: [{ letter: "a", content: "Ý 1", answer: true }, { letter: "b", content: "Ý 2", answer: false }],
        body: { scoringMethod: "COUNT_TABLE", scoreTable: [0.1, 0.25, 0.5, 1] },
        ...classification,
      },
    },
    {
      rowNumber: 7,
      sourceCode: "MAU-0006",
      status: "VALID",
      issues: [],
      draft: {
        type: "MATCHING",
        content: "Ghép đạo hàm với nguyên hàm",
        body: {
          left: [{ id: "l1", content: "x" }, { id: "l2", content: "eˣ" }, { id: "l3", content: "cos x" }],
          right: [{ id: "rA", content: "x²/2 + C" }, { id: "rB", content: "eˣ + C" }, { id: "rC", content: "sin x + C" }, { id: "rD", content: "tan x + C" }],
          pairs: [{ leftId: "l1", rightId: "rA" }, { leftId: "l2", rightId: "rB" }, { leftId: "l3", rightId: "rC" }],
          reuseRight: false,
        },
        ...classification,
      },
    },
    {
      rowNumber: 8,
      sourceCode: "TOAN11-0016",
      status: "VALID",
      issues: [{ code: "GRADING_GUIDE_MISSING", severity: "warning", field: "gradingGuide" }],
      draft: { type: "ESSAY", content: "Phân tích", rubric: [], points: 1, ...classification },
    },
  ],
};

function stubImportApi(overrides: { history?: unknown[]; validation?: unknown } = {}) {
  fakeApi.get("/api/v1/admin/content-reference", ok(apiContentReference()));
  fakeApi.get(BASE, ok(overrides.history ?? [apiHistory]));
  fakeApi.post(`${BASE}/uploads`, ok(apiUpload, { status: 201 }));
  fakeApi.post(`${BASE}/uploads/{id}/validate`, ok(overrides.validation ?? apiValidation));
  fakeApi.post(
    `${BASE}/uploads/{id}/commit`,
    ok(
      {
        ...apiHistory,
        id: "imp-1",
        fileName: "ngan-hang-toan-11.xlsx",
        createdCount: 6,
        singleCount: 6,
        setCount: 0,
        setChildCount: 0,
        skippedDuplicateCount: 1,
        errorRowCount: 1,
        errors: [{ rowNumber: 3, sourceCode: "TOAN11-0002", issue: { code: "SUBJECT_UNKNOWN", severity: "error", field: "subject", value: "Toan" } }],
      },
      { status: 201 },
    ),
  );
}

beforeEach(() => {
  resetCatalogLookup();
  window.history.replaceState(null, "", "/questions/import");
});

function fileInput(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("no file input");
  return input;
}

async function uploadSample(container: HTMLElement, user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
  await user.upload(fileInput(container), new File(["xlsx"], "ngan-hang-toan-11.xlsx"));
  await user.click(screen.getByRole("button", { name: "Đọc tệp" }));
  return screen.findByRole("heading", { name: "Ánh xạ cột" });
}

describe("Nhập câu hỏi từ tệp (API thật, client giả)", () => {
  it("bước 1: vùng chọn tệp, các cột của tệp mẫu và lịch sử nhập, không còn dải dữ liệu mẫu", async () => {
    stubImportApi();
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Nhập câu hỏi từ tệp" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Các bước nhập câu hỏi" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Các cột của tệp mẫu" })).toBeInTheDocument();
    const history = await screen.findByRole("table", { name: "Lịch sử nhập câu hỏi" });
    expect(within(history).getByText("toan-10-chuong-1.xlsx")).toBeInTheDocument();
    expect(within(history).getByText("120 tạo mới · 3 trùng · 2 lỗi")).toBeInTheDocument();
  });

  it("bước 1: hướng dẫn nêu đủ 8 dạng câu và 20 cột của tệp mẫu (vi và en)", async () => {
    stubImportApi();
    const { unmount } = renderWithProviders(<QuestionImportView />);
    const viTable = await screen.findByRole("table", { name: "Các cột của tệp mẫu" });
    expect(within(viTable).getAllByRole("row")).toHaveLength(21);
    expect(screen.getByText("20 cột, dòng đầu là tên cột")).toBeInTheDocument();
    const rowOf = (table: HTMLElement, name: string) => within(table).getByRole("rowheader", { name }).closest("tr")!;
    const viType = rowOf(viTable, "Dạng câu");
    for (const name of ["Trả lời ngắn", "Điền khuyết", "Ghép đôi", "Tự luận", "Câu chùm", "Đúng / Sai"]) {
      expect(viType).toHaveTextContent(name);
    }
    expect(rowOf(viTable, "Cách chấm")).toHaveTextContent("COUNT_TABLE");
    expect(rowOf(viTable, "Lựa chọn phải")).toHaveTextContent("Ghép đôi");
    expect(within(viTable).queryByText("A, B, C, D")).not.toBeInTheDocument();
    unmount();

    renderWithProviders(<QuestionImportView />, { locale: "en", messages: en });
    const enTable = await screen.findByRole("table", { name: "Template columns" });
    expect(within(enTable).getAllByRole("row")).toHaveLength(21);
    expect(screen.getByText("20 columns, the first row holds the column names")).toBeInTheDocument();
    const enType = rowOf(enTable, "Question type");
    for (const name of ["Short answer", "Cloze", "Matching", "Essay", "Question set", "True / False"]) {
      expect(enType).toHaveTextContent(name);
    }
  });

  it("bước 1: tên từng cột trong bảng hướng dẫn trùng đúng tên cột của tệp mẫu tải về", async () => {
    stubImportApi();
    renderWithProviders(<QuestionImportView />);
    const viTable = await screen.findByRole("table", { name: "Các cột của tệp mẫu" });
    for (const header of templateFixture.rows[0]!) {
      expect(within(viTable).getByRole("rowheader", { name: header })).toBeInTheDocument();
    }
  });

  it("báo lỗi ngay khi chọn tệp sai loại, không gọi máy chủ", async () => {
    stubImportApi();
    const user = userEvent.setup({ applyAccept: false });
    const { container } = renderWithProviders(<QuestionImportView />);
    await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
    await user.upload(fileInput(container), new File(["x"], "de-thi.pdf"));
    expect(screen.getByRole("alert")).toHaveTextContent("Chỉ nhận tệp .xlsx hoặc .csv.");
    expect(screen.getByRole("button", { name: "Đọc tệp" })).toBeDisabled();
    expect(fakeApi.calls.some((call) => call.path === `${BASE}/uploads`)).toBe(false);
  });

  it("lỗi tải dữ liệu tham chiếu: có nút thử lại", async () => {
    stubImportApi();
    fakeApi.get("/api/v1/admin/content-reference", fail(500, { code: "INTERNAL" }));
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Không tải được dữ liệu tham chiếu" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("lịch sử nhập rỗng", async () => {
    stubImportApi({ history: [] });
    renderWithProviders(<QuestionImportView />);
    expect(await screen.findByRole("heading", { name: "Chưa có lần nhập nào." })).toBeInTheDocument();
  });

  it("đang tải dữ liệu tham chiếu: khung chờ", () => {
    stubImportApi();
    fakeApi.pending("/api/v1/admin/content-reference");
    renderWithProviders(<QuestionImportView />);
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
  });

  it("đi hết bốn bước: đọc tệp, ánh xạ, kiểm tra, nhập thành Nháp", async () => {
    stubImportApi();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);

    const mappingHeading = await uploadSample(container, user);
    await waitFor(() => expect(mappingHeading).toHaveFocus());
    expect(screen.getByText("7 cột đã ánh xạ · 1 chưa ánh xạ · 0 bỏ qua")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    const checkHeading = await screen.findByRole("heading", { name: "Kiểm tra và xem trước" });
    await waitFor(() => expect(checkHeading).toHaveFocus());
    const summary = screen.getByLabelText("Tóm tắt kiểm tra");
    for (const count of ["8", "6", "1"]) expect(within(summary).getAllByText(count).length).toBeGreaterThan(0);

    const errors = screen.getByRole("table", { name: "Các dòng lỗi" });
    expect(within(errors).getByText("Môn học “Toan” không có trong danh mục")).toBeInTheDocument();
    expect(window.location.search).toBe("");

    await user.click(screen.getByRole("button", { name: "Xem trước dòng 3" }));
    const preview = await screen.findByRole("heading", { name: "Xem trước dòng 3" });
    await waitFor(() => expect(preview).toHaveFocus());
    const previewSection = preview.closest("section");
    if (!previewSection) throw new Error("no preview section");
    expect(within(previewSection).getByText(/Môn học “Toan” không có trong danh mục/)).toBeInTheDocument();
    expect(within(previewSection).getByText("Có lỗi")).toBeInTheDocument();

    // Câu Tự luận chưa có tiêu chí chấm vẫn hợp lệ, kèm cảnh báo (câu ở Nháp tới khi bổ sung).
    await user.click(screen.getByRole("tab", { name: "Hợp lệ (6)" }));
    await user.click(screen.getByRole("button", { name: "Xem trước dòng 8" }));
    const essayPreview = await screen.findByRole("heading", { name: "Xem trước dòng 8" });
    const essaySection = essayPreview.closest("section");
    if (!essaySection) throw new Error("no essay preview section");
    expect(within(essaySection).getByText(/Câu tự luận chưa có hướng dẫn chấm/)).toBeInTheDocument();
    expect(within(essaySection).getByText("Chưa có tiêu chí chấm; câu sẽ ở Nháp tới khi bổ sung.")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Trùng (1)" }));
    expect(window.location.search).toBe("?tab=duplicates");
    expect(screen.getByRole("link", { name: "CH-000004" })).toHaveAttribute("href", "/questions/q-0004");

    await user.click(screen.getByRole("button", { name: "Nhập 6 câu" }));
    const resultHeading = await screen.findByRole("heading", { name: "Đã nhập xong 6 câu" });
    await waitFor(() => expect(resultHeading).toHaveFocus());
    expect(screen.getByRole("link", { name: "Xem các câu vừa nhập" })).toHaveAttribute("href", "/questions?status=DRAFT");
    expect(screen.getByRole("button", { name: "Tải file lỗi" })).toBeInTheDocument();
    expect(window.location.search).toBe("");

    const commit = fakeApi.calls.find((call) => call.path === `${BASE}/uploads/{id}/commit`);
    expect(commit?.options).toMatchObject({
      params: { path: { id: "upl-1" } },
      body: { uploadId: "upl-1", duplicatePolicy: "SKIP", skipErrors: true },
    });

    await user.click(screen.getByRole("button", { name: "Nhập tệp khác" }));
    expect(await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" })).toHaveFocus();
  }, 20_000);

  it("xem trước Điền khuyết, Đúng/Sai và Ghép đôi đọc đúng từ thân câu của máy chủ", async () => {
    stubImportApi();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);
    await uploadSample(container, user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    await screen.findByRole("heading", { name: "Kiểm tra và xem trước" });

    await user.click(screen.getByRole("tab", { name: "Hợp lệ (6)" }));
    await user.click(screen.getByRole("button", { name: "Xem trước dòng 5" }));
    const cloze = (await screen.findByRole("heading", { name: "Xem trước dòng 5" })).closest("section")!;
    const blanks = within(cloze).getByRole("list", { name: "Các ô trống" });
    expect(within(blanks).getByText("Hà Nội")).toBeInTheDocument();
    expect(within(blanks).getByText("Hồng | sông Hồng")).toBeInTheDocument();
    expect(within(blanks).getByText("0,5 điểm")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xem trước dòng 6" }));
    const trueFalse = (await screen.findByRole("heading", { name: "Xem trước dòng 6" })).closest("section")!;
    expect(within(trueFalse).getByText(/Bảng theo số ý đúng: 0,1 \/ 0,25 \/ 0,5 \/ 1/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xem trước dòng 7" }));
    const matching = (await screen.findByRole("heading", { name: "Xem trước dòng 7" })).closest("section")!;
    const pairs = within(matching).getByRole("list", { name: "Các cặp ghép" });
    expect(within(pairs).getAllByRole("listitem")).toHaveLength(3);
    expect(within(pairs).getByText("ghép với B. eˣ + C")).toBeInTheDocument();
    expect(within(matching).getByText(/D\. tan x \+ C/)).toBeInTheDocument();
    expect(within(matching).getByText(/Mỗi lựa chọn dùng một lần/)).toBeInTheDocument();
  }, 20_000);

  async function reachCheckStep() {
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);
    await uploadSample(container, user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    await screen.findByRole("heading", { name: "Kiểm tra và xem trước" });
    return user;
  }

  it("không bỏ qua dòng lỗi thì không gửi lệnh nhập và màn báo lỗi", async () => {
    stubImportApi();
    const user = await reachCheckStep();
    const skip = screen.getByRole("checkbox", { name: "Bỏ qua 1 dòng lỗi, chỉ nhập các câu hợp lệ" });
    await user.click(skip);
    await user.click(screen.getByRole("button", { name: "Nhập 6 câu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Tệp còn dòng lỗi.");
    expect(skip).toHaveFocus();
    expect(fakeApi.calls.some((call) => call.path === `${BASE}/uploads/{id}/commit`)).toBe(false);
  });

  it("lệnh nhập bị máy chủ từ chối: IMPORT_HAS_ERRORS, IMPORT_SOURCE_GONE và lỗi chung báo đúng chữ", async () => {
    stubImportApi();
    const user = await reachCheckStep();
    const commit = `${BASE}/uploads/{id}/commit`;

    fakeApi.post(commit, fail(422, { code: "IMPORT_HAS_ERRORS" }));
    await user.click(screen.getByRole("button", { name: "Nhập 6 câu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Tệp còn dòng lỗi.");

    fakeApi.post(commit, fail(410, { code: "IMPORT_SOURCE_GONE" }));
    await user.click(screen.getByRole("button", { name: "Nhập 6 câu" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Tệp tạm đã hết hạn. Chọn lại tệp."));

    fakeApi.post(commit, fail(503, { code: "UNAVAILABLE" }));
    await user.click(screen.getByRole("button", { name: "Nhập 6 câu" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Không nhập được câu hỏi. Thử lại sau."));
  });

  it("thiếu cột bắt buộc thì không qua được bước ánh xạ", async () => {
    stubImportApi();
    fakeApi.post(
      `${BASE}/uploads`,
      ok(
        {
          ...apiUpload,
          fileName: "thieu-cot.csv",
          fileType: "CSV",
          sheetName: null,
          columns: apiUpload.columns.slice(0, 6),
          suggestedMapping: { c1: "sourceCode", c2: "type", c3: "content", c4: "correctAnswer", c5: "subject", c6: "grade" },
        },
        { status: 201 },
      ),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<QuestionImportView />);
    await screen.findByRole("heading", { name: "Chọn tệp câu hỏi" });
    await user.upload(fileInput(container), new File(["x"], "thieu-cot.csv", { type: "text/csv" }));
    await user.click(screen.getByRole("button", { name: "Đọc tệp" }));
    await screen.findByRole("heading", { name: "Ánh xạ cột" });

    await user.click(screen.getByRole("button", { name: "Tiếp tục: kiểm tra" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Chưa ánh xạ trường bắt buộc: Mức độ.");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.queryByRole("heading", { name: "Kiểm tra và xem trước" })).not.toBeInTheDocument();
    expect(fakeApi.calls.some((call) => call.path === `${BASE}/uploads/{id}/validate`)).toBe(false);
  });
});
