import type { AppLocale } from "@thi360/web-kit/preferences";
import type { KnowledgeRef, MultipleChoiceRule, QuestionType, TrueFalseMethod } from "@/features/content/types";
import { normalizePrimary } from "@/features/content/knowledge-tree";
import { isChildQuestionType, type ChildQuestionType, type SaveChildQuestionRequest, type SaveQuestionRequest } from "./editor-types";
import { clozeMarker, type QuestionBody, type QuestionDetail } from "./types";

/**
 * Giá trị biểu mẫu soạn câu hỏi và chuyển đổi qua lại với dữ liệu API.
 *
 * Phần thân theo dạng câu gom vào một `EditorBody` có đủ các mảng của mọi dạng: đường dẫn trường giống nhau ở câu
 * đơn (`body.options`) và câu con của câu chùm (`children.0.body.options`), nên các khối soạn dùng lại được. Khi lưu
 * chỉ lấy phần của dạng đang chọn.
 */

export interface EditorOption {
  id: string;
  content: string;
  correct: boolean;
}

export type StatementAnswer = "" | "TRUE" | "FALSE";

export interface EditorStatement {
  id: string;
  content: string;
  answer: StatementAnswer;
  /** Điểm của ý khi chấm `PER_STATEMENT` (chuỗi như ô Điểm); chấm theo bảng thì không dùng. */
  points: string;
}

export interface EditorBlank {
  id: string;
  /** Đối tượng thay cho chuỗi để `useFieldArray` giữ được khóa của từng dòng. */
  answers: { id: string; value: string }[];
  /** Điểm của ô (Điền khuyết; chuỗi như ô Điểm, mặc định 1). Trả lời ngắn không dùng. */
  points: string;
}

/** Mục cột trái của câu Ghép đôi, kèm lựa chọn đúng ở cột phải và điểm của cặp. */
export interface EditorMatchLeft {
  id: string;
  content: string;
  /** Id lựa chọn đúng ở cột phải; chuỗi rỗng: chưa chọn. */
  rightId: string;
  points: string;
}

export interface EditorMatchRight {
  id: string;
  content: string;
}

/** Một tiêu chí chấm của câu Tự luận; điểm là chuỗi như ô Điểm của câu. */
export interface EditorCriterion {
  id: string;
  description: string;
  points: string;
}

export interface EditorBody {
  options: EditorOption[];
  /** Nhiều đáp án: đúng hết mới có điểm hay chấm từng phần. */
  scoringRule: MultipleChoiceRule;
  statements: EditorStatement[];
  /** Đúng/Sai: cộng điểm từng ý hay tra bảng theo số ý đúng. */
  scoringMethod: TrueFalseMethod;
  /** Đúng/Sai: điểm khi đúng 1, 2, … ý (chuỗi như ô Điểm); rỗng: chia đều theo ý. */
  scoreTable: string[];
  blanks: EditorBlank[];
  /** Ghép đôi: cột trái (kèm đáp án và điểm từng cặp), cột phải (có thể nhiều hơn cột trái). */
  left: EditorMatchLeft[];
  right: EditorMatchRight[];
  /** Ghép đôi: một lựa chọn dùng cho nhiều mục trái (`true`) hay một lần (`false`). */
  reuseRight: boolean;
  /** Tự luận: hướng dẫn chấm (bắt buộc khi Sẵn sàng), tiêu chí chấm (tùy chọn), bài làm mẫu, số chữ gợi ý (rỗng: không giới hạn). */
  gradingGuide: string;
  rubric: EditorCriterion[];
  sampleAnswer: string;
  wordLimit: string;
  /** Trả lời ngắn: hướng dẫn nhập cho học viên. */
  inputHint: string;
  /** Câu chùm: số bắt đầu đánh chỗ trống của ngữ liệu (chuỗi như ô nhập; 1–200, mặc định "1"); dạng khác không dùng. */
  blankNumberStart: string;
}

export interface EditorChild {
  /** Khóa dòng của biểu mẫu. */
  key: string;
  /** Id câu con đã lưu; `null` với câu con mới. */
  existingId: string | null;
  type: ChildQuestionType;
  content: string;
  solution: string;
  points: string;
  /** Khối lớp, độ khó riêng của câu con; chuỗi rỗng: kế thừa của bộ. */
  gradeCode: string;
  difficultyCode: string;
  /** `true`: câu con có kiến thức riêng (`knowledge`); `false`: kế thừa của bộ. */
  knowledgeOverride: boolean;
  knowledge: KnowledgeRef[];
  body: EditorBody;
}

export interface QuestionEditorValues {
  type: QuestionType;
  subjectCode: string;
  gradeCode: string;
  difficultyCode: string;
  /** Năng lực (V-ACT); chuỗi rỗng: không gắn. */
  competencyCode: string;
  /** Dạng bài; chuỗi rỗng: không gắn. */
  taskTypeCode: string;
  /** Chuỗi để giữ đúng điều người dùng gõ ("1,5"); đổi sang số khi lưu. */
  points: string;
  knowledge: KnowledgeRef[];
  tagCodes: string[];
  /** Đề bài; câu chùm: đoạn dẫn chung. */
  content: string;
  solution: string;
  /** Lỗi sai thường gặp. */
  commonMistakes: string;
  attachmentIds: string[];
  body: EditorBody;
  children: EditorChild[];
}

/** Chỗ trống trong đề bài của câu Điền đáp án (giống dữ liệu mẫu và tệp nhập). */
export const BLANK_MARKER = "___";

export function countBlankMarkers(content: string): number {
  return content.split(BLANK_MARKER).length - 1;
}

/** Khoảng hợp lệ của `blankNumberStart` (spec UNI-2638 mục 3.4). */
export const BLANK_NUMBER_START_MIN = 1;
export const BLANK_NUMBER_START_MAX = 200;

/** Ô "Đánh số từ" là số nguyên 1–200 không? */
export function isBlankNumberStart(value: string): boolean {
  const text = value.trim();
  if (!/^\d{1,3}$/.test(text)) return false;
  const number = Number(text);
  return number >= BLANK_NUMBER_START_MIN && number <= BLANK_NUMBER_START_MAX;
}

/** Giá trị gửi lên: ô hợp lệ thì lấy, không thì 1 (kiểm tra đã chặn lúc lưu). */
export function parseBlankNumberStart(value: string): number {
  return isBlankNumberStart(value) ? Number(value.trim()) : BLANK_NUMBER_START_MIN;
}

/** Điểm mặc định của câu đơn và câu con (UniNote PBI-03 mục 4.1). */
export const DEFAULT_POINTS = 1;

let rowSequence = 0;
/** Id tạm cho dòng mới trong biểu mẫu; máy chủ giữ hoặc thay khi lưu. */
export function draftRowId(prefix: string): string {
  rowSequence += 1;
  return `new-${prefix}-${rowSequence}`;
}

/** Id này là id tạm của dòng chưa lưu (từ `draftRowId`)? */
export function isDraftRowId(id: string): boolean {
  return id.startsWith("new-");
}

export function emptyOption(): EditorOption {
  return { id: draftRowId("o"), content: "", correct: false };
}

export function emptyStatement(): EditorStatement {
  return { id: draftRowId("s"), content: "", answer: "", points: "" };
}

export function emptyBlank(): EditorBlank {
  return { id: draftRowId("b"), answers: [{ id: draftRowId("a"), value: "" }], points: String(DEFAULT_POINTS) };
}

export function emptyMatchRight(): EditorMatchRight {
  return { id: draftRowId("mr"), content: "" };
}

export function emptyMatchLeft(rightId = "", points = ""): EditorMatchLeft {
  return { id: draftRowId("ml"), content: "", rightId, points };
}

export function emptyCriterion(points = ""): EditorCriterion {
  return { id: draftRowId("r"), description: "", points };
}

/**
 * Phần thân trống theo dạng: 4 phương án, 2 ý Đúng/Sai (bảng điểm chia đều, điểm từng ý chia đều), 2 cặp ghép chia
 * đều điểm, 1 tiêu chí chấm mang đủ điểm mặc định; Điền đáp án thêm ô cùng lúc với chỗ trống.
 */
export function emptyBody(type: QuestionType, locale: AppLocale = "vi"): EditorBody {
  const matchRight = type === "MATCHING" ? [emptyMatchRight(), emptyMatchRight()] : [];
  const matchShare = evenSplitText(matchRight.length, DEFAULT_POINTS, locale);
  const statements = type === "TRUE_FALSE" ? [emptyStatement(), emptyStatement()] : [];
  const statementShare = evenSplitText(statements.length, DEFAULT_POINTS, locale);
  return {
    options:
      type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE"
        ? [emptyOption(), emptyOption(), emptyOption(), emptyOption()]
        : [],
    scoringRule: "ALL_OR_NOTHING",
    statements: statements.map((statement, index) => ({ ...statement, points: statementShare[index] ?? "" })),
    scoringMethod: "COUNT_TABLE",
    scoreTable: type === "TRUE_FALSE" ? evenScoreTableText(2, DEFAULT_POINTS, locale) : [],
    blanks: type === "SHORT_ANSWER" ? [emptyBlank()] : [],
    left: matchRight.map((right, index) => emptyMatchLeft(right.id, matchShare[index] ?? "")),
    right: matchRight,
    reuseRight: false,
    gradingGuide: "",
    rubric: [],
    sampleAnswer: "",
    wordLimit: "",
    inputHint: "",
    blankNumberStart: "1",
  };
}

export function emptyChild(type: ChildQuestionType, locale: AppLocale = "vi"): EditorChild {
  return {
    key: draftRowId("c"),
    existingId: null,
    type,
    content: "",
    solution: "",
    points: String(DEFAULT_POINTS),
    gradeCode: "",
    difficultyCode: "",
    knowledgeOverride: false,
    knowledge: [],
    body: emptyBody(type, locale),
  };
}

export function emptyEditorValues(type: QuestionType, locale: AppLocale = "vi"): QuestionEditorValues {
  return {
    type,
    subjectCode: "",
    gradeCode: "",
    difficultyCode: "",
    competencyCode: "",
    taskTypeCode: "",
    points: String(DEFAULT_POINTS),
    knowledge: [],
    tagCodes: [],
    content: "",
    solution: "",
    commonMistakes: "",
    attachmentIds: [],
    body: emptyBody(type, locale),
    children: [],
  };
}

/** 1.5 → "1,5" (vi) hoặc "1.5" (en): cách viết của ô Điểm theo ngôn ngữ giao diện. Khi đọc ô, cả hai dấu đều được nhận. */
export function formatPoints(points: number, locale: AppLocale = "vi"): string {
  return locale === "en" ? String(points) : String(points).replace(".", ",");
}

/** "1,5" hoặc "1.5" → 1.5; không phải số thì `NaN`. */
export function parsePoints(value: string): number {
  const normalized = value.trim().replace(",", ".");
  if (!/^\d+(\.\d+)?$/.test(normalized)) return Number.NaN;
  return Number(normalized);
}

/** Dòng đáp án của biểu mẫu; câu Nháp chưa có đáp án vẫn hiện một dòng trống để nhập. */
function answerRows(answers: readonly string[]): { id: string; value: string }[] {
  return (answers.length > 0 ? answers : [""]).map((value) => ({ id: draftRowId("a"), value }));
}

export function bodyToEditor(body: QuestionBody, locale: AppLocale = "vi"): EditorBody {
  const base = emptyBody("QUESTION_SET");
  switch (body.type) {
    case "SINGLE_CHOICE":
      return {
        ...base,
        options: body.options.map((option) => ({ ...option })),
      };
    case "MULTIPLE_CHOICE":
      return {
        ...base,
        options: body.options.map((option) => ({ ...option })),
        scoringRule: body.scoringRule,
      };
    case "TRUE_FALSE": {
      const perStatement = body.scoringMethod === "PER_STATEMENT";
      const questionPoints = perStatement
        ? roundPoints(body.statements.reduce((sum, statement) => sum + (statement.points ?? 0), 0))
        : (body.scoreTable.at(-1) ?? DEFAULT_POINTS);
      const share = evenSplitText(body.statements.length, questionPoints > 0 ? questionPoints : DEFAULT_POINTS, locale);
      return {
        ...base,
        scoringMethod: body.scoringMethod,
        statements: body.statements.map((statement, index) => ({
          id: statement.id,
          content: statement.content,
          answer: statement.answer ? "TRUE" : "FALSE",
          points: statement.points === null ? (share[index] ?? "") : formatPoints(statement.points, locale),
        })),
        scoreTable: perStatement
          ? evenScoreTableText(body.statements.length, questionPoints > 0 ? questionPoints : DEFAULT_POINTS, locale)
          : body.scoreTable.map((value) => formatPoints(value, locale)),
      };
    }
    case "SHORT_ANSWER":
      return {
        ...base,
        blanks: [
          {
            id: draftRowId("b"),
            answers: answerRows(body.answers),
            points: String(DEFAULT_POINTS),
          },
        ],
        inputHint: body.inputHint ?? "",
      };
    case "CLOZE":
      return {
        ...base,
        blanks: body.blanks.map((blank) => ({
          id: blank.id,
          answers: answerRows(blank.answers),
          points: formatPoints(blank.points, locale),
        })),
      };
    case "MATCHING": {
      const rightOf = new Map(body.pairs.map((pair) => [pair.leftId, pair]));
      return {
        ...base,
        left: body.left.map((item) => {
          const pair = rightOf.get(item.id);
          return { id: item.id, content: item.content, rightId: pair?.rightId ?? "", points: pair ? formatPoints(pair.points, locale) : "" };
        }),
        right: body.right.map((item) => ({ ...item })),
        reuseRight: body.reuseRight,
      };
    }
    case "QUESTION_SET":
      return { ...base, blankNumberStart: String(body.blankNumberStart) };
    case "ESSAY":
      return {
        ...base,
        gradingGuide: body.gradingGuide,
        rubric: body.rubric.map((criterion) => ({
          id: criterion.id,
          description: criterion.description,
          points: formatPoints(criterion.points, locale),
        })),
        sampleAnswer: body.sampleAnswer ?? "",
        wordLimit: body.wordLimit === null ? "" : String(body.wordLimit),
      };
  }
}

export function toEditorValues(question: QuestionDetail, locale: AppLocale = "vi"): QuestionEditorValues {
  return {
    type: question.type,
    subjectCode: question.subjectCode,
    gradeCode: question.gradeCode,
    difficultyCode: question.difficultyCode,
    competencyCode: question.competencyCode ?? "",
    taskTypeCode: question.taskTypeCode ?? "",
    points: formatPoints(question.points, locale),
    knowledge: normalizePrimary(question.knowledge),
    tagCodes: [...question.tagCodes],
    content: question.body.type === "CLOZE" ? clozeToEditorContent(question.body.passage) : question.content,
    solution: question.solution ?? "",
    commonMistakes: question.commonMistakes ?? "",
    attachmentIds: [...question.attachmentIds],
    body: bodyToEditor(question.body, locale),
    children: question.children.map((child) => {
      const sameKnowledge = sameNodeIds(child.knowledge, question.knowledge);
      return {
        key: child.id,
        existingId: child.id,
        type: isChildQuestionType(child.type) ? child.type : "SINGLE_CHOICE",
        content: child.content,
        solution: child.solution ?? "",
        points: formatPoints(child.points, locale),
        // Máy chủ trả phân loại đã kế thừa: giống câu cha thì hiện là "kế thừa".
        gradeCode: child.gradeCode === question.gradeCode ? "" : child.gradeCode,
        difficultyCode: child.difficultyCode === question.difficultyCode ? "" : child.difficultyCode,
        knowledgeOverride: !sameKnowledge,
        knowledge: sameKnowledge ? [] : normalizePrimary(child.knowledge),
        body: bodyToEditor(child.body, locale),
      };
    }),
  };
}

function trimmed(value: string): string {
  return value.trim();
}

const CLOZE_MARKER_RE = /\[\[[^\]]+\]\]/g;

/** Ngữ liệu Điền khuyết (`[[b1]]`) sang đề bài của trình soạn (`___`). */
export function clozeToEditorContent(passage: string): string {
  return passage.replace(CLOZE_MARKER_RE, BLANK_MARKER);
}

/** Đề bài của trình soạn (`___`) sang ngữ liệu Điền khuyết: chỗ trống thứ n thành `[[id của ô n]]`. */
export function clozePassage(content: string, blankIds: readonly string[]): string {
  let index = 0;
  return content.split(BLANK_MARKER).reduce((text, part, position) => {
    if (position === 0) return part;
    const id = blankIds[index++];
    return `${text}${id === undefined ? BLANK_MARKER : clozeMarker(id)}${part}`;
  }, "");
}

/**
 * Phần thân gửi lên theo dạng câu. `points` là điểm của câu: Đúng/Sai không có bảng điểm thì chia đều theo ý; Tự luận
 * lấy làm điểm tối đa.
 */
export function editorToBody(type: QuestionType, body: EditorBody, content: string, points: number = DEFAULT_POINTS): QuestionBody {
  switch (type) {
    case "SINGLE_CHOICE":
      return {
        type,
        options: body.options.map((option) => ({ id: option.id, content: trimmed(option.content), correct: option.correct })),
      };
    case "MULTIPLE_CHOICE":
      return {
        type,
        options: body.options.map((option) => ({ id: option.id, content: trimmed(option.content), correct: option.correct })),
        scoringRule: body.scoringRule,
      };
    case "TRUE_FALSE": {
      const perStatement = body.scoringMethod === "PER_STATEMENT";
      return {
        type,
        statements: body.statements.map((statement) => ({
          id: statement.id,
          content: trimmed(statement.content),
          answer: statement.answer === "TRUE",
          points: perStatement ? parsePoints(statement.points) : null,
        })),
        scoringMethod: body.scoringMethod,
        scoreTable: perStatement
          ? []
          : body.scoreTable.length === body.statements.length
            ? body.scoreTable.map(parsePoints)
            : evenScoreTable(body.statements.length, points),
      };
    }
    case "SHORT_ANSWER":
      return {
        type,
        answers: (body.blanks[0]?.answers ?? []).map((answer) => trimmed(answer.value)).filter(Boolean),
        inputHint: trimmed(body.inputHint) || null,
      };
    case "CLOZE":
      return {
        type,
        passage: clozePassage(content, body.blanks.map((blank) => blank.id)),
        blanks: body.blanks.map((blank) => ({
          id: blank.id,
          answers: blank.answers.map((answer) => trimmed(answer.value)).filter(Boolean),
          points: parsePoints(blank.points),
        })),
      };
    case "MATCHING":
      return {
        type,
        left: body.left.map((item) => ({ id: item.id, content: trimmed(item.content) })),
        right: body.right.map((item) => ({ id: item.id, content: trimmed(item.content) })),
        pairs: body.left
          .filter((item) => item.rightId)
          .map((item) => ({ leftId: item.id, rightId: item.rightId, points: parsePoints(item.points) })),
        reuseRight: body.reuseRight,
      };
    case "QUESTION_SET":
      return { type, passage: trimmed(content), blankNumberStart: parseBlankNumberStart(body.blankNumberStart), childIds: [] };
    case "ESSAY": {
      const wordLimit = Number.parseInt(body.wordLimit.trim(), 10);
      return {
        type,
        gradingGuide: trimmed(body.gradingGuide),
        rubric: body.rubric.map((criterion) => ({
          id: criterion.id,
          description: trimmed(criterion.description),
          points: parsePoints(criterion.points),
        })),
        sampleAnswer: trimmed(body.sampleAnswer) || null,
        wordLimit: Number.isInteger(wordLimit) && wordLimit > 0 ? wordLimit : null,
      };
    }
  }
}

/**
 * Chia `points` đều cho `count` phần (làm tròn 2 chữ số); phần cuối nhận số dư để tổng đúng bằng `points`
 * (ví dụ 1 chia 3 ra 0,33 / 0,33 / 0,34).
 */
export function evenSplit(count: number, points: number): number[] {
  if (count <= 0) return [];
  const share = Math.round((points * 100) / count) / 100;
  return Array.from({ length: count }, (_, index) => (index === count - 1 ? roundPoints(points - share * (count - 1)) : share));
}

/** Như `evenSplit`, viết theo ô Điểm ("0,5"). */
export function evenSplitText(count: number, points: number, locale: AppLocale = "vi"): string[] {
  return evenSplit(count, points).map((value) => formatPoints(value, locale));
}

/** Tổng các điểm hợp lệ trong danh sách ô Điểm. */
export function pointsTotal(values: readonly string[]): number {
  return roundPoints(
    values.reduce((sum, value) => {
      const points = parsePoints(value);
      return Number.isFinite(points) ? sum + points : sum;
    }, 0),
  );
}

function sameNodeIds(left: readonly { nodeId: string }[], right: readonly { nodeId: string }[]): boolean {
  const ids = new Set(right.map((ref) => ref.nodeId));
  return left.length === right.length && left.every((ref) => ids.has(ref.nodeId));
}

/** Bảng điểm Đúng/Sai chia đều: đúng `i + 1` ý được `(i + 1) / n` điểm của câu, làm tròn 2 chữ số. */
export function evenScoreTable(statementCount: number, points: number): number[] {
  return Array.from({ length: statementCount }, (_, index) => Math.round((points * (index + 1) * 100) / statementCount) / 100);
}

/** Như `evenScoreTable`, viết theo ô Điểm ("0,5"). */
export function evenScoreTableText(statementCount: number, points: number, locale: AppLocale = "vi"): string[] {
  return evenScoreTable(statementCount, points).map((value) => formatPoints(value, locale));
}

/** Làm tròn điểm tới 3 chữ số thập phân (0,125). */
export function roundPoints(value: number): number {
  return Math.round(value * 1000) / 1000;
}

/** Hai giá trị điểm coi là bằng nhau (bỏ qua sai số làm tròn của số thực). */
export function samePoints(left: number, right: number): boolean {
  return Math.abs(left - right) < 0.0005;
}

/**
 * Bảng điểm mẫu của kỳ thi (tính theo câu 1 điểm, ví dụ THPTQG 0,1 / 0,25 / 0,5 / 1) đổi theo điểm của câu: nhân tỉ lệ
 * `points / phần tử cuối`, phần tử cuối đúng bằng `points`.
 */
export function scaleScoreTable(table: readonly number[], points: number): number[] {
  const last = table.at(-1);
  if (!last || !Number.isFinite(points) || points <= 0) return [...table];
  return table.map((value, index) => (index === table.length - 1 ? points : roundPoints((value * points) / last)));
}

/** Tổng điểm các tiêu chí chấm có điểm hợp lệ. */
export function rubricTotal(rubric: readonly Pick<EditorCriterion, "points">[]): number {
  return roundPoints(
    rubric.reduce((sum, criterion) => {
      const points = parsePoints(criterion.points);
      return Number.isFinite(points) ? sum + points : sum;
    }, 0),
  );
}

function toChildRequest(child: EditorChild): SaveChildQuestionRequest {
  return {
    id: child.existingId,
    type: child.type,
    content: trimmed(child.content),
    body: editorToBody(child.type, child.body, child.content, parsePoints(child.points)),
    solution: trimmed(child.solution) || null,
    points: parsePoints(child.points),
    gradeCode: child.gradeCode || null,
    difficultyCode: child.difficultyCode || null,
    knowledgeIds: child.knowledgeOverride ? child.knowledge.map((ref) => ref.nodeId) : null,
  };
}

/** Giá trị đã qua kiểm tra → thân yêu cầu lưu. */
export function toSaveRequest(values: QuestionEditorValues): SaveQuestionRequest {
  const isSet = values.type === "QUESTION_SET";
  const children = isSet ? values.children.map(toChildRequest) : [];
  return {
    type: values.type,
    subjectCode: values.subjectCode,
    gradeCode: values.gradeCode,
    difficultyCode: values.difficultyCode,
    taskTypeCode: values.taskTypeCode || null,
    competencyCode: values.competencyCode || null,
    points: isSet
      ? children.reduce((sum, child) => sum + child.points, 0)
      : values.type === "CLOZE"
        ? pointsTotal(values.body.blanks.map((blank) => blank.points))
        : parsePoints(values.points),
    knowledge: normalizePrimary(values.knowledge),
    tagCodes: [...values.tagCodes],
    content:
      values.type === "CLOZE"
        ? clozePassage(trimmed(values.content), values.body.blanks.map((blank) => blank.id))
        : trimmed(values.content),
    solution: isSet ? null : trimmed(values.solution) || null,
    commonMistakes: trimmed(values.commonMistakes) || null,
    attachmentIds: [...values.attachmentIds],
    body: editorToBody(values.type, values.body, values.content, parsePoints(values.points)),
    children,
  };
}

/** Tổng điểm câu chùm (chỉ đọc): cộng điểm hợp lệ của câu con. */
export function questionSetPoints(children: readonly Pick<EditorChild, "points">[]): number {
  return children.reduce((sum, child) => {
    const points = parsePoints(child.points);
    return Number.isFinite(points) ? sum + points : sum;
  }, 0);
}

/** Bỏ chỗ trống thứ `index` (tính từ 0) khỏi đề bài; không có thì giữ nguyên. */
export function removeBlankMarker(content: string, index: number): string {
  let start = -1;
  let from = 0;
  for (let found = 0; found <= index; found += 1) {
    start = content.indexOf(BLANK_MARKER, from);
    if (start === -1) return content;
    from = start + BLANK_MARKER.length;
  }
  return content.slice(0, start) + content.slice(start + BLANK_MARKER.length);
}
