import { describe, expect, it } from "vitest";
import { sectionGroups } from "../components/exam-structure-draft";
import { fitsForm } from "../subject-forms/fit";
import {
  addMatrixBlock,
  changeMatrixBlockType,
  dimensionDeclared,
  editMatrixCell,
  initialEnforcement,
  matrixCell,
  moveMatrixBlock,
  recomputeAnswerQuotas,
  removeMatrixBlock,
  resetMatrixSectionBlocks,
  resolveMatrixDefaultSnapshot,
  roleBlocks,
  structureOf,
  toggleMatrixSetMode,
  toggleSectionMixBlocks,
  updateMatrixSetDimensions,
  writeStructure,
} from "../subject-forms/matrix-form-model";
import { subjectFormFor } from "../subject-forms/registry";
import type { AnswerQuotas, BlockMatrix, ExamSection } from "../types";
import { block, section, setBlock, standardFixtureTemplates } from "./subject-form-builders";

/**
 * Mô hình MT (FTM R-3, R-4, R-8; CR-2 AC-1, AC-3, AC-8): số chùm nhất quán, chùm tối thiểu 1, môn không chỉ tiêu giữ trống, khứ hồi
 * mọi mẫu MT, chùm cỡ linh hoạt giữ `null`.
 */
const axes = { grades: ["GRADE_10", "GRADE_11", "GRADE_12", "GRADE_MIXED"], difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"] };

/** BCA Sử đúng như mẫu: 7 câu đơn + 1 chùm × 3, T = 10; nhãn và mã mẫu của hai nhóm là dữ liệu không dẫn xuất. */
const bcaHistory = (): ExamSection =>
  section("su", "SU", [
    block("su-single", { questionCount: 7, templateGroupCode: "SU_DON", requiredPointsPerQuestion: 1 }),
    setBlock("su-set", 1, 3, { tagCodes: ["SU_TU_LIEU_LICH_SU"], templateGroupCode: "SU_CHUM", requiredPointsPerQuestion: 1 }),
  ]);

const scienceWithQuotas = (quotas: AnswerQuotas | null): ExamSection =>
  section("toan", "TOAN", [block("single", { questionType: null, questionCount: 32 }), setBlock("set", 1, 3), block("fill", { questionType: "SHORT_ANSWER", questionCount: 15 })], { answerQuotas: quotas });

const hsaB: AnswerQuotas = {
  unit: "COUNT",
  items: [
    { answerType: "SINGLE_CHOICE", value: 32 },
    { answerType: "SHORT_ANSWER", value: 15 },
    { answerType: "QUESTION_SET", value: 3 },
  ],
};
const legacy: AnswerQuotas = {
  unit: "COUNT",
  items: [
    { answerType: "SINGLE_CHOICE", value: 35 },
    { answerType: "SHORT_ANSWER", value: 15 },
  ],
};

const valueOf = (target: ExamSection, id: string) => target.blocks.find((item) => item.id === id)!;

describe("Cấu trúc câu: số chùm, T, S (FTM R-3, CR-2 AC-1)", () => {
  it("consistentSetCounts: BCA Sử T = 10, số chùm 1 → 2 ghi nhóm chùm 2 × 3 = 6, nhóm đơn 4, giữ trường không dẫn xuất", () => {
    const { section: next, choiceBelowChildren } = writeStructure(bcaHistory(), { sets: 2 });
    const set = valueOf(next, "su-set");
    expect(choiceBelowChildren).toBe(false);
    expect([set.setCount, set.setSize, set.questionCount]).toEqual([2, 3, 6]);
    expect(valueOf(next, "su-single").questionCount).toBe(4);
    for (const id of ["su-single", "su-set"]) {
      const before = valueOf(bcaHistory(), id);
      const after = valueOf(next, id);
      expect({ id: after.id, tagCodes: after.tagCodes, templateGroupCode: after.templateGroupCode, name: after.name, points: after.requiredPointsPerQuestion }).toEqual({
        id: before.id,
        tagCodes: before.tagCodes,
        templateGroupCode: before.templateGroupCode,
        name: before.name,
        points: before.requiredPointsPerQuestion,
      });
    }
    expect(next.blocks.map((item) => item.id)).toEqual(["su-single", "su-set"]);
    expect(structureOf(next)).toMatchObject({ choice: 10, sets: 2, children: 6, single: 4, total: 10 });
  });

  it("đổi T giữ số chùm: nhóm đơn là T trừ câu con; T nhỏ hơn câu con ghi nhóm đơn 0 và báo", () => {
    const grown = writeStructure(bcaHistory(), { choice: 12 });
    expect(valueOf(grown.section, "su-single").questionCount).toBe(9);
    expect(valueOf(grown.section, "su-set").questionCount).toBe(3);

    const below = writeStructure(bcaHistory(), { choice: 2 });
    expect(below.choiceBelowChildren).toBe(true);
    expect(valueOf(below.section, "su-single").questionCount).toBe(0);
  });

  it("câu đơn và câu điền về 0 giữ nguyên nhóm và mọi trường; đặt lại thì dùng lại chính nhóm đó", () => {
    const base = scienceWithQuotas(null);
    const zeroed = writeStructure(base, { fill: 0 }).section;
    const fill = valueOf(zeroed, "fill");
    expect(zeroed.blocks).toHaveLength(3);
    expect(fill).toEqual({ ...valueOf(base, "fill"), questionCount: 0 });
    const restored = writeStructure(zeroed, { fill: 4 }).section;
    expect(valueOf(restored, "fill")).toEqual({ ...valueOf(base, "fill"), questionCount: 4 });
  });

  it("fixedSetMinOne: Số chùm 0 ghi chùm 0 (máy chủ không nhận), nhóm đơn nhận phần của chùm; chặn lưu do `matrix-blocking`", () => {
    const { section: next } = writeStructure(bcaHistory(), { sets: 0 });
    const set = valueOf(next, "su-set");
    expect([set.setCount, set.setSize, set.questionCount]).toEqual([0, 3, 0]);
    expect(valueOf(next, "su-single").questionCount).toBe(10);
  });

  it("không đổi gì thì trả lại chính môn (không tạo thay đổi giả)", () => {
    const base = bcaHistory();
    expect(writeStructure(base, {}).section).toBe(base);
    expect(writeStructure(base, { choice: 10, sets: 1 }).section).toBe(base);
  });

  it("noQuotaStaysNull: môn không có chỉ tiêu dạng trả lời giữ `null` sau khi sửa", () => {
    const { section: next } = writeStructure(scienceWithQuotas(null), { fill: 12 });
    expect(next.answerQuotas).toBeNull();
    expect(next.matrix).toBeNull();
  });

  it("answerQuotas giữ đúng dạng đang lưu: HSA-B đếm riêng, dạng cũ gộp câu con vào Trắc nghiệm, chỉ khi đổi Cấu trúc câu", () => {
    const hsaBefore = scienceWithQuotas(hsaB);
    expect(writeStructure(hsaBefore, {}).section.answerQuotas).toBe(hsaB);
    const hsaAfter = writeStructure(hsaBefore, { choice: 30, fill: 12, sets: 2 }).section;
    expect(hsaAfter.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { answerType: "SINGLE_CHOICE", value: 24 },
        { answerType: "SHORT_ANSWER", value: 12 },
        { answerType: "QUESTION_SET", value: 6 },
      ],
    });
    const legacyAfter = writeStructure(scienceWithQuotas(legacy), { choice: 33, fill: 12 }).section;
    expect(legacyAfter.answerQuotas).toEqual({
      unit: "COUNT",
      items: [
        { answerType: "SINGLE_CHOICE", value: 33 },
        { answerType: "SHORT_ANSWER", value: 12 },
      ],
    });
  });

  it("answerQuotas theo % tính lại thành % của số câu của môn; dòng của vai trò không còn nhóm giữ nguyên", () => {
    const percent: AnswerQuotas = { unit: "PERCENT", items: [{ answerType: "SINGLE_CHOICE", value: 64 }, { answerType: "SHORT_ANSWER", value: 30 }, { answerType: "QUESTION_SET", value: 6 }] };
    const next = writeStructure(scienceWithQuotas(percent), { choice: 40, fill: 10 }).section;
    expect(next.answerQuotas!.unit).toBe("PERCENT");
    expect(next.answerQuotas!.items.map((item) => item.answerType)).toEqual(["SINGLE_CHOICE", "SHORT_ANSWER", "QUESTION_SET"]);
    expect(next.answerQuotas!.items.reduce((total, item) => total + item.value, 0)).toBe(100);

    const noFill = section("lich-su", "SU", [block("s", { questionCount: 22 }), setBlock("c", 1, 3)], { answerQuotas: hsaB });
    const out = recomputeAnswerQuotas(noFill.answerQuotas, structureOf(noFill), 25)!;
    expect(out.items.find((item) => item.answerType === "SHORT_ANSWER")!.value).toBe(15);
  });
});

describe("Chùm cỡ linh hoạt (H28, V-ACT Toán; CR-2 AC-3)", () => {
  const vactMath = (): ExamSection =>
    section("toan", "TOAN", [block("single", { questionCount: 12 }), block("set", { questionType: "QUESTION_SET", questionCount: 18, setCount: null, setSize: null })], { questionCount: 30 });

  it("vactFlexibleSets: nhập tổng câu con 18 lưu `questionCount` 18, `setCount` và `setSize` vẫn `null`; không chia đều", () => {
    const base = vactMath();
    expect(structureOf(base)).toMatchObject({ flexible: true, sets: null, setSize: null, children: 18, single: 12, choice: 30 });
    const grown = writeStructure(base, { children: 15, choice: 27 }).section;
    const set = valueOf(grown, "set");
    expect([set.setCount, set.setSize, set.questionCount]).toEqual([null, null, 15]);
    expect(valueOf(grown, "single").questionCount).toBe(12);
    const back = writeStructure(grown, { children: 18, choice: 30 }).section;
    expect(valueOf(back, "set")).toEqual(valueOf(base, "set"));
  });

  it("tổng câu con về 0 giữ nhóm như nhóm câu đơn, `setCount` và `setSize` vẫn `null`", () => {
    const zero = writeStructure(vactMath(), { children: 0, choice: 12 }).section;
    expect(valueOf(zero, "set")).toEqual({ ...valueOf(vactMath(), "set"), questionCount: 0 });
  });

  it("chùm cỡ linh hoạt không có ô Số chùm: `sets` null, ghi `sets` không đổi `setCount`", () => {
    const next = writeStructure(vactMath(), { sets: 3 }).section;
    expect(valueOf(next, "set").setCount).toBeNull();
  });
});

describe("Khối lớp, độ khó (R-4, R-8, R-9; CR-2 AC-6)", () => {
  const context = { total: 25, axes };
  const edit = (matrix: BlockMatrix | null, dimension: "grade" | "difficulty", code: string, value: number | null, unit: "COUNT" | "PERCENT" = "COUNT") =>
    editMatrixCell(matrix, { dimension, code, unit, value }, context);

  it("ma trận null giữ null khi ô để trống; ô đầu tiên tạo ma trận SEPARATE COUNT với chiều kia là mảng rỗng", () => {
    expect(edit(null, "difficulty", "RECOGNITION", null)).toBeNull();
    const created = edit(null, "difficulty", "RECOGNITION", 10)!;
    expect(created).toMatchObject({ mode: "SEPARATE", unit: "COUNT", byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 10 }] });
    expect(created.gradeEnforcement).toBeUndefined();
  });

  it("QDA Lý: khai 10/10/5 và để trống Vận dụng cao không ghi giá trị Vận dụng cao; 10/10/5/0 ghi ô 0; trống hết ghi mảng rỗng", () => {
    let matrix: BlockMatrix | null = null;
    for (const [code, value] of [["RECOGNITION", 10], ["COMPREHENSION", 10], ["APPLICATION", 5]] as const) matrix = edit(matrix, "difficulty", code, value);
    expect(matrix!.byDifficulty).toEqual([
      { code: "RECOGNITION", value: 10 },
      { code: "COMPREHENSION", value: 10 },
      { code: "APPLICATION", value: 5 },
    ]);
    expect(matrixCell(matrix, "difficulty", "HIGH_APPLICATION")).toBeNull();
    matrix = edit(matrix, "difficulty", "HIGH_APPLICATION", 0);
    expect(matrixCell(matrix, "difficulty", "HIGH_APPLICATION")).toBe(0);
    for (const code of ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"]) matrix = edit(matrix, "difficulty", code, null);
    expect(matrix!.byDifficulty).toEqual([]);
    expect(dimensionDeclared(matrix, "difficulty")).toBe(false);
  });

  it("mảng giữ thứ tự của trục dù nhập ô sau trước", () => {
    const matrix = edit(edit(null, "grade", "GRADE_12", 14), "grade", "GRADE_10", 6)!;
    expect(matrix.byGrade.map((item) => item.code)).toEqual(["GRADE_10", "GRADE_12"]);
  });

  it("nhập % ở cột Tỷ lệ đổi ma trận sang PERCENT (số đã có quy ra %), nhập số câu đổi về COUNT; giữ mức áp dụng", () => {
    const counts: BlockMatrix = { mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 5 }, { code: "COMPREHENSION", value: 10 }, { code: "APPLICATION", value: 10 }], gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" };
    const percent = edit(counts, "difficulty", "RECOGNITION", 25, "PERCENT")!;
    expect(percent.unit).toBe("PERCENT");
    expect(percent.byDifficulty.map((item) => item.value)).toEqual([25, 40, 40]);
    expect(percent.difficultyEnforcement).toBe("REFERENCE");
    const back = edit(percent, "difficulty", "RECOGNITION", 8, "COUNT")!;
    expect(back.unit).toBe("COUNT");
    expect(back.byDifficulty[0]).toEqual({ code: "RECOGNITION", value: 8 });
  });

  it("ma trận mới của kỳ Tham khảo hay Chỉ đối chiếu ghi REFERENCE; kỳ khác để mặc định", () => {
    expect(initialEnforcement("FIXED_REFERENCE")).toBe("REFERENCE");
    expect(initialEnforcement("REFERENCE_WITH_APPLY")).toBe("REFERENCE");
    expect(initialEnforcement("DECLARED_REQUIRED")).toBeUndefined();
    const created = editMatrixCell(null, { dimension: "grade", code: "GRADE_10", unit: "COUNT", value: 3 }, { ...context, enforcement: "REFERENCE" })!;
    expect(created).toMatchObject({ gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" });
  });
});

describe("Khứ hồi mọi mẫu MT của QDA, BCA, V-ACT trong fixture (CR-2 AC-8, plan B4)", () => {
  const MATRIX_KINDS = ["QDA", "BCA", "VACT"];

  it("roundTripAllMatrixTemplates: mở rồi ghi lại Cấu trúc câu không đổi byte; answerQuotas tính lại ra đúng số đã lưu", () => {
    const covered: string[] = [];
    for (const template of standardFixtureTemplates().filter((item) => MATRIX_KINDS.includes(item.kindCode))) {
      for (const part of template.parts) {
        for (const group of sectionGroups(part)) {
          for (const target of group.sections) {
            const entry = subjectFormFor(template.kindCode, target.subjectCode);
            if (entry?.form !== "MATRIX") continue;
            const verdict = fitsForm(target, entry, { kindCode: template.kindCode, subjectCode: target.subjectCode });
            expect(verdict, `${template.code} ${target.subjectCode}`).toEqual({ fits: true });
            const before = JSON.stringify(target);
            const structure = structureOf(target);
            const identity = writeStructure(target, { choice: structure.choice, ...(structure.sets === null ? {} : { sets: structure.sets }), fill: structure.fill, ...(structure.flexible ? { children: structure.children } : {}) });
            expect(JSON.stringify(identity.section), `${template.code} ${target.subjectCode}`).toBe(before);
            expect(identity.choiceBelowChildren).toBe(false);
            expect(JSON.stringify(recomputeAnswerQuotas(target.answerQuotas, structure, target.questionCount)), `${template.code} ${target.subjectCode} answerQuotas`).toBe(JSON.stringify(target.answerQuotas));
            covered.push(`${template.kindCode}:${target.subjectCode}`);
          }
        }
      }
    }
    expect(new Set(covered)).toEqual(new Set(["QDA:TOAN", "QDA:LY", "QDA:HOA", "QDA:SINH", "QDA:SU", "QDA:DIA", "BCA:TOAN", "BCA:SU", "BCA:LY", "BCA:HOA", "BCA:SINH", "BCA:DIA", "VACT:VIETNAMESE", "VACT:TOAN"]));
  });

  it("vai trò của từng mẫu: V-ACT Toán chùm cỡ linh hoạt, V-ACT Tiếng Việt chỉ nhóm câu đơn", () => {
    const vact = standardFixtureTemplates().find((item) => item.code === "VACT_SUBJECT_TOAN")!;
    const toan = vact.parts[0]!.sections![0]!;
    expect(structureOf(toan)).toMatchObject({ flexible: true, children: 18, single: 12 });
    const vn = standardFixtureTemplates().find((item) => item.code === "VACT_SUBJECT_VIETNAMESE")!.parts[0]!.sections![0]!;
    const roles = roleBlocks(vn);
    expect(roles.single).not.toBeNull();
    expect(roles.set).toBeNull();
    expect(roles.fill).toBeNull();
  });
});

describe("Bảng thành phần câu hỏi v3: dòng động, cỡ chùm, trộn lẫn toàn môn và mặc định của môn (UNI-2776)", () => {
  it("addMatrixBlock & removeMatrixBlock: kế thừa điểm đồng nhất, giữ ít nhất 1 dòng, gắn deletedGroupName cho mapidRules và gỡ answerQuotas khi thêm định dạng mở rộng", () => {
    const base: ExamSection = {
      ...scienceWithQuotas(hsaB),
      blocks: scienceWithQuotas(hsaB).blocks.map((item) => ({ ...item, requiredPointsPerQuestion: 2 })),
      mapidRules: [{ id: "r1", type: "COUNT", groupId: "fill", nodeIds: ["kn-1"], comparator: "AT_LEAST", count: 3 }],
    };
    const withCloze = addMatrixBlock(base, "CLOZE", "Đọc điền khuyết");
    expect(withCloze.blocks).toHaveLength(4);
    expect(withCloze.blocks[3]).toMatchObject({ name: "Đọc điền khuyết", questionType: "CLOZE", questionCount: 0, requiredPointsPerQuestion: 2 });
    expect(withCloze.answerQuotas).toBeNull();

    const removedFill = removeMatrixBlock(withCloze, "fill");
    expect(removedFill.blocks.map((item) => item.id)).not.toContain("fill");
    expect(removedFill.mapidRules?.[0]?.deletedGroupName).toBe("fill");

    const oneLeft = removeMatrixBlock(removeMatrixBlock(removedFill, "single"), "set");
    expect(oneLeft.blocks).toHaveLength(1);
    expect(removeMatrixBlock(oneLeft, oneLeft.blocks[0]!.id)).toBe(oneLeft);
  });

  it("moveMatrixBlock & toggleSectionMixBlocks: đổi chỗ dòng khi tắt trộn lẫn; khóa đổi chỗ khi mixBlocks = true; tắt xóa sạch khóa mixBlocks", () => {
    const base = bcaHistory();
    const swapped = moveMatrixBlock(base, "su-set", -1);
    expect(swapped.blocks.map((item) => item.id)).toEqual(["su-set", "su-single"]);

    const mixed = toggleSectionMixBlocks(swapped, true);
    expect(mixed.mixBlocks).toBe(true);
    expect(moveMatrixBlock(mixed, "su-single", -1)).toBe(mixed);

    const unmixed = toggleSectionMixBlocks(mixed, false);
    expect("mixBlocks" in unmixed).toBe(false);
  });

  it("changeMatrixBlockType, toggleMatrixSetMode & updateMatrixSetDimensions: đổi đơn ↔ chùm sạch dữ liệu, đổi cỡ cố định ↔ linh hoạt mượt", () => {
    const base = bcaHistory();
    const toSet = changeMatrixBlockType(base, "su-single", "QUESTION_SET", { nextName: "Chùm mới" });
    expect(valueOf(toSet, "su-single")).toMatchObject({ name: "Chùm mới", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, childAnswerType: "SINGLE_CHOICE" });

    const resized = updateMatrixSetDimensions(toSet, "su-single", { setCount: 2, setSize: 5 });
    expect(valueOf(resized, "su-single")).toMatchObject({ setCount: 2, setSize: 5, questionCount: 10 });

    const flexible = toggleMatrixSetMode(resized, "su-single", "FLEXIBLE");
    expect(valueOf(flexible, "su-single")).toMatchObject({ setCount: null, setSize: null, questionCount: 10 });

    const backFixed = toggleMatrixSetMode(flexible, "su-single", "FIXED");
    expect(valueOf(backFixed, "su-single")).toMatchObject({ setCount: 3, setSize: 3, questionCount: 9 });

    const backSingle = changeMatrixBlockType(backFixed, "su-single", "TRUE_FALSE");
    expect(valueOf(backSingle, "su-single")).toMatchObject({ questionType: "TRUE_FALSE", setCount: null, setSize: null, childAnswerType: null });
  });

  it("resolveMatrixDefaultSnapshot & resetMatrixSectionBlocks: khôi phục từ bản lưu gốc về đúng dòng, answerQuotas và mixBlocks ban đầu", () => {
    const original = scienceWithQuotas(hsaB);
    const baselineParts = [
      {
        id: "p1",
        name: "P1",
        order: 1,
        mode: "FIXED" as const,
        durationMinutes: 75,
        questionCount: 50,
        questionCountOnPaper: 50,
        questionCountRequired: 50,
        participation: { mode: "ALL" as const, choose: null },
        maxPoints: 50,
        sections: [original],
        options: [],
      },
    ];
    const edited = toggleSectionMixBlocks(addMatrixBlock(original, "ESSAY", "Tự luận"), true);
    const snapshot = resolveMatrixDefaultSnapshot(edited, "HSA", null, [], baselineParts)!;
    expect(snapshot).not.toBeNull();
    const reset = resetMatrixSectionBlocks(edited, snapshot);
    expect(reset.blocks.map((item) => item.id)).toEqual(["single", "set", "fill"]);
    expect(reset.answerQuotas).toEqual(hsaB);
    expect("mixBlocks" in reset).toBe(false);
  });
});
