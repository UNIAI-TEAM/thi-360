import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { ReferenceDistributions } from "../components/subject-allocation-extras";
import { TopicQuotaTable } from "../components/topic-quota-table";
import { referenceEntries } from "../reference-distribution";
import type { ExamQuota, ExamSection, TopicQuota } from "../types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;
const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10", "GRADE_11", "GRADE_12", "GRADE_MIXED"], difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };

interface Boot {
  section?: Record<string, unknown>;
  sectionStates?: unknown[];
  template?: Record<string, unknown>;
}

/** Bài một phần, một môn 30 câu, một nhóm 30 câu; `section` ghi đè trường của môn. */
function boot({ section = {}, sectionStates, template }: Boot = {}) {
  const base = FIXED_PART.sections![0] as object;
  const wireSection = {
    ...base,
    questionCount: 30,
    blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
    ...section,
  };
  const part = { ...FIXED_PART, questionCount: 30, sections: [wireSection] };
  const server = new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 30, templateCode: template ? "T_FIXED" : undefined }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: template ? [template] : [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part], ...(sectionStates ? { sectionStates } : {}) } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
  return server;
}

function open(search = "?section=sec-1") {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

const percentMatrix = (grade: number, difficulties: number[]) => ({
  mode: "SEPARATE",
  unit: "PERCENT",
  byGrade: [{ code: "GRADE_12", value: grade }],
  byDifficulty: difficulties.map((value, index) => ({ code: axes.difficulties[index]!, value })),
});

async function makeDirty(user: UserEvent) {
  await user.click(await screen.findByRole("button", { name: "Thêm chỉ tiêu chủ đề" }));
}

describe("Row 5: tổng tỷ lệ khác 100% (R-118)", () => {
  it("báo ở bảng và không gửi lên máy chủ; sửa về 100% thì lưu được", async () => {
    const server = boot({ section: { matrix: percentMatrix(90, [25, 50, 25, 0]) } });
    const user = userEvent.setup();
    open();
    expect(await screen.findByText(/Theo khối lớp đang là 90%/)).toBeInTheDocument();
    await makeDirty(user);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    expect(screen.getByText(/Theo khối lớp đang là 90%/)).toBeInTheDocument();

    const grade = screen.getByRole("textbox", { name: /Lớp 12 · tỷ lệ phần trăm/ });
    await user.clear(grade);
    await user.type(grade, "100");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
  });
});

describe("Row 6: đổi tổng từ 30 xuống 20 (R-121)", () => {
  async function changeTotal() {
    const user = userEvent.setup();
    open();
    const count = await screen.findByRole("textbox", { name: "Số câu của mục" });
    await user.clear(count);
    await user.type(count, "20");
    return user;
  }
  const section = () => ({
    matrix: percentMatrix(100, [25, 50, 25, 0]),
    topicQuotas: [{ id: "t1", name: "Xác suất", nodeIds: [], count: 3, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" }],
  });

  it("hiện thông báo với số câu mới theo tỷ lệ", async () => {
    boot({ section: section() });
    await changeTotal();
    const notice = await screen.findByRole("alert");
    expect(notice).toHaveTextContent("Tổng đổi thành 20 câu (trước đó 30 câu)");
    expect(notice).toHaveTextContent("Độ khó: Nhận biết 5 · Thông hiểu 10 · Vận dụng 5 · Vận dụng cao 0");
    expect(notice).toHaveTextContent("Xác suất 2");
  });

  it("Áp dụng số mới: bảng tỷ lệ tính lại, chỉ tiêu chủ đề đổi count và giữ percent", async () => {
    const server = boot({ section: section() });
    const user = await changeTotal();
    await user.click(await screen.findByRole("button", { name: "Áp dụng số mới" }));
    expect(screen.queryByText(/Tổng đổi thành/)).not.toBeInTheDocument();
    expect(screen.getByText("2 câu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ matrix: { unit: string }; topicQuotas: TopicQuota[] }> }> };
    expect(sent.parts[0]!.sections[0]!.matrix.unit).toBe("PERCENT");
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("Giữ số cũ: bảng đổi sang số câu của tổng 30 và chỉ tiêu chủ đề giữ 3 câu, bỏ percent", async () => {
    const server = boot({ section: section() });
    const user = await changeTotal();
    await user.click(await screen.findByRole("button", { name: "Giữ số cũ" }));
    expect(screen.queryByText(/Tổng đổi thành/)).not.toBeInTheDocument();
    const difficulty = ["Nhận biết", "Thông hiểu", "Vận dụng"].map((name) => (screen.getByRole("textbox", { name: new RegExp(`${name} · số câu`) }) as HTMLInputElement).value);
    expect(difficulty).toEqual(["8", "15", "7"]);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ matrix: { unit: string }; topicQuotas: TopicQuota[] }> }> };
    expect(sent.parts[0]!.sections[0]!.matrix.unit).toBe("COUNT");
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 3 });
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]!.percent).toBeUndefined();
  });

  it("R1-02: Lưu khi chưa chọn Áp dụng hay Giữ: không gửi, mở môn, thông báo còn đó; chọn xong thì gửi count 2", async () => {
    const server = boot({ section: section() });
    const user = await changeTotal();
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    expect(await screen.findByText(/tổng số câu của môn .* đã đổi/)).toBeInTheDocument();
    expect(screen.getByText(/Tổng đổi thành 20 câu/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Áp dụng số mới" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ topicQuotas: TopicQuota[] }> }> };
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("R2-07: sau Áp dụng số mới hoặc Giữ số cũ tiêu điểm về ô Số câu của mục, không rơi về thân trang", async () => {
    for (const action of ["Áp dụng số mới", "Giữ số cũ"]) {
      boot({ section: section() });
      const user = await changeTotal();
      await user.click(await screen.findByRole("button", { name: action }));
      await waitFor(() => expect(screen.getByRole("textbox", { name: "Số câu của mục" })).toHaveFocus());
      cleanup();
    }
  });

  it("R1-25: chỉ tiêu dạng trả lời nhập theo tỷ lệ cũng hiện trong thông báo; Giữ số cũ đổi sang số câu của tổng 30", async () => {
    const server = boot({
      section: { answerQuotas: { unit: "PERCENT", items: [{ answerType: "SINGLE_CHOICE", value: 50 }, { answerType: "TRUE_FALSE", value: 50 }] } },
    });
    const user = await changeTotal();
    expect(await screen.findByRole("alert")).toHaveTextContent(/Dạng trả lời: .* 10 · .* 10/);
    await user.click(screen.getByRole("button", { name: "Giữ số cũ" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ answerQuotas: { unit: string; items: Array<{ value: number }> } }> }> };
    expect(sent.parts[0]!.sections[0]!.answerQuotas).toMatchObject({ unit: "COUNT", items: [{ value: 15 }, { value: 15 }] });
  });
});

describe("R1-03: đổi tổng của nhóm tính lại chỉ tiêu chủ đề theo tỷ lệ", () => {
  it("R1-28: môn 50 câu, nhóm 30 → 20 với chỉ tiêu 10%: tính trên tổng của nhóm (3 → 2 câu), lưu count 2, percent 10", async () => {
    const server = boot({
      section: {
        questionCount: 50,
        blocks: [
          {
            id: "blk-1",
            name: "Choice",
            questionType: "SINGLE_CHOICE",
            questionCount: 30,
            requiredPointsPerQuestion: 1,
            topicQuotas: [{ id: "t1", name: "Xác suất", nodeIds: [], count: 3, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" }],
          },
        ],
      },
    });
    const user = userEvent.setup();
    open("?block=blk-1");
    const count = await screen.findByRole("textbox", { name: /^Số câu cần lấy/ });
    expect(screen.getByText("3 câu")).toBeInTheDocument();
    await user.clear(count);
    await user.type(count, "20");
    expect(await screen.findByText("2 câu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ blocks: Array<{ topicQuotas: TopicQuota[] }> }> }> };
    expect(sent.parts[0]!.sections[0]!.blocks[0]!.topicQuotas[0]).toMatchObject({ count: 2, percent: 10 });
  });
});

describe("Row 8: Đủ nguồn theo ma trận (R-122)", () => {
  const checked = [{ partId: "part-1", sectionId: "sec-1", subjectCode: "MATH", state: "CHECKED" }];

  it("không hiện khi chưa kiểm tra đạt", async () => {
    boot();
    open();
    await screen.findByText("Khớp số câu của môn");
    expect(screen.queryByText("Đủ nguồn theo ma trận")).not.toBeInTheDocument();
  });

  it("hiện khi môn đã kiểm tra đạt và hết khi sửa môn", async () => {
    boot({ sectionStates: checked });
    const user = userEvent.setup();
    open();
    expect(await screen.findByText("Đủ nguồn theo ma trận")).toBeInTheDocument();
    await makeDirty(user);
    expect(screen.queryByText("Đủ nguồn theo ma trận")).not.toBeInTheDocument();
  });

  it("hiện sau khi bấm Kiểm tra môn và máy chủ trả môn đạt", async () => {
    const server = boot();
    server.on("POST", `${EXAM_PATH}/check`, { status: 200, body: { ...checkResultWire("VALID"), sectionStates: checked } });
    const user = userEvent.setup();
    open();
    await screen.findByText("Khớp số câu của môn");
    expect(screen.queryByText("Đủ nguồn theo ma trận")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Kiểm tra môn" }));
    expect(await screen.findByText("Đủ nguồn theo ma trận")).toBeInTheDocument();
  });
});

describe("Row 12: mẫu khóa số câu (H27)", () => {
  const template = (fixedCounts: boolean) => ({
    code: "T_FIXED",
    kindCode: "HSA",
    scope: "FULL",
    timing: "PER_PART",
    label: "Mẫu",
    active: true,
    isDefault: true,
    buildMode: "STANDARD",
    publishable: true,
    fixedCounts,
    structure: { parts: [] },
  });

  it("khóa ô số câu của mục; phân bổ bên trong mục vẫn sửa được", async () => {
    boot({ template: template(true) });
    open();
    const count = await screen.findByRole("textbox", { name: "Số câu của mục" });
    expect(count).toHaveAttribute("readonly");
    expect(screen.getByText("Mẫu đã khóa số câu")).toBeInTheDocument();
  });

  it("nhóm trong mục khóa vẫn sửa được số câu", async () => {
    boot({ template: template(true) });
    open("?block=blk-1");
    const groupCount = await screen.findByRole("textbox", { name: /Số câu cần lấy/ });
    expect(groupCount).not.toHaveAttribute("readonly");
  });

  it("mẫu không khóa: ô số câu của mục sửa được", async () => {
    boot({ template: template(false) });
    open();
    const count = await screen.findByRole("textbox", { name: "Số câu của mục" });
    expect(count).not.toHaveAttribute("readonly");
  });

  it("R1-11: mẫu khóa số câu thì ô số câu của phần và số câu trên đề cũng chỉ đọc; mẫu không khóa thì sửa được", async () => {
    boot({ template: template(true) });
    const locked = open("?part=part-1");
    for (const name of [/^Số câu trên đề/, /^Số câu phải làm/]) {
      expect(await screen.findByRole("textbox", { name })).toHaveAttribute("readonly");
    }
    expect(screen.getAllByText("Mẫu đã khóa số câu").length).toBeGreaterThanOrEqual(2);
    locked.unmount();

    boot({ template: template(false) });
    open("?part=part-1");
    for (const name of [/^Số câu trên đề/, /^Số câu phải làm/]) {
      expect(await screen.findByRole("textbox", { name })).not.toHaveAttribute("readonly");
    }
  });
});

function Harness({ initial, total = 30 }: { initial: TopicQuota[]; total?: number }) {
  const [quotas, setQuotas] = useState(initial);
  return (
    <>
      <TopicQuotaTable id="s1" quotas={quotas} onChange={setQuotas} total={total} levelName="Toán học" subjectCode="MATH" reference={reference} axes={axes} labels={labels} />
      <output data-testid="state">{JSON.stringify(quotas)}</output>
    </>
  );
}
const quota = (over: Partial<TopicQuota> = {}): TopicQuota => ({ id: "q1", name: "Xác suất", nodeIds: [], count: 5, enforcement: "REQUIRED", basis: "QUESTION", ...over });
const stateOf = () => JSON.parse(screen.getByTestId("state").textContent ?? "[]") as TopicQuota[];

describe("Row 10: gắn MAPID cho dòng Chờ mapping (D12)", () => {
  it("gắn mã giữ số câu, mức và giới hạn; hết nhãn Chờ mapping", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[quota({ gradeCodes: ["GRADE_12"], difficultyCodes: ["HIGH_APPLICATION"] })]} />);
    expect(screen.getByText("Chờ mapping")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Gắn MAPID cho Xác suất" }));
    const dialog = await screen.findByRole("dialog");
    const boxes = await within(dialog).findAllByRole("checkbox");
    await user.click(boxes.find((box) => !(box as HTMLInputElement).disabled && box.getAttribute("aria-label")?.includes("Mệnh đề")) ?? boxes[0]!);
    await user.click(within(dialog).getByRole("button", { name: /^Áp dụng/ }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.queryByText("Chờ mapping")).not.toBeInTheDocument();
    expect(stateOf()[0]).toMatchObject({ name: "Xác suất", count: 5, enforcement: "REQUIRED", gradeCodes: ["GRADE_12"], difficultyCodes: ["HIGH_APPLICATION"] });
    expect(stateOf()[0]!.nodeIds.length).toBeGreaterThan(0);
  });
});

describe("Làm tròn chỉ tiêu chủ đề (ADV:FE-3b:2)", () => {
  it("50% + 50% trên 5 câu: 3 + 3 vượt 1 câu, báo ở khối, không tự sửa; Phần còn lại không âm", () => {
    renderWithProviders(<Harness total={5} initial={[quota({ id: "a", name: "A", count: 3, percent: 50 }), quota({ id: "b", name: "B", count: 3, percent: 50 })]} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Đã phân bổ 6/5 · Vượt 1 câu");
    expect(stateOf().map((row) => row.count)).toEqual([3, 3]);
    const rest = screen.getByRole("row", { name: /Phần còn lại/ });
    expect(rest).toHaveTextContent("0 câu");
    expect(rest).not.toHaveTextContent("-");
  });
});

describe("Xóa dòng chỉ tiêu chủ đề giữ tiêu điểm trong bảng (R1-26)", () => {
  it("bấm xóa dòng thì tiêu điểm chuyển sang nút Thêm chỉ tiêu chủ đề, không rơi về thân trang", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[quota({ id: "a", name: "A" }), quota({ id: "b", name: "B" })]} />);
    await user.click(screen.getByRole("button", { name: "Xóa chỉ tiêu A" }));
    expect(stateOf().map((row) => row.id)).toEqual(["b"]);
    await waitFor(() => expect(screen.getByRole("button", { name: "Thêm chỉ tiêu chủ đề" })).toHaveFocus());
  });
});

describe("HSA Tiếng Anh khối lớp chờ chuyên gia (ADV:FE-3b:2)", () => {
  const section: ExamSection = { id: "s", subjectCode: "ANH", questionCount: 50, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: null, blocks: [] };
  const grade = (buckets: ExamQuota["buckets"]): ExamQuota => ({
    id: "hsa-english-grade",
    name: "Lớp Tiếng Anh tham khảo",
    dimension: "GRADE",
    basis: "QUESTION",
    enforcement: "REFERENCE",
    subjectCodes: ["ANH"],
    buckets,
  });
  const render = (quota: ExamQuota, kind: string) =>
    renderWithProviders(<ReferenceDistributions entries={referenceEntries(section, [quota], kind)} labels={labels} subjectName="Tiếng Anh" onUse={vi.fn()} onApply={vi.fn()} />);

  it("có số 30/70: chip vẫn hiện, hai nút bật", () => {
    render(grade([{ codes: ["GRADE_10", "GRADE_11"], percent: 30 }, { codes: ["GRADE_12"], percent: 70 }]), "HSA");
    expect(screen.getByText("Chờ chuyên gia xác nhận")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Áp dụng phân bổ này/ })).toBeEnabled();
  });

  it("chưa có số: chip hiện và hai nút khóa", () => {
    render(grade([{ codes: ["GRADE_12"], percent: 0 }]), "HSA");
    expect(screen.getByText("Chờ chuyên gia xác nhận")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Áp dụng phân bổ này/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Dùng ma trận tham khảo/ })).toBeDisabled();
  });

  it("kỳ thi khác có số thì không có chip", () => {
    render(grade([{ codes: ["GRADE_12"], percent: 100 }]), "SP2E");
    expect(screen.queryByText("Chờ chuyên gia xác nhận")).not.toBeInTheDocument();
  });
});

describe("H20: không xác định được cấp từ fieldErrors (ADV:FE-3b:2)", () => {
  it("hiện lỗi ở khối chỉ tiêu của nhóm đang sửa, không chỉ ở thông báo nổi", async () => {
    const topicQuotas = Array.from({ length: 8 }, (_, index) => ({ id: `t${index}`, name: `Chủ đề ${index}`, nodeIds: [], count: 1, enforcement: "REFERENCE" }));
    const base = FIXED_PART.sections![0] as object;
    const part = {
      ...FIXED_PART,
      questionCount: 30,
      sections: [{ ...base, questionCount: 30, blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1, topicQuotas }] }],
    };
    const server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 30 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
      .on("PUT", STRUCTURE, { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", message: "x", fieldErrors: [{ field: "topicQuotas", code: "MAX_TOPIC_QUOTAS" }] } })
      .install();
    const user = userEvent.setup();
    open("?block=blk-1");
    const first = await screen.findByRole("textbox", { name: "Tên chủ đề của chỉ tiêu 1" });
    await user.type(first, "x");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const block = screen.getByRole("region", { name: /Chỉ tiêu chủ đề của/ }).closest("section")!;
    expect(await within(block).findByRole("alert")).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
  });
});

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});
