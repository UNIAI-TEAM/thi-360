import type { components } from "@thi360/api-client";
import {
  countWords,
  parseRawQuestionBody,
  readRawQuestionBody,
  type AnswerValue as KitAnswerValue,
  type ClozeAnswer as KitClozeAnswer,
  type EssayAnswer as KitEssayAnswer,
  type KitQuestion,
  type MatchingAnswer as KitMatchingAnswer,
  type MultipleChoiceAnswer as KitMultipleChoiceAnswer,
  type QuestionSetAnswer as KitQuestionSetAnswer,
  type ShortAnswerAnswer as KitShortAnswerAnswer,
  type SingleChoiceAnswer as KitSingleChoiceAnswer,
  type TrueFalseAnswer as KitTrueFalseAnswer,
} from "@thi360/ui/question";
import { rewriteQuestionHtmlAssets, sanitizeAttemptAttachmentUrl } from "./attempt-assets";

export type AttemptStateResponse = components["schemas"]["AttemptStateResponse"];
export type AttemptPartStateView = components["schemas"]["AttemptPartStateView"];
export type AttemptItemView = components["schemas"]["AttemptItemView"];
export type AttemptQuestionBody = components["schemas"]["AttemptQuestionBody"];
export type AttemptAttachmentView = components["schemas"]["AttemptAttachmentView"];
export type ApiQuestionType = NonNullable<AttemptItemView["questionType"]>;
export type ApiAnswerValue = NonNullable<AttemptItemView["answer"]>;
export type AttemptPosition = components["schemas"]["AttemptPosition"];

export type ItemCompletionStatus = "blank" | "partial" | "complete";

export interface QuestionDisplayUnit {
  id: string;
  kind: "LEAF" | "QUESTION_SET";
  parentItem: AttemptItemView;
  childItems: AttemptItemView[];
  leafItems: AttemptItemView[];
  kitQuestion: KitQuestion;
  childKitQuestions: KitQuestion[];
  attachments: AttemptAttachmentView[];
  displayStart: number;
  displayEnd: number;
}

export interface EssayLimitStatus {
  isEssay: boolean;
  wordCount: number;
  wordLimit: number | null;
  underLimit: boolean;
}

export interface ItemCompletionDetail {
  status: ItemCompletionStatus;
  filledCount: number;
  totalCount: number;
}

export function sanitizeItemAttachments(
  attachments: AttemptAttachmentView[] | undefined,
  attemptId: string,
): AttemptAttachmentView[] {
  if (!attachments || attachments.length === 0) return [];
  return attachments
    .map((att) => ({
      ...att,
      url: sanitizeAttemptAttachmentUrl(att.url, attemptId, att.assetId),
    }))
    .filter((att) => Boolean(att.url));
}

export function toKitQuestion(
  item: AttemptItemView,
  attemptId: string,
  childIds?: string[],
): KitQuestion {
  const itemId = item.itemId ?? "";
  const displayNo = item.displayNo ?? 1;
  const stem = item.content ? rewriteQuestionHtmlAssets(item.content, attemptId) : undefined;
  const body = parseRawQuestionBody(item.questionBody, {
    questionType: item.questionType,
    rewriteHtml: (html) => rewriteQuestionHtmlAssets(html, attemptId),
    childIds,
  });

  return {
    id: itemId,
    displayNo,
    points: item.points,
    stem,
    body,
    optionOrder: item.optionOrder && item.optionOrder.length > 0 ? item.optionOrder : undefined,
  };
}

export function toKitAnswer(apiAnswer: ApiAnswerValue | null | undefined): KitAnswerValue | null {
  if (!apiAnswer) return null;
  switch (apiAnswer.type) {
    case "SINGLE_CHOICE":
      return { optionId: apiAnswer.optionId ?? null } satisfies KitSingleChoiceAnswer;
    case "MULTIPLE_CHOICE":
      return { optionIds: [...(apiAnswer.optionIds ?? [])] } satisfies KitMultipleChoiceAnswer;
    case "SHORT_ANSWER":
      return { text: apiAnswer.text ?? "" } satisfies KitShortAnswerAnswer;
    case "CLOZE":
      return { blanks: { ...(apiAnswer.blanks ?? {}) } } satisfies KitClozeAnswer;
    case "TRUE_FALSE": {
      const statements: Record<string, boolean | null> = {};
      for (const [k, v] of Object.entries(apiAnswer.statements ?? {})) {
        statements[k] = v ?? null;
      }
      return { statements } satisfies KitTrueFalseAnswer;
    }
    case "MATCHING": {
      const pairs: Record<string, string | null> = {};
      for (const [k, v] of Object.entries(apiAnswer.pairs ?? {})) {
        pairs[k] = v ?? null;
      }
      return { pairs } satisfies KitMatchingAnswer;
    }
    case "ESSAY": {
      const html = apiAnswer.html ?? "";
      return {
        html,
        wordCount: typeof apiAnswer.wordCount === "number" ? apiAnswer.wordCount : countWords(html),
      } satisfies KitEssayAnswer;
    }
  }
}

export function toApiAnswer(
  questionType: ApiQuestionType,
  kitAnswer: KitAnswerValue | null | undefined,
): ApiAnswerValue | null {
  if (!kitAnswer || questionType === "QUESTION_SET") return null;

  switch (questionType) {
    case "SINGLE_CHOICE": {
      const val = kitAnswer as KitSingleChoiceAnswer;
      return val.optionId ? { type: "SINGLE_CHOICE", optionId: val.optionId } : { type: "SINGLE_CHOICE" };
    }
    case "MULTIPLE_CHOICE": {
      const val = kitAnswer as KitMultipleChoiceAnswer;
      return { type: "MULTIPLE_CHOICE", optionIds: [...(val.optionIds ?? [])] };
    }
    case "SHORT_ANSWER": {
      const val = kitAnswer as KitShortAnswerAnswer;
      return { type: "SHORT_ANSWER", text: val.text ?? "" };
    }
    case "CLOZE": {
      const val = kitAnswer as KitClozeAnswer;
      return { type: "CLOZE", blanks: { ...(val.blanks ?? {}) } };
    }
    case "TRUE_FALSE": {
      const val = kitAnswer as KitTrueFalseAnswer;
      const statements: Record<string, boolean> = {};
      for (const [k, v] of Object.entries(val.statements ?? {})) {
        if (typeof v === "boolean") {
          statements[k] = v;
        }
      }
      return { type: "TRUE_FALSE", statements };
    }
    case "MATCHING": {
      const val = kitAnswer as KitMatchingAnswer;
      const pairs: Record<string, string> = {};
      for (const [k, v] of Object.entries(val.pairs ?? {})) {
        if (typeof v === "string" && v.length > 0) {
          pairs[k] = v;
        }
      }
      return { type: "MATCHING", pairs };
    }
    case "ESSAY": {
      const val = kitAnswer as KitEssayAnswer;
      const html = val.html ?? "";
      return {
        type: "ESSAY",
        html,
        wordCount: typeof val.wordCount === "number" ? val.wordCount : countWords(html),
      };
    }
  }
}

export function buildQuestionSetKitValue(
  childItems: AttemptItemView[],
  getAnswer: (itemId: string) => ApiAnswerValue | null | undefined,
): KitQuestionSetAnswer {
  const children: Record<string, KitAnswerValue> = {};
  for (const child of childItems) {
    const id = child.itemId;
    if (!id) continue;
    const mapped = toKitAnswer(getAnswer(id));
    if (mapped) {
      children[id] = mapped;
    }
  }
  return { children };
}

export function buildQuestionUnits(
  items: AttemptItemView[] | undefined,
  attemptId: string,
): QuestionDisplayUnit[] {
  if (!items || items.length === 0) return [];

  const childrenByParent = new Map<string, AttemptItemView[]>();
  const parentIds = new Set<string>();
  for (const item of items) {
    if (item.questionType === "QUESTION_SET" && item.itemId) {
      parentIds.add(item.itemId);
    }
  }

  for (const item of items) {
    if (item.parentItemId && parentIds.has(item.parentItemId)) {
      const list = childrenByParent.get(item.parentItemId) ?? [];
      list.push(item);
      childrenByParent.set(item.parentItemId, list);
    }
  }

  const units: QuestionDisplayUnit[] = [];
  for (const item of items) {
    const itemId = item.itemId ?? "";
    if (item.parentItemId && parentIds.has(item.parentItemId)) {
      continue;
    }

    if (item.questionType === "QUESTION_SET") {
      const children = childrenByParent.get(itemId) ?? [];
      const childIds = children.map((c) => c.itemId ?? "").filter(Boolean);
      const childKits = children.map((c) => toKitQuestion(c, attemptId));
      const leafDisplays = children.map((c) => c.displayNo ?? 0).filter((n) => n > 0);
      const displayStart = leafDisplays.length > 0 ? Math.min(...leafDisplays) : (item.displayNo ?? 1);
      const displayEnd = leafDisplays.length > 0 ? Math.max(...leafDisplays) : displayStart;
      units.push({
        id: itemId,
        kind: "QUESTION_SET",
        parentItem: item,
        childItems: children,
        leafItems: children,
        kitQuestion: toKitQuestion(item, attemptId, childIds),
        childKitQuestions: childKits,
        attachments: sanitizeItemAttachments(item.attachments, attemptId),
        displayStart,
        displayEnd,
      });
    } else {
      const start = item.displayNo ?? 1;
      const span = Math.max(1, item.unitCount ?? 1);
      units.push({
        id: itemId,
        kind: "LEAF",
        parentItem: item,
        childItems: [],
        leafItems: [item],
        kitQuestion: toKitQuestion(item, attemptId),
        childKitQuestions: [],
        attachments: sanitizeItemAttachments(item.attachments, attemptId),
        displayStart: start,
        displayEnd: start + span - 1,
      });
    }
  }

  return units;
}

export function getLeafItems(items: AttemptItemView[] | undefined): AttemptItemView[] {
  if (!items || items.length === 0) return [];
  return items.filter((item) => item.questionType !== "QUESTION_SET" && Boolean(item.itemId));
}

export function getItemCompletionDetail(
  item: AttemptItemView,
  answer: ApiAnswerValue | null | undefined,
): ItemCompletionDetail {
  const rawBody = readRawQuestionBody(item.questionBody);
  const qType = item.questionType ?? (rawBody?.type as ApiQuestionType | undefined);
  if (!qType || qType === "QUESTION_SET") {
    return { status: "blank", filledCount: 0, totalCount: 0 };
  }
  if (!answer || answer.type !== qType) {
    if (qType === "CLOZE") {
      const blanks = rawBody?.blanks ?? [];
      return { status: "blank", filledCount: 0, totalCount: blanks.length };
    }
    if (qType === "TRUE_FALSE") {
      const stmts = rawBody?.statements ?? [];
      return { status: "blank", filledCount: 0, totalCount: stmts.length };
    }
    if (qType === "MATCHING") {
      const left = rawBody?.left ?? [];
      return { status: "blank", filledCount: 0, totalCount: left.length };
    }
    return { status: "blank", filledCount: 0, totalCount: 1 };
  }

  switch (answer.type) {
    case "SINGLE_CHOICE": {
      const filled = Boolean(answer.optionId && answer.optionId.trim().length > 0);
      return { status: filled ? "complete" : "blank", filledCount: filled ? 1 : 0, totalCount: 1 };
    }
    case "MULTIPLE_CHOICE": {
      const filled = (answer.optionIds ?? []).length > 0;
      return { status: filled ? "complete" : "blank", filledCount: filled ? 1 : 0, totalCount: 1 };
    }
    case "SHORT_ANSWER": {
      const filled = (answer.text ?? "").trim().length > 0;
      return { status: filled ? "complete" : "blank", filledCount: filled ? 1 : 0, totalCount: 1 };
    }
    case "CLOZE": {
      const bodyBlanks = rawBody?.blanks ?? [];
      const blankIds = bodyBlanks.map((b) => b.id ?? "").filter(Boolean);
      const blanksMap = answer.blanks ?? {};
      const total = blankIds.length > 0 ? blankIds.length : Object.keys(blanksMap).length;
      if (total === 0) return { status: "blank", filledCount: 0, totalCount: 0 };
      const filled =
        blankIds.length > 0
          ? blankIds.filter((id) => (blanksMap[id] ?? "").trim().length > 0).length
          : Object.values(blanksMap).filter((v) => v.trim().length > 0).length;
      if (filled === 0) return { status: "blank", filledCount: 0, totalCount: total };
      if (filled < total) return { status: "partial", filledCount: filled, totalCount: total };
      return { status: "complete", filledCount: filled, totalCount: total };
    }
    case "TRUE_FALSE": {
      const bodyStmts = rawBody?.statements ?? [];
      const stmtIds = bodyStmts.map((s) => s.id ?? "").filter(Boolean);
      const stmtMap = answer.statements ?? {};
      const total = stmtIds.length > 0 ? stmtIds.length : Object.keys(stmtMap).length;
      if (total === 0) return { status: "blank", filledCount: 0, totalCount: 0 };
      const filled =
        stmtIds.length > 0
          ? stmtIds.filter((id) => typeof stmtMap[id] === "boolean").length
          : Object.values(stmtMap).filter((v) => typeof v === "boolean").length;
      if (filled === 0) return { status: "blank", filledCount: 0, totalCount: total };
      if (filled < total) return { status: "partial", filledCount: filled, totalCount: total };
      return { status: "complete", filledCount: filled, totalCount: total };
    }
    case "MATCHING": {
      const bodyLeft = rawBody?.left ?? [];
      const leftIds = bodyLeft.map((l) => l.id ?? "").filter(Boolean);
      const pairsMap = answer.pairs ?? {};
      const total = leftIds.length > 0 ? leftIds.length : Object.keys(pairsMap).length;
      if (total === 0) return { status: "blank", filledCount: 0, totalCount: 0 };
      const filled =
        leftIds.length > 0
          ? leftIds.filter((id) => Boolean(pairsMap[id] && pairsMap[id].trim().length > 0)).length
          : Object.values(pairsMap).filter((v) => Boolean(v && v.trim().length > 0)).length;
      if (filled === 0) return { status: "blank", filledCount: 0, totalCount: total };
      if (filled < total) return { status: "partial", filledCount: filled, totalCount: total };
      return { status: "complete", filledCount: filled, totalCount: total };
    }
    case "ESSAY": {
      const words = countWords(answer.html ?? "");
      const filled = words > 0;
      return { status: filled ? "complete" : "blank", filledCount: filled ? 1 : 0, totalCount: 1 };
    }
  }
}

export function classifyItemCompletion(
  item: AttemptItemView,
  answer: ApiAnswerValue | null | undefined,
): ItemCompletionStatus {
  return getItemCompletionDetail(item, answer).status;
}

export function getEssayLimitStatus(
  item: AttemptItemView,
  answer: ApiAnswerValue | null | undefined,
): EssayLimitStatus {
  if (item.questionType !== "ESSAY") {
    return { isEssay: false, wordCount: 0, wordLimit: null, underLimit: false };
  }
  const essayBody = readRawQuestionBody(item.questionBody);
  const wordLimit = typeof essayBody?.wordLimit === "number" ? essayBody.wordLimit : null;
  const html = answer?.type === "ESSAY" ? (answer.html ?? "") : "";
  const wordCount = countWords(html);
  return {
    isEssay: true,
    wordCount,
    wordLimit,
    underLimit: wordLimit !== null && wordCount < wordLimit,
  };
}

export function parseLastPosition(
  raw: Record<string, unknown> | undefined | null,
): { partId?: string; itemId?: string } | null {
  if (!raw || typeof raw !== "object") return null;
  const partId = typeof raw.partId === "string" && raw.partId ? raw.partId : undefined;
  const itemId = typeof raw.itemId === "string" && raw.itemId ? raw.itemId : undefined;
  if (!partId && !itemId) return null;
  return { partId, itemId };
}

export interface ParsedChoiceRecord {
  optionCode?: string;
  subjectCodes?: string[];
}

export function parseChoiceMap(raw: Record<string, unknown> | undefined | null): ParsedChoiceRecord | null {
  if (!raw || typeof raw !== "object") return null;
  const optionCode = typeof raw.optionCode === "string" && raw.optionCode ? raw.optionCode : undefined;
  const subjectCodes = Array.isArray(raw.subjectCodes)
    ? raw.subjectCodes.filter((s): s is string => typeof s === "string" && s.length > 0)
    : undefined;
  if (!optionCode && (!subjectCodes || subjectCodes.length === 0)) return null;
  return { optionCode, subjectCodes };
}
