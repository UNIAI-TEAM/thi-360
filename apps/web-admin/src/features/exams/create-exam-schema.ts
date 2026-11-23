import { z } from "zod";
import type { CatalogOption, CombinationOption, ExamKindOption } from "@/features/content/types";
import { questionHtmlText } from "@thi360/ui/rich-text";
import { isAllOmittedTemplatePart } from "./create-exam-overview";
import type {
  CreateExamRequest,
  ExamBuildMode,
  ExamKind,
  ExamOverviewRow,
  ExamScope as ApiExamScope,
  ExamTemplate,
  ExamTemplatePart,
  ExamTiming,
} from "./types";

export const EXAM_SCOPES: readonly ApiExamScope[] = ["FULL", "PART", "SUBJECT"];

export function scopesForKind(kinds: readonly ExamKindOption[], kind: ExamKind): ApiExamScope[] {
  const option = kindOptionFor(kinds, kind);
  if (!option) return ["FULL", "PART"];
  return EXAM_SCOPES.filter((scope) => option.scopes.includes(scope));
}

export const EXAM_NAME_MAX = 200;
export const EXAM_CODE_MAX = 50;
export const EXAM_DESCRIPTION_MAX = 1000;
export const COVER_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const CODE_PATTERN = /^[A-Z0-9][A-Z0-9-]*$/;

const PART_CODES = ["MATH", "LITERATURE", "SCIENCE", "ENGLISH"] as const;
type PartCode = (typeof PART_CODES)[number];

export function isPartCode(value: string): value is PartCode {
  return (PART_CODES as readonly string[]).includes(value);
}

const positiveInteger = (value: number) => Number.isInteger(value) && value > 0;


export function kindOptionFor(kinds: readonly ExamKindOption[], kind: ExamKind): ExamKindOption | undefined {
  return kinds.find((option) => option.code === kind);
}

/** Đầu vào chọn phạm vi của bước 1 và của tab Thông tin chung: kỳ thi, phạm vi, phần (Theo phần) hoặc môn (Theo môn). */
export interface ScopeSelection {
  kind: ExamKind;
  scope: ApiExamScope;
  /** Phần của bài Theo phần (`partCode` của mẫu); phạm vi khác để trống. */
  partCode: string;
  /** Môn của bài Theo môn hoặc môn chọn trước phần (SP2E, SPT Theo phần); phạm vi khác để trống. */
  subjectCode: string;
  buildMode?: ExamBuildMode;
}

export function sameSelection(a: ScopeSelection, b: ScopeSelection): boolean {
  return (
    a.kind === b.kind &&
    a.scope === b.scope &&
    a.partCode === b.partCode &&
    a.subjectCode === b.subjectCode &&
    (a.buildMode ?? "STANDARD") === (b.buildMode ?? "STANDARD")
  );
}

export function matchingTemplates(templates: readonly ExamTemplate[], selection: ScopeSelection): ExamTemplate[] {
  const buildMode = selection.scope === "SUBJECT" ? (selection.buildMode ?? "STANDARD") : null;
  return templates
    .filter(
      (template) =>
        template.kind === selection.kind &&
        template.scope === selection.scope &&
        (selection.scope !== "PART" || template.partCode === selection.partCode) &&
        (selection.scope !== "SUBJECT" || buildMode === "REVIEW" || template.subjectCode === selection.subjectCode) &&
        (selection.scope !== "PART" || !selection.subjectCode || template.subjectCode === selection.subjectCode) &&
        (buildMode === null || (template.buildMode ?? "STANDARD") === buildMode),
    )
    .sort((a, b) => (b.structureYear ?? 0) - (a.structureYear ?? 0));
}

export function latestTemplate(templates: readonly ExamTemplate[], selection: ScopeSelection): ExamTemplate | undefined {
  const matches = matchingTemplates(templates, selection);
  return matches.find((template) => template.isDefault) ?? matches[0];
}

const subjectPartCode = (order: number): string => `P${order}`;

export function scopeTemplate(
  templates: readonly ExamTemplate[],
  kinds: readonly ExamKindOption[],
  selection: ScopeSelection,
): ExamTemplate | undefined {
  if (selection.scope !== "PART" || kindOptionFor(kinds, selection.kind)?.templateBy !== "SUBJECT") {
    return latestTemplate(templates, selection);
  }
  const dedicated = latestTemplate(templates, selection);
  if (dedicated) return dedicated;
  const base = latestTemplate(templates, { ...selection, scope: "SUBJECT", partCode: "", buildMode: "STANDARD" });
  if (!base) return undefined;
  // Phần cắt từ mẫu Theo môn không mang phân đoạn hiển thị (H40: chỉ bài Toàn bài/Theo môn giữ displaySegments).
  const parts = base.parts
    .filter((part, index) => subjectPartCode(part.order ?? index + 1) === selection.partCode)
    .map((part) => {
      const cut = { ...part };
      delete cut.displaySegments;
      return cut;
    });
  const shape = { parts, maxScore: parts[0]?.maxPoints ?? parts[0]?.points ?? base.maxScore };
  if (base.timing === "SHARED") return { ...base, ...shape, durationMinutes: parts[0]?.durationMinutes ?? null };
  return { ...base, ...shape };
}

/** Số mẫu Theo môn của từng môn trong kỳ thi (để ô chọn môn nêu môn nào có mẫu). */
export function templateCountBySubject(templates: readonly ExamTemplate[], kind: ExamKind): Map<string, number> {
  const counts = new Map<string, number>();
  for (const template of templates) {
    if (template.kind !== kind || template.scope !== "SUBJECT" || !template.subjectCode) continue;
    counts.set(template.subjectCode, (counts.get(template.subjectCode) ?? 0) + 1);
  }
  return counts;
}

function defaultSubjectFor(
  templates: readonly ExamTemplate[],
  subjects: readonly CatalogOption[],
  kind: ExamKind,
  needsParts = false,
): string {
  const counts = templateCountBySubject(templates, kind);
  if (counts.size === 0) return "";
  return (
    subjects.find(
      (subject) =>
        counts.has(subject.code) && (!needsParts || subjectPartOptions(templates, kind, subject.code).length > 0),
    )?.code ?? ""
  );
}

export function findTemplate(templates: readonly ExamTemplate[], code: string | null | undefined): ExamTemplate | undefined {
  return code ? templates.find((template) => template.code === code) : undefined;
}

/** Năm cấu trúc khác năm ma trận của mẫu (SP2E Toán 2026 dùng ma trận 2025): cần chuyên gia xác nhận chỉ tiêu. */
export function yearsDiffer(template: Pick<ExamTemplate, "structureYear" | "matrixYear"> | null | undefined): boolean {
  const structureYear = template?.structureYear ?? null;
  const matrixYear = template?.matrixYear ?? null;
  return structureYear !== null && matrixYear !== null && structureYear !== matrixYear;
}

interface SubjectPartOption {
  code: string;
  name: string;
  questionCount: number;
  audio?: "REQUIRED" | "FORBIDDEN";
}

/** Điều kiện audio chung của một phần mẫu (xem `SubjectPartOption.audio`). */
export function templatePartAudio(part: ExamTemplatePart | undefined): SubjectPartOption["audio"] {
  const sections = part?.sections ?? [];
  const first = sections[0]?.audioRequirement;
  if ((first !== "REQUIRED" && first !== "FORBIDDEN") || !sections.every((section) => section.audioRequirement === first)) {
    return undefined;
  }
  return first;
}

function dedicatedPartTemplates(templates: readonly ExamTemplate[], kind: ExamKind, subjectCode: string): ExamTemplate[] {
  const codes = new Set<string>();
  for (const template of templates) {
    if (template.kind === kind && template.scope === "PART" && template.subjectCode === subjectCode && template.partCode) {
      codes.add(template.partCode);
    }
  }
  return [...codes]
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
    .flatMap((partCode) => latestTemplate(templates, { kind, scope: "PART", partCode, subjectCode }) ?? []);
}

export function subjectPartOptions(templates: readonly ExamTemplate[], kind: ExamKind, subjectCode: string): SubjectPartOption[] {
  const dedicated = dedicatedPartTemplates(templates, kind, subjectCode);
  if (dedicated.length > 0) {
    return dedicated.map((template) => ({
      code: template.partCode ?? "",
      name: template.parts[0]?.name ?? template.name,
      questionCount: template.parts[0]?.questionCount ?? 0,
    }));
  }
  const template = latestTemplate(templates, { kind, scope: "SUBJECT", partCode: "", subjectCode, buildMode: "STANDARD" });
  const hideOmitted = template?.omittedLocked === true;
  return (template?.parts ?? []).flatMap((part, index) => {
    if (hideOmitted && isAllOmittedTemplatePart(part)) return [];
    const audio = templatePartAudio(part);
    return [
      {
        code: subjectPartCode(part.order ?? index + 1),
        name: part.name,
        questionCount: part.questionCount,
        ...(audio ? { audio } : {}),
      },
    ];
  });
}

export function subjectAudioParts(templates: readonly ExamTemplate[], kind: ExamKind, subjectCode: string): SubjectPartOption[] {
  const options = subjectPartOptions(templates, kind, subjectCode);
  const split =
    options.length >= 2 &&
    options.every((option) => option.audio) &&
    options.some((option) => option.audio === "REQUIRED") &&
    options.some((option) => option.audio === "FORBIDDEN");
  return split ? options : [];
}

export function hasLockedOmittedPart(templates: readonly ExamTemplate[], kind: ExamKind, subjectCode: string): boolean {
  const template = latestTemplate(templates, { kind, scope: "SUBJECT", partCode: "", subjectCode, buildMode: "STANDARD" });
  return template?.omittedLocked === true && template.parts.some(isAllOmittedTemplatePart);
}

export function hasDedicatedParts(templates: readonly ExamTemplate[], kind: ExamKind, subjectCode: string): boolean {
  return dedicatedPartTemplates(templates, kind, subjectCode).length > 0;
}

export function partCodesForKind(
  templates: readonly ExamTemplate[],
  kinds: readonly ExamKindOption[],
  kind: ExamKind,
  subjectCode = "",
): string[] {
  if (kindOptionFor(kinds, kind)?.templateBy === "SUBJECT") {
    return subjectPartOptions(templates, kind, subjectCode).map((option) => option.code);
  }
  const codes: string[] = [];
  for (const template of templates) {
    if (template.kind !== kind || template.scope !== "PART" || !template.partCode) continue;
    if (!codes.includes(template.partCode)) codes.push(template.partCode);
  }
  return codes.length === 0 ? [...PART_CODES] : codes;
}

/** Kỳ thi `templateBy = SUBJECT` chọn môn trước phần (SP2E, SPT Theo phần); `subjectCode` bắt buộc khi `scope = PART`. */
export function partNeedsSubject(kinds: readonly ExamKindOption[], kind: ExamKind): boolean {
  return kindOptionFor(kinds, kind)?.templateBy === "SUBJECT";
}

export function resolveScope(
  templates: readonly ExamTemplate[],
  kinds: readonly ExamKindOption[],
  subjects: readonly CatalogOption[],
  current: ScopeSelection,
  change: Partial<ScopeSelection>,
): ScopeSelection {
  const kind = change.kind ?? current.kind;
  const scopes = scopesForKind(kinds, kind);
  const requested = change.scope ?? current.scope;
  const fallback = scopes.includes("FULL") ? "FULL" : scopes.includes("SUBJECT") ? "SUBJECT" : (scopes[0] ?? "FULL");
  const scope = scopes.includes(requested) ? requested : fallback;
  const needsSubject = scope !== "FULL" && partNeedsSubject(kinds, kind);
  let subjectCode = "";
  if (scope === "SUBJECT" || needsSubject) {
    subjectCode = change.subjectCode ?? current.subjectCode;
    const keep =
      subjectCode &&
      (change.subjectCode !== undefined ||
        (needsSubject && scope === "PART"
          ? subjectPartOptions(templates, kind, subjectCode).length > 0
          : templateCountBySubject(templates, kind).has(subjectCode)));
    if (!keep) subjectCode = defaultSubjectFor(templates, subjects, kind, needsSubject && scope === "PART");
  }
  let partCode = "";
  if (scope === "PART") {
    const codes = partCodesForKind(templates, kinds, kind, subjectCode);
    const wanted = change.partCode ?? current.partCode;
    partCode = codes.includes(wanted) ? wanted : (codes[0] ?? wanted ?? "");
  }
  const buildMode = scope === "SUBJECT" ? (change.buildMode ?? current.buildMode ?? "STANDARD") : undefined;
  return { kind, scope, partCode, subjectCode, buildMode };
}

interface ScopeDefaults {
  timing: ExamTiming;
  sharedDuration: number;
  maxScore: number | null;
  pointsByPart: boolean;
}

export function scopeDefaults(
  template: ExamTemplate | undefined,
  kind: ExamKindOption | undefined,
  scope: ApiExamScope,
  buildMode?: ExamBuildMode,
): ScopeDefaults {
  const timing = template?.timing ?? kind?.timing ?? "PER_PART";
  const review = scope === "SUBJECT" && buildMode === "REVIEW";
  return {
    timing,
    sharedDuration: timing === "SHARED" ? (template?.durationMinutes ?? 0) : 0,
    maxScore: review
      ? (template?.maxScore ?? null)
      : template
        ? (template.maxScore ?? kind?.maxScore ?? null)
        : scope === "PART"
          ? null
          : (kind?.maxScore ?? null),
    pointsByPart: (template?.parts ?? []).some((part) => part.points !== undefined && part.points !== null),
  };
}

export function combinationsForKind(combinations: readonly CombinationOption[], kind: ExamKind): CombinationOption[] {
  return combinations.filter((combination) => combination.examKindCode === kind);
}


export interface ExamInfoFormValues {
  name: string;
  code: string;
  kind: ExamKind;
  scope: ApiExamScope;
  partCode: string;
  subjectCode: string;
  timing: ExamTiming;
  sharedDuration: number;
  maxScore: number | null;
  buildMode?: ExamBuildMode;
  yearsConfirmed: boolean;
  description: string;
  instructions: string;
}

export interface ExamInfoSchemaMessages {
  nameRequired: string;
  nameMax: string;
  codeRequired: string;
  codeInvalid: string;
  codeMax: string;
  codeTaken: string;
  subjectRequired: string;
  kindSubjectRequired: string;
  sharedDurationPositive: string;
  maxScoreRequired: string;
  maxScorePositive: string;
  descriptionMax: string;
}

export type TakenCodes = ReadonlySet<string>;

const infoShape = (messages: ExamInfoSchemaMessages, takenCodes: TakenCodes) => ({
  name: z.string().trim().min(1, messages.nameRequired).max(EXAM_NAME_MAX, messages.nameMax),
  code: z
    .string()
    .trim()
    .min(1, messages.codeRequired)
    .max(EXAM_CODE_MAX, messages.codeMax)
    .transform((value) => value.toUpperCase())
    .refine((value) => CODE_PATTERN.test(value), messages.codeInvalid)
    .refine((value) => !takenCodes.has(value), messages.codeTaken),
  kind: z.string().min(1),
  scope: z.enum(["FULL", "PART", "SUBJECT"]),
  partCode: z.string(),
  subjectCode: z.string(),
  timing: z.enum(["PER_PART", "SHARED"]),
  sharedDuration: z.number(),
  maxScore: z.number().nullable(),
  buildMode: z.enum(["STANDARD", "REVIEW"]).optional(),
  yearsConfirmed: z.boolean(),
  description: z.string().max(EXAM_DESCRIPTION_MAX, messages.descriptionMax),
  instructions: z.string(),
});

type InfoValues = Pick<ExamInfoFormValues, "kind" | "scope" | "partCode" | "subjectCode" | "timing" | "sharedDuration" | "maxScore" | "buildMode">;

function refineInfo(
  values: InfoValues,
  context: z.RefinementCtx,
  messages: ExamInfoSchemaMessages,
  kinds: readonly ExamKindOption[],
) {
  const kind = kindOptionFor(kinds, values.kind);
  if (values.scope === "PART" && !values.partCode) {
    context.addIssue({ code: "custom", path: ["partCode"], message: messages.subjectRequired });
  }
  if (values.scope === "SUBJECT" && !values.subjectCode) {
    context.addIssue({ code: "custom", path: ["subjectCode"], message: messages.kindSubjectRequired });
  }
  if (values.timing === "SHARED" && !positiveInteger(values.sharedDuration)) {
    context.addIssue({ code: "custom", path: ["sharedDuration"], message: messages.sharedDurationPositive });
  }
  if (values.maxScore === null) {
    const review = values.scope === "SUBJECT" && values.buildMode === "REVIEW";
    if (!review && kind && kind.maxScore !== null) {
      context.addIssue({ code: "custom", path: ["maxScore"], message: messages.maxScoreRequired });
    }
  } else if (!(values.maxScore > 0)) {
    context.addIssue({ code: "custom", path: ["maxScore"], message: messages.maxScorePositive });
  }
}

export function createExamInfoSchema(
  messages: ExamInfoSchemaMessages,
  takenCodes: TakenCodes = new Set(),
  kinds: readonly ExamKindOption[] = [],
) {
  return z
    .object(infoShape(messages, takenCodes))
    .superRefine((values, context) => refineInfo(values, context, messages, kinds));
}


interface Step1RequestInput {
  name: string;
  code: string;
  selection: ScopeSelection;
  description: string;
  instructions: string;
  timing: ExamTiming;
  sharedDuration: number | null;
  maxScore: number | null;
  overview: ExamOverviewRow[];
}

export function toCreateExamRequest(input: Step1RequestInput): CreateExamRequest {
  const { selection } = input;
  return {
    name: input.name.trim(),
    code: input.code.trim().toUpperCase(),
    kind: selection.kind,
    scope: selection.scope,
    partCode: selection.scope === "PART" ? selection.partCode : null,
    subjectCode: selection.subjectCode || null,
    buildMode: selection.scope === "SUBJECT" ? (selection.buildMode ?? "STANDARD") : null,
    durationMinutes: input.timing === "SHARED" ? input.sharedDuration : null,
    maxScore: input.maxScore,
    description: input.description.trim() || null,
    instructions: questionHtmlText(input.instructions).trim() ? input.instructions.trim() : null,
    overview: input.overview,
  };
}
