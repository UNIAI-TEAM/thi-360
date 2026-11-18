import { describe, expect, it } from "vitest";
import { describeStructureChanges } from "../components/exam-structure-changes";
import { addBlock, newBlock, removeBlock, updateBlock, updatePart, updateSection } from "../components/exam-structure-draft";
import type { ExamPart, ExamSection, QuestionBlock } from "../types";

const block = (id: string, over: Partial<QuestionBlock> = {}): QuestionBlock => ({ ...newBlock(`Nhóm ${id}`), id, questionCount: 10, ...over });

const section: ExamSection = {
  id: "s1",
  subjectCode: "MATH",
  questionCount: 20,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block("b1"), block("b2")],
};

const base: ExamPart[] = [
  {
    id: "p1",
    order: 1,
    name: "Phần 1",
    durationMinutes: 60,
    questionCount: 20,
    questionCountOnPaper: 20,
    questionCountRequired: 20,
    participation: { mode: "ALL", choose: null },
    mode: "FIXED",
    sections: [section],
    options: [],
  },
];

const subjectName = (code: string) => (code === "MATH" ? "Toán" : code);
const location = { partId: "p1", optionId: null, sectionId: "s1" };

describe("describeStructureChanges", () => {
  it("bản nháp giống bản đã lưu: không có thay đổi", () => {
    expect(describeStructureChanges(base, structuredClone(base), subjectName)).toEqual([]);
  });

  it("báo số câu nhóm, nhóm thêm, nhóm xóa và đổi chỉ tiêu môn kèm tên môn", () => {
    let draft = updateBlock(base, "b1", (item) => ({ ...item, questionCount: 14 }));
    draft = removeBlock(draft, "b2");
    const added = block("b3", { name: "Nhóm mới" });
    draft = addBlock(draft, location, added);
    draft = updateSection(draft, location, (item) => ({ ...item, answerQuotas: { unit: "COUNT", items: [] } }));
    expect(describeStructureChanges(base, draft, subjectName)).toEqual([
      { kind: "sectionSettings", subject: "Toán" },
      { kind: "blockCount", name: "Nhóm b1", from: 10, to: 14 },
      { kind: "blockAdded", subject: "Toán", name: "Nhóm mới" },
      { kind: "blockRemoved", subject: "Toán", name: "Nhóm b2" },
    ]);
  });

  it("báo số của phần với giá trị cũ và mới, và đổi tên phần", () => {
    const draft = updatePart(base, "p1", (item) => ({ ...item, name: "Phần Toán", durationMinutes: 45, maxPoints: 50 }));
    expect(describeStructureChanges(base, draft, subjectName)).toEqual([
      { kind: "partRenamed", from: "Phần 1", to: "Phần Toán" },
      { kind: "partNumber", part: "Phần Toán", field: "durationMinutes", from: 60, to: 45 },
      { kind: "partNumber", part: "Phần Toán", field: "maxPoints", from: null, to: 50 },
    ]);
  });
});
