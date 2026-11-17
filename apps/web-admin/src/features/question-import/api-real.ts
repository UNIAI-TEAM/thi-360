import type { components } from "@thi360/api-client";
import { extractFilenameFromDisposition } from "@/features/bulk-import/api";
import { ContentApiError, toContentApiError } from "@/features/content/api-error";
import { withIdempotencyKey } from "@/features/content/idempotency";
import { QUESTION_TYPES, type MultipleChoiceRule, type QuestionType } from "@/features/content/types";
import { createBrowserApiClient } from "@/lib/api/client";
import { importFileType } from "./files";
import {
  IGNORE_COLUMN,
  IMPORT_FIELDS,
  IMPORT_ISSUE_CODES,
  UNMAPPED_COLUMN,
  type ColumnMapping,
  type ColumnTarget,
  type CommitImportParams,
  type ImportDraftMatching,
  type ImportErrorLine,
  type ImportField,
  type ImportHistoryEntry,
  type ImportIssueCode,
  type ImportQuestionDraft,
  type ImportRowIssue,
  type ImportRowResult,
  type ImportUpload,
  type ImportValidation,
  type ValidateImportParams,
} from "./types";

/**
 * Nhánh API thật của nhập câu hỏi (`/api/v1/admin/question-imports`, spec 4.5): tải tệp, kiểm tra, nhập (có
 * `Idempotency-Key`), lịch sử, tệp mẫu. Kiểu sinh từ OpenAPI có mọi trường tùy chọn nên các hàm `map*` điền giá trị
 * mặc định; chữ nằm ở file dịch theo mã lỗi.
 */

type Schemas = components["schemas"];
type ApiUpload = Schemas["Upload"];
type ApiRow = Schemas["Row"];
type ApiIssue = Schemas["Issue"];
type ApiDraft = Schemas["Draft"];
type ApiHistoryEntry = Schemas["QuestionImportHistoryEntry"];

/**
 * Phần của `Draft.body` mà xem trước đọc. Kiểu sinh của `body` là hợp các thân có `type` bằng giao của hai hằng khác
 * nhau nên mọi thành viên thành `never`; ở đây khai báo hình dạng cần đọc, chọn thân theo `Draft.type`.
 */
interface MatchingSide {
  id?: string;
  content?: string;
}

interface DraftBodyShape {
  scoringRule?: MultipleChoiceRule;
  scoringMethod?: "PER_STATEMENT" | "COUNT_TABLE";
  scoreTable?: number[];
  statements?: Array<{ points?: number }>;
  blanks?: Array<{ id?: string; answers?: string[]; points?: number }>;
  left?: MatchingSide[];
  right?: MatchingSide[];
  pairs?: Array<{ leftId?: string; rightId?: string }>;
  reuseRight?: boolean;
}

export interface DownloadedFile {
  blob: Blob;
  filename: string;
}

const COLUMN_TARGETS: readonly string[] = [...IMPORT_FIELDS, IGNORE_COLUMN, UNMAPPED_COLUMN];

function toColumnTarget(value: string): ColumnTarget {
  return COLUMN_TARGETS.includes(value) ? (value as ColumnTarget) : UNMAPPED_COLUMN;
}

function isImportField(value: string | undefined): value is ImportField {
  return value !== undefined && (IMPORT_FIELDS as readonly string[]).includes(value);
}

function isQuestionType(value: string | undefined): value is QuestionType {
  return value !== undefined && (QUESTION_TYPES as readonly string[]).includes(value);
}

function mapIssue(issue: ApiIssue): ImportRowIssue {
  const code = (IMPORT_ISSUE_CODES as readonly string[]).includes(issue.code ?? "")
    ? (issue.code as ImportIssueCode)
    : "SAVE_VALIDATION";
  return {
    code,
    severity: issue.severity === "warning" ? "warning" : "error",
    field: isImportField(issue.field) ? issue.field : null,
    column: issue.column ?? null,
    ...(issue.value !== undefined && { value: issue.value }),
    ...(issue.relatedRow !== undefined && { relatedRow: issue.relatedRow }),
    ...(isQuestionType(issue.questionType) && { questionType: issue.questionType }),
    ...(issue.subjectCode !== undefined && { subjectCode: issue.subjectCode }),
  };
}

/** Chìa khóa hiển thị của mục ghép đôi: `l1` là `1`, `r1` là `A` (backend đặt id theo nhãn trong tệp). */
function matchKey(id: string | undefined, index: number, fallback: "number" | "letter"): string {
  const label = id !== undefined && id.length > 1 ? id.slice(1) : "";
  if (label) return label;
  return fallback === "letter" ? String.fromCharCode(65 + (index % 26)) : String(index + 1);
}

function mapMatching(body: DraftBodyShape): ImportDraftMatching {
  const sidesLeft = body.left ?? [];
  const sidesRight = body.right ?? [];
  const left = sidesLeft.map((side, index) => ({ key: matchKey(side.id, index, "number"), content: side.content ?? "" }));
  const right = sidesRight.map((side, index) => ({ key: matchKey(side.id, index, "letter"), content: side.content ?? "" }));
  return {
    left,
    right,
    pairs: (body.pairs ?? []).map((pair) => ({
      leftKey: left[sidesLeft.findIndex((side) => side.id === pair.leftId)]?.key ?? "",
      rightKey: right[sidesRight.findIndex((side) => side.id === pair.rightId)]?.key ?? "",
    })),
    reuseRight: body.reuseRight ?? false,
  };
}

function mapDraft(draft: ApiDraft | undefined): ImportQuestionDraft {
  // Cast có tên: hình dạng thật của `body` theo `Draft.type`, kiểu sinh không biểu diễn được (xem `DraftBodyShape`).
  const body: DraftBodyShape = (draft?.body ?? {}) as DraftBodyShape;
  const type = draft?.type;
  const scoringRule: MultipleChoiceRule | null = type === "MULTIPLE_CHOICE" ? (body.scoringRule ?? null) : null;
  const trueFalseScoring =
    type === "TRUE_FALSE" && body.scoringMethod
      ? {
          method: body.scoringMethod,
          points:
            body.scoringMethod === "COUNT_TABLE"
              ? [...(body.scoreTable ?? [])]
              : (body.statements ?? []).map((statement) => statement.points ?? 0),
        }
      : null;
  const mistakes = (draft?.commonMistakes ?? []).filter((mistake) => mistake.trim() !== "");
  return {
    type: draft?.type ?? null,
    content: draft?.content ?? "",
    options: (draft?.options ?? []).map((option) => ({
      letter: option.letter ?? "",
      content: option.content ?? "",
      correct: option.correct ?? false,
    })),
    statements: (draft?.statements ?? []).map((statement) => ({
      letter: statement.letter ?? "",
      content: statement.content ?? "",
      answer: statement.answer ?? null,
    })),
    acceptedAnswers: draft?.acceptedAnswers ?? [],
    scoringRule,
    trueFalseScoring,
    blanks:
      type === "CLOZE"
        ? (body.blanks ?? []).map((blank) => ({ id: blank.id ?? "", answers: blank.answers ?? [], points: blank.points ?? null }))
        : [],
    matching: type === "MATCHING" ? mapMatching(body) : null,
    commonMistakes: mistakes.length > 0 ? mistakes.join("; ") : null,
    taskTypeCode: draft?.taskTypeCode ?? null,
    points: draft?.points ?? null,
    inputHint: draft?.inputHint ?? null,
    gradingGuide: draft?.gradingGuide ?? null,
    rubric: (draft?.rubric ?? []).map((criterion) => ({ description: criterion.description ?? "", points: criterion.points ?? 0 })),
    sampleAnswer: draft?.sampleAnswer ?? null,
    solution: draft?.solution ?? null,
    subjectCode: draft?.subjectCode ?? null,
    gradeCode: draft?.gradeCode ?? null,
    difficultyCode: draft?.difficultyCode ?? null,
    knowledge: (draft?.knowledge ?? []).map((ref) => ({
      nodeId: ref.nodeId,
      code: ref.code ?? "",
      name: ref.name ?? "",
      primary: ref.primary,
    })),
    tagCodes: draft?.tagCodes ?? [],
    setCode: draft?.setCode ?? null,
  };
}

function mapRow(row: ApiRow): ImportRowResult {
  return {
    rowNumber: row.rowNumber ?? 0,
    sourceCode: row.sourceCode ?? null,
    status: row.status ?? "ERROR",
    issues: (row.issues ?? []).map(mapIssue),
    draft: mapDraft(row.draft),
    duplicateOf: row.duplicateOf?.id
      ? { id: row.duplicateOf.id, code: row.duplicateOf.code ?? row.duplicateOf.sourceCode ?? "" }
      : null,
    isSetPassage: row.isSetPassage ?? false,
    parentRowNumber: row.parentRowNumber ?? null,
  };
}

function mapHistoryEntry(entry: ApiHistoryEntry): ImportHistoryEntry {
  return {
    id: entry.id ?? "",
    importedAt: entry.importedAt ?? "",
    importedBy: entry.importedBy ?? { id: "", name: "" },
    fileName: entry.fileName ?? "",
    createdCount: entry.createdCount ?? 0,
    singleCount: entry.singleCount ?? 0,
    setCount: entry.setCount ?? 0,
    setChildCount: entry.setChildCount ?? 0,
    skippedDuplicateCount: entry.skippedDuplicateCount ?? 0,
    newVersionCount: entry.newVersionCount ?? 0,
    errorRowCount: entry.errorRowCount ?? 0,
    missingKnowledgeCount: entry.missingKnowledgeCount ?? 0,
    durationMs: entry.durationMs ?? 0,
    errors: (entry.errors ?? []).map<ImportErrorLine>((line) => ({
      rowNumber: line.rowNumber ?? 0,
      sourceCode: line.sourceCode ?? null,
      issue: mapIssue(line.issue ?? {}),
    })),
  };
}

function mapUpload(upload: ApiUpload, file: File): ImportUpload {
  const mapping: ColumnMapping = Object.fromEntries(
    Object.entries(upload.suggestedMapping ?? {}).map(([key, value]) => [key, toColumnTarget(value)]),
  );
  return {
    id: upload.id ?? "",
    fileName: upload.fileName ?? file.name,
    fileType: upload.fileType ?? importFileType(file.name) ?? "XLSX",
    sheetName: upload.sheetName ?? null,
    rowCount: upload.rowCount ?? 0,
    columns: (upload.columns ?? []).map((column) => ({
      key: column.key ?? "",
      header: column.header ?? "",
      example: column.example ?? "",
    })),
    suggestedMapping: mapping,
  };
}

export async function downloadQuestionTemplate(): Promise<DownloadedFile> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/question-imports/template", { parseAs: "blob" });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return {
    blob: data,
    filename: extractFilenameFromDisposition(response.headers.get("content-disposition"), "question-import-template.xlsx"),
  };
}

export async function uploadQuestionFile(file: File): Promise<ImportUpload> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/question-imports/uploads", {
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const formData = new FormData();
      formData.append("file", file);
      return formData;
    },
  });
  if (!response.ok || !data?.id) {
    const failure = toContentApiError(response.status, error);
    // Tầng web có thể chặn 413 trước khi tới dịch vụ nhập (không có thân lỗi): vẫn báo đúng lỗi của tệp.
    if (response.status === 413 && !failure.code) throw new ContentApiError(413, failure.message, "IMPORT_FILE_TOO_LARGE");
    throw failure;
  }
  return mapUpload(data, file);
}

export async function validateImport({ uploadId, mapping }: ValidateImportParams): Promise<ImportValidation> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/question-imports/uploads/{id}/validate", {
    params: { path: { id: uploadId } },
    body: { uploadId, mapping },
  });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  const summary = data.summary ?? {};
  return {
    uploadId: data.uploadId ?? uploadId,
    rows: (data.rows ?? []).map(mapRow),
    summary: {
      total: summary.total ?? 0,
      valid: summary.valid ?? 0,
      error: summary.error ?? 0,
      duplicate: summary.duplicate ?? 0,
      validSets: summary.validSets ?? 0,
      validSetChildren: summary.validSetChildren ?? 0,
    },
  };
}

/**
 * Nhập với `Idempotency-Key` (spec 7.1, quy tắc chung ở `content/idempotency.ts`): một lần nhập là một `uploadId`;
 * cùng `uploadId` mà đổi ánh xạ, cách xử lý trùng hay bỏ qua lỗi là lệnh khác nên khóa mới.
 */
function commitFingerprint({ mapping, duplicatePolicy, skipErrors }: CommitImportParams): string {
  return JSON.stringify([duplicatePolicy, skipErrors, Object.entries(mapping).sort(([a], [b]) => (a < b ? -1 : 1))]);
}

export async function commitImport(params: CommitImportParams): Promise<ImportHistoryEntry> {
  const data = await withIdempotencyKey(
    `commit:${params.uploadId}`,
    async (key) => {
      const client = createBrowserApiClient();
      const { data, error, response } = await client.POST("/api/v1/admin/question-imports/uploads/{id}/commit", {
        params: { path: { id: params.uploadId }, header: { "Idempotency-Key": key } },
        body: {
          uploadId: params.uploadId,
          mapping: params.mapping,
          duplicatePolicy: params.duplicatePolicy,
          skipErrors: params.skipErrors,
        },
      });
      if (!response.ok || !data) throw toContentApiError(response.status, error);
      return data;
    },
    commitFingerprint(params),
  );
  return mapHistoryEntry(data);
}

export async function listImportHistory(): Promise<ImportHistoryEntry[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/question-imports");
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return data.map(mapHistoryEntry);
}
