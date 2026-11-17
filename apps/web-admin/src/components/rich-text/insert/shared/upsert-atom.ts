import type { Editor } from "@tiptap/core";
import type { Node as PMNode } from "@tiptap/pm/model";
import { NodeSelection } from "@tiptap/pm/state";

/** Node atom cấp đầu đang được chọn (NodeSelection) khi hộp mở lại để sửa; `null` khi chèn mới. */
export interface SelectedAtom {
  pos: number;
  node: PMNode;
}

export function selectedAtom(editor: Editor, typeName: string): SelectedAtom | null {
  const { selection } = editor.state;
  if (selection instanceof NodeSelection && selection.node.type.name === typeName) {
    return { pos: selection.from, node: selection.node };
  }
  return null;
}

/**
 * Sửa node atom đang chọn (thay thuộc tính tại chỗ) hoặc chèn node mới. Node cấp đầu không nằm trong ô bảng hay mục
 * danh sách (spec 3.3): con trỏ ở sâu hơn đoạn cấp đầu thì chèn sau khối cấp đầu chứa nó; đang chọn một node khác thì
 * chèn ngay sau node đó.
 */
export function upsertAtom(
  editor: Editor,
  typeName: string,
  attrs: Record<string, unknown>,
  existing: SelectedAtom | null,
): void {
  if (existing) {
    editor
      .chain()
      .focus()
      .command(({ tr }) => {
        tr.setNodeMarkup(existing.pos, undefined, attrs);
        return true;
      })
      .run();
    return;
  }
  const content = { type: typeName, attrs };
  const { selection } = editor.state;
  if (selection instanceof NodeSelection) {
    editor.chain().focus().insertContentAt(selection.to, content).run();
  } else if (selection.$from.depth > 1) {
    editor.chain().focus().insertContentAt(selection.$from.after(1), content).run();
  } else {
    editor.chain().focus().insertContent(content).run();
  }
}
