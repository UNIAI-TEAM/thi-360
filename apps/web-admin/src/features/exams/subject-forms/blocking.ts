import { sectionGroups } from "../components/exam-structure-draft";
import type { ExamDetail, ExamPart, ExamSection } from "../types";
import { matrixBlockingRule } from "./matrix-blocking";
import type { RawInputEntry } from "./raw-input-store";
import type { SubjectFormPins } from "./selection";
import { rowFormBlockingRule } from "./row-blocking";

export interface BlockingIssue {
  code: string;
  sectionId: string;
  /** `null`: lỗi của cả môn. */
  blockId: string | null;
  /** Tên ô để đưa focus tới (`data-form-field`); bỏ trống thì dùng ô mặc định của biểu mẫu. */
  field: string | null;
}

interface FormBlockingContext {
  kindCode: string;
  form: "MATRIX" | "ROWS";
}

/** Một luật chặn lưu: nhận một môn đang dùng MT/DS và trả các lỗi của môn đó. */
export type FormBlockingRule = (section: ExamSection, context: FormBlockingContext) => BlockingIssue[];

const FORM_BLOCKING_RULES: readonly FormBlockingRule[] = [matrixBlockingRule, rowFormBlockingRule];

interface FormBlockingInput {
  exam: Pick<ExamDetail, "kind" | "buildMode">;
  draft: readonly ExamPart[];
  pinnedForms: SubjectFormPins;
  rawInputs: readonly RawInputEntry[];
}

export function formBlockingIssues({ exam, draft, pinnedForms, rawInputs }: FormBlockingInput): BlockingIssue[] {
  const issues: BlockingIssue[] = [];
  for (const part of draft) {
    for (const group of sectionGroups(part)) {
      for (const section of group.sections) {
        const form = pinnedForms[section.id]?.form;
        if (form !== "MATRIX" && form !== "ROWS") continue;
        const context: FormBlockingContext = { kindCode: exam.kind, form };
        for (const rule of FORM_BLOCKING_RULES) issues.push(...rule(section, context));
        for (const entry of rawInputs) {
          if (entry.sectionId === section.id) issues.push({ code: entry.code, sectionId: section.id, blockId: entry.blockId, field: entry.field });
        }
      }
    }
  }
  return issues;
}
