import type { SubjectFormSelection } from "./selection";

/**
 * Điều hướng theo biểu mẫu (FTM R-15, plan B1). `?block=`, bấm lỗi kiểm tra, bấm dòng tóm tắt và đích của lỗi chặn lưu đều
 * đi qua đây: biểu mẫu được chọn theo môn cha cho cả môn và nhóm.
 *
 * - MT: mở môn và đưa focus tới ô (`data-form-field` = `field.name`); không mở editor nhóm đầy đủ.
 * - DS: mở môn và mở dòng (`data-form-row` = `blockId`).
 * - MD, ON: đích cũ (editor nhóm, hoặc màn bài ôn của môn).
 */

/** Ô của MT mà đích trỏ tới. CR-2 thêm tên ô cho phần của mình (Nguồn chùm...), CR-2H thêm Quy tắc MAPID của HSA. */
export interface MatrixField {
  sectionId: string;
  name: string;
  /**
   * Nhóm mà đích trỏ tới (`?block=`, bấm lỗi hay tóm tắt của một nhóm): màn MT gắn `data-form-block="<blockId>"` lên ô của vai trò nhóm đó
   * (Trắc nghiệm, Số chùm, Điền đáp án). Không có ô của nhóm thì dùng ô `name`.
   */
  blockId?: string;
}

/** Đích điều hướng: ô của MT, dòng của DS, hoặc nhóm ở màn nhóm cũ (MD, ON). */
export type FormTarget =
  | { kind: "MATRIX_FIELD"; field: MatrixField }
  | { kind: "ROW"; blockId: string }
  | { kind: "LEGACY_BLOCK"; blockId: string };

/** Nơi cần tới: nhóm (kèm môn chứa nó), hoặc một lỗi mang môn, nhóm và tên ô nếu có. */
export interface FormTargetRef {
  sectionId: string;
  blockId?: string | null;
  field?: string | null;
}

/** Tên ô mặc định của MT khi lỗi không nêu ô: khối Cấu trúc câu (R-3). */
export const MATRIX_DEFAULT_FIELD = "STRUCTURE";

/**
 * Đích của một nơi cần tới theo biểu mẫu đã ghim của môn chứa nó. `null`: không có ô hay dòng cụ thể, chỉ mở môn
 * (lỗi cấp môn ở DS, hoặc ở MD/ON không có nhóm).
 */
export function formTarget(selection: SubjectFormSelection, ref: FormTargetRef): FormTarget | null {
  if (selection.form === "MATRIX") {
    return {
      kind: "MATRIX_FIELD",
      field: { sectionId: ref.sectionId, name: ref.field ?? MATRIX_DEFAULT_FIELD, ...(ref.field || !ref.blockId ? {} : { blockId: ref.blockId }) },
    };
  }
  if (!ref.blockId) return null;
  return selection.form === "ROWS" ? { kind: "ROW", blockId: ref.blockId } : { kind: "LEGACY_BLOCK", blockId: ref.blockId };
}

/** Phần tử nhận focus của đích trong vùng biểu mẫu, hoặc `null` khi biểu mẫu chưa vẽ ô đó. */
export function findFormTargetElement(root: ParentNode, target: FormTarget): HTMLElement | null {
  if (target.kind === "LEGACY_BLOCK") return null;
  if (target.kind === "MATRIX_FIELD") {
    const own = target.field.blockId ? root.querySelector<HTMLElement>(`[data-form-block="${CSS.escape(target.field.blockId)}"]`) : null;
    return own ?? root.querySelector<HTMLElement>(`[data-form-field="${CSS.escape(target.field.name)}"]`);
  }
  return root.querySelector<HTMLElement>(`[data-form-row="${CSS.escape(target.blockId)}"]`);
}

const FOCUSABLE = "input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [tabindex]:not([tabindex='-1']), a[href]";

/** Đặt focus vào phần tử đích; phần tử không nhận focus thì vào điều khiển đầu tiên bên trong, không có thì vào chính nó. */
export function focusFormTarget(root: ParentNode, target: FormTarget): boolean {
  const element = findFormTargetElement(root, target);
  if (!element) return false;
  const control = element.matches(FOCUSABLE) ? element : (element.querySelector<HTMLElement>(FOCUSABLE) ?? element);
  if (control === element && !element.matches(FOCUSABLE)) element.tabIndex = -1;
  control.focus();
  return true;
}
