import { describe, expect, it } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import questionsFixture from "@/features/grading/mock/questions.json";
import {
  evaluateReadiness,
  firstReadinessIssue,
  READINESS_RULES,
  readinessFixHref,
  type ReadinessRule,
} from "../readiness";
import type { QuestionDetail } from "../types";

const reference = referenceFixture as ContentReference;
const questions = (questionsFixture as { questions: QuestionDetail[] }).questions;

function sample(type: QuestionDetail["type"]): QuestionDetail {
  const found = questions.find((question) => question.type === type);
  if (!found) throw new Error(`no sample ${type}`);
  return structuredClone(found);
}

function failed(question: QuestionDetail): ReadinessRule[] {
  return evaluateReadiness(question, { reference })
    .rules.filter((rule) => !rule.passed)
    .map((rule) => rule.rule);
}

describe("evaluateReadiness (UniNote PBI-03 muc 7.2)", () => {
  it("tra bay dieu kien theo dung thu tu, cau mau day du thi dat het", () => {
    const result = evaluateReadiness(sample("SINGLE_CHOICE"), { reference });
    expect(result.rules.map((rule) => rule.rule)).toEqual([...READINESS_RULES]);
    expect(result.ready).toBe(true);
    expect(result.failedCount).toBe(0);
    expect(firstReadinessIssue(result)).toBeNull();
  });

  it("moi cau Sẵn sàng trong du lieu mau deu dat du dieu kien", () => {
    for (const question of questions.filter((item) => item.status === "READY")) {
      expect(evaluateReadiness(question, { reference }).ready, question.code).toBe(true);
    }
  });

  it("thieu de bai va phuong an rong: dieu kien noi dung chua dat, chi ro vi tri", () => {
    const question = sample("SINGLE_CHOICE");
    question.content = "  ";
    if (question.body.type === "SINGLE_CHOICE") question.body.options[1]!.content = "";
    const result = evaluateReadiness(question, { reference });
    const content = result.rules.find((rule) => rule.rule === "CONTENT")!;
    expect(content.passed).toBe(false);
    expect(content.issues).toEqual([
      { code: "CONTENT_EMPTY", field: "content" },
      { code: "OPTION_EMPTY", field: "content", position: 2 },
    ]);
  });

  it("trac nghiem 1 dap an: khong co hoac nhieu dap an dung deu khong hop le", () => {
    const none = sample("SINGLE_CHOICE");
    if (none.body.type === "SINGLE_CHOICE") none.body.options.forEach((option) => (option.correct = false));
    expect(evaluateReadiness(none).rules[1]!.issues[0]!.code).toBe("NO_CORRECT_OPTION");

    const many = sample("SINGLE_CHOICE");
    if (many.body.type === "SINGLE_CHOICE") many.body.options.forEach((option) => (option.correct = true));
    expect(evaluateReadiness(many).rules[1]!.issues[0]!.code).toBe("SINGLE_CHOICE_MANY_CORRECT");
  });

  it("thieu khoi, do kho la dieu kien; thieu kien thuc chinh chi la canh bao (D20)", () => {
    const question = sample("MULTIPLE_CHOICE");
    question.gradeCode = "";
    question.difficultyCode = "";
    question.knowledge = question.knowledge.map((ref) => ({ ...ref, primary: false }));
    const classification = evaluateReadiness(question, { reference }).rules.find((r) => r.rule === "CLASSIFICATION")!;
    expect(classification.issues.map((issue) => issue.code)).toEqual(["GRADE_MISSING", "DIFFICULTY_MISSING"]);
    const noPrimary = evaluateReadiness({ ...question, gradeCode: "GRADE_12", difficultyCode: "APPLICATION" }, { reference });
    expect(noPrimary.warnings).toEqual([{ code: "PRIMARY_KNOWLEDGE_MISSING", field: "knowledge" }]);
    expect(noPrimary.rules.find((r) => r.rule === "CLASSIFICATION")!.passed).toBe(true);
  });

  it("gia tri phan loai ngoai danh muc hoac kien thuc mon khac", () => {
    const question = sample("SINGLE_CHOICE");
    question.difficultyCode = "VERY_HARD";
    question.tagCodes = ["NOPE"];
    const physics = questions.find((item) => item.subjectCode === "PHYSICS")!;
    question.knowledge = [{ ...physics.knowledge[0]!, primary: true }];
    expect(failed(question)).toEqual(["CATALOG_VALUES"]);
    const issues = evaluateReadiness(question, { reference }).rules.find((r) => r.rule === "CATALOG_VALUES")!.issues;
    expect(issues.map((issue) => [issue.code, issue.value])).toEqual([
      ["DIFFICULTY_INVALID", "VERY_HARD"],
      ["TAG_INVALID", "NOPE"],
      ["KNOWLEDGE_OTHER_SUBJECT", physics.knowledge[0]!.code],
    ]);
    // Khong truyen danh muc thi bo qua dieu kien 4.
    expect(evaluateReadiness(question).rules.find((r) => r.rule === "CATALOG_VALUES")!.passed).toBe(true);
  });

  it("o trong chua co dap an va cau con thieu dap an", () => {
    const fill = sample("SHORT_ANSWER");
    if (fill.body.type === "SHORT_ANSWER") fill.body.answers = [" "];
    expect(failed(fill)).toEqual(["ANSWER"]);

    const cloze: QuestionDetail = {
      ...fill,
      type: "CLOZE",
      body: { type: "CLOZE", passage: "Dien [[b1]] vao cho trong", blanks: [{ id: "b1", answers: [" "], points: 1 }] },
    };
    expect(failed(cloze)).toEqual(["NO_GAPS"]);

    const set = sample("QUESTION_SET");
    const child = set.children[2]!;
    if (child.body.type === "SINGLE_CHOICE") child.body.options.forEach((option) => (option.correct = false));
    const result = evaluateReadiness(set, { reference });
    const answer = result.rules.find((rule) => rule.rule === "ANSWER")!;
    expect(answer.issues).toEqual([{ code: "NO_CORRECT_OPTION", field: "answers", child: 3 }]);
  });

  it("tai nguyen loi va cau chum long cau chum", () => {
    const set = sample("QUESTION_SET");
    set.children[0] = { ...set.children[0]!, type: "QUESTION_SET", body: { type: "QUESTION_SET", passage: "x", childIds: [] } };
    const result = evaluateReadiness(set, { reference, unavailableResources: [{ name: "hinh-2.png", child: 2 }] });
    expect(result.rules.find((rule) => rule.rule === "RESOURCES")!.issues).toEqual([
      { code: "RESOURCE_UNAVAILABLE", field: "content", value: "hinh-2.png", child: 2 },
    ]);
    expect(result.rules.find((rule) => rule.rule === "STRUCTURE")!.issues[0]).toMatchObject({ code: "NESTED_SET", child: 1 });
    expect(result.ready).toBe(false);
  });

  it("lien ket sua tro toi khoi tuong ung tren trang soan", () => {
    expect(readinessFixHref("q-0001", "knowledge")).toBe("/questions/q-0001/edit#question-knowledge");
  });

  it("Đúng/Sai cộng điểm từng ý: tổng điểm các ý phải bằng điểm của câu, không đòi bảng điểm", () => {
    const question = sample("TRUE_FALSE");
    question.points = 1;
    question.body = {
      type: "TRUE_FALSE",
      scoringMethod: "PER_STATEMENT",
      scoreTable: [],
      statements: [
        { id: "s1", content: "a", answer: true, points: 0.5 },
        { id: "s2", content: "b", answer: false, points: 0.5 },
      ],
    };
    expect(failed(question)).toEqual([]);
    question.body.statements[1]!.points = 0.25;
    expect(evaluateReadiness(question, { reference }).rules[1]!.issues.map((issue) => issue.code)).toEqual([
      "STATEMENT_POINTS_MISMATCH",
    ]);
  });

  it("Điền khuyết: mỗi dấu [[id]] có đúng một ô và ngược lại", () => {
    const question = sample("SHORT_ANSWER");
    question.type = "CLOZE";
    question.content = "";
    question.points = 2;
    question.body = {
      type: "CLOZE",
      passage: "A [[b1]] B [[b2]]",
      blanks: [
        { id: "b1", answers: ["x"], points: 1 },
        { id: "b2", answers: ["y"], points: 1 },
      ],
    };
    question.content = "A [[b1]] B [[b2]]";
    const codesOf = () => evaluateReadiness(question, { reference }).rules[1]!.issues.map((issue) => issue.code);
    expect(codesOf()).toEqual([]);
    question.body.passage = "A [[b1]] B [[b2]] C [[b3]]";
    expect(codesOf()).toEqual(["BLANK_MARKER_MISMATCH"]);
    question.body.passage = "A [[b1]] B [[b1]]";
    expect(codesOf()).toEqual(["BLANK_MARKER_MISMATCH"]);
  });

  it("Ghép đôi: cặp trỏ tới mục có thật, lựa chọn dùng một lần khi reuseRight = false, tổng điểm cặp bằng điểm câu", () => {
    const question = sample("MATCHING");
    const codesOf = () => evaluateReadiness(question, { reference }).rules[1]!.issues.map((issue) => issue.code);
    expect(codesOf()).toEqual([]);
    if (question.body.type !== "MATCHING") throw new Error("not matching");
    const body = question.body;
    body.pairs[1]!.rightId = body.pairs[0]!.rightId;
    expect(codesOf()).toEqual(["PAIR_INVALID"]);
    body.reuseRight = true;
    expect(codesOf()).toEqual([]);
    body.pairs[2]!.rightId = "missing";
    expect(codesOf()).toEqual(["PAIR_INVALID"]);
    body.pairs[2]!.rightId = body.pairs[0]!.rightId;
    body.pairs[2]!.points = 0.1;
    expect(codesOf()).toEqual(["PAIR_POINTS_MISMATCH"]);
  });
});
