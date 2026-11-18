import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { draftTotals } from "../components/exam-structure-draft";
import { ExamStructureView } from "../components/exam-structure-view";
import { BCA_COMBINATIONS, BCA_PARTS, codedExam, QDA_COMBINATIONS, QDA_PARTS, summaryRow } from "./coded-fixtures";
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

function openStructure(examId: string) {
  window.history.replaceState(null, "", `/exams/${examId}/structure`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

/** "150 / 150 điểm" của dải tổng bài -> số điểm đầu. */
function headerPoints(): number {
  const value = screen.getByText("Tổng điểm", { selector: "dt" }).nextElementSibling!.textContent!;
  return Number(/^\s*(\d+(?:,\d+)?)\s*\//.exec(value)![1]!.replace(",", "."));
}

/** Tổng cột Điểm của dòng phần trong bảng tổng hợp (mỗi dòng 4 ô: chỉ tiêu, đã phân bổ, chênh lệch, điểm). */
function summaryPartPointsTotal(): number {
  const table = screen.getByRole("table", { name: /Tổng hợp số câu theo phần/ });
  const cells = within(table).getAllByRole("cell").map((cell) => cell.textContent ?? "");
  let total = 0;
  for (let index = 3; index < cells.length; index += 4) total += Number(cells[index]!.replace(",", "."));
  return total;
}

describe("Tổng điểm của phần theo mã (F-5)", () => {
  it("QDA: phần 3 theo mã tính 50 điểm của một mã, tổng bài 150 như bảng tổng hợp", () => {
    const totals = draftTotals(QDA_PARTS, { combinations: QDA_COMBINATIONS });
    expect(totals).toMatchObject({ totalQuestions: 150, totalPoints: 150, allocated: 150 });
  });

  it("BCA: điểm phần 25 / 60 / 15, phần theo mã tính 15 điểm của một mã, tổng bài 100", () => {
    const totals = draftTotals(BCA_PARTS, { sharedDuration: 180, combinations: BCA_COMBINATIONS });
    expect(totals).toMatchObject({ totalPoints: 100, totalMinutes: 180 });
  });

  it("QDA trên màn hình: dải tổng bài '150 / 150 điểm' bằng tổng các dòng phần của bảng tổng hợp", async () => {
    const exam = codedExam("QDA");
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: QDA_COMBINATIONS });
    backend.seedSummary(exam.id, [
      summaryRow("qda-1", "Phần 1", 50, 50),
      summaryRow("qda-2", "Phần 2", 50, 50),
      summaryRow("qda-3", "Phần 3", 50, 50),
    ]);
    openStructure(exam.id);
    expect(await screen.findByText("150 / 150 điểm")).toBeInTheDocument();
    await screen.findByRole("table", { name: /Tổng hợp số câu theo phần/ });
    expect(headerPoints()).toBe(summaryPartPointsTotal());
  });

  it("BCA trên màn hình: dải tổng bài '100 / 100 điểm' bằng tổng các dòng phần của bảng tổng hợp", async () => {
    const exam = codedExam("BCA");
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: BCA_COMBINATIONS });
    backend.seedSummary(exam.id, [
      summaryRow("bca-1", "Phần 1", 1, 25),
      summaryRow("bca-2", "Phần 2", 60, 60),
      summaryRow("bca-3", "Phần 3", 15, 15),
    ]);
    openStructure(exam.id);
    expect(await screen.findByText("100 / 100 điểm")).toBeInTheDocument();
    await screen.findByRole("table", { name: /Tổng hợp số câu theo phần/ });
    expect(headerPoints()).toBe(summaryPartPointsTotal());
  });
});
