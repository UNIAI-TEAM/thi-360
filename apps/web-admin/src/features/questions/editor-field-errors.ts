import type { FieldError } from "@thi360/api-client";
import type { UseFormSetError } from "react-hook-form";
import type { QuestionEditorValues } from "./editor-form";

/**
 * Lỗi 422 khi lưu câu hỏi (spec 4.3, 4.5): `fieldErrors[]` có `field` là đường dẫn trong yêu cầu
 * (`body.options[0].content`, `body.children[1].body.blanks[0].answers`, `content`) và `code` là mã lỗi. Đường dẫn đổi
 * sang tên trường của biểu mẫu rồi đặt lỗi lên đúng ô; chữ lấy từ `questions.editor.errors.server.<mã>`.
 */

/** Mã lỗi lưu có chữ riêng; mã khác dùng `UNKNOWN`. */
export const SERVER_FIELD_ERROR_CODES = [
  "QUESTION_CONTENT_UNSAFE",
  "QUESTION_BODY_INVALID",
  "QUESTION_BODY_TYPE_MISMATCH",
  "QUESTION_TYPE_MISSING",
  "QUESTION_DUPLICATE_ID",
  "QUESTION_OPTIONS_TOO_MANY",
  "QUESTION_STATEMENTS_TOO_MANY",
  "QUESTION_BLANKS_TOO_MANY",
  "QUESTION_ANSWERS_TOO_MANY",
  "QUESTION_CHILDREN_TOO_MANY",
  "QUESTION_CHILD_TYPE_NOT_ALLOWED",
  "QUESTION_BLANK_MARKER_MISMATCH",
  "QUESTION_PAIR_UNKNOWN_LEFT",
  "QUESTION_PAIR_UNKNOWN_RIGHT",
  "QUESTION_PAIR_DUPLICATE_LEFT",
  "QUESTION_ATTACHMENT_UNKNOWN",
  "QUESTION_WORD_LIMIT_INVALID",
  "QUESTION_BLANK_NUMBER_START_INVALID",
  "QUESTION_SCORING_METHOD_MISSING",
  "QUESTION_SCORING_RULE_MISSING",
  "POINTS_NOT_POSITIVE",
  "POINTS_TOO_MANY_DECIMALS",
  "POINTS_TOO_LARGE",
  "SCORE_TABLE_INVALID",
  "MATH_UNBALANCED",
  "CATALOG_VALUE_UNKNOWN",
] as const;

const KNOWN = new Set<string>(SERVER_FIELD_ERROR_CODES);

/** `body.children[1].body.options[0].content` -> `children.1.body.options.0.content`. */
export function toFormPath(path: string): string {
  const dotted = path.replace(/\[(\d+)\]/g, ".$1");
  const child = /^body\.children\.(\d+)(.*)$/.exec(dotted);
  const local = child ? `children.${child[1]}${child[2]}` : dotted;
  // Lỗi sai thường gặp và tệp đính kèm là một ô, không phải mảng, ở biểu mẫu.
  return local.replace(/^(commonMistakes|attachmentIds)\.\d+$/, "$1").replace(/^body\.type$/, "body");
}

/** Khóa chữ của lỗi trong `questions.editor.errors`. */
export function serverErrorKey(code: string): string {
  return `server.${KNOWN.has(code) ? code : "UNKNOWN"}`;
}

/**
 * Đường dẫn của biểu mẫu mà màn hình thật sự vẽ lỗi (ô nhập hoặc dòng lỗi nhóm). Ở biểu mẫu lỗi có thể nằm trên
 * `children.N` của câu chùm (tiền tố `child`); đường dẫn ngoài danh sách này không có chỗ hiện lỗi.
 */
const RENDERED_FORM_PATHS: readonly RegExp[] = [
  /^(content|solution|commonMistakes|points|subjectCode|gradeCode|difficultyCode|knowledge|children)$/,
  /^children\.\d+\.(content|solution|points|knowledge)$/,
  /^(children\.\d+\.)?body\.(options|statements|blanks|left|rubric|sampleAnswer|gradingGuide|wordLimit)$/,
  /^body\.blankNumberStart$/,
  /^(children\.\d+\.)?body\.options\.\d+\.content$/,
  /^(children\.\d+\.)?body\.statements\.\d+\.(content|answer|points)$/,
  /^(children\.\d+\.)?body\.scoreTable\.\d+$/,
  /^(children\.\d+\.)?body\.blanks\.\d+\.(points|answers)$/,
  /^(children\.\d+\.)?body\.blanks\.\d+\.answers\.\d+\.value$/,
  /^(children\.\d+\.)?body\.left\.\d+\.(content|rightId|points)$/,
  /^(children\.\d+\.)?body\.rubric\.\d+\.(description|points)$/,
];

/** Phần của `body` mà biểu mẫu vẽ theo từng dạng câu. */
const BODY_SECTIONS: Record<string, readonly string[]> = {
  SINGLE_CHOICE: ["options"],
  MULTIPLE_CHOICE: ["options"],
  TRUE_FALSE: ["statements", "scoreTable"],
  SHORT_ANSWER: ["blanks"],
  CLOZE: ["blanks"],
  MATCHING: ["left"],
  ESSAY: ["rubric", "sampleAnswer", "gradingGuide", "wordLimit"],
  QUESTION_SET: ["blankNumberStart"],
};

/**
 * Đường dẫn của máy chủ khác cấu trúc biểu mẫu ở vài chỗ: cặp ghép (`body.pairs[n]` chỉ gồm các mục trái đã chọn
 * đáp án), đáp án Trả lời ngắn (`body.answers`, bỏ ô trống), đoạn dẫn của Điền đáp án (`body.passage`, ở biểu mẫu là
 * ô đề bài), kiến thức và các dòng của nhóm không có ô riêng.
 */
function toRenderedCandidate(path: string, scope: string, getValue: (path: string) => unknown): string {
  const rest = path.slice(scope.length);
  const pair = /^body\.pairs(?:\.(\d+)(?:\.(leftId|rightId|points))?)?$/.exec(rest);
  if (pair) {
    const lefts = getValue(`${scope}body.left`);
    const chosen = Array.isArray(lefts)
      ? lefts.flatMap((item: { rightId?: unknown }, index) => (item?.rightId ? [index] : []))
      : [];
    const index = pair[1] === undefined ? undefined : chosen[Number(pair[1])];
    if (index === undefined) return `${scope}body.left`;
    return `${scope}body.left.${index}.${pair[2] === "points" ? "points" : "rightId"}`;
  }
  if (/^body\.answers(\.\d+)?$/.test(rest)) return `${scope}body.blanks.0.answers`;
  if (rest === "body.passage") return `${scope}content`;
  if (/^knowledge\.\d+\./.test(rest)) return `${scope}knowledge`;
  const group = /^(body\.(?:options|statements|blanks|left|rubric))\.\d+\.(?:id|correct)$/.exec(rest);
  if (group) return `${scope}${group[1]}`;
  const answer = /^(body\.blanks\.\d+\.answers)\.\d+$/.exec(rest);
  if (answer) return `${scope}${answer[1]}`;
  return path;
}

/**
 * Đổi đường dẫn lỗi của máy chủ về đường dẫn của biểu mẫu; `null` khi biểu mẫu (với dạng câu đang mở) không có ô hay
 * dòng lỗi nào để hiện lỗi đó.
 */
export function resolveFormPath(field: string, getValue: (path: string) => unknown): string | null {
  const formPath = toFormPath(field);
  const scope = /^children\.\d+\./.exec(formPath)?.[0] ?? "";
  const path = toRenderedCandidate(formPath, scope, getValue);
  if (!RENDERED_FORM_PATHS.some((pattern) => pattern.test(path))) return null;
  const section = /^(?:children\.\d+\.)?body\.([A-Za-z]+)/.exec(path)?.[1];
  if (section === undefined) return path;
  const type = getValue(`${scope}type`);
  const sections = typeof type === "string" ? BODY_SECTIONS[type] : undefined;
  return sections?.includes(section) ? path : null;
}

export interface AppliedServerErrors {
  /** Số lỗi đã đặt lên ô hoặc dòng lỗi mà biểu mẫu vẽ ra. */
  applied: number;
  /** Lỗi không có chỗ hiện trên biểu mẫu: người dùng chỉ thấy được qua dải thông báo, theo mã. */
  unmatched: FieldError[];
}

/** Đặt lỗi của máy chủ lên các ô của biểu mẫu; lỗi không có ô tương ứng trả lại trong `unmatched`. */
export function applyServerFieldErrors(
  fieldErrors: readonly FieldError[],
  setError: UseFormSetError<QuestionEditorValues>,
  getValue: (path: string) => unknown,
): AppliedServerErrors {
  const result: AppliedServerErrors = { applied: 0, unmatched: [] };
  for (const error of fieldErrors) {
    const path = error.field ? resolveFormPath(error.field, getValue) : null;
    if (path === null) {
      result.unmatched.push(error);
      continue;
    }
    setError(path as never, { type: "server", message: serverErrorKey(error.code) });
    result.applied += 1;
  }
  return result;
}
