import type { CombinationOption, ExamKindOption } from "@/features/content/types";
import type { ReloadPartRow } from "./components/reload-structure-dialog";
import {
  combinationsForKind,
  type ExamInfoFormValues,
  initialParts,
  kindOptionFor,
  partNeedsSubject,
  type ScopeSelection,
  scopeTemplate,
  yearsDiffer,
} from "./create-exam-schema";
import type { ExamDetail, ExamTemplate, UpdateExamInfoRequest } from "./types";

/**
 * Giá trị biểu mẫu Thông tin bài thi lấy từ bài đã lưu: dùng chung cho tab Thông tin chung và cho bước 1 ở chế độ sửa
 * (`/exams/new?exam=<id>`). Bài ôn nhận ra qua `buildMode` máy chủ trả (không đoán từ mẫu).
 */
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

/**
 * Yêu cầu `PATCH /exams/{id}` từ biểu mẫu Thông tin bài thi. `changed`: kỳ thi, phạm vi, phần hoặc môn đã đổi nên máy chủ
 * nạp lại cấu trúc theo mẫu mới nhất (`confirmReplace`). Cách dựng bài không có trong yêu cầu: máy chủ giữ giá trị đã lưu.
 */
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
    // Môn đi theo Theo môn và Theo phần của kỳ thi chọn môn trước phần (SP2E, SPT, THPTQG), như bước 1.
    subjectCode:
      submitted.scope === "SUBJECT" || (submitted.scope === "PART" && partNeedsSubject(kinds, submitted.kind))
        ? submitted.subjectCode || null
        : null,
    durationMinutes: submitted.timing === "SHARED" ? submitted.sharedDuration : null,
    maxScore,
    yearsConfirmed: yearsDiffer(appliedTemplate) ? submitted.yearsConfirmed : true,
    description: submitted.description.trim() || null,
    instructions: submitted.instructions.trim() || null,
    confirmReplace: changed,
    expectedRevision: exam.revision,
  };
}

/**
 * Các phần mà phạm vi mới sẽ nạp lại (hộp xác nhận), cùng mẫu với bảng xem trước (`scopeTemplate`) và với máy chủ: Theo
 * phần lấy các phần của mẫu riêng của phần hoặc phần cắt từ mẫu Theo môn (SP2E, SPT, THPTQG); phạm vi khác dựng như bước 1.
 */
export function reloadPartRows({
  templates,
  kinds,
  combinations,
  partName,
  subjectName,
  next,
}: {
  templates: readonly ExamTemplate[];
  kinds: readonly ExamKindOption[];
  combinations: readonly CombinationOption[];
  partName: (code: string) => string;
  subjectName: (code: string) => string;
  next: ScopeSelection;
}): ReloadPartRow[] {
  const nextTemplate = scopeTemplate(templates, kinds, next);
  const sharedNext = (nextTemplate?.timing ?? kindOptionFor(kinds, next.kind)?.timing) === "SHARED";
  if (next.scope === "PART") {
    return (nextTemplate?.parts ?? []).map((part) => ({
      name: part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes ?? null,
      shared: sharedNext,
    }));
  }
  return initialParts({
    templates,
    kind: next.kind,
    scope: next.scope,
    partCode: next.partCode,
    combinations: combinationsForKind(combinations, next.kind),
    scopeName: partName,
    template: nextTemplate ?? null,
    subjectCode: next.subjectCode,
    subjectName,
  }).map((part) => ({
    name: part.name,
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes,
    shared: sharedNext,
  }));
}
