import { describe, expect, it } from "vitest";
import { sectionGroups } from "../components/exam-structure-draft";
import { fitsForm } from "../subject-forms/fit";
import { listSubjectFormPairs, subjectFormFor } from "../subject-forms/registry";
import type { BlockMatrix, ExamSection } from "../types";
import { block, fixtureSource, scienceSection, section, setBlock, standardFixtureTemplates } from "./subject-form-builders";

/**
 * FTM R-2 (AC-5 phần hàm khớp, CR-1 AC-4): mỗi điều kiện không khớp, một mình, trả `fits: false` đúng lý do; chùm cỡ linh hoạt và
 * dữ liệu MAPID của HSA không làm rơi MD; mọi mẫu chuẩn của fixture khớp, in bảng phủ (plan B8).
 */
const verdict = (kind: string, subject: string, target: ExamSection) => {
  const entry = subjectFormFor(kind, subject);
  if (!entry) throw new Error(`khong co muc ${kind}:${subject}`);
  const result = fitsForm(target, entry, { kindCode: kind, subjectCode: subject });
  return result.fits ? "FITS" : result.reason;
};

const separate = (over: Partial<BlockMatrix> = {}): BlockMatrix => ({
  mode: "SEPARATE",
  unit: "COUNT",
  cells: [],
  byGrade: [{ code: "GRADE_10", value: 5 }],
  byDifficulty: [],
  ...over,
});

describe("Khớp biểu mẫu: một điều không khớp, một mình (FTM R-2)", () => {
  const mt = (kind = "QDA", subject = "TOAN", over: Partial<ExamSection> = {}, blocks = [block("s"), setBlock("c", 1, 3), block("f", { questionType: "SHORT_ANSWER", questionCount: 2 })]) =>
    verdict(kind, subject, section("sec", subject, blocks, over));
  const ds = (kind: string, subject: string, blocks = [block("r1")], over: Partial<ExamSection> = {}) => verdict(kind, subject, section("sec", subject, blocks, over));

  it("mục chuẩn của MT và DS khớp", () => {
    expect(mt()).toBe("FITS");
    expect(mt("HSA", "LY")).toBe("FITS");
    expect(ds("SPT", "LY")).toBe("FITS");
    expect(mt("SP2E", "TOAN")).toBe("FITS");
    expect(mt("SPT", "TOAN")).toBe("FITS");
  });

  it("COMBINED ở kỳ không có bảng kết hợp, hoặc ở nhóm; V-SAT cho ở cấp phần", () => {
    const combined = separate({ mode: "COMBINED", cells: [{ gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 3 }], byGrade: [] });
    expect(mt("QDA", "TOAN", { matrix: combined })).toBe("COMBINED_MATRIX");
    expect(ds("SPT", "LY", [block("r")], { matrix: combined })).toBe("COMBINED_MATRIX");
    expect(ds("VSAT", "TOAN", [block("r")], { matrix: { ...combined, gradeEnforcement: "REFERENCE" } })).toBe("FITS");
    expect(ds("VSAT", "TOAN", [block("r", { matrix: combined })])).toBe("COMBINED_MATRIX");
  });

  it("nhóm có số câu để trống", () => {
    expect(mt("QDA", "TOAN", {}, [block("s", { questionCount: null })])).toBe("COUNT_NULL");
    expect(ds("THPTQG", "LY", [block("r", { questionCount: null })])).toBe("COUNT_NULL");
  });

  it("nhập theo tỷ lệ ở kỳ không cho nhập % (V-ACT, SP2E cho)", () => {
    const percent = separate({ unit: "PERCENT", byGrade: [{ code: "GRADE_10", value: 100 }] });
    expect(mt("QDA", "TOAN", { matrix: percent })).toBe("PERCENT_ENTRY");
    expect(mt("VACT", "TOAN", { matrix: percent }, [block("s", { questionCount: 12 }), setBlock("c", 1, 18, { setCount: null, setSize: null })])).toBe("FITS");
    expect(ds("THPTQG", "LY", [block("r")], { matrix: percent })).toBe("PERCENT_ENTRY");
    expect(ds("SP2E", "LY", [block("r")], { matrix: percent })).toBe("FITS");
    // UNI-2876: Toán của SP2E, SPT là MT như HSA (không nhập %): bài cũ lưu % ở màn sửa dòng mở MD, không bị đổi.
    expect(mt("SP2E", "TOAN", { matrix: percent })).toBe("PERCENT_ENTRY");
    expect(mt("SPT", "TOAN", { matrix: percent })).toBe("PERCENT_ENTRY");
    expect(mt("QDA", "TOAN", { answerQuotas: { unit: "PERCENT", items: [{ answerType: "SINGLE_CHOICE", value: 100 }] } })).toBe("PERCENT_ENTRY");
    expect(mt("QDA", "TOAN", {}, [block("s", { topicQuotas: [{ id: "t", name: "t", nodeIds: [], count: 3, percent: 10, enforcement: "REQUIRED" }] })])).toBe("PERCENT_ENTRY");
  });

  it("đơn vị đếm khác câu ở phân bố Bắt buộc; Tham khảo được", () => {
    expect(ds("SPT", "LY", [block("r")], { matrix: separate({ basis: "PROMPT", byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 4 }] }) })).toBe("COUNT_BASIS");
    expect(ds("VSAT", "TOAN", [block("r")], { matrix: separate({ basis: "PROMPT", byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 4 }], difficultyEnforcement: "REFERENCE" }) })).toBe("FITS");
  });

  it("điều kiện lớp Bắt buộc ở chỗ R-11 ẩn khối lớp; Tham khảo hoặc môn không ẩn thì khớp", () => {
    const required = separate({ gradeEnforcement: "REQUIRED" });
    const reference = separate({ gradeEnforcement: "REFERENCE" });
    expect(ds("SPT", "ANH", [block("r")], { matrix: required })).toBe("GRADE_REQUIRED_HIDDEN");
    expect(ds("SPT", "GDKTPL", [block("r")], { matrix: required })).toBe("GRADE_REQUIRED_HIDDEN");
    expect(ds("SP2E", "ECON_LAW", [block("r")], { matrix: required })).toBe("GRADE_REQUIRED_HIDDEN");
    expect(ds("VSAT", "VAN", [block("r")], { matrix: required })).toBe("GRADE_REQUIRED_HIDDEN");
    expect(ds("SPT", "ANH", [block("r")], { matrix: reference })).toBe("FITS");
    expect(ds("SPT", "LY", [block("r")], { matrix: required })).toBe("FITS");
    expect(ds("VSAT", "SU", [block("r")], { matrix: required })).toBe("FITS");
    expect(ds("THPTQG", "SU", [block("r", { matrix: required })])).toBe("GRADE_REQUIRED_HIDDEN");
    expect(ds("SPT", "ANH", [block("r")], { topicQuotas: [{ id: "t", name: "t", nodeIds: [], count: 2, gradeCodes: ["GRADE_12"], enforcement: "REQUIRED" }] })).toBe("GRADE_REQUIRED_HIDDEN");
  });

  it("MT v3: nhiều dòng cùng loại và đủ 8 loại câu hỏi đều khớp biểu mẫu gọn", () => {
    expect(mt("QDA", "TOAN", {}, [block("a"), block("b")])).toBe("FITS");
    expect(mt("QDA", "TOAN", {}, [setBlock("a", 1, 3), setBlock("b", 2, 4)])).toBe("FITS");
    expect(mt("QDA", "TOAN", {}, [block("a"), block("b", { questionType: "TRUE_FALSE" })])).toBe("FITS");
    expect(mt("QDA", "TOAN", {}, [block("a", { questionType: "CLOZE" }), block("b", { questionType: "ESSAY" })])).toBe("FITS");
  });

  it("MT: nhóm có ma trận riêng, điều kiện vị trí, điều kiện từng chùm", () => {
    expect(mt("QDA", "TOAN", {}, [block("a", { matrix: separate() })])).toBe("BLOCK_MATRIX");
    expect(mt("HSA", "TOAN", {}, [block("a", { matrix: separate() })])).toBe("BLOCK_MATRIX");
    expect(mt("QDA", "TOAN", {}, [setBlock("c", 1, 3, { childConditions: [{ from: 1, to: 1, nodeIds: ["n"] }] })])).toBe("POSITION_CONDITION");
    expect(mt("QDA", "TOAN", {}, [setBlock("c", 2, 3, { slots: [{ index: 1, tagCodes: ["x"] }] })])).toBe("SET_CONDITION");
  });

  it("MT: nhóm đơn hoặc điền có phạm vi hay nhãn riêng; dữ liệu cũ chưa ở chế độ MAPID và chùm còn tag khi tắt Nguồn chùm đều rơi về MD", () => {
    const scope = { nodeIds: ["m1"] };
    expect(mt("VACT", "TOAN", {}, [block("a", { knowledgeScope: scope })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("VACT", "TOAN", {}, [block("a"), setBlock("c", 1, 3, { knowledgeScope: scope, tagCodes: ["T"] })])).toBe("FITS");
    // Chưa chuyển sang MAPID (mapidMode undefined): phạm vi cũ cấp nhóm (kể cả trên chùm) rơi về MD.
    expect(mt("QDA", "TOAN", {}, [block("a", { knowledgeScope: scope })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("QDA", "LY", {}, [block("a"), setBlock("c", 1, 3, { knowledgeScope: scope })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("QDA", "LY", {}, [block("a"), setBlock("c", 1, 3, { tagCodes: ["THI_NGHIEM"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("QDA", "TOAN", {}, [block("f", { questionType: "SHORT_ANSWER", tagCodes: ["x"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
    // BCA Sử có sẵn tag SU_TU_LIEU_LICH_SU trên chùm trong mẫu chuẩn: setSource=true giữ khớp; nhưng chùm có scope cũ vẫn rơi về MD.
    expect(mt("BCA", "SU", {}, [block("a"), setBlock("c", 1, 3, { tagCodes: ["SU_TU_LIEU_LICH_SU"] })])).toBe("FITS");
    expect(mt("BCA", "SU", {}, [block("a"), setBlock("c", 1, 3, { knowledgeScope: scope, tagCodes: ["SU_TU_LIEU_LICH_SU"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("HSA", "DIA", {}, [block("a"), setBlock("c", 1, 3, { tagCodes: ["BIEU_DO"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
    // Khi môn đã ở chế độ MAPID (mapidMode: "MATRIX"): phạm vi MAPID cũ bị bỏ qua bởi bộ biên dịch nên vẫn khớp.
    expect(mt("QDA", "TOAN", { mapidMode: "MATRIX" }, [block("a", { knowledgeScope: scope })])).toBe("FITS");
  });

  it("MT: chỉ tiêu chủ đề riêng của nhóm làm rơi khi chưa ở chế độ MAPID; chỉ tiêu có lọc lớp ở môn ẩn lớp rơi về GRADE_REQUIRED_HIDDEN", () => {
    const topic = { id: "t", name: "t", nodeIds: ["n"], count: 2, enforcement: "REQUIRED" as const };
    const refTopic = { id: "t", name: "t", nodeIds: ["n"], count: 2, enforcement: "REFERENCE" as const };
    expect(mt("VACT", "TOAN", {}, [block("a", { topicQuotas: [topic] })])).toBe("BLOCK_TOPIC_QUOTAS");
    expect(mt("QDA", "TOAN", {}, [block("a", { topicQuotas: [topic] })])).toBe("BLOCK_TOPIC_QUOTAS");
    expect(mt("BCA", "TOAN", {}, [block("a", { topicQuotas: [refTopic] })])).toBe("BLOCK_TOPIC_QUOTAS");
    expect(mt("HSA", "TOAN", { mapidMode: "MATRIX", knowledgeScope: { nodeIds: ["n"] }, topicQuotas: [topic] }, [block("a", { topicQuotas: [topic], knowledgeScope: { nodeIds: ["m1"] } })])).toBe("FITS");
    expect(mt("QDA", "TOAN", { mapidMode: "MATRIX" }, [block("a", { topicQuotas: [topic] })])).toBe("FITS");
    expect(mt("HSA", "TOAN", {}, [block("a", { tagCodes: ["x"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
    expect(mt("HSA", "SU", { topicQuotas: [{ ...topic, gradeCodes: ["GRADE_12"] }] }, [block("a"), setBlock("c", 1, 3)])).toBe("GRADE_REQUIRED_HIDDEN");
  });

  it("MT: answerQuotas hợp lệ cho mọi loại câu v3 (mỗi loại tối đa 1 dòng); trùng loại rơi về ANSWER_QUOTAS_SHAPE", () => {
    const quotas = (...types: string[]) => ({ unit: "COUNT" as const, items: types.map((answerType) => ({ answerType: answerType as "SINGLE_CHOICE", value: 5 })) });
    expect(mt("QDA", "TOAN", { answerQuotas: quotas("SINGLE_CHOICE", "SHORT_ANSWER", "QUESTION_SET") })).toBe("FITS");
    expect(mt("QDA", "TOAN", { answerQuotas: quotas("SINGLE_CHOICE", "SHORT_ANSWER") })).toBe("FITS");
    expect(mt("QDA", "TOAN", { answerQuotas: quotas() })).toBe("FITS");
    expect(mt("QDA", "TOAN", { answerQuotas: quotas("SINGLE_CHOICE", "TRUE_FALSE") })).toBe("FITS");
    expect(mt("QDA", "TOAN", { answerQuotas: quotas("SINGLE_CHOICE", "SINGLE_CHOICE") })).toBe("ANSWER_QUOTAS_SHAPE");
  });

  it("nhóm chùm cỡ cố định lệch số câu hoặc setCount ≤ 0; cỡ linh hoạt không làm rơi", () => {
    expect(mt("QDA", "TOAN", {}, [setBlock("c", 1, 3, { questionCount: 4 })])).toBe("SET_SIZE_MISMATCH");
    expect(mt("QDA", "TOAN", {}, [setBlock("c", 0, 3)])).toBe("SET_SIZE_MISMATCH");
    expect(mt("QDA", "TOAN", {}, [block("c", { questionType: "QUESTION_SET", setCount: -1, setSize: 3, questionCount: -3 })])).toBe("SET_SIZE_MISMATCH");
    expect(ds("SPT", "LY", [setBlock("c", 2, 3, { questionCount: 5 })])).toBe("SET_SIZE_MISMATCH");
    const flexible = block("c", { questionType: "QUESTION_SET", setCount: null, setSize: null, questionCount: 18 });
    expect(mt("VACT", "TOAN", {}, [block("s", { questionCount: 12 }), flexible])).toBe("FITS");
    expect(ds("HSCA", "TOAN", [flexible])).toBe("FITS");
  });

  it("mục vi phạm giới hạn R-12 ngay khi nạp mở MD (HSA, QDA Khoa học); kỳ khác không bị", () => {
    const withSets = (count: number) => [block("s", { questionCount: 14 }), setBlock("c", count, 3), block("f", { questionType: "SHORT_ANSWER", questionCount: 1 })];
    expect(mt("QDA", "LY", {}, withSets(3))).toBe("LIMITS_VIOLATED");
    expect(mt("QDA", "LY", {}, withSets(2))).toBe("FITS");
    expect(mt("HSA", "SINH", {}, [block("s"), setBlock("c", 1, 3)])).toBe("LIMITS_VIOLATED");
    expect(mt("HSA", "HOA", {}, [block("s"), setBlock("c", 1, 4), block("f", { questionType: "SHORT_ANSWER", questionCount: 1 })])).toBe("LIMITS_VIOLATED");
    expect(mt("HSA", "SU", {}, [block("s"), setBlock("c", 1, 3)])).toBe("FITS");
    expect(mt("QDA", "DIA", {}, [block("s"), setBlock("c", 1, 3)])).toBe("FITS");
    expect(mt("BCA", "LY", {}, withSets(3))).toBe("FITS");
    expect(verdict("QDA", "LY", scienceSection("ly"))).toBe("FITS");
  });

  it("DS: điều kiện vị trí câu con chỉ ở SP2E, V-SAT Tiếng Anh; nơi khác màn sửa dòng không có mục", () => {
    const conditioned = [setBlock("c", 1, 4, { childConditions: [{ from: 1, to: 2, nodeIds: ["n"] }] })];
    expect(ds("HSA", "VAN", conditioned)).toBe("ROW_CONDITION_UNSUPPORTED");
    expect(ds("SP2E", "ANH", conditioned)).toBe("FITS");
    expect(ds("VSAT", "ANH", conditioned)).toBe("FITS");
    expect(ds("SP2E", "LY", conditioned)).toBe("ROW_CONDITION_UNSUPPORTED");
  });

  it("DS: điều kiện có mục ở màn sửa dòng (nhãn, MAPID, phân bổ, độ khó, từng chùm) không làm rơi", () => {
    const rich = block("r", { tagCodes: ["x"], knowledgeScope: { nodeIds: ["m"] }, matrix: separate({ byGrade: [] , byDifficulty: [{ code: "RECOGNITION", value: 2 }] }), topicQuotas: [{ id: "t", name: "t", nodeIds: ["n"], count: 1, enforcement: "REFERENCE" }] });
    expect(ds("SP2E", "LY", [rich, setBlock("c", 2, 5, { slots: [{ index: 1, tagCodes: ["y"] }] })])).toBe("FITS");
  });

  it("UNI-2876: bài SP2E/SPT Toán cũ lưu bằng màn sửa dòng (điều kiện riêng của dòng) mở MD; chỉ phạm vi cấp môn thì vẫn ở MT", () => {
    const rich = block("r", { tagCodes: ["x"], knowledgeScope: { nodeIds: ["m"] }, matrix: separate({ byGrade: [], byDifficulty: [{ code: "RECOGNITION", value: 2 }] }) });
    for (const kind of ["SP2E", "SPT"]) {
      expect(mt(kind, "TOAN", {}, [rich])).toBe("BLOCK_MATRIX");
      expect(mt(kind, "TOAN", {}, [block("a", { knowledgeScope: { nodeIds: ["m"] } })])).toBe("BLOCK_SCOPE_OR_TAGS");
      expect(mt(kind, "TOAN", {}, [block("a", { topicQuotas: [{ id: "t", name: "t", nodeIds: ["n"], count: 1, enforcement: "REFERENCE" }] })])).toBe("BLOCK_TOPIC_QUOTAS");
      expect(mt(kind, "TOAN", {}, [block("a", { tagCodes: ["TOAN_DU_LIEU_TONG_HOP"] })])).toBe("BLOCK_SCOPE_OR_TAGS");
      expect(mt(kind, "TOAN", { knowledgeScope: { nodeIds: ["m"] } }, [block("a")])).toBe("FITS");
      expect(mt(kind, "TOAN", { mapidMode: "MATRIX" }, [block("a", { knowledgeScope: { nodeIds: ["m"] } })])).toBe("FITS");
    }
  });

  it("DS: ma trận theo năng lực (byCompetency) ở mục hoặc dòng rơi về MD; mảng rỗng vẫn khớp", () => {
    const competency = [{ code: "VACT-TD-LOGIC", value: 12 }, { code: "VACT-TD-SL", value: 18 }];
    expect(ds("VACT", "SCIENTIFIC_THINKING", [block("b", { matrix: separate({ byCompetency: competency }) })])).toBe("ROW_CONDITION_UNSUPPORTED");
    expect(ds("VACT", "SCIENTIFIC_THINKING", [block("b")], { matrix: separate({ byCompetency: competency }) })).toBe("ROW_CONDITION_UNSUPPORTED");
    expect(ds("VACT", "SCIENTIFIC_THINKING", [block("b", { matrix: separate({ byCompetency: [] }) })])).toBe("FITS");
  });
});

describe("Khớp biểu mẫu: mẫu chuẩn của fixture (plan B8, FTM AC 5)", () => {
  const rows = standardFixtureTemplates().flatMap((template) =>
    template.parts.flatMap((p) =>
      [...sectionGroups(p)].flatMap((group) => group.sections.map((s) => ({ template, part: p, group, section: s }))),
    ),
  );

  it("fixture có đúng nguồn (SHA, ngày xuất) và không lẫn mẫu bài ôn", () => {
    expect(fixtureSource.sha).toMatch(/^[0-9a-f]{40}$/);
    expect(fixtureSource.exportedAt).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(standardFixtureTemplates().every((template) => template.buildMode !== "REVIEW")).toBe(true);
    expect(rows.length).toBeGreaterThan(0);
  });

  it("mọi môn của mọi mẫu chuẩn có mục trong bảng đăng ký và khớp biểu mẫu; mẫu thất bại được nêu tên", () => {
    const failures: string[] = [];
    const covered = new Set<string>();
    const coverage: string[] = [];
    for (const { template, group, section: target } of rows) {
      const entry = subjectFormFor(template.kindCode, target.subjectCode);
      const where = `${template.code} (${template.kindCode}/${template.scope}${template.partCode ? `/${template.partCode}` : ""}${template.structureYear ? `/${template.structureYear}` : ""}) ${target.subjectCode} @${group.pool ? "subjectPool" : group.optionId ? "options" : "sections"}`;
      if (!entry) {
        failures.push(`${where}: khong co muc`);
        continue;
      }
      covered.add(`${template.kindCode}:${target.subjectCode}`);
      const result = fitsForm(target, entry, { kindCode: template.kindCode, subjectCode: target.subjectCode });
      coverage.push(`${where} -> ${entry.form} ${result.fits ? "fits" : `NOT FIT ${result.reason}`}`);
      if (!result.fits) failures.push(`${where}: ${result.reason}`);
    }
    console.info(`Bang phu mau (${fixtureSource.sha.slice(0, 8)}, ${fixtureSource.exportedAt}): ${standardFixtureTemplates().length} mau, ${rows.length} muc\n${coverage.join("\n")}`);
    const uncovered = listSubjectFormPairs().filter(({ kindCode, subjectCode }) => !covered.has(`${kindCode}:${subjectCode}`));
    console.info(`Cap chua co mau chuan trong fixture: ${uncovered.map(({ kindCode, subjectCode }) => `${kindCode}:${subjectCode}`).join(", ") || "khong"}`);
    // UNI-2876: Phần II "Dữ liệu tổng hợp" của SP2E Toán 2026 là nhóm câu đơn mang tag TOAN_DU_LIEU_TONG_HOP; MT không có chỗ xem/sửa tag
    // của nhóm câu đơn nên mục này mở MD (thiết lập đầy đủ), như mọi nhóm câu đơn có tag ở HSA, QDA, BCA.
    expect(failures).toEqual(["SP2E_MATH_2026 (SP2E/SUBJECT/2026) TOAN @sections: BLOCK_SCOPE_OR_TAGS"]);
  });
});
