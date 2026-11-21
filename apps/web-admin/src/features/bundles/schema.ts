import { z } from "zod";

import { integerOf } from "./format";
import { BUNDLE_RESULT_VIEWS, type BundleDetailResponse, type BundleResultView, type CreateBundleRequest, type UpdateBundleRequest } from "./types";

export const BUNDLE_LIMITS = {
  code: 50,
  name: 200,
  summary: 300,
  description: 4000,
  benefitNote: 1000,
  itemsMin: 2,
  itemsMax: 12,
  attemptsMin: 1,
  attemptsMax: 100,
} as const;

export interface BundleFormValues {
  examKindItemId: string;
  code: string;
  name: string;
  summary: string;
  description: string;
  examIds: string[];
  listPriceVnd: string;
  attemptsPerExam: string;
  benefitAiExplanation: boolean;
  benefitStrengthWeakness: boolean;
  benefitPractice: boolean;
  benefitNote: string;
  resultView: BundleResultView;
}

export interface BundleValidationMessages {
  examKindRequired: string;
  codeRequired: string;
  codeMax: string;
  nameRequired: string;
  nameMax: string;
  summaryMax: string;
  descriptionMax: string;
  itemsRange: string;
  priceInvalid: string;
  attemptsRange: string;
  benefitNoteMax: string;
}

export function buildBundleFormSchema(messages: BundleValidationMessages) {
  return z.object({
    examKindItemId: z.string().trim().min(1, messages.examKindRequired),
    code: z
      .string()
      .trim()
      .min(1, messages.codeRequired)
      .max(BUNDLE_LIMITS.code, messages.codeMax),
    name: z
      .string()
      .trim()
      .min(1, messages.nameRequired)
      .max(BUNDLE_LIMITS.name, messages.nameMax),
    summary: z.string().max(BUNDLE_LIMITS.summary, messages.summaryMax),
    description: z.string().max(BUNDLE_LIMITS.description, messages.descriptionMax),
    examIds: z
      .array(z.string())
      .min(BUNDLE_LIMITS.itemsMin, messages.itemsRange)
      .max(BUNDLE_LIMITS.itemsMax, messages.itemsRange),
    listPriceVnd: z
      .string()
      .refine((value) => value.trim() === "" || integerOf(value.trim()) !== null, messages.priceInvalid),
    attemptsPerExam: z.string().refine((value) => {
      const trimmed = value.trim();
      if (trimmed === "") return true;
      const num = integerOf(trimmed);
      return (
        num !== null &&
        num >= BUNDLE_LIMITS.attemptsMin &&
        num <= BUNDLE_LIMITS.attemptsMax
      );
    }, messages.attemptsRange),
    benefitAiExplanation: z.boolean(),
    benefitStrengthWeakness: z.boolean(),
    benefitPractice: z.boolean(),
    benefitNote: z.string().max(BUNDLE_LIMITS.benefitNote, messages.benefitNoteMax),
    resultView: z.enum(BUNDLE_RESULT_VIEWS),
  });
}

export function createInitialBundleFormValues(defaultExamKindItemId = ""): BundleFormValues {
  return {
    examKindItemId: defaultExamKindItemId,
    code: "",
    name: "",
    summary: "",
    description: "",
    examIds: [],
    listPriceVnd: "",
    attemptsPerExam: "",
    benefitAiExplanation: false,
    benefitStrengthWeakness: false,
    benefitPractice: false,
    benefitNote: "",
    resultView: "SCORE_AND_ANSWERS",
  };
}

export function detailToBundleFormValues(detail: BundleDetailResponse): BundleFormValues {
  return {
    examKindItemId: detail.examKindItemId,
    code: detail.code,
    name: detail.name,
    summary: detail.summary ?? "",
    description: detail.description ?? "",
    examIds: [...detail.items]
      .sort((a, b) => a.position - b.position)
      .map((item) => item.examId),
    listPriceVnd: detail.listPriceVnd != null ? String(detail.listPriceVnd) : "",
    attemptsPerExam: detail.attemptsPerExam != null ? String(detail.attemptsPerExam) : "",
    benefitAiExplanation: detail.benefits.aiExplanation,
    benefitStrengthWeakness: detail.benefits.strengthWeakness,
    benefitPractice: detail.benefits.practice,
    benefitNote: detail.benefits.note ?? "",
    resultView: detail.resultView,
  };
}

export function buildCreateBundleRequest(values: BundleFormValues): CreateBundleRequest {
  const listPriceVnd = integerOf(values.listPriceVnd.trim());
  const attemptsPerExam = integerOf(values.attemptsPerExam.trim());
  const summary = values.summary.trim();
  const description = values.description.trim();
  const benefitNote = values.benefitNote.trim();

  return {
    examKindItemId: values.examKindItemId,
    code: values.code.trim(),
    name: values.name.trim(),
    ...(summary ? { summary } : {}),
    ...(description ? { description } : {}),
    examIds: values.examIds,
    ...(listPriceVnd !== null ? { listPriceVnd } : {}),
    ...(attemptsPerExam !== null ? { attemptsPerExam } : {}),
    benefitAiExplanation: values.benefitAiExplanation,
    benefitStrengthWeakness: values.benefitStrengthWeakness,
    benefitPractice: values.benefitPractice,
    ...(benefitNote ? { benefitNote } : {}),
    resultView: values.resultView,
  };
}

export function buildUpdateBundleRequest(
  values: BundleFormValues,
  expectedRevision: number,
): UpdateBundleRequest {
  const listPriceVnd = integerOf(values.listPriceVnd.trim());
  const attemptsPerExam = integerOf(values.attemptsPerExam.trim());

  return {
    expectedRevision,
    examKindItemId: values.examKindItemId,
    code: values.code.trim(),
    name: values.name.trim(),
    summary: values.summary.trim(),
    description: values.description.trim(),
    examIds: values.examIds,
    ...(listPriceVnd !== null ? { listPriceVnd } : {}),
    ...(attemptsPerExam !== null ? { attemptsPerExam } : {}),
    benefitAiExplanation: values.benefitAiExplanation,
    benefitStrengthWeakness: values.benefitStrengthWeakness,
    benefitPractice: values.benefitPractice,
    benefitNote: values.benefitNote.trim(),
    resultView: values.resultView,
  };
}
