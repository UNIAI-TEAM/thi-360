import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamGeneralView } from "../components/exam-general-view";
import { ExamStructureView } from "../components/exam-structure-view";
import { examStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(examStore.get("ex-qda-f-001")?.check.status).toBe("NEEDS_RECHECK"));
    expect(examStore.get("ex-qda-f-001")).toMatchObject({ hasUnpublishedChanges: true, durationMinutes: 200 });
  });

  it("khối theo %: quy đổi phần dư lớn nhất hiện phần nguyên và phần dư", async () => {
    const user = userEvent.setup();
    openStructure("ex-hsa-f-001");
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

  it("trạng thái rỗng và lỗi theo ?mockState", async () => {
    const { unmount } = openStructure("ex-hsa-f-002", "?mockState=empty");
    expect(await screen.findByRole("heading", { name: "Bài thi chưa có phần thi" })).toBeInTheDocument();
    unmount();
    openStructure("ex-hsa-f-002", "?mockState=error");
    expect(await screen.findByRole("heading", { name: "Không tải được cấu trúc bài thi" })).toBeInTheDocument();
  });
});

describe("Thông tin chung", () => {
  it("bài đã công bố khóa kỳ thi và hình thức; đổi tên rồi Lưu", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", "/exams/ex-qda-f-001");
    renderWithProviders(<ExamGeneralView examId="ex-qda-f-001" />);
    const name = await screen.findByRole("textbox", { name: /Tên bài thi/ });
    expect(screen.getByRole("radio", { name: /HSA/ })).toBeDisabled();
    expect(screen.getByText("Tiếng Anh + 2 tổ hợp")).toBeInTheDocument();
    await user.clear(name);
    await user.type(name, "QDA FULL · Đề luyện 1 (sửa)");
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(examStore.get("ex-qda-f-001")?.name).toBe("QDA FULL · Đề luyện 1 (sửa)"));
    const facts = screen.getByRole("heading", { name: "Tình trạng" }).closest("div")!;
    expect(within(facts).getByText("Có bản sửa chưa công bố")).toBeInTheDocument();
  });
});
