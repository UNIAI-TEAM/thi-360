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
} from "./types";
import {
  clozeMarkers,
  type LabeledItem,
  parseClozeAnswers,
  parseDecimal,
  parseKeyedLines,
  parseLabeledLines,
  parseTrueFalseScoring,
} from "./row-parsers";

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

const WARNING_CODES: ReadonlySet<ImportIssueCode> = new Set([
  "KNOWLEDGE_MISSING",
  "TAG_UNKNOWN",
  "GRADING_GUIDE_MISSING",
  "ANSWER_MISSING",
]);

/** Giới hạn khi lưu (spec 4.3): phương án tối đa, mệnh đề Đúng/Sai tối đa, ô trống Điền khuyết tối đa, đáp án Trả lời ngắn. */
const MAX_CHOICE_OPTIONS = 10;
const MAX_TRUE_FALSE_STATEMENTS = 8;
const MAX_CLOZE_BLANKS = 30;
const MAX_SHORT_ANSWERS = 20;

/** Trường câu con lấy theo dòng ngữ liệu khi để trống. */
const INHERITED_FIELDS = ["subject", "grade", "difficulty", "knowledge"] as const satisfies readonly ImportField[];

/** Trường có thể chứa công thức LaTeX. */
const FORMULA_FIELDS = [
  "content",
  ...OPTION_FIELDS,
  "options",
  "matchRight",
  "solution",
  "gradingGuide",
  "commonMistakes",
] as const satisfies readonly ImportField[];

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

/** "A", "a, c", "B;D" thành danh sách chữ cái A tới J; ký hiệu lạ hoặc lặp thì trả `null`. */
function parseLetters(value: string): string[] | null {
  const parts = value
    .split(/[\s,;]+/)
    .map((part) => part.trim().toUpperCase())
    .filter(Boolean);
  if (parts.length === 0) return null;
  const letters: string[] = [];
  for (const part of parts) {
    if (!/^[A-J]$/.test(part) || letters.includes(part)) return null;
    letters.push(part);
  }
  return letters;
}

function closeTo(left: number, right: number): boolean {
  return Math.abs(left - right) < 1e-6;
}

/**
 * Cách chấm Đúng/Sai hợp lệ: đủ số ý; `PER_STATEMENT` cộng đúng điểm của câu (khi tệp ghi điểm); `COUNT_TABLE` không
 * giảm và phần tử cuối bằng điểm của câu (khi tệp ghi điểm).
 */
function trueFalseScoringValid(
  scoring: NonNullable<ImportQuestionDraft["trueFalseScoring"]>,
  statementCount: number,
  points: number | null,
): boolean {
  if (scoring.points.length !== statementCount) return false;
  if (scoring.method === "PER_STATEMENT") {
    const total = scoring.points.reduce((sum, value) => sum + value, 0);
    return total > 0 && (points === null || closeTo(total, points));
  }
  const nonDecreasing = scoring.points.every((value, index) => index === 0 || value >= scoring.points[index - 1]!);
  const last = scoring.points.at(-1) ?? 0;
  return nonDecreasing && last > 0 && (points === null || closeTo(last, points));
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
    scoringRule: null,
    trueFalseScoring: null,
    blanks: [],
    matching: null,
    commonMistakes: null,
    taskTypeCode: null,
    keepOptionOrder: false,
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
        ...(type ? { questionType: type } : {}),
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

    let points: number | null = null;
    if (raw.points) {
      const parsed = parseDecimal(raw.points);
      if (parsed === null || !(parsed > 0)) add("POINTS_INVALID", "points", { value: raw.points });
      else points = parsed;
    }
    draft.points = points;

    const taskType = raw.taskType ? findCatalogOption(reference.taskTypes, raw.taskType) : undefined;
    if (raw.taskType && !taskType) add("TASK_TYPE_UNKNOWN", "taskType", { value: raw.taskType });
    draft.taskTypeCode = taskType?.code ?? null;
    draft.commonMistakes = raw.commonMistakes || null;

    // Cột Lựa chọn (mỗi dòng `A. nội dung`) thay cho bốn cột phương án A, B, C, D của tệp cũ.
    const optionField: ImportField = raw.options ? "options" : "optionA";
    const listedOptions: LabeledItem[] = raw.options
      ? parseLabeledLines(raw.options, "letter")
      : OPTION_FIELDS.map((field, position) => ({ label: OPTION_LETTERS[position]!, content: raw[field] })).filter(
          (option) => option.content,
        );
    const filledOptions = listedOptions.map(({ label, content }) => ({ letter: label, content }));

    if (type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE") {
      if (filledOptions.length < 2) add("OPTIONS_TOO_FEW", optionField, { min: 2, questionType: type });
      if (filledOptions.length > MAX_CHOICE_OPTIONS) add("OPTIONS_TOO_MANY", optionField, { max: MAX_CHOICE_OPTIONS });
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
      if (type === "MULTIPLE_CHOICE" && raw.scoring) {
        const rule = raw.scoring.trim().toUpperCase();
        if (rule === "ALL_OR_NOTHING" || rule === "PARTIAL") draft.scoringRule = rule;
        else add("SCORING_INVALID", "scoring", { value: raw.scoring });
      }
    } else if (type === "TRUE_FALSE") {
      if (filledOptions.length < 1) add("OPTIONS_TOO_FEW", optionField, { min: 1, questionType: type });
      if (filledOptions.length > MAX_TRUE_FALSE_STATEMENTS) {
        add("OPTIONS_TOO_MANY", optionField, { max: MAX_TRUE_FALSE_STATEMENTS });
      }
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
      if (raw.scoring) {
        const scoring = parseTrueFalseScoring(raw.scoring);
        if (scoring === null || !trueFalseScoringValid(scoring, filledOptions.length, points)) {
          add("SCORING_INVALID", "scoring", { value: raw.scoring });
        } else draft.trueFalseScoring = scoring;
      }
    } else if (type === "SHORT_ANSWER") {
      if (!raw.correctAnswer) add("REQUIRED", "correctAnswer");
      // Mỗi dòng một đáp án (dòng đầu là đáp án chuẩn); tệp cũ ngăn cách bằng dấu | vẫn đọc được (D12).
      draft.acceptedAnswers = raw.correctAnswer
        .split(/[\n|]/)
        .map((answer) => answer.trim())
        .filter(Boolean);
      if (draft.acceptedAnswers.length > MAX_SHORT_ANSWERS) {
        add("ANSWER_INVALID", "correctAnswer", { value: raw.correctAnswer, questionType: type });
      }
      draft.inputHint = raw.inputHint || null;
    } else if (type === "CLOZE") {
      const markers = clozeMarkers(raw.content);
      const ids = Array.from(new Set(markers));
      if (ids.length !== markers.length || ids.length > MAX_CLOZE_BLANKS) {
        add("CLOZE_MARKER_INVALID", "content", { max: MAX_CLOZE_BLANKS });
      }
      const answerLines = raw.correctAnswer ? parseClozeAnswers(raw.correctAnswer) : { blanks: [], invalid: null };
      if (answerLines.invalid !== null) {
        add("ANSWER_INVALID", "correctAnswer", { value: answerLines.invalid, questionType: type });
      }
      const answerKeys = new Set<string>();
      for (const blank of answerLines.blanks) {
        if (answerKeys.has(blank.id)) add("ENTRY_DUPLICATE", "correctAnswer", { value: blank.id });
        answerKeys.add(blank.id);
        if (!ids.includes(blank.id)) add("ANSWER_INVALID", "correctAnswer", { value: blank.id, questionType: type });
      }
      const pointLines = raw.scoring ? parseKeyedLines(raw.scoring) : { entries: [], invalid: null };
      if (pointLines.invalid !== null) add("SCORING_INVALID", "scoring", { value: pointLines.invalid });
      const blankPoints = new Map<string, number>();
      const pointKeys = new Set<string>();
      for (const entry of pointLines.entries) {
        if (pointKeys.has(entry.key)) add("ENTRY_DUPLICATE", "scoring", { value: entry.key });
        pointKeys.add(entry.key);
        const value = parseDecimal(entry.value);
        if (!ids.includes(entry.key) || value === null || !(value > 0)) {
          add("SCORING_INVALID", "scoring", { value: `${entry.key} = ${entry.value}` });
        } else if (!blankPoints.has(entry.key)) blankPoints.set(entry.key, value);
      }
      draft.blanks = ids.map((id) => ({
        id,
        answers: answerLines.blanks.find((blank) => blank.id === id)?.answers ?? [],
        points: blankPoints.get(id) ?? null,
      }));
      if (draft.blanks.length === 0 || draft.blanks.some((blank) => blank.answers.length === 0)) {
        add("ANSWER_MISSING", "correctAnswer", { questionType: type });
      }
    } else if (type === "MATCHING") {
      const left = parseLabeledLines(raw.options, "number");
      const right = parseLabeledLines(raw.matchRight, "letter");
      if (left.length === 0) add("OPTIONS_TOO_FEW", "options", { min: 1, questionType: type });
      if (right.length === 0) add("OPTIONS_TOO_FEW", "matchRight", { min: 1, questionType: type });
      const repeatedLeftLabel = left.find((item, position) => left.findIndex((other) => other.label === item.label) !== position);
      if (repeatedLeftLabel) add("ENTRY_DUPLICATE", "options", { value: repeatedLeftLabel.label });
      const repeatedRightLabel = right.find(
        (item, position) => right.findIndex((other) => other.label === item.label) !== position,
      );
      if (repeatedRightLabel) add("ENTRY_DUPLICATE", "matchRight", { value: repeatedRightLabel.label });
      let reuseRight = false;
      if (raw.scoring) {
        const mode = raw.scoring.trim().toUpperCase();
        if (mode === "REUSE" || mode === "ONCE") reuseRight = mode === "REUSE";
        else add("SCORING_INVALID", "scoring", { value: raw.scoring });
      }
      const pairLines = raw.correctAnswer ? parseKeyedLines(raw.correctAnswer) : { entries: [], invalid: null };
      if (pairLines.invalid !== null) add("ANSWER_INVALID", "correctAnswer", { value: pairLines.invalid, questionType: type });
      const leftKeys = new Set(left.map((item) => item.label));
      const rightKeys = new Set(right.map((item) => item.label));
      const pairs: Array<{ leftKey: string; rightKey: string }> = [];
      for (const entry of pairLines.entries) {
        const leftKey = entry.key.toUpperCase();
        const rightKey = entry.value.toUpperCase();
        const repeatedLeft = pairs.some((pair) => pair.leftKey === leftKey);
        const repeatedRight = !reuseRight && pairs.some((pair) => pair.rightKey === rightKey);
        if (!leftKeys.has(leftKey) || !rightKeys.has(rightKey) || repeatedLeft || repeatedRight) {
          add("ANSWER_INVALID", "correctAnswer", { value: `${entry.key} = ${entry.value}`, questionType: type });
        } else pairs.push({ leftKey, rightKey });
      }
      if (left.some((item) => !pairs.some((pair) => pair.leftKey === item.label))) {
        add("ANSWER_MISSING", "correctAnswer", { questionType: type });
      }
      draft.matching = {
        left: left.map((item) => ({ key: item.label, content: item.content })),
        right: right.map((item) => ({ key: item.label, content: item.content })),
        pairs,
        reuseRight,
      };
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

    for (const field of FORMULA_FIELDS) {
      if (raw[field] && !bracesBalanced(raw[field])) add("FORMULA_INVALID", field);
    }

    const parent = parentIndexOf(index);
    // Điền khuyết không làm câu con của Bộ câu hỏi (D19).
    if (parent !== undefined && type === "CLOZE") add("SET_CHILD_TYPE_INVALID", "type", { value: raw.type });
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
