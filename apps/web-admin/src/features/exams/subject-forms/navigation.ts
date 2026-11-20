import type { SubjectFormSelection } from "./selection";

/**
 * Điều hướng theo biểu mẫu (FTM R-15, plan B1). `?block=`, bấm lỗi kiểm tra, bấm dòng tóm tắt và đích của lỗi chặn lưu đều
 * đi qua đây: biểu mẫu được chọn theo môn cha cho cả môn và nhóm.
 *
 * - MT: mở môn và đưa focus tới ô (`data-form-field` = `field.name`); không mở editor nhóm đầy đủ.
 * - DS: mở môn và mở dòng (`data-form-row` = `blockId`).
 * - MD, ON: đích cũ (editor nhóm, hoặc màn bài ôn của môn).
 */

/** Ô của MT mà đích trỏ tới. CR-2 thêm tên ô cho phần của mình (Nguồn chùm, Quy tắc MAPID của HSA...). */
export interface MatrixField {
  sectionId: string;
  name: string;
}

/** Đích điều hướng: ô của MT, dòng của DS, hoặc nhóm ở màn nhóm cũ (MD, ON). */
export type FormTarget =
  | { kind: "MATRIX_FIELD"; field: MatrixField }
  | { kind: "ROW"; blockId: string; /** Ô của màn sửa dòng nhận focus (`data-form-field`); bỏ trống: tiêu đề của màn sửa. */ field?: string }
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
    return { kind: "MATRIX_FIELD", field: { sectionId: ref.sectionId, name: ref.field ?? MATRIX_DEFAULT_FIELD } };
  }
  if (!ref.blockId) return null;
  if (selection.form !== "ROWS") return { kind: "LEGACY_BLOCK", blockId: ref.blockId };
  return ref.field ? { kind: "ROW", blockId: ref.blockId, field: ref.field } : { kind: "ROW", blockId: ref.blockId };
}

/** Phần tử nhận focus của đích trong vùng biểu mẫu, hoặc `null` khi biểu mẫu chưa vẽ ô đó. */
export function findFormTargetElement(root: ParentNode, target: FormTarget): HTMLElement | null {
  if (target.kind === "LEGACY_BLOCK") return null;
  const selector = target.kind === "MATRIX_FIELD" ? `[data-form-field="${CSS.escape(target.field.name)}"]` : `[data-form-row="${CSS.escape(target.blockId)}"]`;
  return root.querySelector<HTMLElement>(selector);
}

const FOCUSABLE = "input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [tabindex]:not([tabindex='-1']), a[href]";

/**
 * Sự kiện báo dòng đích cho Danh sách dòng (R-15: mở màn sửa đúng dòng). Phát trên phần tử `data-form-row` của dòng; màn DS nhận ở
 * khung của mình, mở màn sửa dòng rồi tự đặt focus (màn sửa vẽ sau khung, nên không đợi focus của khung), và gọi `preventDefault` để
 * khung bỏ bước đặt focus vào nút của dòng.
 */
export const ROW_TARGET_EVENT = "thi360:form-row-target";

/** Đặt focus vào phần tử đích; phần tử không nhận focus thì vào điều khiển đầu tiên bên trong, không có thì vào chính nó. */
export function focusFormTarget(root: ParentNode, target: FormTarget): boolean {
  const element = findFormTargetElement(root, target);
  if (!element) return false;
  if (target.kind === "ROW") {
    const notHandled = element.dispatchEvent(new CustomEvent(ROW_TARGET_EVENT, { bubbles: true, cancelable: true, detail: { blockId: target.blockId, field: target.field ?? null } }));
    if (!notHandled) return true;
  }
  const control = element.matches(FOCUSABLE) ? element : (element.querySelector<HTMLElement>(FOCUSABLE) ?? element);
  if (control === element && !element.matches(FOCUSABLE)) element.tabIndex = -1;
  control.focus();
  return true;
}
