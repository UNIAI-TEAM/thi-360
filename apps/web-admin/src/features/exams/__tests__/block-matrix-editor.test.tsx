import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { BlockMatrixEditor } from "../components/block-matrix-editor";
import { emptyMatrix } from "../matrix";
import type { AvailableQuestionCounts, BlockMatrix, QuestionBlock } from "../types";

const axes = { grades: ["GRADE_10", "GRADE_11"], difficulties: ["RECOGNITION", "COMPREHENSION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };

function block(matrix: BlockMatrix, questionCount: number): QuestionBlock & { matrix: BlockMatrix; questionCount: number } {
  return {
    id: "blk-1",
    name: "Nhóm",
    questionType: "SINGLE_CHOICE",
    questionCount,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope: null,
    tagCodes: [],
    matrix,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
  };
}

const available: AvailableQuestionCounts = {
  total: 40,
  cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 40 }],
  byAnswerType: [],
};

function renderEditor(matrix: BlockMatrix, questionCount: number, props: { availableState?: "loading" | "error" | "ready" } = {}) {
  return renderWithProviders(
    <BlockMatrixEditor
      block={block(matrix, questionCount)}
      axes={axes}
      labels={labels}
      competencies={[]}
      onChange={() => undefined}
      available={available}
      availableState={props.availableState ?? "ready"}
    />,
  );
}

describe("BlockMatrixEditor: nguồn (UNI-1703)", () => {
  it("không còn dòng 'Nguồn phù hợp với bộ lọc của khối', gợi ý nguồn của từng ô vẫn có", () => {
    renderEditor(
      { ...emptyMatrix(), cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 5 }] },
      5,
    );
    expect(screen.queryByText(/Nguồn phù hợp/)).not.toBeInTheDocument();
    expect(screen.queryByText(/câu Sẵn sàng/)).not.toBeInTheDocument();
    expect(screen.getByText("nguồn 40")).toBeInTheDocument();
  });
});

const cell = (gradeCode: string, difficultyCode: string, value: number) => ({ gradeCode, difficultyCode, value });
/** Ma trận kết hợp 2 × 2 với tổng đúng `total` câu. */
const combined = (total: number): BlockMatrix => {
  const base = Math.floor(total / 4);
  const last = total - base * 3;
  return {
    ...emptyMatrix(),
    cells: [
      cell("GRADE_10", "RECOGNITION", base),
      cell("GRADE_10", "COMPREHENSION", base),
      cell("GRADE_11", "RECOGNITION", base),
      cell("GRADE_11", "COMPREHENSION", last),
    ],
  };
};

describe("BlockMatrixEditor: thanh phân bổ (UNI-1702 C1)", () => {
  it("vượt 76/50: thanh đỏ đầy 100%, 'Vượt 26 câu' và thông báo màu lỗi, giá trị đọc nêu trạng thái", () => {
    renderEditor(combined(76), 50);
    const meter = screen.getByRole("meter");
    expect(meter).toHaveAttribute("data-state", "over");
    expect(meter).toHaveAttribute("aria-valuenow", "76");
    expect(meter).toHaveAttribute("aria-valuemin", "0");
    expect(meter).toHaveAttribute("aria-valuetext", "76 trên 50 câu, vượt 26 câu");
    const fill = meter.firstElementChild as HTMLElement;
    expect(fill).toHaveClass("bg-danger");
    expect(fill.style.width).toBe("100%");
    expect(screen.getByText("Vượt 26 câu")).toHaveClass("text-danger");
    expect(screen.getByText(/Tổng ma trận là 76 câu, khối cần 50 câu/).closest("li")).toHaveClass("text-danger");
    expect(screen.queryByText(/Thiếu \d+ câu/)).not.toBeInTheDocument();
  });

  it("khớp 50/50: thanh xanh, 'Khớp số câu của khối' màu thành công, không có chữ thiếu hoặc vượt", () => {
    renderEditor(combined(50), 50);
    const meter = screen.getByRole("meter");
    expect(meter).toHaveAttribute("data-state", "exact");
    expect(meter.firstElementChild).toHaveClass("bg-success");
    expect(meter).toHaveAttribute("aria-valuetext", "50 trên 50 câu, khớp");
    expect(screen.getByText("Khớp số câu của khối").closest("p")).toHaveClass("text-success");
    expect(screen.queryByText(/Vượt|Thiếu/)).not.toBeInTheDocument();
  });

  it("thiếu 30/50: thanh vàng theo tỷ lệ, 'Thiếu 20 câu' màu cảnh báo", () => {
    renderEditor(combined(30), 50);
    const meter = screen.getByRole("meter");
    expect(meter).toHaveAttribute("data-state", "under");
    const fill = meter.firstElementChild as HTMLElement;
    expect(fill).toHaveClass("bg-warning");
    expect(fill.style.width).toBe("60%");
    expect(meter).toHaveAttribute("aria-valuetext", "30 trên 50 câu, thiếu 20 câu");
    expect(screen.getByText("Thiếu 20 câu")).toHaveClass("text-warning");
    expect(screen.getByText(/Tổng ma trận là 30 câu, khối cần 50 câu/).closest("li")).toHaveClass("text-warning");
  });

  it("chỉ tiêu riêng chưa khai báo: không báo thiếu hay vượt", () => {
    renderWithProviders(
      <BlockMatrixEditor
        block={block(emptyMatrix(), 50)}
        axes={axes}
        labels={labels}
        competencies={[]}
        onChange={() => undefined}
        available={available}
        availableState="ready"
        declared={false}
      />,
    );
    expect(screen.getByRole("meter")).toHaveAttribute("data-state", "neutral");
    expect(screen.queryByText(/Vượt|Thiếu/)).not.toBeInTheDocument();
  });
});
