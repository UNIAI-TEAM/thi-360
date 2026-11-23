import type {
  AnswerFormat,
  ChoiceOption,
  ClozeBlank,
  EssayCriterion,
  MatchingItem,
  MatchingPair,
  MultipleChoiceRule,
  QuestionBody,
  QuestionType,
  TrueFalseMethod,
  TrueFalseStatement,
} from "./types";

export interface RawChoiceOptionShape {
  id?: string | null;
  content?: string | null;
  correct?: boolean | null;
}

export interface RawClozeBlankShape {
  id?: string | null;
  answers?: readonly (string | null | undefined)[] | null;
  points?: number | null;
}

export interface RawTrueFalseStatementShape {
  id?: string | null;
  content?: string | null;
  answer?: boolean | null;
  points?: number | null;
}

export interface RawMatchingItemShape {
  id?: string | null;
  content?: string | null;
}

export interface RawMatchingPairShape {
  leftId?: string | null;
  rightId?: string | null;
  points?: number | null;
}

export interface RawEssayCriterionShape {
  id?: string | null;
  description?: string | null;
  points?: number | null;
}

export interface RawQuestionBodyShape {
  type?: string | null;
  options?: readonly RawChoiceOptionShape[] | null;
  shuffleOptions?: boolean | null;
  scoringRule?: string | null;
  answers?: readonly (string | null | undefined)[] | null;
  inputHint?: string | null;
  answerFormat?: string | null;
  passage?: string | null;
  blanks?: readonly RawClozeBlankShape[] | null;
  blankNumberStart?: number | null;
  childIds?: readonly (string | null | undefined)[] | null;
  statements?: readonly RawTrueFalseStatementShape[] | null;
  scoringMethod?: string | null;
  scoreTable?: readonly (number | null | undefined)[] | null;
  left?: readonly RawMatchingItemShape[] | null;
  right?: readonly RawMatchingItemShape[] | null;
  pairs?: readonly RawMatchingPairShape[] | null;
  reuseRight?: boolean | null;
  gradingGuide?: string | null;
  rubric?: readonly RawEssayCriterionShape[] | null;
  sampleAnswer?: string | null;
  wordLimit?: number | null;
}

export interface RawAttachmentLike {
  mime?: string | null;
  mimeType?: string | null;
  mediaType?: string | null;
  kind?: string | null;
}

export interface ParseRawQuestionBodyOptions {
  /** Explicit question type; falls back to `rawBody.type`, then `"SINGLE_CHOICE"`. */
  questionType?: string | null;
  /** Optional HTML rewriter applied to rich-text fields (e.g. student attempt asset rewriting). */
  rewriteHtml?: (html: string | undefined | null) => string;
  /** Explicit child question IDs for `QUESTION_SET` (overrides `rawBody.childIds` when provided). */
  childIds?: readonly string[];
  /** Explicit `blankNumberStart` fallback when `rawBody.blankNumberStart` is absent. */
  blankNumberStart?: number;
  /**
   * When `true` (admin preview), retains answer keys, rubrics, scoring tables, and shuffle flags.
   * Defaults to `false` so student and trial mappers never leak answer keys.
   */
  includeAnswers?: boolean;
  /** Top-level option fallback used by admin `PreviewQuestion` when `rawBody` omits `options`/`statements`. */
  fallbackOptions?: readonly RawChoiceOptionShape[] | null;
  /** Top-level single-answer fallback used by admin `PreviewQuestion` for `SHORT_ANSWER` / `CLOZE`. */
  fallbackAnswer?: string | null;
  /** Top-level passage fallback used by admin `PreviewQuestion` when `rawBody` omits `passage`. */
  fallbackPassage?: string | null;
  /** Top-level essay rubric override/fallback used by admin `PreviewQuestion`. */
  fallbackRubric?: readonly RawEssayCriterionShape[] | null;
}

const VALID_QUESTION_TYPES = new Set<QuestionType>([
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "SHORT_ANSWER",
  "CLOZE",
  "QUESTION_SET",
  "TRUE_FALSE",
  "MATCHING",
  "ESSAY",
]);

const VALID_ANSWER_FORMATS = new Set<AnswerFormat>([
  "INTEGER",
  "DECIMAL",
  "FRACTION",
  "TEXT",
  "CHEM_FORMULA",
  "CHEM_EQUATION",
]);

export function readRawQuestionBody(raw: unknown): RawQuestionBodyShape | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return undefined;
  }
  return raw as RawQuestionBodyShape;
}

export function asQuestionType(raw: string | undefined | null): QuestionType | undefined {
  if (!raw) return undefined;
  if (raw === "MCQ_SINGLE") return "SINGLE_CHOICE";
  if (raw === "MCQ_MULTI") return "MULTIPLE_CHOICE";
  if (VALID_QUESTION_TYPES.has(raw as QuestionType)) {
    return raw as QuestionType;
  }
  return undefined;
}

export function asAnswerFormat(raw: string | undefined | null): AnswerFormat | null {
  if (raw && VALID_ANSWER_FORMATS.has(raw as AnswerFormat)) {
    return raw as AnswerFormat;
  }
  return null;
}

export function asScoringRule(raw: string | undefined | null): MultipleChoiceRule | undefined {
  if (raw === "ALL_OR_NOTHING" || raw === "PARTIAL") return raw;
  return undefined;
}

export function asTrueFalseMethod(raw: string | undefined | null): TrueFalseMethod | undefined {
  if (raw === "PER_STATEMENT" || raw === "COUNT_TABLE") return raw;
  return undefined;
}

export function isAudioMime(mime: string | undefined | null): boolean {
  return Boolean(mime?.trim().toLowerCase().startsWith("audio/"));
}

export function filterAudioAttachments<T extends RawAttachmentLike>(
  attachments: readonly T[] | null | undefined,
): T[] {
  if (!attachments || attachments.length === 0) return [];
  return attachments.filter(
    (att) =>
      isAudioMime(att.mime) ||
      isAudioMime(att.mimeType) ||
      isAudioMime(att.mediaType) ||
      att.kind?.trim().toUpperCase() === "AUDIO",
  );
}

function mapChoiceOptions(
  sourceOptions: readonly RawChoiceOptionShape[],
  fallbackOptions: readonly RawChoiceOptionShape[] | null | undefined,
  includeAnswers: boolean,
  html: (value: string | undefined | null) => string,
): ChoiceOption[] {
  return sourceOptions.map((opt) => {
    const id = opt.id ?? "";
    const content = html(opt.content);
    if (!includeAnswers) {
      return { id, content };
    }
    const fallbackCorrect = fallbackOptions?.find((f) => f.id === opt.id)?.correct;
    const correct = opt.correct ?? fallbackCorrect;
    return {
      id,
      content,
      ...(typeof correct === "boolean" ? { correct } : {}),
    };
  });
}

export function parseRawQuestionBody(
  rawBody: unknown,
  options?: ParseRawQuestionBodyOptions,
): QuestionBody {
  const raw = readRawQuestionBody(rawBody);
  const qType =
    asQuestionType(options?.questionType) ?? asQuestionType(raw?.type) ?? "SINGLE_CHOICE";
  const rawBodyType = asQuestionType(raw?.type);
  const matchingRaw = raw && (!raw.type || rawBodyType === qType) ? raw : undefined;
  const includeAnswers = Boolean(options?.includeAnswers);
  const html = options?.rewriteHtml ?? ((value: string | undefined | null) => value ?? "");

  switch (qType) {
    case "SINGLE_CHOICE": {
      const sourceOptions = matchingRaw?.options ?? options?.fallbackOptions ?? [];
      return {
        type: "SINGLE_CHOICE",
        options: mapChoiceOptions(
          sourceOptions,
          options?.fallbackOptions,
          includeAnswers,
          html,
        ),
        ...(includeAnswers && typeof matchingRaw?.shuffleOptions === "boolean"
          ? { shuffleOptions: matchingRaw.shuffleOptions }
          : {}),
      };
    }
    case "MULTIPLE_CHOICE": {
      const sourceOptions = matchingRaw?.options ?? options?.fallbackOptions ?? [];
      return {
        type: "MULTIPLE_CHOICE",
        options: mapChoiceOptions(
          sourceOptions,
          options?.fallbackOptions,
          includeAnswers,
          html,
        ),
        scoringRule: asScoringRule(matchingRaw?.scoringRule),
        ...(includeAnswers && typeof matchingRaw?.shuffleOptions === "boolean"
          ? { shuffleOptions: matchingRaw.shuffleOptions }
          : {}),
      };
    }
    case "SHORT_ANSWER": {
      const rawAnswers = (matchingRaw?.answers ?? []).filter(
        (entry): entry is string => typeof entry === "string",
      );
      const answers = includeAnswers
        ? rawAnswers.length > 0
          ? rawAnswers
          : options?.fallbackAnswer
            ? [options.fallbackAnswer]
            : []
        : [];
      return {
        type: "SHORT_ANSWER",
        answers,
        inputHint: matchingRaw?.inputHint ?? null,
        answerFormat: asAnswerFormat(matchingRaw?.answerFormat),
      };
    }
    case "CLOZE": {
      const passageSource =
        matchingRaw?.passage !== undefined && matchingRaw.passage !== null
          ? matchingRaw.passage
          : (options?.fallbackPassage ?? "");
      const blanks: ClozeBlank[] = matchingRaw?.blanks
        ? matchingRaw.blanks.map((blank) => ({
            id: blank.id ?? "",
            answers: includeAnswers
              ? (blank.answers ?? []).filter(
                  (entry): entry is string => typeof entry === "string",
                )
              : [],
            ...(includeAnswers && typeof blank.points === "number"
              ? { points: blank.points }
              : {}),
          }))
        : includeAnswers && options?.fallbackAnswer
          ? [{ id: "blank-1", answers: [options.fallbackAnswer] }]
          : [];
      const blankNumberStart =
        typeof matchingRaw?.blankNumberStart === "number"
          ? matchingRaw.blankNumberStart
          : options?.blankNumberStart;
      return {
        type: "CLOZE",
        passage: html(passageSource),
        blanks,
        ...(typeof blankNumberStart === "number" ? { blankNumberStart } : {}),
      };
    }
    case "TRUE_FALSE": {
      const statements: TrueFalseStatement[] = matchingRaw?.statements
        ? matchingRaw.statements.map((st) => ({
            id: st.id ?? "",
            content: html(st.content),
            ...(includeAnswers
              ? {
                  ...(typeof st.answer === "boolean" ? { answer: st.answer } : {}),
                  points: typeof st.points === "number" ? st.points : null,
                }
              : {}),
          }))
        : includeAnswers && options?.fallbackOptions
          ? options.fallbackOptions.map((opt) => ({
              id: opt.id ?? "",
              content: html(opt.content),
              ...(typeof opt.correct === "boolean" ? { answer: opt.correct } : {}),
              points: null,
            }))
          : [];
      const scoringMethod = asTrueFalseMethod(matchingRaw?.scoringMethod);
      const scoreTable = Array.isArray(matchingRaw?.scoreTable)
        ? matchingRaw.scoreTable.filter((entry): entry is number => typeof entry === "number")
        : undefined;
      return {
        type: "TRUE_FALSE",
        statements,
        ...(includeAnswers && scoringMethod ? { scoringMethod } : {}),
        ...(includeAnswers && scoreTable ? { scoreTable } : {}),
      };
    }
    case "MATCHING": {
      const left: MatchingItem[] = (matchingRaw?.left ?? []).map((side) => ({
        id: side.id ?? "",
        content: html(side.content),
      }));
      const right: MatchingItem[] = (matchingRaw?.right ?? []).map((side) => ({
        id: side.id ?? "",
        content: html(side.content),
      }));
      const pairs: MatchingPair[] | undefined = includeAnswers
        ? (matchingRaw?.pairs ?? []).map((pair) => ({
            leftId: pair.leftId ?? "",
            rightId: pair.rightId ?? "",
            ...(typeof pair.points === "number" ? { points: pair.points } : {}),
          }))
        : undefined;
      return {
        type: "MATCHING",
        left,
        right,
        ...(pairs !== undefined ? { pairs } : {}),
        reuseRight: matchingRaw?.reuseRight ?? false,
      };
    }
    case "ESSAY": {
      const wordLimit =
        typeof matchingRaw?.wordLimit === "number" ? matchingRaw.wordLimit : null;
      if (!includeAnswers) {
        return {
          type: "ESSAY",
          wordLimit,
        };
      }
      const rawRubric = options?.fallbackRubric ?? matchingRaw?.rubric ?? [];
      const rubric: EssayCriterion[] = rawRubric.map((criterion) => ({
        id: criterion.id ?? "",
        description: options?.rewriteHtml
          ? html(criterion.description)
          : (criterion.description ?? ""),
        points: typeof criterion.points === "number" ? criterion.points : 0,
      }));
      return {
        type: "ESSAY",
        ...(matchingRaw?.gradingGuide !== undefined
          ? {
              gradingGuide:
                matchingRaw.gradingGuide !== null
                  ? html(matchingRaw.gradingGuide)
                  : null,
            }
          : {}),
        rubric,
        ...(matchingRaw?.sampleAnswer !== undefined
          ? {
              sampleAnswer:
                matchingRaw.sampleAnswer !== null
                  ? html(matchingRaw.sampleAnswer)
                  : null,
            }
          : {}),
        wordLimit,
      };
    }
    case "QUESTION_SET":
    default: {
      const passageSource =
        matchingRaw?.passage !== undefined && matchingRaw.passage !== null
          ? matchingRaw.passage
          : (options?.fallbackPassage ?? "");
      const blankNumberStart =
        typeof matchingRaw?.blankNumberStart === "number"
          ? matchingRaw.blankNumberStart
          : options?.blankNumberStart;
      const childIds = options?.childIds
        ? [...options.childIds]
        : matchingRaw?.childIds
          ? matchingRaw.childIds.filter(
              (entry): entry is string => typeof entry === "string",
            )
          : undefined;
      return {
        type: "QUESTION_SET",
        passage: html(passageSource),
        ...(typeof blankNumberStart === "number" ? { blankNumberStart } : {}),
        ...(childIds !== undefined ? { childIds } : {}),
      };
    }
  }
}
