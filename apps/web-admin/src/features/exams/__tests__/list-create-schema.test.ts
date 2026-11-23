import { describe, expect, it } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import {
  combinationsForKind,
  createExamInfoSchema,
  type ExamInfoFormValues,
  type ExamInfoSchemaMessages,
  findTemplate,
  latestTemplate,
  matchingTemplates,
  partCodesForKind,
  resolveScope,
  scopeDefaults,
  scopeTemplate,
  type ScopeSelection,
  scopesForKind,
  toCreateExamRequest,
  yearsDiffer,
} from "../create-exam-schema";
import { overviewDiff, overviewRowsFromTemplate, updateRowCell, validateOverview } from "../create-exam-overview";
import type { ExamKind, ExamOverviewRow, ExamScope, ExamTemplate } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";

const MESSAGES: ExamInfoSchemaMessages = {
  nameRequired: "nameRequired",
  nameMax: "nameMax",
  codeRequired: "codeRequired",
  codeInvalid: "codeInvalid",
  codeMax: "codeMax",
  codeTaken: "codeTaken",
  subjectRequired: "subjectRequired",
  kindSubjectRequired: "kindSubjectRequired",
  sharedDurationPositive: "sharedDurationPositive",
  maxScoreRequired: "maxScoreRequired",
  maxScorePositive: "maxScorePositive",
  descriptionMax: "descriptionMax",
};

const REFERENCE = reference as ContentReference;
const COMBINATIONS = REFERENCE.combinations;
const KINDS = REFERENCE.examKinds;
const SUBJECTS = REFERENCE.subjects;

function toRequest(input: ExamInfoFormValues, overview: ExamOverviewRow[] = []) {
  return toCreateExamRequest({
    name: input.name,
    code: input.code,
    selection: {
      kind: input.kind,
      scope: input.scope,
      partCode: input.partCode,
      subjectCode: input.subjectCode,
      buildMode: input.buildMode,
    },
    description: input.description,
    instructions: input.instructions,
    timing: input.timing,
    sharedDuration: input.timing === "SHARED" ? input.sharedDuration : null,
    maxScore: input.maxScore,
    overview,
  });
}

function values(overrides: Partial<ExamInfoFormValues> = {}): ExamInfoFormValues {
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
    ...overrides,
  };
}

function errors(input: ExamInfoFormValues, taken: ReadonlySet<string> = new Set()) {
  const result = createExamInfoSchema(MESSAGES, taken, KINDS).safeParse(input);
  if (result.success) return {};
  const map: Record<string, string> = {};
  for (const issue of result.error.issues) map[issue.path.join(".")] ??= issue.message;
  return map;
}

function template(code: string) {
  const found = findTemplate(FIXTURE_TEMPLATES, code);
  if (!found) throw new Error(`thieu mau ${code}`);
  return found;
}

function kindOption(code: string) {
  return KINDS.find((kind) => kind.code === code);
}

describe("kiểm tra biểu mẫu Thông tin chung / Tạo bài thi", () => {
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
    expect(errors(values({ scope: "PART", partCode: "" }))).toEqual({
      partCode: "subjectRequired",
    });
    expect(errors(values({ kind: "SPT", scope: "SUBJECT", subjectCode: "", maxScore: 10 }))).toMatchObject({
      subjectCode: "kindSubjectRequired",
    });
    expect(errors(values({ instructions: `<p>${"x".repeat(5001)}</p>` }))).toEqual({});
  });
});

describe("bảy kỳ thi: mẫu nạp vào trang Tạo bài thi và Thông tin chung", () => {
  it("BCA: thời lượng chung 180 phút, điểm 25/60/15, bảng tổng quan khớp mẫu", () => {
    const bcaTemplate = template("BCA_FULL");
    const defaults = scopeDefaults(bcaTemplate, kindOption("BCA"), "FULL");
    expect(defaults).toEqual({ timing: "SHARED", sharedDuration: 180, maxScore: 100, pointsByPart: true });
    const rows = overviewRowsFromTemplate(bcaTemplate, "FULL");
    expect(rows.filter((row) => row.kind === "part").map((row) => [row.questionCount, row.maxPoints])).toEqual([
      ["1", "25"],
      ["65", "60"],
      ["15", "15"],
    ]);
    const bcaValues = values({ kind: "BCA", code: "BCA-F-009", timing: defaults.timing, sharedDuration: defaults.sharedDuration, maxScore: defaults.maxScore });
    expect(errors(bcaValues)).toEqual({});
    const request = toRequest(bcaValues);
    expect(request).toMatchObject({ durationMinutes: 180, maxScore: 100 });
    expect(request).not.toHaveProperty("parts");
    expect(combinationsForKind(COMBINATIONS, "BCA").length).toBeGreaterThan(0);
  });

  it("BCA: thời lượng chung phải dương", () => {
    const defaults = scopeDefaults(template("BCA_FULL"), kindOption("BCA"), "FULL");
    expect(
      errors(values({ kind: "BCA", timing: defaults.timing, sharedDuration: 0, maxScore: defaults.maxScore })),
    ).toEqual({
      sharedDuration: "sharedDurationPositive",
    });
  });

  it("bài ôn Theo môn khớp mẫu <KỲ>_REVIEW không ghi môn, với mọi môn; bài chuẩn chỉ khớp mẫu của môn", () => {
    const review = (kind: ExamKind, code: string) =>
      ({ ...FIXTURE_TEMPLATES[0]!, code, kind, scope: "SUBJECT", subjectCode: null, partCode: null, buildMode: "REVIEW" }) as ExamTemplate;
    const templates = [...FIXTURE_TEMPLATES, review("SPT", "SPT_REVIEW"), review("HSA", "HSA_REVIEW")];
    const select = (kind: ExamKind, subjectCode: string, buildMode: "STANDARD" | "REVIEW"): ScopeSelection => ({
      kind,
      scope: "SUBJECT",
      partCode: "",
      subjectCode,
      buildMode,
    });
    for (const subject of ["MATH", "PHYSICS"]) {
      expect(matchingTemplates(templates, select("SPT", subject, "REVIEW")).map((item) => item.code)).toEqual(["SPT_REVIEW"]);
      expect(latestTemplate(templates, select("HSA", subject, "REVIEW"))?.code).toBe("HSA_REVIEW");
    }
    expect(matchingTemplates(templates, select("SPT", "MATH", "STANDARD")).map((item) => item.code)).toEqual(["SPT_MATH_2025"]);
  });

  it("SP2E Toán: mẫu theo năm, mới nhất trước; năm cấu trúc 2026 khác năm ma trận 2025", () => {
    const math: ScopeSelection = { kind: "SP2E", scope: "SUBJECT", partCode: "", subjectCode: "MATH" };
    expect(matchingTemplates(FIXTURE_TEMPLATES, math).map((item) => item.code)).toEqual([
      "SP2E_MATH_2026",
      "SP2E_MATH_2025",
    ]);
    expect(latestTemplate(FIXTURE_TEMPLATES, math)?.code).toBe("SP2E_MATH_2026");
    expect(yearsDiffer(template("SP2E_MATH_2026"))).toBe(true);
    expect(yearsDiffer(template("SP2E_MATH_2025"))).toBe(false);
    const sp2eRows = overviewRowsFromTemplate(template("SP2E_MATH_2026"), "SUBJECT");
    expect(sp2eRows[0]).toMatchObject({ questionCount: "40", durationMinutes: "90" });
    const input = values({
      kind: "SP2E",
      scope: "SUBJECT",
      subjectCode: "MATH",
      maxScore: 10,
      yearsConfirmed: true,
    });
    expect(errors(input)).toEqual({});
    const request = toRequest(input);
    expect(request).toMatchObject({ scope: "SUBJECT", subjectCode: "MATH" });
    expect(request).not.toHaveProperty("templateCode");
    expect(request).not.toHaveProperty("yearsConfirmed");
  });

  it("kỳ thi theo môn phải chọn môn; thang điểm bắt buộc trừ bài ôn REVIEW; V-ACT thang 1.200 chia điểm theo phần", () => {
    expect(errors(values({ kind: "THPTQG", scope: "SUBJECT", maxScore: 10 }))).toMatchObject({
      subjectCode: "kindSubjectRequired",
    });
    expect(errors(values({ maxScore: null }))).toEqual({ maxScore: "maxScoreRequired" });
    expect(errors(values({ scope: "SUBJECT", subjectCode: "MATH", buildMode: "REVIEW", maxScore: null }))).toEqual({});
    expect(errors(values({ maxScore: 0 }))).toEqual({ maxScore: "maxScorePositive" });
    const defaults = scopeDefaults(template("VACT_FULL"), kindOption("VACT"), "FULL");
    expect(defaults).toMatchObject({ timing: "SHARED", sharedDuration: 150, maxScore: 1200, pointsByPart: true });
    expect(errors(values({ kind: "VACT", timing: defaults.timing, sharedDuration: defaults.sharedDuration, maxScore: defaults.maxScore }))).toEqual({});
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
    expect(resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, hsaPart, { kind: "BCA" })).toMatchObject({ kind: "BCA", scope: "PART" });
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
    const sp2e = resolveScope(FIXTURE_TEMPLATES, KINDS, SUBJECTS, literature, { kind: "SP2E" });
    expect(sp2e).toMatchObject({ kind: "SP2E", scope: "SUBJECT", subjectCode: "LITERATURE" });
    expect(latestTemplate(FIXTURE_TEMPLATES, sp2e)?.code).toBe("SP2E_LITERATURE_2026");
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
    const physics = template("SP2E_PHYSICS_2026");
    expect(physics.parts).toHaveLength(1);
    expect(physics.parts[0]).not.toHaveProperty("blocks");
    expect(physics.expertNote).toBeTruthy();
  });

  it("môn chưa có mẫu: thang điểm mặc định theo kỳ thi", () => {
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

  it("THPTQG Theo phần: mẫu môn dùng một đồng hồ chung thì thời lượng không lấy từ mẫu môn mà do người nhập (H35)", () => {
    const sharedClock = FIXTURE_TEMPLATES.map((template) =>
      template.kind === "THPTQG"
        ? { ...template, timing: "SHARED" as const, durationMinutes: 90, parts: template.parts.map((part) => ({ ...part, durationMinutes: null })) }
        : template,
    );
    const selection: ScopeSelection = { kind: "THPTQG", scope: "PART", partCode: "P1", subjectCode: "MATH" };
    const template = scopeTemplate(sharedClock, KINDS, selection)!;
    expect(template).toMatchObject({ timing: "SHARED", durationMinutes: null });
    expect(scopeDefaults(template, kindOption("THPTQG"), "PART")).toMatchObject({ timing: "SHARED", sharedDuration: 0 });
  });

  it("THPTQG Theo phần (D25): Ngữ văn, Tiếng Anh là mẫu môn một phần PER_PART nên giữ thời lượng của mẫu, nhập được", () => {
    const cases = [
      ["LITERATURE", 120],
      ["ENGLISH", 50],
    ] as const;
    for (const [subjectCode, minutes] of cases) {
      const selection: ScopeSelection = { kind: "THPTQG", scope: "PART", partCode: "P1", subjectCode };
      const template = scopeTemplate(FIXTURE_TEMPLATES, KINDS, selection)!;
      expect(template).toMatchObject({ timing: "PER_PART" });
      expect(template.parts.map((part) => part.durationMinutes)).toEqual([minutes]);
    }
    for (const subjectCode of ["LITERATURE", "ENGLISH"] as const) {
      const template = scopeTemplate(FIXTURE_TEMPLATES, KINDS, { kind: "THPTQG", scope: "PART", partCode: "P1", subjectCode })!;
      const rows = overviewRowsFromTemplate(template, "PART", "P1");
      const partKey = rows.find((row) => row.kind === "part")!.key;
      expect(overviewDiff(updateRowCell(rows, partKey, "durationMinutes", "130"))).toEqual([{ partCode: "P1", durationMinutes: 130 }]);
      const emptied = updateRowCell(rows, partKey, "durationMinutes", "");
      expect(validateOverview(emptied).filter((issue) => issue.rowKey === partKey)).toEqual([]);
      expect(overviewDiff(emptied)).toEqual([]);
    }
  });

  it("Theo phần cắt từ mẫu Theo môn không mang phân đoạn hiển thị (H40: phân đoạn chỉ của bài cả môn)", () => {
    const segments = [{ code: "I", name: "Phần I", fromPosition: 1, toPosition: 25, commandCount: 67, maxPoints: 150 }];
    const withSegments = FIXTURE_TEMPLATES.map((template) =>
      template.code === "SP2E_MATH_2026"
        ? { ...template, parts: template.parts.map((part) => ({ ...part, displaySegments: segments })) }
        : template,
    );
    const whole = scopeTemplate(withSegments, KINDS, { kind: "SP2E", scope: "SUBJECT", partCode: "", subjectCode: "MATH" })!;
    expect(whole.parts[0]!.displaySegments).toEqual(segments);
    const cut = scopeTemplate(withSegments, KINDS, { kind: "SP2E", scope: "PART", partCode: "P1", subjectCode: "MATH" })!;
    expect(cut.parts).toHaveLength(1);
    expect(cut.parts[0]!.displaySegments).toBeUndefined();
    expect(cut.parts[0]!).not.toHaveProperty("displaySegments");
  });

  it("SP2E Theo phần vẫn lấy thời lượng của mẫu môn (H35 chỉ áp cho THPTQG)", () => {
    const selection: ScopeSelection = { kind: "SP2E", scope: "PART", partCode: "P1", subjectCode: "MATH" };
    expect(scopeTemplate(FIXTURE_TEMPLATES, KINDS, selection)).toMatchObject({ timing: "PER_PART", durationMinutes: 90 });
  });
});
