"use client";

import { useTranslations } from "next-intl";
import type { InsertDialogProps } from "../registry";
import { StubDialog } from "../shared/stub-dialog";

/** Hộp tạm của mục `cross` — lane sóng 2 thay bằng hộp thật theo bản vẽ của mục. */
export default function CrossInsertDialog({ open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insert.cross");
  return <StubDialog open={open} onOpenChange={onOpenChange} title={t("label")} description={t("description")} />;
}
