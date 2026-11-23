import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { draftTotals } from "../components/exam-structure-draft";
import { ExamStructureView } from "../components/exam-structure-view";
import { ExamsBackend } from "./fake-exams-backend";

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

describe("Cấu trúc theo bảy kỳ thi", () => {
  it("THPTQG Tin học: thời lượng chung không có ô nhập; phần chung và hai nhánh; thêm nhánh", async () => {
    const user = userEvent.setup();
    openStructure("ex-thptqg-tin-001", "?part=part-t2");
    expect(await screen.findByRole("heading", { name: "Phần II · Đúng/Sai" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Tổng hợp cấu trúc")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /Thời lượng \(phút\)/ })).not.toBeInTheDocument();
    expect(screen.getByText("50 phút, dùng chung cả bài")).toBeInTheDocument();
    expect(draftTotals(backend.app("ex-thptqg-tin-001")!.parts)).toMatchObject({
      totalQuestions: 28,
      printedQuestions: 30,
    });
    expect(screen.getByRole("textbox", { name: /Số câu phải làm/ })).toHaveValue("4");
    expect(screen.getByRole("heading", { name: "Phần chung của mọi nhánh" })).toBeInTheDocument();
    expect(screen.getByText("Nhánh (2)")).toBeInTheDocument();
    expect(screen.getAllByText(/Khoa học máy tính/).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Thêm nhánh" }));
    const dialog = await screen.findByRole("dialog", { name: "Thêm nhánh" });
    await user.click(within(dialog).getByRole("button", { name: "Thêm nhánh" }));
    expect(await within(dialog).findByText("Nhập tên nhánh.")).toBeInTheDocument();
    await user.type(within(dialog).getByRole("textbox", { name: /Tên nhánh/ }), "Lập trình");
    await user.click(within(dialog).getByRole("button", { name: "Thêm nhánh" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByText("Nhánh (3)")).toBeInTheDocument();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });

  it("BCA: điểm phần và thang 100", async () => {
    openStructure("ex-bca-f-001", "?part=part-b3");
    expect(await screen.findByRole("heading", { name: "Phần 3 · Tự chọn" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Tổng hợp cấu trúc")).not.toBeInTheDocument();
    const exam = backend.app("ex-bca-f-001")!;
    expect(exam.maxScore).toBe(100);
    expect(draftTotals(exam.parts).totalPoints).toBe(100);
    expect(screen.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue("15");
  });

  it("V-ACT: khối Tư duy khoa học chia theo năng lực", async () => {
    openStructure("ex-vact-f-001", "?block=blk-v3");
    expect(await screen.findByRole("switch", { name: "Phân bổ theo năng lực" })).toBeChecked();
    expect(screen.getByRole("table", { name: "Theo năng lực" })).toBeInTheDocument();
    expect(screen.getAllByText(/Logic, phân tích số liệu/).length).toBeGreaterThan(0);
  });
});
