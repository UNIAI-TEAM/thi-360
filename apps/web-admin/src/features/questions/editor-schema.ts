import { z } from "zod";
import { MULTIPLE_CHOICE_RULES, QUESTION_TYPES } from "@/features/content/types";
import { countBlankMarkers, parsePoints, type EditorBody, type QuestionEditorValues } from "./editor-form";
import { CHILD_QUESTION_TYPES } from "./editor-types";

/**
 * Kiểm tra biểu mẫu soạn câu hỏi (react-hook-form + zod). Thông báo lỗi là **khóa dịch** dưới
 * `questions.editor.errors.*` (DESIGN.md 5.13: lưu mã, dịch lúc hiển thị).
 *
 * Quy tắc theo bản vẽ và UniNote PBI-03:
 * - Bắt buộc: môn, khối, mức độ, kiến thức (bản vẽ đánh dấu *), đề bài, đáp án theo dạng câu.
 * - Điểm lớn hơn 0 (mặc định 1). Câu chùm: điểm là tổng điểm câu con, chỉ đọc.
 * - Trắc nghiệm 1 đáp án: ít nhất 2 phương án, đúng một phương án đúng.
 * - Trắc nghiệm nhiều đáp án: ít nhất 2 phương án, ít nhất một phương án đúng.
 * - Đúng/Sai: ít nhất 2 ý, mỗi ý chọn Đúng hoặc Sai.
 * - Điền đáp án: ít nhất 1 ô, mỗi ô có ít nhất một đáp án chấp nhận, số chỗ trống `___` trong đề bài bằng số ô.
 * - Kéo thả: ít nhất 1 ô thả, mỗi ô có nhãn và thẻ đúng; thẻ nhiễu không rỗng.
 * - Câu chùm: đoạn dẫn chung, ít nhất 2 câu con, mỗi câu con hợp lệ theo dạng của nó.
 */

export const CONTENT_MAX = 10_000;
export const OPTION_MAX = 1_000;
export const SOLUTION_MAX = 10_000;
export const ANSWER_MAX = 200;
export const MIN_OPTIONS = 2;
export const MIN_STATEMENTS = 2;
export const MIN_CHILDREN = 2;

export type EditorErrorKey =
  | "subjectRequired"
  | "gradeRequired"
  | "difficultyRequired"
  | "knowledgeRequired"
  | "pointsInvalid"
  | "contentRequired"
  | "passageRequired"
  | "contentMax"
  | "solutionMax"
  | "optionsMin"
  | "optionRequired"
  | "optionMax"
  | "singleCorrectRequired"
  | "multipleCorrectRequired"
  | "statementsMin"
  | "statementRequired"
  | "statementAnswerRequired"
  | "blanksMin"
  | "blankAnswerRequired"
  | "answerMax"
  | "blankCountMismatch"
  | "zonesMin"
  | "zoneLabelRequired"
  | "zoneItemRequired"
  | "distractorRequired"
  | "childrenMin"
  | "childContentRequired";

export interface EditorIssue {
  path: (string | number)[];
  message: EditorErrorKey;
}

const blank = (value: string) => value.trim().length === 0;

function validatePoints(value: string, path: (string | number)[], issues: EditorIssue[]) {
  const points = parsePoints(value);
  if (!Number.isFinite(points) || points <= 0) issues.push({ path, message: "pointsInvalid" });
}

/** Kiểm tra phần thân theo dạng câu; `prefix` là đường dẫn tới `body` (câu đơn hoặc câu con). */
export function validateBody(
  type: QuestionEditorValues["type"],
  body: EditorBody,
  content: string,
  prefix: (string | number)[],
  issues: EditorIssue[],
): void {
  switch (type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE": {
      body.options.forEach((option, index) => {
        if (blank(option.content)) issues.push({ path: [...prefix, "options", index, "content"], message: "optionRequired" });
        else if (option.content.length > OPTION_MAX) {
          issues.push({ path: [...prefix, "options", index, "content"], message: "optionMax" });
        }
      });
      const correct = body.options.filter((option) => option.correct).length;
      if (body.options.length < MIN_OPTIONS) issues.push({ path: [...prefix, "options"], message: "optionsMin" });
      else if (type === "SINGLE_CHOICE" && correct !== 1) {
        issues.push({ path: [...prefix, "options"], message: "singleCorrectRequired" });
      } else if (type === "MULTIPLE_CHOICE" && correct < 1) {
        issues.push({ path: [...prefix, "options"], message: "multipleCorrectRequired" });
      }
      return;
    }
    case "TRUE_FALSE": {
      body.statements.forEach((statement, index) => {
        if (blank(statement.content)) {
          issues.push({ path: [...prefix, "statements", index, "content"], message: "statementRequired" });
        } else if (statement.content.length > OPTION_MAX) {
          issues.push({ path: [...prefix, "statements", index, "content"], message: "optionMax" });
        }
        if (statement.answer === "") {
          issues.push({ path: [...prefix, "statements", index, "answer"], message: "statementAnswerRequired" });
        }
      });
      if (body.statements.length < MIN_STATEMENTS) {
        issues.push({ path: [...prefix, "statements"], message: "statementsMin" });
      }
      return;
    }
    case "FILL_IN": {
      body.blanks.forEach((entry, index) => {
        if (entry.answers.every((answer) => blank(answer.value))) {
          issues.push({ path: [...prefix, "blanks", index, "answers"], message: "blankAnswerRequired" });
        }
        entry.answers.forEach((answer, answerIndex) => {
          if (answer.value.length > ANSWER_MAX) {
            issues.push({ path: [...prefix, "blanks", index, "answers", answerIndex, "value"], message: "answerMax" });
          }
        });
      });
      if (body.blanks.length < 1) issues.push({ path: [...prefix, "blanks"], message: "blanksMin" });
      else if (countBlankMarkers(content) !== body.blanks.length) {
        issues.push({ path: [...prefix, "blanks"], message: "blankCountMismatch" });
      }
      return;
    }
    case "DRAG_DROP": {
      body.zones.forEach((zone, index) => {
        if (blank(zone.label)) issues.push({ path: [...prefix, "zones", index, "label"], message: "zoneLabelRequired" });
        if (blank(zone.itemContent)) {
          issues.push({ path: [...prefix, "zones", index, "itemContent"], message: "zoneItemRequired" });
        }
      });
      body.distractors.forEach((item, index) => {
        if (blank(item.content)) {
          issues.push({ path: [...prefix, "distractors", index, "content"], message: "distractorRequired" });
        }
      });
      if (body.zones.length < 1) issues.push({ path: [...prefix, "zones"], message: "zonesMin" });
      return;
    }
    case "QUESTION_SET":
      return;
    case "ESSAY":
      // Kiểm tra tiêu chí chấm (tổng điểm bằng điểm của câu) do nhánh soạn Tự luận thêm (UNI-2603).
      return;
  }
}

/** Mọi lỗi của biểu mẫu, theo thứ tự trên màn hình. Rỗng khi hợp lệ. */
export function validateQuestionEditor(values: QuestionEditorValues): EditorIssue[] {
  const issues: EditorIssue[] = [];
  const isSet = values.type === "QUESTION_SET";
  if (blank(values.subjectCode)) issues.push({ path: ["subjectCode"], message: "subjectRequired" });
  if (blank(values.gradeCode)) issues.push({ path: ["gradeCode"], message: "gradeRequired" });
  if (blank(values.difficultyCode)) issues.push({ path: ["difficultyCode"], message: "difficultyRequired" });
  if (!isSet) validatePoints(values.points, ["points"], issues);
  if (values.knowledge.length === 0) issues.push({ path: ["knowledge"], message: "knowledgeRequired" });

  if (blank(values.content)) issues.push({ path: ["content"], message: isSet ? "passageRequired" : "contentRequired" });
  else if (values.content.length > CONTENT_MAX) issues.push({ path: ["content"], message: "contentMax" });

  validateBody(values.type, values.body, values.content, ["body"], issues);

  if (isSet) {
    values.children.forEach((child, index) => {
      const prefix = ["children", index];
      if (blank(child.content)) issues.push({ path: [...prefix, "content"], message: "childContentRequired" });
      else if (child.content.length > CONTENT_MAX) issues.push({ path: [...prefix, "content"], message: "contentMax" });
      validateBody(child.type, child.body, child.content, [...prefix, "body"], issues);
      validatePoints(child.points, [...prefix, "points"], issues);
      if (child.solution.length > SOLUTION_MAX) issues.push({ path: [...prefix, "solution"], message: "solutionMax" });
    });
    if (values.children.length < MIN_CHILDREN) issues.push({ path: ["children"], message: "childrenMin" });
  } else if (values.solution.length > SOLUTION_MAX) {
    issues.push({ path: ["solution"], message: "solutionMax" });
  }
  return issues;
}

const optionSchema = z.object({ id: z.string(), content: z.string(), correct: z.boolean() });
const bodySchema = z.object({
  options: z.array(optionSchema),
  shuffleOptions: z.boolean(),
  statements: z.array(z.object({ id: z.string(), content: z.string(), answer: z.enum(["", "TRUE", "FALSE"]) })),
  blanks: z.array(z.object({ id: z.string(), answers: z.array(z.object({ id: z.string(), value: z.string() })) })),
  zones: z.array(z.object({ id: z.string(), label: z.string(), itemId: z.string(), itemContent: z.string() })),
  distractors: z.array(z.object({ id: z.string(), content: z.string() })),
  scoringRule: z.enum(MULTIPLE_CHOICE_RULES),
  scoreTable: z.array(z.string()),
  rubric: z.array(z.object({ id: z.string(), description: z.string(), points: z.string() })),
  sampleAnswer: z.string(),
  wordLimit: z.string(),
});

/** Lược đồ của biểu mẫu: kiểu dữ liệu ở `z.object`, quy tắc nghiệp vụ trong `validateQuestionEditor`. */
export const questionEditorSchema = z
  .object({
    type: z.enum(QUESTION_TYPES),
    subjectCode: z.string(),
    gradeCode: z.string(),
    difficultyCode: z.string(),
    points: z.string(),
    knowledge: z.array(z.object({ nodeId: z.string(), code: z.string(), name: z.string(), primary: z.boolean() })),
    tagCodes: z.array(z.string()),
    content: z.string(),
    solution: z.string(),
    body: bodySchema,
    children: z.array(
      z.object({
        key: z.string(),
        existingId: z.string().nullable(),
        type: z.enum(CHILD_QUESTION_TYPES),
        content: z.string(),
        solution: z.string(),
        points: z.string(),
        body: bodySchema,
      }),
    ),
  })
  .superRefine((values, ctx) => {
    for (const issue of validateQuestionEditor(values)) {
      ctx.addIssue({ code: "custom", path: issue.path, message: issue.message });
    }
  }) satisfies z.ZodType<QuestionEditorValues>;
