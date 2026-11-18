import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { SubjectChoiceOptions } from "../components/subject-choice-options";
import type { BlockMatrix, ExamPartOption, ExamSection } from "../types";

const axes = { grades: ["GRADE_10", "GRADE_11", "GRADE_12"], difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"] };
const labels = { grade: (code: string) => code.replace("GRADE_", "L"), difficulty: (code: string) => code.slice(0, 2) };
const names = { subject: (code: string) => code, combination: (code: string) => code };

const matrix = (byGrade: number[], byDifficulty: number[]): BlockMatrix => ({
  mode: "SEPARATE",
  unit: "COUNT",
  cells: [],
  byGrade: axes.grades.map((code, index) => ({ code, value: byGrade[index] ?? 0 })),
  byDifficulty: axes.difficulties.map((code, index) => ({ code, value: byDifficulty[index] ?? 0 })),
});

const section = (id: string, subjectCode: string, over: Partial<ExamSection> = {}): ExamSection => ({
  id,
  subjectCode,
  questionCount: 17,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: "NATURAL",
  blocks: [
    { id: `${id}-b`, name: "G", questionType: "SINGLE_CHOICE", questionCount: 17, setCount: null, setSize: null, requiredPointsPerQuestion: 1, knowledgeScope: null, tagCodes: [], matrix: null, childAnswerType: null, templateGroupCode: null, answerTypeLocked: false },
  ],
  ...over,
});

/** HSA Khoa học: 5 môn × 17 câu, chọn 3; Vật lí và Hóa học có ma trận, Sinh học chưa khai. */
const OPTION: ExamPartOption = {
  id: "opt-sci",
  kind: "SUBJECT_CHOICE",
  code: "SCIENCE",
  name: "Khoa học",
  chooseSubjects: 3,
  countsByPick: [17, 17, 16],
  sections: [
    section("s-phy", "PHYSICS", { matrix: matrix([5, 6, 6], [4, 5, 5, 3]) }),
    section("s-che", "CHEMISTRY", { matrix: matrix([3, 7, 7], [2, 6, 6, 3]) }),
    section("s-bio", "BIOLOGY"),
    section("s-his", "HISTORY", { choiceGroup: "SOCIAL" }),
    section("s-geo", "GEOGRAPHY", { choiceGroup: "SOCIAL" }),
  ],
};

describe("HSA Khoa học: bảng môn của phương án chọn môn (R1-18)", () => {
  it("mỗi môn hiện số câu 17, ma trận lớp và độ khó quy ra số câu; môn chưa khai ma trận hiện —", () => {
    renderWithProviders(<SubjectChoiceOptions option={OPTION} names={names} axes={axes} labels={labels} onSelectSection={vi.fn()} />);
    const table = screen.getByRole("table", { name: /./ });
    const rowOf = (subject: string) => within(table).getByRole("row", { name: new RegExp(`^${subject}`) });
    expect(within(table).getAllByRole("row")).toHaveLength(1 + 5);

    const physics = within(rowOf("PHYSICS")).getAllByRole("cell").map((cell) => cell.textContent);
    expect(physics.slice(1, 4)).toEqual(["17", "5/6/6", "4/5/5/3"]);
    const chemistry = within(rowOf("CHEMISTRY")).getAllByRole("cell").map((cell) => cell.textContent);
    expect(chemistry.slice(1, 4)).toEqual(["17", "3/7/7", "2/6/6/3"]);
    const biology = within(rowOf("BIOLOGY")).getAllByRole("cell").map((cell) => cell.textContent);
    expect(biology.slice(1, 4)).toEqual(["17", "—", "—"]);
    for (const subject of ["HISTORY", "GEOGRAPHY"]) {
      expect(within(rowOf(subject)).getAllByRole("cell")[1]!.textContent).toBe("17");
    }
  });

  it("ma trận theo tỷ lệ quy ra số câu bằng phần dư lớn nhất trên 17 câu", () => {
    const percent: BlockMatrix = { ...matrix([0, 0, 0], [0, 0, 0, 0]), unit: "PERCENT", byGrade: [{ code: "GRADE_10", value: 30 }, { code: "GRADE_11", value: 30 }, { code: "GRADE_12", value: 40 }], byDifficulty: [{ code: "RECOGNITION", value: 25 }, { code: "COMPREHENSION", value: 25 }, { code: "APPLICATION", value: 25 }, { code: "HIGH_APPLICATION", value: 25 }] };
    renderWithProviders(
      <SubjectChoiceOptions option={{ ...OPTION, sections: [section("s-phy", "PHYSICS", { matrix: percent })] }} names={names} axes={axes} labels={labels} onSelectSection={vi.fn()} />,
    );
    const cells = within(screen.getByRole("row", { name: /^PHYSICS/ })).getAllByRole("cell");
    // 17 × 30% = 5,1; 5,1; 6,8 -> 5 + 5 + 7; 17 / 4 = 4,25 mỗi mức -> 5 + 4 + 4 + 4.
    expect(cells[2]!.textContent).toBe("5/5/7");
    expect(cells[3]!.textContent).toBe("5/4/4/4");
  });
});
