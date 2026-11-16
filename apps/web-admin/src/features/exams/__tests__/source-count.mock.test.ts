import { afterEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { countAvailableQuestions, countSubjectAvailableQuestions } from "../api";
import { candidatesFor, matchesScope, queryOfBlock } from "../mock/source-count";
import { examStore } from "../mock/store";
import type { AvailableQuestionsQuery, ExamSection, QuestionBlock } from "../types";
import { pinInMemoryBackend } from "./in-memory-backend";

/**
 * Đếm câu nguồn của nhánh mẫu (`mock/source-count.ts`, `api.mock.ts`): tính trên kho câu hỏi mẫu trong bộ nhớ. Ở nhánh
 * thật máy chủ đếm, client chỉ gửi lọc và hiển thị (xem `structure-api.test.ts`), nên tệp này bị xóa cùng `mock/` khi
 * QA bỏ lớp mẫu.
 */
pinInMemoryBackend();

afterEach(() => {
  examStore.reset();
  questionStore.reset();
});

const MATH_SINGLE: AvailableQuestionsQuery = {
  subjectCode: "MATH",
  questionType: "SINGLE_CHOICE",
  answerTypes: [],
  childAnswerType: null,
  knowledgeScope: null,
};

describe("phạm vi MAPID của nhóm: lọc câu nguồn (D21, D23)", () => {
  const section = (knowledgeScope: ExamSection["knowledgeScope"]): ExamSection => ({
    id: "s-math",
    subjectCode: "MATH",
    questionCount: 50,
    knowledgeScope,
    answerQuotas: null,
    matrix: null,
    groupOrder: "SHUFFLE",
    choiceGroup: null,
    blocks: [],
  });
  const singles = (knowledgeScope: QuestionBlock["knowledgeScope"]): QuestionBlock => ({
    id: "b-1",
    name: "Câu đơn",
    questionType: "SINGLE_CHOICE",
    questionCount: 5,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope,
    matrix: null,
    taskTypeCode: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
  } as QuestionBlock);
  const codesOf = (target: QuestionBlock, within: ExamSection) =>
    candidatesFor(queryOfBlock(target, within))
      .map((question) => question.code)
      .sort();

  it("nhóm dùng phạm vi của môn, phạm vi riêng không giao; nút cha khớp nút con", () => {
    // CH-000001 gắn kn-1D4-1-1 (con của kn-1D4-1, con của kn-1D4); CH-000010 gắn kn-0D1-2-1 và kn-0D1-1-1.
    const math = section({ nodeIds: ["kn-1D4"], match: "ANY" });
    expect(codesOf(singles(null), math)).toContain("CH-000001");
    expect(codesOf(singles(null), math)).not.toContain("CH-000010");
    const own = singles({ nodeIds: ["kn-0D1"], match: "ANY" });
    expect(codesOf(own, math)).toContain("CH-000010");
    expect(codesOf(own, math)).not.toContain("CH-000001");
    // Cả hai cùng trống là không giới hạn MAPID: câu chưa gắn nút nào cũng khớp.
    expect(codesOf(singles(null), section(null))).toEqual(expect.arrayContaining(["CH-000001", "CH-000010", "CH-008008"]));
    // Nút cha đã chọn khớp nút con; nút con đã chọn không khớp câu chỉ gắn nút cha của nó.
    const byCode = (code: string) => questionStore.all().find((question) => question.code === code)!;
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4"], match: "ANY" })).toBe(true);
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4-1"], match: "ANY" })).toBe(true);
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4-3"], match: "ANY" })).toBe(false);
    expect(matchesScope(byCode("CH-000010"), { nodeIds: ["kn-0D1-1-1", "kn-0D1-2-1"], match: "ALL" })).toBe(true);
    expect(matchesScope(byCode("CH-000010"), { nodeIds: ["kn-0D1-1-1", "kn-1D4"], match: "ALL" })).toBe(false);
  });
});

describe("đếm câu nguồn của khối (api.mock)", () => {
  it("đếm câu Sẵn sàng theo môn và dạng, thu hẹp khi chọn kiến thức", async () => {
    const all = await countAvailableQuestions({
      ...MATH_SINGLE,
      knowledgeScope: { nodeIds: [], match: "ANY" },
    });
    expect(all.total).toBeGreaterThan(0);
    expect(all.cells.reduce((total, cell) => total + cell.value, 0)).toBe(all.total);
    const none = await countAvailableQuestions({
      ...MATH_SINGLE,
      knowledgeScope: { nodeIds: ["khong-co"], match: "ANY" },
    });
    expect(none.total).toBe(0);
  });

  it("countSubjectAvailableQuestions dedups across groups", async () => {
    // Hai nhóm cùng môn Toán có nguồn giao nhau: CH-000001 gắn kn-1D4-1-1 nằm trong cả hai phạm vi (nút cha khớp nút con, D23).
    const group = (nodeIds: string[]) => ({ ...MATH_SINGLE, knowledgeScope: { nodeIds, match: "ANY" as const } });
    const first = await countAvailableQuestions(group(["kn-1D4"]));
    const second = await countAvailableQuestions(group(["kn-1D4-1"]));
    expect(first.total).toBeGreaterThan(0);
    expect(second.total).toBeGreaterThan(0);
    const union = await countSubjectAvailableQuestions({
      subjectCode: "MATH",
      groups: [group(["kn-1D4"]), group(["kn-1D4-1"])],
    });
    expect(union.total).toBeLessThan(first.total + second.total);
    expect(union.total).toBe(first.total);
    // Nhóm lấy theo chỉ tiêu dạng trả lời của môn: hợp các dạng, byAnswerType cộng đúng tổng.
    const byQuota = await countSubjectAvailableQuestions({
      subjectCode: "MATH",
      groups: [{ ...MATH_SINGLE, questionType: null, answerTypes: ["SINGLE_CHOICE", "SHORT_ANSWER"], knowledgeScope: null }],
    });
    expect(byQuota.byAnswerType.map((item) => item.answerType).sort()).toEqual(["SHORT_ANSWER", "SINGLE_CHOICE"]);
    expect(byQuota.byAnswerType.reduce((total, item) => total + item.value, 0)).toBe(byQuota.total);
  });
});
