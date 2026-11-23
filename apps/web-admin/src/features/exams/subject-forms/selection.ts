import { rendersReviewPanel } from "../allocation";
import { findSection, sectionGroups } from "../components/exam-structure-draft";
import type { ExamDetail, ExamPart, ExamSection } from "../types";
import { fitsForm, type FitReason } from "./fit";
import { subjectFormFor } from "./registry";

export interface SubjectFormSelection {
  form: "REVIEW" | "MATRIX" | "ROWS" | "DEFAULT";
  /** Hiện dòng chữ "đang dùng thiết lập đầy đủ" (chỉ ở bước 3). */
  notice: boolean;
  reason?: FitReason;
}

export type SubjectFormPins = Readonly<Record<string, SubjectFormSelection>>;

const DEFAULT_FORM: SubjectFormSelection = { form: "DEFAULT", notice: false };

export function selectSubjectForm(
  exam: Pick<ExamDetail, "kind" | "buildMode">,
  section: ExamSection,
  draft: readonly ExamPart[],
): SubjectFormSelection {
  if (exam.buildMode === "REVIEW") return rendersReviewPanel(section, true) ? { form: "REVIEW", notice: false } : DEFAULT_FORM;
  if (!findSection(draft, section.id)) return DEFAULT_FORM;
  const entry = subjectFormFor(exam.kind, section.subjectCode);
  if (!entry) return DEFAULT_FORM;
  const fit = fitsForm(section, entry, { kindCode: exam.kind, subjectCode: section.subjectCode });
  return fit.fits ? { form: entry.form, notice: false } : { form: "DEFAULT", notice: true, reason: fit.reason };
}

export function selectAllSubjectForms(exam: Pick<ExamDetail, "kind" | "buildMode">, draft: readonly ExamPart[]): SubjectFormPins {
  const pins: Record<string, SubjectFormSelection> = {};
  for (const part of draft) {
    for (const group of sectionGroups(part)) {
      for (const section of group.sections) pins[section.id] = selectSubjectForm(exam, section, draft);
    }
  }
  return pins;
}

/** Biểu mẫu đã ghim của môn; môn chưa có ghim (mục mới xuất hiện sau khi nạp lại dữ liệu) dùng MD không dòng chữ. */
export function pinnedForm(pins: SubjectFormPins, sectionId: string): SubjectFormSelection {
  return pins[sectionId] ?? DEFAULT_FORM;
}
