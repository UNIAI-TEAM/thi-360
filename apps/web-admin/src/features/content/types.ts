/**
 * Kiểu dữ liệu dùng chung của Ngân hàng câu hỏi và Bài thi (đợt 2, dữ liệu mẫu).
 *
 * Mã môn, khối, độ khó, nhãn là mã giá trị của danh mục dùng chung (PBI-02); khi nối backend lấy từ API lựa chọn của
 * danh mục. Cây kiến thức là danh mục dạng cây "Cấu trúc kiến thức"; `code` là MAPID.
 */

/**
 * Sáu dạng câu theo bản vẽ (BA ghi bốn dạng; đang chờ BA chốt, xem UNI-2590) và dạng Tự luận cho BCA, SPT, THPTQG
 * Ngữ văn (UNI-2599, chấm tay).
 */
export const QUESTION_TYPES = [
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN",
  "DRAG_DROP",
  "QUESTION_SET",
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

export interface ContentReference {
  subjects: CatalogOption[];
  /** Lớp 10, 11, 12 và "Liên lớp" (`GRADE_MIXED`, câu dùng kiến thức nhiều lớp, V-ACT). */
  grades: CatalogOption[];
  difficulties: CatalogOption[];
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

/** Hình thức bài thi: `FULL` đủ cấu trúc của kỳ thi (hoặc của môn với kỳ thi có mẫu theo môn), `SINGLE` bài lẻ một phần. */
export type ExamFormat = "FULL" | "SINGLE";

/** `PER_PART`: mỗi phần có thời lượng riêng; `SHARED`: một thời lượng chung cho cả bài (BCA, V-ACT). */
export type ExamTiming = "PER_PART" | "SHARED";

/** Nhiều đáp án đúng: `ALL_OR_NOTHING` đúng hết mới có điểm; `PARTIAL` theo tỉ lệ, chọn sai một phương án là 0. */
export const MULTIPLE_CHOICE_RULES = ["ALL_OR_NOTHING", "PARTIAL"] as const;
export type MultipleChoiceRule = (typeof MULTIPLE_CHOICE_RULES)[number];

/**
 * Quy tắc chấm mặc định của kỳ thi. `trueFalseTable[i]` là điểm khi đúng `i + 1` ý của câu Đúng/Sai (THPTQG 4 ý:
 * 0,1 / 0,25 / 0,5 / 1, tính theo điểm tối đa 1); `null` khi kỳ thi không dùng bảng (chia đều theo ý).
 */
export interface ScoringDefaults {
  trueFalseTable: number[] | null;
  multipleChoiceRule: MultipleChoiceRule;
}

/** Một kỳ thi trong danh mục. */
export interface ExamKindOption {
  /** Mã ngắn: `HSA`, `QDA`, `BCA`, `VACT`, `SPT`, `SP2E`, `THPTQG`. */
  code: string;
  name: string;
  formats: ExamFormat[];
  /** `KIND`: một mẫu cho cả kỳ thi; `SUBJECT`: mẫu theo môn (và năm) như SPT, SP2E, THPTQG. */
  templateBy: "KIND" | "SUBJECT";
  timing: ExamTiming;
  /** Thang điểm của bài; `null` khi thang đặt lúc thiết lập (V-ACT luyện tập). */
  maxScore: number | null;
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
