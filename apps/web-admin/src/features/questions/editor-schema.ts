import { z } from "zod";
import { MULTIPLE_CHOICE_RULES, QUESTION_TYPES, TRUE_FALSE_METHODS } from "@/features/content/types";
import { questionHtmlText } from "@/components/rich-text/sanitize";
import {
  countBlankMarkers,
  isBlankNumberStart,
  parsePoints,
  pointsTotal,
  rubricTotal,
  samePoints,
  type EditorBody,
  type QuestionEditorValues,
} from "./editor-form";
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
 * - Đúng/Sai: ít nhất 2 ý, mỗi ý chọn Đúng hoặc Sai. Bảng điểm theo số ý đúng: mỗi ô là số không âm, không giảm dần,
 *   ô cuối bằng điểm của câu (UNI-2599).
 * - Trả lời ngắn: ít nhất một đáp án. Điền khuyết: ít nhất 1 ô, mỗi ô có ít nhất một đáp án, số chỗ trống `___` trong đề
 *   bài bằng số ô. Ghép đôi: ít nhất 1 mục trái, mỗi mục có nhãn và lựa chọn đúng; lựa chọn thừa không rỗng.
 * - Câu chùm: đoạn dẫn chung, ít nhất 2 câu con, mỗi câu con hợp lệ theo dạng của nó.
 * - Tự luận: hướng dẫn chấm bắt buộc; tiêu chí chấm tùy chọn, có thì mỗi tiêu chí có mô tả, điểm lớn hơn 0 và tổng bằng
 *   điểm của câu (D20); số chữ gợi ý (nếu có) là số nguyên dương.
 */

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
  | "optionsMin"
  | "optionRequired"
  | "singleCorrectRequired"
  | "multipleCorrectRequired"
  | "statementsMin"
  | "statementRequired"
  | "statementAnswerRequired"
  | "blanksMin"
  | "blankAnswerRequired"
  | "answerMax"
  | "blankCountMismatch"
  | "matchLeftMin"
  | "matchLeftRequired"
  | "matchRightRequired"
  | "matchAnswerRequired"
  | "matchRightReused"
  | "pairPointsInvalid"
  | "pairPointsMismatch"
  | "blankPointsInvalid"
  | "statementPointsInvalid"
  | "statementPointsMismatch"
  | "childKnowledgeRequired"
  | "childrenMin"
  | "childContentRequired"
  | "blankNumberStartInvalid"
  | "scoreValueInvalid"
  | "scoreNotIncreasing"
  | "scoreLastMismatch"
  | "gradingGuideRequired"
  | "criterionRequired"
  | "criterionPointsInvalid"
  | "rubricTotalMismatch"
  | "wordLimitInvalid";

export interface EditorIssue {
  path: (string | number)[];
  message: EditorErrorKey;
}

const blank = (value: string) => value.trim().length === 0;

/** Ô HTML trống: không còn chữ nào và không có ảnh — mirror `isBlankHtml` của backend (`<p></p>` cũng là trống). */
const blankHtml = (value: string) => questionHtmlText(value).trim() === "" && !/<img\b/i.test(value);

function validatePoints(value: string, path: (string | number)[], issues: EditorIssue[]) {
  const points = parsePoints(value);
  if (!Number.isFinite(points) || points <= 0) issues.push({ path, message: "pointsInvalid" });
}

/**
 * Kiểm tra phần thân theo dạng câu; `prefix` là đường dẫn tới `body` (câu đơn hoặc câu con). `points` là ô Điểm của
 * câu (chuỗi), dùng cho bảng điểm Đúng/Sai và tổng điểm tiêu chí Tự luận; không phải số thì bỏ qua hai phép so đó.
 */
export function validateBody(
  type: QuestionEditorValues["type"],
  body: EditorBody,
  content: string,
  prefix: (string | number)[],
  issues: EditorIssue[],
  points = "",
): void {
  const questionPoints = parsePoints(points);
  const pointsValid = Number.isFinite(questionPoints) && questionPoints > 0;
  switch (type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE": {
      body.options.forEach((option, index) => {
        if (blankHtml(option.content)) issues.push({ path: [...prefix, "options", index, "content"], message: "optionRequired" });
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
        if (blankHtml(statement.content)) {
          issues.push({ path: [...prefix, "statements", index, "content"], message: "statementRequired" });
        }
        if (statement.answer === "") {
          issues.push({ path: [...prefix, "statements", index, "answer"], message: "statementAnswerRequired" });
        }
        if (body.scoringMethod === "PER_STATEMENT") {
          const value = parsePoints(statement.points);
          if (!Number.isFinite(value) || value <= 0) {
            issues.push({ path: [...prefix, "statements", index, "points"], message: "statementPointsInvalid" });
          }
        }
      });
      if (body.statements.length < MIN_STATEMENTS) {
        issues.push({ path: [...prefix, "statements"], message: "statementsMin" });
      }
      if (body.scoringMethod === "COUNT_TABLE") {
        validateScoreTable(body.scoreTable, pointsValid ? questionPoints : null, [...prefix, "scoreTable"], issues);
      } else if (
        pointsValid &&
        body.statements.every((statement) => parsePoints(statement.points) > 0) &&
        !samePoints(pointsTotal(body.statements.map((statement) => statement.points)), questionPoints)
      ) {
        issues.push({ path: [...prefix, "statements"], message: "statementPointsMismatch" });
      }
      return;
    }
    case "SHORT_ANSWER": {
      const answers = body.blanks[0]?.answers ?? [];
      if (answers.every((answer) => blank(answer.value))) {
        issues.push({ path: [...prefix, "blanks", 0, "answers"], message: "blankAnswerRequired" });
      }
      answers.forEach((answer, answerIndex) => {
        if (answer.value.length > ANSWER_MAX) {
          issues.push({ path: [...prefix, "blanks", 0, "answers", answerIndex, "value"], message: "answerMax" });
        }
      });
      return;
    }
    case "CLOZE": {
      body.blanks.forEach((entry, index) => {
        if (entry.answers.every((answer) => blank(answer.value))) {
          issues.push({ path: [...prefix, "blanks", index, "answers"], message: "blankAnswerRequired" });
        }
        const blankPoints = parsePoints(entry.points);
        if (!Number.isFinite(blankPoints) || blankPoints <= 0) {
          issues.push({ path: [...prefix, "blanks", index, "points"], message: "blankPointsInvalid" });
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
    case "MATCHING": {
      const rightIds = new Set(body.right.map((item) => item.id));
      const used = new Set<string>();
      body.left.forEach((item, index) => {
        const at = [...prefix, "left", index];
        if (blankHtml(item.content)) issues.push({ path: [...at, "content"], message: "matchLeftRequired" });
        if (!item.rightId || !rightIds.has(item.rightId)) {
          issues.push({ path: [...at, "rightId"], message: "matchAnswerRequired" });
        } else {
          if (!body.reuseRight && used.has(item.rightId)) issues.push({ path: [...at, "rightId"], message: "matchRightReused" });
          used.add(item.rightId);
        }
        const value = parsePoints(item.points);
        if (!Number.isFinite(value) || value <= 0) issues.push({ path: [...at, "points"], message: "pairPointsInvalid" });
      });
      body.right.forEach((item, index) => {
        const at = [...prefix, "right", index, "content"];
        if (blankHtml(item.content)) issues.push({ path: at, message: "matchRightRequired" });
      });
      if (body.left.length < 1) issues.push({ path: [...prefix, "left"], message: "matchLeftMin" });
      else if (
        pointsValid &&
        body.left.every((item) => parsePoints(item.points) > 0) &&
        !samePoints(pointsTotal(body.left.map((item) => item.points)), questionPoints)
      ) {
        issues.push({ path: [...prefix, "left"], message: "pairPointsMismatch" });
      }
      return;
    }
    case "QUESTION_SET":
      if (!isBlankNumberStart(body.blankNumberStart)) {
        issues.push({ path: [...prefix, "blankNumberStart"], message: "blankNumberStartInvalid" });
      }
      return;
    case "ESSAY": {
      if (blankHtml(body.gradingGuide)) issues.push({ path: [...prefix, "gradingGuide"], message: "gradingGuideRequired" });
      body.rubric.forEach((criterion, index) => {
        const at = [...prefix, "rubric", index];
        if (blankHtml(criterion.description)) issues.push({ path: [...at, "description"], message: "criterionRequired" });
        const value = parsePoints(criterion.points);
        if (!Number.isFinite(value) || value <= 0) issues.push({ path: [...at, "points"], message: "criterionPointsInvalid" });
      });
      if (
        body.rubric.length > 0 &&
        pointsValid &&
        body.rubric.every((criterion) => parsePoints(criterion.points) > 0) &&
        !samePoints(rubricTotal(body.rubric), questionPoints)
      ) {
        issues.push({ path: [...prefix, "rubric"], message: "rubricTotalMismatch" });
      }
      const wordLimit = body.wordLimit.trim();
      if (wordLimit && (!/^\d+$/.test(wordLimit) || Number(wordLimit) <= 0)) {
        issues.push({ path: [...prefix, "wordLimit"], message: "wordLimitInvalid" });
      }
      return;
    }
  }
}

/**
 * Bảng điểm Đúng/Sai: lỗi gắn vào ô gây lỗi (ô không phải số; ô nhỏ hơn ô trước; ô cuối khác điểm của câu) để người
 * dùng tới đúng chỗ. Độ dài bảng do biểu mẫu giữ bằng số ý.
 */
function validateScoreTable(
  table: readonly string[],
  points: number | null,
  prefix: (string | number)[],
  issues: EditorIssue[],
): void {
  let previous = 0;
  table.forEach((cell, index) => {
    const value = parsePoints(cell);
    if (!Number.isFinite(value)) {
      issues.push({ path: [...prefix, index], message: "scoreValueInvalid" });
      return;
    }
    if (value < previous) issues.push({ path: [...prefix, index], message: "scoreNotIncreasing" });
    else if (index === table.length - 1 && points !== null && !samePoints(value, points)) {
      issues.push({ path: [...prefix, index], message: "scoreLastMismatch" });
    }
    previous = value;
  });
}

/** Mọi lỗi của biểu mẫu, theo thứ tự trên màn hình. Rỗng khi hợp lệ. */
export function validateQuestionEditor(values: QuestionEditorValues): EditorIssue[] {
  const issues: EditorIssue[] = [];
  const isSet = values.type === "QUESTION_SET";
  if (blank(values.subjectCode)) issues.push({ path: ["subjectCode"], message: "subjectRequired" });
  if (blank(values.gradeCode)) issues.push({ path: ["gradeCode"], message: "gradeRequired" });
  if (blank(values.difficultyCode)) issues.push({ path: ["difficultyCode"], message: "difficultyRequired" });
  if (!isSet && values.type !== "CLOZE") validatePoints(values.points, ["points"], issues);
  if (values.knowledge.length === 0) issues.push({ path: ["knowledge"], message: "knowledgeRequired" });

  if (blankHtml(values.content)) issues.push({ path: ["content"], message: isSet ? "passageRequired" : "contentRequired" });

  validateBody(values.type, values.body, values.content, ["body"], issues, values.points);

  if (isSet) {
    values.children.forEach((child, index) => {
      const prefix = ["children", index];
      if (blankHtml(child.content)) issues.push({ path: [...prefix, "content"], message: "childContentRequired" });
      validateBody(child.type, child.body, child.content, [...prefix, "body"], issues, child.points);
      validatePoints(child.points, [...prefix, "points"], issues);
      if (child.knowledgeOverride && child.knowledge.length === 0) {
        issues.push({ path: [...prefix, "knowledge"], message: "childKnowledgeRequired" });
      }
    });
    if (values.children.length < MIN_CHILDREN) issues.push({ path: ["children"], message: "childrenMin" });
  }
  return issues;
}

const optionSchema = z.object({ id: z.string(), content: z.string(), correct: z.boolean() });
const bodySchema = z.object({
  options: z.array(optionSchema),
  statements: z.array(
    z.object({ id: z.string(), content: z.string(), answer: z.enum(["", "TRUE", "FALSE"]), points: z.string() }),
  ),
  blanks: z.array(
    z.object({ id: z.string(), answers: z.array(z.object({ id: z.string(), value: z.string() })), points: z.string() }),
  ),
  left: z.array(z.object({ id: z.string(), content: z.string(), rightId: z.string(), points: z.string() })),
  right: z.array(z.object({ id: z.string(), content: z.string() })),
  reuseRight: z.boolean(),
  scoringMethod: z.enum(TRUE_FALSE_METHODS),
  scoringRule: z.enum(MULTIPLE_CHOICE_RULES),
  scoreTable: z.array(z.string()),
  gradingGuide: z.string(),
  inputHint: z.string(),
  rubric: z.array(z.object({ id: z.string(), description: z.string(), points: z.string() })),
  sampleAnswer: z.string(),
  wordLimit: z.string(),
  blankNumberStart: z.string(),
});

/** Lược đồ của biểu mẫu: kiểu dữ liệu ở `z.object`, quy tắc nghiệp vụ trong `validateQuestionEditor`. */
export const questionEditorSchema = z
  .object({
    type: z.enum(QUESTION_TYPES),
    subjectCode: z.string(),
    gradeCode: z.string(),
    difficultyCode: z.string(),
    competencyCode: z.string(),
    taskTypeCode: z.string(),
    points: z.string(),
    knowledge: z.array(z.object({ nodeId: z.string(), code: z.string(), name: z.string(), primary: z.boolean() })),
    tagCodes: z.array(z.string()),
    content: z.string(),
    solution: z.string(),
    commonMistakes: z.string(),
    attachmentIds: z.array(z.string()),
    body: bodySchema,
    children: z.array(
      z.object({
        key: z.string(),
        existingId: z.string().nullable(),
        type: z.enum(CHILD_QUESTION_TYPES),
        content: z.string(),
        solution: z.string(),
        points: z.string(),
        gradeCode: z.string(),
        difficultyCode: z.string(),
        knowledgeOverride: z.boolean(),
        knowledge: z.array(z.object({ nodeId: z.string(), code: z.string(), name: z.string(), primary: z.boolean() })),
        body: bodySchema,
      }),
    ),
  })
  .superRefine((values, ctx) => {
    for (const issue of validateQuestionEditor(values)) {
      ctx.addIssue({ code: "custom", path: issue.path, message: issue.message });
    }
  }) satisfies z.ZodType<QuestionEditorValues>;
