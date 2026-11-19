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
