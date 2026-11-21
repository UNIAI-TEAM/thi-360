import type {
  AnswerFormat,
  AnswerValue,
  ClozeBlank,
  EssayAnswer,
  KitQuestion,
  MatchingAnswer,
  MatchingPair,
  MultipleChoiceRule,
  PartResult,
  QuestionBody,
  QuestionOutcome,
  QuestionReview,
  QuestionSetAnswer,
  TrueFalseMethod,
} from "@thi360/ui/question";
import type {
  AttemptReviewEssayFeedback,
  AttemptReviewItem,
  AttemptReviewPart,
  AttemptReviewResponse,
} from "./api";

export interface ChildEssayFeedbackEntry {
  id: string;
  displayNo: number;
  feedback: AttemptReviewEssayFeedback;
}

export interface ReviewItemGroup {
  id: string;
  partId?: string;
  partCode?: string;
  displayNo: number;
  rangeFrom?: number;
  rangeTo?: number;
  outcome: QuestionOutcome;
  question: KitQuestion;
  children: KitQuestion[];
  value: AnswerValue | null;
  review: QuestionReview;
  essayFeedback: AttemptReviewEssayFeedback | null;
  childEssayFeedbacks: ChildEssayFeedbackEntry[];
}

export interface MappedAttemptReview {
  parts: AttemptReviewPart[];
  includeSolution: boolean;
  items: ReviewItemGroup[];
}

const VALID_ANSWER_FORMATS = new Set<AnswerFormat>([
  "INTEGER",
  "DECIMAL",
  "FRACTION",
  "TEXT",
  "CHEM_FORMULA",
  "CHEM_EQUATION",
]);

function resolveAnswerFormat(fmt: string | undefined): AnswerFormat | null {
  if (fmt && VALID_ANSWER_FORMATS.has(fmt as AnswerFormat)) {
    return fmt as AnswerFormat;
  }
  return null;
}

function resolveScoringRule(rule: string | undefined): MultipleChoiceRule | undefined {
  if (rule === "ALL_OR_NOTHING" || rule === "PARTIAL") {
    return rule;
  }
  return undefined;
}

function resolveTrueFalseMethod(method: string | undefined): TrueFalseMethod | undefined {
  if (method === "PER_STATEMENT" || method === "COUNT_TABLE") {
    return method;
  }
  return undefined;
}

function buildQuestionBody(item: AttemptReviewItem): QuestionBody {
  const type = item.questionType ?? "SINGLE_CHOICE";
  const body = item.body;
  const expected = item.expectedAnswer;
  const detail = item.gradingDetail;

  switch (type) {
    case "SINGLE_CHOICE": {
      const expectedIds = expected?.expectedOptionIds ?? [];
      const expectedSet = new Set(expectedIds);
      const hasExpected = expectedIds.length > 0;
      const options = (body?.options ?? []).map((opt, index) => {
        const id = opt.id ?? `opt-${index}`;
        return {
          id,
          content: opt.content ?? "",
          ...(hasExpected ? { correct: expectedSet.has(id) } : {}),
        };
      });
      return {
        type: "SINGLE_CHOICE",
        options,
        shuffleOptions: Boolean(item.optionOrder && item.optionOrder.length > 0),
      };
    }

    case "MULTIPLE_CHOICE": {
      const expectedIds = expected?.expectedOptionIds ?? [];
      const expectedSet = new Set(expectedIds);
      const hasExpected = expectedIds.length > 0;
      const options = (body?.options ?? []).map((opt, index) => {
        const id = opt.id ?? `opt-${index}`;
        return {
          id,
          content: opt.content ?? "",
          ...(hasExpected ? { correct: expectedSet.has(id) } : {}),
        };
      });
      return {
        type: "MULTIPLE_CHOICE",
        options,
        scoringRule: resolveScoringRule(body?.scoringRule ?? detail?.scoringRule),
        shuffleOptions: Boolean(item.optionOrder && item.optionOrder.length > 0),
      };
    }

    case "SHORT_ANSWER": {
      return {
        type: "SHORT_ANSWER",
        answers: expected?.acceptedTexts ?? [],
        inputHint: body?.inputHint ?? null,
        answerFormat: resolveAnswerFormat(body?.answerFormat),
      };
    }

    case "CLOZE": {
      const keyByBlank = new Map<string, string[]>();
      for (const key of expected?.clozeKeys ?? []) {
        if (key.blankId) {
          keyByBlank.set(key.blankId, key.acceptedTexts ?? []);
        }
      }
      const blanks: ClozeBlank[] = (body?.blanks ?? []).map((blank, index) => {
        const id = blank.id ?? `blank-${index}`;
        return {
          id,
          answers: keyByBlank.get(id) ?? [],
        };
      });
      return {
        type: "CLOZE",
        passage: body?.passage ?? "",
        blanks,
      };
    }

    case "TRUE_FALSE": {
      const keyByStatement = new Map<string, boolean>();
      for (const key of expected?.trueFalseKeys ?? []) {
        if (key.statementId && typeof key.expected === "boolean") {
          keyByStatement.set(key.statementId, key.expected);
        }
      }
      const statements = (body?.statements ?? []).map((stmt, index) => {
        const id = stmt.id ?? `stmt-${index}`;
        return {
          id,
          content: stmt.content ?? "",
          ...(keyByStatement.has(id) ? { answer: keyByStatement.get(id) } : {}),
        };
      });
      return {
        type: "TRUE_FALSE",
        statements,
        scoringMethod: resolveTrueFalseMethod(detail?.scoringMethod ?? body?.scoringRule),
      };
    }

    case "MATCHING": {
      const left = (body?.left ?? []).map((side, index) => ({
        id: side.id ?? `left-${index}`,
        content: side.content ?? "",
      }));
      const right = (body?.right ?? []).map((side, index) => ({
        id: side.id ?? `right-${index}`,
        content: side.content ?? "",
      }));
      const pairs: MatchingPair[] = (expected?.matchingKeys ?? [])
        .filter((pair): pair is { leftId: string; rightId: string } => Boolean(pair.leftId && pair.rightId))
        .map((pair) => ({
          leftId: pair.leftId,
          rightId: pair.rightId,
        }));
      return {
        type: "MATCHING",
        left,
        right,
        ...(pairs.length > 0 ? { pairs } : {}),
        reuseRight: body?.reuseRight ?? detail?.reuseRight,
      };
    }

    case "ESSAY": {
      return {
        type: "ESSAY",
        wordLimit: body?.wordLimit ?? null,
      };
    }

    case "QUESTION_SET": {
      return {
        type: "QUESTION_SET",
        passage: body?.passage ?? "",
        blankNumberStart: body?.blankNumberStart,
      };
    }
  }
}

function mapStudentAnswer(item: AttemptReviewItem): AnswerValue | null {
  const sa = item.studentAnswer;
  if (!sa) return null;

  switch (item.questionType) {
    case "SINGLE_CHOICE": {
      const val = sa as { optionId?: string | null };
      return { optionId: val.optionId ?? null };
    }
    case "MULTIPLE_CHOICE": {
      const val = sa as { optionIds?: string[] };
      return { optionIds: val.optionIds ?? [] };
    }
    case "SHORT_ANSWER": {
      const val = sa as { text?: string };
      return { text: val.text ?? "" };
    }
    case "CLOZE": {
      const val = sa as { blanks?: Record<string, string> };
      return { blanks: val.blanks ?? {} };
    }
    case "TRUE_FALSE": {
      const val = sa as { statements?: Record<string, boolean> };
      return { statements: val.statements ?? {} };
    }
    case "MATCHING": {
      const val = sa as { pairs?: Record<string, string | null> };
      return { pairs: val.pairs ?? {} };
    }
    case "ESSAY": {
      const val = sa as { html?: string; wordCount?: number };
      return {
        html: val.html ?? "",
        wordCount: val.wordCount ?? item.gradingDetail?.wordCount ?? 0,
      };
    }
    default:
      return null;
  }
}

function buildPartResults(item: AttemptReviewItem, studentValue: AnswerValue | null): Record<string, PartResult> | undefined {
  const detail = item.gradingDetail;
  if (!detail) return undefined;

  const map: Record<string, PartResult> = {};

  if (detail.blankResults && detail.blankResults.length > 0) {
    for (const blank of detail.blankResults) {
      if (!blank.id) continue;
      map[blank.id] = {
        key: blank.id,
        outcome: blank.outcome ?? "BLANK",
        earned: blank.pointsEarned ?? null,
        max: blank.pointsMax ?? null,
      };
    }
    return map;
  }

  if (detail.statementResults && detail.statementResults.length > 0) {
    for (const stmt of detail.statementResults) {
      if (!stmt.id) continue;
      const outcome: QuestionOutcome = stmt.matched ? "CORRECT" : stmt.answered ? "WRONG" : "BLANK";
      map[stmt.id] = {
        key: stmt.id,
        outcome,
        earned: null,
      };
    }
    return map;
  }

  if (detail.pairResults && detail.pairResults.length > 0) {
    const studentPairs = (studentValue as MatchingAnswer | null)?.pairs ?? {};
    for (const pair of detail.pairResults) {
      if (!pair.leftId) continue;
      const outcome: QuestionOutcome = pair.matched
        ? "CORRECT"
        : studentPairs[pair.leftId]
          ? "WRONG"
          : "BLANK";
      map[pair.leftId] = {
        key: pair.leftId,
        outcome,
        earned: pair.pointsEarned ?? null,
      };
    }
    return map;
  }

  return undefined;
}

function buildLeafReview(
  item: AttemptReviewItem,
  studentValue: AnswerValue | null,
  includeSolution: boolean,
): QuestionReview {
  const solutionHtml = includeSolution ? (item.solution ?? null) : null;
  const gradingDetail = buildPartResults(item, studentValue);

  const review: QuestionReview = {
    outcome: item.outcome ?? null,
    earned: item.earnedPoints ?? null,
    max: item.pointsMax ?? 1,
    showAnswers: true,
    solutionHtml,
    ...(gradingDetail ? { gradingDetail } : {}),
  };

  if (item.questionType === "ESSAY") {
    const essayVal = studentValue as EssayAnswer | null;
    review.essay = {
      state: item.outcome === "PENDING_REVIEW" ? "PENDING" : "GRADED",
      wordCount: essayVal?.wordCount ?? item.gradingDetail?.wordCount,
      comment: item.essayFeedback?.comment ?? null,
    };
  }

  return review;
}

function toKitQuestion(item: AttemptReviewItem, index: number): KitQuestion {
  return {
    id: item.itemId ?? `item-${item.displayNo ?? item.ord ?? index}`,
    displayNo: item.displayNo ?? item.ord ?? index + 1,
    points: item.pointsMax ?? 1,
    stem: item.content ?? "",
    body: buildQuestionBody(item),
    ...(item.optionOrder && item.optionOrder.length > 0 ? { optionOrder: item.optionOrder } : {}),
  };
}

function sortByOrdAndDisplay(a: AttemptReviewItem, b: AttemptReviewItem): number {
  const ordDiff = (a.ord ?? Number.MAX_SAFE_INTEGER) - (b.ord ?? Number.MAX_SAFE_INTEGER);
  if (ordDiff !== 0) return ordDiff;
  return (a.displayNo ?? Number.MAX_SAFE_INTEGER) - (b.displayNo ?? Number.MAX_SAFE_INTEGER);
}

export function mapAttemptReview(response: AttemptReviewResponse): MappedAttemptReview {
  const includeSolution = response.resultView === "FULL_SOLUTION";
  const parts = [...(response.parts ?? [])].sort(
    (a, b) => (a.ord ?? Number.MAX_SAFE_INTEGER) - (b.ord ?? Number.MAX_SAFE_INTEGER),
  );

  const rawItems = response.items ?? [];
  const rawChildrenByParent = new Map<string, AttemptReviewItem[]>();
  const topLevelItems: AttemptReviewItem[] = [];

  for (const item of rawItems) {
    if (item.parentItemId) {
      const list = rawChildrenByParent.get(item.parentItemId) ?? [];
      list.push(item);
      rawChildrenByParent.set(item.parentItemId, list);
    } else {
      topLevelItems.push(item);
    }
  }

  topLevelItems.sort(sortByOrdAndDisplay);

  const mappedItems: ReviewItemGroup[] = [];

  for (let idx = 0; idx < topLevelItems.length; idx++) {
    const item = topLevelItems[idx]!;
    if (item.outcome === "PENDING_REVIEW") {
      continue;
    }

    if (item.questionType === "QUESTION_SET") {
      const rawChildren = (item.itemId ? rawChildrenByParent.get(item.itemId) : undefined) ?? [];
      const sortedRawChildren = [...rawChildren].sort(sortByOrdAndDisplay);
      const visibleChildren = sortedRawChildren.filter((child) => child.outcome !== "PENDING_REVIEW");

      if (sortedRawChildren.length > 0 && visibleChildren.length === 0) {
        continue;
      }

      const childQuestions: KitQuestion[] = [];
      const childAnswers: Record<string, AnswerValue> = {};
      const childReviews: Record<string, QuestionReview> = {};
      const childEssayFeedbacks: ChildEssayFeedbackEntry[] = [];

      visibleChildren.forEach((child, cIdx) => {
        const kitChild = toKitQuestion(child, cIdx);
        const childVal = mapStudentAnswer(child);
        const childRev = buildLeafReview(child, childVal, includeSolution);
        childQuestions.push(kitChild);
        if (childVal) {
          childAnswers[kitChild.id] = childVal;
        }
        childReviews[kitChild.id] = childRev;
        if (child.essayFeedback?.criteria && child.essayFeedback.criteria.length > 0) {
          childEssayFeedbacks.push({
            id: kitChild.id,
            displayNo: kitChild.displayNo,
            feedback: child.essayFeedback,
          });
        }
      });

      const parentKit = toKitQuestion(item, idx);
      if (parentKit.body.type === "QUESTION_SET") {
        parentKit.body.childIds = childQuestions.map((c) => c.id);
      }

      const setValue: QuestionSetAnswer | null =
        Object.keys(childAnswers).length > 0 ? { children: childAnswers } : null;

      const parentReview: QuestionReview = {
        outcome: item.outcome ?? null,
        earned: item.earnedPoints ?? null,
        max: item.pointsMax ?? 1,
        showAnswers: true,
        solutionHtml: includeSolution ? (item.solution ?? null) : null,
        children: childReviews,
      };

      const childDisplayNos = childQuestions.map((c) => c.displayNo).filter((n) => n > 0);
      const rangeFrom = childDisplayNos.length > 0 ? Math.min(...childDisplayNos) : parentKit.displayNo;
      const rangeTo = childDisplayNos.length > 0 ? Math.max(...childDisplayNos) : parentKit.displayNo;

      mappedItems.push({
        id: parentKit.id,
        partId: item.partId,
        partCode: item.partCode,
        displayNo: parentKit.displayNo,
        ...(rangeFrom !== rangeTo ? { rangeFrom, rangeTo } : {}),
        outcome: item.outcome ?? "BLANK",
        question: parentKit,
        children: childQuestions,
        value: setValue,
        review: parentReview,
        essayFeedback: null,
        childEssayFeedbacks,
      });
      continue;
    }

    const kitQuestion = toKitQuestion(item, idx);
    const studentValue = mapStudentAnswer(item);
    const questionReview = buildLeafReview(item, studentValue, includeSolution);

    mappedItems.push({
      id: kitQuestion.id,
      partId: item.partId,
      partCode: item.partCode,
      displayNo: kitQuestion.displayNo,
      outcome: item.outcome ?? "BLANK",
      question: kitQuestion,
      children: [],
      value: studentValue,
      review: questionReview,
      essayFeedback: item.essayFeedback ?? null,
      childEssayFeedbacks: [],
    });
  }

  return {
    parts,
    includeSolution,
    items: mappedItems,
  };
}

export function getItemsForPart(
  mapped: MappedAttemptReview,
  selectedPart: AttemptReviewPart | undefined,
): ReviewItemGroup[] {
  if (!selectedPart) {
    return mapped.items;
  }
  return mapped.items.filter((item) => {
    if (selectedPart.partId && item.partId) {
      return selectedPart.partId === item.partId;
    }
    if (selectedPart.partCode && item.partCode) {
      return selectedPart.partCode === item.partCode;
    }
    return false;
  });
}

export function computePaletteSummary(items: readonly ReviewItemGroup[]) {
  let correct = 0;
  let partial = 0;
  let wrong = 0;
  let blank = 0;
  for (const item of items) {
    switch (item.outcome) {
      case "CORRECT":
        correct += 1;
        break;
      case "PARTIAL":
        partial += 1;
        break;
      case "WRONG":
        wrong += 1;
        break;
      case "BLANK":
      default:
        blank += 1;
        break;
    }
  }
  return { correct, partial, wrong, blank };
}
