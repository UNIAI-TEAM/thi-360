/**
 * Bảng đăng ký biểu mẫu theo môn (FTM R-1, plan B1): khóa bằng cặp mã kỳ + mã môn canonical. Mục cho biết biểu mẫu
 * (MT Ma trận môn, DS Danh sách dòng lấy câu) và các cấu hình của cặp (R-4, R-8 đến R-12). Cờ `enabled` bật theo lát cắt: mỗi lát (plan B7)
 * bật mục của mình khi đủ tiêu chí chấp nhận, mục còn lại dùng MD.
 *
 * Tra bằng mã canonical. Bí danh chỉ đổi mã **dùng để tra** và chỉ ở kỳ có mã canonical tương ứng; không trả và không ghi lại
 * mã đã đổi (mã môn gốc của bài giữ nguyên). Mã không có trong bảng, kể cả mã hệ thống cũ, không có mục (dùng MD).
 */
// Nạp màn Danh sách dòng cho dạng ROWS cùng bảng đăng ký (CR-3): bảng và màn bật cờ cùng nơi.
import "./row-form-register";

export type SubjectFormKind = "MATRIX" | "ROWS";

export interface SubjectFormEntry {
  form: SubjectFormKind;
  /** Bật theo lát cắt (plan B7). */
  enabled: boolean;
  /** R-9: nhập tỷ lệ. `TWO_COLUMNS`: hai cột Số câu / Tỷ lệ (MT của V-ACT). `COUNT_OR_PERCENT`: số câu hoặc tỷ lệ (DS). */
  percentEntry: "NONE" | "TWO_COLUMNS" | "COUNT_OR_PERCENT";
  /** R-8: mức áp dụng của phân bố theo kỳ. */
  enforcement: "FIXED_REQUIRED" | "DECLARED_REQUIRED" | "FIXED_REFERENCE" | "REFERENCE_WITH_APPLY";
  /** R-10: bảng kết hợp Lớp × Độ khó; chỉ V-SAT, ở cấp phần. */
  combinedMatrix: "NONE" | "PART_LEVEL";
  /** R-11: ẩn khối lớp ở màn soạn theo môn. */
  hideGrade: boolean;
  /** R-4: lớp là tùy chọn (V-ACT Tiếng Việt). */
  gradeOptional: boolean;
  /** R-4: cột Liên lớp (V-ACT Toán). */
  gradeMixedColumn: boolean;
  /** R-12: giới hạn số chùm và cỡ chùm (Khoa học HSA, QDA). */
  setCount?: { min: number; max: number; size: number };
  /** R-12: số câu điền đáp án tối thiểu (Lý, Hóa, Sinh ≥ 1; Sử, Địa được 0). */
  shortAnswerMin?: number;
  /** H28: nhập tổng câu con, cỡ chùm linh hoạt (V-ACT Toán). */
  flexibleSets: boolean;
  /** R-5, R-6: luồng MAPID của môn ở MT. */
  mapidFlow: "HSA_RULES" | "SCOPE_AND_QUOTA" | "NONE";
  /** R-3: mục "Nguồn chùm" trong Thêm điều kiện của dòng Câu chùm. */
  setSource: boolean;
  /** R-7: thêm hoặc tách dòng (H-SCA Anh, V-ACT Tư duy khoa học). */
  rowsEditable: "NONE" | "ADD_AND_SPLIT";
  /** THPTQG Văn: chỉ hiện dòng "Tự luận – tạm bỏ qua". */
  readOnlyNotice?: "ESSAY_SKIPPED";
}

/** Mã kỳ trong bảng đăng ký (cùng mã với `EXAM_KINDS`). */
export type SubjectFormExamKind = "HSA" | "QDA" | "BCA" | "VACT" | "SP2E" | "SPT" | "THPTQG" | "HSCA" | "VSAT";

/** Bí danh môn → mã canonical (D-3, AD-35). Chỉ dùng để tra; chỉ có tác dụng ở kỳ có mã canonical đó. */
export const SUBJECT_CODE_ALIASES: Readonly<Record<string, string>> = {
  TIN: "INFORMATICS",
  GDKTPL: "ECON_LAW",
  CNCN: "TECH_INDUSTRY",
  CNNN: "TECH_AGRICULTURE",
};

interface KindRule {
  enforcement: SubjectFormEntry["enforcement"];
  combinedMatrix: SubjectFormEntry["combinedMatrix"];
  percent: Record<SubjectFormKind, SubjectFormEntry["percentEntry"]>;
  /** Môn ẩn khối lớp (R-11, mã canonical). */
  hideGrade: readonly string[];
  matrix: readonly string[];
  rows: readonly string[];
  /** Cặp DS đã bật (B7): lát CR-3a bật HSA, QDA, BCA (`VAN`, `ANH`) và THPTQG; lát CR-3b bật V-ACT (`ANH`, `SCIENTIFIC_THINKING`), SP2E, SPT, H-SCA, V-SAT. Cờ của MT thuộc CR-2. */
  enabledRows?: readonly string[];
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

/** Bảng kỳ theo R-1 (mã môn, số cặp, MT, DS) cùng cấu hình theo kỳ (R-8 đến R-11). */
const KIND_RULES: Readonly<Record<SubjectFormExamKind, KindRule>> = {
  HSA: {
    enforcement: "FIXED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: ["ANH"],
    matrix: SCIENCE_MATRIX,
    rows: ["VAN", "ANH"],
    enabledRows: ["VAN", "ANH"],
  },
  QDA: {
    enforcement: "DECLARED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: [],
    matrix: SCIENCE_MATRIX,
    rows: ["VAN", "ANH"],
    enabledRows: ["VAN", "ANH"],
  },
  BCA: {
    enforcement: "FIXED_REFERENCE",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: [],
    matrix: ["TOAN", "SU", "LY", "HOA", "SINH", "DIA"],
    rows: ["VAN", "ANH"],
    enabledRows: ["VAN", "ANH"],
  },
  VACT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "TWO_COLUMNS", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: [],
    matrix: ["TOAN", "VIETNAMESE"],
    rows: ["ANH", "SCIENTIFIC_THINKING"],
    enabledRows: ["ANH", "SCIENTIFIC_THINKING"],
  },
  SP2E: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: ["TOAN", "VAN", "LY", "HOA", "SINH", "DIA", "SU", "ECON_LAW", "ANH"],
    enabledRows: ["TOAN", "VAN", "LY", "HOA", "SINH", "DIA", "SU", "ECON_LAW", "ANH"],
  },
  SPT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: TWELVE_SUBJECTS,
    enabledRows: TWELVE_SUBJECTS,
  },
  THPTQG: {
    enforcement: "DECLARED_REQUIRED",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "NONE" },
    hideGrade: SOCIAL_AND_ENGLISH,
    matrix: [],
    rows: TWELVE_SUBJECTS,
    enabledRows: TWELVE_SUBJECTS,
  },
  HSCA: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "NONE",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: [],
    matrix: [],
    rows: ["TOAN", "LY", "HOA", "SINH", "VAN", "ANH"],
    enabledRows: ["TOAN", "LY", "HOA", "SINH", "VAN", "ANH"],
  },
  VSAT: {
    enforcement: "REFERENCE_WITH_APPLY",
    combinedMatrix: "PART_LEVEL",
    percent: { MATRIX: "NONE", ROWS: "COUNT_OR_PERCENT" },
    hideGrade: ["DIA", "ANH", "VAN"],
    matrix: [],
    rows: ["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN", "ANH"],
    enabledRows: ["TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN", "ANH"],
  },
};

/**
 * Cờ `enabled` theo lát cắt (plan B7): cặp chỉ bật khi lát có đủ hành vi FTM yêu cầu cho nó. CR-2 bật MT của QDA, BCA, V-ACT (Toán,
 * Tiếng Việt); CR-3 bật toàn bộ DS; MT của HSA chờ CR-2H nên vẫn tắt và dùng MD.
 */
const MATRIX_ENABLED_KINDS: readonly SubjectFormExamKind[] = ["QDA", "BCA", "VACT"];

/** Giới hạn R-12 của Khoa học HSA, QDA: 1–2 chùm, mỗi chùm 3 câu con; Lý, Hóa, Sinh có ít nhất 1 câu điền. */
const SCIENCE_SET_COUNT = { min: 1, max: 2, size: 3 } as const;
const SCIENCE_LIMITS: Readonly<Record<string, number>> = { LY: 1, HOA: 1, SINH: 1, SU: 0, DIA: 0 };
const LIMITED_KINDS: readonly SubjectFormExamKind[] = ["HSA", "QDA"];

function buildEntry(kind: SubjectFormExamKind, subject: string, form: SubjectFormKind): SubjectFormEntry {
  const rule = KIND_RULES[kind];
  const matrix = form === "MATRIX";
  const limited = LIMITED_KINDS.includes(kind) && subject in SCIENCE_LIMITS;
  return {
    form,
    enabled: matrix ? MATRIX_ENABLED_KINDS.includes(kind) : (rule.enabledRows?.includes(subject) ?? false),
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

/** Chỉnh mục để kiểm thử (bật cờ, đặt giới hạn thử); rỗng ở sản phẩm. Xem `patchSubjectFormEntry`. */
const patches = new Map<string, Partial<SubjectFormEntry>>();

function resolve(key: string): SubjectFormEntry | null {
  const base = REGISTRY.get(key);
  if (!base) return null;
  const patch = patches.get(key);
  return patch ? { ...base, ...patch } : base;
}

/**
 * Mục của cặp, hoặc `null` khi cặp không có mục (kỳ lạ, môn lạ, mã hệ thống cũ, bí danh ở kỳ không có mã canonical): nơi gọi
 * dùng MD. Mã canonical tra trước; chỉ khi không có mới thử bí danh (D-3).
 */
export function subjectFormFor(kindCode: string, subjectCode: string): SubjectFormEntry | null {
  const direct = resolve(pairKey(kindCode, subjectCode));
  if (direct) return direct;
  const canonical = SUBJECT_CODE_ALIASES[subjectCode];
  return canonical ? resolve(pairKey(kindCode, canonical)) : null;
}

/** Mọi cặp canonical của bảng đăng ký (75 cặp), theo thứ tự kỳ rồi MT trước DS. Dùng để dựng bảng phủ và kiểm thử. */
export function listSubjectFormPairs(): Array<{ kindCode: string; subjectCode: string; entry: SubjectFormEntry }> {
  return [...REGISTRY.keys()].map((key) => {
    const [kindCode, subjectCode] = key.split(":") as [string, string];
    return { kindCode, subjectCode, entry: resolve(key)! };
  });
}

/**
 * Chỗ cắm để kiểm thử: chỉnh một mục (thường là `{ enabled: true }`) cho tới khi gọi hàm trả về. Sản phẩm giữ mọi mục
 * `enabled: false` (plan B7); lát cắt bật cờ bằng cách sửa hàng của bảng, không dùng hàm này.
 */
export function patchSubjectFormEntry(kindCode: string, subjectCode: string, patch: Partial<SubjectFormEntry>): () => void {
  const key = pairKey(kindCode, subjectCode);
  if (!REGISTRY.has(key)) throw new Error(`khong co muc ${key} trong bang dang ky`);
  const previous = patches.get(key);
  patches.set(key, { ...previous, ...patch });
  return () => {
    if (previous) patches.set(key, previous);
    else patches.delete(key);
  };
}
