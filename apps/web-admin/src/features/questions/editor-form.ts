import type { KnowledgeRef, MultipleChoiceRule, QuestionType } from "@/features/content/types";
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
}

export interface EditorBlank {
  id: string;
  /** Đối tượng thay cho chuỗi để `useFieldArray` giữ được khóa của từng dòng. */
  answers: { id: string; value: string }[];
}

/** Ô thả và thẻ đúng của nó. */
export interface EditorZone {
  id: string;
  label: string;
  itemId: string;
  itemContent: string;
}

export interface EditorDistractor {
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
  shuffleOptions: boolean;
  /** Nhiều đáp án: đúng hết mới có điểm hay chấm từng phần. */
  scoringRule: MultipleChoiceRule;
  statements: EditorStatement[];
  /** Đúng/Sai: điểm khi đúng 1, 2, … ý (chuỗi như ô Điểm); rỗng: chia đều theo ý. */
  scoreTable: string[];
  blanks: EditorBlank[];
  zones: EditorZone[];
  distractors: EditorDistractor[];
  /** Tự luận: hướng dẫn chấm (bắt buộc khi Sẵn sàng), tiêu chí chấm (tùy chọn), bài làm mẫu, số chữ gợi ý (rỗng: không giới hạn). */
  gradingGuide: string;
  rubric: EditorCriterion[];
  sampleAnswer: string;
  wordLimit: string;
  /** Trả lời ngắn: hướng dẫn nhập cho học viên. */
  inputHint: string;
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
  /** Lỗi sai thường gặp (trình soạn trường này làm ở FE-3b; giá trị được giữ nguyên khi lưu). */
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

/** Điểm mặc định của câu đơn và câu con (UniNote PBI-03 mục 4.1). */
export const DEFAULT_POINTS = 1;

let rowSequence = 0;
/** Id tạm cho dòng mới trong biểu mẫu; máy chủ giữ hoặc thay khi lưu. */
export function draftRowId(prefix: string): string {
  rowSequence += 1;
  return `new-${prefix}-${rowSequence}`;
}

export function emptyOption(): EditorOption {
  return { id: draftRowId("o"), content: "", correct: false };
}

export function emptyStatement(): EditorStatement {
  return { id: draftRowId("s"), content: "", answer: "" };
}

export function emptyBlank(): EditorBlank {
  return { id: draftRowId("b"), answers: [{ id: draftRowId("a"), value: "" }] };
}

export function emptyZone(): EditorZone {
  return { id: draftRowId("z"), label: "", itemId: draftRowId("i"), itemContent: "" };
}

export function emptyDistractor(): EditorDistractor {
  return { id: draftRowId("i"), content: "" };
}

export function emptyCriterion(points = ""): EditorCriterion {
  return { id: draftRowId("r"), description: "", points };
}

/**
 * Phần thân trống theo dạng: 4 phương án, 2 ý Đúng/Sai kèm bảng điểm chia đều, 2 ô thả, 1 tiêu chí chấm mang đủ điểm
 * mặc định; Điền đáp án thêm ô cùng lúc với chỗ trống.
 */
export function emptyBody(type: QuestionType): EditorBody {
  return {
    options:
      type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE"
        ? [emptyOption(), emptyOption(), emptyOption(), emptyOption()]
        : [],
    shuffleOptions: true,
    scoringRule: "ALL_OR_NOTHING",
    statements: type === "TRUE_FALSE" ? [emptyStatement(), emptyStatement()] : [],
    scoreTable: type === "TRUE_FALSE" ? evenScoreTableText(2, DEFAULT_POINTS) : [],
    blanks: type === "SHORT_ANSWER" ? [emptyBlank()] : [],
    zones: type === "MATCHING" ? [emptyZone(), emptyZone()] : [],
    distractors: [],
    gradingGuide: "",
    rubric: [],
    sampleAnswer: "",
    wordLimit: "",
    inputHint: "",
  };
}

export function emptyChild(type: ChildQuestionType): EditorChild {
  return {
    key: draftRowId("c"),
    existingId: null,
    type,
    content: "",
    solution: "",
    points: String(DEFAULT_POINTS),
    body: emptyBody(type),
  };
}

export function emptyEditorValues(type: QuestionType): QuestionEditorValues {
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
    body: emptyBody(type),
    children: [],
  };
}

/** 1.5 → "1,5" (cách viết của ô Điểm). */
export function formatPoints(points: number): string {
  return String(points).replace(".", ",");
}

/** "1,5" hoặc "1.5" → 1.5; không phải số thì `NaN`. */
export function parsePoints(value: string): number {
  const normalized = value.trim().replace(",", ".");
  if (!/^\d+(\.\d+)?$/.test(normalized)) return Number.NaN;
  return Number(normalized);
}

export function bodyToEditor(body: QuestionBody): EditorBody {
  const base = emptyBody("QUESTION_SET");
  switch (body.type) {
    case "SINGLE_CHOICE":
      return {
        ...base,
        options: body.options.map((option) => ({ ...option })),
        shuffleOptions: body.shuffleOptions,
      };
    case "MULTIPLE_CHOICE":
      return {
        ...base,
        options: body.options.map((option) => ({ ...option })),
        shuffleOptions: body.shuffleOptions,
        scoringRule: body.scoringRule,
      };
    case "TRUE_FALSE":
      return {
        ...base,
        statements: body.statements.map((statement) => ({
          id: statement.id,
          content: statement.content,
          answer: statement.answer ? "TRUE" : "FALSE",
        })),
        // `PER_STATEMENT` (cộng điểm từng ý) chưa có trình soạn riêng (FE-3b): hiện như bảng điểm theo số ý đúng.
        scoreTable: body.scoreTable.map(formatPoints),
      };
    case "SHORT_ANSWER":
      return {
        ...base,
        blanks: [{ id: draftRowId("b"), answers: body.answers.map((value) => ({ id: draftRowId("a"), value })) }],
        inputHint: body.inputHint ?? "",
      };
    case "CLOZE":
      return {
        ...base,
        blanks: body.blanks.map((blank) => ({
          id: blank.id,
          answers: blank.answers.map((value) => ({ id: draftRowId("a"), value })),
        })),
      };
    case "MATCHING": {
      const rightOf = new Map(body.right.map((item) => [item.id, item]));
      const pairOf = new Map(body.pairs.map((pair) => [pair.leftId, pair.rightId]));
      const used = new Set(body.pairs.map((pair) => pair.rightId));
      return {
        ...base,
        zones: body.left.map((item) => {
          const right = rightOf.get(pairOf.get(item.id) ?? "");
          return { id: item.id, label: item.content, itemId: right?.id ?? draftRowId("i"), itemContent: right?.content ?? "" };
        }),
        distractors: body.right.filter((item) => !used.has(item.id)).map((item) => ({ ...item })),
      };
    }
    case "QUESTION_SET":
      return base;
    case "ESSAY":
      return {
        ...base,
        gradingGuide: body.gradingGuide,
        rubric: body.rubric.map((criterion) => ({
          id: criterion.id,
          description: criterion.description,
          points: formatPoints(criterion.points),
        })),
        sampleAnswer: body.sampleAnswer ?? "",
        wordLimit: body.wordLimit === null ? "" : String(body.wordLimit),
      };
  }
}

export function toEditorValues(question: QuestionDetail): QuestionEditorValues {
  return {
    type: question.type,
    subjectCode: question.subjectCode,
    gradeCode: question.gradeCode,
    difficultyCode: question.difficultyCode,
    competencyCode: question.competencyCode ?? "",
    taskTypeCode: question.taskTypeCode ?? "",
    points: formatPoints(question.points),
    knowledge: normalizePrimary(question.knowledge),
    tagCodes: [...question.tagCodes],
    content: question.body.type === "CLOZE" ? clozeToEditorContent(question.body.passage) : question.content,
    solution: question.solution ?? "",
    commonMistakes: question.commonMistakes ?? "",
    attachmentIds: [...question.attachmentIds],
    body: bodyToEditor(question.body),
    children: question.children.map((child) => ({
      key: child.id,
      existingId: child.id,
      type: isChildQuestionType(child.type) ? child.type : "SINGLE_CHOICE",
      content: child.content,
      solution: child.solution ?? "",
      points: formatPoints(child.points),
      body: bodyToEditor(child.body),
    })),
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
        shuffleOptions: body.shuffleOptions,
      };
    case "MULTIPLE_CHOICE":
      return {
        type,
        options: body.options.map((option) => ({ id: option.id, content: trimmed(option.content), correct: option.correct })),
        shuffleOptions: body.shuffleOptions,
        scoringRule: body.scoringRule,
      };
    case "TRUE_FALSE":
      return {
        type,
        statements: body.statements.map((statement) => ({
          id: statement.id,
          content: trimmed(statement.content),
          answer: statement.answer === "TRUE",
          points: null,
        })),
        scoringMethod: "COUNT_TABLE",
        scoreTable:
          body.scoreTable.length === body.statements.length
            ? body.scoreTable.map(parsePoints)
            : evenScoreTable(body.statements.length, points),
      };
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
          points: DEFAULT_POINTS,
        })),
      };
    case "MATCHING": {
      const pairPoints = body.zones.length ? Math.round((points * 10_000) / body.zones.length) / 10_000 : 0;
      return {
        type,
        left: body.zones.map((zone) => ({ id: zone.id, content: trimmed(zone.label) })),
        right: [
          ...body.zones.map((zone) => ({ id: zone.itemId, content: trimmed(zone.itemContent) })),
          ...body.distractors.map((item) => ({ id: item.id, content: trimmed(item.content) })),
        ],
        pairs: body.zones.map((zone) => ({ leftId: zone.id, rightId: zone.itemId, points: pairPoints })),
        reuseRight: false,
        shuffleLeft: false,
        shuffleRight: true,
      };
    }
    case "QUESTION_SET":
      return { type, passage: trimmed(content), childIds: [] };
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

/** Bảng điểm Đúng/Sai chia đều: đúng `i + 1` ý được `(i + 1) / n` điểm của câu, làm tròn 2 chữ số. */
export function evenScoreTable(statementCount: number, points: number): number[] {
  return Array.from({ length: statementCount }, (_, index) => Math.round((points * (index + 1) * 100) / statementCount) / 100);
}

/** Như `evenScoreTable`, viết theo ô Điểm ("0,5"). */
export function evenScoreTableText(statementCount: number, points: number): string[] {
  return evenScoreTable(statementCount, points).map(formatPoints);
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
    // Phân loại riêng của câu con (khối lớp, độ khó, kiến thức) có trình soạn ở FE-3b; tới đó kế thừa của bộ.
    gradeCode: null,
    difficultyCode: null,
    knowledgeIds: null,
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
        ? values.body.blanks.length * DEFAULT_POINTS
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

/**
 * Chèn chỗ trống `___` vào đề bài tại vị trí con trỏ (không cắt đôi một chỗ trống có sẵn). Trả về đề bài mới, thứ tự
 * của chỗ trống vừa chèn (để thêm dòng đáp án đúng vị trí) và vị trí con trỏ sau chỗ trống.
 */
export function insertBlankMarker(
  content: string,
  position: number,
): { content: string; index: number; caret: number } {
  let at = Math.min(Math.max(position, 0), content.length);
  // Con trỏ đang nằm giữa một `___` thì dời ra sau nó.
  for (let start = content.indexOf(BLANK_MARKER); start !== -1; start = content.indexOf(BLANK_MARKER, start + BLANK_MARKER.length)) {
    if (at > start && at < start + BLANK_MARKER.length) at = start + BLANK_MARKER.length;
  }
  const before = content.slice(0, at);
  const after = content.slice(at);
  const spaceBefore = before.length > 0 && !/\s$/.test(before) ? " " : "";
  const spaceAfter = after.length > 0 && !/^\s/.test(after) ? " " : "";
  const next = `${before}${spaceBefore}${BLANK_MARKER}${spaceAfter}${after}`;
  return {
    content: next,
    index: countBlankMarkers(before),
    caret: before.length + spaceBefore.length + BLANK_MARKER.length,
  };
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
