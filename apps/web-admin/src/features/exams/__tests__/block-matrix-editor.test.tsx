import { screen, within } from "@testing-library/react";
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

/** Ô Tổng của chân bảng chứa số `value` (ô `output` trong `td`). */
const totalCell = (value: string) => screen.getByText(value, { selector: "output" }).closest("td")!;

describe("BlockMatrixEditor: ô Tổng theo trạng thái phân bổ (UNI-2756)", () => {
  it("vượt: ô Tổng dùng token lỗi, mang data-state=over và chữ cho trình đọc màn hình (không chỉ màu)", () => {
    renderEditor(combined(76), 50);
    const cell = totalCell("76");
    expect(cell).toHaveAttribute("data-state", "over");
    expect(cell).toHaveClass("bg-danger-bg", "text-danger");
    expect(within(cell).getByText("vượt")).toHaveClass("sr-only");
    expect(cell).toHaveTextContent(/^vượt 76$/);
  });

  it("thiếu: ô Tổng dùng token cảnh báo, data-state=under", () => {
    renderEditor(combined(30), 50);
    const cell = totalCell("30");
    expect(cell).toHaveAttribute("data-state", "under");
    expect(cell).toHaveClass("bg-warning-bg", "text-warning");
    expect(within(cell).getByText("thiếu")).toHaveClass("sr-only");
    expect(cell).toHaveTextContent(/^thiếu 30$/);
  });

  it("khớp: ô Tổng giữ nguyên, không data-state, không màu trạng thái", () => {
    renderEditor(combined(50), 50);
    const cell = totalCell("50");
    expect(cell).not.toHaveAttribute("data-state");
    expect(cell.className).not.toMatch(/danger|warning/);
  });

  it("chỉ tiêu riêng chưa khai báo: ô Tổng không tô trạng thái", () => {
    renderWithProviders(
      <BlockMatrixEditor
        block={block({ ...combined(76) }, 50)}
        axes={axes}
        labels={labels}
        competencies={[]}
        onChange={() => undefined}
        available={available}
        availableState="ready"
        declared={false}
      />,
    );
    expect(totalCell("76")).not.toHaveAttribute("data-state");
  });

  it("riêng từng chiều (ảnh BA): Theo khối lớp tổng 60 vượt thì chỉ ô đó đỏ, Theo độ khó tổng 50 giữ nguyên", () => {
    renderEditor(
      {
        ...emptyMatrix(),
        mode: "SEPARATE",
        byGrade: [
          { code: "GRADE_10", value: 30 },
          { code: "GRADE_11", value: 30 },
        ],
        byDifficulty: [
          { code: "RECOGNITION", value: 25 },
          { code: "COMPREHENSION", value: 25 },
        ],
      },
      50,
    );
    expect(totalCell("60")).toHaveAttribute("data-state", "over");
    expect(totalCell("50")).not.toHaveAttribute("data-state");
  });

  it("nhập theo %: tổng 110% là vượt, 90% là thiếu", () => {
    const percent = (total: number): BlockMatrix => ({
      ...emptyMatrix(),
      unit: "PERCENT",
      cells: [cell("GRADE_10", "RECOGNITION", total - 30), cell("GRADE_10", "COMPREHENSION", 10), cell("GRADE_11", "RECOGNITION", 10), cell("GRADE_11", "COMPREHENSION", 10)],
    });
    const first = renderEditor(percent(110), 50);
    expect(totalCell("110%")).toHaveAttribute("data-state", "over");
    first.unmount();
    renderEditor(percent(90), 50);
    expect(totalCell("90%")).toHaveAttribute("data-state", "under");
  });
});
