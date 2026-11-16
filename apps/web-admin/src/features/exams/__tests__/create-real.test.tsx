import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api.mock";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { EXAM_ID, examDetailWire, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

const EXAMS_PATH = "/api/v1/admin/exams";
const STRUCTURE_PATH = `${EXAMS_PATH}/${EXAM_ID}/structure`;

const TEMPLATE = {
  active: true,
  code: "HSA_FULL_2025",
  kindCode: "HSA",
  scope: "FULL",
  timing: "PER_PART",
  structure: {
    parts: [
      {
        id: null,
        order: 1,
        name: "Phần 1",
        durationMinutes: 60,
        questionCount: 10,
        participation: { mode: "ALL" },
        sections: [
          {
            id: null,
            subjectCode: "MATH",
            questionCount: 10,
            groupOrder: "FIXED",
            blocks: [{ id: null, name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 10, requiredPointsPerQuestion: 1 }],
          },
        ],
      },
    ],
  },
};

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
});

describe("trang Tạo bài thi, nhánh thật", () => {
  it("POST 201 rồi PUT cấu trúc 422: chuyển sang tab Cấu trúc của bài đã tạo kèm thông báo, chỉ POST một lần", async () => {
    const server = new FakeServer()
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [TEMPLATE] })
      .on("GET", `${EXAMS_PATH}/suggested-code`, { status: 200, body: { code: "HSA-F-001" } })
      .on("POST", EXAMS_PATH, { status: 201, body: examDetailWire({ revision: 1 }) })
      .on("PUT", STRUCTURE_PATH, { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", fieldErrors: [] } })
      .install();
    const user = userEvent.setup();
    renderWithProviders(<CreateExamPage />);
    await screen.findByRole("form", { name: "Tạo bài thi" });
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-F-001"));
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA thử");

    // Sửa cấu trúc so với mẫu: đổi thời lượng của phần.
    const duration = await waitFor(() => {
      const field = document.querySelector<HTMLInputElement>('input[name="parts.0.durationMinutes"]');
      if (!field) throw new Error("chưa có ô thời lượng của phần 1");
      return field;
    });
    await user.clear(duration);
    await user.type(duration, "45");
    await user.click(screen.getAllByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" })[0]!);

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    expect(server.calls("POST", EXAMS_PATH)).toHaveLength(1);
    expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(await screen.findByText(/Đã tạo bài thi HSA-F-001 với cấu trúc của mẫu/)).toBeInTheDocument();
  });
});
