import type { PersonRef, QuestionType } from "@/features/content/types";

/**
 * Bài thi và ma trận đề (PBI-04), dữ liệu mẫu. Ma trận nằm trong bài thi (UniNote PBI-04).
 * Khi có OpenAPI của backend, đổi các kiểu dưới đây thành bí danh của `components["schemas"][...]`.
 */

export type ExamKind = "HSA" | "QDA";
/** `FULL`: đủ ba phần; `SINGLE`: bài lẻ một môn. */
export type ExamFormat = "FULL" | "SINGLE";
/** `PUBLISHED`: đang phục vụ một phiên bản; `DISCONTINUED`: ngừng bán, học viên đã mua vẫn làm được. */
export type ExamStatus = "DRAFT" | "PUBLISHED" | "DISCONTINUED";
/** Kết quả kiểm tra cấu hình gần nhất; `NEEDS_RECHECK`: nguồn câu hỏi đã đổi sau lần kiểm tra. */
export type ExamCheckStatus = "UNCHECKED" | "VALID" | "INVALID" | "NEEDS_RECHECK";

export interface ExamCheckSummary {
  status: ExamCheckStatus;
  issueCount: number;
  checkedAt: string | null;
}

export interface ExamSummary {
  id: string;
  code: string;
  name: string;
  kind: ExamKind;
  format: ExamFormat;
  status: ExamStatus;
  /** Phiên bản học viên đang làm; `null` khi chưa công bố lần nào. */
  servingVersion: number | null;
  /** Có bản sửa chưa công bố (bản làm việc khác phiên bản đang phục vụ). */
  hasUnpublishedChanges: boolean;
  totalQuestions: number;
  durationMinutes: number;
  /** Phần tự chọn: có nhánh Tiếng Anh không, và số tổ hợp khoa học. Bài lẻ: môn của bài. */
  englishOption: boolean;
  combinationCount: number;
  singleSubjectCode: string | null;
  check: ExamCheckSummary;
  /** Tổng lượt làm của mọi phiên bản đã công bố. */
  attemptCount: number;
  updatedAt: string;
  updatedBy: PersonRef;
}

/** Phạm vi kiến thức của khối câu hỏi theo MAPID. */
export interface KnowledgeScope {
  nodeIds: string[];
  /** `ANY`: câu gắn một trong các nút; `ALL`: câu gắn đủ các nút. */
  match: "ANY" | "ALL";
  /** Tính cả nút con của nút đã chọn. Mặc định tắt (UniNote PBI-04). */
  includeDescendants: boolean;
}

export type MatrixMode = "COMBINED" | "SEPARATE";
export type MatrixUnit = "COUNT" | "PERCENT";

export interface MatrixCell {
  gradeCode: string;
  difficultyCode: string;
  value: number;
}

export interface MatrixDimensionValue {
  code: string;
  value: number;
}

/**
 * Ma trận của một khối. `COMBINED`: ô Lớp × Độ khó (`cells`); `SEPARATE`: hai chiều riêng (`byGrade`,
 * `byDifficulty`). Đơn vị `PERCENT` quy ra số câu theo phần dư lớn nhất.
 */
export interface BlockMatrix {
  mode: MatrixMode;
  unit: MatrixUnit;
  cells: MatrixCell[];
  byGrade: MatrixDimensionValue[];
  byDifficulty: MatrixDimensionValue[];
}

export interface QuestionBlock {
  id: string;
  name: string;
  questionType: QuestionType;
  /** Số câu tính điểm của khối; câu chùm tính theo câu con (`setCount × setSize`). */
  questionCount: number;
  /** Câu chùm: số bộ và số câu con mỗi bộ; dạng khác để `null`. */
  setCount: number | null;
  setSize: number | null;
  pointsPerQuestion: number;
  knowledgeScope: KnowledgeScope;
  tagCodes: string[];
  matrix: BlockMatrix;
  /** Quy tắc đảo (bản vẽ BaiThiCauTruc). Bỏ trống coi như `SHUFFLE`. */
  shuffle?: BlockShuffleRule;
}

/** Một môn trong phần thi (hoặc trong một tổ hợp). */
export interface ExamSection {
  id: string;
  subjectCode: string;
  questionCount: number;
  blocks: QuestionBlock[];
}

/** Một lựa chọn của phần tự chọn: một môn (Tiếng Anh) hoặc một mã tổ hợp (KH-1, ...). */
export interface ExamPartOption {
  id: string;
  kind: "SUBJECT" | "COMBINATION";
  /** Mã môn (`ENGLISH`) hoặc mã tổ hợp (`KH-1`). */
  code: string;
  sections: ExamSection[];
}

export interface ExamPart {
  id: string;
  order: number;
  name: string;
  durationMinutes: number;
  questionCount: number;
  /** `FIXED`: mọi học viên làm cùng `sections`; `CHOICE`: học viên chọn một trong `options`. */
  mode: "FIXED" | "CHOICE";
  sections: ExamSection[];
  options: ExamPartOption[];
}

export type ExamVersionState = "WORKING" | "SERVING" | "RETIRED";

export interface ExamVersion {
  /** Số phiên bản; bản làm việc chưa công bố để `null`. */
  version: number | null;
  state: ExamVersionState;
  publishedAt: string | null;
  publishedBy: PersonRef | null;
  attemptCount: number;
  note: string | null;
}

export interface ExamDetail extends ExamSummary {
  description: string | null;
  /** Mẫu cấu trúc đã nạp khi tạo bài, ví dụ `HSA_FULL`. */
  templateCode: string | null;
  parts: ExamPart[];
  versions: ExamVersion[];
}

export interface ListExamsQuery {
  q?: string;
  kind?: ExamKind;
  format?: ExamFormat;
  status?: ExamStatus;
  checkStatus?: ExamCheckStatus;
  /** Chỉ bài có bản sửa chưa công bố (ô đánh dấu của bản vẽ BaiThi). */
  hasUnpublishedChanges?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/** Mẫu cấu trúc nạp sẵn khi tạo bài thi (HSA FULL: Toán 50/75, Văn 50/60, KH hoặc TA 50/60; QDA: 80/55/60). */
export interface ExamTemplate {
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  name: string;
  parts: Array<{
    name: string;
    questionCount: number;
    durationMinutes: number;
    mode: "FIXED" | "CHOICE";
    /** Môn của phần cố định; phần tự chọn và mẫu bài lẻ để `null` (môn do người tạo chọn). */
    subjectCode?: string | null;
  }>;
}

export interface CreateExamRequest {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  templateCode: string | null;
  singleSubjectCode: string | null;
  description: string | null;
  /**
   * Cấu trúc khởi tạo đã chỉnh ở trang Tạo bài thi (UniNote PBI-04 mục 5.1–5.3). Bỏ trống thì nạp nguyên mẫu theo
   * `templateCode`.
   */
  parts?: CreateExamPartInput[];
}

/** Quy tắc đảo câu của khối: đảo thứ tự câu trong khối hoặc giữ thứ tự. */
export type BlockShuffleRule = "SHUFFLE" | "KEEP_ORDER";

/** Sửa Thông tin chung. Đổi kỳ thi, hình thức, mẫu hoặc môn của bài lẻ thì cấu trúc được nạp lại theo mẫu. */
export interface UpdateExamInfoRequest {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  templateCode: string | null;
  singleSubjectCode: string | null;
  description: string | null;
}

/** Bản làm việc của cấu trúc và ma trận (`GET/PUT /exams/{id}/structure`). */
export interface ExamStructure {
  examId: string;
  parts: ExamPart[];
}

export interface UpdateExamStructureRequest {
  parts: ExamPart[];
}

export interface DuplicateExamRequest {
  /** Tên bài mới; mã do hệ thống sinh. */
  name: string;
}

export interface SuggestExamCodeQuery {
  kind: ExamKind;
  format: ExamFormat;
  /** Phần/môn của bài lẻ, để mã có đoạn môn (`HSA-L-TOAN-002`). */
  singleScope?: string | null;
}

/** Bộ lọc nguồn của một khối: môn của phần, dạng câu, phạm vi kiến thức. Chỉ đếm câu Sẵn sàng. */
export interface AvailableQuestionsQuery {
  subjectCode: string;
  questionType: QuestionType;
  knowledgeScope: KnowledgeScope;
}

/** Số câu Sẵn sàng khớp bộ lọc của khối, tổng và theo từng ô Lớp × Độ khó (câu chùm đếm theo câu con). */
export interface AvailableQuestionCounts {
  total: number;
  cells: MatrixCell[];
}

/** Một phần của cấu trúc khởi tạo khi tạo bài thi: tên, số câu, thời lượng, môn cố định hoặc các lựa chọn. */
export interface CreateExamPartInput {
  name: string;
  questionCount: number;
  durationMinutes: number;
  mode: "FIXED" | "CHOICE";
  /** Môn của phần cố định; phần tự chọn để `null`. */
  subjectCode: string | null;
  /** Phần tự chọn: có nhánh Tiếng Anh. */
  englishOption: boolean;
  /** Phần tự chọn: tổ hợp đã gắn, số câu từng môn theo thứ tự môn của tổ hợp (tổng bằng số câu của phần). */
  combinations: Array<{ code: string; subjectCounts: number[] }>;
}
