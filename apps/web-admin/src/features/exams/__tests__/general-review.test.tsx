import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import type * as ContentApi from "@/features/content/api";
import { renderWithProviders } from "@/test/render";
import { ExamGeneralView } from "../components/exam-general-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const TEMPLATES_PATH = "/api/v1/admin/exam-templates";

type Wire = Record<string, unknown>;

const part = (name: string, count: number, extra: Wire = {}) => ({
  id: null,
  order: 1,
  name,
  durationMinutes: 60,
  questionCount: count,
  participation: { mode: "ALL" },
  mode: "FIXED",
  sections: [],
  ...extra,
});

/** Mẫu theo dạng máy chủ trả: `structure` là tài liệu `{ parts }`. */
function template(code: string, fields: Wire, parts: Wire[]): Wire {
  return { active: true, kindCode: "HSA", scope: "SUBJECT", timing: "PER_PART", buildMode: "STANDARD", isDefault: true, ...fields, code, structure: { parts } };
}

// Hình thật: mẫu bài ôn không ghi môn và không có thang; mẫu STANDARD của môn có thang 150.
const HSA_MATH = template("HSA_MATH", { subjectCode: "MATH", structureYear: 2025, maxScore: 150 }, [part("Toán chuẩn", 50)]);
const HSA_PHYSICS = template("HSA_PHYSICS", { subjectCode: "PHYSICS", structureYear: 2025, maxScore: 150 }, [part("Vật lí chuẩn", 40)]);
const HSA_REVIEW = template("HSA_REVIEW", { subjectCode: null, buildMode: "REVIEW", maxScore: null }, [part("Bài ôn", 0, { id: "HSA_REVIEW_PART" })]);

/** Bài ôn HSA còn nháp, chưa có thang. `buildMode` do máy chủ trả. */
const reviewExam = (overrides: Wire = {}) =>
  examDetailWire({
    code: "HSA-ON-001",
    name: "Bài ôn HSA",
    scope: "SUBJECT",
    subjectCode: "MATH",
    buildMode: "REVIEW",
    templateCode: "HSA_REVIEW",
    maxScore: undefined,
    ...overrides,
  } as never);

let server: FakeServer;

beforeEach(() => {
  server = new FakeServer()
    .on("GET", TEMPLATES_PATH, { status: 200, body: [HSA_MATH, HSA_PHYSICS, HSA_REVIEW] })
    .on("PATCH", EXAM_PATH, { status: 200, body: reviewExam({ revision: 8 }) });
  window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
});

async function renderGeneral(exam = reviewExam()) {
  server.on("GET", EXAM_PATH, { status: 200, body: exam }).install();
  const user = userEvent.setup();
  renderWithProviders(<ExamGeneralView examId={EXAM_ID} />);
  const scale = await screen.findByRole("textbox", { name: /^Thang điểm/ });
  return { user, scale };
}

describe("Thông tin chung của bài ôn (ADV:FIX-QA:3: bài ôn không có thang nếu người dùng không nhập)", () => {
  it("ô thang của bài ôn để trống, ghi là tùy chọn, không đánh dấu bắt buộc", async () => {
    const { scale } = await renderGeneral();
    expect(scale).toHaveValue("");
    expect(scale).not.toBeRequired();
    expect(screen.getByText("Để trống nếu thang điểm đặt lúc thiết lập bài")).toBeInTheDocument();
  });

  it("đổi môn: thang vẫn trống, hộp xác nhận hiện mẫu bài ôn, PATCH gửi maxScore null", async () => {
    const { user, scale } = await renderGeneral();
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: /Vật lí/ }));
    expect(scale).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("1. Vật lí")).toBeInTheDocument();
    expect(within(dialog).getByText(/0 câu/)).toBeInTheDocument();
    expect(within(dialog).queryByText(/Bài ôn/)).not.toBeInTheDocument();
    expect(within(dialog).queryByText(/Vật lí chuẩn/)).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    const body = server.calls("PATCH", EXAM_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ subjectCode: "PHYSICS", confirmReplace: true });
    expect(body.maxScore ?? null).toBeNull();
  });

  it("mẫu đã nạp không còn trong danh sách: bài ôn vẫn nhận ra từ buildMode của API, lưu với thang trống không bị chặn", async () => {
    const { user } = await renderGeneral(reviewExam({ templateCode: null }));
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Bài ôn HSA (sửa)");
    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));

    await waitFor(() => expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    const body = server.calls("PATCH", EXAM_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ name: "Bài ôn HSA (sửa)", confirmReplace: false });
    expect(body.maxScore ?? null).toBeNull();
  });
});

describe("Thông tin chung của bài ôn: đi vòng phạm vi (FE-R2-1)", () => {
  it("Toàn bài rồi Theo môn: thang vẫn trống, không bắt buộc, lưu không qua hộp xác nhận, maxScore null", async () => {
    const { user, scale } = await renderGeneral();
    await user.click(screen.getByRole("radio", { name: /^Toàn bài/ }));
    await user.click(screen.getByRole("radio", { name: /^Theo môn/ }));
    expect(scale).toHaveValue("");
    expect(scale).not.toBeRequired();

    await user.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(server.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const body = server.calls("PATCH", EXAM_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ scope: "SUBJECT", subjectCode: "MATH", confirmReplace: false });
    expect(body.maxScore ?? null).toBeNull();
  });
});
