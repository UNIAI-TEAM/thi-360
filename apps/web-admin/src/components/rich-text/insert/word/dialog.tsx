"use client";

import { useTranslations } from "next-intl";
import type { InsertDialogProps } from "../registry";
import { markWordPending } from "./pending-marks";
import { WordPasteDialog } from "./word-paste-dialog";

/**
 * Hộp "Dán từ Word" của registry (mục `word`): người soạn dán nội dung Word vào vùng dán, xem lại, rồi nội dung được
 * chèn vào ô soạn đang mở, không tách theo nhãn (ô soạn đơn không có phương án/lời giải). Hộp đầy đủ của form soạn
 * câu hỏi — tách A./B./C./D./Lời giải, điền nhiều ô, hỏi ghi đè — dùng thẳng `WordPasteDialog`
 * (`features/questions/components/question-editor-form.tsx`).
 */
export default function WordInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.word");
  return (
    <WordPasteDialog
      open={open}
      onOpenChange={onOpenChange}
      initialHtml={null}
      splitAvailable={false}
      onApply={({ fields, pending }) => {
        editor.chain().focus().insertContent(fields.stem).run();
        markWordPending(pending, t("mark"));
        onOpenChange(false);
      }}
    />
  );
}
