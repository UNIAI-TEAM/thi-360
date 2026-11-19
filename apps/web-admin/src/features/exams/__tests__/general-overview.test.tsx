import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamGeneralView } from "../components/exam-general-view";
import { ExamStructureView } from "../components/exam-structure-view";
import type { ExamDetail, ExamPart, ExamSection, QuestionBlock } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireTemplate } from "./fake-exams-backend";
import { sp2eVanExam } from "./sp2e-van-fixture";

/**
 * UNI-2751: tab Thông tin chung có "Cấu trúc tổng quan" như bước 1, dựng từ cấu trúc đã lưu. Lưu ghi cấu trúc trước
 * (PUT /structure với `expectedRevision` của cấu trúc), rồi mới ghi thông tin (PATCH); lỗi ở PUT thì không gửi gì khác.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAM_ID = "ex-general-overview";
const EXAM_PATH = `/api/v1/admin/exams/${EXAM_ID}`;
const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const SAVE = "Lưu thông tin";

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

const section = (order: number, subjectCode: string, count: number): ExamSection => ({
  id: `sec-${order}`,
  subjectCode,
  questionCount: count,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block(`blk-${order}`, 50)],
});

/** Phần HSA như bước 1 tạo ra: số câu làm là `count`, đề ghi 50 câu, nhóm lấy câu vẫn 50 câu. */
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
  sections: [section(order, subjectCode, count)],
  options: [],
});

const MATH = "Toán học và xử lí số liệu";
const LITERATURE = "Ngôn ngữ – Văn học";
const SCIENCE = "Khoa học tự nhiên";
const countOf = (name: string) => screen.getByRole("textbox", { name: `Số câu của ${name}` });
const tableRegion = () => screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });

let backend: ExamsBackend;
let seeded: ExamDetail;

/** Bài HSA toàn bài đã sửa số câu Toán 50 → 45 lúc tạo (45 / 50 / 50). `configure` đăng ký phản hồi ghi đè trước khi cài. */
function setup(options: { lockCounts?: boolean; configure?: (backend: ExamsBackend) => void } = {}) {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  if (options.lockCounts) {
    const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
    backend.server.on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [{ ...toWireTemplate(hsaFull), fixedCounts: true }] });
  }
  options.configure?.(backend);
  backend.install();
  const base = backend.app("ex-hsa-f-001");
  seeded = {
    ...base,
    id: EXAM_ID,
    code: "HSA-OV-001",
    name: "HSA tổng quan",
    kind: "HSA",
    scope: "FULL",
    timing: "PER_PART",
    templateCode: "HSA_FULL",
    servingVersion: null,
    parts: [hsaPart(1, MATH, 45, 75, "MATH"), hsaPart(2, LITERATURE, 50, 60, "LITERATURE"), hsaPart(3, SCIENCE, 50, 60, "PHYSICS")],
  };
  backend.seedExam(seeded);
}

/** Cài bài có sẵn (không qua mẫu HSA) để thử hình dạng riêng của một kỳ thi. */
/** Bài HSA nền để dựng bài khác từ đó. */
const seeded0 = (): ExamDetail => new ExamsBackend().app("ex-hsa-f-001");

function setupExam(exam: ExamDetail) {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
  seeded = { ...exam, id: EXAM_ID };
  backend.seedExam(seeded);
}

function open() {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
  return renderWithProviders(<ExamGeneralView examId={EXAM_ID} />);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Thông tin chung: bảng Cấu trúc tổng quan (UNI-2751)", () => {
  it("AC-3: bảng hiện số đã lưu của từng phần (45 / 50 / 50), tổng và thời lượng", async () => {
    setup();
    open();
    const table = await tableRegion();
    expect(screen.getByRole("heading", { name: "Cấu trúc tổng quan" })).toBeInTheDocument();
    expect(within(table).getAllByRole("textbox", { name: /^Số câu của/ }).map((input) => (input as HTMLInputElement).value)).toEqual(["45", "50", "50"]);
    expect(within(table).getAllByRole("textbox", { name: /^Thời lượng của/ }).map((input) => (input as HTMLInputElement).value)).toEqual(["75", "60", "60"]);
    const totals = within(table).getByRole("row", { name: /^Tổng/ });
    expect(within(totals).getByText("145")).toBeInTheDocument();
    expect(within(totals).getByText("195")).toBeInTheDocument();
  }, 20_000);

  it("AC-4: sửa số câu của phần rồi Lưu gửi PUT cấu trúc (khối giữ nguyên, đúng revision) và không gửi PATCH khi thông tin chưa sửa", async () => {
    setup();
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "40");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1));
    const body = backend.calls("PUT", STRUCTURE_PATH)[0]!.body as { expectedRevision: number; parts: ExamPart[] };
    expect(body.expectedRevision).toBe(seeded.revision);
    expect(body.parts).toHaveLength(3);
    expect(body.parts[0]).toMatchObject({ id: "part-1", questionCount: 40, questionCountOnPaper: 50, durationMinutes: 75 });
    // Phần một môn: số câu môn đi theo số câu phần như máy chủ làm ở bước 1; nhóm lấy câu không đổi.
    expect(body.parts[0]!.sections[0]).toMatchObject({ id: "sec-1", questionCount: 40 });
    expect(body.parts[0]!.sections[0]!.blocks.map((item) => ({ id: item.id, questionCount: item.questionCount }))).toEqual([{ id: "blk-1", questionCount: 50 }]);
    expect(body.parts[1]).toMatchObject({ id: "part-2", questionCount: 50 });
    expect(body.parts[2]).toMatchObject({ id: "part-3", questionCount: 50 });
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(backend.app(EXAM_ID).parts[0]!.questionCount).toBe(40);
  }, 30_000);

  it("lưu cả bảng và thông tin: PUT cấu trúc trước, PATCH thông tin sau với revision mới", async () => {
    setup();
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.clear(countOf(LITERATURE));
    await user.type(countOf(LITERATURE), "48");
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "HSA tổng quan sửa");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    const order = backend.server.requests.filter((request) => request.method !== "GET").map((request) => `${request.method} ${request.url.pathname}`);
    expect(order).toEqual([`PUT ${STRUCTURE_PATH}`, `PATCH ${EXAM_PATH}`]);
    const patch = backend.calls("PATCH", EXAM_PATH)[0]!.body as { name: string; expectedRevision: number };
    expect(patch.name).toBe("HSA tổng quan sửa");
    expect(patch.expectedRevision).toBeGreaterThan(seeded.revision);
    expect(backend.app(EXAM_ID).parts[1]!.questionCount).toBe(48);
  }, 30_000);

  it("AC-5: ô số câu không hợp lệ hiện lỗi tại ô và không gửi gì", async () => {
    setup();
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "abc");
    expect(await screen.findByText("Số câu phải là số nguyên dương.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: SAVE }));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(countOf(MATH)).toHaveFocus();
  }, 30_000);

  it("AC-6: mẫu khóa số câu thì ô số câu chỉ đọc và có chip khóa; thời lượng vẫn sửa được", async () => {
    setup({ lockCounts: true });
    open();
    const table = await tableRegion();
    expect(screen.getByText("Mẫu đã khóa số câu")).toBeInTheDocument();
    for (const input of within(table).getAllByRole("textbox", { name: /^Số câu của/ })) {
      expect(input).toHaveAttribute("readonly");
    }
    for (const input of within(table).getAllByRole("textbox", { name: /^Thời lượng của/ })) {
      expect(input).not.toHaveAttribute("readonly");
    }
  }, 20_000);

  it("AC-10: PUT bị 412 thì hiện câu xung đột, không gửi PATCH và giữ nguyên giá trị đã gõ", async () => {
    setup({ configure: (server) => server.server.on("PUT", STRUCTURE_PATH, { status: 412, body: { code: "EXAM_REVISION_CONFLICT" } }) });
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "41");
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Tên mới giữ lại");
    await user.click(screen.getByRole("button", { name: SAVE }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Bài thi vừa được người khác sửa");
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(countOf(MATH)).toHaveValue("41");
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("Tên mới giữ lại");
  }, 30_000);

  it("PATCH lỗi sau khi PUT thành công: báo phần đã lưu và phần chưa lưu, giữ giá trị đã gõ", async () => {
    setup({ configure: (server) => server.server.on("PATCH", EXAM_PATH, { status: 500, body: { code: "INTERNAL" } }) });
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.clear(countOf(LITERATURE));
    await user.type(countOf(LITERATURE), "47");
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Tên chưa lưu được");
    await user.click(screen.getByRole("button", { name: SAVE }));

    expect(await screen.findByText(/đã được lưu, nhưng thông tin chung chưa lưu được/)).toBeInTheDocument();
    expect(backend.app(EXAM_ID).parts[1]!.questionCount).toBe(47);
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("Tên chưa lưu được");
    expect(countOf(LITERATURE)).toHaveValue("47");
  }, 30_000);

  it("đổi phạm vi đang chờ xác nhận: bảng hiện số của mẫu mới ở dạng chỉ đọc", async () => {
    setup();
    const user = userEvent.setup();
    open();
    await tableRegion();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(await screen.findByText(/Đang chọn phạm vi khác/)).toBeInTheDocument();
    const table = await tableRegion();
    const inputs = within(table).getAllByRole("textbox");
    expect(inputs.length).toBeGreaterThan(0);
    for (const input of inputs) expect(input).toHaveAttribute("readonly");
  }, 30_000);

  it("AC-9: hạ số câu của phần ở Thông tin chung thì bước Cấu trúc báo lệch phân bổ, không tự đổi khối", async () => {
    setup();
    const user = userEvent.setup();
    const { unmount } = open();
    await tableRegion();
    await user.clear(countOf(LITERATURE));
    await user.type(countOf(LITERATURE), "30");
    await user.click(screen.getByRole("button", { name: SAVE }));
    await waitFor(() => expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1));
    await waitFor(() => expect(backend.app(EXAM_ID).parts[1]!.questionCount).toBe(30));
    unmount();

    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
    expect((await screen.findAllByText("Ngữ văn: các khối có 50 câu, môn cần 30 câu.")).length).toBeGreaterThan(0);
    // Khối lấy câu không bị tự giảm: máy chủ vẫn giữ 50 câu ở nhóm, lệch này do người dùng tự chia lại ở bước Cấu trúc.
    expect(backend.app(EXAM_ID).parts[1]!.sections[0]!.blocks[0]!.questionCount).toBe(50);
  }, 30_000);

  it("R1-1: phần mà mọi nhóm đều bỏ qua là dòng chỉ đọc, không lỗi khi tải; sửa Phần I rồi lưu giữ nguyên số câu 0 của Phần II", async () => {
    setupExam(sp2eVanExam());
    const user = userEvent.setup();
    open();
    const table = await tableRegion();
    expect(screen.queryByText("Số câu phải là số nguyên dương.")).not.toBeInTheDocument();
    expect(within(table).queryByRole("textbox", { name: /Part II/ })).not.toBeInTheDocument();
    expect(within(table).getByText(/Part II Writing/)).toBeInTheDocument();
    const count = within(table).getByRole("textbox", { name: "Số câu của Part I Reading" });
    await user.clear(count);
    await user.type(count, "28");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1));
    const body = backend.calls("PUT", STRUCTURE_PATH)[0]!.body as { parts: ExamPart[] };
    expect(body.parts[0]).toMatchObject({ id: "p1", questionCount: 28 });
    expect(body.parts[1]).toMatchObject({ id: "p2", questionCount: 0 });
    expect(body.parts[1]!.sections[0]).toMatchObject({ questionCount: 0 });
  }, 30_000);

  it("R1-2: V-ACT có hai mục cùng môn SCIENTIFIC_THINKING: ô số câu mang hai tên khác nhau như ở bước 1", async () => {
    const competent = (order: number, code: string, count: number): ExamSection => ({
      ...section(order, "SCIENTIFIC_THINKING", count),
      knowledgeScope: { nodeIds: [], competencyCodes: [code] },
    });
    const thinking: ExamPart = {
      ...hsaPart(1, "Tư duy khoa học", 30, 0, "SCIENTIFIC_THINKING"),
      sections: [competent(1, "VACT-TD-LOGIC", 12), competent(2, "VACT-TD-SL", 18)],
    };
    setupExam({ ...seeded0(), kind: "VACT", timing: "SHARED", templateCode: null, parts: [thinking] });
    open();
    const table = await tableRegion();
    expect(within(table).getByRole("textbox", { name: "Số câu của Logic và phân tích số liệu" })).toHaveValue("12");
    expect(within(table).getByRole("textbox", { name: "Số câu của Suy luận khoa học" })).toHaveValue("18");
  }, 30_000);
});

