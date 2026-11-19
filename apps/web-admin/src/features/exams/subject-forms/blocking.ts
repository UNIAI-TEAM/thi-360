import { sectionGroups } from "../components/exam-structure-draft";
import type { ExamDetail, ExamPart, ExamSection } from "../types";
import type { RawInputEntry } from "./raw-input-store";
import type { SubjectFormPins } from "./selection";

/**
 * Lỗi chặn lưu của biểu mẫu MT/DS (FTM R-15, plan B1). Tính trên **bản nháp của mọi môn** cộng trạng thái nhập thô, không trên
 * component đang mở: môn chưa mở hay đã đóng vẫn được xét. Chỉ xét môn có biểu mẫu ghim là MT/DS; môn MD và bài ôn không bị
 * xét (dữ liệu của chúng giữ hành vi hôm nay).
 */
export interface BlockingIssue {
  code: string;
  sectionId: string;
  /** `null`: lỗi của cả môn. */
  blockId: string | null;
  /** Tên ô để đưa focus tới (`data-form-field`); bỏ trống thì dùng ô mặc định của biểu mẫu. */
  field: string | null;
}

export interface FormBlockingContext {
  kindCode: string;
  form: "MATRIX" | "ROWS";
}

/** Một luật chặn lưu: nhận một môn đang dùng MT/DS và trả các lỗi của môn đó. */
export type FormBlockingRule = (section: ExamSection, context: FormBlockingContext) => BlockingIssue[];

/**
 * Danh sách luật của sản phẩm. CR-2 (`matrix-blocking.ts`) và CR-3 mỗi task thêm một phần tử trỏ tới tệp luật của mình; chưa
 * có luật nào thì chỉ còn trạng thái nhập thô chặn lưu.
 */
export const FORM_BLOCKING_RULES: readonly FormBlockingRule[] = [];

const testRules: FormBlockingRule[] = [];

/** Chỗ cắm kiểm thử: thêm một luật cho tới khi gọi hàm trả về (sản phẩm không dùng). */
export function registerFormBlockingRuleForTest(rule: FormBlockingRule): () => void {
  testRules.push(rule);
  return () => {
    const index = testRules.indexOf(rule);
    if (index >= 0) testRules.splice(index, 1);
  };
}

export interface FormBlockingInput {
  exam: Pick<ExamDetail, "kind" | "buildMode">;
  draft: readonly ExamPart[];
  pinnedForms: SubjectFormPins;
  rawInputs: readonly RawInputEntry[];
}

/**
 * Lỗi chặn lưu theo thứ tự bản nháp (phần → môn); mỗi môn: lỗi của luật rồi lỗi nhập thô. Mục nhập thô của môn không còn trong
 * bản nháp hoặc không dùng MT/DS không tính (không có chỗ để sửa).
 */
export function formBlockingIssues({ exam, draft, pinnedForms, rawInputs }: FormBlockingInput): BlockingIssue[] {
  const issues: BlockingIssue[] = [];
  for (const part of draft) {
    for (const group of sectionGroups(part)) {
      for (const section of group.sections) {
        const form = pinnedForms[section.id]?.form;
        if (form !== "MATRIX" && form !== "ROWS") continue;
        const context: FormBlockingContext = { kindCode: exam.kind, form };
        for (const rule of [...FORM_BLOCKING_RULES, ...testRules]) issues.push(...rule(section, context));
        for (const entry of rawInputs) {
          if (entry.sectionId === section.id) issues.push({ code: entry.code, sectionId: section.id, blockId: entry.blockId, field: entry.field });
        }
      }
    }
  }
  return issues;
}
