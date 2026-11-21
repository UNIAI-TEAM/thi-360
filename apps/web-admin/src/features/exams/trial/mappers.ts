import {
  countWords,
  type AnswerFormat,
  type AnswerValue as KitAnswerValue,
  type ClozeAnswer as KitClozeAnswer,
  type EssayAnswer as KitEssayAnswer,
  type KitQuestion,
  type MatchingAnswer as KitMatchingAnswer,
  type MultipleChoiceAnswer as KitMultipleChoiceAnswer,
  type MultipleChoiceRule,
  type QuestionBody as KitQuestionBody,
  type QuestionOutcome,
  type QuestionSetAnswer as KitQuestionSetAnswer,
  type ShortAnswerAnswer as KitShortAnswerAnswer,
  type SingleChoiceAnswer as KitSingleChoiceAnswer,
  type TrueFalseAnswer as KitTrueFalseAnswer,
} from "@thi360/ui/question";
import type { PreviewAttachment } from "../review-types";
import type {
  PreviewGradeResponse,
  PreviewGradedItem,
  PreviewTrialAnswerValue,
  PreviewTrialAttachment,
  PreviewTrialBlock,
  PreviewTrialItem,
  PreviewTrialOutcome,
  PreviewTrialPaperResponse,
  PreviewTrialPart,
  PreviewTrialQuestion,
  PreviewTrialQuestionSet,
  PreviewTrialQuestionType,
  PreviewTrialSegment,
} from "./types";

const VALID_ANSWER_FORMATS = new Set<AnswerFormat>([
  "INTEGER",
  "DECIMAL",
  "FRACTION",
  "TEXT",
  "CHEM_FORMULA",
  "CHEM_EQUATION",
]);

function asAnswerFormat(raw: string | undefined | null): AnswerFormat | null {
  if (raw && VALID_ANSWER_FORMATS.has(raw as AnswerFormat)) {
    return raw as AnswerFormat;
  }
  return null;
}

function asScoringRule(raw: string | undefined | null): MultipleChoiceRule | undefined {
  if (raw === "ALL_OR_NOTHING" || raw === "PARTIAL") return raw;
  return undefined;
}

interface RawOptionView {
  id?: string;
  content?: string;
}
interface RawBlankView {
  id?: string;
}
interface RawStatementView {
  id?: string;
  content?: string;
}
interface RawSideView {
  id?: string;
  content?: string;
}
interface RawQuestionBodyShape {
  type?: string;
  options?: RawOptionView[];
  scoringRule?: string;
  inputHint?: string;
  answerFormat?: string;
  passage?: string;
  blanks?: RawBlankView[];
  statements?: RawStatementView[];
  left?: RawSideView[];
  right?: RawSideView[];
  reuseRight?: boolean;
  wordLimit?: number;
}

/**
 * Map sanitized `PreviewTrialQuestion` to `@thi360/ui/question` `KitQuestion`.
 * Strictly keeps only prompt/choice fields — no solutions, keys, or rubrics.
 */
export function toKitTrialQuestion(q: PreviewTrialQuestion): KitQuestion {
  const id = q.questionId ?? "";
  const displayNo = q.number ?? 1;
  const rawBody = q.body as RawQuestionBodyShape | undefined;
  const qType: PreviewTrialQuestionType =
    q.type ?? (rawBody?.type as PreviewTrialQuestionType | undefined) ?? "SINGLE_CHOICE";

  let body: KitQuestionBody;
  switch (qType) {
    case "SINGLE_CHOICE": {
      body = {
        type: "SINGLE_CHOICE",
        options: (rawBody?.options ?? []).map((opt) => ({
          id: opt.id ?? "",
          content: opt.content ?? "",
        })),
      };
      break;
    }
    case "MULTIPLE_CHOICE": {
      body = {
        type: "MULTIPLE_CHOICE",
        options: (rawBody?.options ?? []).map((opt) => ({
          id: opt.id ?? "",
          content: opt.content ?? "",
        })),
        scoringRule: asScoringRule(rawBody?.scoringRule),
      };
      break;
    }
    case "SHORT_ANSWER": {
      body = {
        type: "SHORT_ANSWER",
        answers: [],
        inputHint: rawBody?.inputHint ?? null,
        answerFormat: asAnswerFormat(rawBody?.answerFormat),
      };
      break;
    }
    case "CLOZE": {
      body = {
        type: "CLOZE",
        passage: rawBody?.passage ?? "",
        blanks: (rawBody?.blanks ?? []).map((blank) => ({
          id: blank.id ?? "",
          answers: [],
        })),
      };
      break;
    }
    case "TRUE_FALSE": {
      body = {
        type: "TRUE_FALSE",
        statements: (rawBody?.statements ?? []).map((st) => ({
          id: st.id ?? "",
          content: st.content ?? "",
        })),
      };
      break;
    }
    case "MATCHING": {
      body = {
        type: "MATCHING",
        left: (rawBody?.left ?? []).map((side) => ({
          id: side.id ?? "",
          content: side.content ?? "",
        })),
        right: (rawBody?.right ?? []).map((side) => ({
          id: side.id ?? "",
          content: side.content ?? "",
        })),
        reuseRight: rawBody?.reuseRight ?? false,
      };
      break;
    }
    case "ESSAY": {
      body = {
        type: "ESSAY",
        wordLimit: rawBody?.wordLimit ?? null,
      };
      break;
    }
    case "QUESTION_SET":
    default: {
      body = {
        type: "QUESTION_SET",
        passage: rawBody?.passage ?? "",
      };
      break;
    }
  }

  return {
    id,
    displayNo,
    points: q.points,
    stem: q.content ?? undefined,
    body,
    optionOrder: q.optionOrder && q.optionOrder.length > 0 ? q.optionOrder : undefined,
  };
}

export function toKitTrialSet(setItem: PreviewTrialQuestionSet): {
  setQuestion: KitQuestion;
  childQuestions: KitQuestion[];
} {
  const children = setItem.children ?? [];
  const childQuestions = children.map(toKitTrialQuestion);
  const childIds = childQuestions.map((c) => c.id).filter(Boolean);
  const childNumbers = children.map((c) => c.number ?? 0).filter((n) => n > 0);
  const displayNo = childNumbers.length > 0 ? Math.min(...childNumbers) : 1;

  return {
    setQuestion: {
      id: setItem.questionId ?? "",
      displayNo,
      stem: setItem.content ?? undefined,
      body: {
        type: "QUESTION_SET",
        passage: setItem.passage ?? "",
        blankNumberStart: setItem.blankNumberStart,
        childIds,
      },
    },
    childQuestions,
  };
}

export function toTrialAttachments(
  attachments: readonly PreviewTrialAttachment[] | undefined,
): PreviewAttachment[] {
  if (!attachments || attachments.length === 0) return [];
  return attachments
    .map((att) => ({
      id: att.id ?? "",
      name: att.name ?? "",
      mime: att.mime ?? "",
    }))
    .filter((att) => Boolean(att.id && att.mime));
}

export interface TrialSegmentSlice {
  segment: PreviewTrialSegment;
  blocks: PreviewTrialBlock[];
}

export function sliceTrialPartSegments(part: PreviewTrialPart): TrialSegmentSlice[] | null {
  const segments = part.displaySegments;
  if (!segments || segments.length === 0) return null;
  const firstNumber = part.firstNumber ?? 1;

  return [...segments]
    .sort((a, b) => (a.fromPosition ?? 0) - (b.fromPosition ?? 0))
    .map((segment) => {
      const fromPos = segment.fromPosition ?? 1;
      const toPos = segment.toPosition ?? Number.MAX_SAFE_INTEGER;
      const inside = (q: PreviewTrialQuestion) => {
        const position = (q.number ?? 1) - firstNumber + 1;
        return position >= fromPos && position <= toPos;
      };
      const blocks = (part.blocks ?? []).flatMap((block): PreviewTrialBlock[] => {
        const items = (block.items ?? []).flatMap((item): PreviewTrialItem[] => {
          if (item.kind === "QUESTION" && item.question) {
            return inside(item.question) ? [item] : [];
          }
          if (item.kind === "SET" && item.set) {
            const matchingChildren = (item.set.children ?? []).filter(inside);
            if (matchingChildren.length === 0) return [];
            if (matchingChildren.length === (item.set.children ?? []).length) return [item];
            return [{ ...item, set: { ...item.set, children: matchingChildren } }];
          }
          return [];
        });
        if (items.length === 0) return [];
        const numbers = items.flatMap((item) =>
          (item.kind === "SET" ? (item.set?.children ?? []) : item.question ? [item.question] : [])
            .map((q) => q.number ?? 0)
            .filter((n) => n > 0),
        );
        return [
          {
            ...block,
            items,
            firstNumber: numbers.length > 0 ? Math.min(...numbers) : block.firstNumber,
            lastNumber: numbers.length > 0 ? Math.max(...numbers) : block.lastNumber,
            questionCount: numbers.length || block.questionCount,
          },
        ];
      });
      return { segment, blocks };
    });
}

export interface TrialLeafDescriptor {
  question: PreviewTrialQuestion;
  questionId: string;
  number: number;
  type: PreviewTrialQuestionType;
  partId: string;
  partName: string;
  setQuestionId?: string;
}

export function listPaperLeafQuestions(paper: PreviewTrialPaperResponse): TrialLeafDescriptor[] {
  const result: TrialLeafDescriptor[] = [];
  const parts = [...(paper.parts ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  for (const part of parts) {
    const partId = part.partId ?? "";
    const partName = part.name ?? "";
    for (const block of part.blocks ?? []) {
      for (const item of block.items ?? []) {
        if (item.kind === "QUESTION" && item.question?.questionId) {
          result.push({
            question: item.question,
            questionId: item.question.questionId,
            number: item.question.number ?? result.length + 1,
            type: item.question.type ?? "SINGLE_CHOICE",
            partId,
            partName,
          });
        } else if (item.kind === "SET" && item.set) {
          for (const child of item.set.children ?? []) {
            if (!child.questionId) continue;
            result.push({
              question: child,
              questionId: child.questionId,
              number: child.number ?? result.length + 1,
              type: child.type ?? "SINGLE_CHOICE",
              partId,
              partName,
              setQuestionId: item.set.questionId,
            });
          }
        }
      }
    }
  }
  return result.sort((a, b) => a.number - b.number);
}

export function buildSetAnswerValue(
  children: readonly PreviewTrialQuestion[],
  answersByQuestionId: Readonly<Record<string, KitAnswerValue>>,
): KitQuestionSetAnswer {
  const childMap: Record<string, KitAnswerValue> = {};
  for (const child of children) {
    if (!child.questionId) continue;
    const ans = answersByQuestionId[child.questionId];
    if (ans) childMap[child.questionId] = ans;
  }
  return { children: childMap };
}

export function isTrialLeafAnswered(
  type: PreviewTrialQuestionType,
  value: KitAnswerValue | null | undefined,
): boolean {
  if (!value || type === "QUESTION_SET") return false;
  switch (type) {
    case "SINGLE_CHOICE": {
      const v = value as KitSingleChoiceAnswer;
      return Boolean(v.optionId && v.optionId.trim().length > 0);
    }
    case "MULTIPLE_CHOICE": {
      const v = value as KitMultipleChoiceAnswer;
      return (v.optionIds ?? []).length > 0;
    }
    case "SHORT_ANSWER": {
      const v = value as KitShortAnswerAnswer;
      return (v.text ?? "").trim().length > 0;
    }
    case "CLOZE": {
      const v = value as KitClozeAnswer;
      return Object.values(v.blanks ?? {}).some((entry) => entry.trim().length > 0);
    }
    case "TRUE_FALSE": {
      const v = value as KitTrueFalseAnswer;
      return Object.values(v.statements ?? {}).some((entry) => typeof entry === "boolean");
    }
    case "MATCHING": {
      const v = value as KitMatchingAnswer;
      return Object.values(v.pairs ?? {}).some((entry) => typeof entry === "string" && entry.length > 0);
    }
    case "ESSAY": {
      const v = value as KitEssayAnswer;
      const html = v.html ?? "";
      return (v.wordCount ?? countWords(html)) > 0 || countWords(html) > 0;
    }
  }
}

export function toApiTrialAnswer(
  type: PreviewTrialQuestionType,
  value: KitAnswerValue | null | undefined,
): PreviewTrialAnswerValue | null {
  if (!isTrialLeafAnswered(type, value) || !value) return null;
  switch (type) {
    case "SINGLE_CHOICE": {
      const v = value as KitSingleChoiceAnswer;
      return v.optionId ? { type: "SINGLE_CHOICE", optionId: v.optionId } : null;
    }
    case "MULTIPLE_CHOICE": {
      const v = value as KitMultipleChoiceAnswer;
      return { type: "MULTIPLE_CHOICE", optionIds: [...(v.optionIds ?? [])] };
    }
    case "SHORT_ANSWER": {
      const v = value as KitShortAnswerAnswer;
      return { type: "SHORT_ANSWER", text: (v.text ?? "").trim() };
    }
    case "CLOZE": {
      const v = value as KitClozeAnswer;
      const blanks: Record<string, string> = {};
      for (const [k, text] of Object.entries(v.blanks ?? {})) {
        if (text.trim().length > 0) blanks[k] = text.trim();
      }
      return { type: "CLOZE", blanks };
    }
    case "TRUE_FALSE": {
      const v = value as KitTrueFalseAnswer;
      const statements: Record<string, boolean> = {};
      for (const [k, choice] of Object.entries(v.statements ?? {})) {
        if (typeof choice === "boolean") statements[k] = choice;
      }
      return { type: "TRUE_FALSE", statements };
    }
    case "MATCHING": {
      const v = value as KitMatchingAnswer;
      const pairs: Record<string, string> = {};
      for (const [k, rightId] of Object.entries(v.pairs ?? {})) {
        if (typeof rightId === "string" && rightId.length > 0) pairs[k] = rightId;
      }
      return { type: "MATCHING", pairs };
    }
    case "ESSAY": {
      const v = value as KitEssayAnswer;
      const html = v.html ?? "";
      const words = typeof v.wordCount === "number" ? v.wordCount : countWords(html);
      return { type: "ESSAY", html, wordCount: words };
    }
    case "QUESTION_SET":
      return null;
  }
}

export function toApiTrialAnswers(
  paper: PreviewTrialPaperResponse,
  answersByQuestionId: Readonly<Record<string, KitAnswerValue>>,
): Record<string, PreviewTrialAnswerValue> {
  const payload: Record<string, PreviewTrialAnswerValue> = {};
  for (const leaf of listPaperLeafQuestions(paper)) {
    const converted = toApiTrialAnswer(leaf.type, answersByQuestionId[leaf.questionId]);
    if (converted) payload[leaf.questionId] = converted;
  }
  return payload;
}

export interface ObjectiveTotals {
  earnedPoints: number;
  maxPoints: number;
  gradedCount: number;
  pendingCount: number;
}

/** Sum non-pending (objective) points for AC-22 when essays await grading (`rawScore` is null). */
export function computeObjectiveTotals(grade: PreviewGradeResponse): ObjectiveTotals {
  let earnedPoints = 0;
  let maxPoints = 0;
  let gradedCount = 0;
  let pendingCount = 0;
  for (const item of grade.items ?? []) {
    if (item.pendingReview) {
      pendingCount += 1;
    } else {
      gradedCount += 1;
      earnedPoints += item.earnedPoints ?? 0;
      maxPoints += item.maxPoints ?? 0;
    }
  }
  return {
    earnedPoints: roundPoints(earnedPoints),
    maxPoints: roundPoints(maxPoints),
    gradedCount,
    pendingCount,
  };
}

function roundPoints(n: number): number {
  return Math.round(n * 1000) / 1000;
}

export interface TrialPartScoreSummary {
  partId: string;
  partName: string;
  earnedPoints: number;
  maxPoints: number;
  hasPending: boolean;
  questionCount: number;
}

export function computePartScoreSummaries(
  paper: PreviewTrialPaperResponse,
  grade: PreviewGradeResponse,
): TrialPartScoreSummary[] {
  const byQuestionId = new Map<string, PreviewGradedItem>();
  for (const item of grade.items ?? []) {
    if (item.questionId) byQuestionId.set(item.questionId, item);
  }
  const leavesByPart = new Map<string, TrialLeafDescriptor[]>();
  for (const leaf of listPaperLeafQuestions(paper)) {
    const bucket = leavesByPart.get(leaf.partId) ?? [];
    bucket.push(leaf);
    leavesByPart.set(leaf.partId, bucket);
  }

  return [...(paper.parts ?? [])]
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
    .map((part) => {
      const partId = part.partId ?? "";
      const leaves = leavesByPart.get(partId) ?? [];
      let earned = 0;
      let max = 0;
      let hasPending = false;
      for (const leaf of leaves) {
        const g = byQuestionId.get(leaf.questionId);
        if (g?.pendingReview) {
          hasPending = true;
        } else {
          earned += g?.earnedPoints ?? 0;
          max += g?.maxPoints ?? leaf.question.points ?? 0;
        }
      }
      return {
        partId,
        partName: part.name ?? "",
        earnedPoints: roundPoints(earned),
        maxPoints: roundPoints(max > 0 ? max : (part.points ?? 0)),
        hasPending,
        questionCount: leaves.length,
      };
    });
}

export interface TrueFalseStatementEntry {
  letter: string;
  id: string;
  chosen: boolean | null;
  outcome: QuestionOutcome;
}

export function extractTrueFalseEntries(
  question: PreviewTrialQuestion | undefined,
  answer: KitAnswerValue | undefined,
  gradedItem: PreviewGradedItem | undefined,
): TrueFalseStatementEntry[] {
  const rawBody = question?.body as RawQuestionBodyShape | undefined;
  const statements = rawBody?.statements ?? [];
  const tfAnswer = (answer as KitTrueFalseAnswer | undefined)?.statements ?? {};
  const resultById = new Map<string, { answered?: boolean; matched?: boolean }>();
  for (const r of gradedItem?.gradingDetail?.statementResults ?? []) {
    if (r.id) resultById.set(r.id, r);
  }

  return statements.map((st, index) => {
    const id = st.id ?? `s-${index}`;
    const letter = String.fromCharCode(97 + index);
    const chosen = typeof tfAnswer[id] === "boolean" ? tfAnswer[id] : null;
    const detail = resultById.get(id);
    let outcome: QuestionOutcome = "BLANK";
    if (detail) {
      if (detail.answered === false || chosen === null) outcome = "BLANK";
      else outcome = detail.matched ? "CORRECT" : "WRONG";
    } else if (chosen !== null) {
      outcome = gradedItem?.outcome === "CORRECT" ? "CORRECT" : "WRONG";
    }
    return { letter, id, chosen, outcome };
  });
}

export type TrialOutcomeTone = "success" | "warning" | "danger" | "neutral";

export const TRIAL_OUTCOME_TONE: Record<PreviewTrialOutcome, TrialOutcomeTone> = {
  CORRECT: "success",
  PARTIAL: "warning",
  WRONG: "danger",
  BLANK: "neutral",
  PENDING_REVIEW: "warning",
};

export function optionLetterList(question: PreviewTrialQuestion): Map<string, string> {
  const rawBody = question.body as RawQuestionBodyShape | undefined;
  const options = rawBody?.options ?? [];
  const order = question.optionOrder ?? [];
  const byId = new Map<string, RawOptionView>();
  for (const opt of options) {
    if (opt.id) byId.set(opt.id, opt);
  }
  const orderedIds: string[] = [];
  for (const id of order) {
    if (byId.has(id) && !orderedIds.includes(id)) orderedIds.push(id);
  }
  for (const opt of options) {
    if (opt.id && !orderedIds.includes(opt.id)) orderedIds.push(opt.id);
  }
  const letters = new Map<string, string>();
  orderedIds.forEach((id, idx) => {
    letters.set(id, String.fromCharCode(65 + idx));
  });
  return letters;
}

export function matchingLabelMaps(question: PreviewTrialQuestion): {
  leftLabels: Map<string, string>;
  rightLabels: Map<string, string>;
} {
  const rawBody = question.body as RawQuestionBodyShape | undefined;
  const left = rawBody?.left ?? [];
  const right = rawBody?.right ?? [];
  const order = question.optionOrder ?? [];
  const rightById = new Map<string, RawSideView>();
  for (const item of right) {
    if (item.id) rightById.set(item.id, item);
  }
  const orderedRightIds: string[] = [];
  for (const id of order) {
    if (rightById.has(id) && !orderedRightIds.includes(id)) orderedRightIds.push(id);
  }
  for (const item of right) {
    if (item.id && !orderedRightIds.includes(item.id)) orderedRightIds.push(item.id);
  }

  const leftLabels = new Map<string, string>();
  left.forEach((item, idx) => {
    if (item.id) leftLabels.set(item.id, String(idx + 1));
  });
  const rightLabels = new Map<string, string>();
  orderedRightIds.forEach((id, idx) => {
    rightLabels.set(id, String.fromCharCode(65 + idx));
  });
  return { leftLabels, rightLabels };
}
