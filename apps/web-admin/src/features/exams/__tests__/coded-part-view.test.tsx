import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { codedExam, QDA_COMBINATIONS, QDA_PARTS } from "./coded-fixtures";
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

function openPart(examId: string, partId: string) {
  window.history.replaceState(null, "", `/exams/${examId}/structure?part=${partId}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

describe("Phần theo mã mở được khi quy tắc tham gia là CHOICE (R1-01)", () => {
  it("QDA phần 3 (CHOICE như dữ liệu seed và di trú): hiện bảng môn của phần, mã và vị trí câu", async () => {
    expect(QDA_PARTS[2]!.mode).toBe("CHOICE");
    expect(QDA_PARTS[2]!.participation.mode).toBe("CHOICE");
    const exam = codedExam("QDA");
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: QDA_COMBINATIONS });
    openPart(exam.id, "qda-3");

    const subjects = await screen.findByRole("table", { name: /Các môn của Part 3/ });
    expect(within(subjects).getAllByRole("row")).toHaveLength(1 + QDA_PARTS[2]!.subjectPool!.length);
    expect(within(subjects).getAllByRole("button", { name: /Thiết lập môn/ })).toHaveLength(QDA_PARTS[2]!.subjectPool!.length);
    expect(screen.getByRole("heading", { name: "Mã của kỳ thi (chỉ xem)" })).toBeInTheDocument();
    expect(screen.getAllByText("QD01").length).toBeGreaterThan(0);
    expect(screen.queryByText(/trong \d+ phương án/)).not.toBeInTheDocument();
  });

  it("R1-13: số câu thập phân của máy chủ vào thông điệp ICU qua roundForDisplay, tối đa 2 chữ số (7,125 → 7,13)", async () => {
    const exam = codedExam("QDA");
    const pool = structuredClone(QDA_PARTS[2]!.subjectPool!);
    pool[0]!.blocks[0]!.questionCount = 7.125;
    exam.parts = exam.parts.map((part) => (part.id === "qda-3" ? { ...part, subjectPool: pool } : part));
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: QDA_COMBINATIONS });
    openPart(exam.id, "qda-3");

    const subjects = await screen.findByRole("table", { name: /Các môn của Part 3/ });
    const row = within(subjects).getByRole("row", { name: /Vật lí/ });
    expect(row).toHaveTextContent("7,13");
    expect(row).not.toHaveTextContent("7,125");
  });

  it("R1-22: thêm môn còn thiếu thì cảnh báo và nút biến mất ngay, môn vào bảng, không đợi lưu", async () => {
    const user = userEvent.setup();
    const exam = codedExam("QDA");
    const missing = { ...QDA_COMBINATIONS[0]!, code: "QD06", examCode: "QD06", subjectCodes: ["PHYSICS", "INFORMATICS"], missingSubjects: ["INFORMATICS"] };
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: [...QDA_COMBINATIONS, missing] });
    openPart(exam.id, "qda-3");

    const add = await screen.findByRole("button", { name: /^Thêm môn/ });
    const before = within(await screen.findByRole("table", { name: /Các môn của Part 3/ })).getAllByRole("row").length;
    await user.click(add);
    expect(screen.queryByRole("button", { name: /^Thêm môn/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/^Mã QD06 thiếu môn/)).not.toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: /Các môn của Part 3/ })).getAllByRole("row")).toHaveLength(before + 1);
  });

  it("UNI-1728 / UNI-2837: bảng phần theo mã hiện CA1..CA4 đúng mã tổ hợp khi examCode từ máy chủ là BCA", async () => {
    const exam = codedExam("BCA");
    const bcaCombos = [
      { code: "CA1", examCode: "BCA", partId: "bca-3", subjectCodes: ["PHYSICS"], missingSubjects: [], nameVi: "CA1", nameEn: "CA1" },
      { code: "CA2", examCode: "BCA", partId: "bca-3", subjectCodes: ["CHEMISTRY"], missingSubjects: [], nameVi: "CA2", nameEn: "CA2" },
      { code: "CA3", examCode: "BCA", partId: "bca-3", subjectCodes: ["BIOLOGY"], missingSubjects: [], nameVi: "CA3", nameEn: "CA3" },
      { code: "CA4", examCode: "BCA", partId: "bca-3", subjectCodes: ["GEOGRAPHY"], missingSubjects: [], nameVi: "CA4", nameEn: "CA4" },
    ];
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations: bcaCombos });
    openPart(exam.id, "bca-3");

    const subjects = await screen.findByRole("table", { name: /Các môn của/ });
    expect(within(subjects).getByRole("row", { name: /Vật lí/ })).toHaveTextContent("CA1");
    expect(within(subjects).getByRole("row", { name: /Hóa học/ })).toHaveTextContent("CA2");
    expect(within(subjects).getByRole("row", { name: /Sinh học/ })).toHaveTextContent("CA3");
    expect(within(subjects).getByRole("row", { name: /Địa lí/ })).toHaveTextContent("CA4");
    expect(within(subjects).queryByText("BCA")).not.toBeInTheDocument();
    for (const code of ["CA1", "CA2", "CA3", "CA4"]) {
      expect(screen.getAllByText(code).length).toBeGreaterThanOrEqual(2);
    }
  });
});
