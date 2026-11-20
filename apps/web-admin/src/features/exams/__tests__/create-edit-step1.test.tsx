import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import type { ExamDetail, ExamPart, QuestionBlock } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireTemplate } from "./fake-exams-backend";

/**
 * UNI-2751 (BA 1697): quay lại bước 1 từ bước Cấu trúc mở đúng màn Tạo bài thi (`/exams/new?exam=<id>`): thanh bước ở
 * bước 1, ba khối đánh số, cột Tóm tắt, điền sẵn theo bài đã lưu; "Lưu và tiếp tục" lưu rồi về bước 2. Tab Thông tin chung
 * không đổi (kiểm ở general-overview.test.tsx).
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAM_ID = "ex-edit-step1";
const EXAM_PATH = `/api/v1/admin/exams/${EXAM_ID}`;
const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const CONTINUE = "Lưu và tiếp tục";
const MATH = "Toán học và xử lí số liệu";
const LITERATURE = "Ngôn ngữ – Văn học";
const SCIENCE = "Khoa học tự nhiên";

const block = (id: string, count: number): QuestionBlock => ({
  id,
  name: `Khối ${id}`,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

const hsaPart = (order: number, name: string, count: number, minutes: number, subjectCode: string): ExamPart => ({
  id: `part-${order}`,
  order,
  name,
  durationMinutes: minutes,
  questionCount: count,
  questionCountOnPaper: 50,
  questionCountRequired: count,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [
    {
      id: `sec-${order}`,
      subjectCode,
      questionCount: count,
      knowledgeScope: null,
      answerQuotas: null,
      matrix: null,
      choiceGroup: null,
      blocks: [block(`blk-${order}`, 50)],
    },
  ],
  options: [],
});

let backend: ExamsBackend;
let seeded: ExamDetail;

function setup(override: Partial<ExamDetail> = {}, configure?: (backend: ExamsBackend) => void) {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  configure?.(backend);
  backend.install();
  seeded = {
    ...backend.app("ex-hsa-f-001"),
    id: EXAM_ID,
    code: "HSA-EDIT-001",
    name: "HSA quay lại bước 1",
    kind: "HSA",
    scope: "FULL",
    timing: "PER_PART",
    templateCode: "HSA_FULL",
    servingVersion: null,
    parts: [hsaPart(1, MATH, 45, 75, "MATH"), hsaPart(2, LITERATURE, 50, 60, "LITERATURE"), hsaPart(3, SCIENCE, 50, 60, "PHYSICS")],
    ...override,
  };
  backend.seedExam(seeded);
}

async function open() {
  window.history.replaceState(null, "", `/exams/new?exam=${EXAM_ID}`);
  const view = renderWithProviders(<CreateExamPage />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
  return view;
}

const countOf = (name: string) => screen.getByRole("textbox", { name: `Số câu của ${name}` });

/** Mẫu Theo môn (STANDARD) của Toán và mẫu bài ôn HSA như máy chủ trả: có mẫu thì môn đã lưu được giữ khi đổi cách dựng bài. */
function subjectTemplates(server: ExamsBackend) {
  const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
  const standard = toWireTemplate({ ...structuredClone(hsaFull), code: "HSA_SUBJECT_MATH", scope: "SUBJECT", subjectCode: "MATH", parts: [structuredClone(hsaFull.parts[0]!)] });
  const review = {
    ...toWireTemplate({
      ...structuredClone(hsaFull),
      code: "HSA_REVIEW",
      scope: "SUBJECT",
      subjectCode: null,
      maxScore: null,
      parts: [{ ...structuredClone(hsaFull.parts[0]!), name: "Review", questionCount: 0, durationMinutes: null, sections: [] }],
    }),
    buildMode: "REVIEW" as const,
  };
  server.server.on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [...FIXTURE_TEMPLATES.map(toWireTemplate), standard, review] });
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Bước 1 ở chế độ sửa (/exams/new?exam=<id>, UNI-2751)", () => {
  it("AC-1697-1: màn Tạo bài thi (thanh bước ở bước 1, ba khối đánh số, Tóm tắt) điền sẵn theo bài đã lưu", async () => {
    setup();
    await open();
    expect(screen.getByRole("heading", { name: "Tạo bài thi", level: 1 })).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: /Các bước/ })).getByText(/Thông tin chung/).closest("[aria-current]")).toHaveAttribute("aria-current", "step");
    const headings = screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent);
    expect(headings).toEqual(expect.arrayContaining(["Thông tin chung", "Phạm vi bài thi", "Cấu trúc tổng quan", "Tóm tắt"]));
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("HSA quay lại bước 1");
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-EDIT-001");
    const table = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    expect(within(table).getAllByRole("textbox", { name: /^Số câu của/ }).map((input) => (input as HTMLInputElement).value)).toEqual(["45", "50", "50"]);
    // Không phải tab Thông tin chung: không có cột Tình trạng.
    expect(screen.queryByRole("heading", { name: "Tình trạng" })).not.toBeInTheDocument();
    expect(screen.getByText(/Đang sửa bài HSA-EDIT-001/)).toBeInTheDocument();
  }, 30_000);

  it("AC-1697-2: sửa số câu phần 1 rồi Lưu và tiếp tục ghi cấu trúc của bài đó (không tạo bài mới) và về bước 2", async () => {
    setup();
    const user = userEvent.setup();
    await open();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "40");
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    const body = backend.calls("PUT", STRUCTURE_PATH)[0]!.body as { expectedRevision: number; parts: ExamPart[] };
    expect(body.expectedRevision).toBe(seeded.revision);
    expect(body.parts[0]).toMatchObject({ id: "part-1", questionCount: 40 });
    expect(backend.calls("POST", "/api/v1/admin/exams")).toHaveLength(0);
    expect(backend.app(EXAM_ID).parts[0]!.questionCount).toBe(40);
  }, 30_000);

  it("không sửa gì: Lưu và tiếp tục chỉ về bước 2, không gửi ghi", async () => {
    setup();
    const user = userEvent.setup();
    await open();
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
  }, 30_000);

  it("AC-1697-4: đổi phạm vi sang Theo phần thì hỏi nạp lại cấu trúc, xác nhận mới gửi confirmReplace rồi về bước 2", async () => {
    const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
    const englishPart = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_SINGLE")!;
    setup({}, (server) =>
      server.server.on("GET", "/api/v1/admin/exam-templates", {
        status: 200,
        body: [toWireTemplate(hsaFull), toWireTemplate({ ...structuredClone(englishPart), code: "HSA_PART_ANH", partCode: "ANH" })],
      }),
    );
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ kind: "HSA", scope: "PART", confirmReplace: true, expectedRevision: seeded.revision });
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("AC-1697-5a: bài chưa công bố đổi được cách dựng bài; Lưu và tiếp tục hỏi xác nhận, xác nhận gửi buildMode + confirmReplace và máy chủ dựng lại cấu trúc", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, subjectTemplates);
    const user = userEvent.setup();
    await open();
    const buildModeGroup = screen.getByRole("group", { name: /Cách dựng bài/ });
    expect(buildModeGroup).toBeEnabled();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("Đổi cách dựng bài sẽ tạo lại cấu trúc như đề mới. Cấu trúc, ma trận và nhóm lấy câu hiện tại sẽ bị thay."),
    ).toBeInTheDocument();
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Tạo lại cấu trúc" }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      confirmReplace: true,
      expectedRevision: seeded.revision,
    });
    expect(backend.app(EXAM_ID).buildMode).toBe("REVIEW");
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("AC-1697-5b: Quay lại ở hộp xác nhận đổi cách dựng bài không gửi gì, cách dựng bài đã lưu giữ nguyên", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, subjectTemplates);
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Quay lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.app(EXAM_ID).buildMode).toBe("STANDARD");
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 30_000);

  it("AC-1697-5c: bài đã công bố: phạm vi và cách dựng bài chỉ đọc kèm lý do", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", buildMode: "REVIEW", servingVersion: 1, parts: [hsaPart(1, "Review", 50, 75, "MATH")] });
    await open();
    expect(screen.getByRole("group", { name: /Cách dựng bài/ })).toBeDisabled();
    expect(screen.getByText("Bài đã có phiên bản công bố; muốn đổi cách dựng bài hãy tạo bài mới")).toBeInTheDocument();
  }, 30_000);

  it("máy chủ từ chối đổi cách dựng bài (409 EXAM_SCOPE_LOCKED): câu lỗi khóa phạm vi hiện ra, không treo màn hình", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, (server) => {
      subjectTemplates(server);
      server.server.on("PATCH", EXAM_PATH, {
        status: 409,
        body: { code: "EXAM_SCOPE_LOCKED", message: "Phạm vi bài thi đã bị khóa vì bài đã có phiên bản công bố." },
      });
    });
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));
    await user.click(screen.getAllByRole("button", { name: CONTINUE })[0]!);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Tạo lại cấu trúc" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Bài đã công bố nên không đổi được kỳ thi, phạm vi, phần và môn");
    expect(backend.app(EXAM_ID).buildMode).toBe("STANDARD");
  }, 30_000);
});
