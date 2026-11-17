import type { Editor } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import { CROSS_DIAGRAM_NODE } from "../../extensions";

/** Sơ đồ lai đang được chọn khi hộp mở lại để sửa (spec 4.9). */
export interface SelectedCross {
  pos: number;
  /** `html` của node: innerHTML của `figure.cross`. */
  html: string;
}

export function selectedCross(editor: Editor): SelectedCross | null {
  const { selection } = editor.state;
  if (!(selection instanceof NodeSelection) || selection.node.type.name !== CROSS_DIAGRAM_NODE) return null;
  const html = selection.node.attrs.html;
  return { pos: selection.from, html: typeof html === "string" ? html : "" };
}

/**
 * Sửa sơ đồ đang chọn (đổi `html` tại chỗ) hoặc chèn sơ đồ mới. Sơ đồ chỉ đứng ở cấp đầu (spec 3.3): con trỏ nằm sâu
 * hơn đoạn cấp đầu (ô bảng, mục danh sách) thì chèn sau khối cấp đầu chứa nó; đang chọn một node khác thì chèn ngay
 * sau node đó.
 */
export function upsertCross(editor: Editor, html: string, existing: SelectedCross | null): void {
  if (existing) {
    editor
      .chain()
      .focus()
      .command(({ tr }) => {
        tr.setNodeMarkup(existing.pos, undefined, { html });
        return true;
      })
      .run();
    return;
  }
  const content = { type: CROSS_DIAGRAM_NODE, attrs: { html } };
  const { selection } = editor.state;
  if (selection instanceof NodeSelection) {
    editor.chain().focus().insertContentAt(selection.to, content).run();
  } else if (selection.$from.depth > 1) {
    editor.chain().focus().insertContentAt(selection.$from.after(1), content).run();
  } else {
    editor.chain().focus().insertContent(content).run();
  }
}
