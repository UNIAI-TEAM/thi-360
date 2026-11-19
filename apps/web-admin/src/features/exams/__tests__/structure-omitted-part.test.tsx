import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { ExamsBackend } from "./fake-exams-backend";
import { sp2eVanExam } from "./sp2e-van-fixture";

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

describe("Màn cấu trúc sau khi tạo SP2E Ngữ văn từ mẫu mặc định (S3-2)", () => {
  it("phần Viết chỉ có nhóm bỏ qua: không có lỗi cần xử lý nào", async () => {
    const exam = sp2eVanExam();
    backend.seedExam(exam);
    window.history.replaceState(null, "", `/exams/${exam.id}/structure`);
    renderWithProviders(<ExamStructureView examId={exam.id} />);
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    expect(await screen.findByRole("heading", { name: "Lỗi cần xử lý (0)" })).toBeInTheDocument();
    expect(screen.queryByText(/số câu phải là số nguyên dương/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Văn học: các nhóm có/i)).not.toBeInTheDocument();
  });
});
