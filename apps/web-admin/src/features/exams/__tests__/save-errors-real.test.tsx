import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import type * as ContentApi from "@/features/content/api";
import { renderWithProviders } from "@/test/render";
import { ExamGeneralView } from "../components/exam-general-view";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

// Danh mục tham chiếu là dữ liệu của FE-1 (Nội dung): dùng bản mẫu cố định làm dữ liệu nền, không phụ thuộc cờ.
vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const REVISION_CONFLICT = { status: 412, body: { code: "EXAM_REVISION_CONFLICT", message: "stale" } };

let server: FakeServer;

beforeEach(() => {
  server = new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire() })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE_PATH, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [FIXED_PART] } })
    .install();
});

describe("lưu cấu trúc, nhánh thật", () => {
  async function editAndSave() {
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?part=part-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
    const duration = await screen.findByRole("textbox", { name: /Thời lượng \(phút\)/ });
    await user.clear(duration);
    await user.type(duration, "65");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    return { user };
  }

  it("gửi expectedRevision của bài đang hiển thị", async () => {
    server.on("PUT", STRUCTURE_PATH, { status: 200, body: examDetailWire({ revision: 8 }) });
    await editAndSave();

    await waitFor(() => expect(server.calls("PUT", STRUCTURE_PATH)).toHaveLength(1));
    expect(server.calls("PUT", STRUCTURE_PATH)[0]!.body).toMatchObject({ expectedRevision: 7 });
  });

  it("412 báo bài đã được người khác sửa, giữ thay đổi chưa lưu", async () => {
    server.on("PUT", STRUCTURE_PATH, REVISION_CONFLICT);
    await editAndSave();

    expect((await screen.findAllByText(/Bài thi vừa được người khác sửa/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Không lưu được cấu trúc/)).not.toBeInTheDocument();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });

  it("422 báo cấu trúc có giá trị không hợp lệ, không in mã thô của máy chủ", async () => {
    server.on("PUT", STRUCTURE_PATH, {
      status: 422,
      body: {
        code: "EXAM_STRUCTURE_INVALID",
        message: "durationMinutes must be positive",
        fieldErrors: [{ field: "parts[0].durationMinutes", code: "NEGATIVE" }],
      },
    });
    await editAndSave();

    expect((await screen.findAllByText(/Cấu trúc có giá trị không hợp lệ/)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/must be positive/)).not.toBeInTheDocument();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  });
});

describe("lưu thông tin chung, nhánh thật", () => {
  async function renameAndSave() {
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
    renderWithProviders(<ExamGeneralView examId={EXAM_ID} />);
    const name = await screen.findByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "HSA đề 1 (sửa)");
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    return { name };
  }

  it("gửi expectedRevision của bài đang hiển thị", async () => {
    server.on("PATCH", EXAM_PATH, { status: 200, body: examDetailWire({ name: "HSA đề 1 (sửa)", revision: 8 }) });
    await renameAndSave();

    await waitFor(() => expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(server.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ expectedRevision: 7, name: "HSA đề 1 (sửa)" });
  });

  it("412 báo bài đã được người khác sửa thay vì lỗi chung", async () => {
    server.on("PATCH", EXAM_PATH, REVISION_CONFLICT);
    await renameAndSave();

    expect(await screen.findByText(/Bài thi vừa được người khác sửa/)).toBeInTheDocument();
    expect(screen.queryByText(/Không lưu được thông tin chung/)).not.toBeInTheDocument();
  });

  it("422 theo trường đánh dấu đúng ô nhập bằng câu tiếng Việt", async () => {
    server.on("PATCH", EXAM_PATH, {
      status: 422,
      body: { code: "EXAM_FIELD_INVALID", message: "name too long", fieldErrors: [{ field: "name", code: "SIZE" }] },
    });
    const { name } = await renameAndSave();

    expect(await screen.findByText("Giá trị không hợp lệ.")).toBeInTheDocument();
    expect(name).toBeInvalid();
    expect(screen.queryByText(/name too long/)).not.toBeInTheDocument();
  });

  it("422 không gắn được vào ô nhập thì báo chung bằng tiếng Việt", async () => {
    server.on("PATCH", EXAM_PATH, { status: 422, body: { code: "EXAM_SCOPE_NOT_ALLOWED", message: "scope" } });
    await renameAndSave();

    expect(await screen.findByText(/Thông tin bài thi có giá trị không hợp lệ/)).toBeInTheDocument();
    expect(screen.queryByText(/Không lưu được thông tin chung/)).not.toBeInTheDocument();
  });
});
