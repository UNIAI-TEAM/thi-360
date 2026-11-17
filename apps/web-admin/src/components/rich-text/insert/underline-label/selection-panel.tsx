"use client";

import { Button } from "@thi360/ui";
import { Mark as PMMark } from "@tiptap/pm/model";
import { Pencil, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { UNDERLINE_LABEL_MARK } from "../../extensions/names";
import type { SelectionPanelProps } from "../registry";
import { readLabelSelection } from "./labels";

/**
 * Khung "Cụm đang chọn: nhãn C" dưới thanh công cụ (spec 4.8, bản vẽ `SoanThaoTiengAnh`): Đổi nhãn mở lại hộp,
 * Bỏ gạch chân gỡ nhãn khỏi cả cụm ngay.
 */
export default function UnderlineLabelSelectionPanel({ editor, target, openDialog }: SelectionPanelProps) {
  const t = useTranslations("richText.insertDialog.underline-label.panel");
  if (!(target instanceof PMMark)) return null;
  const label = String(target.attrs.label ?? "");

  function removeUnderline() {
    const phrase = readLabelSelection(editor.state);
    editor.chain().focus().setTextSelection({ from: phrase.from, to: phrase.to }).unsetMark(UNDERLINE_LABEL_MARK).run();
  }

  return (
    <div
      role="group"
      aria-label={t("title", { label })}
      className="flex flex-wrap items-center gap-2 border-b border-border bg-subtle/60 px-2 py-1.5"
    >
      <span className="text-caption font-medium text-text-muted">{t("title", { label })}</span>
      <Button type="button" size="sm" variant="outline" onClick={openDialog}>
        <Pencil aria-hidden />
        {t("relabel")}
      </Button>
      <Button type="button" size="sm" variant="outline" onClick={removeUnderline}>
        <X aria-hidden />
        {t("remove")}
      </Button>
    </div>
  );
}
