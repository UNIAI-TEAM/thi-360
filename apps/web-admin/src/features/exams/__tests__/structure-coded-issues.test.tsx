import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapParts, toApiParts } from "../api-mapping";
import { validateDraft } from "../components/exam-structure-draft";
import { ExamStructureView } from "../components/exam-structure-view";
import { AXES, BCA_COMBINATIONS, BCA_PARTS, codedExam, QDA_COMBINATIONS, QDA_PARTS } from "./coded-fixtures";
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

/** Máy chủ là nơi quyết định đường của phần theo mã (Global List); màn không tự báo "chưa có nhánh" cho phần đó. */
describe("Phần theo mã không bị báo lỗi 'chưa có nhánh' (F-11)", () => {
  it("máy chủ trả phần theo mã: giữ codeSource và subjectPool, gửi lại không làm mất môn của phần", () => {
    const read = mapParts(toApiParts(QDA_PARTS));
    expect(read[2]!.codeSource).toEqual({ source: "GLOBAL_LIST", branch: null });
    expect(read[2]!.subjectPool!.map((item) => item.subjectCode)).toEqual(["PHYSICS", "CHEMISTRY", "BIOLOGY", "HISTORY", "GEOGRAPHY", "ENGLISH"]);
    expect(toApiParts(read)[2]).toMatchObject({ codeSource: { source: "GLOBAL_LIST" }, subjectPool: expect.any(Array) });
    expect(read[0]!.codeSource).toBeUndefined();
  });

  it("QDA và BCA: phần theo mã không có phương án nào vẫn không có lỗi cấu trúc", () => {
    for (const [parts, timing] of [
      [QDA_PARTS, "PER_PART"],
      [BCA_PARTS, "SHARED"],
    ] as const) {
      expect(parts[2]!.options).toEqual([]);
      expect(validateDraft(parts, AXES, { timing })).toEqual([]);
    }
  });

  it.each([
    ["QDA", QDA_COMBINATIONS],
    ["BCA", BCA_COMBINATIONS],
  ] as const)("%s: máy chủ kiểm tra Hợp lệ -> không có 'Lỗi cần xử lý' ở phần 3, tổng hợp và kết quả cùng Đạt", async (kind, combinations) => {
    const user = userEvent.setup();
    const exam = codedExam(kind);
    backend.seedExam(exam).seedStructureExtras(exam.id, { combinations });
    openStructure(exam.id);
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    expect(await screen.findByRole("heading", { name: "Lỗi cần xử lý (0)" })).toBeInTheDocument();
    expect(screen.queryByText(/chưa có nhánh nào/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    const result = await screen.findByTestId("structure-quick-check-result");
    expect(result).toHaveTextContent("Cấu trúc hợp lệ");
    expect(screen.getByRole("heading", { name: "Lỗi cần xử lý (0)" })).toBeInTheDocument();
    expect(screen.queryByText(/chưa có nhánh nào/)).not.toBeInTheDocument();
  });
});
