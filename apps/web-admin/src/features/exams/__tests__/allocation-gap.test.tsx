import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import reference from "@/test/fixtures/content-reference.json";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { ExamStructureSummaryRegion } from "../components/exam-structure-summary";
import { ExamStructureView } from "../components/exam-structure-view";
import type { BlockMatrix, ExamPart, ExamSection, ExamStructureSummary, QuestionBlock } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

/**
 * UNI-2756 (UNI-1702): mọi chỗ hiện số câu phân bổ lệch chỉ tiêu: vượt là chữ đỏ kèm chữ "Vượt N câu", thiếu là chữ cảnh báo
 * kèm "Thiếu N câu" (không chỉ nhờ màu), đúng thì trung tính.
 */

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSubjectAvailableQuestions: () => ({ isError: false, data: { total: 120, byAnswerType: [] } }) as never };
});

const GRADES = reference.grades.map((grade) => grade.code);
const DIFFICULTIES = reference.difficulties.map((item) => item.code);
const STRUCTURE = `${EXAM_PATH}/structure`;

const separate = (grades: number[], difficulties: number[]): BlockMatrix => ({
  mode: "SEPARATE",
  unit: "COUNT",
  cells: [],
  byGrade: GRADES.map((code, index) => ({ code, value: grades[index] ?? 0 })),
  byDifficulty: DIFFICULTIES.map((code, index) => ({ code, value: difficulties[index] ?? 0 })),
});

const block = (over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b1",
  name: "Nhóm 1",
  questionType: "SINGLE_CHOICE",
  questionCount: 10,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

function renderSubject(section: ExamSection) {
  const part = { id: "p1", sections: [section], options: [] } as unknown as ExamPart;
  return renderWithProviders(
    <ExamStructureSubjectPanel
      context={{ part, option: null, section, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
      subjectName="Toán học"
      axes={{ grades: GRADES, difficulties: DIFFICULTIES }}
      labels={{ grade: (code) => code, difficulty: (code) => code }}
      reference={reference as unknown as ContentReference}
      onChange={() => {}}
      onSelectBlock={() => {}}
      onAddBlock={() => {}}
    />,
  );
}

const sectionOf = (over: Partial<ExamSection>): ExamSection => ({
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 10,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block()],
  ...over,
});

const OVER = (count: number) => `Vượt ${count} câu`;
const UNDER = (count: number) => `Thiếu ${count} câu`;

/** Chữ vượt là chữ đỏ, chữ thiếu là chữ cảnh báo; chữ hiện trong đúng ô. */
function expectOver(scope: HTMLElement, count: number) {
  expect(within(scope).getByText(OVER(count))).toHaveClass("text-danger");
}
function expectUnder(scope: HTMLElement, count: number) {
  expect(within(scope).getByText(UNDER(count))).toHaveClass("text-warning");
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Chỉ tiêu dạng trả lời của môn", () => {
  it("cột Còn thiếu: dạng nhóm lấy vượt chỉ tiêu hiện 'Vượt N câu' đỏ; thiếu hiện 'Thiếu N câu'; đủ hiện 0", () => {
    // Một đáp án: chỉ tiêu 6, nhóm lấy 10 (vượt 4). Đúng/Sai: chỉ tiêu 3, chưa nhóm nào (thiếu 3). Trả lời ngắn: chỉ tiêu 0 (đủ).
    renderSubject(
      sectionOf({
        answerQuotas: { unit: "COUNT", items: [{ answerType: "SINGLE_CHOICE", value: 6 }, { answerType: "TRUE_FALSE", value: 3 }, { answerType: "SHORT_ANSWER", value: 0 }] },
      }),
    );
    const table = screen.getByRole("region", { name: "Dạng trả lời" });
    const rowOf = (name: string) => within(table).getByRole("rowheader", { name }).closest("tr") as HTMLElement;
    expectOver(rowOf("Một đáp án"), 4);
    expectUnder(rowOf("Đúng/Sai"), 3);
    expect(within(rowOf("Trả lời ngắn")).queryByText(/Vượt|Thiếu/)).not.toBeInTheDocument();
  });

  it("dòng tổng: tổng chỉ tiêu lớn hơn số câu của môn hiện 'Vượt', nhỏ hơn hiện 'Thiếu'", () => {
    const view = renderSubject(sectionOf({ answerQuotas: { unit: "COUNT", items: [{ answerType: "SINGLE_CHOICE", value: 8 }, { answerType: "TRUE_FALSE", value: 7 }] } }));
    const total = () => screen.getByRole("region", { name: "Dạng trả lời" }).querySelector("tfoot") as HTMLElement;
    expectOver(total(), 5);
    view.unmount();
    renderSubject(sectionOf({ answerQuotas: { unit: "COUNT", items: [{ answerType: "SINGLE_CHOICE", value: 4 }, { answerType: "TRUE_FALSE", value: 1 }] } }));
    expectUnder(total(), 5);
  });
});

describe("Đầu màn môn: chip chênh lệch", () => {
  const chip = (text: string) => within(screen.getByRole("status")).getByText(text);

  it("vượt chỉ tiêu: chip 'Vượt N câu' tông danger; thiếu: tông warning; đủ: tông success", () => {
    // Môn 10 câu, nhóm lấy 15 (vượt 5); nhóm lấy 7 (thiếu 3); nhóm lấy 10 (đủ).
    const view = renderSubject(sectionOf({ blocks: [block({ questionCount: 15 })] }));
    expect(chip(OVER(5))).toHaveAttribute("data-tone", "danger");
    view.unmount();
    const under = renderSubject(sectionOf({ blocks: [block({ questionCount: 7 })] }));
    expect(chip(UNDER(3))).toHaveAttribute("data-tone", "warning");
    under.unmount();
    renderSubject(sectionOf({ blocks: [block({ questionCount: 10 })] }));
    expect(within(screen.getByRole("status")).getByText("Khớp số câu của môn")).toHaveAttribute("data-tone", "success");
  });
});

describe("Chỉ tiêu khối lớp và độ khó của môn", () => {
  it("cột Còn thiếu của hai bảng chi tiết: nhóm lấy vượt chỉ tiêu hiện 'Vượt N câu', thiếu hiện 'Thiếu N câu'", () => {
    // Chỉ tiêu môn 4/3/3; nhóm khai riêng 5/5/0: lớp 10 vượt 1, lớp 11 vượt 2, lớp 12 thiếu 3.
    renderSubject(sectionOf({ matrix: separate([4, 3, 3], [4, 3, 3]), blocks: [block({ matrix: separate([5, 5, 0], [5, 5, 0]) })] }));
    const gradeTable = screen.getAllByRole("columnheader", { name: "Đã phân bổ ở nhóm" })[0]!.closest("table") as HTMLElement;
    expectOver(within(gradeTable).getByRole("rowheader", { name: "GRADE_10" }).closest("tr") as HTMLElement, 1);
    expectOver(within(gradeTable).getByRole("rowheader", { name: "GRADE_11" }).closest("tr") as HTMLElement, 2);
    expectUnder(within(gradeTable).getByRole("rowheader", { name: "GRADE_12" }).closest("tr") as HTMLElement, 3);
  });
});

describe("Phần: bảng Môn trong phần", () => {
  function open(partCount: number) {
    const section = (id: string, subjectCode: string, questionCount: number, blockCount: number) => ({
      id,
      subjectCode,
      questionCount,
      blocks: [{ id: `blk-${id}`, name: id, questionType: "SINGLE_CHOICE", questionCount: blockCount, requiredPointsPerQuestion: 1 }],
    });
    // Môn Toán 6 câu mà nhóm lấy 8 (vượt 2); môn Vật lí 4 câu mà nhóm lấy 3 (thiếu 1).
    const part = { ...FIXED_PART, questionCount: partCount, sections: [section("sec-1", "MATH", 6, 8), section("sec-2", "PHYSICS", 4, 3)] };
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: partCount }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
      .install();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?part=part-1`);
    return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
  }

  it("cột Đã phân bổ vào nhóm: môn vượt hiện 'Vượt 2 câu' đỏ, môn thiếu hiện 'Thiếu 1 câu'", async () => {
    open(10);
    const table = (await screen.findByRole("heading", { name: "Môn trong phần" })).closest("section") as HTMLElement;
    const rows = within(table).getAllByRole("button", { name: /^Soạn môn/ }).map((button) => button.closest("tr") as HTMLElement);
    expectOver(rows[0]!, 2);
    expectUnder(rows[1]!, 1);
  });

  it("dòng tổng của phần: tổng số câu các môn khác số câu của phần hiện 'Thiếu N câu'", async () => {
    open(12);
    const table = (await screen.findByRole("heading", { name: "Môn trong phần" })).closest("section") as HTMLElement;
    expectUnder(table.querySelector("tfoot") as HTMLElement, 2);
  });
});

describe("Tổng hợp cấu trúc: cột Chênh lệch", () => {
  const summary: ExamStructureSummary = {
    rows: [
      { name: "Phần 1", partId: "p1", sectionId: null, blockId: null, target: 10, allocated: 13, maxPoints: null },
      { name: "TOAN", partId: "p1", sectionId: "s1", blockId: null, target: 10, allocated: 7, maxPoints: null },
      { name: "Nhóm", partId: "p1", sectionId: "s1", blockId: "b1", target: 5, allocated: 5, maxPoints: null },
    ],
  } as unknown as ExamStructureSummary;

  it("dòng vượt hiện 'Vượt 3 câu' đỏ, dòng thiếu hiện 'Thiếu 3 câu', dòng đủ không có chữ lệch", () => {
    renderWithProviders(
      <ExamStructureSummaryRegion
        summary={summary}
        load="ready"
        dirty={false}
        local={{ allocated: 0, total: 0, missing: 0 }}
        issues={[]}
        issueText={() => ""}
        checkResult={null}
        names={{ subject: (code) => code }}
        onSelect={() => {}}
      />,
    );
    const table = screen.getByRole("table");
    const rowOf = (name: string) => within(table).getByRole("button", { name: `Đến ${name}` }).closest("tr") as HTMLElement;
    expectOver(rowOf("Phần 1"), 3);
    expectUnder(rowOf("TOAN"), 3);
    expect(within(rowOf("Nhóm")).queryByText(/Vượt|Thiếu/)).not.toBeInTheDocument();
  });
});

describe("Chỉ tiêu cấp bài (tổng quan)", () => {
  function open(counts: [number, number]) {
    const quota = {
      id: "q-grade",
      name: "Lớp",
      dimension: "GRADE",
      basis: "QUESTION",
      enforcement: "REFERENCE",
      subjectCodes: ["MATH"],
      buckets: [
        { codes: ["GRADE_10"], count: counts[0] },
        { codes: ["GRADE_12"], count: counts[1] },
      ],
    };
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, templateCode: "T_NOW" }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [FIXED_PART], examQuotas: [quota] } })
      .install();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?exam=1`);
    return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
  }

  it("tổng chỉ tiêu theo số câu lớn hơn số câu trong phạm vi: 'Vượt 2 câu' đỏ; nhỏ hơn: 'Thiếu 2 câu'", async () => {
    const view = open([7, 5]);
    expectOver((await screen.findByRole("region", { name: "Lớp" })) as HTMLElement, 2);
    view.unmount();
    open([4, 4]);
    expectUnder((await screen.findByRole("region", { name: "Lớp" })) as HTMLElement, 2);
  });
});
