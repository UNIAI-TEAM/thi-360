import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createExam } from "../api";
import { ExamGeneralView } from "../components/exam-general-view";
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

function facts() {
  const panel = screen.getByRole("heading", { name: "Tình trạng" }).closest("div");
  if (!panel) throw new Error("khong thay Tinh trang");
  return within(panel);
}

describe("Thông tin chung: trường mới của bảy kỳ thi", () => {
  it("BCA: thời lượng chung, thang điểm và tự luận hiện ở Tình trạng; sửa thời lượng chung rồi Lưu", async () => {
    const exam = await createExam({
      name: "BCA thu",
      code: "BCA-F-902",
      kind: "BCA",
      scope: "FULL",
      description: null,
    });
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${exam.id}`);
    renderWithProviders(<ExamGeneralView examId={exam.id} />);
    const shared = await screen.findByRole("textbox", { name: /Thời lượng chung/ });
    expect(shared).toHaveValue("180");
    expect(screen.getByRole("radio", { name: /BCA/ })).toBeChecked();
    expect(facts().getByText("3 phần · 81 câu · 180 phút chung")).toBeInTheDocument();
    expect(facts().getByText("100 điểm")).toBeInTheDocument();
    expect(facts().getByText("Có câu tự luận, chấm tay")).toBeInTheDocument();

    await user.clear(shared);
    await user.type(shared, "170");
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(backend.app(exam.id).durationMinutes).toBe(170));
    expect(backend.app(exam.id).parts).toHaveLength(3);
  });

  it("SP2E Toán 2026: chờ chuyên gia xác nhận năm ma trận; xác nhận rồi Lưu", async () => {
    const exam = await createExam({
      name: "SP2E thu",
      code: "SP2E-F-TOAN-902",
      kind: "SP2E",
      scope: "SUBJECT",
      subjectCode: "MATH",
      description: null,
    });
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${exam.id}`);
    renderWithProviders(<ExamGeneralView examId={exam.id} />);
    const confirm = await screen.findByRole("checkbox", { name: /Chuyên gia đã xác nhận chỉ tiêu/ });
    expect(confirm).not.toBeChecked();
    expect(facts().getByText("Năm cấu trúc 2026 · năm ma trận 2025")).toBeInTheDocument();
    expect(facts().getByText("Chờ chuyên gia xác nhận")).toBeInTheDocument();

    await user.click(confirm);
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(backend.app(exam.id).yearsConfirmed).toBe(true));
    expect(await facts().findByText("Chuyên gia đã xác nhận")).toBeInTheDocument();
  });
});
