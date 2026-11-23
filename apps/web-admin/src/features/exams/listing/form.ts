import { z } from "zod";
import type { ListingTerms, ResultView, UpdateListingRequest } from "./types";

export const LISTING_LIMITS = {
  summary: 300,
  description: 4000,
  objectives: 2000,
  audience: 2000,
  benefitNote: 1000,
  attemptsMin: 1,
  attemptsMax: 100,
} as const;

/** Giá trị của biểu mẫu: giá và số lượt là chuỗi để ô nhập trống được (chưa nhập là hợp lệ khi lưu nháp). */
export interface ListingFormValues {
  summary: string;
  description: string;
  objectives: string;
  audience: string;
  /** Chỉ chữ số, không dấu phân cách; hiển thị theo ngôn ngữ ở ô nhập. */
  price: string;
  attempts: string;
  benefitAiExplanation: boolean;
  benefitStrengthWeakness: boolean;
  benefitPractice: boolean;
  benefitNote: string;
  resultView: ResultView;
}

export function formValuesOf(terms: ListingTerms): ListingFormValues {
  return {
    summary: terms.summary,
    description: terms.description,
    objectives: terms.objectives,
    audience: terms.audience,
    price: terms.listPriceVnd === null ? "" : String(terms.listPriceVnd),
    attempts: terms.attemptsGranted === null ? "" : String(terms.attemptsGranted),
    benefitAiExplanation: terms.benefitAiExplanation,
    benefitStrengthWeakness: terms.benefitStrengthWeakness,
    benefitPractice: terms.benefitPractice,
    benefitNote: terms.benefitNote,
    resultView: terms.resultView,
  };
}

/** Giá nhập tay giữ lại chữ số: "249.000", "249,000" và "249 000" đều là 249000. */
export function priceDigits(text: string): string {
  return text.replace(/\D/g, "").replace(/^0+(?=\d)/, "");
}

/** Số nguyên không âm từ chuỗi chữ số; `null` khi trống hoặc vượt số nguyên an toàn. */
export function integerOf(digits: string): number | null {
  if (!/^\d+$/.test(digits)) return null;
  const value = Number(digits);
  return Number.isSafeInteger(value) ? value : null;
}

/** Các điều khoản bán đang nhập, để vẽ thẻ xem trước và so với bản đang bán. */
export function termsOfValues(values: ListingFormValues, coverAssetId: string | null): ListingTerms {
  return {
    coverAssetId,
    summary: values.summary.trim(),
    description: values.description.trim(),
    objectives: values.objectives.trim(),
    audience: values.audience.trim(),
    listPriceVnd: integerOf(values.price),
    attemptsGranted: integerOf(values.attempts),
    benefitAiExplanation: values.benefitAiExplanation,
    benefitStrengthWeakness: values.benefitStrengthWeakness,
    benefitPractice: values.benefitPractice,
    benefitNote: values.benefitNote.trim(),
    resultView: values.resultView,
  };
}

export function toListingRequest(
  values: ListingFormValues,
  coverAssetId: string | null,
  expectedRevision: number,
): UpdateListingRequest {
  return { ...termsOfValues(values, coverAssetId), expectedRevision };
}

/** Chữ lỗi của từng ô, đã dịch (`exams.listing.validation`). */
export interface ListingValidationMessages {
  summaryMax: string;
  descriptionMax: string;
  objectivesMax: string;
  audienceMax: string;
  benefitNoteMax: string;
  priceInvalid: string;
  attemptsRange: string;
}

export function listingSchema(messages: ListingValidationMessages) {
  return z.object({
    summary: z.string().max(LISTING_LIMITS.summary, messages.summaryMax),
    description: z.string().max(LISTING_LIMITS.description, messages.descriptionMax),
    objectives: z.string().max(LISTING_LIMITS.objectives, messages.objectivesMax),
    audience: z.string().max(LISTING_LIMITS.audience, messages.audienceMax),
    price: z.string().refine((value) => value === "" || integerOf(value) !== null, messages.priceInvalid),
    attempts: z.string().refine((value) => {
      if (value === "") return true;
      const count = integerOf(value);
      return count !== null && count >= LISTING_LIMITS.attemptsMin && count <= LISTING_LIMITS.attemptsMax;
    }, messages.attemptsRange),
    benefitAiExplanation: z.boolean(),
    benefitStrengthWeakness: z.boolean(),
    benefitPractice: z.boolean(),
    benefitNote: z.string().max(LISTING_LIMITS.benefitNote, messages.benefitNoteMax),
    resultView: z.enum(["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"]),
  });
}

/** Trường của máy chủ (`fieldErrors[].field`) tới ô của biểu mẫu; `coverAssetId` không có ô nên đi riêng. */
export const FORM_FIELD_OF: Readonly<Record<string, keyof ListingFormValues>> = {
  summary: "summary",
  description: "description",
  objectives: "objectives",
  audience: "audience",
  listPriceVnd: "price",
  attemptsGranted: "attempts",
  benefitNote: "benefitNote",
  resultView: "resultView",
};

/** Trường khác nhau giữa bản làm việc và bản đang bán, theo thứ tự hiện ở dải giá. */
export const LISTING_TERM_FIELDS = [
  "listPriceVnd",
  "attemptsGranted",
  "resultView",
  "benefits",
  "description",
  "summary",
  "objectives",
  "audience",
  "coverAssetId",
] as const;
export type ListingTermField = (typeof LISTING_TERM_FIELDS)[number];

export function differingTerms(working: ListingTerms, published: ListingTerms): ListingTermField[] {
  const differs: Record<ListingTermField, boolean> = {
    listPriceVnd: working.listPriceVnd !== published.listPriceVnd,
    attemptsGranted: working.attemptsGranted !== published.attemptsGranted,
    resultView: working.resultView !== published.resultView,
    benefits:
      working.benefitAiExplanation !== published.benefitAiExplanation ||
      working.benefitStrengthWeakness !== published.benefitStrengthWeakness ||
      working.benefitPractice !== published.benefitPractice ||
      working.benefitNote.trim() !== published.benefitNote.trim(),
    description: working.description.trim() !== published.description.trim(),
    summary: working.summary.trim() !== published.summary.trim(),
    objectives: working.objectives.trim() !== published.objectives.trim(),
    audience: working.audience.trim() !== published.audience.trim(),
    coverAssetId: working.coverAssetId !== published.coverAssetId,
  };
  return LISTING_TERM_FIELDS.filter((field) => differs[field]);
}
