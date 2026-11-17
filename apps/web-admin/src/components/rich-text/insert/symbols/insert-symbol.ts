import type { Editor, JSONContent } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import type { SymbolDef } from "./symbol-data";

/** Nội dung chèn của một ký hiệu: chữ thường, đoạn chỉ số dưới/trên mang mark `subscript`/`superscript` thật. */
export function symbolContent(entry: SymbolDef): JSONContent[] {
  return entry.parts.map((part): JSONContent => {
    if (typeof part === "string") return { type: "text", text: part };
    if ("sub" in part) return { type: "text", text: part.sub, marks: [{ type: "subscript" }] };
    return { type: "text", text: part.sup, marks: [{ type: "superscript" }] };
  });
}

/**
 * Chèn ký hiệu tại con trỏ (thay vùng chọn chữ, nếu có). Không gọi `focus()`: hộp vẫn mở sau mỗi lần bấm (spec 4.4) và
 * giữ focus của nó. Đang chọn một node (hình, công thức) thì chèn ngay sau node đó, không thay nó. Chữ gõ tiếp sau một
 * ký hiệu kết thúc bằng chỉ số trên/dưới không được thừa kế chỉ số đó.
 */
export function insertSymbol(editor: Editor, entry: SymbolDef): void {
  const content = symbolContent(entry);
  const { selection } = editor.state;
  const chain = editor.chain();
  if (selection instanceof NodeSelection) chain.insertContentAt(selection.to, content);
  else chain.insertContent(content);
  chain.unsetMark("subscript").unsetMark("superscript").run();
}
