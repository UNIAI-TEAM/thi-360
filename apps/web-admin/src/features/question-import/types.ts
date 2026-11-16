import type { KnowledgeRef, PersonRef, QuestionType } from "@/features/content/types";

/**
 * Nhập câu hỏi từ tệp (PBI-03, UNI-2594), dữ liệu mẫu. Khi có OpenAPI của backend, đổi các kiểu dưới đây thành bí
 * danh của `components["schemas"][...]` và sửa chỗ lệch ở `api.ts`.
 */

/** Trường của hệ thống mà một cột trong tệp có thể ánh xạ vào (bản vẽ `NhapCauHoiAnhXa`). */
export const IMPORT_FIELDS = [
  "sourceCode",
  "type",
  "content",
  "optionA",
  "optionB",
  "optionC",
  "optionD",
  "correctAnswer",
  "solution",
  "subject",
  "grade",
  "difficulty",
  "knowledge",
  "tags",
  "setCode",
  "rubric",
  "points",
  "options",
  "matchRight",
  "scoring",
  "inputHint",
  "gradingGuide",
  "commonMistakes",
  "taskType",
] as const;
export type ImportField = (typeof IMPORT_FIELDS)[number];

/** Trường bắt buộc phải có cột ánh xạ (bản vẽ: Dạng câu, Nội dung, Đáp án đúng, Môn học, Khối lớp, Mức độ). */
export const REQUIRED_IMPORT_FIELDS = [
  "type",
  "content",
  "correctAnswer",
  "subject",
  "grade",
  "difficulty",
] as const satisfies readonly ImportField[];

export const OPTION_FIELDS = ["optionA", "optionB", "optionC", "optionD"] as const satisfies readonly ImportField[];
export const OPTION_LETTERS = ["A", "B", "C", "D"] as const;
export type OptionLetter = (typeof OPTION_LETTERS)[number];

/** Giá trị ánh xạ của một cột: một trường, bỏ qua cột, hoặc chưa ánh xạ. */
export const IGNORE_COLUMN = "IGNORE";
export const UNMAPPED_COLUMN = "UNMAPPED";
export type ColumnTarget = ImportField | typeof IGNORE_COLUMN | typeof UNMAPPED_COLUMN;

/** Ánh xạ theo khóa cột (`ImportColumn.key`). */
export type ColumnMapping = Record<string, ColumnTarget>;

export interface ImportColumn {
  /** Khóa ổn định của cột trong lần tải (`c0`, `c1`…); tên cột có thể trùng nhau nên không dùng làm khóa. */
  key: string;
  /** Tên cột ở dòng 1 của tệp. */
  header: string;
  /** Giá trị ở dòng dữ liệu đầu tiên (dòng 2), hiện ở cột "Ví dụ". */
  example: string;
}

export interface ImportSheetRow {
  /** Số dòng trong tệp (dòng 1 là tên cột, dữ liệu bắt đầu từ dòng 2). */
  rowNumber: number;
  /** Giá trị theo khóa cột. */
  cells: Record<string, string>;
}

export type ImportFileType = "XLSX" | "CSV";

/** Kết quả đọc tệp: các cột, số dòng và ánh xạ gợi ý. */
export interface ImportUpload {
  id: string;
  fileName: string;
  fileType: ImportFileType;
  /** Tên trang tính với `.xlsx`; `null` với `.csv`. */
  sheetName: string | null;
  rowCount: number;
  columns: ImportColumn[];
  suggestedMapping: ColumnMapping;
}

/** Xử lý câu trùng Mã nguồn với câu đã có trong ngân hàng (bản vẽ `NhapCauHoiAnhXa`). */
export const DUPLICATE_POLICIES = ["SKIP", "NEW_VERSION"] as const;
export type DuplicatePolicy = (typeof DUPLICATE_POLICIES)[number];

export const IMPORT_ISSUE_CODES = [
  "REQUIRED",
  "TYPE_UNKNOWN",
  "TYPE_NOT_IMPORTABLE",
  "SUBJECT_UNKNOWN",
  "GRADE_UNKNOWN",
  "DIFFICULTY_UNKNOWN",
  "KNOWLEDGE_UNKNOWN",
  "KNOWLEDGE_NOT_LEAF",
  "OPTIONS_TOO_FEW",
  "ANSWER_INVALID",
  "FORMULA_INVALID",
  "DUPLICATE_IN_FILE",
  "SET_PASSAGE_MISSING",
  "SET_PASSAGE_INVALID",
  "SET_CODE_REPEATED",
  "SET_NO_CHILDREN",
  "KNOWLEDGE_MISSING",
  "TAG_UNKNOWN",
  "RUBRIC_INVALID",
  "GRADING_GUIDE_MISSING",
  "POINTS_INVALID",
] as const;
export type ImportIssueCode = (typeof IMPORT_ISSUE_CODES)[number];

/** Lỗi chặn nhập dòng; cảnh báo vẫn nhập (ví dụ chưa có kiến thức). */
export type ImportIssueSeverity = "error" | "warning";

export interface ImportRowIssue {
  code: ImportIssueCode;
  severity: ImportIssueSeverity;
  /** Trường liên quan; `null` khi lỗi thuộc cả dòng. */
  field: ImportField | null;
  /** Tên cột trong tệp của trường đó (cột "Cột" của bảng lỗi). */
  column: string | null;
  /** Giá trị gây lỗi, nếu có. */
  value?: string;
  /** Số dòng liên quan (dòng có Mã nguồn trùng, dòng ngữ liệu của chùm). */
  relatedRow?: number;
  /** Dạng câu của dòng, để gợi ý cách sửa đúng dạng. */
  questionType?: QuestionType;
  /** Mã môn của dòng, để nêu tên môn trong lỗi kiến thức. */
  subjectCode?: string;
  /** Số phương án tối thiểu (lỗi `OPTIONS_TOO_FEW`). */
  min?: number;
}

export type ImportRowStatus = "VALID" | "ERROR" | "DUPLICATE";

export interface ImportDraftOption {
  letter: OptionLetter;
  content: string;
  correct: boolean;
}

export interface ImportDraftStatement {
  letter: OptionLetter;
  content: string;
  /** `null` khi chưa đọc được đáp án. */
  answer: boolean | null;
}

/** Một tiêu chí chấm của câu Tự luận đọc từ cột Tiêu chí chấm ("Mô tả: điểm", các tiêu chí cách nhau dấu ;). */
export interface ImportDraftCriterion {
  description: string;
  points: number;
}

/** Câu hỏi đọc được từ một dòng, dùng cho xem trước và để tạo câu khi nhập. */
export interface ImportQuestionDraft {
  type: QuestionType | null;
  /** Đề bài; với dòng ngữ liệu của câu chùm là ngữ liệu chung. */
  content: string;
  options: ImportDraftOption[];
  statements: ImportDraftStatement[];
  /** Trả lời ngắn: đáp án chuẩn đứng đầu, sau là các đáp án được chấp nhận. */
  acceptedAnswers: string[];
  /** Điểm của câu (cột Điểm); `null`: mặc định 1. */
  points: number | null;
  /** Trả lời ngắn: hướng dẫn nhập (cột Hướng dẫn nhập). */
  inputHint: string | null;
  /** Tự luận: hướng dẫn chấm (cột Hướng dẫn chấm; thiếu thì câu ở Nháp), tiêu chí chấm tùy chọn và bài làm mẫu (cột Đáp án). */
  gradingGuide: string | null;
  rubric: ImportDraftCriterion[];
  sampleAnswer: string | null;
  solution: string | null;
  subjectCode: string | null;
  gradeCode: string | null;
  difficultyCode: string | null;
  knowledge: KnowledgeRef[];
  tagCodes: string[];
  setCode: string | null;
}

export interface ImportRowResult {
  rowNumber: number;
  sourceCode: string | null;
  status: ImportRowStatus;
  issues: ImportRowIssue[];
  draft: ImportQuestionDraft;
  /** Câu đã có trong ngân hàng cùng Mã nguồn. */
  duplicateOf: { id: string; code: string } | null;
  /** Dòng là ngữ liệu của câu chùm. */
  isSetPassage: boolean;
  /** Dòng ngữ liệu của chùm mà dòng này là câu con; `null` với câu đơn và dòng ngữ liệu. */
  parentRowNumber: number | null;
}

export interface ImportSummary {
  total: number;
  valid: number;
  error: number;
  duplicate: number;
  /** Câu chùm hợp lệ và số câu con hợp lệ của chúng (đã tính trong `valid`). */
  validSets: number;
  validSetChildren: number;
}

export interface ImportValidation {
  uploadId: string;
  rows: ImportRowResult[];
  summary: ImportSummary;
}

/** Một dòng của file lỗi. */
export interface ImportErrorLine {
  rowNumber: number;
  sourceCode: string | null;
  issue: ImportRowIssue;
}

/** Một lần nhập (lịch sử nhập, và kết quả trả về sau khi nhập). */
export interface ImportHistoryEntry {
  id: string;
  importedAt: string;
  importedBy: PersonRef;
  fileName: string;
  /** Số câu tạo mới (câu đơn và câu chùm, chưa tính câu con), trạng thái Nháp. */
  createdCount: number;
  singleCount: number;
  setCount: number;
  setChildCount: number;
  /** Câu trùng Mã nguồn được bỏ qua. */
  skippedDuplicateCount: number;
  /** Câu trùng Mã nguồn được tạo phiên bản mới. */
  newVersionCount: number;
  /** Số dòng lỗi không nhập. */
  errorRowCount: number;
  /** Câu vừa tạo chưa có kiến thức chính. */
  missingKnowledgeCount: number;
  durationMs: number;
  errors: ImportErrorLine[];
}

export interface ValidateImportParams {
  uploadId: string;
  mapping: ColumnMapping;
}

export interface CommitImportParams {
  uploadId: string;
  mapping: ColumnMapping;
  duplicatePolicy: DuplicatePolicy;
  /** Bỏ qua dòng lỗi và nhập phần còn lại; `false` mà tệp còn lỗi thì máy chủ từ chối (422 `IMPORT_HAS_ERRORS`). */
  skipErrors: boolean;
}

/** Từ điển đọc tệp (tên cột, tên dạng câu, ký hiệu Đúng/Sai); dữ liệu nằm trong JSON vì là chữ tiếng Việt. */
export interface ImportDictionary {
  fieldAliases: Record<ImportField, string[]>;
  ignoreHeaders: string[];
  typeAliases: Record<QuestionType, string[]>;
  trueTokens: string[];
  falseTokens: string[];
}
