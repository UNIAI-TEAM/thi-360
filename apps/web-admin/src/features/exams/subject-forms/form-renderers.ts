import type { ComponentType } from "react";
import type { SubjectPanelProps } from "../components/exam-structure-subject-panel";
import type { SubjectFormKind } from "./registry";
import type { RawInputStore } from "./raw-input-store";

/**
 * Chỗ gắn màn vẽ của MT và DS vào view (plan B1, B2): CR-2 đăng ký Ma trận môn, CR-3 đăng ký Danh sách dòng. Màn nhận cùng dữ
 * liệu với màn môn mặc định (không có `onSelectBlock`, `onAddBlock`: MT và DS không mở editor nhóm đầy đủ, không thêm nhóm
 * trái dạng) cộng kho nhập thô của phiên.
 *
 * Hợp đồng focus: màn gắn `data-form-field="<tên ô>"` lên ô của MT và `data-form-row="<blockId>"` lên dòng của DS; khung
 * (view) đặt focus theo đích của `navigation.ts` sau khi màn vẽ xong.
 */
export interface SubjectFormRendererProps extends Omit<SubjectPanelProps, "onSelectBlock" | "onAddBlock"> {
  rawInputs: RawInputStore;
}

const renderers: Partial<Record<SubjectFormKind, ComponentType<SubjectFormRendererProps>>> = {};

/** Màn vẽ của dạng; `undefined` khi chưa có (view vẽ MD, không để môn trống). */
export function subjectFormRenderer(kind: SubjectFormKind): ComponentType<SubjectFormRendererProps> | undefined {
  return renderers[kind];
}

/** Đăng ký màn vẽ của một dạng; trả hàm gỡ (dùng khi kiểm thử). */
export function registerSubjectFormRenderer(kind: SubjectFormKind, component: ComponentType<SubjectFormRendererProps>): () => void {
  const previous = renderers[kind];
  renderers[kind] = component;
  return () => {
    if (previous) renderers[kind] = previous;
    else delete renderers[kind];
  };
}
