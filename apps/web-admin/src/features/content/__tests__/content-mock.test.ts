import { describe, expect, it } from "vitest";
import { indexKnowledge, filterKnowledgeTree, normalizePrimary } from "@/features/content/knowledge-tree";
import { getContentReference } from "@/features/content/api";
import { listQuestions, getQuestion } from "@/features/questions/api";
import { getExam, listExams } from "@/features/exams/api";
import { validateStructure } from "@/features/exams/matrix";
import { createMockStore, MockApiError, mockRequest, paginate, readMockScenario } from "@/lib/mock/content-mock";

describe("lớp dữ liệu mẫu", () => {
  it("đọc kịch bản trên URL, mặc định là data", () => {
    expect(readMockScenario("")).toBe("data");
    expect(readMockScenario("?mockState=empty")).toBe("empty");
    expect(readMockScenario("?mockState=khac")).toBe("data");
  });

  it("trả bản sao để màn hình không sửa được kho", async () => {
    const source = { items: [1, 2] };
    const copy = await mockRequest(() => source);
    copy.items.push(3);
    expect(source.items).toEqual([1, 2]);
  });

  it("kho trong bộ nhớ báo 404 khi không có bản ghi", () => {
    const store = createMockStore([{ id: "a" }]);
    expect(() => store.require("b")).toThrow(MockApiError);
    store.insert({ id: "b" });
    expect(store.all().map((item) => item.id)).toEqual(["b", "a"]);
    store.reset();
    expect(store.all()).toHaveLength(1);
  });

  it("cắt trang như PageResponse", () => {
    const page = paginate([1, 2, 3, 4, 5], 1, 2);
    expect(page).toMatchObject({ content: [3, 4], page: 1, totalElements: 5, totalPages: 3 });
  });
});

describe("dữ liệu mẫu dùng chung", () => {
  it("mọi kiến thức gắn với câu hỏi có trong cây, mỗi câu có tối đa một kiến thức chính", async () => {
    const reference = await getContentReference();
    const index = indexKnowledge(reference.knowledgeTree);
    const page = await listQuestions({ size: 100 });
    expect(page.totalElements).toBeGreaterThan(20);
    for (const question of page.content) {
      for (const ref of question.knowledge) {
        expect(index.get(ref.nodeId)?.node.level, ref.code).toBe("KNOWLEDGE");
      }
      expect(question.knowledge.filter((ref) => ref.primary).length).toBeLessThanOrEqual(1);
    }
  });

  it("lọc câu hỏi theo môn và trạng thái", async () => {
    const page = await listQuestions({ subjectCode: "MATH", status: "READY", size: 100 });
    expect(page.content.length).toBeGreaterThan(0);
    expect(page.content.every((q) => q.subjectCode === "MATH" && q.status === "READY")).toBe(true);
    const detail = await getQuestion(page.content[0]!.id);
    expect(detail.body.type).toBe(detail.type);
  });

  it("lọc cây theo chữ tìm giữ lại nhánh chứa nút khớp", async () => {
    const reference = await getContentReference();
    const tree = filterKnowledgeTree(reference.knowledgeTree, { query: "1D4-2-3" });
    const index = indexKnowledge(tree);
    expect([...index.values()].filter((entry) => entry.node.level === "KNOWLEDGE").map((e) => e.node.code)).toEqual([
      "1D4-2-3",
    ]);
  });

  it("giữ đúng một kiến thức chính", () => {
    const refs = normalizePrimary([
      { nodeId: "a", code: "A", name: "A", primary: false },
      { nodeId: "b", code: "B", name: "B", primary: false },
    ]);
    expect(refs.map((ref) => ref.primary)).toEqual([true, false]);
  });

  it("bài thi mẫu: số câu của phần bằng tổng các môn, tổ hợp 17-17-16", async () => {
    const list = await listExams({});
    expect(list.content.map((exam) => exam.code)).toContain("HSA-F-001");
    const exam = await getExam("ex-hsa-f-001");
    // Môn của SUBJECT_CHOICE không có số câu cố định (D25): tổng theo `countsByPick`, kiểm ở check-exam.test.ts.
    expect(validateStructure(exam.parts, { grades: [], difficulties: [] })).toEqual([]);
    const choice = exam.parts.find((part) => part.mode === "CHOICE");
    const science = choice?.options.find((o) => o.kind === "SUBJECT_CHOICE");
    expect(science).toMatchObject({ code: "SCIENCE", chooseSubjects: 3, countsByPick: [17, 17, 16] });
    expect(science?.sections).toHaveLength(5);
    expect(exam.totalQuestions).toBe(150);
  });
});
