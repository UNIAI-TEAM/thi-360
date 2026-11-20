import { describe, expect, it } from "vitest";
import type { BlockMatrix } from "../types";
import { validateMatrix } from "../matrix";
import { listSubjectFormPairs, subjectFormFor } from "../subject-forms/registry";
import {
  applyNewTotal,
  conditionSummary,
  convertReferenceUnit,
  dimensionCell,
  dimensionDeclared,
  enforcementModeOf,
  gradeVisible,
  keepOldTotal,
  lockReasonsOf,
  modePatch,
  pruneMatrix,
  readRow,
  rowFigures,
  rowLocks,
  rowsLockedOf,
  setDimensionCell,
  writeRow,
  type RowContext,
} from "../subject-forms/row-list-model";
import { block, section, setBlock, standardFixtureTemplates } from "./subject-form-builders";

const AXES = { grades: ["GRADE_10", "GRADE_11", "GRADE_12"], difficulties: ["NB", "TH", "VD", "VDC"] };
const context = (over: Partial<RowContext> = {}): RowContext => ({ scope: "SUBJECT", reviewMode: false, template: null, ...over });
const entryOf = (kind: string, subject: string) => subjectFormFor(kind, subject)!;

describe("row-list-model: khóa của dòng (R-7)", () => {
  it("khóa dòng chỉ ở bài SUBJECT từ mẫu fixedBlockCounts; PART và bài ôn không khóa", () => {
    const template = { fixedBlockCounts: true, omittedLocked: null, parts: [] };
    expect(rowsLockedOf(context({ template }))).toBe(true);
    expect(rowsLockedOf(context({ template, scope: "PART" }))).toBe(false);
    expect(rowsLockedOf(context({ template, reviewMode: true }))).toBe(false);
    expect(rowsLockedOf(context())).toBe(false);
  });

  it("mỗi loại khóa một lý do riêng; nhóm tự luận lược không khóa thì không có lý do", () => {
    const essay = block("e", { questionType: "ESSAY", omitted: true, templateGroupCode: "W" });
    const templateOf = (omittedLocked: boolean) => ({
      fixedBlockCounts: false,
      omittedLocked,
      parts: [{ sections: [{ omittedBlocks: [{ templateGroupCode: "W", name: "W", questionCount: 1 }], blocks: [{ templateGroupCode: "W", name: "W" }] }] }] as never,
    });
    expect(lockReasonsOf(rowLocks(essay, context({ template: templateOf(true) })))).toEqual(["omitted"]);
    expect(lockReasonsOf(rowLocks(essay, context({ template: templateOf(false) })))).toEqual([]);
    const typed = block("t", { answerTypeLocked: true });
    expect(lockReasonsOf(rowLocks(typed, context({ template: { fixedBlockCounts: true, omittedLocked: null, parts: [] } })))).toEqual(["rowCounts", "answerType"]);
  });

  it("lockedFieldsUnchangedInPayload: sửa tên hay điều kiện một dòng không đụng số lượng, dạng, khóa và dòng khác", () => {
    const locked = block("a", { name: "A", questionCount: 25, answerTypeLocked: true, templateGroupCode: "G_A", shuffleEditable: true });
    const other = setBlock("b", 2, 5, { templateGroupCode: "G_B" });
    const original = section("s", "TOAN", [locked, other]);
    const written = writeRow(original, "a", { name: "A mới", tagCodes: ["T1"] });
    expect(readRow(written, "a")).toEqual({ ...locked, name: "A mới", tagCodes: ["T1"] });
    expect(readRow(written, "b")).toBe(other);
    expect(written.blocks.map((item) => item.id)).toEqual(["a", "b"]);
    expect({ ...written, blocks: [] }).toEqual({ ...original, blocks: [] });
    // Patch rỗng và id lạ không đổi gì.
    expect(writeRow(original, "a", {})).toEqual(original);
    expect(writeRow(original, "zzz", { name: "x" })).toBe(original);
  });

  it("đổi Cách lấy đúng quy tắc màn nhóm: đơn → chùm 1 × tổng; chùm → đơn giữ số câu, bỏ điều kiện từng chùm", () => {
    const single = block("a", { questionCount: 10 });
    const toSet = writeRow(section("s", "VAN", [single]), "a", modePatch(single, "set")).blocks[0]!;
    expect(toSet).toMatchObject({ questionType: "QUESTION_SET", setCount: 1, setSize: 10, questionCount: 10 });
    const withSlots = setBlock("b", 2, 5, { slots: [{ index: 1, tagCodes: ["T"] }, { index: 2 }], childAnswerType: "SINGLE_CHOICE" });
    const toSingle = writeRow(section("s", "VAN", [withSlots]), "b", modePatch(withSlots, "single")).blocks[0]!;
    expect(toSingle).toMatchObject({ questionType: "SINGLE_CHOICE", setCount: null, setSize: null, questionCount: 10, childAnswerType: null });
    expect(toSingle).not.toHaveProperty("slots");
    expect(modePatch(single, "single")).toEqual({});
  });
});

describe("row-list-model: chỉ số và tóm tắt", () => {
  it("rowFigures: 4 câu Đ/S, 4 ý, điểm 1 = 16 ý, thang 4 điểm", () => {
    const tf = block("t", { questionType: "TRUE_FALSE", questionCount: 4, requiredPointsPerQuestion: 1, itemShape: { statements: 4 } });
    expect(rowFigures(tf)).toMatchObject({ questions: 4, statements: 16, points: 4, trueFalse: true, sets: null });
    expect(rowFigures(setBlock("s", 5, 5))).toMatchObject({ sets: { count: 5, size: 5 }, statements: null, points: null });
  });

  it("conditionSummary đếm đúng điều kiện đang có theo thứ tự của bảng R-7", () => {
    const row = setBlock("s", 2, 3, {
      tagCodes: ["T"],
      knowledgeScope: { nodeIds: ["n1", "n2"] },
      topicQuotas: [{ id: "q", name: "", nodeIds: [], count: 1, enforcement: "REQUIRED" }],
      matrix: { mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 6 }] },
      slots: [{ index: 1, tagCodes: ["X"] }, { index: 2 }],
    });
    expect(conditionSummary(row).map((item) => [item.key, item.count])).toEqual([["tags", 1], ["scope", 2], ["topicQuotas", 1], ["difficulty", null], ["slots", 1]]);
    expect(conditionSummary(block("x"))).toEqual([]);
  });
});

describe("row-list-model: chiều ma trận của Phân bổ đối chiếu (R-8, R-11)", () => {
  it("emptyDimensionIsEmptyArray: ô trống bị bỏ, ô nhập 0 giữ, chiều hết ô ghi mảng rỗng, chiều kia và lớp ẩn giữ nguyên", () => {
    let matrix = setDimensionCell(null, "DIFFICULTY", "NB", 8, AXES);
    expect(matrix).toEqual({ mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 8 }] });
    matrix = setDimensionCell(matrix, "DIFFICULTY", "VD", 4, AXES);
    matrix = setDimensionCell(matrix, "DIFFICULTY", "TH", 6, AXES);
    // THPTQG Lý 18 câu: 8/6/4, trống Vận dụng cao: ba giá trị, theo thứ tự trục.
    expect(matrix.byDifficulty).toEqual([{ code: "NB", value: 8 }, { code: "TH", value: 6 }, { code: "VD", value: 4 }]);
    expect(dimensionDeclared(matrix, "DIFFICULTY")).toEqual({ total: 18, cells: 3 });
    // 8/6/4/0: ô 0 được lưu.
    matrix = setDimensionCell(matrix, "DIFFICULTY", "VDC", 0, AXES);
    expect(dimensionCell(matrix, "DIFFICULTY", "VDC")).toBe(0);
    expect(dimensionCell(matrix, "DIFFICULTY", "VD")).toBe(4);
    // Xóa hết ô: mảng rỗng, không null; lớp (đang ẩn) giữ dữ liệu.
    const withGrade: BlockMatrix = { ...matrix, byGrade: [{ code: "GRADE_10", value: 3 }], gradeEnforcement: "REFERENCE" };
    let cleared = withGrade;
    for (const code of ["NB", "TH", "VD", "VDC"]) cleared = setDimensionCell(cleared, "DIFFICULTY", code, null, AXES);
    expect(cleared.byDifficulty).toEqual([]);
    expect(cleared.byGrade).toEqual([{ code: "GRADE_10", value: 3 }]);
    expect(cleared.gradeEnforcement).toBe("REFERENCE");
    expect(pruneMatrix({ ...cleared, byGrade: [] })).toBeNull();
    expect(pruneMatrix(cleared)).toBe(cleared);
  });

  it("BCA ghi mức Tham khảo cho chiều đã khai; QDA, THPTQG, HSA để mặc định Bắt buộc", () => {
    expect(setDimensionCell(null, "GRADE", "GRADE_10", 5, AXES, entryOf("BCA", "VAN").enforcement).gradeEnforcement).toBe("REFERENCE");
    for (const kind of ["QDA", "THPTQG", "HSA"]) expect(setDimensionCell(null, "GRADE", "GRADE_10", 5, AXES, entryOf(kind, "ANH").enforcement)).not.toHaveProperty("gradeEnforcement");
  });

  it("mức đã ghi Tham khảo ở kỳ Bắt buộc hiện chip Tham khảo (ngoại lệ HSA-C); khối lớp ẩn theo bảng R-11", () => {
    const reference = { mode: "SEPARATE" as const, unit: "COUNT" as const, cells: [], byGrade: [], byDifficulty: [], gradeEnforcement: "REFERENCE" as const };
    expect(enforcementModeOf(entryOf("HSA", "ANH"), reference, "GRADE")).toBe("FIXED_REFERENCE");
    expect(enforcementModeOf(entryOf("HSA", "ANH"), reference, "DIFFICULTY")).toBe("FIXED_REQUIRED");
    const hidden = (kind: string, subject: string) => !gradeVisible(entryOf(kind, subject));
    expect([hidden("HSA", "ANH"), hidden("HSA", "VAN"), hidden("THPTQG", "ECON_LAW"), hidden("THPTQG", "TOAN"), hidden("VSAT", "DIA"), hidden("VSAT", "SU")]).toEqual([true, false, true, false, true, false]);
  });

  it("setDimensionCell đặt lại difficultyEnforcement từ REQUIRED về REFERENCE khi xóa ô cuối trên REFERENCE_WITH_APPLY và giữ nguyên gradeEnforcement (R2-1)", () => {
    const initial: BlockMatrix = {
      mode: "SEPARATE",
      unit: "PERCENT",
      basis: "QUESTION",
      gradeEnforcement: "REQUIRED",
      difficultyEnforcement: "REQUIRED",
      cells: [],
      byGrade: [{ code: "GRADE_10", value: 100 }],
      byDifficulty: [
        { code: "NB", value: 40 },
        { code: "TH", value: 60 },
      ],
    };
    const partial = setDimensionCell(initial, "DIFFICULTY", "NB", null, AXES, "REFERENCE_WITH_APPLY");
    expect(partial.difficultyEnforcement).toBe("REQUIRED");
    expect(partial.gradeEnforcement).toBe("REQUIRED");

    const cleared = setDimensionCell(partial, "DIFFICULTY", "TH", null, AXES, "REFERENCE_WITH_APPLY");
    expect(cleared.byDifficulty).toEqual([]);
    expect(cleared.difficultyEnforcement).toBe("REFERENCE");
    expect(cleared.byGrade).toEqual([{ code: "GRADE_10", value: 100 }]);
    expect(cleared.gradeEnforcement).toBe("REQUIRED");
    expect(validateMatrix(cleared, 20, AXES)).toEqual([]);
  });
});

describe("row-list-model: mọi mẫu DS của fixture", () => {
  it("roundTripAllRowTemplates: đọc rồi ghi lại mọi dòng của mọi môn DS không đổi gì (R-8, AC-12)", () => {
    const rowPairs = new Set(listSubjectFormPairs().filter(({ entry }) => entry.form === "ROWS").map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`));
    let sections = 0;
    let rows = 0;
    for (const template of standardFixtureTemplates()) {
      for (const part of template.parts) {
        const all = [...part.sections, ...(part.subjectPool ?? []), ...part.options.flatMap((option) => option.sections)];
        for (const item of all) {
          if (!rowPairs.has(`${template.kindCode}:${item.subjectCode}`)) continue;
          sections += 1;
          let written = item;
          for (const row of item.blocks) {
            rows += 1;
            written = writeRow(written, row.id, {});
            expect(readRow(written, row.id), `${template.code} ${row.id}`).toEqual(row);
          }
          expect(written, `${template.code} ${item.id}`).toEqual(item);
        }
      }
    }
    expect(sections).toBeGreaterThan(50);
    expect(rows).toBeGreaterThan(sections);
  });
});

describe("row-list-model: convertReferenceUnit, applyNewTotal và keepOldTotal (R1-6, R1-11, R1-13)", () => {
  it("convertReferenceUnit giữ chiều rỗng rỗng, đổi non-empty COUNT <-> PERCENT, và chuẩn hóa chiều ẩn trên tổng của nó", () => {
    const base: BlockMatrix = {
      mode: "SEPARATE",
      unit: "COUNT",
      basis: "QUESTION",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
      cells: [],
      byGrade: [{ code: "GRADE_10", value: 5 }, { code: "GRADE_11", value: 5 }],
      byDifficulty: [],
    };
    const percentVisible = convertReferenceUnit(base, "PERCENT", 20, AXES);
    expect(percentVisible.byGrade).toEqual([{ code: "GRADE_10", value: 25 }, { code: "GRADE_11", value: 25 }]);
    expect(percentVisible.byDifficulty).toEqual([]);

    const percentHidden = convertReferenceUnit(base, "PERCENT", 20, AXES, ["GRADE"]);
    expect(percentHidden.byGrade).toEqual([{ code: "GRADE_10", value: 50 }, { code: "GRADE_11", value: 50 }]);
    expect(validateMatrix(percentHidden, 20, AXES)).toEqual([]);

    const backCount = convertReferenceUnit({ ...percentVisible, byDifficulty: [{ code: "NB", value: 40 }, { code: "TH", value: 60 }] }, "COUNT", 20, AXES);
    expect(backCount.byDifficulty).toEqual([{ code: "NB", value: 8 }, { code: "TH", value: 12 }]);
  });

  it("applyNewTotal và keepOldTotal giữ nguyên topicQuotas: undefined khi chưa có và tính đúng khi có", () => {
    const noQuotas = section("s1", "TOAN", [block("b1", { questionCount: 30 })], {
      matrix: { mode: "SEPARATE", unit: "PERCENT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 40 }, { code: "TH", value: 60 }] },
    });
    delete (noQuotas as { topicQuotas?: unknown }).topicQuotas;
    expect(applyNewTotal(noQuotas, 20).topicQuotas).toBeUndefined();
    const keptNoQuotas = keepOldTotal(noQuotas, 30, AXES);
    expect(keptNoQuotas.topicQuotas).toBeUndefined();
    expect(keptNoQuotas.matrix).toMatchObject({ unit: "COUNT", byGrade: [], byDifficulty: [{ code: "NB", value: 12 }, { code: "TH", value: 18 }] });

    const withQuotas = section("s2", "TOAN", [block("b1", { questionCount: 30 })], {
      topicQuotas: [{ id: "q1", name: "Ham so", nodeIds: [], count: 3, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" }],
    });
    expect(applyNewTotal(withQuotas, 20).topicQuotas?.[0]).toMatchObject({ count: 2, percent: 10 });
    const kept = keepOldTotal(withQuotas, 30, AXES);
    expect(kept.topicQuotas?.[0]?.count).toBe(3);
    expect(kept.topicQuotas?.[0]?.percent).toBeUndefined();
  });
});
