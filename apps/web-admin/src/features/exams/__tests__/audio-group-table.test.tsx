import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockEditor } from "../components/block-editor";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import type { AudioRequirement, ExamPart, ExamSection, QuestionBlock } from "../types";
import { byTextContent } from "./text-content";

/**
 * FE-7 vòng 4, F1: bảng nhóm của môn thuộc phần có audio theo bản vẽ `NhomAudio2B`: Nhãn audio, Số lượng, Tổng câu, Tỷ lệ
 * trong phần, Nguồn khả dụng, dòng "Tổng phần …". Số ở bảng là số của vùng sửa nhóm; môn cũ giữ bảng cũ.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const counts = vi.hoisted(() => ({ queries: [] as { questionType: string | null; audioRequirement?: string }[] }));
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useSubjectAvailableQuestions: () => ({ isError: false, data: undefined }) as never,
    useAvailableQuestions: (query: { questionType: string | null; audioRequirement?: string } | null) => {
      if (query) counts.queries.push(query);
      return { isError: false, data: { total: query?.questionType === "QUESTION_SET" ? 17 : 42, cells: [], byAnswerType: [] } } as never;
    },
  };
});

beforeEach(() => {
  counts.queries.length = 0;
});

const block = (patch: Partial<QuestionBlock>): QuestionBlock => ({
  id: "b",
  name: "Dòng",
  questionType: "SINGLE_CHOICE",
  questionCount: 8,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...patch,
});

const blocks = [
  block({ id: "b1", name: "Dòng 1 · Câu đơn", questionCount: 8 }),
  block({ id: "b2", name: "Dòng 2 · Hội thoại 4 câu", questionType: "QUESTION_SET", questionCount: 12, setCount: 3, setSize: 4 }),
  block({ id: "b3", name: "Dòng 3 · Bài nghe 5 câu", questionType: "QUESTION_SET", questionCount: 15, setCount: 3, setSize: 5 }),
];

const section = (audio?: AudioRequirement): ExamSection => ({
  id: "s1",
  subjectCode: "ANH",
  questionCount: 35,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks,
  ...(audio ? { audioRequirement: audio } : {}),
});

const axes = { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
const typedReference = reference as unknown as ContentReference;

function renderPanel(current: ExamSection, options: { english?: boolean; partTotal?: number } = {}) {
  const part = { id: "p1", name: "Nghe", questionCount: options.partTotal ?? 35, sections: [current], options: [] } as unknown as ExamPart;
  renderWithProviders(
    <ExamStructureSubjectPanel
      examId="exam-1"
      context={{ part, option: null, section: current, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
      subjectName="Tiếng Anh"
      axes={axes}
      labels={labels}
      reference={typedReference}
      onChange={() => {}}
      onSelectBlock={() => {}}
      onAddBlock={() => {}}
    />,
    options.english ? { locale: "en", messages: en } : undefined,
  );
}

const table = () => screen.getByRole("table", { name: /^(Các nhóm lấy câu|Question groups)/ });
const rowOf = (name: string) => within(table()).getByRole("rowheader", { name }).closest("tr")!;
const cells = (row: HTMLElement) => Array.from(row.querySelectorAll("th,td"), (cell) => cell.textContent?.trim());

describe("bảng nhóm của môn thuộc phần Nghe (F1)", () => {
  it("cột Nhãn audio, Số lượng, Tổng câu, Tỷ lệ trong phần, Nguồn khả dụng và dòng Tổng phần Nghe", () => {
    renderPanel(section("REQUIRED"));
    const headers = within(table()).getAllByRole("columnheader").map((cell) => cell.textContent);
    expect(headers).toEqual(expect.arrayContaining(["Nhãn audio", "Số lượng", "Tổng câu", "Tỷ lệ trong phần", "Nguồn khả dụng"]));
    expect(headers).not.toContain("Số câu");

    expect(cells(rowOf("Dòng 1 · Câu đơn")).slice(0, 7)).toEqual(["Dòng 1 · Câu đơn", "Một đáp án", "Có audio", "8 câu đơn", "8", "22,9%", "42 câu"]);
    expect(cells(rowOf("Dòng 2 · Hội thoại 4 câu")).slice(2, 7)).toEqual(["Có audio", "3 chùm × 4 câu con", "12", "34,3%", "17 câu"]);
    expect(cells(rowOf("Dòng 3 · Bài nghe 5 câu")).slice(3, 6)).toEqual(["3 chùm × 5 câu con", "15", "42,9%"]);

    const total = within(table()).getByRole("rowheader", { name: "Tổng phần Nghe" }).closest("tr")!;
    expect(Array.from(total.querySelectorAll("th,td"), (cell) => cell.textContent?.trim()).slice(0, 3)).toEqual(["Tổng phần Nghe", "35", "100%"]);
  });

  it("dòng tóm tắt phần Nghe: tên phần, số câu, nhãn Có audio, điều kiện đặt theo phần", () => {
    renderPanel(section("REQUIRED"));
    expect(screen.getByText(byTextContent("Phần Nghe · 35 câu"))).toBeInTheDocument();
    expect(screen.getAllByText("Có audio").length).toBeGreaterThan(1);
    expect(screen.getByText("Điều kiện audio đặt theo phần, không đổi ở từng dòng.")).toBeInTheDocument();
  });

  it("nguồn khả dụng của từng dòng dùng đúng bộ lọc và điều kiện audio của môn", () => {
    renderPanel(section("REQUIRED"));
    expect(counts.queries).toHaveLength(3);
    expect(counts.queries).toEqual(expect.arrayContaining([expect.objectContaining({ questionType: "QUESTION_SET", audioRequirement: "REQUIRED" })]));
  });

  it("số ở bảng bằng số ở vùng sửa nhóm: cùng tỷ lệ trong phần và cùng nguồn khả dụng", () => {
    const current = section("REQUIRED");
    renderPanel(current);
    const shown = cells(rowOf("Dòng 2 · Hội thoại 4 câu"));
    expect(shown[5]).toBe("34,3%");
    expect(shown[6]).toBe("17 câu");
    document.body.innerHTML = "";
    renderWithProviders(
      <BlockEditor
        block={blocks[1]!}
        section={current}
        subjectCode="ANH"
        locationText="Nghe"
        position={2}
        siblingCount={3}
        axes={axes}
        labels={labels}
        reference={typedReference}
        examKind="HSCA"
        scoringDefaults={null}
        competencies={[]}
        partTotal={35}
        onChange={() => {}}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={() => {}}
      />,
    );
    expect(screen.getByText("12 câu · 34,3% của phần")).toBeInTheDocument();
    expect(screen.getByText("Nguồn khả dụng: 17 câu có audio")).toBeInTheDocument();
  });

  it("phần Đọc: nhãn Không có audio ở từng dòng và ở dòng tóm tắt", () => {
    renderPanel(section("FORBIDDEN"));
    expect(cells(rowOf("Dòng 1 · Câu đơn"))[2]).toBe("Không có audio");
    expect(screen.getByText(byTextContent("Phần Nghe · 35 câu")).parentElement).toHaveTextContent("Không có audio");
    expect(screen.getByRole("rowheader", { name: "Tổng phần Nghe" })).toBeInTheDocument();
  });

  it("tổng các dòng khác số câu của phần: dòng tổng hiện tỷ lệ thực, không ép 100%", () => {
    renderPanel(section("REQUIRED"), { partTotal: 70 });
    const total = within(table()).getByRole("rowheader", { name: "Tổng phần Nghe" }).closest("tr")!;
    expect(Array.from(total.querySelectorAll("th,td"), (cell) => cell.textContent?.trim()).slice(1, 3)).toEqual(["35", "50%"]);
  });

  it("tiếng Anh: tiêu đề cột, dòng tổng và dòng tóm tắt bằng tiếng Anh", () => {
    renderPanel(section("REQUIRED"), { english: true });
    const headers = within(table()).getAllByRole("columnheader").map((cell) => cell.textContent);
    expect(headers).toEqual(expect.arrayContaining(["Audio label", "Quantity", "Total questions", "Share of the part", "Available source"]));
    expect(screen.getByText(byTextContent("Part Nghe · 35 questions"))).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: "Part Nghe total" })).toBeInTheDocument();
    expect(within(table()).getByText("3 sets × 4 sub-questions")).toBeInTheDocument();
  });

  it("N3/N5 · tên nhóm, dạng trả lời, phạm vi nguồn và tiêu đề cột số không xuống dòng; bảng vẫn nằm trong vùng cuộn có dòng nhắc", () => {
    renderPanel(section("REQUIRED"));
    const row = rowOf("Dòng 2 · Hội thoại 4 câu");
    expect(row.querySelector("th")).toHaveClass("whitespace-nowrap");
    const cells = Array.from(row.querySelectorAll("td"));
    expect(cells.length).toBeGreaterThanOrEqual(7);
    for (const cell of cells.slice(0, -1)) {
      if (cell.querySelector("button")) continue;
      expect(cell).toHaveClass("whitespace-nowrap");
    }
    for (const header of within(table()).getAllByRole("columnheader")) {
      if (!header.textContent) continue;
      expect(header, header.textContent).toHaveClass("whitespace-nowrap");
      expect(header, header.textContent).not.toHaveClass("max-w-40");
    }
    expect(row.querySelector("td.max-w-40")).toBeNull();
    const region = screen.getByRole("region", { name: /^Các nhóm lấy câu/ });
    expect(region).toHaveAttribute("tabindex", "0");
    expect(region).toContainElement(table());
    expect(region.previousElementSibling).toHaveTextContent("Bảng cuộn ngang");
  });
});

describe("môn không có điều kiện audio (F1)", () => {
  it.each([undefined, "ANY" as const])("điều kiện %s: bảng cũ, không cột audio, không dòng tổng, không đếm nguồn từng dòng", (audio) => {
    renderPanel(section(audio));
    const headers = within(table()).getAllByRole("columnheader").map((cell) => cell.textContent);
    expect(headers).toEqual(["Nhóm", "Dạng trả lời", "Số câu", "Tỷ lệ", "Phạm vi nguồn", "Thao tác"]);
    expect(within(table()).queryByRole("rowheader", { name: /^Tổng phần/ })).toBeNull();
    expect(screen.queryByText(/^Phần Nghe/)).toBeNull();
    expect(counts.queries).toHaveLength(0);
    expect(cells(rowOf("Dòng 2 · Hội thoại 4 câu")).slice(2, 4)).toEqual(["3 chùm × 4 câu con = 12 câu", "34,3%"]);
  });
});
