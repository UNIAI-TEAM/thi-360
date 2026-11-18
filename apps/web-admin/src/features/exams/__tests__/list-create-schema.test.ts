import { describe, expect, it } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import {
  choicePathCount,
  combinationsForKind,
  createExamSchema,
  type CreateExamFormValues,
  type CreateExamSchemaMessages,
  findTemplate,
  initialParts,
  latestTemplate,
  matchingTemplates,
  partsEdited,
  partCodesForKind,
  resolveScope,
  scopeDefaults,
  scopeTemplate,
  type ScopeSelection,
  scopesForKind,
  summarizeParts,
  toCreateExamRequest,
  yearsDiffer,
} from "../create-exam-schema";
import type { ExamKind, ExamOverviewRow, ExamScope } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";

const MESSAGES: CreateExamSchemaMessages = {
  nameRequired: "nameRequired",
  nameMax: "nameMax",
  codeRequired: "codeRequired",
  codeInvalid: "codeInvalid",
  codeMax: "codeMax",
  codeTaken: "codeTaken",
  subjectRequired: "subjectRequired",
  descriptionMax: "descriptionMax",
  partNameRequired: "partNameRequired",
  partNameMax: "partNameMax",
  partSubjectRequired: "partSubjectRequired",
  questionCountPositive: "questionCountPositive",
  durationPositive: "durationPositive",
  noOptions: "noOptions",
  subjectCountPositive: "subjectCountPositive",
  allocationTotal: (actual, expected) => `allocationTotal ${actual}/${expected}`,
  partsRequired: "partsRequired",
  kindSubjectRequired: "kindSubjectRequired",
  sharedDurationPositive: "sharedDurationPositive",
  maxScoreRequired: "maxScoreRequired",
  maxScorePositive: "maxScorePositive",
  pointsPositive: "pointsPositive",
  commonTooLarge: (common, total) => `commonTooLarge ${common}/${total}`,
};

const REFERENCE = reference as ContentReference;
const COMBINATIONS = REFERENCE.combinations;
const KINDS = REFERENCE.examKinds;
const SUBJECTS = REFERENCE.subjects;

function parts(kind: ExamKind, scope: ExamScope, partCode = "") {
  return initialParts({
    templates: FIXTURE_TEMPLATES,
    kind,
    scope,
    partCode,
    combinations: combinationsForKind(COMBINATIONS, kind),
    scopeName: (scope) => scope,
  });
}

/** Yêu cầu tạo bài bước 1 từ giá trị mẫu của test (kỳ thi, phạm vi, môn, phần lấy từ `input`). */
function toRequest(input: CreateExamFormValues, overview: ExamOverviewRow[] = []) {
  return toCreateExamRequest({
    name: input.name,
    code: input.code,
    selection: { kind: input.kind, scope: input.scope, partCode: input.partCode, subjectCode: input.subjectCode },
    description: input.description,
    instructions: input.instructions,
    timing: input.timing,
    sharedDuration: input.timing === "SHARED" ? input.sharedDuration : null,
    maxScore: input.maxScore,
    overview,
  });
}

function values(overrides: Partial<CreateExamFormValues> = {}): CreateExamFormValues {
  return {
    name: "HSA FULL thu",
    code: "hsa-f-003",
    kind: "HSA",
    scope: "FULL",
    partCode: "",
    subjectCode: "",
    timing: "PER_PART",
    sharedDuration: 0,
    maxScore: 150,
    yearsConfirmed: false,
    description: "",
    instructions: "",
    pointsByPart: false,
    parts: parts("HSA", "FULL"),
    ...overrides,
  };
}

/** Lỗi đầu tiên theo đường dẫn trường. */
function errors(input: CreateExamFormValues, taken: ReadonlySet<string> = new Set()) {
  const result = createExamSchema(MESSAGES, taken, KINDS).safeParse(input);
  if (result.success) return {};
  const map: Record<string, string> = {};
  for (const issue of result.error.issues) map[issue.path.join(".")] ??= issue.message;
  return map;
}

describe("mẫu cấu trúc khởi tạo", () => {
  it("HSA FULL: 3 phần 150 câu 195 phút, phần 3 có Tiếng Anh và một phương án Khoa học chọn 3 trong 5 môn", () => {
    const hsa = parts("HSA", "FULL");
    expect(hsa.map((part) => [part.questionCount, part.durationMinutes, part.mode, part.sections[0]?.subjectCode ?? ""])).toEqual([
      [50, 75, "FIXED", "MATH"],
      [50, 60, "FIXED", "LITERATURE"],
      [50, 60, "CHOICE", ""],
    ]);
    expect(hsa[2]!.englishOption).toBe(true);
    expect(hsa[2]!.combinations).toEqual([]);
    expect(hsa[2]!.subjectChoice).toMatchObject({
      code: "SCIENCE",
      chooseSubjects: 3,
      countsByPick: [17, 17, 16],
    });
    expect(hsa[2]!.subjectChoice).not.toHaveProperty("expertConfirmed");
    expect(hsa[2]!.subjectChoice!.sections.map((section) => section.subjectCode)).toEqual([
      "PHYSICS",
      "CHEMISTRY",
      "BIOLOGY",
      "HISTORY",
      "GEOGRAPHY",
    ]);
    expect(summarizeParts(hsa)).toEqual({
      partCount: 3,
      totalQuestions: 150,
      totalMinutes: 195,
      pathCount: 2,
      totalPoints: null,
    });
  });

  it("QDA FULL: Toán 50/80, Văn 50/55, phần 3 50/60 với tổ hợp 2 môn 25–25", () => {
    const qda = parts("QDA", "FULL");
    expect(qda.map((part) => [part.questionCount, part.durationMinutes])).toEqual([
      [50, 80],
      [50, 55],
      [50, 60],
    ]);
    expect(qda[2]!.combinations.map((item) => [item.code, item.subjectCounts])).toEqual([
      ["QD01", [25, 25]],
      ["QD02", [25, 25]],
      ["QD03", [25, 25]],
      ["QD04", [25, 25]],
    ]);
  });

  it("bài lẻ: Toán theo phần Toán của mẫu FULL; Khoa học là phần tự chọn không có Tiếng Anh", () => {
    expect(parts("QDA", "PART", "MATH")).toMatchObject([{ mode: "FIXED", subjectCode: "MATH", questionCount: 50, durationMinutes: 80 }]);
    const science = parts("HSA", "PART", "SCIENCE");
    expect(science).toMatchObject([{ mode: "CHOICE", englishOption: false, questionCount: 50, durationMinutes: 60 }]);
    expect(summarizeParts(science).pathCount).toBe(3);
  });

  it("biết cấu trúc đã bị sửa so với mẫu", () => {
    const base = parts("HSA", "FULL");
    expect(partsEdited(base.map((part) => ({ ...part, key: "khac" })), base)).toBe(false);
    expect(partsEdited([{ ...base[0]!, questionCount: 40 }, ...base.slice(1)], base)).toBe(true);
  });
});

describe("kiểm tra trang Tạo bài thi", () => {
  it("mẫu hợp lệ thì không có lỗi; mã đưa về chữ hoa khi gửi", () => {
    expect(errors(values())).toEqual({});
    const request = toRequest(values({ instructions: "  Chọn tổ hợp trước khi làm bài.  " }));
    expect(request).toMatchObject({
      code: "HSA-F-003",
      scope: "FULL",
      partCode: null,
      subjectCode: null,
      description: null,
      instructions: "Chọn tổ hợp trước khi làm bài.",
    });
    expect(toRequest(values()).instructions).toBeNull();
    expect(request).not.toHaveProperty("parts");
  });

  it("hướng dẫn HTML không còn chữ nào (đoạn rỗng, khoảng trắng) gửi thành null, có chữ thì giữ HTML", () => {
    for (const blank of ["<p></p>", "<p></p><p></p>", "<p> </p>", "<p><br></p>", "  "]) {
      expect(toRequest(values({ instructions: blank })).instructions).toBeNull();
    }
    expect(toRequest(values({ instructions: " <p>Đọc kỹ đề.</p> " })).instructions).toBe("<p>Đọc kỹ đề.</p>");
  });

  it("mã trùng (đã biết từ máy chủ) bị báo, không phân biệt hoa thường", () => {
    expect(errors(values({ code: "hsa-f-001" }), new Set(["HSA-F-001"]))).toEqual({ code: "codeTaken" });
    expect(errors(values({ code: "HSA F 1" }))).toEqual({ code: "codeInvalid" });
  });

  it("Theo phần phải chọn phần; Theo môn phải chọn môn; hướng dẫn làm bài là HTML nên không giới hạn độ dài", () => {
    expect(errors(values({ scope: "PART", partCode: "", parts: parts("HSA", "PART", "MATH") }))).toEqual({
      partCode: "subjectRequired",
    });
    expect(errors(values({ kind: "SPT", scope: "SUBJECT", subjectCode: "", maxScore: 10 }))).toMatchObject({
      subjectCode: "kindSubjectRequired",
    });
    expect(errors(values({ instructions: `<p>${"x".repeat(5001)}</p>` }))).toEqual({});
  });

  it("số câu và thời lượng của phần phải là số nguyên dương; phần cố định phải có môn", () => {
    const base = parts("HSA", "FULL");
    const broken = [{ ...base[0]!, questionCount: 0, durationMinutes: 0, subjectCode: "", sections: [], name: " " }, ...base.slice(1)];
    expect(errors(values({ parts: broken }))).toEqual({
      "parts.0.name": "partNameRequired",
      "parts.0.questionCount": "questionCountPositive",
      "parts.0.durationMinutes": "durationPositive",
      "parts.0.subjectCode": "partSubjectRequired",
    });
    expect(errors(values({ parts: [] }))).toEqual({ parts: "partsRequired" });
  });

  it("tổng số câu các môn của tổ hợp phải bằng số câu của phần (51/50 bị báo)", () => {
    const base = parts("QDA", "FULL");
    const choice = base[2]!;
    const over = { ...choice, combinations: choice.combinations.map((item, index) => (index === 1 ? { ...item, subjectCounts: [25, 26] } : item)) };
    expect(errors(values({ kind: "QDA", parts: [base[0]!, base[1]!, over] }))).toEqual({
      "parts.2.combinations.1.subjectCounts": "allocationTotal 51/50",
    });
    const zero = { ...choice, combinations: [{ code: "QD01", subjectCounts: [50, 0] }] };
    expect(errors(values({ kind: "QDA", parts: [base[0]!, base[1]!, zero] }))).toEqual({
      "parts.2.combinations.0.subjectCounts": "subjectCountPositive",
    });
  });

  it("phần tự chọn phải có ít nhất một đường lựa chọn", () => {
    const base = parts("HSA", "FULL");
    const empty = { ...base[2]!, englishOption: false, combinations: [], subjectChoice: null };
    expect(errors(values({ parts: [base[0]!, base[1]!, empty] }))).toEqual({ "parts.2.combinations": "noOptions" });
    expect(summarizeParts([base[0]!, base[1]!, empty]).pathCount).toBe(0);
  });
});

function template(code: string) {
  const found = findTemplate(FIXTURE_TEMPLATES, code);
  if (!found) throw new Error(`thieu mau ${code}`);
  return found;
}

function kindOption(code: string) {
  return KINDS.find((kind) => kind.code === code);
}

function templateParts(code: string) {
  const source = template(code);
  return initialParts({
    templates: FIXTURE_TEMPLATES,
    kind: source.kind,
    scope: source.scope,
    partCode: "",
    combinations: combinationsForKind(COMBINATIONS, source.kind),
    scopeName: (scope) => scope,
    template: source,
    subjectCode: source.subjectCode ?? "",
  });
}

describe("bảy kỳ thi: mẫu nạp vào trang Tạo bài thi", () => {
  it("BCA: thời lượng chung 180 phút, điểm 25/60/15, phần 2 nhiều môn 35/10/20, phần 3 tổ hợp CA1–CA4 kèm khối", () => {
    const bca = templateParts("BCA_FULL");
    expect(bca.map((part) => [part.mode, part.questionCount, part.points])).toEqual([
      ["FIXED", 1, 25],
      ["FIXED", 65, 60],
      ["CHOICE", 15, 15],
    ]);
    expect(bca[0]!.blocks).toMatchObject([{ questionType: "ESSAY", requiredPointsPerQuestion: 25 }]);
    expect(bca[1]!.sections.map((section) => [section.subjectCode, section.questionCount])).toEqual([
      ["MATH", 35],
      ["HISTORY", 10],
      ["ENGLISH", 20],
    ]);
    expect(bca[2]!.englishOption).toBe(false);
    expect(bca[2]!.combinations.map((item) => [item.code, item.subjectCounts, item.blocks?.length])).toEqual([
      ["CA1", [15], 1],
      ["CA2", [15], 1],
      ["CA3", [15], 1],
      ["CA4", [15], 1],
    ]);
    const defaults = scopeDefaults(template("BCA_FULL"), kindOption("BCA"), "FULL");
    expect(defaults).toEqual({ timing: "SHARED", sharedDuration: 180, maxScore: 100, pointsByPart: true });
    expect(summarizeParts(bca, defaults)).toEqual({
      partCount: 3,
      totalQuestions: 81,
      totalMinutes: 180,
      pathCount: 4,
      totalPoints: 100,
    });
    const bcaValues = values({ kind: "BCA", code: "BCA-F-009", ...defaults, parts: bca });
    expect(errors(bcaValues)).toEqual({});
    const request = toRequest(bcaValues);
    expect(request).toMatchObject({ durationMinutes: 180, maxScore: 100 });
    expect(request).not.toHaveProperty("parts");
  });

  it("BCA: thời lượng chung phải dương; điểm phần phải nhập khi chia điểm theo phần; phần nhiều môn phải khớp tổng", () => {
    const defaults = scopeDefaults(template("BCA_FULL"), kindOption("BCA"), "FULL");
    const bca = templateParts("BCA_FULL");
    const broken = [
      { ...bca[0]!, points: null },
      {
        ...bca[1]!,
        sections: bca[1]!.sections.map((section, index) => (index === 0 ? { ...section, questionCount: 30 } : section)),
      },
      bca[2]!,
    ];
    expect(errors(values({ kind: "BCA", ...defaults, sharedDuration: 0, parts: broken }))).toEqual({
      sharedDuration: "sharedDurationPositive",
      "parts.0.points": "pointsPositive",
      "parts.1.sections": "allocationTotal 60/65",
    });
  });

  it("THPTQG Tin học: phần tự chọn có phần chung 2 câu và hai nhánh 2 câu; nhánh sai số câu bị báo", () => {
    const informatics = templateParts("THPTQG_INFORMATICS_2025");
    const choice = informatics[1]!;
    expect(choice.mode).toBe("CHOICE");
    expect(choice.sections.map((section) => [section.subjectCode, section.questionCount])).toEqual([["INFORMATICS", 2]]);
    expect(choice.branches.map((branch) => [branch.code, branch.questionCount])).toEqual([
      ["CS", 2],
      ["ICT", 2],
    ]);
    expect(choice.englishOption).toBe(false);
    expect(choicePathCount(choice)).toBe(2);
    const defaults = scopeDefaults(template("THPTQG_INFORMATICS_2025"), kindOption("THPTQG"), "FULL");
    expect(defaults.timing).toBe("SHARED");
    const base = values({
      kind: "THPTQG",
      scope: "SUBJECT",
      subjectCode: "INFORMATICS",
      ...defaults,
      parts: informatics,
    });
    expect(errors(base)).toEqual({});
    const wrong = {
      ...choice,
      branches: choice.branches.map((branch, index) => (index === 1 ? { ...branch, questionCount: 3 } : branch)),
    };
    expect(errors({ ...base, parts: [informatics[0]!, wrong] })).toEqual({
      "parts.1.branches.1.questionCount": "allocationTotal 3/2",
    });
    const tooLarge = { ...choice, sections: [{ ...choice.sections[0]!, questionCount: 4 }] };
    expect(errors({ ...base, parts: [informatics[0]!, tooLarge] })).toEqual({ "parts.1.sections": "commonTooLarge 4/4" });
  });

  it("SP2E Toán: mẫu theo năm, mới nhất trước; năm cấu trúc 2026 khác năm ma trận 2025 thì gửi xác nhận", () => {
    const math: ScopeSelection = { kind: "SP2E", scope: "SUBJECT", partCode: "", subjectCode: "MATH" };
    expect(matchingTemplates(FIXTURE_TEMPLATES, math).map((item) => item.code)).toEqual([
      "SP2E_MATH_2026",
      "SP2E_MATH_2025",
    ]);
    expect(latestTemplate(FIXTURE_TEMPLATES, math)?.code).toBe("SP2E_MATH_2026");
    expect(yearsDiffer(template("SP2E_MATH_2026"))).toBe(true);
    expect(yearsDiffer(template("SP2E_MATH_2025"))).toBe(false);
    const sp2e = templateParts("SP2E_MATH_2026");
    // SP2E Toán 2026: 40 câu, 90 phút (PBI-04 mục 5.1).
    expect(sp2e[0]).toMatchObject({ questionCount: 40, durationMinutes: 90 });
    const input = values({
      kind: "SP2E",
      scope: "SUBJECT",
      subjectCode: "MATH",
      maxScore: 10,
      yearsConfirmed: true,
      parts: sp2e,
    });
    expect(errors(input)).toEqual({});
    // Bước 1 gửi phạm vi và môn; mẫu và xác nhận năm đi sau (applyExamTemplate, updateExamInfo).
    const request = toRequest(input);
    expect(request).toMatchObject({ scope: "SUBJECT", subjectCode: "MATH" });
    expect(request).not.toHaveProperty("templateCode");
    expect(request).not.toHaveProperty("yearsConfirmed");
  });

  it("kỳ thi theo môn phải chọn môn; thang điểm bắt buộc; V-ACT thang 1.200 chia điểm theo phần", () => {
    expect(errors(values({ kind: "THPTQG", scope: "SUBJECT", maxScore: 10 }))).toMatchObject({
      subjectCode: "kindSubjectRequired",
    });
    expect(errors(values({ maxScore: null }))).toEqual({ maxScore: "maxScoreRequired" });
    expect(errors(values({ maxScore: 0 }))).toEqual({ maxScore: "maxScorePositive" });
    const vact = templateParts("VACT_FULL");
    const defaults = scopeDefaults(template("VACT_FULL"), kindOption("VACT"), "FULL");
    expect(defaults).toMatchObject({ timing: "SHARED", sharedDuration: 150, maxScore: 1200, pointsByPart: true });
    expect(errors(values({ kind: "VACT", ...defaults, parts: vact }))).toEqual({});
  });

  it("phạm vi theo hồ sơ kỳ thi (2B 5.1): HSA, QDA, BCA ba phạm vi; SP2E, SPT Theo môn và Theo phần; V-ACT Toàn bài; THPTQG Theo môn", () => {
    expect(scopesForKind(KINDS, "HSA")).toEqual(["FULL", "PART", "SUBJECT"]);
    expect(scopesForKind(KINDS, "QDA")).toEqual(["FULL", "PART", "SUBJECT"]);
    expect(scopesForKind(KINDS, "BCA")).toEqual(["FULL", "PART", "SUBJECT"]);
    expect(scopesForKind(KINDS, "SP2E")).toEqual(["PART", "SUBJECT"]);
    expect(scopesForKind(KINDS, "SPT")).toEqual(["PART", "SUBJECT"]);
    expect(scopesForKind(KINDS, "VACT")).toEqual(["FULL"]);
    expect(scopesForKind(KINDS, "THPTQG")).toEqual(["SUBJECT"]);
  });

  it("đổi phạm vi: phạm vi không có trong kỳ thi về Toàn bài, không có thì Theo môn; giữ môn khi kỳ thi mới có mẫu cho môn đó", () => {
    const hsaPart: ScopeSelection = { kind: "HSA", scope: "PART", partCode: "MATH", subjectCode: "" };
    // BCA cho Theo phần: giữ phạm vi.
    expect(resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, hsaPart, { kind: "BCA" })).toMatchObject({ kind: "BCA", scope: "PART" });
    // V-ACT chỉ có Toàn bài.
    expect(resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, hsaPart, { kind: "VACT" })).toEqual({
      kind: "VACT",
      scope: "FULL",
      partCode: "",
      subjectCode: "",
      buildMode: undefined,
    });
    const literature = resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, hsaPart, {
      kind: "THPTQG",
      subjectCode: "LITERATURE",
    });
    expect(literature).toEqual({ kind: "THPTQG", scope: "SUBJECT", partCode: "", subjectCode: "LITERATURE", buildMode: "STANDARD" });
    expect(latestTemplate(FIXTURE_TEMPLATES, literature)?.code).toBe("THPTQG_LITERATURE_2025");
    // Sang SP2E: môn Ngữ văn có mẫu SP2E 2026 nên giữ môn; mẫu mới nhất theo năm cấu trúc.
    const sp2e = resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, literature, { kind: "SP2E" });
    expect(sp2e).toMatchObject({ kind: "SP2E", scope: "SUBJECT", subjectCode: "LITERATURE" });
    expect(latestTemplate(FIXTURE_TEMPLATES, sp2e)?.code).toBe("SP2E_LITERATURE_2026");
    // Sang Theo phần của HSA: mặc định phần Toán, buildMode bỏ.
    expect(resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, sp2e, { kind: "HSA", scope: "PART" })).toEqual({
      kind: "HSA",
      scope: "PART",
      partCode: "MATH",
      subjectCode: "",
      buildMode: undefined,
    });
  });

  it("mẫu SP2E 9 môn (2026) và SPT 12 môn theo bảng số câu, thời lượng của PBI-04 mục 5.1", () => {
    const table = (kind: string) =>
      FIXTURE_TEMPLATES.filter((item) => item.kind === kind && item.structureYear === (kind === "SP2E" ? 2026 : 2025))
        .map((item) => [item.subjectCode, item.parts[0]!.questionCount, item.durationMinutes])
        .sort((x, y) => String(x[0]).localeCompare(String(y[0])));
    expect(table("SP2E")).toEqual(
      [
        ["MATH", 40, 90],
        ["LITERATURE", 22, 90],
        ["ENGLISH", 50, 60],
        ["PHYSICS", 28, 60],
        ["CHEMISTRY", 28, 60],
        ["BIOLOGY", 28, 60],
        ["HISTORY", 32, 60],
        ["GEOGRAPHY", 28, 60],
        ["ECON_LAW", 32, 60],
      ].sort((x, y) => String(x[0]).localeCompare(String(y[0]))),
    );
    expect(table("SPT")).toHaveLength(12);
    expect(table("SPT")).toContainEqual(["MATH", 25, 90]);
    expect(table("SPT")).toContainEqual(["TECH_AGRICULTURE", 24, 60]);
    // Môn chưa có cách chia phần: một phần đủ số câu, kèm ghi chú cho chuyên gia, không có khối bịa.
    const physics = template("SP2E_PHYSICS_2026");
    expect(physics.parts).toHaveLength(1);
    expect(physics.parts[0]).not.toHaveProperty("blocks");
    expect(physics.expertNote).toBeTruthy();
  });

  it("môn chưa có mẫu: một phần trống của môn, thang điểm theo kỳ thi", () => {
    const blank = initialParts({
      templates: FIXTURE_TEMPLATES,
      kind: "SPT",
      scope: "SUBJECT",
      partCode: "",
      combinations: [],
      scopeName: (scope) => scope,
      template: null,
      subjectCode: "PHYSICS",
      subjectName: () => "Vat li",
    });
    expect(blank).toMatchObject([{ name: "Vat li", mode: "FIXED", subjectCode: "PHYSICS", questionCount: 0 }]);
    expect(scopeDefaults(undefined, kindOption("SPT"), "SUBJECT")).toEqual({
      timing: "PER_PART",
      sharedDuration: 0,
      maxScore: 10,
      pointsByPart: false,
    });
    expect(scopeDefaults(undefined, kindOption("QDA"), "PART").maxScore).toBeNull();
  });
});

describe("V-ACT và THPTQG: phạm vi mới và thời lượng bài theo phần (UNI-2677)", () => {
  const VACT_KINDS = KINDS.map((kind) => (kind.code === "VACT" ? { ...kind, scopes: ["FULL", "PART", "SUBJECT"] as ExamScope[] } : kind));
  const base = FIXTURE_TEMPLATES.find((template) => template.code === "HSA_SINGLE")!;
  const vactPart = (partCode: string) => ({ ...base, code: `VACT_PART_${partCode}`, kind: "VACT", scope: "PART" as const, partCode, subjectCode: null });
  const vactSubject = (subjectCode: string) => ({ ...base, code: `VACT_SUBJECT_${subjectCode}`, kind: "VACT", scope: "SUBJECT" as const, partCode: null, subjectCode });
  const VACT_TEMPLATES = [vactPart("LANGUAGE"), vactPart("THINKING"), vactPart("LOGIC"), vactPart("REASONING"), vactSubject("VIETNAMESE"), vactSubject("TOAN")];

  it("V-ACT Theo phần: bốn mã phần theo thứ tự mẫu; Theo môn chỉ chào môn có mẫu theo môn", () => {
    expect(partCodesForKind(VACT_TEMPLATES, VACT_KINDS, "VACT")).toEqual(["LANGUAGE", "THINKING", "LOGIC", "REASONING"]);
    const current: ScopeSelection = { kind: "VACT", scope: "FULL", partCode: "", subjectCode: "" };
    expect(resolveScope(VACT_TEMPLATES, VACT_KINDS, SUBJECTS, current, { scope: "PART" })).toMatchObject({ scope: "PART", partCode: "LANGUAGE" });
    expect(resolveScope(VACT_TEMPLATES, VACT_KINDS, SUBJECTS, current, { scope: "SUBJECT" })).toMatchObject({ scope: "SUBJECT", subjectCode: "VIETNAMESE" });
  });

  it("THPTQG Theo phần: thời lượng không lấy từ mẫu môn mà do người nhập (H35)", () => {
    const selection: ScopeSelection = { kind: "THPTQG", scope: "PART", partCode: "P1", subjectCode: "MATH" };
    const template = scopeTemplate(FIXTURE_TEMPLATES, KINDS, selection)!;
    expect(template).toMatchObject({ timing: "SHARED", durationMinutes: null });
    expect(scopeDefaults(template, kindOption("THPTQG"), "PART")).toMatchObject({ timing: "SHARED", sharedDuration: 0 });
  });

  it("SP2E Theo phần vẫn lấy thời lượng của mẫu môn (H35 chỉ áp cho THPTQG)", () => {
    const selection: ScopeSelection = { kind: "SP2E", scope: "PART", partCode: "P1", subjectCode: "MATH" };
    expect(scopeTemplate(FIXTURE_TEMPLATES, KINDS, selection)).toMatchObject({ timing: "PER_PART", durationMinutes: 90 });
  });
});
