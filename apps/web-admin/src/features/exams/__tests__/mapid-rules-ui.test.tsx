import { screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { saveBlockers } from "../allocation";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { HsaExamContext } from "../hsa-enforcement";
import { focusMapidRule } from "../mapid-rules";
import type { ExamPart, ExamSection, MapidRule, QuestionBlock } from "../types";

// Danh mục mẫu đặt Toán là `MATH`, còn cấu trúc HSA dùng mã môn thật `TOAN`: đổi mã trong danh mục để hộp chọn MAPID lọc đúng môn.
const { asToan } = vi.hoisted(() => ({ asToan: (ref: unknown) => JSON.parse(JSON.stringify(ref).replaceAll('"MATH"', '"TOAN"')) as ContentReference }));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: Parameters<typeof getContentReferenceFixture>[0]) => asToan(await getContentReferenceFixture(locale));
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const reference = asToan(referenceFixture);
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSubjectAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

const TOPIC = { id: "kn-0D1", name: "Mệnh đề và tập hợp" };

const block = (over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b-short",
  name: "Trả lời ngắn",
  questionType: "SHORT_ANSWER",
  questionCount: 15,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: null,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

function makeSection(over: Partial<ExamSection> = {}): ExamSection {
  return {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 50,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [
      block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }),
      block(),
      block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 }),
    ],
    ...over,
  };
}

const makePart = (section: ExamSection): ExamPart => ({ id: "p1", sections: [section], options: [] }) as unknown as ExamPart;

/** Giữ môn trong state như `ExamStructureView` và ghi lại môn mới nhất. */
function renderPanel(initial: ExamSection, { hsa = true, kind = "HSA" }: { hsa?: boolean; kind?: string | null } = {}) {
  const latest = { section: initial };
  function Harness() {
    const [section, setSection] = useState(initial);
    return (
      <HsaExamContext.Provider value={hsa}>
        <ExamStructureSubjectPanel
          examId="exam-1"
          examKind={kind}
          context={{ part: makePart(section), option: null, section, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
          subjectName="Toán học"
          axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
          labels={{ grade: (code) => code, difficulty: (code) => code }}
          reference={reference as unknown as ContentReference}
          onChange={(update) => {
            const next = update(section);
            latest.section = next;
            setSection(next);
          }}
          onSelectBlock={() => {}}
          onAddBlock={() => {}}
        />
      </HsaExamContext.Provider>
    );
  }
  renderWithProviders(<Harness />);
  return latest;
}

const user = () => userEvent.setup();
const table = () => screen.getByTestId("mapid-rules-table");
const ruleDialog = (name = "Thêm quy tắc") => screen.getByRole("dialog", { name });

async function pickTopic(u: UserEvent) {
  await u.click(within(ruleDialog()).getByTestId("mapid-rule-mapid-picker"));
  const picker = await screen.findByRole("dialog", { name: "Chọn kiến thức (MAPID)" });
  await u.type(within(picker).getByRole("searchbox", { name: "Tìm kiến thức" }), "0D1");
  await u.click(await within(picker).findByRole("checkbox", { name: /0D1 Mệnh đề và tập hợp/ }));
  await u.click(within(picker).getByRole("button", { name: "Áp dụng (1)" }));
}

async function openAdd(u: UserEvent) {
  await u.click(screen.getByTestId("mapid-rule-add"));
  return ruleDialog();
}

async function selectScope(u: UserEvent, optionPattern: RegExp) {
  const scopeBox = within(screen.getByRole("dialog", { name: /^(Thêm|Sửa) quy tắc$/ })).getByTestId("mapid-rule-scope");
  await u.click(within(scopeBox).getByRole("combobox"));
  await u.click(await screen.findByRole("option", { name: optionPattern }));
}

describe("Chế độ MAPID của môn HSA", () => {
  it("chưa có dữ liệu: Toàn bài, không có bảng quy tắc, ẩn phạm vi cũ và Chỉ tiêu chủ đề", () => {
    renderPanel(makeSection());
    expect(screen.getByRole("radio", { name: "Toàn bài (không giới hạn MAPID)" })).toBeChecked();
    expect(screen.queryByTestId("mapid-rules-table")).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Theo chủ đề" })).not.toBeInTheDocument();
    expect(screen.queryByText("Chỉ tiêu chủ đề")).not.toBeInTheDocument();
  });

  it("chọn Theo ma trận MAPID: ghi mapidMode, hiện bảng; về Toàn bài: bảng ẩn, quy tắc còn, ghi chú tạm ngừng", async () => {
    const u = user();
    const rules: MapidRule[] = [{ id: "r1", type: "PRESENT", groupId: null, nodeIds: [TOPIC.id] }];
    const latest = renderPanel(makeSection({ mapidMode: "ALL", mapidRules: rules }));
    expect(screen.getByTestId("mapid-paused-note")).toHaveTextContent("1 quy tắc tạm ngừng áp dụng");
    await u.click(screen.getByRole("radio", { name: "Theo ma trận MAPID" }));
    expect(latest.section.mapidMode).toBe("MATRIX");
    expect(within(table()).getByTestId("mapid-rule-row-r1")).toHaveTextContent("Toàn môn · Phải có Mệnh đề và tập hợp");
    expect(screen.queryByTestId("mapid-paused-note")).not.toBeInTheDocument();
    await u.click(screen.getByRole("radio", { name: "Toàn bài (không giới hạn MAPID)" }));
    expect(latest.section.mapidMode).toBe("ALL");
    expect(latest.section.mapidRules).toEqual(rules);
    expect(screen.queryByTestId("mapid-rules-table")).not.toBeInTheDocument();
    expect(screen.getByTestId("mapid-paused-note")).toBeInTheDocument();
  });
});

describe("Thêm quy tắc qua hộp thoại", () => {
  it("Phải xuất hiện: không có ô số; lưu thành PRESENT cả môn và hiện câu", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    await pickTopic(u);
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    expect(within(dialog).queryByTestId("mapid-rule-percent")).not.toBeInTheDocument();
    expect(within(dialog).queryByTestId("mapid-rule-comparator")).not.toBeInTheDocument();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules).toEqual([expect.objectContaining({ type: "PRESENT", groupId: null, nodeIds: [TOPIC.id] })]);
    expect(latest.section.mapidRules![0]).not.toHaveProperty("count");
    expect(table()).toHaveTextContent("Toàn môn · Phải có Mệnh đề và tập hợp");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Số lượng: ô số có mặt, Bằng 3 của nhóm Trả lời ngắn hiện 3/15 câu", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn · 15 câu$/);
    expect(within(dialog).getByTestId("mapid-rule-scope")).toHaveTextContent("15/50 câu");
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickTopic(u);
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    const count = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(count);
    await u.type(count, "3");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules).toEqual([expect.objectContaining({ type: "COUNT", groupId: "b-short", comparator: "EXACT", count: 3 })]);
    expect(table()).toHaveTextContent("Trả lời ngắn · Đúng 3/15 câu thuộc Mệnh đề và tập hợp");
  });

  it("Số lượng theo tỷ lệ: 20% của nhóm 15 câu là 3 câu, lưu cả percent và count", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn · 15 câu$/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickTopic(u);
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    const percent = within(dialog).getByTestId("mapid-rule-percent");
    await u.clear(percent);
    await u.type(percent, "20");
    expect(within(dialog).getByTestId("mapid-rule-percent-result")).toHaveTextContent("= 3 câu trên mẫu số 15");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ type: "COUNT", groupId: "b-short", comparator: "AT_LEAST", count: 3, percent: 20 });
  });

  it("Chỉ xuất hiện ở nhóm này: ẩn khi chọn Toàn môn; chỉ hiện khi chọn nhóm; ô số chỉ có khi bật Kèm số lượng", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    expect(within(dialog).queryByTestId("mapid-rule-type-ONLY_IN_GROUP")).not.toBeInTheDocument();
    await selectScope(u, /^Trả lời ngắn · 15 câu$/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    await pickTopic(u);
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    await u.click(within(dialog).getByRole("checkbox", { name: "Kèm số lượng" }));
    const count = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(count);
    await u.type(count, "3");
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ type: "ONLY_IN_GROUP", groupId: "b-short", comparator: "EXACT", count: 3 });
    expect(table()).toHaveTextContent("Mệnh đề và tập hợp chỉ xuất hiện ở nhóm Trả lời ngắn · Đúng 3/15 câu");
  });

  it("Chỉ lấy nguồn từ các MAPID: không có ô số; chùm cuối hiện 1 chùm × 3 câu con và 3/50 câu", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    await selectScope(u, /^Chùm cuối · 1 chùm × 3 câu con$/);
    expect(within(dialog).getByTestId("mapid-rule-scope")).toHaveTextContent("3/50 câu");
    await u.click(within(dialog).getByTestId("mapid-rule-type-SOURCE"));
    await pickTopic(u);
    expect(within(dialog).queryByTestId("mapid-rule-count")).not.toBeInTheDocument();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules).toEqual([expect.objectContaining({ type: "SOURCE", groupId: "b-set", nodeIds: [TOPIC.id] })]);
    expect(table()).toHaveTextContent("Chùm cuối · Chỉ lấy từ Mệnh đề và tập hợp");
  });

  it("hai quy tắc dễ nhầm có câu tóm tắt phân biệt rõ ràng khi chọn", async () => {
    const u = user();
    renderPanel(makeSection({ mapidMode: "MATRIX" }));
    const dialog = await openAdd(u);
    await selectScope(u, /^Trả lời ngắn · 15 câu$/);
    await pickTopic(u);
    await u.click(within(dialog).getByTestId("mapid-rule-type-ONLY_IN_GROUP"));
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Mệnh đề và tập hợp chỉ được xuất hiện trong nhóm Trả lời ngắn");
    await u.click(within(dialog).getByTestId("mapid-rule-type-SOURCE"));
    expect(within(dialog).getByTestId("mapid-rule-summary")).toHaveTextContent("Nhóm Trả lời ngắn chỉ lấy câu thuộc Mệnh đề và tập hợp");
  });
});

describe("Mẫu số của nhóm lấy phần còn lại (R1-5)", () => {
  const remainder = (over: Partial<ExamSection> = {}) =>
    makeSection({
      mapidMode: "MATRIX",
      questionCount: 10,
      blocks: [block({ id: "b-fixed", name: "Cố định", questionType: "SINGLE_CHOICE", questionCount: 4 }), block({ id: "b-rem", name: "Phần còn lại", questionType: "SINGLE_CHOICE", questionCount: null })],
      ...over,
    });

  it("tỷ lệ 50% của nhóm còn lại: hộp thoại nêu mẫu số 6 và 3 câu, lưu cả percent và count, câu mô tả 3/6 (50%)", async () => {
    const u = user();
    const latest = renderPanel(remainder());
    const dialog = await openAdd(u);
    await selectScope(u, /^Phần còn lại · lấy phần còn lại \(6 câu\)$/);
    expect(within(dialog).getByTestId("mapid-rule-scope")).toHaveTextContent("6/10 câu");
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickTopic(u);
    await u.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    const percent = within(dialog).getByTestId("mapid-rule-percent");
    await u.clear(percent);
    await u.type(percent, "50");
    expect(dialog).toHaveTextContent("Mẫu số: 6 câu của Phần còn lại");
    expect(within(dialog).getByTestId("mapid-rule-percent-result")).toHaveTextContent("= 3 câu trên mẫu số 6");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ type: "COUNT", groupId: "b-rem", comparator: "EXACT", count: 3, percent: 50 });
    expect(table()).toHaveTextContent("Phần còn lại · Đúng 3/6 (50%) câu thuộc Mệnh đề và tập hợp");
  });

  it("môn chưa có số câu: tỷ lệ báo chưa biết mẫu số và khóa nút Lưu; nhập theo số câu thì lưu được", async () => {
    const u = user();
    const latest = renderPanel(remainder({ questionCount: null }));
    const dialog = await openAdd(u);
    await selectScope(u, /^Phần còn lại · lấy phần còn lại$/);
    await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
    await pickTopic(u);
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Chưa biết mẫu số");
    await u.click(within(dialog).getByRole("radio", { name: "Số câu" }));
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ type: "COUNT", groupId: "b-rem", count: 1 });
  });
});

describe("Bảng quy tắc", () => {
  const rule = (index: number): MapidRule => ({ id: `r${index}`, type: "PRESENT", groupId: null, nodeIds: [TOPIC.id] });

  it("sửa mở lại hộp thoại với giá trị cũ, xóa bỏ dòng", async () => {
    const u = user();
    const latest = renderPanel(makeSection({ mapidMode: "MATRIX", mapidRules: [{ id: "r1", type: "COUNT", groupId: "b-short", nodeIds: [TOPIC.id], comparator: "EXACT", count: 3 }, rule(2)] }));
    await u.click(screen.getByRole("button", { name: /^Sửa quy tắc: Trả lời ngắn/ }));
    const dialog = ruleDialog("Sửa quy tắc");
    expect(within(dialog).getByTestId("mapid-rule-count")).toHaveValue("3");
    await u.clear(within(dialog).getByTestId("mapid-rule-count"));
    await u.type(within(dialog).getByTestId("mapid-rule-count"), "4");
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ id: "r1", count: 4 });
    expect(latest.section.mapidRules).toHaveLength(2);
    await u.click(screen.getByRole("button", { name: /^Xóa quy tắc: Toàn môn · Phải có/ }));
    expect(latest.section.mapidRules!.map((item) => item.id)).toEqual(["r1"]);
  });

  it("tối đa 20 quy tắc: bộ đếm, nút thêm khóa khi đủ", () => {
    renderPanel(makeSection({ mapidMode: "MATRIX", mapidRules: Array.from({ length: 19 }, (_, index) => rule(index)) }));
    expect(screen.getByTestId("mapid-rules-counter")).toHaveTextContent("19/20 quy tắc");
    expect(screen.getByTestId("mapid-rule-add")).toBeEnabled();
  });

  it("đủ 20 quy tắc: nút thêm bị khóa", () => {
    renderPanel(makeSection({ mapidMode: "MATRIX", mapidRules: Array.from({ length: 20 }, (_, index) => rule(index)) }));
    expect(screen.getByTestId("mapid-rules-counter")).toHaveTextContent("20/20 quy tắc");
    expect(screen.getByTestId("mapid-rule-add")).toBeDisabled();
    expect(screen.getByText("Đã đủ 20 quy tắc.")).toBeInTheDocument();
  });

  it("mỗi dòng có data-testid và mỏ neo mapid-rule-<id>; focusMapidRule đưa focus tới dòng", () => {
    renderPanel(makeSection({ mapidMode: "MATRIX", mapidRules: [rule(1)] }));
    const row = screen.getByTestId("mapid-rule-row-r1");
    expect(row).toHaveAttribute("id", "mapid-rule-r1");
    expect(row).toHaveAttribute("data-field", "mapid-rule-r1");
    row.scrollIntoView = vi.fn();
    expect(focusMapidRule("r1")).toBe(true);
    expect(row).toHaveFocus();
    expect(focusMapidRule("missing")).toBe(false);
  });

  it("quy tắc của nhóm đã xóa hiện Nhóm đã xóa và chặn lưu; chọn lại nhóm thì hết", async () => {
    const u = user();
    const bad: MapidRule = { id: "r9", type: "PRESENT", groupId: "gone", nodeIds: [TOPIC.id] };
    const section = makeSection({ mapidMode: "MATRIX", mapidRules: [bad] });
    const latest = renderPanel(section);
    expect(screen.getByTestId("mapid-rule-row-r9")).toHaveTextContent("Nhóm đã xóa");
    expect(saveBlockers([makePart(section)], { grades: [], difficulties: [] }).map((item) => item.kind)).toContain("MAPID_GROUP");
    await u.click(screen.getByRole("button", { name: /^Sửa quy tắc/ }));
    const dialog = ruleDialog("Sửa quy tắc");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    await selectScope(u, /^Toàn môn/);
    await u.click(within(dialog).getByTestId("mapid-rule-save"));
    expect(latest.section.mapidRules![0]).toMatchObject({ id: "r9", groupId: null });
    expect(saveBlockers([makePart(latest.section)], { grades: [], difficulties: [] })).toEqual([]);
  });

  it("Toàn bài còn quy tắc của nhóm đã xóa: ghi chú báo phải chuyển lại để sửa", () => {
    renderPanel(makeSection({ mapidMode: "ALL", mapidRules: [{ id: "r9", type: "PRESENT", groupId: "gone", nodeIds: [TOPIC.id] }] }));
    expect(screen.getByTestId("mapid-paused-note")).toHaveTextContent("1 quy tắc trỏ tới nhóm đã xóa");
  });
});

describe("Cột Phạm vi nguồn của bảng nhóm", () => {
  it("Theo ma trận: nhóm liệt kê quy tắc của nó, SOURCE cả môn và loại trừ do nhóm khác; không có thì Không giới hạn MAPID", () => {
    renderPanel(
      makeSection({
        mapidMode: "MATRIX",
        mapidRules: [
          { id: "own", type: "COUNT", groupId: "b-short", nodeIds: [TOPIC.id], comparator: "EXACT", count: 3 },
          { id: "src", type: "SOURCE", groupId: null, nodeIds: [TOPIC.id] },
          { id: "only", type: "ONLY_IN_GROUP", groupId: "b-set", nodeIds: [TOPIC.id] },
        ],
      }),
    );
    const short = screen.getByTestId("mapid-group-scope-b-short");
    expect(short).toHaveTextContent("Đúng 3/15 câu thuộc Mệnh đề và tập hợp");
    expect(short).toHaveTextContent("Chỉ lấy từ Mệnh đề và tập hợp");
    expect(short).toHaveTextContent("Không lấy Mệnh đề và tập hợp");
    expect(screen.getByTestId("mapid-group-scope-b-set")).toHaveTextContent("Chỉ nhóm này có Mệnh đề và tập hợp");
  });

  it("Theo ma trận mà chưa có quy tắc: Không giới hạn MAPID cho mọi nhóm", () => {
    renderPanel(makeSection({ mapidMode: "MATRIX" }));
    expect(screen.getAllByText("Không giới hạn MAPID").length).toBeGreaterThanOrEqual(3);
  });
});

describe("Môn và kỳ thi khác giữ nguyên", () => {
  it("kỳ thi không phải HSA: vẫn phạm vi Toàn môn / Theo chủ đề và Chỉ tiêu chủ đề", () => {
    renderPanel(makeSection(), { hsa: false, kind: "TSA" });
    expect(screen.getByRole("radio", { name: "Theo chủ đề" })).toBeInTheDocument();
    expect(screen.getByText("Chỉ tiêu chủ đề")).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Theo ma trận MAPID" })).not.toBeInTheDocument();
  });

  it.each(["VAN", "ANH"])("HSA %s: không có chế độ MAPID", (subjectCode) => {
    renderPanel(makeSection({ subjectCode }));
    expect(screen.getByRole("radio", { name: "Theo chủ đề" })).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Theo ma trận MAPID" })).not.toBeInTheDocument();
  });

  it("HSA Toán còn chỉ tiêu có lọc khối lớp (không chuyển được): giữ kiểu cũ", () => {
    renderPanel(makeSection({ topicQuotas: [{ id: "q", name: "Hàm số", nodeIds: ["kn-0D1"], count: 3, enforcement: "REQUIRED", gradeCodes: ["GRADE_11"] }] }));
    expect(screen.getByText("Chỉ tiêu chủ đề")).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Theo ma trận MAPID" })).not.toBeInTheDocument();
  });
});

it("marks stored group names within rule sentences", () => {
  renderPanel(makeSection({ mapidMode: "MATRIX", mapidRules: [
    { id: "marked", type: "PRESENT", groupId: "b-short", nodeIds: [TOPIC.id] },
  ] }));
  const row = screen.getByTestId("mapid-rule-row-marked");
  expect(row.querySelector("[data-exam-content]")).toHaveTextContent("Trả lời ngắn");
});
