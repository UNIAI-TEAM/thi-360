import { describe, expect, it } from "vitest";
import reference from "@/features/content/mock/reference.json";
import type { ContentReference } from "@/features/content/types";
import {
  choicePathCount,
  combinationsForKind,
  createExamSchema,
  type CreateExamFormValues,
  type CreateExamSchemaMessages,
  type ExamFormat,
  type ExamScope,
  findTemplate,
  formatOfScope,
  initialParts,
  NO_TEMPLATE,
  partsEdited,
  resolveScope,
  scopeDefaults,
  subjectTemplates,
  summarizeParts,
  toCreateExamRequest,
  yearsDiffer,
} from "../create-exam-schema";
import { MOCK_EXAM_TEMPLATES } from "../mock/store";
import type { ExamKind } from "../types";

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

function parts(kind: ExamKind, format: ExamFormat, singleScope = "") {
  return initialParts({
    templates: MOCK_EXAM_TEMPLATES,
    kind,
    format,
    singleScope,
    combinations: combinationsForKind(COMBINATIONS, kind),
    scopeName: (scope) => scope,
  });
}

function values(overrides: Partial<CreateExamFormValues> = {}): CreateExamFormValues {
  return {
    name: "HSA FULL thu",
    code: "hsa-f-003",
    kind: "HSA",
    format: "FULL",
    singleSubjectCode: "",
    subjectCode: "",
    templateCode: "HSA_FULL",
    timing: "PER_PART",
    sharedDuration: 0,
    maxScore: 150,
    yearsConfirmed: false,
    description: "",
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
      expertConfirmed: false,
    });
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
    expect(parts("QDA", "SINGLE", "MATH")).toMatchObject([{ mode: "FIXED", subjectCode: "MATH", questionCount: 50, durationMinutes: 80 }]);
    const science = parts("HSA", "SINGLE", "SCIENCE");
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
    const request = toCreateExamRequest(values(), KINDS);
    expect(request).toMatchObject({ code: "HSA-F-003", scope: "FULL", partCode: null, description: null });
    expect(request.parts?.[2]).toMatchObject({ mode: "CHOICE", subjectCode: null, englishOption: true });
  });

  it("mã trùng (đã biết từ máy chủ) bị báo, không phân biệt hoa thường", () => {
    expect(errors(values({ code: "hsa-f-001" }), new Set(["HSA-F-001"]))).toEqual({ code: "codeTaken" });
    expect(errors(values({ code: "HSA F 1" }))).toEqual({ code: "codeInvalid" });
  });

  it("bài lẻ phải chọn phần/môn", () => {
    expect(errors(values({ format: "SINGLE", singleSubjectCode: "", parts: parts("HSA", "SINGLE", "MATH") }))).toEqual({
      singleSubjectCode: "subjectRequired",
    });
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
  const found = findTemplate(MOCK_EXAM_TEMPLATES, code);
  if (!found) throw new Error(`thieu mau ${code}`);
  return found;
}

function kindOption(code: string) {
  return KINDS.find((kind) => kind.code === code);
}

function templateParts(code: string) {
  const source = template(code);
  return initialParts({
    templates: MOCK_EXAM_TEMPLATES,
    kind: source.kind,
    format: formatOfScope(source.scope),
    singleScope: "",
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
    const bcaValues = values({ kind: "BCA", code: "BCA-F-009", templateCode: "BCA_FULL", ...defaults, parts: bca });
    expect(errors(bcaValues)).toEqual({});
    const request = toCreateExamRequest(bcaValues, KINDS);
    expect(request).toMatchObject({ durationMinutes: 180, maxScore: 100 });
    expect(request.parts?.[0]).toMatchObject({ durationMinutes: null, points: 25 });
    expect(request.parts?.[1]?.sections).toHaveLength(3);
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
    expect(errors(values({ kind: "BCA", templateCode: "BCA_FULL", ...defaults, sharedDuration: 0, parts: broken }))).toEqual({
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
      subjectCode: "INFORMATICS",
      templateCode: "THPTQG_INFORMATICS_2025",
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
    expect(subjectTemplates(MOCK_EXAM_TEMPLATES, "SP2E", "FULL", "MATH").map((item) => item.code)).toEqual([
      "SP2E_MATH_2026",
      "SP2E_MATH_2025",
    ]);
    expect(yearsDiffer(template("SP2E_MATH_2026"))).toBe(true);
    expect(yearsDiffer(template("SP2E_MATH_2025"))).toBe(false);
    const sp2e = templateParts("SP2E_MATH_2026");
    expect(sp2e[0]!.durationMinutes).toBe(0);
    const input = values({
      kind: "SP2E",
      subjectCode: "MATH",
      templateCode: "SP2E_MATH_2026",
      maxScore: 10,
      yearsConfirmed: true,
      parts: sp2e,
    });
    expect(errors(input)).toEqual({ "parts.0.durationMinutes": "durationPositive" });
    // Bước 1 gửi phạm vi và môn; mẫu và xác nhận năm đi sau (applyExamTemplate, updateExamInfo).
    const request = toCreateExamRequest(input, KINDS);
    expect(request).toMatchObject({ scope: "SUBJECT", subjectCode: "MATH" });
    expect(request).not.toHaveProperty("templateCode");
    expect(request).not.toHaveProperty("yearsConfirmed");
  });

  it("kỳ thi theo môn phải chọn môn; thang điểm bắt buộc; V-ACT thang 1.200 chia điểm theo phần", () => {
    expect(errors(values({ kind: "THPTQG", templateCode: NO_TEMPLATE, maxScore: 10 }))).toMatchObject({
      subjectCode: "kindSubjectRequired",
    });
    expect(errors(values({ maxScore: null }))).toEqual({ maxScore: "maxScoreRequired" });
    expect(errors(values({ maxScore: 0 }))).toEqual({ maxScore: "maxScorePositive" });
    const vact = templateParts("VACT_FULL");
    const defaults = scopeDefaults(template("VACT_FULL"), kindOption("VACT"), "FULL");
    expect(defaults).toMatchObject({ timing: "SHARED", sharedDuration: 150, maxScore: 1200, pointsByPart: true });
    expect(errors(values({ kind: "VACT", templateCode: "VACT_FULL", ...defaults, parts: vact }))).toEqual({});
  });

  it("đổi phạm vi: hình thức không có trong kỳ thi về FULL; giữ môn khi kỳ thi mới có mẫu; mẫu chọn tay được giữ", () => {
    const hsaSingle: ExamScope = {
      kind: "HSA",
      format: "SINGLE",
      singleScope: "MATH",
      subjectCode: "",
      templateCode: NO_TEMPLATE,
    };
    expect(resolveScope(MOCK_EXAM_TEMPLATES, KINDS, SUBJECTS, hsaSingle, { kind: "BCA" })).toEqual({
      kind: "BCA",
      format: "FULL",
      singleScope: "",
      subjectCode: "",
      templateCode: "BCA_FULL",
    });
    const literature = resolveScope(MOCK_EXAM_TEMPLATES, KINDS, SUBJECTS, hsaSingle, {
      kind: "THPTQG",
      subjectCode: "LITERATURE",
    });
    expect(literature).toMatchObject({ subjectCode: "LITERATURE", templateCode: "THPTQG_LITERATURE_2025" });
    const sp2e = resolveScope(MOCK_EXAM_TEMPLATES, KINDS, SUBJECTS, literature, { kind: "SP2E" });
    expect(sp2e).toMatchObject({ kind: "SP2E", subjectCode: "MATH", templateCode: "SP2E_MATH_2026" });
    expect(resolveScope(MOCK_EXAM_TEMPLATES, KINDS, SUBJECTS, sp2e, { templateCode: "SP2E_MATH_2025" }).templateCode).toBe(
      "SP2E_MATH_2025",
    );
    expect(resolveScope(MOCK_EXAM_TEMPLATES, KINDS, SUBJECTS, sp2e, { kind: "THPTQG" })).toMatchObject({
      subjectCode: "MATH",
      templateCode: "THPTQG_MATH_2025",
    });
  });

  it("môn chưa có mẫu: một phần trống của môn, thang điểm theo kỳ thi", () => {
    const blank = initialParts({
      templates: MOCK_EXAM_TEMPLATES,
      kind: "SPT",
      format: "FULL",
      singleScope: "",
      combinations: [],
      scopeName: (scope) => scope,
      template: null,
      subjectCode: "PHYSICS",
      subjectName: () => "Vat li",
    });
    expect(blank).toMatchObject([{ name: "Vat li", mode: "FIXED", subjectCode: "PHYSICS", questionCount: 0 }]);
    expect(scopeDefaults(undefined, kindOption("SPT"), "FULL")).toEqual({
      timing: "PER_PART",
      sharedDuration: 0,
      maxScore: 10,
      pointsByPart: false,
    });
    expect(scopeDefaults(undefined, kindOption("QDA"), "SINGLE").maxScore).toBeNull();
  });
});
