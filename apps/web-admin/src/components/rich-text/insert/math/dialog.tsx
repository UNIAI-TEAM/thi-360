"use client";

import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Input } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import type { InsertDialogProps } from "../registry";

/** Dữ liệu `openForSelection` của mục `math` trả về — mở lại hộp với công thức đang chọn. */
export interface MathEditing {
  latex: string;
  display: boolean;
}

function asMathEditing(editing: unknown): MathEditing | null {
  if (typeof editing !== "object" || editing === null) return null;
  const { latex, display } = editing as Partial<MathEditing>;
  return typeof latex === "string" ? { latex, display: display === true } : null;
}

/**
 * Hộp "Chèn công thức" bản tạm (chuyển nguyên từ `rich-text-editor.tsx`, giữ khóa `richText.formula.*`):
 * ô LaTeX và chọn Cùng dòng/Khối. ED-1 thay bằng hộp mathlive theo bản vẽ `SoanThaoCongThucToan`.
 */
export default function MathInsertDialog({ editor, open, onOpenChange, editing }: InsertDialogProps) {
  const t = useTranslations("richText.formula");
  const initial = asMathEditing(editing);
  const [formulaLatex, setFormulaLatex] = useState(initial?.latex ?? "");
  const [formulaDisplay, setFormulaDisplay] = useState(initial?.display ?? false);

  function applyFormula() {
    editor.chain().focus().setMath({ latex: formulaLatex, display: formulaDisplay }).run();
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("close")}>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            // Dialog dựng trong portal: submit React vẫn nổi bọt lên biểu mẫu của trang chứa trình soạn.
            event.stopPropagation();
            applyFormula();
          }}
        >
          <label className="mb-4 block">
            <span className="mb-1.5 block text-field-label text-text">{t("latexLabel")}</span>
            <Input
              value={formulaLatex}
              onChange={(event) => setFormulaLatex(event.target.value)}
              placeholder={t("latexPlaceholder")}
              autoFocus
              dir="ltr"
            />
          </label>
          <fieldset className="mb-2 flex gap-4">
            <legend className="sr-only">{t("displayLabel")}</legend>
            <label className="flex items-center gap-2 text-body text-text">
              <input type="radio" name="formula-display" checked={!formulaDisplay} onChange={() => setFormulaDisplay(false)} />
              {t("inline")}
            </label>
            <label className="flex items-center gap-2 text-body text-text">
              <input type="radio" name="formula-display" checked={formulaDisplay} onChange={() => setFormulaDisplay(true)} />
              {t("block")}
            </label>
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={!formulaLatex.trim()}>
              {t("insert")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
