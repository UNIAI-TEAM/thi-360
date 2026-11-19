import { type ComponentType, createElement, type ReactElement } from "react";
import type { SubjectPanelProps } from "../components/exam-structure-subject-panel";
import type { RawInputStore } from "./raw-input-store";
import type { SubjectFormKind } from "./registry";

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

/** Dạng đã có màn vẽ chưa; chưa có thì view vẽ MD, không để môn trống. */
export function hasSubjectFormRenderer(kind: SubjectFormKind): boolean {
  return renderers[kind] !== undefined;
}

/** Vẽ màn của dạng đã đăng ký. Thành phần cố định để tra màn trong lúc vẽ không tạo thành phần mới ở mỗi lần render của view. */
export function SubjectFormScreen({ kind, ...props }: SubjectFormRendererProps & { kind: SubjectFormKind }): ReactElement | null {
  const component = renderers[kind];
  return component ? createElement(component, props) : null;
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
