import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference, KnowledgeNode, QuestionType } from "@/features/content/types";
import { questionStore } from "@/features/questions/mock/store";
import type { QuestionDetail } from "@/features/questions/types";
import type {
  AvailableQuestionCounts,
  AvailableQuestionsQuery,
  ExamSection,
  KnowledgeScope,
  QuestionBlock,
  SubjectAvailableQuestionsQuery,
} from "../types";

/**
 * Đếm nguồn câu hỏi của dữ liệu mẫu (spec 5.2 "Đếm nguồn", B9, D22, D23), dùng chung cho `countAvailableQuestions`,
 * `countSubjectAvailableQuestions` và kiểm tra cấu hình. Chỉ đếm câu Sẵn sàng; một câu độc lập, một câu con của chùm
 * hoặc một vị trí Điền khuyết là một đơn vị đếm. Chỉ là gợi ý khi soạn: kết luận đủ nguồn thuộc bước Kiểm tra.
 */

const reference = referenceFixture as ContentReference;

/** Một đơn vị đếm: dạng trả lời của chính nó (D22), lớp, độ khó, năng lực. */
export interface CountUnit {
  answerType: QuestionType;
  gradeCode: string;
  difficultyCode: string;
  competencyCode: string | null;
}

function indexDescendants(nodes: readonly KnowledgeNode[]): Map<string, Set<string>> {
  const index = new Map<string, Set<string>>();
  const visit = (node: KnowledgeNode): Set<string> => {
    const ids = new Set<string>([node.id]);
    for (const child of node.children) for (const id of visit(child)) ids.add(id);
    index.set(node.id, ids);
    return ids;
  };
  nodes.forEach(visit);
  return index;
}

let descendantsIndex: Map<string, Set<string>> | null = null;

/**
 * Phạm vi MAPID có hiệu lực của nhóm (D21): phạm vi riêng của nhóm nếu có (không giao với phạm vi của môn), không thì
 * phạm vi của môn; cả hai trống là không giới hạn.
 */
export function effectiveScope(block: Pick<QuestionBlock, "knowledgeScope">, section: Pick<ExamSection, "knowledgeScope">): KnowledgeScope | null {
  return block.knowledgeScope ?? section.knowledgeScope ?? null;
}

/**
 * Dạng trả lời nhóm nhận: dạng của nhóm; nhóm lấy theo chỉ tiêu của môn (`questionType = null`) nhận các dạng trong
 * `answerQuotas` của môn.
 */
export function groupAnswerTypes(block: Pick<QuestionBlock, "questionType">, section: Pick<ExamSection, "answerQuotas">): QuestionType[] {
  if (block.questionType) return [block.questionType];
  return section.answerQuotas?.items.map((item) => item.answerType) ?? [];
}

/** Bộ lọc nguồn của một nhóm trong môn (phạm vi MAPID có hiệu lực, dạng trả lời theo chỉ tiêu của môn). */
export function queryOfBlock(block: QuestionBlock, section: ExamSection): AvailableQuestionsQuery {
  return {
    subjectCode: section.subjectCode,
    questionType: block.questionType,
    answerTypes: groupAnswerTypes(block, section),
    childAnswerType: block.childAnswerType,
    knowledgeScope: effectiveScope(block, section),
    taskTypeCode: block.taskTypeCode,
    tagCodes: block.tagCodes,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
  };
}

/** Đơn vị đếm của một câu (D22): chùm theo câu con, Điền khuyết theo vị trí, còn lại một đơn vị. */
export function unitsOf(question: QuestionDetail): CountUnit[] {
  const competencyCode = question.competencyCode ?? null;
  if (question.type === "QUESTION_SET" && question.children.length) {
    return question.children.map((child) => ({
      answerType: child.type,
      gradeCode: child.gradeCode,
      difficultyCode: child.difficultyCode,
      competencyCode: child.competencyCode ?? competencyCode,
    }));
  }
  if (question.type === "CLOZE" && question.body.type === "CLOZE") {
    return question.body.blanks.map(() => ({
      answerType: "CLOZE" as const,
      gradeCode: question.gradeCode,
      difficultyCode: question.difficultyCode,
      competencyCode,
    }));
  }
  return [{ answerType: question.type, gradeCode: question.gradeCode, difficultyCode: question.difficultyCode, competencyCode }];
}

/**
 * Phạm vi MAPID khớp câu khi câu (hoặc câu con) gắn nút đã chọn hoặc nút con của nó (D23); `ALL`: mọi nút đã chọn đều
 * khớp. Phạm vi trống: khớp mọi câu.
 */
export function matchesScope(question: QuestionDetail, scope: KnowledgeScope | null): boolean {
  if (!scope || scope.nodeIds.length === 0) return true;
  descendantsIndex ??= indexDescendants(reference.knowledgeTree);
  const attached = new Set([question, ...question.children].flatMap((item) => item.knowledge.map((ref) => ref.nodeId)));
  const covers = (nodeId: string) => {
    const targets = descendantsIndex?.get(nodeId) ?? new Set([nodeId]);
    for (const id of attached) if (targets.has(id)) return true;
    return false;
  };
  return scope.match === "ALL" ? scope.nodeIds.every(covers) : scope.nodeIds.some(covers);
}

/** Câu Sẵn sàng khớp bộ lọc: môn, dạng trả lời, dạng của câu con, dạng bài, nhãn, năng lực, phạm vi MAPID. */
export function candidatesFor(query: AvailableQuestionsQuery): QuestionDetail[] {
  const competencies = query.competencyCodes ?? query.knowledgeScope?.competencyCodes ?? [];
  const tags = query.tagCodes ?? [];
  return questionStore.all().filter((question) => {
    if (question.status !== "READY" || question.subjectCode !== query.subjectCode) return false;
    if (query.questionType !== null) {
      if (question.type !== query.questionType) return false;
    } else if (question.type === "QUESTION_SET" || question.type === "CLOZE") {
      return false;
    } else if (query.answerTypes.length > 0 && !query.answerTypes.includes(question.type)) {
      return false;
    }
    if (query.childAnswerType && question.type === "QUESTION_SET") {
      if (!question.children.every((child) => child.type === query.childAnswerType)) return false;
    }
    if (query.taskTypeCode && question.taskTypeCode !== query.taskTypeCode) return false;
    if (!tags.every((tag) => question.tagCodes.includes(tag))) return false;
    if (competencies.length && !competencies.includes(question.competencyCode ?? "")) return false;
    return matchesScope(question, query.knowledgeScope);
  });
}

/** Tổng hợp đơn vị đếm của các câu: tổng, theo ô Lớp × Độ khó, theo dạng trả lời, theo năng lực. */
export function countOf(questions: readonly QuestionDetail[]): AvailableQuestionCounts {
  const cells = new Map<string, number>();
  const answerTypes = new Map<QuestionType, number>();
  const competencies = new Map<string, number>();
  let total = 0;
  for (const question of questions) {
    for (const unit of unitsOf(question)) {
      const key = `${unit.gradeCode}|${unit.difficultyCode}`;
      cells.set(key, (cells.get(key) ?? 0) + 1);
      answerTypes.set(unit.answerType, (answerTypes.get(unit.answerType) ?? 0) + 1);
      if (unit.competencyCode) competencies.set(unit.competencyCode, (competencies.get(unit.competencyCode) ?? 0) + 1);
      total += 1;
    }
  }
  return {
    total,
    cells: [...cells].map(([key, value]) => {
      const [gradeCode = "", difficultyCode = ""] = key.split("|");
      return { gradeCode, difficultyCode, value };
    }),
    byAnswerType: [...answerTypes].map(([answerType, value]) => ({ answerType, value })),
    ...(competencies.size ? { byCompetency: [...competencies].map(([code, value]) => ({ code, value })) } : {}),
  };
}

export function countForQuery(query: AvailableQuestionsQuery): AvailableQuestionCounts {
  return countOf(candidatesFor(query));
}

/** Hợp nguồn của các nhóm trong môn: một câu nằm trong nguồn của hai nhóm chỉ đếm một lần. */
export function subjectCandidates(query: SubjectAvailableQuestionsQuery): QuestionDetail[] {
  const union = new Map<string, QuestionDetail>();
  for (const group of query.groups) {
    for (const question of candidatesFor({ ...group, subjectCode: query.subjectCode })) union.set(question.id, question);
  }
  return [...union.values()];
}

export function countForSubject(query: SubjectAvailableQuestionsQuery): AvailableQuestionCounts {
  return countOf(subjectCandidates(query));
}
