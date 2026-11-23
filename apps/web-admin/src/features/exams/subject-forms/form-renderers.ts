import { type ComponentType, createElement, type ReactElement } from "react";
import type { SubjectPanelProps } from "../components/exam-structure-subject-panel";
import { MatrixForm } from "./matrix-form";
import type { RawInputStore } from "./raw-input-store";
import type { SubjectFormKind } from "./registry";
import { RowListForm } from "./row-list-form";

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
