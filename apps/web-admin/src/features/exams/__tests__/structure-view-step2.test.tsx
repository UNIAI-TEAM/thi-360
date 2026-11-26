import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import type { ExamDetail, ExamPart, ExamScope, ExamSection, QuestionBlock } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireTemplate } from "./fake-exams-backend";

/**
 * FE-7 (R1-4): các phần mới của bước 2 chạy trong `StructureEditor`, không chỉ ở từng thành phần: tỷ lệ sai chặn lưu,
 * mẫu `fixedBlockCounts` khóa dòng của bài cả môn, bảng mạch theo lệnh chỉ có ở bài cả môn.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const STRUCTURE_PATH = "/api/v1/admin/exams/ex-fe7/structure";
const TEMPLATE_CODE = "FE7_VSAT_ENGLISH";

const block = (patch: Partial<QuestionBlock>): QuestionBlock => ({
  id: "blk-set",
  name: "Bài đọc 4 câu",
  questionType: "QUESTION_SET",
  questionCount: 8,
  setCount: 2,
  setSize: 4,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...patch,
});

const section: ExamSection = {
  id: "sec-en",
  subjectCode: "ENGLISH",
  questionCount: 35,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  audioRequirement: "REQUIRED",
  blocks: [block({}), block({ id: "blk-single", name: "Câu đơn", questionType: "SINGLE_CHOICE", questionCount: 27, setCount: null, setSize: null })],
  topicQuotas: [
    { id: "t1", name: "Đọc hiểu", nodeIds: [], count: 42, enforcement: "REFERENCE", basis: "PROMPT" },
    { id: "t2", name: "Ngữ pháp", nodeIds: [], count: 25, enforcement: "REFERENCE", basis: "PROMPT" },
  ],
};

const part: ExamPart = {
  id: "part-en",
  order: 1,
  name: "Phần Nghe",
  durationMinutes: 35,
  questionCount: 35,
  questionCountOnPaper: 35,
  questionCountRequired: 35,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [section],
  options: [],
};

let backend: ExamsBackend;

function setup(scope: ExamScope) {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  // Mẫu khóa dòng của bài cả môn (H42): `toWireTemplate` luôn trả `fixedBlockCounts: false`, nên đặt lại ở đây.
  backend.server.on("GET", "/api/v1/admin/exam-templates", {
    status: 200,
    body: [{ ...toWireTemplate({ ...FIXTURE_TEMPLATES[0]!, code: TEMPLATE_CODE }), kindCode: "VSAT", fixedBlockCounts: true }],
  });
  backend.install();
  const base = backend.app("ex-hsa-f-001");
  const exam: ExamDetail = { ...base, id: "ex-fe7", code: "VSAT-FE7", kind: "VSAT", scope, templateCode: TEMPLATE_CODE, parts: [part] };
  backend.seedExam(exam);
}

function openStructure(search: string) {
  window.history.replaceState(null, "", `/exams/ex-fe7/structure${search}`);
  return renderWithProviders(<ExamStructureView examId="ex-fe7" />);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("bước 2 trong màn cấu trúc (FE-7)", () => {
  it("tỷ lệ không ra chùm nguyên: báo ở dòng, bấm Lưu không gửi PUT và có câu chặn lưu", async () => {
    const user = userEvent.setup();
    setup("PART");
    openStructure("?block=blk-set");
    const entry = (await screen.findByRole("radio", { name: "Số lượng" })).closest("fieldset")!;
    await user.click(within(entry).getByRole("radio", { name: "Tỷ lệ" }));
    const rate = screen.getByRole("textbox", { name: "Tỷ lệ trong phần (%)" });
    await user.clear(rate);
    // Một tỷ lệ hợp lệ trước để bản nháp có thay đổi chưa lưu, rồi mới nhập tỷ lệ sai.
    await user.type(rate, "34,3");
    await screen.findByText("Có thay đổi chưa lưu");
    await user.clear(rate);
    await user.type(rate, "28,6");
    expect(await screen.findByText("Không ghép được chùm nguyên.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(await screen.findAllByText(/một dòng nhập theo tỷ lệ chưa ra chùm nguyên/)).not.toHaveLength(0);
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
  }, 30_000);

  it("bài cả môn từ mẫu fixedBlockCounts: dòng nhận khóa, số chùm và số câu con chỉ đọc", async () => {
    setup("SUBJECT");
    openStructure("?block=blk-set");
    expect(await screen.findByText("Mẫu đã khóa dòng")).toBeInTheDocument();
    for (const name of ["Số chùm", /^Số câu con mỗi chùm/]) {
      expect(screen.getByRole("textbox", { name })).toHaveAttribute("readonly");
    }
    expect(screen.getByRole("heading", { name: "Ví dụ chấm" })).toBeInTheDocument();
  }, 30_000);

  it("bài theo phần từ cùng mẫu: dòng không khóa, số chùm nhập được", async () => {
    setup("PART");
    openStructure("?block=blk-set");
    await screen.findByRole("textbox", { name: "Số chùm" });
    expect(screen.queryByText("Mẫu đã khóa dòng")).toBeNull();
    expect(screen.getByRole("textbox", { name: "Số chùm" })).not.toHaveAttribute("readonly");
  }, 30_000);

  it("bảng mạch theo lệnh: có ở bài cả môn, không có ở bài theo phần", async () => {
    setup("SUBJECT");
    const { unmount } = openStructure("?section=sec-en");
    expect(await screen.findByRole("table", { name: /Mạch đối chiếu của .* theo lệnh/ })).toBeInTheDocument();
    unmount();

    setup("PART");
    openStructure("?section=sec-en");
    await waitFor(() => expect(screen.getAllByText("Phần Nghe").length).toBeGreaterThan(0));
    expect(screen.queryByRole("table", { name: /Mạch đối chiếu/ })).toBeNull();
    expect(screen.queryByText("Mạch đối chiếu theo lệnh")).toBeNull();
  }, 30_000);
});
