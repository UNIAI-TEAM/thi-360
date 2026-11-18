import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { remainderGroupCount, resolveQuotaCounts, sectionCaseCounts, subjectAllocation } from "../subject-matrix";
import en from "../../../messages/en.json";
import vi_ from "../../../messages/vi.json";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

let backend: ExamsBackend;

beforeEach(() => {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
});

function openStructure(examId: string, search = "") {
  window.history.replaceState(null, "", `/exams/${examId}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

const AXES = {
  grades: reference.grades.map((grade) => grade.code),
  difficulties: reference.difficulties.map((difficulty) => difficulty.code),
};

/** Môn Toán HSA (ex-hsa-f-001): 50 câu, chỉ tiêu dạng trả lời Một đáp án 35 + Trả lời ngắn 15, một nhóm câu đơn mở và một nhóm chùm. */
function mathSection() {
  return backend.app("ex-hsa-f-001").parts[0]!.sections[0]!;
}

function tableRow(table: HTMLElement, name: string | RegExp) {
  const row = within(table).getByRole("rowheader", { name }).closest("tr");
  if (!row) throw new Error(`khong thay dong ${name}`);
  return within(row);
}

describe("số đã phân bổ và còn thiếu của môn", () => {
  it("câu con của chùm trừ vào chỉ tiêu dạng trả lời: chùm 2 × 5 câu con Một đáp án giảm còn thiếu 35 → 25", () => {
    const section = mathSection();
    expect(section.answerQuotas?.items).toEqual([
      { answerType: "SINGLE_CHOICE", value: 35 },
      { answerType: "SHORT_ANSWER", value: 15 },
    ]);
    // Chùm chưa giới hạn dạng câu con: chưa trừ được vào dạng nào, 50 câu của hai nhóm đều là câu chưa xác định dạng.
    const before = subjectAllocation(section, AXES, 50);
    expect(before.answerTypes?.map((row) => [row.key, row.quota, row.allocated, row.missing])).toEqual([
      ["SINGLE_CHOICE", 35, 0, 35],
      ["SHORT_ANSWER", 15, 0, 15],
    ]);
    expect(before).toMatchObject({ allocated: 50, missing: 0, openAllocated: 50, openMissing: 0 });

    const withChildren = {
      ...section,
      blocks: section.blocks.map((block) => (block.questionType === "QUESTION_SET" ? { ...block, childAnswerType: "SINGLE_CHOICE" as const } : block)),
    };
    const after = subjectAllocation(withChildren, AXES, 50);
    expect(after.answerTypes?.map((row) => [row.key, row.allocated, row.missing])).toEqual([
      ["SINGLE_CHOICE", 10, 25],
      ["SHORT_ANSWER", 0, 15],
    ]);
    // Nhóm câu đơn mở (40 câu) vừa đủ phủ phần chỉ tiêu còn thiếu 25 + 15.
    expect(after).toMatchObject({ openAllocated: 40, openMissing: 0 });
    // Chia sẵn từ đầu: nhóm câu đơn lấy 25 Một đáp án + 15 Trả lời ngắn (không còn là "chưa xác định dạng").
    expect(after.openSplit).toEqual([
      { key: "SINGLE_CHOICE", count: 25 },
      { key: "SHORT_ANSWER", count: 15 },
    ]);
    // Chưa đặt dạng câu con: cả 50 câu là phần mở, chia sẵn 35 + 15 theo chỉ tiêu môn.
    expect(before.openSplit).toEqual([
      { key: "SINGLE_CHOICE", count: 35 },
      { key: "SHORT_ANSWER", count: 15 },
    ]);
  });

  it("quy chỉ tiêu theo tỷ lệ ra số câu bằng phần dư lớn nhất; môn Khoa học có hai trường hợp 16 và 17 câu", () => {
    expect(resolveQuotaCounts("PERCENT", [70, 30], 17)).toEqual([12, 5]);
    expect(resolveQuotaCounts("COUNT", [35, 15], 50)).toEqual([35, 15]);
    const science = backend.app("ex-hsa-f-001").parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    expect(sectionCaseCounts(science.sections[0]!, science)).toEqual([16, 17]);
    // Nhóm "lấy phần còn lại" nhận phần còn lại của môn: Lịch sử có 10 câu cố định.
    const history = science.sections.find((section) => section.subjectCode === "HISTORY")!;
    expect(subjectAllocation(history, AXES, 16)).toMatchObject({ fixedTotal: 10, remainder: 6, allocated: 16, missing: 0 });
    expect(subjectAllocation(history, AXES, 17)).toMatchObject({ remainder: 7, allocated: 17, missing: 0 });
  });

  it("hai nhóm lấy phần còn lại: không nhóm nào nhận, môn báo còn thiếu", () => {
    const science = backend.app("ex-hsa-f-001").parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    const history = science.sections.find((section) => section.subjectCode === "HISTORY")!;
    expect(remainderGroupCount(history.blocks)).toBe(1);
    const doubled = { ...history, blocks: history.blocks.map((block) => ({ ...block, questionCount: null })) };
    expect(remainderGroupCount(doubled.blocks)).toBe(2);
    expect(subjectAllocation(doubled, AXES, 16)).toMatchObject({ fixedTotal: 0, allocated: 0, missing: 16 });
  });
});

describe("màn môn (Toán HSA)", () => {
  it("hiện chỉ tiêu cấp môn với đã phân bổ và còn thiếu; nhóm chưa đặt MAPID hiện phạm vi của môn; đổi dạng câu con thì còn thiếu giảm", async () => {
    const user = userEvent.setup();
    openStructure("ex-hsa-f-001");
    expect(await screen.findByRole("heading", { name: "Môn Toán" })).toBeInTheDocument();
    expect(screen.getByText("Khớp số câu của môn")).toBeInTheDocument();

    const quota = screen.getByRole("region", { name: "Dạng trả lời" });
    const single = tableRow(quota, "Một đáp án");
    expect(single.getByRole("textbox", { name: "Chỉ tiêu Một đáp án" })).toHaveValue("35");
    expect(single.getAllByRole("cell").map((cell) => cell.textContent)).toContain("35");
    expect(tableRow(quota, "Trả lời ngắn").getByRole("textbox", { name: "Chỉ tiêu Trả lời ngắn" })).toHaveValue("15");

    // Nhóm chưa đặt MAPID hiện "Theo phạm vi của môn" (môn Toán có phạm vi MAPID).
    const groups = screen.getByRole("region", { name: "Nhóm lấy câu" });
    const singles = tableRow(groups, "Câu đơn theo chỉ tiêu dạng trả lời");
    expect(singles.getByText("Theo phạm vi của môn")).toBeInTheDocument();
    expect(singles.getByText("Theo chỉ tiêu của môn")).toBeInTheDocument();
    const sets = tableRow(groups, "Chùm ngữ cảnh");
    expect(sets.getByText("2 chùm × 5 câu con = 10 câu")).toBeInTheDocument();

    // Chia dạng hiện từ đầu, không còn câu "chưa xác định dạng".
    expect(screen.getByText("Nhóm câu đơn (50 câu) chia theo chỉ tiêu của môn: 35 Một đáp án + 15 Trả lời ngắn.")).toBeInTheDocument();
    expect(screen.queryByText(/chưa xác định dạng/)).not.toBeInTheDocument();

    // Chùm giới hạn câu con là Một đáp án: 10 câu con trừ vào chỉ tiêu Một đáp án (còn thiếu 35 → 25).
    await user.click(sets.getByRole("button", { name: "Mở nhóm Chùm ngữ cảnh" }));
    await user.click(await screen.findByRole("combobox", { name: /Dạng của câu con/ }));
    await user.click(await screen.findByRole("option", { name: "Một đáp án" }));
    await user.click(screen.getByRole("button", { name: "Về màn môn" }));
    const after = tableRow(await screen.findByRole("region", { name: "Dạng trả lời" }), "Một đáp án");
    expect(after.getAllByRole("cell").map((cell) => cell.textContent)).toEqual(expect.arrayContaining(["35", "10", "25"]));
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  }, 20_000);

  it("nhóm: dạng trả lời điền sẵn chỉ đọc; không còn công tắc MAPID cấp dưới và Quy tắc đảo; ma trận riêng của nhóm mặc định đóng", async () => {
    openStructure("ex-hsa-f-001", "?block=blk-27");
    expect(await screen.findByRole("heading", { name: /Nhóm lấy câu · / })).toBeInTheDocument();
    expect(screen.queryByText("Quy tắc đảo")).not.toBeInTheDocument();
    expect(screen.queryByText(/Bao gồm MAPID cấp dưới/)).not.toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: /cấp dưới/ })).not.toBeInTheDocument();
    // Ngữ văn: nhóm Trắc nghiệm có dạng trả lời điền sẵn.
    const locked = backend.app("ex-hsa-f-001").parts[1]!.sections[0]!.blocks.find((block) => block.id === "blk-27")!;
    expect(locked.answerTypeLocked).toBe(true);
    expect(screen.getByRole("textbox", { name: /Dạng trả lời/ })).toHaveAttribute("readonly");
    expect(screen.getByText("Ma trận lớp và độ khó riêng của nhóm").closest("details")).not.toHaveAttribute("open");
  });

  it("nhóm của môn không có MAPID riêng hiện 'Theo phạm vi của môn'; hủy hộp chọn giữ nguyên kế thừa, không có thay đổi chưa lưu (R1-3)", async () => {
    const user = userEvent.setup();
    openStructure("ex-hsa-f-001", "?block=blk-22");
    const scope = (await screen.findByText("Phạm vi kiến thức (MAPID)")).closest("fieldset")!;
    expect(within(scope).getByText("Theo phạm vi của môn")).toBeInTheDocument();
    await user.click(within(scope).getByRole("button", { name: "Đặt phạm vi riêng" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(within(scope).getByText("Theo phạm vi của môn")).toBeInTheDocument();
    expect(within(scope).queryByRole("button", { name: "Dùng phạm vi của môn" })).not.toBeInTheDocument();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
  }, 20_000);

  it("nhóm V-ACT: bỏ chọn rồi chọn lại năng lực vẫn 'Theo phạm vi của môn', không thành phạm vi riêng 0 kiến thức (R1-4)", async () => {
    const user = userEvent.setup();
    openStructure("ex-vact-f-001", "?block=blk-v1-1");
    const scope = (await screen.findByText("Phạm vi kiến thức (MAPID)")).closest("fieldset")!;
    const competency = await screen.findByRole("checkbox", { name: "Sử dụng ngôn ngữ · Tiếng Việt" });
    expect(competency).toBeChecked();
    expect(within(scope).getByText("Theo phạm vi của môn")).toBeInTheDocument();
    await user.click(competency);
    expect(competency).not.toBeChecked();
    expect(within(scope).getByText("Theo phạm vi của môn")).toBeInTheDocument();
    await user.click(competency);
    expect(competency).toBeChecked();
    expect(within(scope).getByText("Theo phạm vi của môn")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Về màn môn" }));
    const groups = await screen.findByRole("region", { name: "Nhóm lấy câu" });
    expect(tableRow(groups, "Tiếng Việt").queryByText(/kiến thức riêng/)).not.toBeInTheDocument();
  }, 20_000);

  it("nhóm dùng dạng chưa khai chỉ tiêu: bảng hiện hàng riêng, đã phân bổ 10, thiếu -10 (R1-6)", async () => {
    backend.mutate("ex-hsa-f-001", (exam) => {
      exam.parts[0]!.sections![0]!.blocks!.find((block) => block.questionType === "QUESTION_SET")!.childAnswerType = "MULTIPLE_CHOICE";
    });
    openStructure("ex-hsa-f-001");
    const quota = await screen.findByRole("region", { name: "Dạng trả lời" });
    const extra = tableRow(quota, /^Nhiều đáp án/);
    expect(extra.getByText("Chưa khai chỉ tiêu")).toBeInTheDocument();
    expect(extra.getAllByRole("cell").map((cell) => cell.textContent)).toEqual(expect.arrayContaining(["10", "-10"]));
    expect(within(quota).queryByRole("textbox", { name: "Chỉ tiêu Nhiều đáp án" })).not.toBeInTheDocument();
  }, 20_000);

  it("nhóm chưa đặt tên: nút mở nhóm có tên 'Mở nhóm Chưa đặt tên' (R1-9)", async () => {
    backend.mutate("ex-hsa-f-001", (exam) => {
      exam.parts[0]!.sections![0]!.blocks![0]!.name = "";
    });
    openStructure("ex-hsa-f-001");
    const groups = await screen.findByRole("region", { name: "Nhóm lấy câu" });
    expect(within(groups).getByRole("button", { name: "Mở nhóm Chưa đặt tên" })).toBeInTheDocument();
  }, 20_000);
});

describe("HSA Khoa học: một phương án chọn môn", () => {
  it("5 môn soạn một lần, học viên chọn 3 môn, quy tắc chia B10 chỉ đọc, không có danh sách 10 tổ hợp", async () => {
    openStructure("ex-hsa-f-001", "?part=part-33");
    expect(await screen.findByRole("heading", { name: "Khoa học" })).toBeInTheDocument();
    expect(screen.getByText("Học viên chọn 3 trong 5 môn")).toBeInTheDocument();
    expect(screen.getByText("Mỗi môn soạn một lần, không khai lặp lại cho từng bộ ba môn.")).toBeInTheDocument();
    expect(screen.getByText(/Nhóm có nhiều môn được chọn hơn nhận 17 câu mỗi môn, môn của nhóm còn lại nhận 16 câu/)).toBeInTheDocument();
    // Ba ví dụ của B10: 17 + 17 + 16; 16 + 17 + 17; ba môn tự nhiên có ba cách chia.
    expect(screen.getByText(/tự nhiên \+ tự nhiên \+ xã hội: 17 \+ 17 \+ 16/)).toBeInTheDocument();
    expect(screen.getByText(/tự nhiên \+ xã hội \+ xã hội: 16 \+ 17 \+ 17/)).toBeInTheDocument();
    expect(screen.getByText(/tự nhiên \+ tự nhiên \+ tự nhiên: 16 \+ 17 \+ 17 hoặc 17 \+ 16 \+ 17 hoặc 17 \+ 17 \+ 16/)).toBeInTheDocument();

    const card = screen.getByRole("region", { name: "Khoa học" });
    expect(within(card).getAllByRole("button", { name: /^Thiết lập môn / })).toHaveLength(5);
    expect(screen.queryByText(/KH-\d/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sinh tổ hợp" })).not.toBeInTheDocument();
    expect(screen.queryByText("Chờ chuyên gia xác nhận")).not.toBeInTheDocument();
    expect(screen.queryByText("chờ chuyên gia xác nhận")).not.toBeInTheDocument();
  });

  it("phần Khoa học chỉ có phương án chọn môn: quy tắc tham gia chỉ đọc, không có ô 'chọn k trong 1 phương án'; phần có hai phương án giữ ô chọn (R1-10)", async () => {
    const { unmount } = openStructure("ex-hsa-f-001", "?part=part-33");
    expect(await screen.findByRole("textbox", { name: "Số phương án phải chọn" })).toHaveValue("1");
    unmount();
    backend.mutate("ex-hsa-f-001", (exam) => {
      const part = exam.parts[2]!;
      part.options = part.options!.filter((option) => option.kind === "SUBJECT_CHOICE");
    });
    openStructure("ex-hsa-f-001", "?part=part-33");
    expect(await screen.findByRole("heading", { name: "Quy tắc tham gia" })).toBeInTheDocument();
    expect(screen.getByText("Học viên làm phương án chọn môn bên dưới.")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Số phương án phải chọn" })).not.toBeInTheDocument();
    expect(screen.queryByText(/trong 1 phương án/)).not.toBeInTheDocument();
    expect(screen.getByText("Học viên chọn 3 trong 5 môn")).toBeInTheDocument();
  });

  it("mở một môn: số câu 16 hoặc 17 theo lựa chọn, nhóm 'phần còn lại' nhận 6 hoặc 7 câu", async () => {
    const user = userEvent.setup();
    const history = backend.app("ex-hsa-f-001").parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections.find((section) => section.subjectCode === "HISTORY")!;
    openStructure("ex-hsa-f-001", `?section=${history.id}`);
    expect(await screen.findByRole("heading", { name: /Môn Lịch sử/ })).toBeInTheDocument();
    expect(screen.getByText("Môn xã hội")).toBeInTheDocument();
    expect(screen.getByText(/Số câu của môn: 16 \/ 17/)).toBeInTheDocument();
    const groups = screen.getByRole("region", { name: "Nhóm lấy câu" });
    expect(within(groups).getByText("Phần còn lại (6 câu)")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "17 câu" }));
    expect(within(groups).getByText("Phần còn lại (7 câu)")).toBeInTheDocument();
  });

  it("môn đã có nhóm lấy phần còn lại: ô 'Lấy phần còn lại' của nhóm khác bị khóa; nhóm đang giữ vẫn bỏ chọn được (R1-5)", async () => {
    const user = userEvent.setup();
    openStructure("ex-hsa-f-001", "?block=blk-sci-1-4-a");
    const fixed = await screen.findByRole("checkbox", { name: "Lấy phần còn lại của môn" });
    expect(fixed).toBeDisabled();
    expect(screen.getByText("Môn đã có nhóm lấy phần còn lại khác; bỏ chọn ở nhóm đó trước.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Về màn môn" }));
    await user.click(await screen.findByRole("button", { name: "Mở nhóm Phần còn lại" }));
    const holder = await screen.findByRole("checkbox", { name: "Lấy phần còn lại của môn" });
    expect(holder).toBeChecked();
    expect(holder).toBeEnabled();
  }, 20_000);

  it("nhóm trùng 'phần còn lại' do dữ liệu: màn môn báo lỗi và không nhóm nào nhận phần còn lại (R1-5)", async () => {
    const history = backend.app("ex-hsa-f-001").parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections.find((section) => section.subjectCode === "HISTORY")!;
    backend.mutate("ex-hsa-f-001", (exam) => {
      const choice = exam.parts[2]!.options!.find((option) => option.kind === "SUBJECT_CHOICE")!;
      for (const block of choice.sections!.find((section) => section.subjectCode === "HISTORY")!.blocks!) delete block.questionCount;
    });
    openStructure("ex-hsa-f-001", `?section=${history.id}`);
    const groups = await screen.findByRole("region", { name: "Nhóm lấy câu" });
    expect(screen.getByText(/Môn có 2 nhóm lấy phần còn lại, chỉ được một nhóm/)).toBeInTheDocument();
    expect(within(groups).getAllByText("Phần còn lại (chưa chia được)")).toHaveLength(2);
    expect(screen.getByText("Còn thiếu 16 câu")).toBeInTheDocument();
  }, 20_000);

  it("đường dẫn ?section= mở lại đúng môn; tải lại không mất vị trí", async () => {
    openStructure("ex-hsa-f-001", `?section=${mathSection().id}`);
    expect(await screen.findByRole("heading", { name: "Môn Toán" })).toBeInTheDocument();
  });
});

describe("khóa dịch", () => {
  it("màn Cấu trúc không còn chữ 'Bao gồm MAPID cấp dưới' và 'Quy tắc đảo' trong vi.json, en.json", () => {
    // Chỉ xét màn soạn cấu trúc: "Quy tắc đảo câu" còn là tên trường trong dòng thay đổi ở tab Phiên bản (change-where).
    for (const messages of [vi_, en]) {
      const text = JSON.stringify(messages.exams.structure);
      expect(text).not.toContain("Bao gồm MAPID cấp dưới");
      expect(text).not.toContain("Quy tắc đảo");
      expect(text).not.toMatch(/Include (sub-?levels|descendants|child)/i);
      expect(text).not.toMatch(/"Shuffle rule"/);
      // Khóa của ô và công tắc đã bỏ khỏi nhóm lấy câu.
      expect(Object.keys(messages.exams.structure.block)).not.toEqual(expect.arrayContaining(["shuffle", "shuffleRules"]));
    }
  });
});
