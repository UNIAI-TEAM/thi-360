import { describe, expect, it } from "vitest";
import { correctAnswer, emptyAnswer, gradePreview, normalizeAnswer, type PreviewAnswer } from "../preview/grading";
import type { QuestionBody, QuestionDetail } from "../types";

function question(body: QuestionBody, points: number, children: QuestionDetail[] = []): QuestionDetail {
  return {
    id: `q-${body.type}`,
    code: "CH-TEST",
    type: body.type,
    status: "READY",
    contentPreview: "",
    content: "",
    subjectCode: "MATH",
    gradeCode: "G12",
    difficultyCode: "EASY",
    points,
    knowledge: [],
    taskTypeCode: null,
    tagCodes: [],
    hasSolution: false,
    version: 1,
    revision: 0,
    usedInExamCount: 0,
    sourceCode: null,
    createdBy: { id: "u", name: "U" },
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
    body,
    solution: null,
    commonMistakes: null,
    attachmentIds: [],
    children,
  };
}

const options = [
  { id: "a", content: "A", correct: true },
  { id: "b", content: "B", correct: true },
  { id: "c", content: "C", correct: false },
  { id: "d", content: "D", correct: false },
];

describe("cham thu: quy tac cham theo spec muc 8", () => {
  it("Mot dap an: dung P, sai 0, bo trong la blank", () => {
    const q = question({ type: "SINGLE_CHOICE", options: [options[0]!, options[2]!] }, 0.75);
    expect(gradePreview(q, { type: "SINGLE_CHOICE", selected: "a" })).toMatchObject({ state: "correct", earned: 0.75 });
    expect(gradePreview(q, { type: "SINGLE_CHOICE", selected: "c" })).toMatchObject({ state: "wrong", earned: 0 });
    expect(gradePreview(q, emptyAnswer(q))).toMatchObject({ state: "blank", earned: 0, max: 0.75 });
  });

  it("Nhieu dap an ALL_OR_NOTHING: thieu hoac thua deu 0", () => {
    const q = question({ type: "MULTIPLE_CHOICE", options, scoringRule: "ALL_OR_NOTHING" }, 1);
    const pick = (selected: string[]) => gradePreview(q, { type: "MULTIPLE_CHOICE", selected });
    expect(pick(["a", "b"])).toMatchObject({ state: "correct", earned: 1 });
    expect(pick(["a"])).toMatchObject({ state: "wrong", earned: 0 });
    expect(pick(["a", "b", "c"])).toMatchObject({ state: "wrong", earned: 0 });
  });

  it("Nhieu dap an PARTIAL: chon sai bat ky thi 0, con lai theo ty le dap an dung", () => {
    const q = question({ type: "MULTIPLE_CHOICE", options, scoringRule: "PARTIAL" }, 1);
    const pick = (selected: string[]) => gradePreview(q, { type: "MULTIPLE_CHOICE", selected });
    expect(pick(["a"])).toMatchObject({ state: "partial", earned: 0.5 });
    expect(pick(["a", "c"])).toMatchObject({ state: "wrong", earned: 0 });
    expect(pick(["a", "b"])).toMatchObject({ state: "correct", earned: 1 });
  });

  it("Tra loi ngan: chuan hoa khoang trang, NFC, hoa thuong; khong doi dau phay thanh dau cham", () => {
    const q = question({ type: "SHORT_ANSWER", answers: ["0,5", "Hà Nội"], inputHint: null }, 2);
    const grade = (text: string) => gradePreview(q, { type: "SHORT_ANSWER", text });
    expect(grade("  HÀ   nội ")).toMatchObject({ state: "correct", earned: 2 });
    expect(grade("Ha\u0300 No\u0302\u0323i").state).toBe("correct");
    expect(grade("0,5").state).toBe("correct");
    expect(grade("0.5").state).toBe("wrong");
    expect(grade("   ").state).toBe("blank");
    expect(normalizeAnswer("E\u0301")).toBe(normalizeAnswer("É"));
  });

  it("Dien khuyet: tong diem cac vi tri khop, moi vi tri theo dap an cua chinh no", () => {
    const q = question(
      {
        type: "CLOZE",
        passage: "[[b1]] và [[b2]]",
        blanks: [
          { id: "b1", answers: ["x", "X1"], points: 0.5 },
          { id: "b2", answers: ["y"], points: 1 },
        ],
      },
      1.5,
    );
    const result = gradePreview(q, { type: "CLOZE", values: { b1: "x1", b2: "x" } });
    expect(result).toMatchObject({ state: "partial", earned: 0.5, max: 1.5 });
    expect(result.parts.map((part) => part.state)).toEqual(["correct", "wrong"]);
    expect(gradePreview(q, { type: "CLOZE", values: { b1: "", b2: "" } }).state).toBe("blank");
    expect(gradePreview(q, correctAnswer(q))).toMatchObject({ state: "correct", earned: 1.5 });
  });

  it("Dung/Sai PER_STATEMENT: tong diem cac y dung, bo trong khong tinh la dung", () => {
    const q = question(
      {
        type: "TRUE_FALSE",
        scoringMethod: "PER_STATEMENT",
        scoreTable: [],
        statements: [
          { id: "s1", content: "", answer: true, points: 0.25 },
          { id: "s2", content: "", answer: false, points: 0.25 },
          { id: "s3", content: "", answer: true, points: 0.5 },
        ],
      },
      1,
    );
    const result = gradePreview(q, { type: "TRUE_FALSE", values: { s1: true, s2: true, s3: null } });
    expect(result).toMatchObject({ state: "partial", earned: 0.25 });
    expect(result.parts.map((part) => part.state)).toEqual(["correct", "wrong", "blank"]);
    expect(result.parts[0]).toMatchObject({ earned: 0.25, max: 0.25 });
  });

  it("Dung/Sai COUNT_TABLE: k y dung lay scoreTable[k-1], k = 0 thi 0", () => {
    const q = question(
      {
        type: "TRUE_FALSE",
        scoringMethod: "COUNT_TABLE",
        scoreTable: [0.1, 0.25, 0.5, 1],
        statements: ["s1", "s2", "s3", "s4"].map((id) => ({ id, content: "", answer: true, points: null })),
      },
      1,
    );
    const grade = (values: Record<string, boolean | null>) => gradePreview(q, { type: "TRUE_FALSE", values });
    expect(grade({ s1: true, s2: true, s3: true, s4: false })).toMatchObject({ earned: 0.5, state: "partial" });
    expect(grade({ s1: true, s2: false, s3: false, s4: false }).earned).toBe(0.1);
    expect(grade({ s1: false, s2: false, s3: false, s4: false })).toMatchObject({ earned: 0, state: "wrong" });
    expect(grade({ s1: true, s2: true, s3: true, s4: true })).toMatchObject({ earned: 1, state: "correct" });
    expect(grade({ s1: true, s2: null, s3: null, s4: null }).parts[0]).toMatchObject({ earned: null, max: null });
  });

  it("Ghep doi: tong diem cac cap dung, cap bo trong 0", () => {
    const q = question(
      {
        type: "MATCHING",
        left: [
          { id: "l1", content: "" },
          { id: "l2", content: "" },
          { id: "l3", content: "" },
        ],
        right: [
          { id: "r1", content: "" },
          { id: "r2", content: "" },
          { id: "r3", content: "" },
          { id: "r4", content: "" },
        ],
        pairs: [
          { leftId: "l1", rightId: "r1", points: 0.5 },
          { leftId: "l2", rightId: "r2", points: 0.5 },
          { leftId: "l3", rightId: "r2", points: 1 },
        ],
        reuseRight: true,
      },
      2,
    );
    const answer: PreviewAnswer = { type: "MATCHING", picks: { l1: "r1", l2: "r3", l3: null } };
    const result = gradePreview(q, answer);
    expect(result).toMatchObject({ state: "partial", earned: 0.5, max: 2 });
    expect(result.parts.map((part) => part.state)).toEqual(["correct", "wrong", "blank"]);
    expect(gradePreview(q, correctAnswer(q))).toMatchObject({ state: "correct", earned: 2 });
  });

  it("Bo cau hoi: tong diem cac cau con, moi cau con cham theo dang cua no; cau con Tu luan cham tay", () => {
    const single = question({ type: "SINGLE_CHOICE", options: [options[0]!, options[2]!] }, 1);
    const essay = question(
      { type: "ESSAY", gradingGuide: "Guide", rubric: [], sampleAnswer: "Mẫu", wordLimit: null },
      3,
    );
    const set = question({ type: "QUESTION_SET", passage: "", childIds: [single.id, essay.id] }, 4, [single, essay]);

    const answered = gradePreview(set, {
      type: "QUESTION_SET",
      children: { [single.id]: { type: "SINGLE_CHOICE", selected: "a" }, [essay.id]: { type: "ESSAY", text: "bài làm" } },
    });
    expect(answered).toMatchObject({ earned: 1, max: 4, hasManualPart: true, state: "partial" });
    expect(answered.children[single.id]!.state).toBe("correct");
    expect(answered.children[essay.id]).toMatchObject({ state: "manual", earned: 0, max: 3 });
    expect(gradePreview(set, emptyAnswer(set)).state).toBe("blank");
  });

  it("Bo cau hoi chi co phan Tu luan duoc dien: Cho cham, khong bao Sai (UniNote 8.5)", () => {
    const essay = question(
      { type: "ESSAY", gradingGuide: "Guide", rubric: [], sampleAnswer: null, wordLimit: null },
      1.5,
    );
    const set = question({ type: "QUESTION_SET", passage: "", childIds: [essay.id] }, 1.5, [essay]);
    const filled = gradePreview(set, { type: "QUESTION_SET", children: { [essay.id]: { type: "ESSAY", text: "bài làm" } } });
    expect(filled).toMatchObject({ state: "manual", earned: 0, max: 1.5, hasManualPart: true });

    // Cau con tu cham bo trong, cau Tu luan da dien: van la cho cham, khong phai sai.
    const single = question({ type: "SINGLE_CHOICE", options: [options[0]!, options[2]!] }, 1);
    const mixed = question({ type: "QUESTION_SET", passage: "", childIds: [single.id, essay.id] }, 2.5, [single, essay]);
    const essayOnly = gradePreview(mixed, {
      type: "QUESTION_SET",
      children: { [essay.id]: { type: "ESSAY", text: "bài làm" } },
    });
    expect(essayOnly.state).toBe("manual");

    // Co cau con tu cham bi sai thi van la Sai.
    const wrong = gradePreview(mixed, {
      type: "QUESTION_SET",
      children: { [single.id]: { type: "SINGLE_CHOICE", selected: "c" }, [essay.id]: { type: "ESSAY", text: "bài làm" } },
    });
    expect(wrong).toMatchObject({ state: "wrong", earned: 0 });

    // Khong dien gi: Bo trong.
    expect(gradePreview(mixed, emptyAnswer(mixed)).state).toBe("blank");
  });

  it("Tu luan: khong co diem tu dong, bo trong la blank", () => {
    const q = question({ type: "ESSAY", gradingGuide: "G", rubric: [], sampleAnswer: null, wordLimit: 500 }, 5);
    expect(gradePreview(q, { type: "ESSAY", text: "một hai ba" })).toMatchObject({
      state: "manual",
      earned: 0,
      max: 5,
      hasManualPart: true,
    });
    expect(gradePreview(q, emptyAnswer(q)).state).toBe("blank");
  });
});
