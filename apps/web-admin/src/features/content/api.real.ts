import type { components } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { toContentApiError } from "./api-error";
import {
  MULTIPLE_CHOICE_RULES,
  PRACTICE_SCORINGS,
  QUESTION_TYPES,
  TRUE_FALSE_METHODS,
  EXAM_SCOPES,
  type ContentReference,
  type ExamKindOption,
  type ExamScope,
  type KnowledgeNode,
  type MultipleChoiceRule,
  type PracticeScoring,
  type QuestionTypeOption,
  type TrueFalseDefaults,
  type TrueFalseMethod,
} from "./types";

type ReferenceResponse = components["schemas"]["ContentReferenceResponse"];
type ExamKindResponse = components["schemas"]["ExamKindOption"];
type KnowledgeNodeResponse = components["schemas"]["KnowledgeNode"];

function oneOf<T extends string>(values: readonly T[], value: string | undefined, fallback: T): T {
  return (values as readonly string[]).includes(value ?? "") ? (value as T) : fallback;
}

/** `trueFalseScoring` của hồ sơ kỳ thi là JSON `{method, table, statementPoints}`; chưa có hồ sơ thì chấm từng ý. */
function toTrueFalseDefaults(raw: ExamKindResponse["trueFalseScoring"]): TrueFalseDefaults {
  const json = (raw ?? {}) as { method?: string; table?: unknown; statementPoints?: unknown };
  return {
    method: oneOf<TrueFalseMethod>(TRUE_FALSE_METHODS, json.method, "PER_STATEMENT"),
    table: Array.isArray(json.table) ? json.table.filter((value): value is number => typeof value === "number") : [],
    statementPoints: typeof json.statementPoints === "number" ? json.statementPoints : null,
  };
}

function toExamKind(kind: ExamKindResponse): ExamKindOption {
  return {
    code: kind.code,
    name: kind.name,
    scopes: kind.scopes.filter((scope): scope is ExamScope => (EXAM_SCOPES as readonly string[]).includes(scope)),
    templateBy: kind.templateBy === "SUBJECT" ? "SUBJECT" : "KIND",
    timing: kind.timing === "SHARED" ? "SHARED" : "PER_PART",
    maxScore: kind.maxScore ?? null,
    referenceMax: kind.referenceMax ?? null,
    practiceScoring: oneOf<PracticeScoring>(PRACTICE_SCORINGS, kind.practiceScoring, "RAW"),
    scoringDefaults: {
      trueFalse: toTrueFalseDefaults(kind.trueFalseScoring),
      multipleChoiceRule: oneOf<MultipleChoiceRule>(MULTIPLE_CHOICE_RULES, kind.multipleChoiceRule, "ALL_OR_NOTHING"),
    },
  };
}

function toKnowledgeNode(node: KnowledgeNodeResponse): KnowledgeNode {
  return {
    id: node.id,
    code: node.code,
    name: node.name,
    level: node.level,
    subjectCode: node.subjectCode ?? "",
    gradeCode: node.gradeCode ?? null,
    active: node.active,
    children: node.children.map(toKnowledgeNode),
  };
}

/** Tám dạng câu cố định (D1): bỏ mã lạ, giữ thứ tự của máy chủ. */
function toQuestionTypes(types: ReferenceResponse["questionTypes"]): QuestionTypeOption[] {
  return types
    .filter((type) => (QUESTION_TYPES as readonly string[]).includes(type.code))
    .map((type) => ({ code: type.code as QuestionTypeOption["code"], name: type.name, active: type.active }));
}

export function toContentReference(response: ReferenceResponse): ContentReference {
  return {
    subjects: response.subjects,
    grades: response.grades,
    difficulties: response.difficulties,
    taskTypes: response.taskTypes,
    questionTypes: toQuestionTypes(response.questionTypes),
    tags: response.tags,
    examKinds: response.examKinds.map(toExamKind),
    combinations: response.combinations,
    competencies: response.competencies.map((competency) => ({
      code: competency.code,
      name: competency.name,
      subjectCode: competency.subjectCode ?? null,
      examKindCodes: competency.examKindCodes,
    })),
    knowledgeTree: response.knowledgeTree.map(toKnowledgeNode),
  };
}

/**
 * `GET /content-reference`. Tên danh mục đã theo ngôn ngữ của yêu cầu: proxy Next.js đặt `Accept-Language` từ cookie
 * `thi360_locale`, nên khóa truy vấn của màn phải có ngôn ngữ để đổi ngôn ngữ thì tải lại.
 */
export async function getContentReference(): Promise<ContentReference> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/content-reference");
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return toContentReference(data);
}
