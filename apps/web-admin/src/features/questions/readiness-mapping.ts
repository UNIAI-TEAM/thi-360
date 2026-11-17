import type { components } from "@thi360/api-client";
import {
  READINESS_RULES,
  type QuestionReadiness,
  type ReadinessField,
  type ReadinessIssue,
  type ReadinessIssueCode,
  type ReadinessRule,
} from "./readiness";

type ApiReadiness = components["schemas"]["QuestionReadiness"];
type ApiFieldError = components["schemas"]["QuestionFieldError"];

interface Placement {
  code: ReadinessIssueCode;
  rule: ReadinessRule;
  field: ReadinessField;
}

const place = (code: ReadinessIssueCode, rule: ReadinessRule, field: ReadinessField): Placement => ({ code, rule, field });

/** Mã điều kiện Sẵn sàng của máy chủ (`QuestionBodyValidator`) -> mã, điều kiện và khu vực sửa trên màn. */
const PLACEMENTS: Record<string, Placement> = {
  CONTENT_EMPTY: place("CONTENT_EMPTY", "CONTENT", "content"),
  PASSAGE_EMPTY: place("PASSAGE_EMPTY", "CONTENT", "content"),
  OPTION_EMPTY: place("OPTION_EMPTY", "CONTENT", "content"),
  TOO_FEW_OPTIONS: place("TOO_FEW_OPTIONS", "CONTENT", "content"),
  STATEMENT_EMPTY: place("STATEMENT_EMPTY", "CONTENT", "content"),
  SET_WITHOUT_CHILDREN: place("SET_WITHOUT_CHILDREN", "CONTENT", "content"),
  CRITERION_EMPTY: place("CRITERION_EMPTY", "CONTENT", "content"),
  NO_CORRECT_OPTION: place("NO_CORRECT_OPTION", "ANSWER", "answers"),
  SINGLE_CHOICE_MANY_CORRECT: place("SINGLE_CHOICE_MANY_CORRECT", "ANSWER", "answers"),
  TOO_FEW_CORRECT_OPTIONS: place("TOO_FEW_CORRECT_OPTIONS", "ANSWER", "answers"),
  NO_STATEMENTS: place("NO_STATEMENTS", "ANSWER", "answers"),
  NO_BLANKS: place("NO_BLANKS", "ANSWER", "answers"),
  SCORE_TABLE_INVALID: place("SCORE_TABLE_INVALID", "ANSWER", "answers"),
  STATEMENT_ANSWER_REQUIRED: place("STATEMENT_ANSWER_REQUIRED", "ANSWER", "answers"),
  QUESTION_BLANK_MARKER_MISMATCH: place("BLANK_MARKER_MISMATCH", "ANSWER", "answers"),
  QUESTION_PAIR_UNKNOWN_LEFT: place("PAIR_INVALID", "ANSWER", "answers"),
  QUESTION_PAIR_UNKNOWN_RIGHT: place("PAIR_INVALID", "ANSWER", "answers"),
  QUESTION_PAIR_DUPLICATE_LEFT: place("PAIR_INVALID", "ANSWER", "answers"),
  QUESTION_RIGHT_REUSED: place("PAIR_INVALID", "ANSWER", "answers"),
  QUESTION_PAIR_POINTS_MISMATCH: place("PAIR_POINTS_MISMATCH", "ANSWER", "answers"),
  NO_GRADING_GUIDE: place("NO_GRADING_GUIDE", "ANSWER", "answers"),
  RUBRIC_TOTAL_MISMATCH: place("RUBRIC_TOTAL_MISMATCH", "ANSWER", "answers"),
  POINTS_REQUIRED: place("POINTS_INVALID", "ANSWER", "answers"),
  POINTS_NOT_POSITIVE: place("POINTS_INVALID", "ANSWER", "answers"),
  POINTS_TOO_MANY_DECIMALS: place("POINTS_INVALID", "ANSWER", "answers"),
  POINTS_TOO_LARGE: place("POINTS_INVALID", "ANSWER", "answers"),
  SUBJECT_MISSING: place("SUBJECT_MISSING", "CLASSIFICATION", "classification"),
  GRADE_MISSING: place("GRADE_MISSING", "CLASSIFICATION", "classification"),
  DIFFICULTY_MISSING: place("DIFFICULTY_MISSING", "CLASSIFICATION", "classification"),
  CATALOG_VALUE_UNKNOWN: place("CATALOG_VALUE_UNKNOWN", "CATALOG_VALUES", "classification"),
  BLANK_WITHOUT_ANSWER: place("BLANK_WITHOUT_ANSWER", "NO_GAPS", "answers"),
  QUESTION_LEFT_UNPAIRED: place("LEFT_WITHOUT_PAIR", "NO_GAPS", "answers"),
  MATH_UNBALANCED: place("MATH_UNBALANCED", "RESOURCES", "content"),
  QUESTION_ATTACHMENT_UNKNOWN: place("RESOURCE_UNAVAILABLE", "RESOURCES", "content"),
  QUESTION_BODY_TYPE_MISMATCH: place("BODY_TYPE_MISMATCH", "STRUCTURE", "structure"),
  QUESTION_CHILD_TYPE_NOT_ALLOWED: place("NESTED_SET", "STRUCTURE", "structure"),
  PRIMARY_KNOWLEDGE_MISSING: place("PRIMARY_KNOWLEDGE_MISSING", "CONTENT", "knowledge"),
  // Spec UNI-2638 mục 6: bốn mã chặn nằm ở nội dung HTML (sửa ở ô nội dung); IMAGE_ALT_MISSING là cảnh báo.
  BLANK_CHILD_MISMATCH: place("BLANK_CHILD_MISMATCH", "STRUCTURE", "content"),
  BLANK_OUTSIDE_SET_PASSAGE: place("BLANK_OUTSIDE_SET_PASSAGE", "STRUCTURE", "content"),
  UNDERLINE_LABEL_DUPLICATE: place("UNDERLINE_LABEL_DUPLICATE", "STRUCTURE", "content"),
  UNDERLINE_LABEL_MISPLACED: place("UNDERLINE_LABEL_MISPLACED", "STRUCTURE", "content"),
  IMAGE_ALT_MISSING: place("IMAGE_ALT_MISSING", "CONTENT", "content"),
};

const FALLBACK = place("READINESS_OTHER", "STRUCTURE", "structure");

const CHILD_PATH = /^body\.children\[(\d+)\]\.(.*)$/;
const FIRST_INDEX = /\[(\d+)\]/;

/** `body.children[1].body.options[2].content` -> câu con 2, vị trí 3 (tính từ 1). */
function locate(path: string | undefined): { child?: number; position?: number; local: string } {
  const text = path ?? "";
  const childMatch = CHILD_PATH.exec(text);
  const local = childMatch ? (childMatch[2] ?? "") : text;
  const position = FIRST_INDEX.exec(local);
  return {
    ...(childMatch ? { child: Number(childMatch[1]) + 1 } : {}),
    ...(position ? { position: Number(position[1]) + 1 } : {}),
    local,
  };
}

function toIssue(error: ApiFieldError): { issue: ReadinessIssue; rule: ReadinessRule } {
  const code = error.code ?? "";
  const found = PLACEMENTS[code];
  const { child, position, local } = locate(error.path);
  // Hai bên của câu Ghép đôi cùng mã `MATCHING_SIDE_EMPTY`, phân biệt theo đường dẫn.
  const placement =
    code === "MATCHING_SIDE_EMPTY"
      ? place(local.startsWith("body.right") ? "MATCH_RIGHT_EMPTY" : "MATCH_LEFT_EMPTY", "CONTENT", "content")
      : (found ?? FALLBACK);
  const issue: ReadinessIssue = {
    code: placement.code,
    field: placement.field,
    ...(child !== undefined ? { child } : {}),
    ...(position !== undefined ? { position } : {}),
    ...(placement === FALLBACK ? { value: code } : {}),
  };
  return { issue, rule: placement.rule };
}

/** Kết quả kiểm tra của máy chủ -> bảy điều kiện của canvas `KiemTraSanSang`, lỗi chặn nhóm theo điều kiện. */
export function fromApiReadiness(readiness: ApiReadiness): QuestionReadiness {
  const placed = readiness.errors.map(toIssue);
  const rules = READINESS_RULES.map((rule) => {
    const issues = placed.filter((entry) => entry.rule === rule).map((entry) => entry.issue);
    return { rule, passed: issues.length === 0, issues };
  });
  return {
    ready: readiness.ready,
    failedCount: rules.filter((rule) => !rule.passed).length,
    rules,
    warnings: readiness.warnings.map((warning) => toIssue(warning).issue),
  };
}
