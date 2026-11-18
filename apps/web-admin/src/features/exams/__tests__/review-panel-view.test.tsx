import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAMS_QUERY_KEYS } from "../keys";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;
const reference = referenceFixture as unknown as ContentReference;

const reviewTemplate = {
  code: "T_REVIEW",
  kindCode: "HSA",
  scope: "FULL",
  timing: "PER_PART",
  label: "Bài ôn",
  active: true,
  isDefault: true,
  buildMode: "REVIEW",
  publishable: true,
  fixedCounts: false,
  structure: { parts: [] },
};

interface SentBlock {
  questionType: string | null;
  requiredPointsPerQuestion?: number | null;
  questionCount: number | null;
  setCount?: number | null;
  setSize?: number | null;
  slots?: Array<{ index: number; tagCodes?: string[] }>;
}
interface SentSection {
  questionCount: number | null;
  blocks: SentBlock[];
  matrix?: { unit: string } | null;
  topicQuotas?: Array<{ count: number; percent?: number }>;
  knowledgeScope?: { nodeIds: string[] } | null;
}
interface Sent {
  parts: Array<{ questionCount: number; sections: SentSection[] }>;
}

const percentMatrix = {
  mode: "SEPARATE",
  unit: "PERCENT",
  byGrade: [{ code: "GRADE_12", value: 100 }],
  byDifficulty: [
    { code: "RECOGNITION", value: 25 },
    { code: "COMPREHENSION", value: 50 },
    { code: "APPLICATION", value: 25 },
    { code: "HIGH_APPLICATION", value: 0 },
  ],
};

/** Bài ôn: một phần 10 câu, một môn, một nhóm; `block` ghi đè trường của nhóm. */
function boot(block: Record<string, unknown> = {}, templatesStatus = 200, section: Record<string, unknown> = {}, exam: Parameters<typeof examDetailWire>[0] = {}) {
  const base = FIXED_PART.sections![0] as object;
  const part = {
    ...FIXED_PART,
    sections: [{ ...base, blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 10, requiredPointsPerQuestion: 1, ...block }], ...section }],
  };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, templateCode: "T_REVIEW", ...exam }) })
    .on("GET", "/api/v1/admin/exam-templates", templatesStatus === 200 ? { status: 200, body: [reviewTemplate] } : { status: templatesStatus, body: { code: "BOOM" } })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
}

function open() {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

const sentOf = (server: FakeServer) => server.calls("PUT", STRUCTURE)[0]!.body as Sent;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Bài ôn: sửa và lưu (R1-17, R1-30)", () => {
  it("R1-17: đổi dạng câu và số lượng, lưu gửi đúng dạng và số câu của môn lẫn nhóm; lệch bước 1 thì cảnh báo", async () => {
    const server = boot();
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    const count = screen.getByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "12");
    expect(await screen.findByText(/Bước 1 đặt 10 câu, ở đây đang 12/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(12);
    expect(section.blocks).toHaveLength(1);
    expect(section.blocks[0]).toMatchObject({ questionType: "SHORT_ANSWER", questionCount: 12 });
  });

  it("R1-17: khớp bước 1 thì không có cảnh báo lệch", async () => {
    boot();
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByText(/Bước 1 đặt/)).not.toBeInTheDocument();
  });

  it("R1-27: mẫu cấu trúc không tải được thì chưa cho sửa: bài ôn không hiện màn đầy đủ, chỉ có nút Thử lại", async () => {
    boot({}, 500);
    open();
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /Số câu của mục/ })).not.toBeInTheDocument();
  });

  it("R1-30: đổi số chùm 3 → 2 bỏ điều kiện của chùm 3 và tính lại số câu, như ở nhóm thường", async () => {
    const server = boot({
      questionType: "QUESTION_SET",
      setCount: 3,
      setSize: 2,
      questionCount: 6,
      slots: [{ index: 1, tagCodes: ["X"] }, { index: 3, tagCodes: ["Y"] }],
    });
    const user = userEvent.setup();
    open();
    const sets = await screen.findByRole("textbox", { name: /^Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const block = sentOf(server).parts[0]!.sections[0]!.blocks[0]!;
    expect(block).toMatchObject({ setCount: 2, setSize: 2, questionCount: 4 });
    expect(block.slots?.map((slot) => slot.index)).toEqual([1, 2]);
    expect(block.slots?.[0]?.tagCodes).toEqual(["X"]);
  });
});

describe("Bài ôn vừa tạo từ mẫu: nhóm chưa khai số câu (V-01b)", () => {
  // Dạng thật máy chủ trả sau POST /exams (mẫu `<KỲ>_REVIEW` + tổng quan 10 câu): phần và môn đã có 10, nhóm chỉ có `id`.
  const fresh = { name: undefined, questionType: null, questionCount: null, requiredPointsPerQuestion: undefined };

  it("ô Số lượng hiện 10, không báo lệch bước 1 và không báo thiếu câu", async () => {
    boot(fresh).on("GET", `${STRUCTURE}/summary`, {
      status: 200,
      body: { parts: [{ partId: "part-1", name: "Review", target: 10, allocated: 0, children: [] }] },
    });
    open();
    const count = await screen.findByRole("textbox", { name: /^Số lượng/ });
    expect(count).toHaveValue("10");
    await waitFor(() => expect(screen.getByTestId("structure-headline")).toHaveTextContent("Đã phân bổ đủ 10 câu."));
    expect(screen.queryByText(/Bước 1 đặt/)).not.toBeInTheDocument();
    expect(screen.queryByText(/các khối có 0 câu/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Còn thiếu 10 câu/)).not.toBeInTheDocument();
  });

  it("lưu gửi 10 câu ở cả môn lẫn nhóm", async () => {
    const server = boot(fresh);
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(10);
    expect(section.blocks[0]).toMatchObject({ questionType: "SHORT_ANSWER", questionCount: 10 });
  });
});

describe("Bài ôn: đổi số câu khi bảng nhập theo tỷ lệ (R2-01)", () => {
  const topic = { id: "t1", name: "Xác suất", nodeIds: [], count: 1, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" };

  it("ma trận theo tỷ lệ và chỉ tiêu chủ đề 10%: đổi Số lượng 10 → 20 rồi Lưu gửi ngay, số câu chỉ tiêu tính lại theo 20", async () => {
    const server = boot({}, 200, { matrix: percentMatrix, topicQuotas: [topic] });
    const user = userEvent.setup();
    open();
    const count = await screen.findByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "20");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(20);
    expect(section.matrix?.unit).toBe("PERCENT");
    expect(section.topicQuotas?.[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("đổi số chùm cũng không bị chặn: số câu và chỉ tiêu chủ đề tính lại theo số chùm × số câu con", async () => {
    const server = boot({ questionType: "QUESTION_SET", setCount: 2, setSize: 5, questionCount: 10 }, 200, { matrix: percentMatrix, topicQuotas: [topic] });
    const user = userEvent.setup();
    open();
    const sets = await screen.findByRole("textbox", { name: /^Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "4");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(20);
    expect(section.topicQuotas?.[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("đối chứng: bài ôn có hai nhóm vẽ màn môn thường, vẫn hỏi Áp dụng số mới hay Giữ số cũ trước khi lưu", async () => {
    const group = (id: string) => ({ id, name: id, questionType: "SINGLE_CHOICE", questionCount: 5, requiredPointsPerQuestion: 1 });
    const server = boot({}, 200, { blocks: [group("blk-1"), group("blk-2")], matrix: percentMatrix, topicQuotas: [topic] });
    const user = userEvent.setup();
    open();
    const count = await screen.findByRole("textbox", { name: "Số câu của mục" });
    await user.clear(count);
    await user.type(count, "20");
    expect(await screen.findByRole("alert")).toHaveTextContent("Tổng đổi thành 20 câu (trước đó 10 câu)");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Áp dụng số mới" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).parts[0]!.sections[0]!.topicQuotas?.[0]).toMatchObject({ count: 2, percent: 10 });
  });
});

describe("Bài ôn: tải mẫu và phạm vi (R2-03, R2-05, R2-02)", () => {
  it("R2-03: mẫu còn đang tải thì chưa có nút Lưu và chưa có màn đầy đủ; tải xong mới hiện màn bài ôn", async () => {
    const server = boot();
    server.hold("GET", "/api/v1/admin/exam-templates");
    open();
    expect(await screen.findByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    await waitFor(() => expect(server.calls("GET", "/api/v1/admin/exam-templates")).toHaveLength(1));
    expect(screen.queryByRole("button", { name: "Lưu" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
    server.release("GET", "/api/v1/admin/exam-templates");
    expect(await screen.findByRole("combobox", { name: "Dạng câu" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
  });

  it("R2-02: làm mới nền mẫu lỗi khi mẫu đã có thì giữ trình sửa và bản nháp chưa lưu, không hiện thẻ lỗi", async () => {
    const server = boot();
    const user = userEvent.setup();
    const { client } = open();
    const count = await screen.findByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "12");
    // Từ lần gọi sau mẫu trả 500.
    server.on("GET", "/api/v1/admin/exam-templates", { status: 500, body: { code: "BOOM" } });
    await client.refetchQueries({ queryKey: EXAMS_QUERY_KEYS.templates() }).catch(() => undefined);
    await waitFor(() => expect(server.calls("GET", "/api/v1/admin/exam-templates").length).toBeGreaterThan(1));
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Số lượng/ })).toHaveValue("12");
  });

  it("R2-05: bỏ phạm vi MAPID của môn ở màn bài ôn rồi lưu: bản gửi không còn nút MAPID nào", async () => {
    const nodeId = reference.knowledgeTree[0]!.id;
    const server = boot({}, 200, { knowledgeScope: { nodeIds: [nodeId] } });
    const user = userEvent.setup();
    open();
    const scope = (await screen.findByText("Phạm vi kiến thức")).closest("fieldset")!;
    expect(within(scope).getByRole("radio", { name: "Theo chủ đề" })).toBeChecked();
    await user.click(within(scope).getByRole("radio", { name: "Toàn môn" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).parts[0]!.sections[0]!.knowledgeScope ?? null).toBeNull();
  });

  it("R2-05: chọn MAPID cho môn ở màn bài ôn rồi lưu: bản gửi mang các nút đã chọn", async () => {
    const server = boot();
    const user = userEvent.setup();
    open();
    const scope = (await screen.findByText("Phạm vi kiến thức")).closest("fieldset")!;
    await user.click(within(scope).getByRole("radio", { name: "Theo chủ đề" }));
    await user.click(within(scope).getByRole("button", { name: "Chọn kiến thức" }));
    const dialog = await screen.findByRole("dialog");
    const boxes = await within(dialog).findAllByRole("checkbox");
    await user.click(boxes.find((box) => !(box as HTMLInputElement).disabled) ?? boxes[0]!);
    await user.click(within(dialog).getByRole("button", { name: /^Áp dụng/ }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).parts[0]!.sections[0]!.knowledgeScope?.nodeIds.length).toBeGreaterThan(0);
  });
});

describe("Bài ôn: điểm mỗi câu theo thang của bài (F2, QA)", () => {
  // Dạng thật sau POST /exams: nhóm duy nhất chưa có dạng câu, chưa có điểm; thang là mặc định của kỳ thi hoặc người dùng nhập.
  const fresh = { name: undefined, questionType: null, questionCount: null, requiredPointsPerQuestion: undefined };

  async function chooseTypeAndSave(server: FakeServer, user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    return sentOf(server).parts[0]!.sections[0]!.blocks[0]!;
  }

  it("thang mặc định 150, 10 câu: lưu gửi 15 điểm mỗi câu (không còn 10/150)", async () => {
    const server = boot(fresh, 200, {}, { maxScore: 150 });
    open();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    expect(await screen.findByText(/Mỗi câu 15 điểm \(thang 150 chia 10 câu\)/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).parts[0]!.sections[0]!.blocks[0]!.requiredPointsPerQuestion).toBe(15);
  });

  it("người dùng nhập thang 100 ở bước 1 (HSA 10 câu): điểm mỗi câu là 10; đổi số lượng sang 8 thì 12,5", async () => {
    const server = boot(fresh, 200, {}, { maxScore: 100 });
    const user = userEvent.setup();
    open();
    const count = await screen.findByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "8");
    const block = await chooseTypeAndSave(server, user);
    expect(block).toMatchObject({ questionCount: 8, requiredPointsPerQuestion: 12.5 });
  });

  it("thang chia không hết (150 ÷ 7): gửi đúng số thực, không làm tròn, để tổng khớp trong dung sai", async () => {
    const server = boot(fresh, 200, {}, { maxScore: 150 });
    const user = userEvent.setup();
    open();
    const count = await screen.findByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "7");
    const block = await chooseTypeAndSave(server, user);
    expect(block.requiredPointsPerQuestion).toBe(150 / 7);
    expect(Math.abs(block.requiredPointsPerQuestion! * 7 - 150)).toBeLessThan(1e-6);
  });

  it("V-ACT chưa nhập thang: 10 điểm mỗi câu; V-ACT nhập thang 300 (LINEAR) với 10 câu: 30", async () => {
    const noScale = boot(fresh, 200, {}, { kind: "VACT", timing: "SHARED", maxScore: undefined, referenceMax: undefined, practiceScoring: "LINEAR" });
    const user = userEvent.setup();
    const first = open();
    expect((await chooseTypeAndSave(noScale, user)).requiredPointsPerQuestion).toBe(10);
    first.unmount();

    const scaled = boot(fresh, 200, {}, { kind: "VACT", timing: "SHARED", maxScore: 300, referenceMax: 300, practiceScoring: "LINEAR" });
    open();
    expect((await chooseTypeAndSave(scaled, user)).requiredPointsPerQuestion).toBe(30);
  });

  it("bài ôn chưa đụng tới (chưa chọn dạng câu) không tự thành thay đổi chưa lưu", async () => {
    boot(fresh, 200, {}, { maxScore: 150 });
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByText(/Mỗi câu/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled();
  });
});
