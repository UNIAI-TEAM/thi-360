import { focusMapidRule, mapidRuleAnchor } from "../mapid-rules";
import type { SubjectFormSelection } from "./selection";

export interface MatrixField {
  sectionId: string;
  name: string;
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

export const MATRIX_DEFAULT_FIELD = "STRUCTURE";

export const MATRIX_MAPID_RULES_FIELD = "MAPID_RULES";

const MAPID_RULE_ISSUE_CODES = new Set(["MAPID_RULE_CONFLICT", "MAPID_RULE_GROUP_MISSING"]);

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
