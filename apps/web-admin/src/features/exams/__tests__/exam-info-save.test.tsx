import { QueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import type * as ContentApi from "@/features/content/api";
import { renderWithProviders } from "@/test/render";
import { updateExamInfo } from "../api";
import { mapExamDetail } from "../api-mapping";
import { ExamGeneralForm } from "../components/exam-general-form";
import { buildUpdateInfoRequest, examToFormValues } from "../create-exam-edit";
import { overviewRowsFromParts } from "../create-exam-overview-structure";
import { updateRowCell } from "../create-exam-overview";
import { useExamInfoSave } from "../create-exam-save";
import type { ExamInfoFormValues } from "../create-exam-schema";
import { EXAMS_QUERY_KEYS } from "../keys";
import type { ExamDetail } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";

function SaveHarness({ initialExam }: { initialExam: ExamDetail }) {
  const [currentExam, setCurrentExam] = useState(initialExam);
  const [holdStates, setHoldStates] = useState<boolean[]>([]);
  const [rows, setRows] = useState(() =>
    updateRowCell(overviewRowsFromParts(initialExam.parts, { fixedCounts: false, countsLocked: false }), "p:0", "questionCount", "40"),
  );
  const [overviewDirty, setOverviewDirty] = useState(true);
  const form = useForm<ExamInfoFormValues>({ defaultValues: examToFormValues(initialExam) });

  const { save, saveOverviewThenInfo, submitError } = useExamInfoSave({
    exam: currentExam,
    structure: {
      examId: currentExam.id,
      revision: currentExam.revision,
      combinations: [],
      parts: currentExam.parts,
      examQuotas: [],
    },
    rows,
    formDirty: true,
    setError: form.setError,
    onHold: (hold) => setHoldStates((prev) => [...prev, hold]),
    onStructureSaved: (saved) => {
      setCurrentExam(saved);
      setRows(overviewRowsFromParts(saved.parts, { fixedCounts: false, countsLocked: false }));
      setOverviewDirty(false);
    },
    onSaved: () => undefined,
    onFailed: () => undefined,
  });

  const onSubmit = form.handleSubmit((values) => {
    const request = buildUpdateInfoRequest({
      exam: currentExam,
      submitted: values,
      kinds: [],
      appliedTemplate: undefined,
      changed: false,
      maxScore: currentExam.maxScore,
    });
    if (overviewDirty) {
      void saveOverviewThenInfo({ request, parts: [] });
    } else {
      save({ request, parts: [] });
    }
  });

  return (
    <form aria-label="Harness lưu bài thi" onSubmit={onSubmit}>
      <label htmlFor="harness-code">Mã bài thi</label>
      <input id="harness-code" {...form.register("code")} />
      {form.formState.errors.code && <span>{form.formState.errors.code.message}</span>}
      {submitError && <div role="alert">{submitError}</div>}
      <output data-testid="hold-history">{JSON.stringify(holdStates)}</output>
      <button type="submit">Lưu</button>
    </form>
  );
}

describe("Red-first B-01 / B-02 / B-06 (AC-09, AC-10, AC-12)", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
  });

  it("AC-09 (R-05): xóa trắng hướng dẫn Tiptap (<p></p>) gửi instructions: '' trong PATCH /exams/{id}", async () => {
    const server = new FakeServer()
      .on("PATCH", EXAM_PATH, { status: 200, body: examDetailWire({ instructions: undefined, revision: 8 }) })
      .install();

    const exam = mapExamDetail(examDetailWire({ instructions: "<p>Đọc kỹ đề trước khi làm</p>" }));
    const submitted: ExamInfoFormValues = {
      ...examToFormValues(exam),
      instructions: "<p></p>",
    };
    const request = buildUpdateInfoRequest({
      exam,
      submitted,
      kinds: [],
      appliedTemplate: undefined,
      changed: false,
      maxScore: exam.maxScore,
    });
    expect(request.instructions).toBe("");

    await updateExamInfo(EXAM_ID, request);
    expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1);
    expect(server.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({
      instructions: "",
      expectedRevision: 7,
    });
  });

  it("AC-10 (R-06): PUT /structure thành công (revision 8) nhưng PATCH lỗi 409 thì cập nhật cache detail, nhả hold và bấm Lưu lần hai gửi expectedRevision 8 thành công 200", async () => {
    const afterStructureWire = examDetailWire({
      revision: 8,
      parts: [{ ...FIXED_PART, questionCount: 40 }],
    });
    const afterPatchWire = examDetailWire({
      code: "HSA-UNIQUE-009",
      revision: 9,
      parts: [{ ...FIXED_PART, questionCount: 40 }],
    });
    const server = new FakeServer()
      .on("PUT", STRUCTURE_PATH, { status: 200, body: afterStructureWire })
      .on(
        "PATCH",
        EXAM_PATH,
        { status: 409, body: { code: "EXAM_CODE_TAKEN", message: "taken" } },
        { status: 200, body: afterPatchWire },
      )
      .install();

    const initialExam = mapExamDetail(examDetailWire({ revision: 7 }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(EXAMS_QUERY_KEYS.detail(EXAM_ID), initialExam);

    const user = userEvent.setup();
    renderWithProviders(<SaveHarness initialExam={initialExam} />, { client });

    const codeInput = screen.getByLabelText("Mã bài thi");
    await user.clear(codeInput);
    await user.type(codeInput, "TAKEN-CODE");
    await user.click(screen.getByRole("button", { name: "Lưu" }));

    expect(await screen.findByText("Mã bài thi đã được dùng. Nhập mã khác.")).toBeInTheDocument();
    expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1);

    // Cache EXAMS_QUERY_KEYS.detail(id) phải cập nhật ngay sang revision 8 và cờ hold phải được nhả (true -> false).
    const cached = client.getQueryData<ExamDetail>(EXAMS_QUERY_KEYS.detail(EXAM_ID));
    expect(cached?.revision).toBe(8);
    expect(JSON.parse(screen.getByTestId("hold-history").textContent ?? "[]")).toEqual([true, false]);
    // Giá trị người dùng đang gõ không mất.
    expect(codeInput).toHaveValue("TAKEN-CODE");

    // Sửa mã hợp lệ và bấm Lưu lần hai: không gửi lại PUT /structure, PATCH gửi expectedRevision = 8 và trả 200.
    await user.clear(codeInput);
    await user.type(codeInput, "HSA-UNIQUE-009");
    await user.click(screen.getByRole("button", { name: "Lưu" }));

    await waitFor(() => expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(2));
    expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(server.calls("PATCH", EXAM_PATH)[1]!.body).toMatchObject({
      code: "HSA-UNIQUE-009",
      expectedRevision: 8,
    });
  });

  it("AC-12 (R-08.1): bài REVIEW H42 đã công bố (servingVersion !== null, fixedBlockCounts: true) khóa phạm vi nhưng mở sửa bảng tổng quan", async () => {
    const reviewPart = { ...FIXED_PART, name: "Bài ôn", questionCount: 20, questionCountOnPaper: 20, durationMinutes: 45 };
    const publishedReview = examDetailWire({
      code: "HSCA-REV-001",
      name: "Bài ôn HSCA Toán",
      kind: "HSCA",
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      status: "PUBLISHED",
      servingVersion: 1,
      templateCode: "HSCA_REVIEW",
      maxScore: 10,
      parts: [reviewPart],
    } as never);
    const server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: publishedReview })
      .on("GET", STRUCTURE_PATH, {
        status: 200,
        body: {
          id: EXAM_ID,
          revision: 7,
          servingVersion: 1,
          hasUnpublishedChanges: false,
          combinations: [],
          parts: [reviewPart],
          examQuotas: [],
        },
      })
      .on("GET", TEMPLATES_PATH, {
        status: 200,
        body: [
          {
            active: true,
            code: "HSCA_REVIEW",
            kindCode: "HSCA",
            scope: "SUBJECT",
            subjectCode: null,
            timing: "PER_PART",
            buildMode: "REVIEW",
            isDefault: true,
            fixedCounts: false,
            fixedBlockCounts: true,
            parts: [],
            structure: {
              parts: [
                {
                  id: "p1",
                  order: 1,
                  name: "Review",
                  questionCount: 0,
                  durationMinutes: null,
                  participation: { mode: "ALL" },
                  mode: "FIXED",
                  sections: [],
                },
              ],
            },
          },
        ],
      })
      .install();

    const { renderExamsUi } = await import("./render-exams-ui");
    renderExamsUi(<ExamGeneralForm mode="edit" examId={EXAM_ID} />);
    const table = await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    const countInput = within(table).getByRole("textbox", { name: /^Số câu của/ });
    expect(countInput).not.toHaveAttribute("readonly");
    expect(server.calls("GET", EXAM_PATH)).toHaveLength(1);
  });

  it("AC-11 (R-07): mode='create' khi năm cấu trúc (2026) khác năm ma trận (2025) cho tích yearsConfirmed và gửi POST /exams rồi PATCH /exams/{id} trước khi router.push", async () => {
    const { ExamsBackend } = await import("./fake-exams-backend");
    const { renderExamsUi } = await import("./render-exams-ui");
    const backend = new ExamsBackend();
    backend.install();

    const user = userEvent.setup();
    window.history.replaceState(null, "", "/exams/new");
    renderExamsUi(<ExamGeneralForm mode="create" />);

    await screen.findByRole("form", { name: "Tạo bài thi" });
    await user.click(screen.getByRole("combobox", { name: /^Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: /\(SP2E\)/ }));

    // SP2E Toán mặc định là mẫu 2026 (structureYear: 2026, matrixYear: 2025).
    const confirmCheckbox = await screen.findByRole("checkbox", { name: /Chuyên gia đã xác nhận chỉ tiêu/ });
    expect(confirmCheckbox).not.toBeChecked();

    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "SP2E Toán tạo mới xác nhận năm");
    await user.clear(screen.getByRole("textbox", { name: /Mã bài thi/ }));
    await user.type(screen.getByRole("textbox", { name: /Mã bài thi/ }), "SP2E-NEW-2026");
    await user.click(confirmCheckbox);
    await user.click(screen.getByRole("button", { name: /Lưu và tiếp tục/ }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledTimes(1));
    const writes = backend.server.requests
      .filter((req) => req.method !== "GET")
      .map((req) => ({ method: req.method, path: req.url.pathname, body: req.body as Record<string, unknown> }));

    expect(writes).toHaveLength(2);
    expect(writes[0]!.method).toBe("POST");
    expect(writes[0]!.path).toBe("/api/v1/admin/exams");

    const createdId = backend.wireByCode("SP2E-NEW-2026")!.id;
    expect(writes[1]!.method).toBe("PATCH");
    expect(writes[1]!.path).toBe(`/api/v1/admin/exams/${createdId}`);
    expect(writes[1]!.body).toMatchObject({
      yearsConfirmed: true,
      expectedRevision: 1,
    });
    expect(backend.app(createdId).yearsConfirmed).toBe(true);
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${createdId}/structure`);
  }, 30_000);

  it("AC-13 (R-08.3): bài BCA (pointsByPart) ở mode='edit' chỉ sửa Điểm của một phần (25 -> 30, tổng 100 -> 105) gửi PUT /structure kèm PATCH /exams/{id} cập nhật maxScore: 105", async () => {
    const { createExam } = await import("../api");
    const { ExamsBackend } = await import("./fake-exams-backend");
    const { renderExamsUi } = await import("./render-exams-ui");
    const backend = new ExamsBackend();
    backend.install();

    const bca = await createExam({
      name: "BCA kiểm đồng bộ thang điểm",
      code: "BCA-SYNC-105",
      kind: "BCA",
      scope: "FULL",
      description: null,
    });
    const bcaPath = `/api/v1/admin/exams/${bca.id}`;
    const bcaStructurePath = `${bcaPath}/structure`;

    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${bca.id}`);
    renderExamsUi(<ExamGeneralForm mode="edit" examId={bca.id} />);

    const table = await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    const pointInputs = within(table).getAllByRole("textbox", { name: /^Điểm của/ });
    expect(pointInputs[0]).toHaveValue("25");

    await user.clear(pointInputs[0]!);
    await user.type(pointInputs[0]!, "30");
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));

    await waitFor(() => expect(backend.calls("PATCH", bcaPath)).toHaveLength(1));
    expect(backend.calls("PUT", bcaStructurePath)).toHaveLength(1);
    expect(backend.calls("PATCH", bcaPath)[0]!.body).toMatchObject({
      maxScore: 105,
      expectedRevision: bca.revision + 1,
    });
    expect(backend.app(bca.id).maxScore).toBe(105);
  }, 30_000);
});
