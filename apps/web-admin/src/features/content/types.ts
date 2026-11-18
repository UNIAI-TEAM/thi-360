/**
 * Kiểu dữ liệu dùng chung của Ngân hàng câu hỏi và Bài thi (đợt 2, dữ liệu mẫu).
 *
 * Mã môn, khối, độ khó, nhãn là mã giá trị của danh mục dùng chung (PBI-02); khi nối backend lấy từ API lựa chọn của
 * danh mục. Cây kiến thức là danh mục dạng cây "Cấu trúc kiến thức"; `code` là MAPID.
 */

/**
 * Tám dạng câu theo BA (spec 1, B6): Một đáp án, Nhiều đáp án, Trả lời ngắn, Điền khuyết, Bộ câu hỏi, Đúng/Sai, Ghép
 * đôi, Tự luận (chấm tay). Là mã cố định trong mã nguồn; tên hiển thị lấy từ `ContentReference.questionTypes`.
 */
export const QUESTION_TYPES = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "SHORT_ANSWER",
  "CLOZE",
  "QUESTION_SET",
  "TRUE_FALSE",
  "MATCHING",
  "ESSAY",
] as const;
export type QuestionType = (typeof QUESTION_TYPES)[number];

/** Trạng thái câu hỏi theo UniNote PBI-03 mục 9: Nháp, Sẵn sàng, Ngừng sử dụng. */
export const QUESTION_STATUSES = ["DRAFT", "READY", "RETIRED"] as const;
export type QuestionStatus = (typeof QUESTION_STATUSES)[number];

export interface CatalogOption {
  code: string;
  name: string;
}

/** Môn › Chương › Bài › Kiến thức; câu hỏi chỉ gắn được ở cấp Kiến thức. */
export type KnowledgeLevel = "SUBJECT" | "CHAPTER" | "LESSON" | "KNOWLEDGE";

export interface KnowledgeNode {
  id: string;
  /** MAPID, duy nhất trong cây. */
  code: string;
  name: string;
  level: KnowledgeLevel;
  subjectCode: string;
  /** Lớp của nút từ cấp Chương trở xuống; nút môn để `null`. */
  gradeCode: string | null;
  active: boolean;
  children: KnowledgeNode[];
}

/** Dạng câu trong danh mục (D1): `code` là mã dạng cố định; `active = false` thì không tạo được câu mới dạng này. */
export interface QuestionTypeOption {
  code: QuestionType;
  name: string;
  active: boolean;
}

export interface ContentReference {
  subjects: CatalogOption[];
  /** Lớp 10, 11, 12 và "Liên lớp" (`GRADE_MIXED`, câu dùng kiến thức nhiều lớp, V-ACT). */
  grades: CatalogOption[];
  difficulties: CatalogOption[];
  /** Dạng bài (nhóm danh mục `TASK_TYPE`), gắn vào câu hỏi qua `taskTypeCode`. */
  taskTypes: CatalogOption[];
  /** Tám dạng câu kèm tên hiển thị và trạng thái (D1). */
  questionTypes: QuestionTypeOption[];
  tags: CatalogOption[];
  /** Bảy kỳ thi (UNI-2599): HSA, QDA, BCA, VACT, SPT, SP2E, THPTQG. */
  examKinds: ExamKindOption[];
  /** Mã tổ hợp (danh mục kiểu Tổ hợp) của phần tự chọn, gắn với một kỳ thi: KH-n (HSA), QĐ01–QĐ04 (QDA), CA1–CA4 (BCA). */
  combinations: CombinationOption[];
  /** Năng lực dùng để phân loại câu và dựng ma trận ngoài MAPID (V-ACT). */
  competencies: CompetencyOption[];
  /** Mỗi phần tử là một môn (nút gốc). */
  knowledgeTree: KnowledgeNode[];
}

export interface CombinationOption {
  code: string;
  name: string;
  subjectCodes: string[];
  /** Kỳ thi dùng tổ hợp này. */
  examKindCode: string;
}

/**
 * Phạm vi bài thi (PBI-04 mục 4): `FULL` Toàn bài, `PART` Theo phần, `SUBJECT` Theo môn. Kỳ thi chỉ cho phép các phạm
 * vi trong `ExamKindOption.scopes`.
 */
export const EXAM_SCOPES = ["FULL", "PART", "SUBJECT"] as const;
export type ExamScope = (typeof EXAM_SCOPES)[number];

/** `PER_PART`: mỗi phần có thời lượng riêng; `SHARED`: một thời lượng chung cho cả bài (BCA, V-ACT). */
export type ExamTiming = "PER_PART" | "SHARED";

/** Nhiều đáp án đúng: `ALL_OR_NOTHING` đúng hết mới có điểm; `PARTIAL` theo tỉ lệ, chọn sai một phương án là 0. */
export const MULTIPLE_CHOICE_RULES = ["ALL_OR_NOTHING", "PARTIAL"] as const;
export type MultipleChoiceRule = (typeof MULTIPLE_CHOICE_RULES)[number];

/** Cách chấm Đúng/Sai: cộng điểm từng ý, hoặc bảng điểm theo số ý đúng. */
export const TRUE_FALSE_METHODS = ["PER_STATEMENT", "COUNT_TABLE"] as const;
export type TrueFalseMethod = (typeof TRUE_FALSE_METHODS)[number];

/**
 * Cách chấm Đúng/Sai mặc định của kỳ thi. `COUNT_TABLE`: `table[i]` là điểm khi đúng `i + 1` ý (THPTQG 4 ý: 0,1 / 0,25
 * / 0,5 / 1, tính theo điểm tối đa 1). `PER_STATEMENT`: mỗi ý `statementPoints` điểm, `table` rỗng.
 */
export interface TrueFalseDefaults {
  method: TrueFalseMethod;
  table: number[];
  statementPoints: number | null;
}

/** Quy tắc chấm mặc định của kỳ thi. */
export interface ScoringDefaults {
  trueFalse: TrueFalseDefaults;
  multipleChoiceRule: MultipleChoiceRule;
}

/** Điểm hiển thị của bài (D11): `RAW` điểm thô; `LINEAR` quy tuyến tính điểm thô về `referenceMax` (V-ACT). */
export const PRACTICE_SCORINGS = ["RAW", "LINEAR"] as const;
export type PracticeScoring = (typeof PRACTICE_SCORINGS)[number];

/**
 * Kiểu xây bài Theo môn (2B H4, 15.1): `STANDARD` — "Dùng cấu trúc môn [kỳ thi]", `REVIEW` — "Tự thiết lập bài ôn"
 * (mẫu `<KỲ>_REVIEW` rút gọn). Chỉ có khi phạm vi là `SUBJECT`; gửi trong `CreateExamRequest.buildMode`.
 */
export const EXAM_BUILD_MODES = ["STANDARD", "REVIEW"] as const;
export type ExamBuildMode = (typeof EXAM_BUILD_MODES)[number];

/** Một kỳ thi trong danh mục (hồ sơ kỳ thi). */
export interface ExamKindOption {
  /** Mã ngắn: `HSA`, `QDA`, `BCA`, `VACT`, `SPT`, `SP2E`, `THPTQG`. */
  code: string;
  name: string;
  /** Phạm vi bài thi kỳ thi cho phép (2B 5.1): HSA, QDA FULL+PART; BCA FULL+SUBJECT; V-ACT, THPTQG SUBJECT; SPT, SP2E SUBJECT+PART. */
  scopes: ExamScope[];
  /** `KIND`: một mẫu cho cả kỳ thi; `SUBJECT`: mẫu theo môn (và năm) như SPT, SP2E, THPTQG. */
  templateBy: "KIND" | "SUBJECT";
  timing: ExamTiming;
  /** Thang điểm thô của bài; `null` khi thang đặt lúc thiết lập (V-ACT luyện tập). */
  maxScore: number | null;
  /** Thang tham chiếu hiển thị (V-ACT 1.200); `null` khi kỳ thi không có. */
  referenceMax: number | null;
  practiceScoring: PracticeScoring;
  scoringDefaults: ScoringDefaults;
}

/** Năng lực (V-ACT): phân loại câu theo năng lực bên cạnh MAPID. */
export interface CompetencyOption {
  code: string;
  name: string;
  subjectCode: string | null;
  examKindCodes: string[];
}

/** Kiến thức gắn với câu hỏi; đúng một mục `primary` khi có ít nhất một mục. */
export interface KnowledgeRef {
  nodeId: string;
  code: string;
  name: string;
  primary: boolean;
}

export interface PersonRef {
  id: string;
  name: string;
}
