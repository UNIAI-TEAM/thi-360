import type { KnowledgeRef, QuestionType } from "@/features/content/types";
import { normalizePrimary } from "@/features/content/knowledge-tree";
import type { ChildQuestionType, SaveChildQuestionRequest, SaveQuestionRequest } from "./editor-types";
import type { QuestionBody, QuestionDetail } from "./types";

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

export interface EditorBody {
  options: EditorOption[];
  shuffleOptions: boolean;
  statements: EditorStatement[];
  blanks: EditorBlank[];
  zones: EditorZone[];
  distractors: EditorDistractor[];
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
  /** Chuỗi để giữ đúng điều người dùng gõ ("1,5"); đổi sang số khi lưu. */
  points: string;
  knowledge: KnowledgeRef[];
  tagCodes: string[];
  /** Đề bài; câu chùm: đoạn dẫn chung. */
  content: string;
  solution: string;
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

/** Phần thân trống theo dạng: 4 phương án, 2 ý Đúng/Sai, 2 ô thả; Điền đáp án thêm ô cùng lúc với chỗ trống. */
export function emptyBody(type: QuestionType): EditorBody {
  return {
    options:
      type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE"
        ? [emptyOption(), emptyOption(), emptyOption(), emptyOption()]
        : [],
    shuffleOptions: true,
    statements: type === "TRUE_FALSE" ? [emptyStatement(), emptyStatement()] : [],
    blanks: [],
    zones: type === "DRAG_DROP" ? [emptyZone(), emptyZone()] : [],
    distractors: [],
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
    points: String(DEFAULT_POINTS),
    knowledge: [],
    tagCodes: [],
    content: "",
    solution: "",
    body: emptyBody(type),
    children: [],
  };
}

function formatPoints(points: number): string {
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
    case "MULTIPLE_CHOICE":
      return {
        ...base,
        options: body.options.map((option) => ({ ...option })),
        shuffleOptions: body.shuffleOptions,
      };
    case "TRUE_FALSE":
      return {
        ...base,
        statements: body.statements.map((statement) => ({
          id: statement.id,
          content: statement.content,
          answer: statement.answer ? "TRUE" : "FALSE",
        })),
      };
    case "FILL_IN":
      return {
        ...base,
        blanks: body.blanks.map((blank) => ({
          id: blank.id,
          answers: blank.acceptedAnswers.map((value) => ({ id: draftRowId("a"), value })),
        })),
      };
    case "DRAG_DROP": {
      const contentOf = new Map(body.items.map((item) => [item.id, item.content]));
      const used = new Set(body.zones.map((zone) => zone.correctItemId));
      return {
        ...base,
        zones: body.zones.map((zone) => ({
          id: zone.id,
          label: zone.label,
          itemId: zone.correctItemId,
          itemContent: contentOf.get(zone.correctItemId) ?? "",
        })),
        distractors: body.items.filter((item) => !used.has(item.id)).map((item) => ({ ...item })),
      };
    }
    case "QUESTION_SET":
      return base;
  }
}

export function toEditorValues(question: QuestionDetail): QuestionEditorValues {
  return {
    type: question.type,
    subjectCode: question.subjectCode,
    gradeCode: question.gradeCode,
    difficultyCode: question.difficultyCode,
    points: formatPoints(question.points),
    knowledge: normalizePrimary(question.knowledge),
    tagCodes: [...question.tagCodes],
    content: question.content,
    solution: question.solution ?? "",
    body: bodyToEditor(question.body),
    children: question.children.map((child) => ({
      key: child.id,
      existingId: child.id,
      type: child.type === "QUESTION_SET" ? "SINGLE_CHOICE" : child.type,
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

export function editorToBody(type: QuestionType, body: EditorBody, content: string): QuestionBody {
  switch (type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return {
        type,
        options: body.options.map((option) => ({ id: option.id, content: trimmed(option.content), correct: option.correct })),
        shuffleOptions: body.shuffleOptions,
      };
    case "TRUE_FALSE":
      return {
        type,
        statements: body.statements.map((statement) => ({
          id: statement.id,
          content: trimmed(statement.content),
          answer: statement.answer === "TRUE",
        })),
      };
    case "FILL_IN":
      return {
        type,
        blanks: body.blanks.map((blank) => ({
          id: blank.id,
          acceptedAnswers: blank.answers.map((answer) => trimmed(answer.value)).filter(Boolean),
        })),
      };
    case "DRAG_DROP":
      return {
        type,
        items: [
          ...body.zones.map((zone) => ({ id: zone.itemId, content: trimmed(zone.itemContent) })),
          ...body.distractors.map((item) => ({ id: item.id, content: trimmed(item.content) })),
        ],
        zones: body.zones.map((zone) => ({ id: zone.id, label: trimmed(zone.label), correctItemId: zone.itemId })),
      };
    case "QUESTION_SET":
      return { type, passage: trimmed(content), childIds: [] };
  }
}

function toChildRequest(child: EditorChild): SaveChildQuestionRequest {
  return {
    id: child.existingId,
    type: child.type,
    content: trimmed(child.content),
    body: editorToBody(child.type, child.body, child.content),
    solution: trimmed(child.solution) || null,
    points: parsePoints(child.points),
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
    points: isSet ? children.reduce((sum, child) => sum + child.points, 0) : parsePoints(values.points),
    knowledge: normalizePrimary(values.knowledge),
    tagCodes: [...values.tagCodes],
    content: trimmed(values.content),
    solution: isSet ? null : trimmed(values.solution) || null,
    body: editorToBody(values.type, values.body, values.content),
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
