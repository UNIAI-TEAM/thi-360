import { afterEach, describe, expect, it } from "vitest";
import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference } from "@/features/content/types";
import { listQuestions } from "../api";
import { updateQuestion } from "../editor-api";
import { resetQuestionHistoryForTests } from "../editor-api.mock";
import {
  emptyChild,
  emptyEditorValues,
  rubricTotal,
  scaleScoreTable,
  toEditorValues,
  toSaveRequest,
  type QuestionEditorValues,
} from "../editor-form";
import { validateQuestionEditor } from "../editor-schema";
import { diffSnapshots, toSnapshot } from "../editor-versioning";
import { questionStore } from "../mock/store";
import { evaluateReadiness, isValidScoreTable } from "../readiness";
import {
  parseQuestionFilters,
  serializeQuestionFilters,
  toListQuestionsQuery,
  countAdvancedFilters,
  DEFAULT_QUESTION_FILTERS,
} from "../components/question-filters-state";
import type { QuestionDetail } from "../types";

const reference = referenceFixture as ContentReference;
const KNOWLEDGE = [{ nodeId: "kn-2V1-1-1", code: "2V1-1-1", name: "Luan diem", primary: true }];

afterEach(() => {
  questionStore.reset();
  resetQuestionHistoryForTests();
});

function classified(type: QuestionEditorValues["type"]): QuestionEditorValues {
  return {
    ...emptyEditorValues(type),
    subjectCode: "LITERATURE",
    gradeCode: "GRADE_12",
    difficultyCode: "APPLICATION",
    knowledge: KNOWLEDGE,
    content: "De bai",
  };
}

function essay(points: string, rubric: Array<[string, string]>): QuestionEditorValues {
  const values = classified("ESSAY");
  values.points = points;
  values.body.gradingGuide = "Huong dan cham";
  values.body.rubric = rubric.map(([description, criterionPoints], index) => ({
    id: `r${index}`,
    description,
    points: criterionPoints,
  }));
  return values;
}

function trueFalse(points: string, table: string[]): QuestionEditorValues {
  const values = classified("TRUE_FALSE");
  values.points = points;
  values.body.statements = table.map((_, index) => ({ id: `s${index}`, content: `Y ${index + 1}`, answer: "TRUE" }));
  values.body.scoreTable = table;
  return values;
}

const messages = (values: QuestionEditorValues) => validateQuestionEditor(values).map((issue) => issue.message);

describe("soan cau Tu luan", () => {
  it("bieu mau moi chua co tieu chi (tuy chon) va can huong dan cham", () => {
    const values = classified("ESSAY");
    expect(values.body.rubric).toHaveLength(0);
    expect(messages(values)).toEqual(["gradingGuideRequired"]);
  });

  it("tieu chi tuy chon; co thi tong diem bang diem cua cau", () => {
    expect(messages(essay("2", [["Mo bai", "0,5"], ["Than bai", "1,5"]]))).toEqual([]);
    expect(messages(essay("2", []))).toEqual([]);
    expect(messages(essay("2", [["Mo bai", "0,5"], ["Than bai", "1"]]))).toEqual(["rubricTotalMismatch"]);
    expect(messages(essay("2", [["Mo bai", "0"], ["", "2"]]))).toEqual(["criterionPointsInvalid", "criterionRequired"]);
  });

  it("so chu goi y la so nguyen duong hoac de trong", () => {
    const values = essay("1", [["Y chinh", "1"]]);
    values.body.wordLimit = "200";
    expect(messages(values)).toEqual([]);
    values.body.wordLimit = "2,5";
    expect(messages(values)).toEqual(["wordLimitInvalid"]);
  });

  it("cau con Tu luan cua cau chum so voi diem cua cau con", () => {
    const values = classified("QUESTION_SET");
    const first = emptyChild("ESSAY");
    first.content = "Cau 1";
    first.points = "0,5";
    first.body.rubric = [{ id: "r1", description: "Nhan biet", points: "0,5" }];
    const second = emptyChild("ESSAY");
    second.content = "Cau 2";
    second.body.rubric = [{ id: "r2", description: "Giai thich", points: "0,5" }];
    values.children = [first, second];
    const issues = validateQuestionEditor(values);
    expect(issues).toEqual([{ path: ["children", 1, "body", "rubric"], message: "rubricTotalMismatch" }]);
  });

  it("luu: diem toi da bang diem cua cau, tieu chi doi sang so, kem nang luc", () => {
    const values = essay("2", [["Mo bai", "0,5"], ["Than bai", "1,5"]]);
    values.body.sampleAnswer = "  Bai mau  ";
    values.body.wordLimit = "200";
    values.competencyCode = "VACT-NN-TV";
    const request = toSaveRequest(values);
    expect(request.competencyCode).toBe("VACT-NN-TV");
    expect(request.body).toEqual({
      type: "ESSAY",
      maxPoints: 2,
      rubric: [
        { id: "r0", description: "Mo bai", points: 0.5 },
        { id: "r1", description: "Than bai", points: 1.5 },
      ],
      sampleAnswer: "Bai mau",
      wordLimit: 200,
    });
    expect(rubricTotal(values.body.rubric)).toBe(2);
  });

  it("doc lai cau mau Tu luan vao bieu mau", () => {
    const values = toEditorValues(questionStore.require("q-8001"));
    expect(values.type).toBe("ESSAY");
    expect(values.body.rubric.map((criterion) => criterion.points)).toEqual(["0,25", "0,25", "1", "0,5"]);
    expect(values.body.wordLimit).toBe("200");
    expect(messages(values)).toEqual([]);
  });
});

describe("bang diem Dung/Sai", () => {
  it("cau moi co bang chia deu theo 2 y", () => {
    expect(classified("TRUE_FALSE").body.scoreTable).toEqual(["0,5", "1"]);
  });

  it("khong giam, o cuoi bang diem cua cau, moi o la so", () => {
    expect(messages(trueFalse("1", ["0,1", "0,25", "0,5", "1"]))).toEqual([]);
    expect(messages(trueFalse("1", ["0,5", "0,25", "0,5", "1"]))).toEqual(["scoreNotIncreasing"]);
    expect(messages(trueFalse("2", ["0,1", "0,25", "0,5", "1"]))).toEqual(["scoreLastMismatch"]);
    expect(messages(trueFalse("1", ["a", "1"]))).toEqual(["scoreValueInvalid"]);
  });

  it("bang mau ky thi doi theo diem cua cau", () => {
    const thpt = reference.examKinds.find((kind) => kind.code === "THPTQG")?.scoringDefaults.trueFalse.table;
    expect(thpt).toEqual([0.1, 0.25, 0.5, 1]);
    expect(scaleScoreTable(thpt ?? [], 1)).toEqual([0.1, 0.25, 0.5, 1]);
    expect(scaleScoreTable(thpt ?? [], 2)).toEqual([0.2, 0.5, 1, 2]);
    expect(scaleScoreTable(thpt ?? [], 0.5)).toEqual([0.05, 0.125, 0.25, 0.5]);
  });

  it("luu bang diem dang so", () => {
    const request = toSaveRequest(trueFalse("1", ["0,1", "0,25", "0,5", "1"]));
    expect(request.body).toMatchObject({ type: "TRUE_FALSE", scoreTable: [0.1, 0.25, 0.5, 1] });
  });
});

describe("dieu kien San sang cho quy tac cham", () => {
  const ruleIssues = (question: QuestionDetail) =>
    evaluateReadiness(question, { reference }).rules.flatMap((rule) => rule.issues.map((issue) => issue.code));

  it("cau Tu luan mau: du tieu chi thi dat, tong diem lech thi chua dat", () => {
    expect(evaluateReadiness(questionStore.require("q-8001"), { reference }).ready).toBe(true);
    expect(ruleIssues(questionStore.require("q-8002"))).toEqual(["RUBRIC_TOTAL_MISMATCH"]);
    expect(evaluateReadiness(questionStore.require("q-8003"), { reference }).ready).toBe(true);
  });

  it("Tu luan chua co tieu chi, tieu chi trong mo ta", () => {
    const question = structuredClone(questionStore.require("q-8001"));
    if (question.body.type !== "ESSAY") throw new Error("not essay");
    question.body.rubric = [];
    expect(ruleIssues(question)).toEqual(["NO_RUBRIC"]);
    question.body.rubric = [{ id: "r1", description: " ", points: 2 }];
    expect(ruleIssues(question)).toEqual(["CRITERION_EMPTY"]);
  });

  it("Dung/Sai can bang diem hop le", () => {
    expect(isValidScoreTable([0.1, 0.25, 0.5, 1], 4, 1)).toBe(true);
    expect(isValidScoreTable([0.3333, 0.6667, 1.0], 3, 1)).toBe(true);
    expect(isValidScoreTable([0.1, 0.25, 0.5], 4, 1)).toBe(false);
    expect(isValidScoreTable([0.5, 0.25, 0.5, 1], 4, 1)).toBe(false);
    const question = structuredClone(questionStore.require("q-8004"));
    if (question.body.type !== "TRUE_FALSE") throw new Error("not true/false");
    question.body.scoreTable = [0.1, 0.25, 0.5, 0.75];
    expect(ruleIssues(question)).toEqual(["SCORE_TABLE_INVALID"]);
  });
});

describe("loc nang luc va dang Tu luan", () => {
  it("bo loc nang luc tren URL nam trong Bo loc khac", () => {
    const filters = parseQuestionFilters("?competency=VACT-TOAN&type=ESSAY");
    expect(filters).toMatchObject({ competency: "VACT-TOAN", type: "ESSAY" });
    expect(countAdvancedFilters(filters)).toBe(1);
    expect(serializeQuestionFilters({ ...DEFAULT_QUESTION_FILTERS, competency: "VACT-TOAN" })).toBe("competency=VACT-TOAN");
    expect(toListQuestionsQuery(filters)).toMatchObject({ competencyCode: "VACT-TOAN", type: "ESSAY" });
  });

  it("API loc theo nang luc va dang Tu luan", async () => {
    const byCompetency = await listQuestions({ competencyCode: "VACT-TOAN", size: 100 });
    expect(byCompetency.content.map((question) => question.id)).toContain("q-8008");
    expect(byCompetency.content.every((question) => question.competencyCode === "VACT-TOAN")).toBe(true);
    const essays = await listQuestions({ type: "ESSAY", size: 100 });
    expect(essays.content.map((question) => question.id)).toEqual(expect.arrayContaining(["q-8001", "q-8002"]));
    expect(essays.content.every((question) => question.type === "ESSAY")).toBe(true);
  });
});

describe("phien ban luu quy tac cham va nang luc", () => {
  it("doi tieu chi cham, bang diem, quy tac nhieu dap an hoac nang luc la thay doi noi dung", () => {
    const essayBefore = toSnapshot(questionStore.require("q-8001"));
    const essayAfter = structuredClone(essayBefore);
    if (essayAfter.body.type !== "ESSAY") throw new Error("not essay");
    essayAfter.body.rubric[0]!.points = 0.5;
    essayAfter.competencyCode = "VACT-NN-TV";
    expect(diffSnapshots(essayBefore, essayAfter).map((change) => change.field)).toEqual(["body", "competencyCode"]);

    const choiceBefore = toSnapshot(questionStore.require("q-8006"));
    const choiceAfter = structuredClone(choiceBefore);
    if (choiceAfter.body.type !== "MULTIPLE_CHOICE") throw new Error("not multiple choice");
    choiceAfter.body.scoringRule = "ALL_OR_NOTHING";
    expect(diffSnapshots(choiceBefore, choiceAfter).map((change) => change.field)).toEqual(["body"]);

    const tfBefore = toSnapshot(questionStore.require("q-8004"));
    const tfAfter = structuredClone(tfBefore);
    if (tfAfter.body.type !== "TRUE_FALSE") throw new Error("not true/false");
    tfAfter.body.scoreTable = [0.25, 0.5, 0.75, 1];
    expect(diffSnapshots(tfBefore, tfAfter).map((change) => change.field)).toEqual(["body"]);
  });

  it("luu cau giu nang luc", async () => {
    const question = questionStore.require("q-8007");
    const values = toEditorValues(question);
    const { question: saved } = await updateQuestion(question.id, { ...toSaveRequest(values), expectedRevision: question.revision });
    expect(saved.competencyCode).toBe("VACT-NN-TA");
  });
});
