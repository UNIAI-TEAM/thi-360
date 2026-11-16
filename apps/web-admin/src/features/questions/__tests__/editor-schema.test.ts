import { describe, expect, it } from "vitest";
import {
  emptyChild,
  emptyEditorValues,
  parsePoints,
  questionSetPoints,
  removeBlankMarker,
  toEditorValues,
  toSaveRequest,
  type QuestionEditorValues,
} from "../editor-form";
import { questionEditorSchema, validateQuestionEditor } from "../editor-schema";
import { fixtureAll } from "./api-fixtures";

const KNOWLEDGE = [{ nodeId: "k-1", code: "MAP-1", name: "Ham so bac nhat", primary: true }];

function classified(values: QuestionEditorValues): QuestionEditorValues {
  return {
    ...values,
    subjectCode: "MATH",
    gradeCode: "G10",
    difficultyCode: "EASY",
    knowledge: KNOWLEDGE,
    content: "Đề bài",
  };
}

function singleChoice(correct: number[]): QuestionEditorValues {
  const values = classified(emptyEditorValues("SINGLE_CHOICE"));
  values.body.options = values.body.options.map((option, index) => ({
    ...option,
    content: `Phương án ${index + 1}`,
    correct: correct.includes(index),
  }));
  return values;
}

const messages = (values: QuestionEditorValues) => validateQuestionEditor(values).map((issue) => issue.message);

describe("lược đồ biểu mẫu soạn câu hỏi", () => {
  it("biểu mẫu trống báo đủ trường bắt buộc", () => {
    expect(messages(emptyEditorValues("SINGLE_CHOICE"))).toEqual(
      expect.arrayContaining([
        "subjectRequired",
        "gradeRequired",
        "difficultyRequired",
        "knowledgeRequired",
        "contentRequired",
        "optionRequired",
        "singleCorrectRequired",
      ]),
    );
  });

  it("một đáp án: cần đúng một phương án đúng", () => {
    expect(messages(singleChoice([0]))).toEqual([]);
    expect(messages(singleChoice([]))).toContain("singleCorrectRequired");
    expect(messages(singleChoice([0, 2]))).toContain("singleCorrectRequired");
  });

  it("ô HTML không còn chữ nào là trống như backend isBlankHtml, ô chỉ có ảnh thì không", () => {
    const base = singleChoice([0]);
    for (const empty of ["<p></p><p></p>", "<p> </p>", "<p><br></p>"]) {
      expect(messages({ ...base, content: empty })).toEqual(["contentRequired"]);
      expect(
        messages({ ...base, body: { ...base.body, options: base.body.options.map((o, i) => (i === 1 ? { ...o, content: empty } : o)) } }),
      ).toEqual(["optionRequired"]);
    }
    const imageOnly = '<p><img src="/api/v1/admin/question-assets/9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" data-asset-id="9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"></p>';
    expect(messages({ ...base, content: imageOnly })).toEqual([]);
  });

  it("nhiều đáp án: cần ít nhất một phương án đúng và ít nhất 2 phương án", () => {
    const values = { ...singleChoice([0, 1]), type: "MULTIPLE_CHOICE" as const };
    expect(messages(values)).toEqual([]);
    expect(messages({ ...values, body: { ...values.body, options: values.body.options.map((o) => ({ ...o, correct: false })) } })).toContain(
      "multipleCorrectRequired",
    );
    expect(messages({ ...values, body: { ...values.body, options: values.body.options.slice(0, 1) } })).toContain("optionsMin");
  });

  it("điểm phải là số lớn hơn 0, nhận cả dấu phẩy", () => {
    expect(parsePoints("1,5")).toBe(1.5);
    expect(parsePoints("2.25")).toBe(2.25);
    expect(parsePoints("abc")).toBeNaN();
    expect(messages({ ...singleChoice([0]), points: "0" })).toContain("pointsInvalid");
    expect(messages({ ...singleChoice([0]), points: "-1" })).toContain("pointsInvalid");
    expect(messages({ ...singleChoice([0]), points: "0,5" })).toEqual([]);
  });

  it("đúng/sai: mỗi ý phải chọn Đúng hoặc Sai", () => {
    const values = classified(emptyEditorValues("TRUE_FALSE"));
    values.body.statements = values.body.statements.map((statement) => ({ ...statement, content: "Ý" }));
    const issues = validateQuestionEditor(values);
    expect(issues.filter((issue) => issue.message === "statementAnswerRequired")).toHaveLength(2);
    expect(issues[issues.length - 1]?.path).toEqual(["body", "statements", 1, "answer"]);
    values.body.statements = values.body.statements.map((statement) => ({ ...statement, answer: "TRUE" }));
    expect(messages(values)).toEqual([]);
  });

  it("điền khuyết: số chỗ trống ___ phải bằng số ô đáp án", () => {
    const values = classified(emptyEditorValues("CLOZE"));
    values.content = "Thủ đô của Việt Nam là ___ .";
    expect(messages(values)).toContain("blanksMin");
    values.body.blanks = [{ id: "b1", answers: [{ id: "a1", value: "Hà Nội" }], points: "1" }];
    expect(messages(values)).toEqual([]);
    values.content = "Không có chỗ trống.";
    expect(messages(values)).toContain("blankCountMismatch");
    values.content = "___ và ___";
    values.body.blanks.push({ id: "b2", answers: [{ id: "a2", value: " " }], points: "1" });
    expect(messages(values)).toEqual(["blankAnswerRequired"]);
  });

  it("ghép đôi: mục trái cần nội dung và lựa chọn đúng, lựa chọn không được trống", () => {
    const values = classified(emptyEditorValues("MATCHING"));
    expect(messages(values)).toEqual(expect.arrayContaining(["matchLeftRequired", "matchRightRequired"]));
    values.body.left = values.body.left.map((item, index) => ({ ...item, content: `Mục ${index}` }));
    values.body.right = values.body.right.map((item, index) => ({ ...item, content: `Lựa chọn ${index}` }));
    expect(messages(values)).toEqual([]);
    values.body.right = [...values.body.right, { id: "spare", content: "" }];
    expect(messages(values)).toEqual(["matchRightRequired"]);
  });

  it("câu chùm: cần đoạn dẫn và ít nhất 2 câu con hợp lệ", () => {
    const values = classified(emptyEditorValues("QUESTION_SET"));
    values.content = "";
    expect(messages(values)).toEqual(expect.arrayContaining(["passageRequired", "childrenMin"]));
    expect(messages(values)).not.toContain("pointsInvalid");

    values.content = "Đoạn dẫn";
    const first = emptyChild("SINGLE_CHOICE");
    first.content = "Câu 1";
    first.body.options = first.body.options.map((option, index) => ({ ...option, content: `P${index}`, correct: index === 0 }));
    values.children = [first];
    expect(messages(values)).toEqual(["childrenMin"]);

    const second = emptyChild("TRUE_FALSE");
    values.children = [first, second];
    const issues = validateQuestionEditor(values);
    expect(issues.map((issue) => issue.path[0])).toEqual(expect.arrayContaining(["children"]));
    expect(issues.find((issue) => issue.message === "childContentRequired")?.path).toEqual(["children", 1, "content"]);
  });

  it("zod resolver đưa lỗi về đúng đường dẫn với khóa dịch", () => {
    const result = questionEditorSchema.safeParse(singleChoice([]));
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ["body", "options"], message: "singleCorrectRequired" }),
      );
    }
  });
});

describe("chỗ trống của câu Điền đáp án", () => {
  it("xóa đúng chỗ trống thứ n", () => {
    expect(removeBlankMarker("a ___ b ___ c", 1)).toBe("a ___ b  c");
    expect(removeBlankMarker("a ___ b", 3)).toBe("a ___ b");
  });
});

describe("chuyển đổi giữa câu hỏi và biểu mẫu", () => {
  it("mọi câu mẫu đi qua biểu mẫu rồi trở lại giữ nguyên phần thân, phân loại và điểm", () => {
    for (const question of fixtureAll()) {
      const values = toEditorValues(question);
      const request = toSaveRequest(values);
      expect(request.type, question.code).toBe(question.type);
      expect(request.subjectCode).toBe(question.subjectCode);
      expect(request.tagCodes).toEqual(question.tagCodes);
      if (question.type === "QUESTION_SET") {
        expect(request.children.map((child) => child.id)).toEqual(question.children.map((child) => child.id));
        expect(request.children.map((child) => child.body)).toEqual(question.children.map((child) => child.body));
        expect(request.points).toBe(question.children.reduce((sum, child) => sum + child.points, 0));
        expect(request.solution).toBeNull();
      } else {
        expect(request.body, question.code).toEqual(question.body);
        expect(request.points).toBe(question.points);
      }
    }
  });

  it("tổng điểm câu chùm bỏ qua điểm chưa hợp lệ", () => {
    expect(questionSetPoints([{ points: "1,5" }, { points: "2" }, { points: "x" }])).toBe(3.5);
  });
});
