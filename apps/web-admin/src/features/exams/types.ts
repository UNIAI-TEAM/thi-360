import type {
  ExamBuildMode,
  ExamScope,
  ExamTiming,
  MultipleChoiceRule,
  PersonRef,
  PracticeScoring,
  QuestionType,
  TrueFalseMethod,
} from "@/features/content/types";
import type { ApiPage } from "@/lib/api/page";

export type { ExamBuildMode, ExamScope, ExamTiming, PracticeScoring } from "@/features/content/types";

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
  /** Phạm vi bài thi: Toàn bài, Theo phần, Theo môn (PBI-04 mục 4). */
  scope: ExamScope;
  status: ExamStatus;
  /** Khóa lạc quan (D13): tăng ở mọi lần ghi. Mọi lệnh ghi gửi lại làm `expectedRevision`. */
  revision: number;
  /** Phiên bản học viên đang làm; `null` khi chưa công bố lần nào. */
  servingVersion: number | null;
  /** Có bản sửa chưa công bố (bản làm việc khác phiên bản đang phục vụ). */
  hasUnpublishedChanges: boolean;
  totalQuestions: number;
  durationMinutes: number;
  /** Phần tự chọn: có nhánh Tiếng Anh không, và số tổ hợp khoa học. */
  englishOption: boolean;
  combinationCount: number;
  /** Phần thi của bài Theo phần (HSA, QDA: `MATH`, `LITERATURE`, `SCIENCE`, `ENGLISH`); phạm vi khác để `null`. */
  partCode: string | null;
  /** Môn của bài Theo môn (SPT, SP2E, THPTQG); phạm vi khác để `null`. */
  subjectCode: string | null;
  /** `SHARED`: `durationMinutes` là thời lượng chung, các phần không có thời lượng riêng (BCA, V-ACT). */
  timing: ExamTiming;
  /** Thang điểm thô; `null` khi chưa đặt (V-ACT đặt thang luyện tập lúc thiết lập). */
  maxScore: number | null;
  /** Thang tham chiếu hiển thị (V-ACT 1.200); `null` khi không có (D11). */
  referenceMax: number | null;
  practiceScoring: PracticeScoring;
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

/**
 * Phạm vi kiến thức theo MAPID của môn hoặc của nhóm lấy câu. Nút đã chọn khớp câu gắn nút đó hoặc nút con của nó
 * (D23): không còn công tắc "Bao gồm cấp dưới".
 */
export interface KnowledgeScope {
  nodeIds: string[];
  /** Lọc thêm theo năng lực (V-ACT); bỏ trống hoặc rỗng: không lọc. */
  competencyCodes?: string[];
}

/** Điều kiện riêng của một chùm (`index` từ 1 đến số chùm); `tagCodes` khớp một trong các nhãn (H22). */
export interface BlockSlot {
  index: number;
  knowledgeScope?: KnowledgeScope | null;
  tagCodes?: string[];
}

/** Câu con ở vị trí `from`–`to` của chùm (đánh số từ 1) phải có ít nhất một MAPID trong `nodeIds`. */
export interface ChildCondition {
  from: number;
  to: number;
  nodeIds: string[];
}

export interface ItemShape {
  statements?: number;
  leftItems?: number;
  rightItems?: number;
}

export interface PositionRange {
  from: number;
  to: number;
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

/**
 * Nhóm lấy câu của môn (B9, D21). Nhóm câu đơn: `questionCount` và phạm vi nguồn; `questionType` là dạng trả lời của
 * nhóm, `null` khi nhóm lấy theo chỉ tiêu dạng trả lời của môn (Toán HSA). Nhóm câu chùm (`QUESTION_SET`, `CLOZE`):
 * `setCount × setSize`, luôn lấy nguyên chùm.
 */
export interface QuestionBlock {
  id: string;
  name: string;
  questionType: QuestionType | null;
  /**
   * Số câu của nhóm; nhóm chùm tính theo câu con (`setCount × setSize`). `null`: nhóm câu đơn "lấy phần còn lại" của
   * môn (môn có số câu thay đổi, HSA Khoa học); môn có tối đa một nhóm như vậy.
   */
  questionCount: number | null;
  /** Nhóm chùm: số chùm và số câu con mỗi chùm; dạng khác để `null`. */
  setCount: number | null;
  setSize: number | null;
  /**
   * Điểm mỗi câu yêu cầu (B5), không bắt buộc. Là điều kiện lọc nguồn: đặt thì chỉ câu có `points` bằng giá trị này
   * (Bộ câu hỏi và Điền khuyết: từng thành phần) vào nguồn của khối. Kỳ thi có thang cố định cần đặt ở mọi khối để
   * kiểm tra cấu hình chứng minh được tổng điểm; `null`: không lọc.
   */
  requiredPointsPerQuestion: number | null;
  /** Phạm vi riêng của nhóm; `null`: kế thừa phạm vi của môn. Phạm vi riêng không giao với phạm vi của môn. */
  knowledgeScope: KnowledgeScope | null;
  tagCodes: string[];
  /** Chỉ tiêu riêng của nhóm (tùy chọn); câu của nhóm vẫn tính vào chỉ tiêu của môn. `null`: không có. */
  matrix: BlockMatrix | null;
  /** Nhóm chùm: giới hạn dạng của câu con; `null`: không giới hạn. */
  childAnswerType: QuestionType | null;
  /** Mã nhóm của mẫu cấu trúc; `null` với nhóm thêm tay. */
  templateGroupCode: string | null;
  /** Mẫu đã điền sẵn dạng trả lời và khóa: màn không cho đổi. */
  answerTypeLocked: boolean;
  /**
   * Quy tắc chấm khối mong đợi, lấy từ mẫu của kỳ thi. Quy tắc thật nằm trên từng câu; kiểm tra cấu hình báo câu có quy
   * tắc khác khối. Bỏ trống: không so.
   */
  expectedScoring?: BlockExpectedScoring;
  /** Đảo đơn vị (câu độc lập hoặc nguyên chùm) trong nhóm theo từng đề (K4); bỏ trống: `true`. */
  shuffleUnits?: boolean;
  /** Mẫu đặt: chỉ nhóm có `true` hiện công tắc "Đảo thứ tự các chùm" (Văn HSA, Văn QDA nhóm 5). */
  shuffleEditable?: boolean;
  /** Nhóm Tự luận tạm bỏ qua (K1): không vào đề, không tính câu và điểm. Chỉ nhóm `ESSAY`. */
  omitted?: boolean;
  /** Nhóm chùm: điều kiện riêng của từng chùm (R-34); chùm không có phần tử dùng điều kiện của nhóm. */
  slots?: BlockSlot[];
  /** Nhóm chùm: điều kiện MAPID theo vị trí câu con (H13). */
  childConditions?: ChildCondition[];
  /** Hình dạng câu: số ý Đúng/Sai, số ý trái và lựa chọn phải của Ghép đôi (R-60, R-88). */
  itemShape?: ItemShape | null;
  /** Nhóm Trả lời ngắn: định dạng đáp án nhận vào (H11); rỗng: không lọc. */
  answerFormats?: string[];
  /** Chỉ có ở phản hồi, do hệ thống tính: dải số thứ tự câu của nhóm trong phần (R-90). Không gửi lại. */
  positionRange?: PositionRange | null;
}

export interface BlockExpectedScoring {
  /** Khối Đúng/Sai: cách chấm mong đợi; `COUNT_TABLE` kèm bảng điểm theo số ý đúng. */
  trueFalseMethod?: TrueFalseMethod;
  trueFalseTable?: number[];
  /** Khối nhiều đáp án: đúng hết mới có điểm hay chấm từng phần. */
  multipleChoiceRule?: MultipleChoiceRule;
}

/** Chỉ tiêu dạng trả lời của môn, tính trên mọi đơn vị đếm kể cả câu con của chùm (D22). */
export interface AnswerQuotas {
  /** `COUNT`: tổng bằng số câu của môn; `PERCENT`: tổng 100, quy ra số câu bằng phần dư lớn nhất. */
  unit: MatrixUnit;
  items: Array<{ answerType: QuestionType; value: number }>;
}

/**
 * Nhóm của môn trong `SUBJECT_CHOICE` (B10): môn tự nhiên (Vật lí, Hóa học, Sinh học) hoặc môn xã hội (Lịch sử, Địa lí).
 * Số câu từng môn tính theo nhóm của các môn đã chọn (`allocateSubjectCounts`).
 */
export type ChoiceGroup = "NATURAL" | "SOCIAL";

/** Một môn trong phần thi (hoặc trong một phương án) cùng các nhóm lấy câu của môn (B9). */
export interface ExamSection {
  id: string;
  subjectCode: string;
  /** Số câu của môn; `null` trong phương án `SUBJECT_CHOICE`: hệ thống tính theo `countsByPick` (D25). */
  questionCount: number | null;
  /** Phạm vi MAPID của môn; nhóm không có phạm vi riêng dùng phạm vi này. `null`: không giới hạn. */
  knowledgeScope: KnowledgeScope | null;
  /** Chỉ tiêu dạng trả lời của môn; `null`: không kiểm soát. */
  answerQuotas: AnswerQuotas | null;
  /** Chỉ tiêu khối lớp, độ khó của môn (tính trên mọi nhóm); `null`: không kiểm soát. */
  matrix: BlockMatrix | null;
  /** Nhóm của môn trong `SUBJECT_CHOICE` (B10); `null` ngoài `SUBJECT_CHOICE`. */
  choiceGroup: ChoiceGroup | null;
  blocks: QuestionBlock[];
}

/**
 * Một lựa chọn của phần tự chọn: một môn (Tiếng Anh), một mã tổ hợp (KH-1, QD01, CA1), một nhánh nội dung (THPTQG Tin
 * học: Khoa học máy tính, Tin học ứng dụng), hoặc `SUBJECT_CHOICE`: học viên chọn `chooseSubjects` trong các môn của
 * `sections` (HSA Khoa học: 5 môn, chọn 3).
 */
export interface ExamPartOption {
  id: string;
  kind: "SUBJECT" | "COMBINATION" | "BRANCH" | "SUBJECT_CHOICE";
  /** Mã môn (`ENGLISH`), mã tổ hợp (`KH-1`), mã nhánh (`CS`, `ICT`) hoặc mã phương án chọn môn (`SCIENCE`). */
  code: string;
  /** Tên hiển thị của nhánh và của phương án chọn môn; môn và tổ hợp lấy tên từ danh mục nên để `null`. */
  name?: string | null;
  sections: ExamSection[];
  /** `SUBJECT_CHOICE`: số môn học viên chọn; kind khác `null`. */
  chooseSubjects: number | null;
  /**
   * `SUBJECT_CHOICE`: các số câu của phương án, chia cho ba môn đã chọn theo nhóm của môn (B10, D25): HSA 17, 17, 16.
   * Nhóm có nhiều môn được chọn hơn nhận số lớn nhất mỗi môn, môn của nhóm còn lại nhận số nhỏ nhất; tổng bằng số câu
   * của phương án. Kind khác `null`.
   */
  countsByPick: number[] | null;
  /**
   * Số câu từng môn đã được chuyên gia xác nhận (tổ hợp sinh mới hoặc sửa số câu chờ xác nhận). `false`: kiểm tra cấu
   * hình nhắc trước khi công bố; bỏ trống: không cần xác nhận. Phương án `SUBJECT_CHOICE` không dùng cờ này.
   */
  expertConfirmed?: boolean;
  /**
   * Khối gợi ý của lựa chọn không có môn riêng (tổ hợp một môn CA1–CA4, nhánh Tin học) khi máy chủ lưu khối ở lựa chọn;
   * bỏ trống: lựa chọn chỉ có khối trong `sections`.
   */
  blocks?: QuestionBlock[];
}

/** Quy tắc tham gia của phần: làm tất cả (`ALL`), hoặc chọn `choose` trong các phương án (`CHOICE`). */
export interface PartParticipation {
  mode: "ALL" | "CHOICE";
  /** Số phương án phải chọn khi `mode = CHOICE`; `null` khi `ALL`. */
  choose: number | null;
}

export interface ExamPart {
  id: string;
  order: number;
  name: string;
  /** Thời lượng riêng của phần; `null` khi bài dùng thời lượng chung (`timing = SHARED`). */
  durationMinutes: number | null;
  /** Số câu học viên làm trong phần (câu chung cộng một lựa chọn). */
  questionCount: number;
  /** Số câu in trên đề của phần (THPTQG Tin học in 30 câu, học viên làm 28). */
  questionCountOnPaper: number;
  /** Số câu học viên phải làm trong phần. */
  questionCountRequired: number;
  /** Quy tắc tham gia của phần (spec 5.2). */
  participation: PartParticipation;
  /** Điểm tối đa của phần khi kỳ thi chia điểm theo phần (BCA: 25 / 60 / 15); `null` hoặc bỏ trống: không chia. */
  maxPoints?: number | null;
  /**
   * `FIXED`: mọi học viên làm cùng `sections`; `CHOICE`: học viên chọn một trong `options`. Phần `CHOICE` có `sections`
   * không rỗng: các môn đó là phần chung mọi học viên làm, cộng với một lựa chọn (THPTQG Tin học).
   */
  mode: "FIXED" | "CHOICE";
  sections: ExamSection[];
  options: ExamPartOption[];
  /**
   * Phần theo mã (QDA, BCA): các môn của phần đổi theo mã bài thi (QD01 → Khoa học + Ngoại ngữ), nên môn nằm trong
   * `subjectPool` chứ không trong `sections`/`options` (R-24, spec 3.4). `null`: phần bình thường, không dùng mã.
   */
  subjectPool?: ExamSection[] | null;
  /**
   * Nguồn mã của phần (`source = GLOBAL_LIST`: danh sách toàn cục theo lý thuyết–địa hướng; `branch`: nhánh giới hạn mã,
   * `null` khi lấy mọi nhánh); `null`: không theo mã. Giữ nguyên khi gửi lại để lưu cấu trúc không làm mất phần theo mã.
   */
  codeSource?: { source: string; branch: string | null } | null;
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
  /** Hướng dẫn làm bài (HTML đã lọc, không nhận ảnh); `null`: chưa có. */
  instructions: string | null;
  /** Mẫu cấu trúc đã nạp, ví dụ `HSA_FULL`, `THPTQG_MATH_2025`. */
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
  scope?: ExamScope;
  subjectCode?: string;
  status?: ExamStatus;
  checkStatus?: ExamCheckStatus;
  /** Chỉ bài có bản sửa chưa công bố (ô đánh dấu của bản vẽ BaiThi). */
  hasUnpublishedChanges?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/** Trang danh sách bài thi. */
export type ExamListPage = ApiPage<ExamSummary>;

/**
 * Mẫu cấu trúc nạp sẵn khi tạo bài thi. Kỳ thi `templateBy = KIND` có một mẫu FULL (và mẫu bài lẻ); kỳ thi
 * `templateBy = SUBJECT` có mẫu theo môn và năm (`subjectCode`, `structureYear`). Xem
 * `docs/specs/2026-11-16-mockup-7-ky-thi.md`.
 */
export interface ExamTemplate {
  code: string;
  kind: ExamKind;
  scope: ExamScope;
  name: string;
  /** Phần của mẫu Theo phần; phạm vi khác để `null`. */
  partCode?: string | null;
  /** Môn của mẫu Theo môn; phạm vi khác để `null`. */
  subjectCode?: string | null;
  structureYear?: number | null;
  matrixYear?: number | null;
  timing: ExamTiming;
  /** Thời lượng chung khi `timing = SHARED`, hoặc tổng thời lượng để hiển thị; `null` khi tài liệu chưa ghi. */
  durationMinutes: number | null;
  maxScore: number | null;
  /**
   * Nhóm mẫu của kỳ thi `templateBy = SUBJECT` (2B 15.1): `STANDARD` — "Dùng cấu trúc môn", `REVIEW` — "Tự thiết
   * lập bài ôn" (rút gọn). Kỳ thi khác để trống.
   */
  buildMode?: ExamBuildMode | null;
  /** Mẫu nạp mặc định của bộ (kỳ thi, phạm vi, môn/phần, `buildMode`, năm mới nhất); `null`: không đánh dấu. */
  isDefault?: boolean | null;
  /** Nhãn ngắn BE gửi kèm; `null`: giao diện tự dựng tên (`useTemplateLabel`). */
  label?: string | null;
  /** Tóm tắt mẫu của BE (ví dụ "150 câu · 195 phút"); `null`: giao diện tự tính. */
  summary?: string | null;
  /** Điều cần chuyên gia xác nhận trước khi bán (ví dụ HSA chia 17–17–16), hiện cạnh mẫu; `null` khi không có. */
  expertNote?: string | null;
  parts: ExamTemplatePart[];
}

export interface ExamTemplatePart {
  /** Id ổn định của phần trong mẫu khi BE gán (đối chiếu `overview`); `null`: so khớp theo `order`. */
  partId?: string | null;
  /** Thứ tự phần trong mẫu (1..n); `null` khi tài liệu cũ chưa ghi. */
  order?: number | null;
  name: string;
  /** Số câu học viên làm trong phần. */
  questionCount: number;
  /** `null` khi `timing = SHARED` hoặc tài liệu chưa ghi. */
  durationMinutes: number | null;
  mode: "FIXED" | "CHOICE";
  /** Môn của phần cố định một môn; phần tự chọn, phần nhiều môn và mẫu Theo phần để `null`. */
  subjectCode?: string | null;
  points?: number | null;
  /** Bí danh của `points` theo dòng `ExamOverviewRow.maxPoints` (2B 15.1); cùng giá trị khi BE đã gửi. */
  maxPoints?: number | null;
  /**
   * Các môn của phần cố định nhiều môn (BCA Phần 2: Toán 35, Lịch sử 10, Tiếng Anh 20) hoặc phần chung của phần tự
   * chọn (THPTQG Tin học).
   */
  sections?: ExamTemplateSection[];
  /** Phần tự chọn: lựa chọn gắn sẵn (mã tổ hợp, `ENGLISH`, nhánh có tên, hoặc `SUBJECT_CHOICE`) và số câu từng môn. */
  options?: Array<{
    kind: ExamPartOption["kind"];
    code: string;
    name?: string | null;
    subjectCounts?: number[];
    expertConfirmed?: boolean;
    blocks?: ExamTemplateBlock[];
    /** `SUBJECT_CHOICE`: môn, số môn chọn và số câu theo thứ tự môn; mỗi môn khai nhóm và nguồn một lần. */
    subjectChoice?: TemplateSubjectChoice;
  }>;
  /** Khối câu gợi ý theo dạng câu khi phần một môn (THPTQG Toán: 12 chọn một, 4 đúng/sai, 6 trả lời ngắn). */
  blocks?: ExamTemplateBlock[];
}

/**
 * Môn của mẫu cùng nhóm khởi tạo sẵn và chỉ tiêu cấp môn (B9). Trường chỉ tiêu bỏ trống nghĩa là `null`: mẫu của
 * QDA, BCA, V-ACT, SPT, SP2E, THPTQG giữ cấu trúc cũ, môn không có chỉ tiêu (D26).
 */
export interface ExamTemplateSection {
  /** Id ổn định của môn trong mẫu khi BE gán (đối chiếu `overview`); `null`: so khớp theo `subjectCode`. */
  sectionId?: string | null;
  /** Nhóm tạm bỏ qua của mẫu (SPT Toán: Tự luận, 2B 3.3): hiện mờ ở bảng tổng quan, không tính vào tổng. */
  omittedBlocks?: Array<{ name: string; questionCount: number | null }>;
  subjectCode: string;
  /** `null` trong `SUBJECT_CHOICE`. */
  questionCount: number | null;
  blocks?: ExamTemplateBlock[];
  knowledgeScope?: KnowledgeScope | null;
  answerQuotas?: AnswerQuotas | null;
  matrix?: BlockMatrix | null;
  choiceGroup?: ChoiceGroup | null;
}

/** Phương án chọn môn của mẫu (HSA Khoa học): 5 môn kèm nhóm, chọn 3, số câu 17, 17, 16 chia theo nhóm (B10, D25). */
export interface TemplateSubjectChoice {
  sections: ExamTemplateSection[];
  chooseSubjects: number;
  countsByPick: number[];
}

/**
 * Nhóm lấy câu trong mẫu (`ExamTemplateBlock` theo `QuestionBlock`, B9): dạng trả lời, số câu, điểm mỗi câu yêu cầu,
 * quy tắc chấm mong đợi. Trường bỏ trống nghĩa là `null` (`answerTypeLocked`: `false`).
 */
export interface ExamTemplateBlock {
  name: string;
  /** Dạng trả lời; `null`: nhóm lấy theo chỉ tiêu dạng trả lời của môn. */
  questionType: QuestionType | null;
  /** `null`: nhóm "lấy phần còn lại" của môn. */
  questionCount: number | null;
  requiredPointsPerQuestion: number | null;
  setCount?: number | null;
  setSize?: number | null;
  competencyCode?: string | null;
  expectedScoring?: BlockExpectedScoring;
  childAnswerType?: QuestionType | null;
  templateGroupCode?: string | null;
  answerTypeLocked?: boolean;
  knowledgeScope?: KnowledgeScope | null;
  matrix?: BlockMatrix | null;
}

/**
 * Bước 1 Tạo bài thi (PBI-04 mục 4). Không chọn mẫu hay năm: hệ thống nạp mẫu mới nhất khớp kỳ thi, phạm vi, phần hoặc
 * môn; chọn phiên bản năm ở bước Cấu trúc (`applyExamTemplate`).
 */
export interface CreateExamRequest {
  name: string;
  code: string;
  kind: ExamKind;
  scope: ExamScope;
  /** Bắt buộc khi `scope = PART`. */
  partCode?: string | null;
  /** Bắt buộc khi `scope = SUBJECT`. */
  subjectCode?: string | null;
  /**
   * Kiểu xây bài Theo môn (2B H4, 15.1): `STANDARD` — dùng cấu trúc môn của kỳ thi, `REVIEW` — tự thiết lập bài ôn
   * (mẫu rút gọn). Chỉ gửi khi `scope = SUBJECT`.
   */
  buildMode?: ExamBuildMode | null;
  description: string | null;
  /** Hướng dẫn làm bài; được để trống khi lưu nháp. */
  instructions?: string | null;
  /** Thời lượng chung khi `timing = SHARED`. */
  durationMinutes?: number | null;
  maxScore?: number | null;
  /**
   * Dòng "Bảng tổng quan" khác mẫu mặc định (2B 15.1): máy chủ nạp mẫu mặc định rồi ghi đè số câu, thời lượng, điểm theo
   * các dòng này. Bỏ trống: giữ nguyên số của mẫu.
   */
  overview?: ExamOverviewRow[];
  /** Cấu trúc khởi tạo đã chỉnh ở trang Tạo bài thi. Bỏ trống thì nạp nguyên mẫu mới nhất. */
  parts?: CreateExamPartInput[];
  /**
   * Bài đã tạo ở lần gửi trước mà bước lưu cấu trúc lỗi (`ExamStructureSaveError`): gửi lại chỉ lưu cấu trúc lên bài này,
   * không POST lại (sẽ trùng mã), nên các chỉnh sửa cấu trúc trên trang không mất (N2-4).
   */
  existing?: ExamDetail;
}

/**
 * Một dòng chỉnh trong "Bảng tổng quan" (2B 15.1): chỉ phần tử khác giá trị của mẫu đã nạp được gửi, so khớp theo
 * `partId` (mẫu cũ không gán id thì theo `partCode` của phần thứ `order`) hoặc `sectionId`/`subjectCode` (dòng môn).
 * `questionCount` là số câu của phần (dòng phần) hoặc của môn (dòng môn); `durationMinutes`/`maxPoints` ghi đè mẫu.
 */
export interface ExamOverviewRow {
  partId?: string;
  partCode?: string;
  sectionId?: string;
  subjectCode?: string;
  questionCount?: number;
  durationMinutes?: number;
  maxPoints?: number;
}

/**
 * Sửa Thông tin chung. Đổi kỳ thi, phạm vi, phần hoặc môn thì cấu trúc được nạp lại theo mẫu (giao diện hỏi xác nhận
 * trước, `confirmReplace`); bài đã công bố không đổi được (409 `EXAM_SCOPE_LOCKED`).
 */
export interface UpdateExamInfoRequest {
  name: string;
  code: string;
  kind: ExamKind;
  scope: ExamScope;
  partCode?: string | null;
  subjectCode?: string | null;
  description: string | null;
  instructions?: string | null;
  durationMinutes?: number | null;
  maxScore?: number | null;
  yearsConfirmed?: boolean;
  /** Cấu trúc đã sửa thì phải `true`, nếu không 409 `EXAM_STRUCTURE_REPLACE_CONFIRM`. */
  confirmReplace?: boolean;
  expectedRevision: number;
}

/** Nạp lại cấu trúc theo một mẫu (chọn phiên bản năm ở bước Cấu trúc). */
export interface ApplyExamTemplateRequest {
  templateCode: string;
  /** Cấu trúc đã sửa thì phải `true`, nếu không 409 `EXAM_STRUCTURE_REPLACE_CONFIRM`. */
  confirmReplace: boolean;
  /**
   * Tổng quan đã sửa khác mẫu hiện hành mà thiếu chính sách thì 409 `EXAM_OVERVIEW_CONFIRM`; `KEEP_EDITED` giữ số đã
   * sửa của phần và môn còn tồn tại, `USE_TEMPLATE` lấy số của mẫu mới (K8, spec 5.2).
   */
  overviewPolicy?: StructureOverviewPolicy | null;
  expectedRevision: number;
}

/** Chính sách tổng quan khi đổi phiên bản cấu trúc (K8, spec 5.2). */
export type StructureOverviewPolicy = "KEEP_EDITED" | "USE_TEMPLATE";

/** Bản làm việc của cấu trúc và ma trận (`GET/PUT /exams/{id}/structure`). */
export interface ExamStructure {
  examId: string;
  revision: number;
  parts: ExamPart[];
  /** Mã bài thi của các phần theo mã (R-24); rỗng hoặc bỏ trống với bài không theo mã. */
  combinations?: StructureCombination[];
  /**
   * Trạng thái kiểm tra theo môn (H2, R-08); `null` hoặc bỏ trống khi BE-5 chưa triển khai — vùng danh sách không
   * hiện trạng thái nhưng vẫn dùng được.
   */
  sectionStates?: ExamSectionState[] | null;
}

/** Trạng thái kiểm tra của một môn trong bản làm việc (BE-5, spec 7.1–7.3). */
export interface ExamSectionState {
  partId: string | null;
  sectionId: string | null;
  subjectCode: string | null;
  /** `CHECKED`: đã kiểm tra đạt; `NEEDS_CHECK`: đã kiểm tra nhưng môn đã đổi, cần kiểm tra lại; `INCOMPLETE`: chưa hoàn tất. */
  state: "INCOMPLETE" | "CHECKED" | "NEEDS_CHECK";
}

/** Mã bài thi của một phần theo mã (QDA, BCA): mã, nhãn và các môn mã đó trỏ tới; chỉ đọc ở bước Cấu trúc (R-24). */
export interface StructureCombination {
  code: string;
  /** Mã bài thi đầy đủ (`QD01`); `null` khi mã là mã tổ hợp nguồn. */
  examCode: string | null;
  partId: string | null;
  subjectCodes: string[];
  /** Môn mã đòi mà cấu trúc chưa có; rỗng khi đủ môn. */
  missingSubjects: string[];
  nameVi: string | null;
  nameEn: string | null;
}

/**
 * Một dòng của `GET /exams/{id}/structure/summary` (BE-5): chỉ tiêu của phần/môn (`target`) so với số câu đã phân bổ
 * (`allocated`, `difference`), kèm số câu theo dạng và khóa để bấm vào dòng chuyển tới đúng phần/môn/nhóm (spec 15.2, R-04).
 */
export interface ExamStructureSummaryRow {
  name: string | null;
  partId: string | null;
  sectionId: string | null;
  blockId: string | null;
  target: number | null;
  allocated: number | null;
  difference: number | null;
  essayCount: number | null;
  objectiveCount: number | null;
  setCount: number | null;
  commandCount: number | null;
  childCount: number | null;
  maxPoints: number | null;
}

/** Tổng hợp cấu trúc cho vùng (d) của bước 2 (spec 15.2, R-04). */
export interface ExamStructureSummary {
  rows: ExamStructureSummaryRow[];
}

export interface UpdateExamStructureRequest {
  parts: ExamPart[];
  expectedRevision: number;
}

export interface DuplicateExamRequest {
  /** Tên bài mới; mã do hệ thống sinh. */
  name: string;
}

export interface SuggestExamCodeQuery {
  kind: ExamKind;
  scope: ExamScope;
  /** Phần của bài Theo phần, để mã có đoạn phần (`HSA-P-TOAN-002`). */
  partCode?: string | null;
  /** Môn của bài Theo môn, để mã có đoạn môn (`THPTQG-S-TOAN-001`). */
  subjectCode?: string | null;
}

/**
 * Bộ lọc nguồn của một nhóm: môn, dạng trả lời, phạm vi kiến thức có hiệu lực (của nhóm, hoặc của môn khi nhóm kế
 * thừa). Chỉ đếm câu Sẵn sàng.
 */
export interface AvailableQuestionsQuery {
  subjectCode: string;
  /** Dạng trả lời của nhóm; `null`: nhóm lấy theo chỉ tiêu dạng trả lời của môn (xem `answerTypes`). */
  questionType: QuestionType | null;
  /** Các dạng trả lời nhóm nhận (dạng của nhóm, hoặc các dạng trong chỉ tiêu của môn); rỗng: theo `questionType`. */
  answerTypes: QuestionType[];
  /** Nhóm chùm: dạng của câu con; `null`: không giới hạn. */
  childAnswerType: QuestionType | null;
  /** Phạm vi có hiệu lực; `null`: không giới hạn MAPID. */
  knowledgeScope: KnowledgeScope | null;
  tagCodes?: string[];
  competencyCodes?: string[];
  /** Điểm mỗi câu yêu cầu của khối (B5); bỏ trống: không lọc. */
  requiredPointsPerQuestion?: number | null;
  /** Hình dạng câu (Đúng/Sai, Ghép đôi); bỏ trống: không lọc. */
  itemShape?: ItemShape | null;
  /** Nhóm Trả lời ngắn: các định dạng đáp án nhận vào (H11); rỗng: không lọc. */
  answerFormats?: string[];
  /** Nhóm chùm: điều kiện MAPID theo vị trí câu con (H13). `slots` của khối không có trường trong hợp đồng đếm. */
  childConditions?: ChildCondition[];
}

/** Đếm nguồn cho cả môn: hợp nguồn của các nhóm trong môn, mỗi câu đếm một lần. */
export interface SubjectAvailableQuestionsQuery {
  subjectCode: string;
  groups: AvailableQuestionsQuery[];
}

/** Số câu Sẵn sàng khớp bộ lọc, tổng và theo từng ô Lớp × Độ khó (câu chùm đếm theo câu con). */
export interface AvailableQuestionCounts {
  total: number;
  cells: MatrixCell[];
  /** Số câu theo dạng trả lời của đơn vị đếm (D22): câu con của chùm theo dạng câu con, vị trí Điền khuyết là `CLOZE`. */
  byAnswerType: Array<{ answerType: QuestionType; value: number }>;
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
   * Phần tự chọn có phương án chọn môn (HSA Khoa học, thay cho 10 tổ hợp KH-n): mã, tên, các môn kèm nhóm, số môn
   * chọn, số câu theo thứ tự môn, xác nhận của chuyên gia. `null` hoặc bỏ trống: không có.
   */
  subjectChoice?: ({ code: string; name?: string | null } & TemplateSubjectChoice) | null;
  /**
   * Phần cố định nhiều môn (BCA Phần 2: Toán 35, Lịch sử 10, Tiếng Anh 20; tổng bằng số câu của phần) hoặc phần chung
   * của phần tự chọn (THPTQG Tin học). Bỏ trống: phần một môn theo `subjectCode`.
   */
  sections?: ExamTemplateSection[];
  /** Khối gợi ý từ mẫu của phần cố định một môn. */
  blocks?: ExamTemplateBlock[];
  /** Phần tự chọn: nhánh nội dung (THPTQG Tin học), số câu của nhánh bằng số câu của phần trừ phần chung. */
  branches?: Array<{ code: string; name: string; questionCount: number; blocks?: ExamTemplateBlock[] }>;
}
