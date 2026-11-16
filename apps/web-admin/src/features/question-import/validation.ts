import type {
  CatalogOption,
  ContentReference,
  KnowledgeNode,
  KnowledgeRef,
  QuestionType,
} from "@/features/content/types";
import { checkMapping } from "./mapping";
import {
  type ColumnMapping,
  type ImportColumn,
  type ImportDictionary,
  type ImportDraftCriterion,
  type ImportDraftOption,
  type ImportDraftStatement,
  type ImportField,
  IMPORT_FIELDS,
  type ImportIssueCode,
  type ImportQuestionDraft,
  type ImportRowIssue,
  type ImportRowResult,
  type ImportSheetRow,
  type ImportSummary,
  OPTION_FIELDS,
  OPTION_LETTERS,
  type OptionLetter,
} from "./types";

/**
 * Kiểm tra từng dòng của tệp nhập câu hỏi (bước 3, bản vẽ `NhapCauHoiKiemTra`). Hàm thuần: cùng đầu vào cho cùng kết
 * quả, không đọc kho dữ liệu. Khi có backend, việc kiểm tra chạy ở máy chủ; hàm này chỉ còn dùng cho dữ liệu mẫu.
 *
 * Quy tắc (theo bản vẽ, chờ BA chốt chi tiết):
 * - Dạng câu, Nội dung, Môn, Khối, Mức độ bắt buộc ở mọi dòng; Đáp án đúng bắt buộc trừ dòng ngữ liệu câu chùm.
 * - Câu con của câu chùm (cùng Mã chùm với một dòng dạng Câu chùm) để trống Môn, Khối, Mức độ, Kiến thức thì lấy theo
 *   dòng ngữ liệu (UniNote PBI-03: phân loại chung áp dụng cho câu con).
 * - Mã nguồn trùng trong tệp: dòng sau là lỗi. Trùng với câu đã có trong ngân hàng: dòng là "trùng", xử lý theo lựa
 *   chọn ở bước 2. Chỉ xét trùng ngân hàng cho câu đơn và dòng ngữ liệu; câu con đi theo dòng ngữ liệu của nó.
 * - Thiếu kiến thức và nhãn không có trong danh mục chỉ là cảnh báo, vẫn nhập.
 * - Tự luận (UNI-2599): cột Đáp án (nếu có) là bài làm mẫu; cột Tiêu chí chấm ghi "Mô tả: điểm", các tiêu chí cách
 *   nhau dấu ; hoặc xuống dòng. Để trống thì cảnh báo, câu vẫn nhập ở Nháp và chưa chuyển Sẵn sàng được tới khi bổ
 *   sung tiêu chí; viết sai thì lỗi.
 */

export interface ExistingQuestionRef {
  id: string;
  code: string;
  sourceCode: string | null;
}

export interface ValidateImportRowsInput {
  columns: readonly ImportColumn[];
  rows: readonly ImportSheetRow[];
  mapping: ColumnMapping;
  reference: ContentReference;
  existingQuestions: readonly ExistingQuestionRef[];
  dictionary: ImportDictionary;
}

const WARNING_CODES: ReadonlySet<ImportIssueCode> = new Set(["KNOWLEDGE_MISSING", "TAG_UNKNOWN", "GRADING_GUIDE_MISSING"]);

/** Trường câu con lấy theo dòng ngữ liệu khi để trống. */
const INHERITED_FIELDS = ["subject", "grade", "difficulty", "knowledge"] as const satisfies readonly ImportField[];

/** Trường có thể chứa công thức LaTeX. */
const FORMULA_FIELDS = ["content", ...OPTION_FIELDS, "solution"] as const satisfies readonly ImportField[];

type RawRow = Record<ImportField, string>;

function sameText(left: string, right: string): boolean {
  return left.normalize("NFC").trim().toLocaleLowerCase("vi") === right.normalize("NFC").trim().toLocaleLowerCase("vi");
}

function sameCode(left: string, right: string): boolean {
  return left.trim().toUpperCase() === right.trim().toUpperCase();
}

function splitList(value: string): string[] {
  return value
    .split(/[,;]/)
    .map((part) => part.trim())
    .filter(Boolean);
}

/** Môn, mức độ, nhãn: khớp mã hoặc tên (không phân biệt hoa thường; có phân biệt dấu, "Toan" khác "Toán"). */
function findCatalogOption(options: readonly CatalogOption[], value: string): CatalogOption | undefined {
  return options.find((option) => sameCode(option.code, value) || sameText(option.name, value));
}

/** Khối: khớp mã, tên, hoặc chỉ số lớp ("11" khớp "Lớp 11"). */
function findGrade(options: readonly CatalogOption[], value: string): CatalogOption | undefined {
  const direct = findCatalogOption(options, value);
  if (direct) return direct;
  if (!/^\d+$/.test(value.trim())) return undefined;
  return options.find((option) => option.name.replace(/\D/g, "") === value.trim());
}

/**
 * Bộ chuyển đổi tệp nhập cũ (D12): mã dạng cũ trong tệp vẫn được xử lý. `FILL_IN` một ô nhập thành Trả lời ngắn;
 * `DRAG_DROP` thành Ghép đôi.
 */
const LEGACY_TYPE_CODES: Readonly<Record<string, QuestionType>> = {
  FILL_IN: "SHORT_ANSWER",
  DRAG_DROP: "MATCHING",
};

function resolveType(value: string, dictionary: ImportDictionary): QuestionType | null {
  if (!value) return null;
  const legacy = LEGACY_TYPE_CODES[value.trim().toUpperCase()];
  if (legacy) return legacy;
  for (const [type, aliases] of Object.entries(dictionary.typeAliases) as [QuestionType, string[]][]) {
    if (sameCode(type, value) || aliases.some((alias) => sameText(alias, value))) return type;
  }
  return null;
}

/** Dấu ngoặc nhọn của công thức có đóng mở đủ (bỏ qua `\{`, `\}`). */
export function bracesBalanced(text: string): boolean {
  let depth = 0;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === "\\") {
      index += 1;
      continue;
    }
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth < 0) return false;
    }
  }
  return depth === 0;
}

/** "A", "a, c", "B;D" thành danh sách chữ cái; ký hiệu lạ hoặc lặp thì trả `null`. */
function parseLetters(value: string): OptionLetter[] | null {
  const parts = value
    .split(/[\s,;]+/)
    .map((part) => part.trim().toUpperCase())
    .filter(Boolean);
  if (parts.length === 0) return null;
  const letters: OptionLetter[] = [];
  for (const part of parts) {
    if (!(OPTION_LETTERS as readonly string[]).includes(part) || letters.includes(part as OptionLetter)) return null;
    letters.push(part as OptionLetter);
  }
  return letters;
}

const CRITERION_RE = /^(.+?)\s*(?:[:=]\s*(\d+(?:[.,]\d+)?)|\(\s*(\d+(?:[.,]\d+)?)\s*\))\s*$/u;

/**
 * Cột Tiêu chí chấm thành danh sách tiêu chí: mỗi tiêu chí "Mô tả: 0,5" hoặc "Mô tả (0,5)", cách nhau dấu ; hoặc xuống
 * dòng. Trả `{ invalid }` là phần đầu tiên không đọc được (thiếu điểm hoặc điểm không lớn hơn 0).
 */
export function parseRubric(value: string): { criteria: ImportDraftCriterion[]; invalid: string | null } {
  const criteria: ImportDraftCriterion[] = [];
  const parts = value
    .split(/[;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
  for (const part of parts) {
    const match = CRITERION_RE.exec(part);
    const points = Number((match?.[2] ?? match?.[3] ?? "").replace(",", "."));
    if (!match || !(points > 0)) return { criteria, invalid: part };
    criteria.push({ description: match[1]!.trim(), points });
  }
  return { criteria, invalid: null };
}

function flattenKnowledge(nodes: readonly KnowledgeNode[], into: KnowledgeNode[] = []): KnowledgeNode[] {
  for (const node of nodes) {
    into.push(node);
    flattenKnowledge(node.children, into);
  }
  return into;
}

function emptyDraft(): ImportQuestionDraft {
  return {
    type: null,
    content: "",
    options: [],
    statements: [],
    acceptedAnswers: [],
    points: null,
    inputHint: null,
    gradingGuide: null,
    rubric: [],
    sampleAnswer: null,
    solution: null,
    subjectCode: null,
    gradeCode: null,
    difficultyCode: null,
    knowledge: [],
    tagCodes: [],
    setCode: null,
  };
}

export function hasErrors(issues: readonly ImportRowIssue[]): boolean {
  return issues.some((issue) => issue.severity === "error");
}

export function validateImportRows(input: ValidateImportRowsInput): ImportRowResult[] {
  const { columns, rows, mapping, reference, existingQuestions, dictionary } = input;
  const { columnByField } = checkMapping(columns, mapping);
  const headerByKey = new Map(columns.map((column) => [column.key, column.header]));
  const headerOf = (field: ImportField | null): string | null => {
    if (!field) return null;
    const key = columnByField.get(field);
    return key ? (headerByKey.get(key) ?? null) : null;
  };
  const knowledgeByCode = new Map(
    flattenKnowledge(reference.knowledgeTree).map((node) => [node.code.toUpperCase(), node]),
  );

  // Lượt 1: giá trị thô theo trường, dạng câu, và dòng ngữ liệu của từng Mã chùm.
  const raws: RawRow[] = rows.map((row) => {
    const raw = {} as RawRow;
    for (const field of IMPORT_FIELDS) {
      const key = columnByField.get(field);
      raw[field] = key ? (row.cells[key] ?? "").trim() : "";
    }
    return raw;
  });
  const types = raws.map((raw) => resolveType(raw.type, dictionary));
  const passageIndexBySet = new Map<string, number>();
  const repeatedPassage = new Map<number, number>();
  raws.forEach((raw, index) => {
    if (types[index] !== "QUESTION_SET" || !raw.setCode) return;
    const setKey = raw.setCode.toUpperCase();
    const first = passageIndexBySet.get(setKey);
    if (first === undefined) passageIndexBySet.set(setKey, index);
    else repeatedPassage.set(index, first);
  });
  const parentIndexOf = (index: number): number | undefined => {
    const raw = raws[index]!;
    if (types[index] === "QUESTION_SET" || !raw.setCode) return undefined;
    return passageIndexBySet.get(raw.setCode.toUpperCase());
  };
  // Câu con để trống phân loại thì lấy theo dòng ngữ liệu. Lỗi của giá trị lấy về đã báo ở dòng ngữ liệu (câu con
  // lỗi theo bằng SET_PASSAGE_INVALID), nên không báo lại ở câu con.
  const inheritedByRow = raws.map(() => new Set<ImportField>());
  raws.forEach((raw, index) => {
    const parent = parentIndexOf(index);
    if (parent === undefined) return;
    for (const field of INHERITED_FIELDS) {
      if (!raw[field] && raws[parent]![field]) {
        raw[field] = raws[parent]![field];
        inheritedByRow[index]!.add(field);
      }
    }
  });

  // Lượt 2: kiểm tra từng dòng.
  const results: ImportRowResult[] = rows.map((row, index) => {
    const raw = raws[index]!;
    const type = types[index] ?? null;
    const issues: ImportRowIssue[] = [];
    const add = (code: ImportIssueCode, field: ImportField | null, extra: Partial<ImportRowIssue> = {}) => {
      if (field && inheritedByRow[index]!.has(field)) return;
      issues.push({
        code,
        severity: WARNING_CODES.has(code) ? "warning" : "error",
        field,
        column: headerOf(field),
        ...extra,
      });
    };
    const draft = emptyDraft();
    draft.type = type;
    draft.content = raw.content;
    draft.solution = raw.solution || null;
    draft.setCode = raw.setCode || null;
    const isSetPassage = type === "QUESTION_SET";

    if (!raw.type) add("REQUIRED", "type");
    else if (!type) add("TYPE_UNKNOWN", "type", { value: raw.type });
    else if (type === "MATCHING" || type === "CLOZE") add("TYPE_NOT_IMPORTABLE", "type", { value: raw.type });

    if (!raw.content) add("REQUIRED", "content");

    const subject = raw.subject ? findCatalogOption(reference.subjects, raw.subject) : undefined;
    if (!raw.subject) add("REQUIRED", "subject");
    else if (!subject) add("SUBJECT_UNKNOWN", "subject", { value: raw.subject });
    draft.subjectCode = subject?.code ?? null;

    const grade = raw.grade ? findGrade(reference.grades, raw.grade) : undefined;
    if (!raw.grade) add("REQUIRED", "grade");
    else if (!grade) add("GRADE_UNKNOWN", "grade", { value: raw.grade });
    draft.gradeCode = grade?.code ?? null;

    const difficulty = raw.difficulty ? findCatalogOption(reference.difficulties, raw.difficulty) : undefined;
    if (!raw.difficulty) add("REQUIRED", "difficulty");
    else if (!difficulty) add("DIFFICULTY_UNKNOWN", "difficulty", { value: raw.difficulty });
    draft.difficultyCode = difficulty?.code ?? null;

    const knowledge: KnowledgeRef[] = [];
    for (const code of splitList(raw.knowledge)) {
      const node = knowledgeByCode.get(code.toUpperCase());
      if (!node || (subject && node.subjectCode !== subject.code)) {
        add("KNOWLEDGE_UNKNOWN", "knowledge", { value: code, subjectCode: subject?.code });
      } else if (node.level !== "KNOWLEDGE") {
        add("KNOWLEDGE_NOT_LEAF", "knowledge", { value: code });
      } else if (!knowledge.some((ref) => ref.nodeId === node.id)) {
        knowledge.push({ nodeId: node.id, code: node.code, name: node.name, primary: knowledge.length === 0 });
      }
    }
    if (!raw.knowledge) add("KNOWLEDGE_MISSING", "knowledge");
    draft.knowledge = knowledge;

    for (const tag of splitList(raw.tags)) {
      const found = findCatalogOption(reference.tags, tag);
      if (!found) add("TAG_UNKNOWN", "tags", { value: tag });
      else if (!draft.tagCodes.includes(found.code)) draft.tagCodes.push(found.code);
    }

    const filledOptions = OPTION_FIELDS.map((field, position) => ({
      letter: OPTION_LETTERS[position]!,
      content: raw[field],
    })).filter((option) => option.content);

    if (type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE") {
      if (filledOptions.length < 2) add("OPTIONS_TOO_FEW", "optionA", { min: 2, questionType: type });
      const letters = raw.correctAnswer ? parseLetters(raw.correctAnswer) : null;
      if (!raw.correctAnswer) {
        add("REQUIRED", "correctAnswer");
      } else {
        const known = new Set(filledOptions.map((option) => option.letter));
        const ok =
          letters !== null &&
          letters.every((letter) => known.has(letter)) &&
          (type === "SINGLE_CHOICE" ? letters.length === 1 : letters.length >= 1);
        if (!ok) add("ANSWER_INVALID", "correctAnswer", { value: raw.correctAnswer, questionType: type });
      }
      draft.options = filledOptions.map<ImportDraftOption>((option) => ({
        ...option,
        correct: letters?.includes(option.letter) ?? false,
      }));
    } else if (type === "TRUE_FALSE") {
      if (filledOptions.length < 1) add("OPTIONS_TOO_FEW", "optionA", { min: 1, questionType: type });
      const tokens = raw.correctAnswer.split(/[\s,;]+/).filter(Boolean);
      const answers = tokens.map((token) =>
        dictionary.trueTokens.some((candidate) => sameText(candidate, token))
          ? true
          : dictionary.falseTokens.some((candidate) => sameText(candidate, token))
            ? false
            : null,
      );
      if (!raw.correctAnswer) add("REQUIRED", "correctAnswer");
      else if (answers.length !== filledOptions.length || answers.some((answer) => answer === null)) {
        add("ANSWER_INVALID", "correctAnswer", { value: raw.correctAnswer, questionType: type });
      }
      draft.statements = filledOptions.map<ImportDraftStatement>((option, position) => ({
        ...option,
        answer: answers[position] ?? null,
      }));
    } else if (type === "SHORT_ANSWER") {
      if (!raw.correctAnswer) add("REQUIRED", "correctAnswer");
      // Mỗi dòng một đáp án (dòng đầu là đáp án chuẩn); tệp cũ ngăn cách bằng dấu | vẫn đọc được (D12).
      draft.acceptedAnswers = raw.correctAnswer
        .split(/[\n|]/)
        .map((answer) => answer.trim())
        .filter(Boolean);
      draft.inputHint = raw.inputHint || null;
    } else if (type === "ESSAY") {
      draft.sampleAnswer = raw.correctAnswer || null;
      draft.gradingGuide = raw.gradingGuide || null;
      if (!raw.gradingGuide) add("GRADING_GUIDE_MISSING", "gradingGuide");
      if (raw.rubric) {
        const { criteria, invalid } = parseRubric(raw.rubric);
        if (invalid !== null) add("RUBRIC_INVALID", "rubric", { value: invalid });
        draft.rubric = criteria;
      }
    }

    if (raw.points) {
      const points = Number(raw.points.replace(",", "."));
      if (!(points > 0)) add("POINTS_INVALID", "points", { value: raw.points });
      else draft.points = points;
    }

    for (const field of FORMULA_FIELDS) {
      if (raw[field] && !bracesBalanced(raw[field])) add("FORMULA_INVALID", field);
    }

    const parent = parentIndexOf(index);
    if (isSetPassage && !raw.setCode) add("REQUIRED", "setCode");
    if (!isSetPassage && raw.setCode && parent === undefined) {
      add("SET_PASSAGE_MISSING", "setCode", { value: raw.setCode });
    }
    const firstPassage = repeatedPassage.get(index);
    if (firstPassage !== undefined) {
      add("SET_CODE_REPEATED", "setCode", { value: raw.setCode, relatedRow: rows[firstPassage]!.rowNumber });
    }

    return {
      rowNumber: row.rowNumber,
      sourceCode: raw.sourceCode || null,
      status: "VALID",
      issues,
      draft,
      duplicateOf: null,
      isSetPassage,
      parentRowNumber: parent === undefined ? null : rows[parent]!.rowNumber,
    };
  });

  // Lượt 3: Mã nguồn trùng trong tệp (dòng sau là lỗi), rồi trùng với ngân hàng.
  const firstRowBySource = new Map<string, number>();
  for (const result of results) {
    if (!result.sourceCode) continue;
    const key = result.sourceCode.toUpperCase();
    const first = firstRowBySource.get(key);
    if (first === undefined) firstRowBySource.set(key, result.rowNumber);
    else {
      result.issues.push({
        code: "DUPLICATE_IN_FILE",
        severity: "error",
        field: "sourceCode",
        column: headerOf("sourceCode"),
        value: result.sourceCode,
        relatedRow: first,
      });
    }
  }
  const existingBySource = new Map<string, ExistingQuestionRef>();
  for (const question of existingQuestions) {
    if (question.sourceCode) existingBySource.set(question.sourceCode.toUpperCase(), question);
  }
  for (const result of results) {
    if (result.parentRowNumber !== null || !result.sourceCode || hasErrors(result.issues)) continue;
    const existing = existingBySource.get(result.sourceCode.toUpperCase());
    if (existing) result.duplicateOf = { id: existing.id, code: existing.code };
  }

  // Lượt 4: câu chùm đi cùng câu con. Ngữ liệu lỗi thì câu con lỗi theo; ngữ liệu trùng thì câu con trùng theo;
  // ngữ liệu không còn câu con hợp lệ nào thì lỗi.
  for (const passage of results) {
    if (!passage.isSetPassage) continue;
    const children = results.filter((result) => result.parentRowNumber === passage.rowNumber);
    if (hasErrors(passage.issues)) {
      for (const child of children) {
        child.issues.push({
          code: "SET_PASSAGE_INVALID",
          severity: "error",
          field: "setCode",
          column: headerOf("setCode"),
          value: passage.draft.setCode ?? undefined,
          relatedRow: passage.rowNumber,
        });
      }
    } else if (!children.some((child) => !hasErrors(child.issues))) {
      passage.duplicateOf = null;
      passage.issues.push({
        code: "SET_NO_CHILDREN",
        severity: "error",
        field: "setCode",
        column: headerOf("setCode"),
        value: passage.draft.setCode ?? undefined,
      });
    } else if (passage.duplicateOf) {
      for (const child of children) {
        if (!hasErrors(child.issues)) child.duplicateOf = passage.duplicateOf;
      }
    }
  }

  for (const result of results) {
    result.status = hasErrors(result.issues) ? "ERROR" : result.duplicateOf ? "DUPLICATE" : "VALID";
  }
  return results;
}

export function summarizeImport(rows: readonly ImportRowResult[]): ImportSummary {
  const summary: ImportSummary = { total: rows.length, valid: 0, error: 0, duplicate: 0, validSets: 0, validSetChildren: 0 };
  for (const row of rows) {
    if (row.status === "VALID") {
      summary.valid += 1;
      if (row.isSetPassage) summary.validSets += 1;
      if (row.parentRowNumber !== null) summary.validSetChildren += 1;
    } else if (row.status === "ERROR") summary.error += 1;
    else summary.duplicate += 1;
  }
  return summary;
}
