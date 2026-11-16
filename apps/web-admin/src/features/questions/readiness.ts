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
 *    điểm của câu); Tự luận có ít nhất một tiêu chí chấm và tổng điểm tiêu chí bằng điểm của câu (UNI-2599).
 * 3. CLASSIFICATION: đủ môn, khối lớp, độ khó và kiến thức chính.
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
  | "DRAG_ITEM_EMPTY"
  | "ZONE_LABEL_EMPTY"
  | "SET_WITHOUT_CHILDREN"
  | "CRITERION_EMPTY"
  // ANSWER
  | "NO_CORRECT_OPTION"
  | "SINGLE_CHOICE_MANY_CORRECT"
  | "NO_STATEMENTS"
  | "NO_BLANKS"
  | "NO_DROP_ZONES"
  | "SCORE_TABLE_INVALID"
  | "NO_RUBRIC"
  | "RUBRIC_TOTAL_MISMATCH"
  // CLASSIFICATION
  | "SUBJECT_MISSING"
  | "GRADE_MISSING"
  | "DIFFICULTY_MISSING"
  | "PRIMARY_KNOWLEDGE_MISSING"
  // CATALOG_VALUES
  | "SUBJECT_INVALID"
  | "GRADE_INVALID"
  | "DIFFICULTY_INVALID"
  | "TAG_INVALID"
  | "KNOWLEDGE_INVALID"
  | "KNOWLEDGE_OTHER_SUBJECT"
  // NO_GAPS
  | "BLANK_WITHOUT_ANSWER"
  | "ZONE_WITHOUT_ANSWER"
  // RESOURCES
  | "RESOURCE_UNAVAILABLE"
  // STRUCTURE
  | "TYPE_UNSUPPORTED"
  | "BODY_TYPE_MISMATCH"
  | "NESTED_SET";

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
    case "DRAG_DROP":
      body.items.forEach((item, index) => {
        if (blank(item.content)) issues.push(at({ code: "DRAG_ITEM_EMPTY", position: index + 1 }));
      });
      body.zones.forEach((zone, index) => {
        if (blank(zone.label)) issues.push(at({ code: "ZONE_LABEL_EMPTY", position: index + 1 }));
      });
      break;
    case "ESSAY":
      body.rubric.forEach((criterion, index) => {
        if (blank(criterion.description)) issues.push(at({ code: "CRITERION_EMPTY", position: index + 1 }));
      });
      break;
    case "FILL_IN":
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
    case "TRUE_FALSE":
      if (body.statements.length === 0) return [at("NO_STATEMENTS")];
      return isValidScoreTable(body.scoreTable, body.statements.length, question.points) ? [] : [at("SCORE_TABLE_INVALID")];
    case "ESSAY": {
      if (body.rubric.length === 0) return [at("NO_RUBRIC")];
      const total = body.rubric.reduce((sum, criterion) => sum + criterion.points, 0);
      return sameScore(total, question.points) ? [] : [at("RUBRIC_TOTAL_MISMATCH")];
    }
    case "FILL_IN":
      return body.blanks.length === 0 ? [at("NO_BLANKS")] : [];
    case "DRAG_DROP":
      return body.zones.length === 0 ? [at("NO_DROP_ZONES")] : [];
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
    case "FILL_IN":
      return body.blanks.flatMap((item, index) =>
        item.acceptedAnswers.some((answer) => !blank(answer)) ? [] : [at("BLANK_WITHOUT_ANSWER", index + 1)],
      );
    case "DRAG_DROP": {
      const itemIds = new Set(body.items.map((item) => item.id));
      return body.zones.flatMap((zone, index) =>
        itemIds.has(zone.correctItemId) ? [] : [at("ZONE_WITHOUT_ANSWER", index + 1)],
      );
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
  if (!question.knowledge.some((ref) => ref.primary)) {
    issues.push({ code: "PRIMARY_KNOWLEDGE_MISSING", field: "knowledge" });
  }
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
    item.type === "QUESTION_SET"
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
  const rules = READINESS_RULES.map((rule) => ({
    rule,
    passed: byRule[rule].length === 0,
    issues: byRule[rule],
  }));
  const failedCount = rules.filter((result) => !result.passed).length;
  return { ready: failedCount === 0, failedCount, rules };
}

/** Vấn đề đầu tiên còn tồn tại (để gợi ý ngắn trên danh sách); `null` khi đã đủ điều kiện. */
export function firstReadinessIssue(readiness: QuestionReadiness): ReadinessIssue | null {
  for (const result of readiness.rules) {
    const issue = result.issues[0];
    if (issue) return issue;
  }
  return null;
}
