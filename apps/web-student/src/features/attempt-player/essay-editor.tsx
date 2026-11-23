"use client";

import { TiptapEssayEditor } from "@thi360/ui/essay-editor";
import type { EssayEditorProps } from "@thi360/ui/question";
import { useTranslations } from "next-intl";

export function EssayEditor(props: EssayEditorProps) {
  const t = useTranslations("attempt.editor");
  return (
    <TiptapEssayEditor
      {...props}
      labels={{
        toolbarLabel: t("toolbarLabel"),
        bold: t("bold"),
        italic: t("italic"),
        bulletList: t("bulletList"),
        orderedList: t("orderedList"),
        undo: t("undo"),
        redo: t("redo"),
      }}
    />
  );
}
