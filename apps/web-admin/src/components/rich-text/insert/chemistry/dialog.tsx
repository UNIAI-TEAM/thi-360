"use client";

import { useTranslations } from "next-intl";
import type { InsertDialogProps } from "../registry";
import { StubDialog } from "../shared/stub-dialog";

/** Hộp tạm của mục `chemistry` — lane sóng 2 thay bằng hộp thật theo bản vẽ của mục. */
export default function ChemistryInsertDialog({ open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insert.chemistry");
  return <StubDialog open={open} onOpenChange={onOpenChange} title={t("label")} description={t("description")} />;
}
