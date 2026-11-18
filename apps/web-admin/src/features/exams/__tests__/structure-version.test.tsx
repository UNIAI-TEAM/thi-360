import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createExam } from "../api";
import { ExamStructureView } from "../components/exam-structure-view";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireTemplate } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

let backend: ExamsBackend;

beforeEach(() => {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
});

function openStructure(examId: string, search = "") {
  window.history.replaceState(null, "", `/exams/${examId}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

describe("Cấu trúc: phiên bản năm", () => {
  it("SP2E Toán: chọn phiên bản 2025 hiện danh sách phần sẽ nạp lại, chỉ thay sau khi xác nhận", async () => {
    const exam = await createExam({ name: "SP2E thử", code: "SP2E-S-902", kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    expect(exam.templateCode).toBe("SP2E_MATH_2026");
    const user = userEvent.setup();
    openStructure(exam.id);
    const picker = await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ });
    // Máy chủ không trả tên mẫu: tên dựng từ kỳ thi, môn và năm cấu trúc.
    expect(picker).toHaveTextContent("SP2E · Toán · 2026");

    await user.click(picker);
    await user.click(await screen.findByRole("option", { name: /SP2E · Toán · 2025/ }));
    const dialog = await screen.findByRole("dialog", { name: /Đổi sang “SP2E · Toán · 2025”\?/ });
    expect(within(dialog).getByText("Bước 1/2")).toBeInTheDocument();
    expect(within(dialog).getByText("Các phần sẽ được nạp lại (1)")).toBeInTheDocument();
    expect(within(dialog).getByText("1. Toán")).toBeInTheDocument();
    // Trước → sau theo từng phần: cấu trúc hiện tại (bản 2026) và phần của mẫu 2025.
    expect(within(dialog).getByText(/→ 22 câu · chưa có thời lượng$/)).toBeInTheDocument();
    expect(within(dialog).getByText(/^Toán · \d+ câu .* → /)).toBeInTheDocument();
    // Chưa xác nhận: cấu trúc chưa đổi.
    expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2026");

    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2025"));
    expect(backend.app(exam.id)).toMatchObject({ totalQuestions: 22, structureYear: 2025 });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  }, 20_000);

  it("hủy hộp xác nhận thì giữ nguyên cấu trúc và bản nháp chưa lưu", async () => {
    const exam = await createExam({ name: "SP2E thử", code: "SP2E-S-903", kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    const user = userEvent.setup();
    openStructure(exam.id);
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click(await screen.findByRole("option", { name: /SP2E · Toán · 2025/ }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2026");
  }, 20_000);

  it("'Nếu chọn …': mỗi phiên bản khác hiện nhãn và tóm tắt của máy chủ; phiên bản chưa có tóm tắt thì không hiện", async () => {
    // Máy chủ trả nhãn và tóm tắt cho phiên bản 2025; phiên bản 2026 (đang dùng) không có.
    const wire = FIXTURE_TEMPLATES.map((template) =>
      template.code === "SP2E_MATH_2025"
        ? { ...toWireTemplate(template), label: "Đề minh họa cũ", summary: "22 câu, 10 điểm." }
        : toWireTemplate(template),
    );
    backend = new ExamsBackend();
    backend.server.on("GET", "/api/v1/admin/exam-templates", { status: 200, body: wire });
    backend.install();
    const exam = await createExam({ name: "SP2E thử", code: "SP2E-S-907", kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    openStructure(exam.id);
    const preview = await screen.findByTestId("structure-version-preview");
    expect(preview).toHaveTextContent("Nếu chọn “Đề minh họa cũ”");
    expect(preview).toHaveTextContent("22 câu, 10 điểm.");
    expect(screen.getAllByTestId("structure-version-preview")).toHaveLength(1);
  }, 20_000);

  it("chỉ một phiên bản thì ẩn ô chọn phiên bản; phần chưa có mẫu cũng không hiện ô", async () => {
    const { unmount } = openStructure("ex-qda-f-001");
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    expect(screen.queryByRole("combobox", { name: /Phiên bản cấu trúc/ })).not.toBeInTheDocument();
    unmount();
    const math = await createExam({ name: "HSA Toán", code: "HSA-P-902", kind: "HSA", scope: "PART", partCode: "MATH", maxScore: 50, description: null });
    expect(math.templateCode).toBe("HSA_SINGLE");
    const view = openStructure(math.id, `?part=${backend.app(math.id).parts[0]!.id}`);
    await screen.findByRole("textbox", { name: /Số câu phải làm/ });
    expect(screen.queryByRole("combobox", { name: /Phiên bản cấu trúc/ })).not.toBeInTheDocument();
    view.unmount();
    const literature = await createExam({ name: "HSA Văn", code: "HSA-P-903", kind: "HSA", scope: "PART", partCode: "LITERATURE", maxScore: 50, description: null });
    openStructure(literature.id, `?part=${backend.app(literature.id).parts[0]!.id}`);
    await screen.findByRole("textbox", { name: /Số câu phải làm/ });
    expect(screen.queryByRole("combobox", { name: /Phiên bản cấu trúc/ })).not.toBeInTheDocument();
  });
});

const policyOf = (body: unknown): unknown => (typeof body === "object" && body !== null && "overviewPolicy" in body ? body.overviewPolicy : undefined);

describe("Cấu trúc: đổi phiên bản khi tổng quan đã sửa (EXAM_OVERVIEW_CONFIRM)", () => {
  async function startReload(code: string) {
    const exam = await createExam({ name: "SP2E thử", code, kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    backend.requireOverviewConfirm(exam.id);
    const user = userEvent.setup();
    openStructure(exam.id);
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click(await screen.findByRole("option", { name: /SP2E · Toán · 2025/ }));
    const reload = await screen.findByRole("dialog", { name: /Đổi sang “/ });
    await user.click(within(reload).getByRole("button", { name: "Nạp lại cấu trúc" }));
    const ask = await screen.findByRole("dialog", { name: "Giữ số tổng quan đã sửa?" });
    return { exam, user, ask };
  }
  const templatePath = (id: string) => `/api/v1/admin/exams/${id}/structure/template`;

  it("hỏi lại sau hộp xác nhận nạp lại; chọn giữ số đã sửa thì gửi lại kèm KEEP_EDITED", async () => {
    const { exam, user, ask } = await startReload("SP2E-S-904");
    expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2026");
    expect(within(ask).getByText("Bước 2/2 · chỉ hiện khi bạn đã sửa tổng quan ở bước 1")).toBeInTheDocument();
    // So sánh số: mẫu đang dùng, bài hiện tại, mẫu mới (chỉ hàng có số).
    const table = within(ask).getByRole("table", { name: "So sánh số tổng quan" });
    for (const name of ["Mục", "Mẫu đang dùng", "Đang có (đã sửa)", "Mẫu mới"]) {
      expect(within(table).getByRole("columnheader", { name })).toBeInTheDocument();
    }
    expect(within(table).getByRole("rowheader", { name: "Toán · số câu" })).toBeInTheDocument();
    // Mặc định giữ số đã sửa.
    expect(within(ask).getByRole("radio", { name: /Giữ số đã sửa/ })).toBeChecked();
    await user.click(within(ask).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2025"));
    expect(backend.server.calls("POST", templatePath(exam.id)).map((call) => policyOf(call.body))).toEqual([undefined, "KEEP_EDITED"]);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  }, 20_000);

  it("chọn lấy số của mẫu thì gửi lại kèm USE_TEMPLATE", async () => {
    const { exam, user, ask } = await startReload("SP2E-S-905");
    await user.click(within(ask).getByRole("radio", { name: /Lấy số của mẫu mới/ }));
    await user.click(within(ask).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2025"));
    expect(backend.server.calls("POST", templatePath(exam.id)).map((call) => policyOf(call.body))).toEqual([undefined, "USE_TEMPLATE"]);
  }, 20_000);

  it("hủy ở câu hỏi giữ số thì không nạp lại: cấu trúc giữ nguyên, không gửi thêm yêu cầu", async () => {
    const { exam, user, ask } = await startReload("SP2E-S-906");
    await user.click(within(ask).getByRole("button", { name: "Hủy đổi phiên bản" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(backend.app(exam.id)?.templateCode).toBe("SP2E_MATH_2026");
    expect(backend.server.calls("POST", templatePath(exam.id))).toHaveLength(1);
  }, 20_000);
});

describe("Cấu trúc: phần thi", () => {
  it("phần tự chọn có quy tắc tham gia; số câu trên đề không được ít hơn số câu phải làm", async () => {
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=part-49");
    expect(await screen.findByRole("heading", { name: "Quy tắc tham gia" })).toBeInTheDocument();
    const choose = screen.getByRole("textbox", { name: "Số phương án phải chọn" });
    expect(choose).toHaveValue("1");
    await user.clear(choose);
    await user.type(choose, "9");
    expect((await screen.findAllByText(/số phương án phải chọn là 9/)).length).toBeGreaterThan(0);

    const paper = screen.getByRole("textbox", { name: /Số câu trên đề/ });
    const required = screen.getByRole("textbox", { name: /Số câu phải làm/ });
    expect(paper).toHaveValue("50");
    expect(required).toHaveValue("50");
    await user.clear(required);
    await user.type(required, "40");
    // Số câu trên đề đi theo khi hai số đang bằng nhau.
    expect(paper).toHaveValue("40");
    await user.clear(paper);
    await user.type(paper, "30");
    expect((await screen.findAllByText(/số câu trên đề \(30\) ít hơn số câu phải làm \(40\)/)).length).toBeGreaterThan(0);
  });

  it("điểm tối đa của phần để trống được; nhập thì lưu vào phần", async () => {
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=part-42");
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    expect(points).toHaveValue("");
    await user.type(points, "50");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(backend.app("ex-qda-f-001")?.parts.find((part) => part.id === "part-42")?.maxPoints).toBe(50));
  });

  it("Điểm mỗi câu yêu cầu của khối không bắt buộc: xóa ô thì lưu null", async () => {
    const user = userEvent.setup();
    const blockId = backend.app("ex-hsa-f-001").parts[0]!.sections[0]!.blocks[0]!.id;
    openStructure("ex-hsa-f-001", `?block=${blockId}`);
    const points = await screen.findByRole("textbox", { name: /Điểm mỗi câu yêu cầu/ });
    await user.clear(points);
    expect(screen.getByText(/Không bắt buộc\. Để trống thì câu nào cũng vào nguồn/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() =>
      expect(
        backend.app("ex-hsa-f-001")?.parts[0]?.sections[0]?.blocks.find((block) => block.id === blockId)?.requiredPointsPerQuestion,
      ).toBeNull(),
    );
  });
});
