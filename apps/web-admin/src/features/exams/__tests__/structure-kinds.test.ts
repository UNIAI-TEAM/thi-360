import { describe, expect, it } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import {
  defaultTrueFalseTable,
  evenTrueFalseTable,
  expectedScoringFor,
  trueFalseTableIssues,
} from "../components/block-scoring-editor";
import {
  addBranch,
  attachCombination,
  confirmOption,
  optionQuestionCount,
  setPartQuestionCount,
  setSectionCount,
} from "../components/exam-structure-draft";
import {
  choiceGroups,
  computeAllocation,
  printedPartQuestions,
  sectionGroups,
  structureTotals,
  validateBlock,
  validateStructure,
  type MatrixAxes,
} from "../matrix";
import { EXAMS_FIXTURE as fixture } from "./exam-fixture";
import type { BlockMatrix, ExamDetail, ExamPart, QuestionBlock } from "../types";

const reference = referenceFixture as ContentReference;
const exams = fixture.exams;
const AXES: MatrixAxes = {
  grades: reference.grades.map((grade) => grade.code),
  difficulties: reference.difficulties.map((difficulty) => difficulty.code),
};

function sample(id: string): ExamDetail {
  const exam = exams.find((item) => item.id === id);
  if (!exam) throw new Error(`missing ${id}`);
  return structuredClone(exam);
}

function competencyBlock(byCompetency: BlockMatrix["byCompetency"], unit: BlockMatrix["unit"] = "COUNT"): QuestionBlock {
  return {
    id: "blk-c",
    name: "Tư duy khoa học",
    questionType: "SINGLE_CHOICE",
    questionCount: 30,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: 1,
    knowledgeScope: { nodeIds: [], match: "ANY", competencyCodes: ["VACT-TD-LOGIC", "VACT-TD-SL"] },
    tagCodes: [],
    taskTypeCode: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    matrix: {
      mode: "SEPARATE",
      unit,
      cells: [],
      byGrade: [{ code: "GRADE_MIXED", value: unit === "PERCENT" ? 100 : 30 }],
      byDifficulty: [{ code: "COMPREHENSION", value: unit === "PERCENT" ? 100 : 30 }],
      byCompetency,
    },
  };
}

describe("cấu trúc theo bảy kỳ thi: bài mẫu", () => {
  it("THPTQG Tin học: học viên làm 28 câu, đề in 30; thời lượng chung 50 phút; tổng 10 điểm", () => {
    const exam = sample("ex-thptqg-tin-001");
    const totals = structureTotals(exam.parts, { sharedDuration: exam.durationMinutes });
    expect(totals).toMatchObject({ totalQuestions: 28, printedQuestions: 30, totalMinutes: 50, totalPoints: 10, missing: 0 });
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    expect(printedPartQuestions(choice)).toBe(6);
    // Phần chung đứng trước rồi mỗi nhánh; mỗi đường = phần chung + một nhánh.
    expect(sectionGroups(choice).map((group) => group.optionId)).toEqual([null, "opt-t2-cs", "opt-t2-ict"]);
    expect(choiceGroups(choice).map((group) => group.sections.map((section) => section.id))).toEqual([
      ["sec-t2-0", "sec-t2-cs"],
      ["sec-t2-0", "sec-t2-ict"],
    ]);
    expect(validateStructure(exam.parts, AXES, { timing: exam.timing })).toEqual([]);
  });

  it("BCA: điểm phần 25 / 60 / 15 khớp tổng điểm khối, tổng 100 trên thang 100", () => {
    const exam = sample("ex-bca-f-001");
    expect(validateStructure(exam.parts, AXES, { timing: exam.timing })).toEqual([]);
    expect(structureTotals(exam.parts, { sharedDuration: exam.durationMinutes })).toMatchObject({
      totalQuestions: 81,
      printedQuestions: 81,
      totalMinutes: 180,
      totalPoints: 100,
    });
  });

  it("BCA: điểm khối lệch điểm phần bị báo PART_POINTS", () => {
    const exam = sample("ex-bca-f-001");
    exam.parts[1]!.sections[2]!.blocks[0]!.requiredPointsPerQuestion = 1;
    expect(validateStructure(exam.parts, AXES, { timing: exam.timing })).toEqual([
      { code: "PART_POINTS", partId: "part-b2", optionId: null, expected: 60, actual: 65 },
    ]);
  });

  it("V-ACT: khối Phần 3 chia theo năng lực 12 / 18, hợp lệ; dòng Liên lớp ở khối Toán", () => {
    const exam = sample("ex-vact-f-001");
    expect(validateStructure(exam.parts, AXES, { timing: exam.timing })).toEqual([]);
    const science = exam.parts[2]!.sections[0]!.blocks[0]!;
    expect(computeAllocation(science, AXES).competency).toMatchObject({
      codes: ["VACT-TD-LOGIC", "VACT-TD-SL"],
      counts: [12, 18],
      total: 30,
    });
    const math = exam.parts[1]!.sections[0]!.blocks[0]!;
    expect(math.matrix!.byGrade.find((item) => item.code === "GRADE_MIXED")?.value).toBe(6);
  });
});

describe("thời lượng chung và theo phần", () => {
  const part: ExamPart = {
    id: "p1",
    order: 1,
    name: "Phần 1",
    durationMinutes: null,
    questionCount: 1,
    questionCountOnPaper: 1,
    questionCountRequired: 1,
    participation: { mode: "ALL", choose: null },
    mode: "FIXED",
    sections: [
      {
        id: "s1",
        subjectCode: "MATH",
        questionCount: 1,
        knowledgeScope: null,
        answerQuotas: null,
        matrix: null,
        groupOrder: "SHUFFLE",
        choiceGroup: null,
        blocks: [],
      },
    ],
    options: [],
  };

  it("bài thời lượng chung không báo thiếu thời lượng phần; bài theo phần thì báo", () => {
    const codes = (timing: ExamDetail["timing"]) =>
      validateStructure([part], AXES, { timing }).map((issue) => issue.code);
    expect(codes("SHARED")).not.toContain("PART_DURATION_NOT_POSITIVE");
    expect(codes("PER_PART")).toContain("PART_DURATION_NOT_POSITIVE");
  });

  it("tổng thời gian là thời lượng chung, không cộng thời lượng phần", () => {
    expect(structureTotals([{ ...part, durationMinutes: 30 }], { sharedDuration: 150 }).totalMinutes).toBe(150);
    expect(structureTotals([{ ...part, durationMinutes: 30 }]).totalMinutes).toBe(30);
  });
});

describe("chiều năng lực của ma trận", () => {
  it("tổng năng lực lệch số câu của khối bị báo theo chiều COMPETENCY", () => {
    expect(validateBlock(competencyBlock([{ code: "VACT-TD-LOGIC", value: 12 }, { code: "VACT-TD-SL", value: 18 }]), AXES)).toEqual([]);
    expect(validateBlock(competencyBlock([{ code: "VACT-TD-LOGIC", value: 12 }, { code: "VACT-TD-SL", value: 10 }]), AXES)).toEqual([
      { code: "ALLOCATION_TOTAL", dimension: "COMPETENCY", expected: 30, actual: 22 },
    ]);
  });

  it("theo %: quy đổi phần dư lớn nhất; tổng khác 100% bị báo", () => {
    const block = competencyBlock([{ code: "VACT-TD-LOGIC", value: 40 }, { code: "VACT-TD-SL", value: 60 }], "PERCENT");
    expect(computeAllocation(block, AXES).competency?.counts).toEqual([12, 18]);
    const wrong = competencyBlock([{ code: "VACT-TD-LOGIC", value: 40 }, { code: "VACT-TD-SL", value: 50 }], "PERCENT");
    expect(validateBlock(wrong, AXES)).toEqual([{ code: "PERCENT_TOTAL", dimension: "COMPETENCY", actual: 90 }]);
  });
});

describe("HSA Khoa học là một phương án chọn môn (B9, D25)", () => {
  it("HSA has one SUBJECT_CHOICE, no KH-n", () => {
    for (const id of ["ex-hsa-f-000", "ex-hsa-f-001", "ex-hsa-f-002"]) {
      const choice = sample(id).parts.find((part) => part.mode === "CHOICE")!;
      expect(choice.options.map((option) => [option.kind, option.code])).toEqual([
        ["SUBJECT", "ENGLISH"],
        ["SUBJECT_CHOICE", "SCIENCE"],
      ]);
      const science = choice.options[1]!;
      expect(science).toMatchObject({ chooseSubjects: 3, countsByPick: [17, 17, 16] });
      expect(science).not.toHaveProperty("expertConfirmed");
      expect(science.sections.map((section) => section.choiceGroup)).toEqual(["NATURAL", "NATURAL", "NATURAL", "SOCIAL", "SOCIAL"]);
      expect(science.sections.map((section) => section.subjectCode)).toEqual([
        "PHYSICS",
        "CHEMISTRY",
        "BIOLOGY",
        "HISTORY",
        "GEOGRAPHY",
      ]);
      expect(science.sections.every((section) => section.questionCount === null)).toBe(true);
    }
    // Mẫu cũng không còn 10 phương án KH-n: một SUBJECT_CHOICE, môn có nhóm tự nhiên, xã hội, không cờ chờ xác nhận.
    const template = fixture.templates.find((item) => item.code === "HSA_FULL")!;
    const options = template.parts[2]!.options!;
    expect(options.map((option) => option.kind)).toEqual(["SUBJECT", "SUBJECT_CHOICE"]);
    expect(options[1]!.subjectChoice).toMatchObject({ chooseSubjects: 3, countsByPick: [17, 17, 16] });
    expect(options[1]!.subjectChoice).not.toHaveProperty("expertConfirmed");
    expect(options[1]!.subjectChoice!.sections.map((section) => section.choiceGroup)).toEqual(["NATURAL", "NATURAL", "NATURAL", "SOCIAL", "SOCIAL"]);
    const serialized = JSON.stringify(fixture);
    expect(serialized).not.toMatch(/"code": ?"KH-\d"/);
  });

  it("Toán HSA: chỉ tiêu cấp môn 35 + 15, lớp 3/20/27, độ khó 2/21/20/7; nhóm có templateGroupCode", () => {
    const math = sample("ex-hsa-f-001").parts[0]!.sections[0]!;
    expect(math.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { answerType: "SINGLE_CHOICE", value: 35 },
        { answerType: "SHORT_ANSWER", value: 15 },
      ],
    });
    expect(math.matrix?.byGrade.map((item) => item.value)).toEqual([3, 20, 27]);
    expect(math.matrix?.byDifficulty.map((item) => item.value)).toEqual([2, 21, 20, 7]);
    expect(math.blocks.map((block) => block.templateGroupCode)).toEqual(["HSA.MATH.SINGLES", "HSA.MATH.CLUSTER"]);
    expect(math.blocks[0]!.questionType).toBeNull();
  });

  it("sáu kỳ thi còn lại chỉ có trường mới rỗng (D26)", () => {
    for (const item of exams.filter((exam) => exam.kind !== "HSA")) {
      for (const part of item.parts) {
        for (const section of [...part.sections, ...part.options.flatMap((option) => option.sections)]) {
          expect(section, item.id).toMatchObject({ knowledgeScope: null, answerQuotas: null, matrix: null });
          for (const block of section.blocks) {
            expect(block, item.id).toMatchObject({
              templateGroupCode: null,
              taskTypeCode: null,
              childAnswerType: null,
              answerTypeLocked: false,
            });
          }
        }
      }
    }
  });
});

describe("tổ hợp của phần tự chọn", () => {
  it("gắn tổ hợp danh mục: số câu chia đều 25–25 cho tổ hợp hai môn, thêm vào cuối danh sách lựa chọn", () => {
    // HSA đã chuyển sang một phương án chọn môn (không còn sinh tổ hợp); bài QDA có hai tổ hợp đã gắn (QD01, QD04).
    const exam = sample("ex-qda-f-001");
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    const qd02 = reference.combinations.find((combination) => combination.code === "QD02")!;
    const before = choice.options.length;
    const next = attachCombination(exam.parts, choice.id, qd02).find((part) => part.id === choice.id)!;
    expect(next.options).toHaveLength(before + 1);
    const added = next.options.at(-1)!;
    expect(added).toMatchObject({ kind: "COMBINATION", code: "QD02" });
    expect(added.sections.map((section) => section.questionCount)).toEqual([25, 25]);
  });

  it("sửa số câu môn của lựa chọn đã xác nhận thì phải xác nhận lại", () => {
    const exam = sample("ex-qda-f-001");
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    const option = choice.options.find((item) => item.kind === "COMBINATION")!;
    const confirmed = confirmOption(exam.parts, choice.id, option.id);
    const optionOf = (parts: ExamPart[]) => parts.find((part) => part.id === choice.id)!.options.find((item) => item.id === option.id)!;
    expect(optionOf(confirmed).expertConfirmed).toBe(true);
    const edited = setSectionCount(confirmed, { partId: choice.id, optionId: option.id, sectionId: option.sections[0]!.id }, 18);
    expect(optionOf(edited).expertConfirmed).toBe(false);
  });
});

describe("nhánh của phần tự chọn có phần chung", () => {
  it("thêm nhánh: mã BR-n, đủ số câu còn lại sau phần chung", () => {
    const exam = sample("ex-thptqg-tin-001");
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    expect(optionQuestionCount(choice)).toBe(2);
    const parts = addBranch(exam.parts, choice.id, { name: "  Lập trình  ", subjectCode: "INFORMATICS" });
    const added = parts.find((part) => part.id === choice.id)!.options.at(-1)!;
    expect(added).toMatchObject({ kind: "BRANCH", code: "BR-3", name: "Lập trình" });
    expect(added.sections.map((section) => section.questionCount)).toEqual([2]);
  });

  it("đổi số câu phần giữ phần chung, nhánh một môn nhận phần còn lại", () => {
    const exam = sample("ex-thptqg-tin-001");
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    const next = setPartQuestionCount(choice, 5);
    expect(next.sections[0]!.questionCount).toBe(2);
    expect(next.options.map((option) => option.sections[0]!.questionCount)).toEqual([3, 3]);
  });
});

describe("quy tắc chấm mong đợi của khối", () => {
  it("bảng Đúng/Sai của kỳ thi quy theo điểm mỗi câu; không có bảng thì chia đều", () => {
    const thptqg = reference.examKinds.find((kind) => kind.code === "THPTQG")!.scoringDefaults;
    expect(defaultTrueFalseTable(thptqg, 1)).toEqual([0.1, 0.25, 0.5, 1]);
    expect(defaultTrueFalseTable(thptqg, 2)).toEqual([0.2, 0.5, 1, 2]);
    expect(defaultTrueFalseTable(null, 1)).toEqual(evenTrueFalseTable(4, 1));
  });

  it("bảng phải không giảm và phần tử cuối bằng điểm của câu", () => {
    expect(trueFalseTableIssues([0.1, 0.25, 0.5, 1], 1)).toEqual([]);
    expect(trueFalseTableIssues([0.5, 0.25, 0.5, 0.9], 1)).toEqual(["LAST_NOT_POINTS", "DECREASING"]);
  });

  it("đổi dạng khối nạp quy tắc của kỳ thi; dạng không có quy tắc thì bỏ", () => {
    const thptqg = reference.examKinds.find((kind) => kind.code === "THPTQG")!.scoringDefaults;
    const hsa = reference.examKinds.find((kind) => kind.code === "HSA")!.scoringDefaults;
    expect(expectedScoringFor("TRUE_FALSE", thptqg, 1)).toEqual({ trueFalseTable: [0.1, 0.25, 0.5, 1] });
    expect(expectedScoringFor("TRUE_FALSE", hsa, 1)).toBeUndefined();
    expect(expectedScoringFor("MULTIPLE_CHOICE", hsa, 1)).toEqual({ multipleChoiceRule: "ALL_OR_NOTHING" });
    expect(expectedScoringFor("SINGLE_CHOICE", thptqg, 1)).toBeUndefined();
  });
});
