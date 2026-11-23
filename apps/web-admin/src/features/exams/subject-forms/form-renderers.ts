import { type ComponentType, createElement, type ReactElement } from "react";
import type { SubjectPanelProps } from "../components/exam-structure-subject-panel";
import { MatrixForm } from "./matrix-form";
import type { RawInputStore } from "./raw-input-store";
import type { SubjectFormKind } from "./registry";
import { RowListForm } from "./row-list-form";

/**
 * Màn vẽ của MT và DS gắn vào view: nhận cùng dữ liệu với màn môn mặc định (không có `onSelectBlock`, `onAddBlock`: MT và DS
 * không mở editor nhóm đầy đủ, không thêm nhóm trái dạng) cộng kho nhập thô của phiên.
 *
 * Hợp đồng focus: màn gắn `data-form-field="<tên ô>"` lên ô của MT và `data-form-row="<blockId>"` lên dòng của DS; khung
 * (view) đặt focus theo đích của `navigation.ts` sau khi màn vẽ xong.
 */
export interface SubjectFormRendererProps extends Omit<SubjectPanelProps, "onSelectBlock" | "onAddBlock"> {
  rawInputs: RawInputStore;
}

const renderers: Record<SubjectFormKind, ComponentType<SubjectFormRendererProps>> = {
  MATRIX: MatrixForm,
  ROWS: RowListForm,
};

/** Vẽ màn của dạng đã đăng ký. Thành phần cố định để tra màn trong lúc vẽ không tạo thành phần mới ở mỗi lần render của view. */
export function SubjectFormScreen({ kind, ...props }: SubjectFormRendererProps & { kind: SubjectFormKind }): ReactElement {
  return createElement(renderers[kind], props);
}
