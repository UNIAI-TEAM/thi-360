import { questionHtmlText } from "@thi360/ui/rich-text";
import type { CombinationOption, ExamKindOption } from "@/features/content/types";
import type { ReloadPartRow } from "./components/reload-structure-dialog";
import {
  type ExamInfoFormValues,
  kindOptionFor,
  partNeedsSubject,
  type ScopeSelection,
  scopeTemplate,
  yearsDiffer,
} from "./create-exam-schema";
import type { ExamDetail, ExamTemplate, UpdateExamInfoRequest } from "./types";

export function examToFormValues(exam: ExamDetail): ExamInfoFormValues {
  return {
    name: exam.name,
    code: exam.code,
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    timing: exam.timing,
    sharedDuration: exam.timing === "SHARED" ? exam.durationMinutes : 0,
    maxScore: exam.maxScore,
    buildMode: exam.scope === "SUBJECT" ? exam.buildMode : undefined,
    yearsConfirmed: exam.yearsConfirmed,
    description: exam.description ?? "",
    instructions: exam.instructions ?? "",
  };
}

/** Kỳ thi, phạm vi, phần, môn và cách dựng bài đang lưu của bài. */
export function examScope(exam: ExamDetail): ScopeSelection {
  return {
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    buildMode: exam.scope === "SUBJECT" ? exam.buildMode : undefined,
  };
}

export function buildUpdateInfoRequest({
  exam,
  submitted,
  kinds,
  appliedTemplate,
  changed,
  maxScore,
}: {
  exam: ExamDetail;
  submitted: ExamInfoFormValues;
  kinds: readonly ExamKindOption[];
  appliedTemplate: ExamTemplate | undefined;
  changed: boolean;
  maxScore: number | null;
}): UpdateExamInfoRequest {
  return {
    name: submitted.name,
    code: submitted.code,
    kind: submitted.kind,
    scope: submitted.scope,
    partCode: submitted.scope === "PART" ? submitted.partCode : null,
    // Môn đi theo Theo môn và Theo phần của kỳ thi chọn môn trước phần (SP2E, SPT, THPTQG).
    subjectCode:
      submitted.scope === "SUBJECT" || (submitted.scope === "PART" && partNeedsSubject(kinds, submitted.kind))
        ? submitted.subjectCode || null
        : null,
    buildMode: submitted.scope === "SUBJECT" ? (submitted.buildMode ?? "STANDARD") : "STANDARD",
    durationMinutes: submitted.timing === "SHARED" ? submitted.sharedDuration : null,
    maxScore,
    yearsConfirmed: yearsDiffer(appliedTemplate ?? exam) ? submitted.yearsConfirmed : true,
    description: submitted.description.trim() || null,
    instructions: questionHtmlText(submitted.instructions).trim() ? submitted.instructions.trim() : "",
    confirmReplace: changed,
    expectedRevision: exam.revision,
  };
}

export function reloadPartRows({
  templates,
  kinds,
  subjectName,
  next,
}: {
  templates: readonly ExamTemplate[];
  kinds: readonly ExamKindOption[];
  combinations?: readonly CombinationOption[];
  partName?: (code: string) => string;
  subjectName: (code: string) => string;
  next: ScopeSelection;
}): ReloadPartRow[] {
  const nextTemplate = scopeTemplate(templates, kinds, next);
  const sharedNext = (nextTemplate?.timing ?? kindOptionFor(kinds, next.kind)?.timing) === "SHARED";
  // Bài ôn (`SUBJECT` + `REVIEW`) hiển thị tên môn đã chọn thay cho tên phần "Review" của mẫu (đồng bộ với `rowLabel`).
  const reviewSubjectName =
    next.scope === "SUBJECT" && next.buildMode === "REVIEW" && next.subjectCode ? subjectName(next.subjectCode) : undefined;
  if (nextTemplate) {
    return nextTemplate.parts.map((part) => ({
      name: reviewSubjectName ?? part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes ?? null,
      shared: sharedNext,
    }));
  }
  if (next.scope === "PART" || !next.subjectCode) return [];
  return [
    {
      name: subjectName(next.subjectCode),
      questionCount: 0,
      durationMinutes: null,
      shared: sharedNext,
    },
  ];
}
