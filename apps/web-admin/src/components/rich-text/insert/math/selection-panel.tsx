"use client";

import { Button, Input } from "@thi360/ui";
import { Node as PMNode, type NodeType } from "@tiptap/pm/model";
import { NodeSelection } from "@tiptap/pm/state";
import { Pencil, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../../extensions";
import type { SelectionPanelProps } from "../registry";
import { DisplayToggle } from "./display-toggle";
import { checkLatex } from "./latex-check";

/** Vị trí của node công thức `type` mang `latex` gần `near` nhất (sau khi thay node, chỗ cũ có thể dời do tách đoạn). */
function findMathPos(doc: PMNode, type: NodeType, latex: string, near: number): number {
  let found = -1;
  doc.descendants((node, pos) => {
    if (node.type === type && node.attrs.latex === latex && (found === -1 || Math.abs(pos - near) < Math.abs(found - near))) {
      found = pos;
    }
  });
  return found;
}

/**
 * Khung "Công thức đang chọn" dưới thanh công cụ (bản vẽ `SoanThaoTongQuan`, spec 4.1): mã LaTeX sửa trực tiếp,
 * Sửa (mở lại hộp công thức), Trong dòng/Dòng riêng, Xóa công thức. Đổi kiểu hiển thị thay node mà vẫn giữ công thức
 * được chọn, nên khung không biến mất sau khi bấm.
 */
export default function MathSelectionPanel({ editor, target, openDialog }: SelectionPanelProps) {
  const t = useTranslations("richText.insert.math.panel");
  const node = target instanceof PMNode ? target : null;
  if (!node) return null;
  const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
  const display = node.type.name === MATH_BLOCK_NODE;
  const invalid = checkLatex(latex, display).status === "error";

  // Ô nhập đọc thẳng `latex` của node đang chọn: sửa là cập nhật thuộc tính node và giữ nguyên vùng chọn node đó,
  // nếu không khung biến mất giữa lúc đang gõ.
  function applyLatex(next: string) {
    const { selection } = editor.state;
    if (!(selection instanceof NodeSelection)) return;
    const pos = selection.from;
    editor
      .chain()
      .command(({ tr }) => {
        tr.setNodeMarkup(pos, undefined, { ...selection.node.attrs, latex: next });
        tr.setSelection(NodeSelection.create(tr.doc, pos));
        return true;
      })
      .run();
  }

  function applyDisplay(nextDisplay: boolean) {
    if (nextDisplay === display || !latex.trim()) return;
    const { selection, schema } = editor.state;
    if (!(selection instanceof NodeSelection)) return;
    const from = selection.from;
    const type = schema.nodes[nextDisplay ? MATH_BLOCK_NODE : MATH_INLINE_NODE];
    if (!type) return;
    editor
      .chain()
      .focus()
      .command(({ tr }) => {
        tr.replaceSelectionWith(type.create({ latex }));
        const pos = findMathPos(tr.doc, type, latex, from);
        if (pos !== -1) tr.setSelection(NodeSelection.create(tr.doc, pos));
        return true;
      })
      .run();
  }

  function deleteFormula() {
    if (!(editor.state.selection instanceof NodeSelection)) return;
    editor.chain().focus().deleteSelection().run();
  }

  return (
    <div
      role="group"
      aria-label={t("title")}
      className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-subtle/60 px-3 py-1.5"
    >
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
        <span className="text-label font-semibold text-text">{t("title")}</span>
        <Input
          value={latex}
          onChange={(event) => applyLatex(event.target.value)}
          aria-label={t("latexLabel")}
          aria-invalid={invalid}
          dir="ltr"
          spellCheck={false}
          className="h-8 min-w-40 flex-1 font-mono text-caption"
        />
        <Button type="button" size="sm" variant="outline" onClick={openDialog}>
          <Pencil aria-hidden />
          {t("edit")}
        </Button>
        <DisplayToggle
          display={display}
          onChange={applyDisplay}
          label={t("displayLabel")}
          inlineLabel={t("inline")}
          blockLabel={t("block")}
        />
      </div>
      <Button type="button" size="sm" variant="danger" onClick={deleteFormula}>
        <Trash2 aria-hidden />
        {t("delete")}
      </Button>
    </div>
  );
}
