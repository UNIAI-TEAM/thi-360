import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { EXAM_ID, examDetailWire, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";

type Wire = Record<string, unknown>;

/**
 * Hình thật của mẫu mặc định SP2E Ngữ văn (đề minh họa cũ, K15): Phần I có 4 văn bản (30 câu, 6 điểm); Phần II · Viết
 * chỉ có nhóm Viết bài tạm bỏ qua (`omitted`), nên số câu của phần là 0 theo thiết kế.
 */
const SP2E_VAN_OLD: Wire = {
  active: true,
  isDefault: true,
  code: "SP2E_VAN_OLD",
  kindCode: "SP2E",
  scope: "SUBJECT",
  subjectCode: "LITERATURE",
  structureYear: 2025,
  matrixYear: 2025,
  timing: "SHARED",
  durationMinutes: null,
  maxScore: 6,
  buildMode: "STANDARD",
  label: "SP2E – Ngữ văn – Đọc hiểu",
  parts: [],
  structure: {
    parts: [
      {
        id: "SP2E_VAN_P1",
        name: "Phần I · Đọc hiểu",
        order: 1,
        questionCount: 30,
        questionCountOnPaper: 30,
        questionCountRequired: 30,
        participation: { mode: "ALL" },
        maxPoints: 6,
        options: [],
        sections: [
          {
            id: "SP2E_VAN_S1",
            subjectCode: "LITERATURE",
            questionCount: 30,
            blocks: [
              { id: "b1", name: "Văn bản 1", questionType: "QUESTION_SET", questionCount: 8, setCount: 1, setSize: 8, childAnswerType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.2 },
              { id: "b2", name: "Văn bản 2", questionType: "QUESTION_SET", questionCount: 8, setCount: 1, setSize: 8, childAnswerType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.2 },
              { id: "b3", name: "Văn bản 3", questionType: "QUESTION_SET", questionCount: 7, setCount: 1, setSize: 7, childAnswerType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.2 },
              { id: "b4", name: "Văn bản 4", questionType: "QUESTION_SET", questionCount: 7, setCount: 1, setSize: 7, childAnswerType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.2 },
            ],
          },
        ],
      },
      {
        id: "SP2E_VAN_P2",
        name: "Phần II · Viết",
        order: 2,
        questionCount: 0,
        questionCountOnPaper: 0,
        questionCountRequired: 0,
        participation: { mode: "ALL" },
        maxPoints: null,
        options: [],
        sections: [
          {
            id: "SP2E_VAN_S2",
            subjectCode: "LITERATURE",
            questionCount: 0,
            blocks: [{ id: "b5", name: "Viết bài", questionType: "ESSAY", questionCount: 1, omitted: true }],
          },
        ],
      },
    ],
  },
};

const HSA_FULL: Wire = {
  active: true,
  isDefault: true,
  code: "HSA_FULL",
  kindCode: "HSA",
  scope: "FULL",
  timing: "PER_PART",
  buildMode: "STANDARD",
  label: "HSA · Toàn bài",
  parts: [],
  structure: {
    parts: [
      {
        id: null,
        order: 1,
        name: "Phần 1",
        durationMinutes: 60,
        questionCount: 10,
        participation: { mode: "ALL" },
        mode: "FIXED",
        sections: [{ id: null, subjectCode: "MATH", questionCount: 10, blocks: [{ id: null, name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 10 }] }],
      },
    ],
  },
};

async function pickLiterature(user: UserEvent) {
  await user.click(screen.getByRole("combobox", { name: /^Kỳ thi/ }));
  await user.click(await screen.findByRole("option", { name: /\(SP2E\)$/ }));
  await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
  await user.click(await screen.findByRole("option", { name: "Ngữ văn" }));
}

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
});

describe("Tạo bài thi: phần chỉ gồm nhóm tạm bỏ qua (F5, ADV:FIX-QA:4)", () => {
  async function open(server: FakeServer) {
    server.install();
    const user = userEvent.setup();
    renderWithProviders(<CreateExamPage />);
    await screen.findByRole("form", { name: "Tạo bài thi" });
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-FULL-001"));
    return user;
  }

  const server = () =>
    new FakeServer()
      .on("GET", TEMPLATES_PATH, { status: 200, body: [HSA_FULL, SP2E_VAN_OLD] })
      .on("GET", `${EXAMS_PATH}/suggested-code`, { status: 200, body: { code: "HSA-FULL-001" } })
      .on("POST", EXAMS_PATH, { status: 201, body: examDetailWire({ id: EXAM_ID }) });

  it("SP2E Ngữ văn để nguyên mẫu: Phần II · Viết chỉ đọc (tạm bỏ qua), không ô nhập, không lỗi; tạo được 30 câu, 6 điểm, không gửi overview", async () => {
    const fake = server();
    const user = await open(fake);
    await pickLiterature(user);

    const row = within(await screen.findByRole("table")).getByText("Phần II · Viết").closest("tr")!;
    expect(within(row).getByText("tạm bỏ qua")).toBeInTheDocument();
    expect(within(row).queryByRole("textbox")).toBeNull();
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(1);

    // Mẫu SHARED chưa ghi thời lượng chung (chờ chuyên gia): người tạo nhập; đây không phải ô của Phần II.
    await user.type(screen.getByRole("textbox", { name: /^Thời lượng chung/ }), "60");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "SP2E Ngữ văn");
    await user.click(screen.getAllByRole("button", { name: "Lưu và tiếp tục" })[0]!);
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    expect(screen.queryByText(/Số câu phải là số nguyên dương/)).toBeNull();
    const body = fake.calls("POST", EXAMS_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ kind: "SP2E", scope: "SUBJECT", subjectCode: "LITERATURE", maxScore: 6 });
    expect(body).not.toHaveProperty("overview");
  }, 30_000);

  it("tổng cuối bảng: 30 câu (phần chỉ có nhóm tạm bỏ qua không cộng thêm)", async () => {
    const user = await open(server());
    await pickLiterature(user);
    const row = within(await screen.findByRole("table")).getByText("Phần II · Viết").closest("tr")!;
    expect(within(row).queryByRole("textbox")).toBeNull();
    const footer = screen.getByRole("table").querySelector("tfoot")!;
    expect(within(footer).getByText("30")).toBeInTheDocument();
  }, 30_000);
});
