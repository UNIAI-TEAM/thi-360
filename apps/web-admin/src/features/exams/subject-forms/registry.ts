export type SubjectFormKind = "MATRIX" | "ROWS";

export interface SubjectFormEntry {
  form: SubjectFormKind;
  percentEntry: "NONE" | "TWO_COLUMNS" | "COUNT_OR_PERCENT";
  enforcement: "FIXED_REQUIRED" | "DECLARED_REQUIRED" | "FIXED_REFERENCE" | "REFERENCE_WITH_APPLY";
  combinedMatrix: "NONE" | "PART_LEVEL";
  hideGrade: boolean;
  gradeOptional: boolean;
  gradeMixedColumn: boolean;
  setCount?: { min: number; max: number; size: number };
  shortAnswerMin?: number;
  flexibleSets: boolean;
  mapidFlow: "HSA_RULES" | "SCOPE_AND_QUOTA" | "NONE";
  setSource: boolean;
  rowsEditable: "NONE" | "ADD_AND_SPLIT";
  /** THPTQG Văn: chỉ hiện dòng "Tự luận – tạm bỏ qua". */
  readOnlyNotice?: "ESSAY_SKIPPED";
}

/** Mã kỳ trong bảng đăng ký (cùng mã với `EXAM_KINDS`). */
type SubjectFormExamKind = "HSA" | "QDA" | "BCA" | "VACT" | "SP2E" | "SPT" | "THPTQG" | "HSCA" | "VSAT";

/** Bí danh môn → mã canonical (D-3, AD-35). Chỉ dùng để tra; chỉ có tác dụng ở kỳ có mã canonical đó. */
const SUBJECT_CODE_ALIASES: Readonly<Record<string, string>> = {
  TIN: "INFORMATICS",
  GDKTPL: "ECON_LAW",
  CNCN: "TECH_INDUSTRY",
  CNNN: "TECH_AGRICULTURE",
};

interface KindRule {
  enforcement: SubjectFormEntry["enforcement"];
  combinedMatrix: SubjectFormEntry["combinedMatrix"];
  percent: Record<SubjectFormKind, SubjectFormEntry["percentEntry"]>;
  hideGrade: readonly string[];
  matrix: readonly string[];
  rows: readonly string[];
}

const SOCIAL_AND_ENGLISH = ["SU", "DIA", "ECON_LAW", "ANH"] as const;
const SCIENCE_MATRIX = ["TOAN", "LY", "HOA", "SINH", "SU", "DIA"] as const;
const TWELVE_SUBJECTS = [
  "TOAN",
  "LY",
  "HOA",
  "SINH",
  "SU",
  "DIA",
  "ECON_LAW",
  "TECH_INDUSTRY",
  "TECH_AGRICULTURE",
  "INFORMATICS",
  "ANH",
  "VAN",
] as const;

const KIND_RULES: Readonly<Record<SubjectFormExamKind, KindRule>> = {
  HSA: {
    enforcement: "FIXED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: ["ANH"],
    matrix: SCIENCE_MATRIX,
    rows: ["VAN", "ANH"],
  },
  QDA: {
    enforcement: "DECLARED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: [],
    matrix: SCIENCE_MATRIX,
    rows: ["VAN", "ANH"],
  },
  BCA: {
    enforcement: "FIXED_REFERENCE",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: [],
    matrix: ["TOAN", "SU", "LY", "HOA", "SINH", "DIA"],
    rows: ["VAN", "ANH"],
  },
  VACT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "TWO_COLUMNS", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: [],
    matrix: ["TOAN", "VIETNAMESE"],
    rows: ["ANH", "SCIENTIFIC_THINKING"],
  },
  SP2E: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: ["TOAN", "VAN", "LY", "HOA", "SINH", "DIA", "SU", "ECON_LAW", "ANH"],
  },
  SPT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: TWELVE_SUBJECTS,
  },
  THPTQG: {
    enforcement: "DECLARED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: TWELVE_SUBJECTS,
  },
  HSCA: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: [],
    matrix: [],
    rows: ["TOAN", "LY", "HOA", "SINH", "VAN", "ANH"],
  },
  VSAT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "PART_LEVEL",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: ["DIA", "ANH", "VAN"],
    matrix: [],
    rows: ["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN", "ANH"],
  },
};

const SCIENCE_SET_COUNT = { min: 1, max: 2, size: 3 } as const;
const SCIENCE_LIMITS: Readonly<Record<string, number>> = { LY: 1, HOA: 1, SINH: 1, SU: 0, DIA: 0 };
const LIMITED_KINDS: readonly SubjectFormExamKind[] = ["HSA", "QDA"];

function buildEntry(kind: SubjectFormExamKind, subject: string, form: SubjectFormKind): SubjectFormEntry {
  const rule = KIND_RULES[kind];
  const matrix = form === "MATRIX";
  const limited = LIMITED_KINDS.includes(kind) && subject in SCIENCE_LIMITS;
  return {
    form,
    percentEntry: rule.percent[form],
    enforcement: rule.enforcement,
    combinedMatrix: rule.combinedMatrix,
    hideGrade: rule.hideGrade.includes(subject),
    gradeOptional: kind === "VACT" && subject === "VIETNAMESE",
    gradeMixedColumn: kind === "VACT" && subject === "TOAN",
    ...(limited ? { setCount: { ...SCIENCE_SET_COUNT }, shortAnswerMin: SCIENCE_LIMITS[subject]! } : {}),
    flexibleSets: kind === "VACT" && subject === "TOAN",
    mapidFlow: !matrix ? "NONE" : kind === "HSA" ? "HSA_RULES" : "SCOPE_AND_QUOTA",
    setSource: matrix && kind !== "HSA",
    rowsEditable: (kind === "HSCA" && subject === "ANH") || (kind === "VACT" && subject === "SCIENTIFIC_THINKING") ? "ADD_AND_SPLIT" : "NONE",
    ...(kind === "THPTQG" && subject === "VAN" ? { readOnlyNotice: "ESSAY_SKIPPED" as const } : {}),
  };
}

const pairKey = (kind: string, subject: string) => `${kind}:${subject}`;

const REGISTRY: ReadonlyMap<string, SubjectFormEntry> = (() => {
  const map = new Map<string, SubjectFormEntry>();
  for (const [kind, rule] of Object.entries(KIND_RULES) as Array<[SubjectFormExamKind, KindRule]>) {
    for (const subject of rule.matrix) map.set(pairKey(kind, subject), buildEntry(kind, subject, "MATRIX"));
    for (const subject of rule.rows) map.set(pairKey(kind, subject), buildEntry(kind, subject, "ROWS"));
  }
  return map;
})();

export function subjectFormFor(kindCode: string, subjectCode: string): SubjectFormEntry | null {
  const direct = REGISTRY.get(pairKey(kindCode, subjectCode));
  if (direct) return direct;
  const canonical = SUBJECT_CODE_ALIASES[subjectCode];
  return canonical ? (REGISTRY.get(pairKey(kindCode, canonical)) ?? null) : null;
}

/** Mọi cặp canonical của bảng đăng ký (75 cặp), theo thứ tự kỳ rồi MT trước DS. Dùng để dựng bảng phủ và kiểm thử. */
export function listSubjectFormPairs(): Array<{ kindCode: string; subjectCode: string; entry: SubjectFormEntry }> {
  return [...REGISTRY.entries()].map(([key, entry]) => {
    const [kindCode, subjectCode] = key.split(":") as [string, string];
    return { kindCode, subjectCode, entry };
  });
}
