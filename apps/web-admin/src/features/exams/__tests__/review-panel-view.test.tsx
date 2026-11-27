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
  parts: [],
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
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, templateCode: "T_REVIEW", buildMode: "REVIEW", ...exam }) })
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
  it("R1-17: đổi dạng câu, lưu gửi đúng dạng và số câu của môn lẫn nhóm; Số lượng chỉ đọc", async () => {
    const server = boot();
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    expect(screen.getByRole("textbox", { name: /^Số lượng/ })).toHaveAttribute("readonly");

    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(10);
    expect(section.blocks).toHaveLength(1);
    expect(section.blocks[0]).toMatchObject({ questionType: "SHORT_ANSWER", questionCount: 10 });
  });

  it("R1-17: nhóm lưu từ trước lệch bước 1 thì cảnh báo; ô Số lượng chỉ đọc, sửa ở thông tin tổng quan", async () => {
    boot({ questionCount: 12 });
    open();
    expect(await screen.findByText(/Bước 1 đặt 10 câu, ở đây đang 12/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Số lượng/ })).toHaveAttribute("readonly");
  });

  it("R1-F2: nhóm không chùm lệch bước 1: 'Dùng số của bước 1' đặt số câu của môn và nhóm về 10, hết cảnh báo, lưu gửi 10", async () => {
    const server = boot({ questionCount: 12 }, 200, { questionCount: 12 });
    const user = userEvent.setup();
    open();
    expect(await screen.findByText(/Bước 1 đặt 10 câu, ở đây đang 12/)).toBeInTheDocument();
    expect(screen.queryByText(/Sửa một trong hai chỗ/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Dùng số của bước 1" }));
    expect(screen.queryByText(/Bước 1 đặt/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Dùng số của bước 1" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Số lượng/ })).toHaveValue("10");

    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(10);
    expect(section.blocks[0]).toMatchObject({ questionCount: 10 });
  });

  it("R1-F2: nhóm không lệch thì không có nút 'Dùng số của bước 1'", async () => {
    boot();
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByRole("button", { name: "Dùng số của bước 1" })).not.toBeInTheDocument();
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
    expect(screen.queryByRole("button", { name: "Tiếp tục: Thông tin bán" })).not.toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
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
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(10);
    expect(section.blocks[0]).toMatchObject({ questionType: "SHORT_ANSWER", questionCount: 10 });
  });
});

describe("Bài ôn: đổi số câu khi bảng nhập theo tỷ lệ (R2-01)", () => {
  const topic = { id: "t1", name: "Xác suất", nodeIds: [], count: 1, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" };

  it("đổi số chùm cũng không bị chặn: số câu và chỉ tiêu chủ đề tính lại theo số chùm × số câu con", async () => {
    const server = boot({ questionType: "QUESTION_SET", setCount: 2, setSize: 5, questionCount: 10 }, 200, { matrix: percentMatrix, topicQuotas: [topic] });
    const user = userEvent.setup();
    open();
    const sets = await screen.findByRole("textbox", { name: /^Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "4");
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(20);
    expect(section.topicQuotas?.[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("đối chứng: bài ôn có hai nhóm vẽ màn môn thường, không có ô Số câu của mục", async () => {
    const group = (id: string) => ({ id, name: id, questionType: "SINGLE_CHOICE", questionCount: 5, requiredPointsPerQuestion: 1 });
    boot({}, 200, { blocks: [group("blk-1"), group("blk-2")], matrix: percentMatrix, topicQuotas: [topic] });
    open();
    await screen.findByRole("heading", { name: /^Môn / });
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Số lượng/ })).not.toBeInTheDocument();
  });
});

describe("Bài ôn: tải mẫu và phạm vi (R2-03, R2-05, R2-02)", () => {
  it("R2-03: mẫu còn đang tải thì chưa có nút Lưu và chưa có màn đầy đủ; tải xong mới hiện màn bài ôn", async () => {
    const server = boot();
    server.hold("GET", "/api/v1/admin/exam-templates");
    open();
    expect(await screen.findByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    await waitFor(() => expect(server.calls("GET", "/api/v1/admin/exam-templates")).toHaveLength(1));
    expect(screen.queryByRole("button", { name: "Tiếp tục: Thông tin bán" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
    server.release("GET", "/api/v1/admin/exam-templates");
    expect(await screen.findByRole("combobox", { name: "Dạng câu" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Số câu của mục" })).not.toBeInTheDocument();
  });

  it("R2-02: làm mới nền mẫu lỗi khi mẫu đã có thì giữ trình sửa và bản nháp chưa lưu, không hiện thẻ lỗi", async () => {
    const server = boot();
    const user = userEvent.setup();
    const { client } = open();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    // Từ lần gọi sau mẫu trả 500.
    server.on("GET", "/api/v1/admin/exam-templates", { status: 500, body: { code: "BOOM" } });
    await client.refetchQueries({ queryKey: EXAMS_QUERY_KEYS.templates() }).catch(() => undefined);
    await waitFor(() => expect(server.calls("GET", "/api/v1/admin/exam-templates").length).toBeGreaterThan(1));
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Dạng câu" })).toHaveTextContent("Trả lời ngắn");
  });

  it("R2-05: bỏ phạm vi MAPID của môn ở màn bài ôn rồi lưu: bản gửi không còn nút MAPID nào", async () => {
    const nodeId = reference.knowledgeTree[0]!.id;
    const server = boot({}, 200, { knowledgeScope: { nodeIds: [nodeId] } });
    const user = userEvent.setup();
    open();
    const scope = (await screen.findByText("Phạm vi kiến thức")).closest("fieldset")!;
    expect(within(scope).getByRole("radio", { name: "Theo chủ đề" })).toBeChecked();
    await user.click(within(scope).getByRole("radio", { name: "Toàn môn" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
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
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).parts[0]!.sections[0]!.knowledgeScope?.nodeIds.length).toBeGreaterThan(0);
  });
});

describe("Bài ôn: lưu không ghi điểm mỗi câu, đầu trang chỉ nêu thang (F2, QA)", () => {
  // Dạng thật sau POST /exams: nhóm duy nhất chưa có dạng câu, chưa có điểm; thang là số người dùng nhập ở bước 1 (hoặc không có).
  const fresh = { name: undefined, questionType: null, questionCount: null, requiredPointsPerQuestion: undefined };

  async function chooseTypeAndSave(server: FakeServer, user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    return sentOf(server).parts[0]!.sections[0]!.blocks[0]!;
  }

  it.each([
    ["HSA, thang 150", { maxScore: 150 }],
    ["HSA, thang người dùng nhập 100", { maxScore: 100 }],
    ["HSA, không thang", { maxScore: undefined }],
    ["V-ACT, không thang (LINEAR)", { kind: "VACT", timing: "SHARED", maxScore: undefined, referenceMax: undefined, practiceScoring: "LINEAR" }],
    ["V-ACT, thang 300 (LINEAR)", { kind: "VACT", timing: "SHARED", maxScore: 300, referenceMax: 300, practiceScoring: "LINEAR" }],
  ])("%s: chọn dạng câu rồi lưu không gửi requiredPointsPerQuestion", async (_label, exam) => {
    const server = boot(fresh, 200, {}, exam as Parameters<typeof examDetailWire>[0]);
    const user = userEvent.setup();
    open();
    const block = await chooseTypeAndSave(server, user);
    expect(block.questionType).toBe("SHORT_ANSWER");
    expect(block.requiredPointsPerQuestion ?? null).toBeNull();
    expect(screen.queryByText(/Mỗi câu .* điểm/)).not.toBeInTheDocument();
  });

  it("panel bài ôn: có thang thì chỉ nêu thang chỉ đọc, không còn dải tổng hợp phụ ở đầu Tab 2 và không so tổng điểm với thang", async () => {
    boot(fresh, 200, {}, { maxScore: 150 });
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByLabelText("Tổng hợp cấu trúc")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Thang điểm" })).toHaveValue("150");
    expect(screen.queryByText("Tổng điểm")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\d\s*\/\s*150/);
  });

  it("panel bài ôn: không có thang thì ẩn ô Thang điểm và không hiện mục điểm", async () => {
    boot(fresh, 200, {}, { maxScore: undefined });
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByLabelText("Tổng hợp cấu trúc")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Thang điểm" })).not.toBeInTheDocument();
    expect(screen.queryByText("Tổng điểm")).not.toBeInTheDocument();
  });

  it("bài ôn chưa đụng tới (chưa chọn dạng câu) không tự thành thay đổi chưa lưu", async () => {
    boot(fresh, 200, {}, { maxScore: 150 });
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" })).toBeEnabled();
    expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull();
  });
});
