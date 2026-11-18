import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamOverviewTable, type ExamOverviewTableProps } from "../components/exam-overview-table";
import { overviewDiff, overviewRowsFromTemplate, overviewTotals, rowsDifferFromTemplate, type OverviewRow } from "../create-exam-overview";
import type { ExamTemplate, ExamTemplateBlock } from "../types";

/** FE-7 dòng 11, 13, 14, 15: bảng tổng quan bước 1 có dòng lấy câu và cột Lệnh hỏi (TaoBaiThiHscaVsat2B). */

const block = (name: string, patch: Partial<ExamTemplateBlock>): ExamTemplateBlock => ({
  name,
  questionType: "SINGLE_CHOICE",
  questionCount: 1,
  requiredPointsPerQuestion: 6,
  ...patch,
});

function template(kind: string, blocks: ExamTemplateBlock[][]): ExamTemplate {
  return {
    code: `${kind}_X`,
    kind,
    scope: "SUBJECT",
    name: "Mẫu",
    timing: "SHARED",
    durationMinutes: 90,
    maxScore: 150,
    fixedBlockCounts: true,
    parts: blocks.map((partBlocks, index) => ({
      name: `Phần ${index + 1}`,
      questionCount: partBlocks.reduce((total, item) => total + (item.questionCount ?? 0), 0),
      durationMinutes: null,
      mode: "FIXED" as const,
      sections: [{ subjectCode: "MATH", questionCount: null, blocks: partBlocks }],
    })),
  };
}

const VSAT_MATH = template("VSAT", [
  [block("Đúng/Sai bốn ý", { questionType: "TRUE_FALSE", questionCount: 9, itemShape: { statements: 4 } })],
  [block("Câu chùm", { questionType: "QUESTION_SET", questionCount: 6, setCount: 2, setSize: 3 })],
]);

function renderTable(rows: OverviewRow[], extra: Partial<ExamOverviewTableProps> = {}) {
  return renderWithProviders(
    <ExamOverviewTable
      rows={rows}
      timing="SHARED"
      showPoints={false}
      showCommands
      onCellChange={vi.fn()}
      issues={new Map()}
      subjectName={(code) => code}
      sharedDuration={{ value: "90", onChange: vi.fn() }}
      {...extra}
    />,
  );
}

describe("dòng lấy câu ở bảng tổng quan (dòng 11)", () => {
  it("H-SCA, V-SAT: dòng lấy câu thụt dưới dòng phần, kèm số câu, tỷ lệ trong phần, lệnh hỏi và điểm", () => {
    const rows = overviewRowsFromTemplate(VSAT_MATH, "SUBJECT");
    expect(rows.map((row) => row.kind)).toEqual(["part", "block", "part", "block"]);
    expect(rows[1]!.block).toEqual({ sets: null, setSize: null, questions: 9, commands: 36, points: 54 });
    expect(rows[3]!.block).toEqual({ sets: 2, setSize: 3, questions: 6, commands: 6, points: 36 });
    expect(overviewTotals(rows, "SHARED", "90")).toMatchObject({ questions: 15, commands: 42, blockPoints: 90 });

    renderTable(rows);
    const table = screen.getByRole("table");
    const grid = within(table).getAllByRole("row").map((row) => Array.from(row.querySelectorAll("th,td"), (cell) => cell.textContent?.replace(/\s+/g, " ").trim()));
    expect(grid[0]).toEqual(["Phần và dòng lấy câu", "Số câu", "Tỷ lệ trong phần", "Lệnh hỏi", "Điểm tối đa"]);
    expect(grid.find((cells) => cells[0]?.includes("Đúng/Sai bốn ý"))).toEqual(["–Đúng/Sai bốn ý", "9", "100%", "36", "54"]);
    expect(grid.find((cells) => cells[0]?.includes("Câu chùm"))?.[1]).toContain("2 chùm × 3 câu con");
    expect(grid.at(-1)).toEqual(["Tổng của bài", "15", "100%", "42", "90"]);
  });

  it("chùm trộn dạng tính lệnh hỏi theo dạng ở từng vị trí câu con", () => {
    const mixed = template("VSAT", [
      [
        block("Bài đọc dùng chung", {
          questionType: "QUESTION_SET",
          questionCount: 8,
          setCount: 1,
          setSize: 8,
          childConditions: [
            { from: 1, to: 2, nodeIds: [], answerType: "TRUE_FALSE", itemShape: { statements: 4 } },
            { from: 3, to: 8, nodeIds: [], answerType: "SINGLE_CHOICE" },
          ],
        }),
      ],
    ]);
    expect(overviewRowsFromTemplate(mixed, "SUBJECT")[1]!.block).toMatchObject({ questions: 8, commands: 14, points: 48 });
  });

  it("H-SCA không có cột Lệnh hỏi; kỳ thi khác giữ bảng như cũ, không có dòng lấy câu", () => {
    const hsca = overviewRowsFromTemplate(template("HSCA", [[block("Một đáp án", { questionCount: 20 })]]), "SUBJECT");
    renderTable(hsca, { showCommands: false });
    expect(screen.queryByRole("columnheader", { name: "Lệnh hỏi" })).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Điểm tối đa" })).toBeInTheDocument();

    const old = overviewRowsFromTemplate(template("THPTQG", [[block("Một đáp án", { questionCount: 12 })]]), "SUBJECT");
    expect(old.map((row) => row.kind)).toEqual(["part"]);
  });

  it("dòng lấy câu chỉ đọc: không vào yêu cầu overview và không làm bảng thành đã sửa", () => {
    const rows = overviewRowsFromTemplate(VSAT_MATH, "SUBJECT");
    expect(overviewDiff(rows)).toEqual([]);
    expect(rowsDifferFromTemplate(rows)).toBe(false);
  });
});

describe("ô thời lượng nằm trên bảng (dòng 13, 14, 15)", () => {
  it("dòng 13 · ô thời lượng chung đứng trên bảng, ô điểm tối đa vẫn ở dưới", () => {
    renderTable(overviewRowsFromTemplate(VSAT_MATH, "SUBJECT"), { maxScore: { value: "150", onChange: vi.fn() } });
    const duration = screen.getByRole("textbox", { name: "Thời lượng chung" });
    const table = screen.getByRole("table");
    const score = screen.getByRole("textbox", { name: "Điểm tối đa" });
    expect(duration.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(table.compareDocumentPosition(score) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("dòng 15 · gợi ý nối với ô bằng aria-describedby; có lỗi thì ô nối với lỗi", () => {
    const rows = overviewRowsFromTemplate(VSAT_MATH, "SUBJECT");
    const { rerender } = renderTable(rows, { sharedDuration: { value: "90", hint: "Điền sẵn theo mẫu, sửa được.", onChange: vi.fn() } });
    expect(screen.getByRole("textbox", { name: "Thời lượng chung" })).toHaveAccessibleDescription("Điền sẵn theo mẫu, sửa được.");

    rerender(
      <ExamOverviewTable
        rows={rows}
        timing="SHARED"
        showPoints={false}
        onCellChange={vi.fn()}
        issues={new Map()}
        subjectName={(code) => code}
        sharedDuration={{ value: "", hint: "Mẫu của môn này để trống thời lượng.", error: "Chưa nhập thời lượng. Môn này không có thời lượng mặc định.", onChange: vi.fn() }}
      />,
    );
    const input = screen.getByRole("textbox", { name: "Thời lượng chung" });
    expect(input).toBeInvalid();
    expect(input).toHaveAccessibleDescription("Chưa nhập thời lượng. Môn này không có thời lượng mặc định.");
  });

  it("dòng 14 · khung hẹp có dòng nhắc bảng cuộn ngang để tìm ô số câu bị khóa", () => {
    renderTable(overviewRowsFromTemplate(VSAT_MATH, "SUBJECT"));
    expect(screen.getByText("Bảng cuộn ngang: vuốt để xem các cột còn lại.")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" })).toHaveAttribute("tabindex", "0");
  });
});
