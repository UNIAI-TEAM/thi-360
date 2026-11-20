import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import referenceFixture from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { ExamStructureView } from "../components/exam-structure-view";
import { useMapidRuleText } from "../components/mapid-rules-table";
import { HsaExamContext } from "../hsa-enforcement";
import type { ExamPart, ExamSection, MapidRule, QuestionBlock } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

// Danh mục mẫu đặt Toán là `MATH`, còn cấu trúc HSA dùng mã môn thật `TOAN`: đổi mã trong danh mục để hộp chọn MAPID lọc đúng môn.
const { asToan } = vi.hoisted(() => ({ asToan: (ref: unknown) => JSON.parse(JSON.stringify(ref).replaceAll('"MATH"', '"TOAN"')) as ContentReference }));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: Parameters<typeof getContentReferenceFixture>[0]) => asToan(await getContentReferenceFixture(locale));
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

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
    blocks: [block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }), block()],
    mapidMode: "MATRIX",
    ...over,
  };
}

const makePart = (section: ExamSection): ExamPart => ({ id: "p1", sections: [section], options: [] }) as unknown as ExamPart;

function renderPanel(initial: ExamSection, locale: "vi" | "en" = "vi") {
  const latest = { section: initial };
  function Harness() {
    const [section, setSection] = useState(initial);
    return (
      <HsaExamContext.Provider value>
        <ExamStructureSubjectPanel
          examId="exam-1"
          examKind="HSA"
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
  renderWithProviders(<Harness />, locale === "en" ? { locale: "en", messages: en } : {});
  return latest;
}

const user = () => userEvent.setup();
const dialogOf = () => screen.getByTestId("mapid-rule-save").closest('[role="dialog"]') as HTMLElement;

async function pickTopic(u: UserEvent) {
  await u.click(within(dialogOf()).getByTestId("mapid-rule-mapid-picker"));
  const picker = await screen.findByRole("dialog", { name: "Chọn kiến thức (MAPID)" });
  await u.type(within(picker).getByRole("searchbox", { name: "Tìm kiến thức" }), "0D1");
  await u.click(await within(picker).findByRole("checkbox", { name: /0D1 Mệnh đề và tập hợp/ }));
  await u.click(within(picker).getByRole("button", { name: "Áp dụng (1)" }));
}

async function openCountDialog(u: UserEvent) {
  await u.click(screen.getByTestId("mapid-rule-add"));
  const dialog = dialogOf();
  await u.click(within(dialog).getByTestId("mapid-rule-type-COUNT"));
  await pickTopic(u);
  return dialog;
}

describe("R1-F2: nhóm đã bỏ không là đích của quy tắc", () => {
  it("hộp thoại không liệt kê nhóm đã bỏ; mẫu số nhóm còn lại không trừ nhóm đó", async () => {
    const u = user();
    renderPanel(
      makeSection({
        questionCount: 10,
        blocks: [block({ id: "b-fixed", name: "Cố định bỏ", questionType: "SINGLE_CHOICE", questionCount: 4, omitted: true }), block({ id: "b-rest", name: "Phần còn lại", questionType: "SINGLE_CHOICE", questionCount: null })],
      }),
    );
    await u.click(screen.getByTestId("mapid-rule-add"));
    const dialog = dialogOf();
    await u.click(within(within(dialog).getByTestId("mapid-rule-scope")).getByRole("combobox"));
    expect(screen.queryByRole("option", { name: /^Cố định bỏ/ })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /^Phần còn lại · lấy phần còn lại \(10 câu\)$/ })).toBeInTheDocument();
  });
});

describe("R1-F5: số câu vượt số câu của môn", () => {
  it("báo lỗi COUNT_ABOVE_TOTAL, khóa Lưu; nhập số hợp lệ thì mở lại", async () => {
    const u = user();
    renderPanel(makeSection());
    const dialog = await openCountDialog(u);
    const count = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(count);
    await u.type(count, "51");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Số câu vượt số câu của môn (50)");
    await u.clear(count);
    await u.type(count, "50");
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeEnabled();
  });
});

describe("R1-F7: ô số nhập sai không được lưu ngầm", () => {
  it("gõ chữ vào ô số: Lưu bị khóa, lỗi màu danger nối với nút Lưu bằng aria-describedby; sửa lại thì hết", async () => {
    const u = user();
    renderPanel(makeSection());
    const dialog = await openCountDialog(u);
    const count = within(dialog).getByTestId("mapid-rule-count");
    await u.clear(count);
    await u.type(count, "2x");
    const save = within(dialog).getByTestId("mapid-rule-save");
    expect(save).toBeDisabled();
    const alert = within(dialog).getByRole("alert");
    expect(alert).toHaveTextContent("Số nhập chưa đúng");
    expect(alert).toHaveClass("text-danger");
    expect(save.getAttribute("aria-describedby")).toBe(alert.id);
    await u.clear(count);
    await u.type(count, "2");
    expect(save).toBeEnabled();
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
    expect(save).not.toHaveAttribute("aria-describedby");
  });

  it("lỗi của biểu mẫu (chưa chọn MAPID) cũng màu danger và gắn với Lưu", async () => {
    const u = user();
    renderPanel(makeSection());
    await u.click(screen.getByTestId("mapid-rule-add"));
    const dialog = dialogOf();
    const alert = within(dialog).getByRole("alert");
    expect(alert).toHaveClass("text-danger");
    expect(within(dialog).getByTestId("mapid-rule-save").getAttribute("aria-describedby")).toBe(alert.id);
  });
});

describe("R1-F8: tiếng Anh có số ít, số nhiều", () => {
  it("ghi chú tạm ngừng: 1 rule, 2 rules; trỏ tới nhóm đã xóa: 1 rule points, 2 rules point", () => {
    const paused = (count: number): MapidRule[] => Array.from({ length: count }, (_, index) => ({ id: `r${index}`, type: "PRESENT", groupId: null, nodeIds: [TOPIC.id] }));
    const one = renderPanel(makeSection({ mapidMode: "ALL", mapidRules: paused(1) }), "en");
    expect(screen.getByTestId("mapid-paused-note")).toHaveTextContent("1 rule paused");
    expect(screen.getByTestId("mapid-paused-note")).not.toHaveTextContent("1 rules");
    expect(one.section.mapidRules).toHaveLength(1);
  });

  it("hai quy tắc trỏ tới nhóm đã xóa; một quy tắc", () => {
    const gone = (id: string): MapidRule => ({ id, type: "PRESENT", groupId: "gone", nodeIds: [TOPIC.id] });
    renderPanel(makeSection({ mapidMode: "ALL", mapidRules: [gone("a")] }), "en");
    expect(screen.getByTestId("mapid-paused-note")).toHaveTextContent("1 rule points to a deleted group");
  });

  it("quy mô nhóm trong hộp thoại: 1 set × 1 sub-question, 2 sets × 3 sub-questions", async () => {
    const u = user();
    renderPanel(
      makeSection({
        blocks: [
          block({ id: "b-one", name: "One", questionType: "QUESTION_SET", questionCount: 1, setCount: 1, setSize: 1 }),
          block({ id: "b-two", name: "Two", questionType: "QUESTION_SET", questionCount: 6, setCount: 2, setSize: 3 }),
        ],
      }),
      "en",
    );
    await u.click(screen.getByTestId("mapid-rule-add"));
    const dialog = dialogOf();
    await u.click(within(within(dialog).getByTestId("mapid-rule-scope")).getByRole("combobox"));
    expect(screen.getByRole("option", { name: /^One · 1 set × 1 sub-question$/ })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /^Two · 2 sets × 3 sub-questions$/ })).toBeInTheDocument();
  });
});

describe("R1-F9: chưa có danh mục thì không hiện mã nút MAPID thô", () => {
  const rule: MapidRule = { id: "r1", type: "PRESENT", groupId: null, nodeIds: ["0f3c9a2e-5b1d-4c77-8e21-aaaaaaaaaaaa"] };

  function Probe({ loaded }: { loaded: boolean }) {
    const text = useMapidRuleText(loaded ? reference : undefined);
    return <p data-testid="probe">{text.sentence(makeSection({ mapidRules: [rule] }), rule)}</p>;
  }

  it("danh mục chưa tải: câu quy tắc dùng chữ giữ chỗ, không có UUID", () => {
    renderWithProviders(<Probe loaded={false} />);
    const text = screen.getByTestId("probe").textContent ?? "";
    expect(text).not.toMatch(/0f3c/);
    expect(text).toBe("Toàn môn · Phải có (đang tải MAPID)");
  });
});

describe("R1-F10: môn tự chọn không nhập được tỷ lệ", () => {
  it("môn tự chọn chưa có số câu: tỷ lệ báo chuyển sang nhập số câu, không bảo nhập số câu của môn", async () => {
    const u = user();
    renderPanel(makeSection({ questionCount: null, choiceGroup: "NATURAL", blocks: [] }));
    const dialog = await openCountDialog(u);
    await u.click(within(dialog).getByRole("radio", { name: "Tỷ lệ" }));
    expect(within(dialog).getByTestId("mapid-rule-save")).toBeDisabled();
    const alert = within(dialog).getByRole("alert");
    expect(alert).toHaveTextContent("Môn tự chọn chưa dùng được tỷ lệ");
    expect(alert).not.toHaveTextContent("Nhập số câu của môn");
  });
});

describe("R1-F4: nhóm đã xóa được gọi tên", () => {
  it("dòng quy tắc ghi «Nhóm «Trả lời ngắn» đã xóa» và câu mang tên nhóm", () => {
    renderPanel(
      makeSection({
        mapidRules: [{ id: "r9", type: "COUNT", groupId: "gone", nodeIds: [TOPIC.id], comparator: "EXACT", count: 3, deletedGroupName: "Trả lời ngắn" }],
      }),
    );
    const row = screen.getByTestId("mapid-rule-row-r9");
    expect(within(row).getByText("Nhóm «Trả lời ngắn» đã xóa")).toBeInTheDocument();
    expect(row).toHaveTextContent("Trả lời ngắn (đã xóa) · Đúng 3 câu thuộc Mệnh đề và tập hợp");
  });
});

describe("R1-F4: xóa nhóm có quy tắc rồi Lưu", () => {
  const STRUCTURE = `${EXAM_PATH}/structure`;

  it("thông báo chặn lưu nêu câu của quy tắc và tên nhóm đã xóa, không nói về bảng phân bổ", async () => {
    const base = FIXED_PART.sections![0] as object;
    const rules = [{ id: "r1", type: "COUNT", groupId: "blk-2", nodeIds: [TOPIC.id], comparator: "EXACT", count: 3 }];
    const section = {
      ...base,
      subjectCode: "TOAN",
      questionCount: 10,
      mapidMode: "MATRIX",
      mapidRules: rules,
      blocks: [
        { id: "blk-1", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 5, requiredPointsPerQuestion: 1 },
        { id: "blk-2", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 5, requiredPointsPerQuestion: 1 },
      ],
    };
    const server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "HSA", questionCount: 10 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 10, sections: [section] }], examQuotas: [] } })
      .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
      .install();
    const u = user();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?block=blk-2`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
    await u.click(await screen.findByRole("button", { name: "Xóa nhóm Trả lời ngắn" }));
    const confirm = await screen.findByRole("dialog", { name: /Xóa nhóm Trả lời ngắn\?/ });
    await u.click(within(confirm).getByRole("button", { name: "Xóa nhóm" }));
    await u.click(screen.getByRole("button", { name: "Lưu" }));
    const messages = await screen.findAllByText(/Chưa lưu được: quy tắc/);
    expect(messages[0]).toHaveTextContent("Trả lời ngắn (đã xóa) · Đúng 3 câu thuộc Mệnh đề và tập hợp");
    expect(messages[0]).toHaveTextContent("nhóm «Trả lời ngắn»");
    expect(screen.queryByText(/bảng phân bổ còn lỗi/)).not.toBeInTheDocument();
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(0));
  });
});

it("uses whole exam terminology and pluralizes group set counts in English", () => {
  renderPanel(makeSection({ blocks: [
    block({ id: "one", name: "One", questionType: "QUESTION_SET", questionCount: 1, setCount: 1, setSize: 1 }),
    block({ id: "two", name: "Two", questionType: "QUESTION_SET", questionCount: 6, setCount: 2, setSize: 3 }),
  ] }), "en");
  expect(screen.getByRole("radio", { name: "Whole exam (no MAPID limit)" })).toBeInTheDocument();
  expect(screen.getByText("1 set × 1 sub-question = 1 question")).toBeInTheDocument();
  expect(screen.getByText("2 sets × 3 sub-questions = 6 questions")).toBeInTheDocument();
});
