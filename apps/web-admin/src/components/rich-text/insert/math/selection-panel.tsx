"use client";

import { Button, Input } from "@thi360/ui";
import { Node as PMNode } from "@tiptap/pm/model";
import { NodeSelection } from "@tiptap/pm/state";
import { Pencil, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import type { SelectionPanelProps } from "../registry";
import { MATH_BLOCK_NODE } from "../../extensions";

/**
 * Khung "Công thức đang chọn" dưới thanh công cụ (bản vẽ `SoanThaoTongQuan`, spec 4.1): mã LaTeX
 * sửa trực tiếp, Trong dòng/Dòng riêng, Xóa công thức, nút Sửa mở lại hộp công thức. Bản tối thiểu
 * của ED-0b — ED-1 thay bằng khung đầy đủ của mình.
 */
export default function MathSelectionPanel({ editor, target, openDialog }: SelectionPanelProps) {
  const t = useTranslations("richText.insert.math.panel");
  const node = target instanceof PMNode ? target : null;
  if (!node) return null;
  const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
  const display = node.type.name === MATH_BLOCK_NODE;
  const nodeName = node.type.name;

  // Ô nhập đọc thẳng `latex` của node đang chọn: sửa là cập nhật node, khung dựng lại theo vùng chọn.
  function applyLatex(next: string) {
    editor.chain().updateAttributes(nodeName, { latex: next }).run();
  }

  function deleteFormula() {
    if (!(editor.state.selection instanceof NodeSelection)) return;
    editor.chain().focus().deleteSelection().run();
  }

  return (
    <div
      role="group"
      aria-label={t("title")}
      className="flex flex-wrap items-center gap-2 border-b border-border bg-subtle/60 px-2 py-1.5"
    >
      <span className="text-caption font-medium text-text-muted">{t("title")}</span>
      <Input
        value={latex}
        onChange={(event) => applyLatex(event.target.value)}
        aria-label={t("latexLabel")}
        dir="ltr"
        className="h-8 min-w-40 flex-1 font-mono text-caption"
      />
      <div role="group" aria-label={t("displayLabel")} className="flex items-center gap-1">
        <Button
          type="button"
          size="sm"
          variant={display ? "secondary" : "soft"}
          aria-pressed={!display}
          onClick={() => editor.chain().focus().setMath({ latex, display: false }).run()}
        >
          {t("inline")}
        </Button>
        <Button
          type="button"
          size="sm"
          variant={display ? "soft" : "secondary"}
          aria-pressed={display}
          onClick={() => editor.chain().focus().setMath({ latex, display: true }).run()}
        >
          {t("block")}
        </Button>
      </div>
      <Button type="button" size="sm" variant="outline" onClick={openDialog}>
        <Pencil aria-hidden />
        {t("edit")}
      </Button>
      <Button type="button" size="sm" variant="danger" onClick={deleteFormula}>
        <Trash2 aria-hidden />
        {t("delete")}
      </Button>
    </div>
  );
}
