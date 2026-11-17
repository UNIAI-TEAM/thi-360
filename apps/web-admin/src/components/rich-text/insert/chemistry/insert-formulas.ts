import type { Editor, JSONContent } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../../extensions";
import { isCeLatex, parseCeLatex, type CeParts } from "./ce";

/** Công thức hóa học đang được chọn khi hộp mở lại để sửa (spec 4.3). */
export interface ChemistryTarget {
  /** Vị trí và kích thước của node công thức để thay đúng nó dù vùng chọn đổi khi hộp đang mở. */
  from: number;
  to: number;
  /** `true` khi công thức đang ở dạng khối (`span.math-block`). */
  display: boolean;
  /** Ba ô của hộp tách từ `\ce{…}`. */
  parts: CeParts;
}

/** Công thức hóa học trong vùng chọn hiện tại (NodeSelection của node công thức mà `latex` bắt đầu bằng `\ce`). */
export function readChemistryTarget(editor: Editor): ChemistryTarget | null {
  const { selection } = editor.state;
  if (!(selection instanceof NodeSelection)) return null;
  const { node } = selection;
  const name = node.type.name;
  if (name !== MATH_INLINE_NODE && name !== MATH_BLOCK_NODE) return null;
  const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
  if (!isCeLatex(latex)) return null;
  const parts = parseCeLatex(latex);
  if (!parts) return null;
  return { from: selection.from, to: selection.to, display: name === MATH_BLOCK_NODE, parts };
}

export interface ChemistryInsertion {
  /** `\ce{…}` đầy đủ của phương trình. */
  equation: string;
  /** LaTeX của "Ghi sau phương trình"; rỗng thì không chèn công thức thứ hai. */
  note: string;
  display: boolean;
}

function mathContent(latex: string, display: boolean): JSONContent {
  return { type: display ? MATH_BLOCK_NODE : MATH_INLINE_NODE, attrs: { latex } };
}

/**
 * Nội dung chèn: phương trình, rồi (nếu có) "ghi sau" là một công thức cùng dòng riêng cách một khoảng trắng. Phương
 * trình dạng khối không có "cùng dòng" để nối nên ghi sau nằm ở đoạn ngay dưới nó.
 */
export function chemistryContent({ equation, note, display }: ChemistryInsertion): JSONContent | JSONContent[] {
  const main = mathContent(equation, display);
  if (!note) return main;
  const after = mathContent(note, false);
  return display ? [main, { type: "paragraph", content: [after] }] : [main, { type: "text", text: " " }, after];
}

/**
 * Chèn hoặc thay công thức hóa học. Có `target` thì thay đúng node đó; không thì chèn tại con trỏ, còn đang chọn một
 * node khác (kể cả công thức toán) thì chèn ngay sau node đó, không thay nó.
 */
export function insertChemistry(editor: Editor, insertion: ChemistryInsertion, target: ChemistryTarget | null): void {
  const content = chemistryContent(insertion);
  const chain = editor.chain().focus();
  if (target) {
    chain.insertContentAt({ from: target.from, to: target.to }, content).run();
    return;
  }
  const { selection } = editor.state;
  if (selection instanceof NodeSelection) chain.insertContentAt(selection.to, content).run();
  else chain.insertContent(content).run();
}
