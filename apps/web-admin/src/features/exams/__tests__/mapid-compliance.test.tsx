import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ContentReference } from "@/features/content/types";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapParts } from "../api-mapping";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { buildMapidRuleIndex, complianceLevel, parseCriterion, resolveMapidRow } from "../review-compliance";
import type { PreviewCompliance } from "../review-types";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";
import { paperOf, partOf } from "./preview-paper-fixtures";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const node = (id: string, code: string, name: string) => ({
    id,
    code,
    name,
    level: "KNOWLEDGE",
    subjectCode: "TOAN",
    gradeCode: "G12",
    active: true,
    children: [],
  });
  const getContentReference = (locale: string = "vi") =>
    Promise.resolve({
      subjects: [{ code: "TOAN", name: locale === "en" ? "Mathematics" : "Toán" }],
      grades: [],
      difficulties: [],
      tags: [],
      competencies: [],
      knowledgeTree: [node("n-hs", "M12-HS", locale === "en" ? "Functions" : "Hàm số")],
    } as unknown as ContentReference);
  return { ...(await importOriginal()), getContentReference, defaultContentApiDeps: { getContentReference } };
});

const row = (criterion: string, scope: string, required: number | null, actual: number, over: Partial<PreviewCompliance> = {}): PreviewCompliance => ({
  criterion,
  scope,
  required,
  actual,
  enforcement: "REQUIRED",
  basis: "QUESTION",
  independent: false,
  note: null,
  originalRequired: null,
  topicName: null,
  ...over,
});

const blockWire = (id: string, name: string, questionType: string, questionCount: number) => ({ id, name, questionType, questionCount });

const structureWire = {
  examId: EXAM_ID,
  revision: 1,
  parts: [
    {
      id: "p1",
      name: "Phần I",
      sections: [
        {
          id: "s1",
          subjectCode: "TOAN",
          questionCount: 50,
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "present", type: "PRESENT", nodeIds: ["n-hs"] },
            { id: "atleast", type: "COUNT", groupId: "short", nodeIds: ["n-hs"], comparator: "AT_LEAST", count: 3 },
            { id: "exact", type: "COUNT", nodeIds: ["n-hs"], comparator: "EXACT", count: 3 },
            { id: "only", type: "ONLY_IN_GROUP", groupId: "short", nodeIds: ["n-hs"], comparator: "EXACT", count: 3 },
          ],
          blocks: [blockWire("short", "Trả lời ngắn", "SHORT_ANSWER", 15), blockWire("single", "Trắc nghiệm", "SINGLE_CHOICE", 32)],
        },
      ],
    },
  ],
};

const ROWS: PreviewCompliance[] = [
  row("MAPID:present", "SECTION:s1", 1, 2),
  row("MAPID:atleast", "BLOCK:short", 3, 2),
  row("MAPID:exact", "SECTION:s1", 3, 4),
  row("MAPID:only", "SECTION:s1", 0, 1),
  row("MAPID:only:COUNT", "BLOCK:short", 3, 3),
  row("MAPID:gone", "SECTION:s1", 1, 1),
];

describe("dòng MAPID của bảng đối chiếu", () => {
  it("parseCriterion tách mã quy tắc và hậu tố :COUNT", () => {
    expect(parseCriterion("MAPID:present")).toEqual({ kind: "MAPID", ruleId: "present", count: false });
    expect(parseCriterion("MAPID:only:COUNT")).toEqual({ kind: "MAPID", ruleId: "only", count: true });
    expect(parseCriterion("MAPID:legacy-q1")).toEqual({ kind: "MAPID", ruleId: "legacy-q1", count: false });
  });

  it("quy tắc của dòng tra theo phạm vi: môn hoặc nhóm; không có quy tắc thì không tra được", () => {
    const index = buildMapidRuleIndex({ parts: mapParts(structureWire.parts as never) })!;

    expect(resolveMapidRow(ROWS[0]!, index)?.rule.id).toBe("present");
    expect(resolveMapidRow(ROWS[1]!, index)?.rule.id).toBe("atleast");
    expect(resolveMapidRow(ROWS[4]!, index)?.rule.id).toBe("only");
    expect(resolveMapidRow(ROWS[5]!, index)).toBeNull();
    expect(resolveMapidRow(row("GRADE:G12", "SECTION:s1", 1, 1), index)).toBeNull();
  });

  it("mức đạt theo quy tắc: 'ít nhất' và 'phải có' đạt khi nhiều hơn, 'đúng' và 'chỉ ở nhóm này' lệch khi khác", () => {
    const index = buildMapidRuleIndex({ parts: mapParts(structureWire.parts as never) })!;
    const level = (item: PreviewCompliance) => complianceLevel(item, index);

    expect(level(ROWS[0]!)).toBe("OK");
    expect(level(ROWS[1]!)).toBe("REQUIRED_DEVIATION");
    expect(level(row("MAPID:atleast", "BLOCK:short", 3, 5))).toBe("OK");
    expect(level(ROWS[2]!)).toBe("REQUIRED_DEVIATION");
    expect(level(ROWS[3]!)).toBe("REQUIRED_DEVIATION");
    expect(level(row("MAPID:only", "SECTION:s1", 0, 0))).toBe("OK");
    expect(level(ROWS[4]!)).toBe("OK");
  });

  it("chưa tra được quy tắc: số 0 phải bằng, số dương là 'ít nhất'", () => {
    expect(complianceLevel(row("MAPID:x", "SECTION:s1", 2, 5))).toBe("OK");
    expect(complianceLevel(row("MAPID:x", "SECTION:s1", 2, 1))).toBe("REQUIRED_DEVIATION");
    expect(complianceLevel(row("MAPID:x", "SECTION:s1", 0, 1))).toBe("REQUIRED_DEVIATION");
  });

  async function renderRows(rows: PreviewCompliance[], locale?: "en") {
    new FakeServer().on("GET", `${EXAM_PATH}/structure`, { status: 200, body: structureWire }).install();
    renderWithProviders(
      <ExamPreviewCompliance
        paper={paperOf([partOf({ partId: "p1", name: "Phần I", blocks: [] })])}
        compliance={rows}
        dropped={[]}
        examId={EXAM_ID}
      />,
      locale ? { locale, messages: en } : undefined,
    );
    // Câu quy tắc chỉ hiện khi cấu trúc và danh mục tải xong.
    const firstExpected = locale ? "Whole subject · Must include Functions" : "Toàn môn · Phải có Hàm số";
    await screen.findByText((_, node) => node?.tagName === "SPAN" && node.textContent === firstExpected, {}, { timeout: 4000 }).catch(() => undefined);
    return screen.findByRole("table", { name: locale ? "Required and actual values of the trial paper" : "Yêu cầu và thực tế của đề thử" });
  }

  it("vi: tiêu chí là câu của quy tắc, yêu cầu và thực tế, chip kết quả", async () => {
    const table = await renderRows(ROWS);
    const rows = within(table).getAllByRole("row").slice(1);

    expect(rows[0]).toHaveTextContent("Toàn môn · Phải có Hàm số");
    expect(rows[0]!.querySelector("[data-exam-content]")).toHaveTextContent("Hàm số");
    expect(within(rows[0]!).getByText("ít nhất 1")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Đạt")).toBeInTheDocument();
    expect(rows[1]).toHaveTextContent("Trả lời ngắn · Ít nhất 3/15 câu thuộc Hàm số");
    expect(rows[1]!.querySelector("[data-exam-content]")).toHaveTextContent("Trả lời ngắn");
    expect(within(rows[1]!).getByText("ít nhất 3")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Lệch · bắt buộc")).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent("Toàn môn · Đúng 3/50 câu thuộc Hàm số");
    expect(rows[2]!.querySelector("[data-exam-content]")).toHaveTextContent("Hàm số");
    expect(within(rows[2]!).getByText("Lệch · bắt buộc")).toBeInTheDocument();
    expect(rows[3]).toHaveTextContent("Hàm số chỉ xuất hiện ở nhóm Trả lời ngắn · Đúng 3/15 câu · ngoài nhóm này");
    expect(rows[4]).toHaveTextContent("Hàm số chỉ xuất hiện ở nhóm Trả lời ngắn · Đúng 3/15 câu · số câu trong nhóm");
    expect(within(rows[4]!).getByText("Đạt")).toBeInTheDocument();
    expect(within(rows[5]!).getByText("Quy tắc MAPID không còn trong bài")).toBeInTheDocument();
  });

  it("en", async () => {
    const table = await renderRows(ROWS.slice(0, 2), "en");
    const rows = within(table).getAllByRole("row").slice(1);

    expect(rows[0]).toHaveTextContent("Whole subject · Must include Functions");
    expect(rows[0]!.querySelector("[data-exam-content]")).toHaveTextContent("Functions");
    expect(within(rows[0]!).getByText("at least 1")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Deviates · required")).toBeInTheDocument();
  });
});
