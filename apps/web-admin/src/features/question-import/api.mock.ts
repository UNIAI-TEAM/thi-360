import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference } from "@/features/content/types";
import { MOCK_CURRENT_AUTHOR, questionStore } from "@/features/questions/mock/store";
import type { QuestionBody, QuestionDetail } from "@/features/questions/types";
import { DEFAULT_POINTS, evenScoreTable } from "@/features/questions/editor-form";
import { createMockStore, MockApiError, mockId, mockRequest } from "@/lib/mock/content-mock";
import {
  checkImportFile,
  importFileType,
  MAX_IMPORT_ROWS,
  parseCsv,
  readFileText,
  sheetFromTable,
  toCsvBlob,
} from "./files";
import { collectErrorLines } from "./error-report";
import { checkMapping, suggestMapping } from "./mapping";
import dictionaryFixture from "./mock/import-dictionary.json";
import historyFixture from "./mock/history.json";
import sampleSheetFixture from "./mock/sample-sheet.json";
import templateFixture from "./mock/template.json";
import type {
  CommitImportParams,
  ImportColumn,
  ImportDictionary,
  ImportHistoryEntry,
  ImportQuestionDraft,
  ImportRowResult,
  ImportSheetRow,
  ImportUpload,
  ImportValidation,
  ValidateImportParams,
} from "./types";
import { summarizeImport, validateImportRows } from "./validation";

/**
 * API nhập câu hỏi từ tệp. Hiện là dữ liệu mẫu: tệp `.xlsx` luôn đọc thành bảng mẫu `mock/sample-sheet.json` (chưa
 * có thư viện đọc Excel), tệp `.csv` được đọc thật; kiểm tra chạy bằng `validateImportRows` trên trình duyệt; câu nhập
 * vào thêm vào `questionStore` ở trạng thái Nháp.
 *
 * Khi nối backend: `uploadQuestionFile` gửi tệp (multipart) và nhận cột, số dòng, ánh xạ gợi ý; `validateImport` và
 * `commitImport` gửi `uploadId` + ánh xạ; `listImportHistory` đọc lịch sử. Giữ nguyên chữ ký; giao diện không đổi.
 */

const dictionary = dictionaryFixture as ImportDictionary;
const reference = referenceFixture as ContentReference;

interface StoredSheet {
  upload: ImportUpload;
  rows: ImportSheetRow[];
}

/** Tệp đã đọc trong phiên, theo `uploadId` (máy chủ giữ tệp tạm cho tới khi nhập xong). */
const uploads = new Map<string, StoredSheet>();

export const importHistoryStore = createMockStore<ImportHistoryEntry>(historyFixture as ImportHistoryEntry[]);

export interface DownloadedFile {
  blob: Blob;
  filename: string;
}

/** Tệp mẫu. Bản mẫu sinh một tệp CSV nhỏ (dòng tên cột và vài câu ví dụ); backend sẽ trả tệp `.xlsx`. */
export async function downloadQuestionTemplate(): Promise<DownloadedFile> {
  // Dựng Blob sau `mockRequest`: hàm đó trả bản sao bằng `structuredClone`, không giữ được Blob ở mọi môi trường.
  const template = await mockRequest(() => templateFixture, { ignoreScenario: true });
  return { blob: toCsvBlob(template.rows), filename: template.fileName };
}

async function readSheet(file: File): Promise<{ sheetName: string | null; columns: ImportColumn[]; rows: ImportSheetRow[] }> {
  if (importFileType(file.name) === "CSV") {
    return { sheetName: null, ...sheetFromTable(parseCsv(await readFileText(file))) };
  }
  return { sheetName: sampleSheetFixture.sheetName, ...sheetFromTable(sampleSheetFixture.rows) };
}

export async function uploadQuestionFile(file: File): Promise<ImportUpload> {
  const fileError = checkImportFile(file);
  if (fileError) throw new MockApiError(fileError === "IMPORT_FILE_TOO_LARGE" ? 413 : 422, fileError);
  const sheet = await readSheet(file);
  return mockRequest(
    () => {
      if (sheet.columns.length === 0 || sheet.columns.every((column) => !column.header)) {
        throw new MockApiError(422, "IMPORT_INVALID_HEADER");
      }
      if (sheet.rows.length === 0) throw new MockApiError(422, "IMPORT_EMPTY_FILE");
      if (sheet.rows.length > MAX_IMPORT_ROWS) throw new MockApiError(422, "IMPORT_ROW_LIMIT_EXCEEDED");
      const upload: ImportUpload = {
        id: mockId("upload"),
        fileName: file.name,
        fileType: importFileType(file.name) ?? "XLSX",
        sheetName: sheet.sheetName,
        rowCount: sheet.rows.length,
        columns: sheet.columns,
        suggestedMapping: suggestMapping(sheet.columns, dictionary.fieldAliases, dictionary.ignoreHeaders),
      };
      uploads.set(upload.id, { upload, rows: sheet.rows });
      return upload;
    },
    { ignoreScenario: true },
  );
}

function requireUpload(uploadId: string): StoredSheet {
  const stored = uploads.get(uploadId);
  if (!stored) throw new MockApiError(404, "IMPORT_SOURCE_GONE");
  return stored;
}

function runValidation({ uploadId, mapping }: ValidateImportParams): ImportValidation {
  const { upload, rows } = requireUpload(uploadId);
  const check = checkMapping(upload.columns, mapping);
  if (!check.valid) throw new MockApiError(422, "IMPORT_MAPPING_INVALID");
  const results = validateImportRows({
    columns: upload.columns,
    rows,
    mapping,
    reference,
    existingQuestions: questionStore.all(),
    dictionary,
  });
  return { uploadId, rows: results, summary: summarizeImport(results) };
}

export async function validateImport(params: ValidateImportParams): Promise<ImportValidation> {
  return mockRequest(() => runValidation(params), { ignoreScenario: true });
}

function nextQuestionNumber(): number {
  return questionStore.all().reduce((max, question) => {
    const number = Number(question.code.replace(/\D/g, ""));
    return Number.isFinite(number) ? Math.max(max, number) : max;
  }, 0);
}

/**
 * Điểm của câu nhập vào: tệp chưa có cột Điểm nên câu đơn 1 điểm; câu Tự luận có tiêu chí chấm thì bằng tổng điểm tiêu
 * chí (để hướng dẫn chấm khớp điểm của câu).
 */
function draftPoints(draft: ImportQuestionDraft): number {
  if (draft.points !== null) return draft.points;
  if (draft.type !== "ESSAY" || draft.rubric.length === 0) return DEFAULT_POINTS;
  return Math.round(draft.rubric.reduce((sum, criterion) => sum + criterion.points, 0) * 1000) / 1000;
}

function bodyFromDraft(draft: ImportQuestionDraft, idPrefix: string, childIds: string[]): QuestionBody {
  switch (draft.type) {
    case "MULTIPLE_CHOICE":
    case "SINGLE_CHOICE": {
      const options = draft.options.map((option, index) => ({
        id: `${idPrefix}-o${index + 1}`,
        content: option.content,
        correct: option.correct,
      }));
      return draft.type === "SINGLE_CHOICE"
        ? { type: "SINGLE_CHOICE", shuffleOptions: true, options }
        : { type: "MULTIPLE_CHOICE", shuffleOptions: true, options, scoringRule: "ALL_OR_NOTHING" };
    }
    case "TRUE_FALSE":
      return {
        type: "TRUE_FALSE",
        statements: draft.statements.map((statement, index) => ({
          id: `${idPrefix}-s${index + 1}`,
          content: statement.content,
          answer: statement.answer ?? false,
          points: null,
        })),
        scoringMethod: "COUNT_TABLE",
        scoreTable: evenScoreTable(draft.statements.length, draftPoints(draft)),
      };
    case "SHORT_ANSWER":
      return { type: "SHORT_ANSWER", answers: draft.acceptedAnswers, inputHint: draft.inputHint };
    case "ESSAY":
      return {
        type: "ESSAY",
        gradingGuide: draft.gradingGuide ?? "",
        rubric: draft.rubric.map((criterion, index) => ({ id: `${idPrefix}-r${index + 1}`, ...criterion })),
        sampleAnswer: draft.sampleAnswer,
        wordLimit: null,
      };
    default:
      return { type: "QUESTION_SET", passage: draft.content, childIds };
  }
}

function questionFromRow(
  row: ImportRowResult,
  children: ImportRowResult[],
  identity: { id: string; code: string },
  now: string,
): QuestionDetail {
  const { draft } = row;
  const childQuestions = children.map((child, index) =>
    questionFromRow(child, [], { id: `${identity.id}-c${index + 1}`, code: `${identity.code}-${index + 1}` }, now),
  );
  const solution = draft.solution;
  return {
    id: identity.id,
    code: identity.code,
    type: draft.type ?? "SINGLE_CHOICE",
    status: "DRAFT",
    taskTypeCode: null,
    contentPreview: draft.content,
    subjectCode: draft.subjectCode ?? "",
    gradeCode: draft.gradeCode ?? "",
    difficultyCode: draft.difficultyCode ?? "",
    points: row.isSetPassage ? childQuestions.reduce((sum, child) => sum + child.points, 0) || 1 : draftPoints(draft),
    knowledge: draft.knowledge,
    tagCodes: draft.tagCodes,
    hasSolution: Boolean(solution) || childQuestions.some((child) => child.hasSolution),
    version: 1,
    revision: 0,
    usedInExamCount: 0,
    sourceCode: row.sourceCode,
    createdBy: MOCK_CURRENT_AUTHOR,
    createdAt: now,
    updatedAt: now,
    content: draft.content,
    body: bodyFromDraft(draft, identity.id, childQuestions.map((child) => child.id)),
    solution,
    commonMistakes: null,
    attachmentIds: [],
    children: childQuestions,
  };
}

export async function commitImport(params: CommitImportParams): Promise<ImportHistoryEntry> {
  return mockRequest(
    () => {
      const startedAt = Date.now();
      const { upload } = requireUpload(params.uploadId);
      const { rows, summary } = runValidation(params);
      if (summary.error > 0 && !params.skipErrors) throw new MockApiError(422, "IMPORT_HAS_ERRORS");
      const now = new Date().toISOString();
      let number = nextQuestionNumber();
      const childrenOf = (row: ImportRowResult) =>
        rows.filter((child) => child.parentRowNumber === row.rowNumber && child.status !== "ERROR");
      const topLevel = rows.filter((row) => row.parentRowNumber === null);

      let singleCount = 0;
      let setCount = 0;
      let setChildCount = 0;
      let missingKnowledgeCount = 0;
      let skippedDuplicateCount = 0;
      let newVersionCount = 0;
      for (const row of topLevel) {
        if (row.status === "VALID") {
          number += 1;
          const children = row.isSetPassage ? childrenOf(row) : [];
          const question = questionFromRow(
            row,
            children,
            { id: mockId("q"), code: `CH-${String(number).padStart(6, "0")}` },
            now,
          );
          questionStore.insert(question);
          if (row.isSetPassage) {
            setCount += 1;
            setChildCount += children.length;
          } else singleCount += 1;
          if (question.knowledge.length === 0) missingKnowledgeCount += 1;
        } else if (row.status === "DUPLICATE" && row.duplicateOf) {
          if (params.duplicatePolicy === "SKIP") {
            skippedDuplicateCount += 1;
            continue;
          }
          const children = row.isSetPassage ? childrenOf(row) : [];
          questionStore.update(row.duplicateOf.id, (current) => {
            const next = questionFromRow(row, children, { id: current.id, code: current.code }, now);
            return {
              ...next,
              version: current.version + 1,
              usedInExamCount: current.usedInExamCount,
              createdBy: current.createdBy,
              createdAt: current.createdAt,
            };
          });
          newVersionCount += 1;
        }
      }

      const entry: ImportHistoryEntry = {
        id: mockId("imp"),
        importedAt: now,
        importedBy: MOCK_CURRENT_AUTHOR,
        fileName: upload.fileName,
        createdCount: singleCount + setCount,
        singleCount,
        setCount,
        setChildCount,
        skippedDuplicateCount,
        newVersionCount,
        errorRowCount: summary.error,
        missingKnowledgeCount,
        durationMs: Math.max(1000, Date.now() - startedAt),
        errors: collectErrorLines(rows),
      };
      importHistoryStore.insert(entry);
      uploads.delete(params.uploadId);
      return entry;
    },
    { ignoreScenario: true },
  );
}

/** Mã lỗi nghiệp vụ của lời gọi API (ví dụ `IMPORT_EMPTY_FILE`), để màn hình dịch; không có thì `undefined`. */
export function importApiErrorCode(error: unknown): string | undefined {
  return error instanceof MockApiError ? error.code : undefined;
}

export async function listImportHistory(): Promise<ImportHistoryEntry[]> {
  return mockRequest(() => [...importHistoryStore.all()], { empty: () => [] });
}
