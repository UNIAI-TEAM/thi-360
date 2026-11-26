import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamGeneralForm } from "../components/exam-general-form";
import { ExamStructureView } from "../components/exam-structure-view";
import { ExamsBackend } from "./fake-exams-backend";
import { renderExamsUi } from "./render-exams-ui";

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

describe("Cấu trúc và ma trận", () => {
  it("sửa thời lượng phần rồi Lưu: có bản sửa chưa công bố, bài đã kiểm tra thành Cần kiểm tra lại", async () => {
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=part-42");
    const duration = await screen.findByRole("textbox", { name: /Thời lượng \(phút\)/ });
    expect(duration).toHaveValue("80");
    await user.clear(duration);
    await user.type(duration, "85");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(backend.app("ex-qda-f-001").check.status).toBe("NEEDS_RECHECK"));
    expect(backend.app("ex-qda-f-001")).toMatchObject({ hasUnpublishedChanges: true, durationMinutes: 200 });
  });

  it("khối theo %: quy đổi phần dư lớn nhất hiện phần nguyên và phần dư", async () => {
    const user = userEvent.setup();
    // Ma trận riêng của nhóm là mục mở thêm, mặc định đóng.
    openStructure("ex-hsa-f-001", "?block=blk-22");
    const summary = await screen.findByText("Ma trận lớp và độ khó riêng của nhóm");
    expect(summary.closest("details")).not.toHaveAttribute("open");
    await user.click(summary);
    await user.click(await screen.findByRole("radio", { name: "Kết hợp Khối lớp × Độ khó" }));
    await user.click(screen.getByRole("radio", { name: "Tỷ lệ %" }));
    expect(await screen.findByText(/Quy đổi thành số câu · khối/)).toBeInTheDocument();
    expect(screen.getAllByText(/→/).length).toBeGreaterThan(0);
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });

  it("phần tự chọn QDA: Tiếng Anh và hai tổ hợp 2 môn 25–25", async () => {
    openStructure("ex-qda-f-001", "?part=part-49");
    expect(await screen.findByRole("heading", { name: "Phần 3 · Khoa học hoặc Tiếng Anh" })).toBeInTheDocument();
    expect(screen.getAllByText(/QD01/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/QD04/).length).toBeGreaterThan(0);
  });

  it("trạng thái rỗng và lỗi theo phản hồi của máy chủ", async () => {
    const path = "/api/v1/admin/exams/ex-hsa-f-002/structure";
    const empty = new ExamsBackend();
    empty.server.on("GET", path, { status: 200, body: { examId: "ex-hsa-f-002", revision: 1, parts: [] } });
    empty.install();
    const { unmount } = openStructure("ex-hsa-f-002");
    expect(await screen.findByRole("heading", { name: "Bài thi chưa có phần thi" })).toBeInTheDocument();
    unmount();
    const failing = new ExamsBackend();
    failing.server.on("GET", path, { status: 500, body: { code: "INTERNAL_ERROR", message: "boom" } });
    failing.install();
    openStructure("ex-hsa-f-002");
    expect(await screen.findByRole("heading", { name: "Không tải được cấu trúc bài thi" })).toBeInTheDocument();
  });
});

describe("Thông tin chung", () => {
  it("bài đã công bố khóa kỳ thi và hình thức; đổi tên rồi Lưu", async () => {
    // Không chờ giữa các thao tác: bản chờ mặc định cộng dồn theo từng ký tự và làm ca này sát hạn 5 giây khi chạy cả bộ.
    const user = userEvent.setup({ delay: null });
    window.history.replaceState(null, "", "/exams/ex-qda-f-001");
    renderExamsUi(<ExamGeneralForm mode="edit" examId="ex-qda-f-001" />);
    const name = await screen.findByRole("textbox", { name: /Tên bài thi/ });
    expect(screen.getByRole("combobox", { name: /^Kỳ thi/ })).toBeDisabled();
    expect(screen.getByRole("radio", { name: /^Toàn bài/ })).toBeDisabled();
    expect(screen.getByText("Tiếng Anh + 2 tổ hợp")).toBeInTheDocument();
    await user.clear(name);
    // Dán một lần thay cho gõ từng ký tự: một lần render của biểu mẫu thay vì hơn hai mươi lần.
    await user.paste("QDA FULL · Đề luyện 1 (sửa)");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Cấu trúc và ma trận" }));
    await waitFor(() => expect(backend.app("ex-qda-f-001").name).toBe("QDA FULL · Đề luyện 1 (sửa)"));
    const facts = screen.getByRole("heading", { name: "Tình trạng" }).closest("div")!;
    expect(within(facts).getByText("Có bản sửa chưa công bố")).toBeInTheDocument();
  });
});
