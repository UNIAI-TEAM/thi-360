import type {
  ExamFormat,
  ExamTiming,
  MultipleChoiceRule,
  PersonRef,
  QuestionType,
} from "@/features/content/types";

export type { ExamFormat, ExamTiming } from "@/features/content/types";

/**
 * Bài thi và ma trận đề (PBI-04), dữ liệu mẫu. Ma trận nằm trong bài thi (UniNote PBI-04).
 * Khi có OpenAPI của backend, đổi các kiểu dưới đây thành bí danh của `components["schemas"][...]`.
 */

/**
 * Mã kỳ thi trong danh mục (`ContentReference.examKinds`): HSA, QDA, BCA, VACT, SPT, SP2E, THPTQG. Là chuỗi vì danh
 * mục quyết định; `EXAM_KINDS` là danh sách của dữ liệu mẫu.
 */
export type ExamKind = string;
export const EXAM_KINDS = ["HSA", "QDA", "BCA", "VACT", "SPT", "SP2E", "THPTQG"] as const;
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
  /** Môn của bài với kỳ thi có mẫu theo môn (SPT, SP2E, THPTQG); kỳ thi khác để `null`. */
  subjectCode: string | null;
  /** `SHARED`: `durationMinutes` là thời lượng chung, các phần không có thời lượng riêng (BCA, V-ACT). */
  timing: ExamTiming;
  /** Thang điểm; `null` khi chưa đặt (V-ACT đặt thang luyện tập lúc thiết lập). */
  maxScore: number | null;
  /** Có câu Tự luận: điểm phần tự luận có sau khi chấm tay. */
  hasEssay: boolean;
  /** Số nhánh nội dung của phần tự chọn (THPTQG Tin học: 2); bỏ trống coi như 0. Danh sách tính từ cấu trúc. */
  branchCount?: number;
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
  /** Lọc thêm theo năng lực (V-ACT); bỏ trống hoặc rỗng: không lọc. */
  competencyCodes?: string[];
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
  /** Chiều năng lực (V-ACT), cộng thêm vào ma trận; bỏ trống: không dùng. */
  byCompetency?: MatrixDimensionValue[];
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
  /**
   * Quy tắc chấm khối mong đợi, lấy từ mẫu của kỳ thi. Quy tắc thật nằm trên từng câu; kiểm tra cấu hình báo câu có quy
   * tắc khác khối. Bỏ trống: không so.
   */
  expectedScoring?: BlockExpectedScoring;
}

export interface BlockExpectedScoring {
  /** Khối Đúng/Sai: bảng điểm theo số ý đúng. */
  trueFalseTable?: number[];
  /** Khối nhiều đáp án: đúng hết mới có điểm hay chấm từng phần. */
  multipleChoiceRule?: MultipleChoiceRule;
}

/** Một môn trong phần thi (hoặc trong một tổ hợp). */
export interface ExamSection {
  id: string;
  subjectCode: string;
  questionCount: number;
  blocks: QuestionBlock[];
}

/**
 * Một lựa chọn của phần tự chọn: một môn (Tiếng Anh), một mã tổ hợp (KH-1, QD01, CA1) hoặc một nhánh nội dung
 * (THPTQG Tin học: Khoa học máy tính, Tin học ứng dụng).
 */
export interface ExamPartOption {
  id: string;
  kind: "SUBJECT" | "COMBINATION" | "BRANCH";
  /** Mã môn (`ENGLISH`), mã tổ hợp (`KH-1`) hoặc mã nhánh (`CS`, `ICT`). */
  code: string;
  /** Tên hiển thị của nhánh; môn và tổ hợp lấy tên từ danh mục nên để `null`. */
  name?: string | null;
  sections: ExamSection[];
  /**
   * Số câu từng môn đã được chuyên gia xác nhận (HSA chia 17–17–16 đang chờ xác nhận). `false`: kiểm tra cấu hình nhắc
   * trước khi công bố; bỏ trống: không cần xác nhận.
   */
  expertConfirmed?: boolean;
}

export interface ExamPart {
  id: string;
  order: number;
  name: string;
  /** Thời lượng riêng của phần; `null` khi bài dùng thời lượng chung (`timing = SHARED`). */
  durationMinutes: number | null;
  /** Số câu học viên làm trong phần (câu chung cộng một lựa chọn). */
  questionCount: number;
  /** Điểm tối đa của phần khi kỳ thi chia điểm theo phần (BCA: 25 / 60 / 15); `null` hoặc bỏ trống: không chia. */
  points?: number | null;
  /**
   * `FIXED`: mọi học viên làm cùng `sections`; `CHOICE`: học viên chọn một trong `options`. Phần `CHOICE` có `sections`
   * không rỗng: các môn đó là phần chung mọi học viên làm, cộng với một lựa chọn (THPTQG Tin học).
   */
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
  /** Mẫu cấu trúc đã nạp khi tạo bài, ví dụ `HSA_FULL`, `THPTQG_MATH_2025`. */
  templateCode: string | null;
  /** Năm cấu trúc và năm ma trận của mẫu (SP2E); `null` khi mẫu không ghi năm. */
  structureYear: number | null;
  matrixYear: number | null;
  /** Năm cấu trúc khác năm ma trận: chuyên gia đã xác nhận chỉ tiêu ma trận chưa. Năm trùng nhau thì luôn `true`. */
  yearsConfirmed: boolean;
  parts: ExamPart[];
  versions: ExamVersion[];
}

export interface ListExamsQuery {
  q?: string;
  kind?: ExamKind;
  format?: ExamFormat;
  subjectCode?: string;
  status?: ExamStatus;
  checkStatus?: ExamCheckStatus;
  /** Chỉ bài có bản sửa chưa công bố (ô đánh dấu của bản vẽ BaiThi). */
  hasUnpublishedChanges?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Mẫu cấu trúc nạp sẵn khi tạo bài thi. Kỳ thi `templateBy = KIND` có một mẫu FULL (và mẫu bài lẻ); kỳ thi
 * `templateBy = SUBJECT` có mẫu theo môn và năm (`subjectCode`, `structureYear`). Xem
 * `docs/specs/2026-11-16-mockup-7-ky-thi.md`.
 */
export interface ExamTemplate {
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  name: string;
  /** Môn của mẫu theo môn; mẫu theo kỳ thi để `null`. */
  subjectCode?: string | null;
  structureYear?: number | null;
  matrixYear?: number | null;
  timing: ExamTiming;
  /** Thời lượng chung khi `timing = SHARED`, hoặc tổng thời lượng để hiển thị; `null` khi tài liệu chưa ghi. */
  durationMinutes: number | null;
  maxScore: number | null;
  /** Điều cần chuyên gia xác nhận trước khi bán (ví dụ HSA chia 17–17–16), hiện cạnh mẫu; `null` khi không có. */
  expertNote?: string | null;
  parts: ExamTemplatePart[];
}

export interface ExamTemplatePart {
  name: string;
  /** Số câu học viên làm trong phần. */
  questionCount: number;
  /** `null` khi `timing = SHARED` hoặc tài liệu chưa ghi. */
  durationMinutes: number | null;
  mode: "FIXED" | "CHOICE";
  /** Môn của phần cố định một môn; phần tự chọn, phần nhiều môn và mẫu bài lẻ để `null`. */
  subjectCode?: string | null;
  points?: number | null;
  /**
   * Các môn của phần cố định nhiều môn (BCA Phần 2: Toán 35, Lịch sử 10, Tiếng Anh 20) hoặc phần chung của phần tự
   * chọn (THPTQG Tin học).
   */
  sections?: Array<{ subjectCode: string; questionCount: number; blocks?: ExamTemplateBlock[] }>;
  /** Phần tự chọn: lựa chọn gắn sẵn (mã tổ hợp, `ENGLISH`, hoặc nhánh có tên) và số câu từng môn. */
  options?: Array<{
    kind: ExamPartOption["kind"];
    code: string;
    name?: string | null;
    subjectCounts?: number[];
    expertConfirmed?: boolean;
    blocks?: ExamTemplateBlock[];
  }>;
  /** Khối câu gợi ý theo dạng câu khi phần một môn (THPTQG Toán: 12 chọn một, 4 đúng/sai, 6 trả lời ngắn). */
  blocks?: ExamTemplateBlock[];
}

/** Khối gợi ý trong mẫu: dạng câu, số câu, điểm mỗi câu, quy tắc chấm mong đợi. */
export interface ExamTemplateBlock {
  name: string;
  questionType: QuestionType;
  questionCount: number;
  pointsPerQuestion: number;
  setCount?: number | null;
  setSize?: number | null;
  competencyCode?: string | null;
  expectedScoring?: BlockExpectedScoring;
}

export interface CreateExamRequest {
  name: string;
  code: string;
  kind: ExamKind;
  format: ExamFormat;
  templateCode: string | null;
  singleSubjectCode: string | null;
  /** Môn của bài với kỳ thi có mẫu theo môn; kỳ thi khác bỏ trống. */
  subjectCode?: string | null;
  /** Thời lượng chung khi `timing = SHARED`. */
  durationMinutes?: number | null;
  maxScore?: number | null;
  /** Năm cấu trúc khác năm ma trận của mẫu: chuyên gia đã xác nhận chỉ tiêu ma trận lúc tạo. */
  yearsConfirmed?: boolean;
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
  subjectCode?: string | null;
  durationMinutes?: number | null;
  maxScore?: number | null;
  yearsConfirmed?: boolean;
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
  /** Môn của bài với kỳ thi có mẫu theo môn, để mã có đoạn môn (`THPTQG-F-TOAN-001`). */
  subjectCode?: string | null;
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
  /** Số câu theo năng lực (V-ACT), cho chiều năng lực của ma trận; bỏ trống khi không có câu gắn năng lực. */
  byCompetency?: MatrixDimensionValue[];
}

/** Một phần của cấu trúc khởi tạo khi tạo bài thi: tên, số câu, thời lượng, môn cố định hoặc các lựa chọn. */
export interface CreateExamPartInput {
  name: string;
  questionCount: number;
  /** `null` khi bài dùng thời lượng chung. */
  durationMinutes: number | null;
  points?: number | null;
  mode: "FIXED" | "CHOICE";
  /** Môn của phần cố định; phần tự chọn để `null`. */
  subjectCode: string | null;
  /** Phần tự chọn: có nhánh Tiếng Anh. */
  englishOption: boolean;
  /**
   * Phần tự chọn: tổ hợp đã gắn, số câu từng môn theo thứ tự môn của tổ hợp (tổng bằng số câu của phần trừ phần
   * chung). `blocks`: khối gợi ý từ mẫu (môn đầu tiên của tổ hợp); `expertConfirmed` như `ExamPartOption`.
   */
  combinations: Array<{ code: string; subjectCounts: number[]; blocks?: ExamTemplateBlock[]; expertConfirmed?: boolean }>;
  /**
   * Phần cố định nhiều môn (BCA Phần 2: Toán 35, Lịch sử 10, Tiếng Anh 20; tổng bằng số câu của phần) hoặc phần chung
   * của phần tự chọn (THPTQG Tin học). Bỏ trống: phần một môn theo `subjectCode`.
   */
  sections?: Array<{ subjectCode: string; questionCount: number; blocks?: ExamTemplateBlock[] }>;
  /** Khối gợi ý từ mẫu của phần cố định một môn. */
  blocks?: ExamTemplateBlock[];
  /** Phần tự chọn: nhánh nội dung (THPTQG Tin học), số câu của nhánh bằng số câu của phần trừ phần chung. */
  branches?: Array<{ code: string; name: string; questionCount: number; blocks?: ExamTemplateBlock[] }>;
}
