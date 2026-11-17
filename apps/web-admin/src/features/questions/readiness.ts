import { indexKnowledge } from "@/features/content/knowledge-tree";
import { QUESTION_TYPES, type ContentReference } from "@/features/content/types";
import type { QuestionDetail } from "./types";

/**
 * Điều kiện chuyển câu hỏi sang Sẵn sàng (UniNote PBI-03 mục 7.2, canvas `KiemTraSanSang`). Hàm thuần: nhận câu
 * hỏi đầy đủ và dữ liệu tham chiếu, trả từng điều kiện đạt hay chưa kèm vị trí cần sửa. Khi có backend, máy chủ
 * là nơi chặn (trả 422 kèm danh sách này); màn hình chỉ hiển thị.
 *
 * Thứ tự điều kiện theo UniNote:
 * 1. CONTENT: đủ nội dung bắt buộc (đề bài, phương án, mệnh đề, ngữ liệu và câu con của câu chùm).
 * 2. ANSWER: có đáp án hợp lệ cho mọi câu được chấm. Đúng/Sai có bảng điểm hợp lệ (đủ số ý, không giảm, ô cuối bằng
 *    điểm của câu); Tự luận có hướng dẫn chấm (bắt buộc), tiêu chí chấm là tùy chọn và có thì tổng điểm tiêu chí bằng
 *    điểm của câu (D20).
 * 3. CLASSIFICATION: đủ môn, khối lớp, độ khó. Kiến thức chính và MAPID không bắt buộc (D20): thiếu thì chỉ cảnh báo.
 * 4. CATALOG_VALUES: giá trị phân loại có trong danh mục và kiến thức thuộc đúng môn.
 * 5. NO_GAPS: không có câu con hoặc vị trí (ô trống, ô thả) khuyết đáp án.
 * 6. RESOURCES: hình ảnh, công thức, ngữ liệu hiển thị được (danh sách tài nguyên lỗi do bên gọi cung cấp).
 * 7. STRUCTURE: cấu trúc thuộc phạm vi hỗ trợ (dạng câu, câu chùm không lồng câu chùm).
 *
 * Lời giải không phải điều kiện: UniNote mục 7.2 không ghi.
 */

export const READINESS_RULES = [
  "CONTENT",
  "ANSWER",
  "CLASSIFICATION",
  "CATALOG_VALUES",
  "NO_GAPS",
  "RESOURCES",
  "STRUCTURE",
] as const;
export type ReadinessRule = (typeof READINESS_RULES)[number];

/**
 * Khu vực cần sửa trên trang soạn câu hỏi. Liên kết "Đến chỗ cần sửa" trỏ tới
 * `/questions/[id]/edit#<READINESS_FIELD_ANCHORS[field]>`; trang soạn đặt `id` này lên khối tương ứng.
 */
export const READINESS_FIELDS = ["content", "answers", "classification", "knowledge", "structure"] as const;
export type ReadinessField = (typeof READINESS_FIELDS)[number];

export const READINESS_FIELD_ANCHORS: Record<ReadinessField, string> = {
  content: "question-content",
  answers: "question-answers",
  classification: "question-classification",
  knowledge: "question-knowledge",
  structure: "question-type",
};

export function readinessFixHref(questionId: string, field: ReadinessField): string {
  return `/questions/${encodeURIComponent(questionId)}/edit#${READINESS_FIELD_ANCHORS[field]}`;
}

export type ReadinessIssueCode =
  // CONTENT
  | "CONTENT_EMPTY"
  | "PASSAGE_EMPTY"
  | "OPTION_EMPTY"
  | "TOO_FEW_OPTIONS"
  | "STATEMENT_EMPTY"
  | "MATCH_RIGHT_EMPTY"
  | "MATCH_LEFT_EMPTY"
  | "SET_WITHOUT_CHILDREN"
  | "CRITERION_EMPTY"
  // ANSWER
  | "NO_CORRECT_OPTION"
  | "SINGLE_CHOICE_MANY_CORRECT"
  | "NO_STATEMENTS"
  | "NO_ANSWERS"
  | "NO_BLANKS"
  | "NO_PAIRS"
  | "SCORE_TABLE_INVALID"
  | "STATEMENT_POINTS_MISMATCH"
  | "BLANK_MARKER_MISMATCH"
  | "PAIR_INVALID"
  | "PAIR_POINTS_MISMATCH"
  | "NO_GRADING_GUIDE"
  | "RUBRIC_TOTAL_MISMATCH"
  // CLASSIFICATION
  | "SUBJECT_MISSING"
  | "GRADE_MISSING"
  | "DIFFICULTY_MISSING"
  // CATALOG_VALUES
  | "SUBJECT_INVALID"
  | "GRADE_INVALID"
  | "DIFFICULTY_INVALID"
  | "TAG_INVALID"
  | "KNOWLEDGE_INVALID"
  | "KNOWLEDGE_OTHER_SUBJECT"
  // NO_GAPS
  | "BLANK_WITHOUT_ANSWER"
  | "LEFT_WITHOUT_PAIR"
  // RESOURCES
  | "RESOURCE_UNAVAILABLE"
  // STRUCTURE
  | "TYPE_UNSUPPORTED"
  | "BODY_TYPE_MISMATCH"
  | "NESTED_SET"
  // Mã riêng của máy chủ (spec 4.3) mà bộ kiểm tra tại chỗ không có
  | "TOO_FEW_CORRECT_OPTIONS"
  | "STATEMENT_ANSWER_REQUIRED"
  | "POINTS_INVALID"
  | "MATH_UNBALANCED"
  | "CATALOG_VALUE_UNKNOWN"
  // Chỗ trống đánh số và gạch chân đánh nhãn (spec UNI-2638 mục 6)
  | "BLANK_CHILD_MISMATCH"
  | "BLANK_OUTSIDE_SET_PASSAGE"
  | "UNDERLINE_LABEL_DUPLICATE"
  | "UNDERLINE_LABEL_MISPLACED"
  /** Mã máy chủ chưa có chữ riêng: hiện kèm mã gốc ở `value`. */
  | "READINESS_OTHER"
  // Cảnh báo (D20)
  | "PRIMARY_KNOWLEDGE_MISSING"
  | "IMAGE_ALT_MISSING";

export interface ReadinessIssue {
  code: ReadinessIssueCode;
  field: ReadinessField;
  /** Câu con của câu chùm, tính từ 1. */
  child?: number;
  /** Vị trí trong câu (phương án, ô trống, ô thả), tính từ 1. */
  position?: number;
  /** Giá trị gây lỗi: mã danh mục, MAPID, tên tệp. */
  value?: string;
}

export interface ReadinessRuleResult {
  rule: ReadinessRule;
  passed: boolean;
  issues: ReadinessIssue[];
}

export interface QuestionReadiness {
  ready: boolean;
  /** Số điều kiện chưa đạt. */
  failedCount: number;
  rules: ReadinessRuleResult[];
  /** Cảnh báo không chặn Sẵn sàng (D20): chưa gắn kiến thức chính hoặc MAPID. */
  warnings: ReadinessIssue[];
}

export interface ReadinessContext {
  /** Danh mục để kiểm điều kiện 4. Không truyền thì bỏ qua điều kiện này (coi là đạt). */
  reference?: Pick<ContentReference, "subjects" | "grades" | "difficulties" | "tags" | "knowledgeTree">;
  /** Tài nguyên (hình, công thức, ngữ liệu) không hiển thị được, do bộ dựng nội dung báo về. */
  unavailableResources?: ReadonlyArray<{ name: string; child?: number }>;
}

type QuestionPart = Pick<QuestionDetail, "type" | "content" | "body" | "children" | "points">;

const sameScore = (left: number, right: number) => Math.abs(left - right) < 0.0005;

/** Bảng điểm Đúng/Sai hợp lệ: đủ số ý, số không âm, không giảm, phần tử cuối bằng điểm của câu. */
export function isValidScoreTable(table: readonly number[] | undefined, statementCount: number, points: number): boolean {
  if (!table || table.length !== statementCount || statementCount === 0) return false;
  for (let index = 0; index < table.length; index += 1) {
    const value = table[index]!;
    if (!Number.isFinite(value) || value < 0) return false;
    if (index > 0 && value < table[index - 1]!) return false;
  }
  return sameScore(table.at(-1)!, points);
}

const blank = (value: string | null | undefined) => !value || value.trim() === "";

function contentIssues(question: QuestionPart, child?: number): ReadinessIssue[] {
  const at = (issue: Omit<ReadinessIssue, "child" | "field">): ReadinessIssue => ({
    ...issue,
    field: "content",
    ...(child !== undefined ? { child } : {}),
  });
  const issues: ReadinessIssue[] = [];
  const body = question.body;
  if (body.type === "QUESTION_SET") {
    if (blank(body.passage) && blank(question.content)) issues.push(at({ code: "PASSAGE_EMPTY" }));
    if (question.children.length === 0) issues.push(at({ code: "SET_WITHOUT_CHILDREN" }));
    question.children.forEach((item, index) => issues.push(...contentIssues(item, index + 1)));
    return issues;
  }
  if (blank(question.content)) issues.push(at({ code: "CONTENT_EMPTY" }));
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      if (body.options.length < 2) issues.push(at({ code: "TOO_FEW_OPTIONS" }));
      body.options.forEach((option, index) => {
        if (blank(option.content)) issues.push(at({ code: "OPTION_EMPTY", position: index + 1 }));
      });
      break;
    case "TRUE_FALSE":
      body.statements.forEach((statement, index) => {
        if (blank(statement.content)) issues.push(at({ code: "STATEMENT_EMPTY", position: index + 1 }));
      });
      break;
    case "MATCHING":
      body.left.forEach((item, index) => {
        if (blank(item.content)) issues.push(at({ code: "MATCH_LEFT_EMPTY", position: index + 1 }));
      });
      body.right.forEach((item, index) => {
        if (blank(item.content)) issues.push(at({ code: "MATCH_RIGHT_EMPTY", position: index + 1 }));
      });
      break;
    case "ESSAY":
      body.rubric.forEach((criterion, index) => {
        if (blank(criterion.description)) issues.push(at({ code: "CRITERION_EMPTY", position: index + 1 }));
      });
      break;
    case "SHORT_ANSWER":
    case "CLOZE":
      break;
  }
  return issues;
}

function answerIssues(question: QuestionPart, child?: number): ReadinessIssue[] {
  const at = (code: ReadinessIssueCode): ReadinessIssue => ({
    code,
    field: "answers",
    ...(child !== undefined ? { child } : {}),
  });
  const body = question.body;
  switch (body.type) {
    case "SINGLE_CHOICE": {
      const correct = body.options.filter((option) => option.correct).length;
      if (correct === 0) return [at("NO_CORRECT_OPTION")];
      return correct > 1 ? [at("SINGLE_CHOICE_MANY_CORRECT")] : [];
    }
    case "MULTIPLE_CHOICE":
      return body.options.some((option) => option.correct) ? [] : [at("NO_CORRECT_OPTION")];
    case "TRUE_FALSE": {
      if (body.statements.length === 0) return [at("NO_STATEMENTS")];
      if (body.scoringMethod === "PER_STATEMENT") {
        const total = body.statements.reduce((sum, statement) => sum + (statement.points ?? 0), 0);
        const valid = body.statements.every((statement) => (statement.points ?? 0) >= 0) && sameScore(total, question.points);
        return valid ? [] : [at("STATEMENT_POINTS_MISMATCH")];
      }
      return isValidScoreTable(body.scoreTable, body.statements.length, question.points) ? [] : [at("SCORE_TABLE_INVALID")];
    }
    case "ESSAY": {
      if (blank(body.gradingGuide)) return [at("NO_GRADING_GUIDE")];
      if (body.rubric.length === 0) return [];
      const total = body.rubric.reduce((sum, criterion) => sum + criterion.points, 0);
      return sameScore(total, question.points) ? [] : [at("RUBRIC_TOTAL_MISMATCH")];
    }
    case "SHORT_ANSWER":
      return body.answers.some((answer) => !blank(answer)) ? [] : [at("NO_ANSWERS")];
    case "CLOZE": {
      if (body.blanks.length === 0) return [at("NO_BLANKS")];
      // Mỗi dấu `[[id]]` trong ngữ liệu có đúng một ô và ngược lại (spec 4.3).
      const markers = Array.from(body.passage.matchAll(/\[\[([^\]]+)\]\]/g), (match) => match[1]!.trim());
      const ids = body.blanks.map((item) => item.id);
      const matched =
        markers.length === ids.length &&
        new Set(markers).size === markers.length &&
        markers.every((marker) => ids.includes(marker));
      return matched ? [] : [at("BLANK_MARKER_MISMATCH")];
    }
    case "MATCHING": {
      if (body.pairs.length === 0) return [at("NO_PAIRS")];
      // Cặp trỏ tới mục có thật, mỗi mục trái một cặp, lựa chọn phải dùng một lần khi `reuseRight = false`.
      const leftIds = new Set(body.left.map((item) => item.id));
      const rightIds = new Set(body.right.map((item) => item.id));
      const pairedLeft = new Set<string>();
      const pairedRight = new Set<string>();
      let invalid = false;
      for (const pair of body.pairs) {
        if (!leftIds.has(pair.leftId) || !rightIds.has(pair.rightId) || pairedLeft.has(pair.leftId)) invalid = true;
        if (!body.reuseRight && pairedRight.has(pair.rightId)) invalid = true;
        pairedLeft.add(pair.leftId);
        pairedRight.add(pair.rightId);
      }
      const issues = invalid ? [at("PAIR_INVALID")] : [];
      const total = body.pairs.reduce((sum, pair) => sum + pair.points, 0);
      return sameScore(total, question.points) ? issues : [...issues, at("PAIR_POINTS_MISMATCH")];
    }
    case "QUESTION_SET":
      return question.children.flatMap((item, index) => answerIssues(item, index + 1));
    default:
      return [];
  }
}

function gapIssues(question: QuestionPart, child?: number): ReadinessIssue[] {
  const at = (code: ReadinessIssueCode, position: number): ReadinessIssue => ({
    code,
    field: "answers",
    position,
    ...(child !== undefined ? { child } : {}),
  });
  const body = question.body;
  switch (body.type) {
    case "CLOZE":
      return body.blanks.flatMap((item, index) =>
        item.answers.some((answer) => !blank(answer)) ? [] : [at("BLANK_WITHOUT_ANSWER", index + 1)],
      );
    case "MATCHING": {
      const paired = new Set(body.pairs.map((pair) => pair.leftId));
      return body.left.flatMap((item, index) => (paired.has(item.id) ? [] : [at("LEFT_WITHOUT_PAIR", index + 1)]));
    }
    case "QUESTION_SET":
      return question.children.flatMap((item, index) => gapIssues(item, index + 1));
    default:
      return [];
  }
}

function classificationIssues(question: QuestionDetail): ReadinessIssue[] {
  const issues: ReadinessIssue[] = [];
  if (blank(question.subjectCode)) issues.push({ code: "SUBJECT_MISSING", field: "classification" });
  if (blank(question.gradeCode)) issues.push({ code: "GRADE_MISSING", field: "classification" });
  if (blank(question.difficultyCode)) issues.push({ code: "DIFFICULTY_MISSING", field: "classification" });
  return issues;
}

function catalogIssues(question: QuestionDetail, reference: ReadinessContext["reference"]): ReadinessIssue[] {
  if (!reference) return [];
  const has = (options: ReadonlyArray<{ code: string }>, code: string) => options.some((option) => option.code === code);
  const issues: ReadinessIssue[] = [];
  if (!blank(question.subjectCode) && !has(reference.subjects, question.subjectCode)) {
    issues.push({ code: "SUBJECT_INVALID", field: "classification", value: question.subjectCode });
  }
  if (!blank(question.gradeCode) && !has(reference.grades, question.gradeCode)) {
    issues.push({ code: "GRADE_INVALID", field: "classification", value: question.gradeCode });
  }
  if (!blank(question.difficultyCode) && !has(reference.difficulties, question.difficultyCode)) {
    issues.push({ code: "DIFFICULTY_INVALID", field: "classification", value: question.difficultyCode });
  }
  for (const tag of question.tagCodes) {
    if (!has(reference.tags, tag)) issues.push({ code: "TAG_INVALID", field: "classification", value: tag });
  }
  const index = indexKnowledge(reference.knowledgeTree);
  for (const ref of question.knowledge) {
    const node = index.get(ref.nodeId)?.node;
    if (!node || !node.active || node.level !== "KNOWLEDGE") {
      issues.push({ code: "KNOWLEDGE_INVALID", field: "knowledge", value: ref.code });
    } else if (!blank(question.subjectCode) && node.subjectCode !== question.subjectCode) {
      issues.push({ code: "KNOWLEDGE_OTHER_SUBJECT", field: "knowledge", value: ref.code });
    }
  }
  return issues;
}

function structureIssues(question: QuestionPart, child?: number): ReadinessIssue[] {
  const at = (code: ReadinessIssueCode): ReadinessIssue => ({
    code,
    field: "structure",
    ...(child !== undefined ? { child } : {}),
  });
  if (!(QUESTION_TYPES as readonly string[]).includes(question.type)) return [at("TYPE_UNSUPPORTED")];
  if (question.body.type !== question.type) return [at("BODY_TYPE_MISMATCH")];
  if (question.type !== "QUESTION_SET") return [];
  return question.children.flatMap((item, index) =>
    item.type === "QUESTION_SET" || item.type === "CLOZE"
      ? [{ code: "NESTED_SET" as const, field: "structure" as const, child: index + 1 }]
      : structureIssues(item, index + 1),
  );
}

function resourceIssues(context: ReadinessContext): ReadinessIssue[] {
  return (context.unavailableResources ?? []).map((resource) => ({
    code: "RESOURCE_UNAVAILABLE" as const,
    field: "content" as const,
    value: resource.name,
    ...(resource.child !== undefined ? { child: resource.child } : {}),
  }));
}

/** Kiểm bảy điều kiện chuyển sang Sẵn sàng. Thứ tự kết quả theo `READINESS_RULES`. */
export function evaluateReadiness(question: QuestionDetail, context: ReadinessContext = {}): QuestionReadiness {
  // Cấu trúc sai (dạng câu không hỗ trợ, phần riêng lệch dạng) thì các điều kiện còn lại không kiểm được đúng.
  const structure = structureIssues(question);
  const structureBroken = structure.some((issue) => issue.child === undefined);
  const byRule: Record<ReadinessRule, ReadinessIssue[]> = {
    CONTENT: structureBroken ? [] : contentIssues(question),
    ANSWER: structureBroken ? [] : answerIssues(question),
    CLASSIFICATION: classificationIssues(question),
    CATALOG_VALUES: catalogIssues(question, context.reference),
    NO_GAPS: structureBroken ? [] : gapIssues(question),
    RESOURCES: resourceIssues(context),
    STRUCTURE: structure,
  };
  const warnings: ReadinessIssue[] = question.knowledge.some((ref) => ref.primary)
    ? []
    : [{ code: "PRIMARY_KNOWLEDGE_MISSING", field: "knowledge" }];
  const rules = READINESS_RULES.map((rule) => ({
    rule,
    passed: byRule[rule].length === 0,
    issues: byRule[rule],
  }));
  const failedCount = rules.filter((result) => !result.passed).length;
  return { ready: failedCount === 0, failedCount, rules, warnings };
}

/** Vấn đề đầu tiên còn tồn tại (để gợi ý ngắn trên danh sách); `null` khi đã đủ điều kiện. */
export function firstReadinessIssue(readiness: QuestionReadiness): ReadinessIssue | null {
  for (const result of readiness.rules) {
    const issue = result.issues[0];
    if (issue) return issue;
  }
  return null;
}
