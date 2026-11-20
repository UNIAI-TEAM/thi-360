import { focusMapidRule, mapidRuleAnchor } from "../mapid-rules";
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
  /** Quy tắc MAPID đích của HSA MT (`data-field="mapid-rule-<id>"`). */
  ruleId?: string;
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
  ruleId?: string | null;
  issueCode?: string | null;
}

/** Tên ô mặc định của MT khi lỗi không nêu ô: khối Cấu trúc câu (R-3). */
export const MATRIX_DEFAULT_FIELD = "STRUCTURE";

/** Tên vùng Quy tắc MAPID của HSA MT (FTM B9, CR-2H AC-10). */
export const MATRIX_MAPID_RULES_FIELD = "MAPID_RULES";

const MAPID_RULE_ISSUE_CODES = new Set(["MAPID_RULE_CONFLICT", "MAPID_RULE_GROUP_MISSING"]);

/**
 * Đích của một nơi cần tới theo biểu mẫu đã ghim của môn chứa nó. `null`: không có ô hay dòng cụ thể, chỉ mở môn
 * (lỗi cấp môn ở DS, hoặc ở MD/ON không có nhóm).
 */
export function formTarget(selection: SubjectFormSelection, ref: FormTargetRef): FormTarget | null {
  if (selection.form === "MATRIX") {
    const mapidTarget = Boolean(ref.ruleId) || (ref.issueCode ? MAPID_RULE_ISSUE_CODES.has(ref.issueCode) : false);
    const name = ref.field ?? (mapidTarget ? MATRIX_MAPID_RULES_FIELD : MATRIX_DEFAULT_FIELD);
    return {
      kind: "MATRIX_FIELD",
      field: {
        sectionId: ref.sectionId,
        name,
        ...(ref.field || !ref.blockId || mapidTarget ? {} : { blockId: ref.blockId }),
        ...(ref.ruleId ? { ruleId: ref.ruleId } : {}),
      },
    };
  }
  if (!ref.blockId) return null;
  if (selection.form !== "ROWS") return { kind: "LEGACY_BLOCK", blockId: ref.blockId };
  return ref.field ? { kind: "ROW", blockId: ref.blockId, field: ref.field } : { kind: "ROW", blockId: ref.blockId };
}

/** Phần tử nhận focus của đích trong vùng biểu mẫu, hoặc `null` khi biểu mẫu chưa vẽ ô đó. */
export function findFormTargetElement(root: ParentNode, target: FormTarget): HTMLElement | null {
  if (target.kind === "LEGACY_BLOCK") return null;
  if (target.kind === "MATRIX_FIELD") {
    if (target.field.ruleId) {
      return (
        root.querySelector<HTMLElement>(`[data-field="${CSS.escape(mapidRuleAnchor(target.field.ruleId))}"]`) ??
        root.querySelector<HTMLElement>('[data-testid="mapid-rules-table"]') ??
        root.querySelector<HTMLElement>(`[data-form-field="${CSS.escape(MATRIX_MAPID_RULES_FIELD)}"]`)
      );
    }
    if (target.field.name === MATRIX_MAPID_RULES_FIELD) {
      return (
        root.querySelector<HTMLElement>('[data-testid="mapid-rules-table"]') ??
        root.querySelector<HTMLElement>(`[data-form-field="${CSS.escape(MATRIX_MAPID_RULES_FIELD)}"]`)
      );
    }
    const own = target.field.blockId ? root.querySelector<HTMLElement>(`[data-form-block="${CSS.escape(target.field.blockId)}"]`) : null;
    return own ?? root.querySelector<HTMLElement>(`[data-form-field="${CSS.escape(target.field.name)}"]`);
  }
  return root.querySelector<HTMLElement>(`[data-form-row="${CSS.escape(target.blockId)}"]`);
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
  if (target.kind === "MATRIX_FIELD" && (target.field.ruleId || target.field.name === MATRIX_MAPID_RULES_FIELD)) {
    if (target.field.ruleId && focusMapidRule(target.field.ruleId, root)) return true;
    const element = findFormTargetElement(root, target);
    if (!element) return false;
    const control = element.matches(`[data-form-field="${CSS.escape(MATRIX_MAPID_RULES_FIELD)}"]`)
      ? (element.querySelector<HTMLElement>(FOCUSABLE) ?? element)
      : element;
    if (!control.matches(FOCUSABLE)) control.tabIndex = -1;
    control.focus();
    return true;
  }
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
