import { rendersReviewPanel } from "../allocation";
import { findSection, sectionGroups } from "../components/exam-structure-draft";
import type { ExamDetail, ExamPart, ExamSection } from "../types";
import { fitsForm, type FitReason } from "./fit";
import { subjectFormFor } from "./registry";

/**
 * Chọn biểu mẫu của một môn (FTM R-1). Thứ tự:
 * 1. Bài ôn (`buildMode = REVIEW`): ON nếu `rendersReviewPanel` đúng, ngược lại MD. Không xét bảng đăng ký, không bao giờ MT/DS.
 * 2. Bài chuẩn: tra cặp; không có mục → MD, không dòng chữ.
 * 3. Có mục, cấu trúc không khớp (R-2) → MD kèm dòng chữ.
 * 4. Còn lại → dạng của mục.
 */
export interface SubjectFormSelection {
  form: "REVIEW" | "MATRIX" | "ROWS" | "DEFAULT";
  /** Hiện dòng chữ "đang dùng thiết lập đầy đủ" (chỉ ở bước 3). */
  notice: boolean;
  reason?: FitReason;
}

export type SubjectFormPins = Readonly<Record<string, SubjectFormSelection>>;

const DEFAULT_FORM: SubjectFormSelection = { form: "DEFAULT", notice: false };

/**
 * Hàm thuần trên dữ liệu đã nạp, không gọi request. `draft` là các phần của bản nháp chứa môn: môn không có trong đó (mã
 * cũ sau khi nạp lại cấu trúc) dùng MD không dòng chữ.
 */
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

/**
 * Biểu mẫu của mọi môn trong bản nháp (mục ở `sections`, `subjectPool` và các phương án), theo `section.id`. View tính một
 * lần khi mở hoặc nạp mẫu rồi ghim trong phiên (R-2): sửa bản nháp không đổi biểu mẫu.
 */
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
