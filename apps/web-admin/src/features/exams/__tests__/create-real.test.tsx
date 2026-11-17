import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
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
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
});

describe("trang Tạo bài thi, nhánh thật", () => {
  it("POST 201 rồi PUT cấu trúc 422: ở lại trang kèm thông báo; gửi lại chỉ PUT cấu trúc đã sửa, không POST lần hai (N2-4)", async () => {
    let puts = 0;
    const server = new FakeServer()
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [TEMPLATE] })
      .on("GET", `${EXAMS_PATH}/suggested-code`, { status: 200, body: { code: "HSA-F-001" } })
      .on("POST", EXAMS_PATH, { status: 201, body: examDetailWire({ revision: 1 }) })
      .on("PUT", STRUCTURE_PATH, () => {
        puts += 1;
        return puts === 1
          ? { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", fieldErrors: [] } }
          : { status: 200, body: examDetailWire({ revision: 2 }) };
      })
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

    expect(await screen.findAllByText(/Đã tạo bài thi HSA-F-001 với cấu trúc của mẫu/)).not.toHaveLength(0);
    // Ở lại trang với các chỉnh sửa còn nguyên, kèm đường dẫn mở bài đã tạo.
    expect(routerMock.push).not.toHaveBeenCalled();
    expect(duration).toHaveValue("45");
    expect(screen.getByRole("link", { name: "Mở bài HSA-F-001 để sửa ở tab Cấu trúc" })).toHaveAttribute(
      "href",
      `/exams/${EXAM_ID}/structure`,
    );

    await user.click(screen.getAllByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" })[0]!);
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    expect(server.calls("POST", EXAMS_PATH)).toHaveLength(1);
    expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(2);
  });
});

const MATH_PART_TEMPLATE = { ...TEMPLATE, code: "HSA_PART_MATH_2025", scope: "PART", partCode: "MATH" };

function createServer() {
  return new FakeServer()
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [TEMPLATE, MATH_PART_TEMPLATE] })
    .on("GET", `${EXAMS_PATH}/suggested-code`, (request) => ({
      status: 200,
      body: { code: request.url.searchParams.get("scope") === "PART" ? "HSA-P-TOAN-001" : "HSA-F-001" },
    }));
}

async function renderPage() {
  const user = userEvent.setup();
  renderWithProviders(<CreateExamPage />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-F-001"));
  return user;
}

function submit() {
  return screen.getAllByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" })[0]!;
}

describe("trang Tạo bài thi, nhánh thật: gửi và lỗi", () => {
  it("409 EXAM_CODE_TAKEN: báo lỗi ở ô Mã, ở lại trang", async () => {
    createServer().on("POST", EXAMS_PATH, { status: 409, body: { code: "EXAM_CODE_TAKEN" } }).install();
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "Trùng mã");
    const code = screen.getByRole("textbox", { name: /Mã bài thi/ });
    await user.clear(code);
    await user.type(code, "HSA-F-009");

    await user.click(submit());

    await waitFor(() => expect(code).toHaveAttribute("aria-invalid", "true"));
    expect(await screen.findAllByText("Mã bài thi đã được dùng. Nhập mã khác.")).not.toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 20_000);

  it("phạm vi Theo phần gửi mã phần và hướng dẫn làm bài; cấu trúc chưa sửa thì không PUT", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /Theo phần/ }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-P-TOAN-001"));
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA Toán · Đề thử");
    // Trình soạn TipTap: jsdom không có elementFromPoint nên không gõ qua click; focus rồi dán văn bản thuần.
    screen.getByRole("textbox", { name: /Hướng dẫn làm bài/ }).focus();
    await user.paste("Đọc kỹ đề trước khi làm.");
    const score = screen.getByRole("textbox", { name: /Thang điểm/ });
    await user.clear(score);
    await user.type(score, "50");

    await user.click(submit());

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    expect(server.calls("POST", EXAMS_PATH)).toHaveLength(1);
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({
      code: "HSA-P-TOAN-001",
      scope: "PART",
      partCode: "MATH",
      maxScore: 50,
      instructions: "<p>Đọc kỹ đề trước khi làm.</p>",
    });
    expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
  }, 20_000);
});
