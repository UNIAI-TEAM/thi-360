"use client";

import { Button, cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { UNDERLINE_LABEL_MARK } from "../../extensions/names";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { nextUnderlineLabel, readLabelSelection, UNDERLINE_LABELS } from "./labels";

/**
 * Hộp "Gạch chân đánh nhãn" (spec 4.8, bản vẽ `SoanThaoTiengAnh`): gạch chân cụm đang chọn và đánh nhãn A–H.
 * Chèn mới thì nhãn mặc định là chữ kế tiếp chưa dùng; mở lại trên cụm đã gạch (nút Chèn đang bật, nhấp đúp hay "Đổi
 * nhãn" của khung đang chọn) thì giữ nhãn cũ, đổi được nhãn hoặc bỏ gạch chân. Nhãn mà cụm khác đang dùng bị khóa:
 * hai cụm cùng nhãn là lỗi `UNDERLINE_LABEL_DUPLICATE` khi Sẵn sàng.
 */
export default function UnderlineLabelInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const tEntry = useTranslations("richText.insert.underline-label");
  const t = useTranslations("richText.insertDialog.underline-label");
  const groupId = useId();
  // Vùng chọn chụp một lần lúc mở: mở lại hộp luôn thấy đúng cụm đã chọn, kể cả khi focus rời trình soạn.
  const [target] = useState(() => readLabelSelection(editor.state));
  const editing = target.label !== null;
  const suggested = nextUnderlineLabel(target.usedByOthers);
  const [label, setLabel] = useState<string | null>(target.label ?? suggested);
  const hasRange = target.to > target.from;
  const canApply = hasRange && target.singleBlock && label !== null;

  function apply() {
    if (!canApply) return;
    // Mark đánh nhãn không loại trừ chính nó (chỉ loại gạch chân thường): gỡ nhãn cũ rồi đặt nhãn mới.
    editor
      .chain()
      .focus()
      .setTextSelection({ from: target.from, to: target.to })
      .unsetMark(UNDERLINE_LABEL_MARK)
      .setMark(UNDERLINE_LABEL_MARK, { label })
      .run();
    onOpenChange(false);
  }

  function remove() {
    editor.chain().focus().setTextSelection({ from: target.from, to: target.to }).unsetMark(UNDERLINE_LABEL_MARK).run();
    onOpenChange(false);
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={tEntry("label")}
      description={tEntry("description")}
      onSubmit={apply}
      submitLabel={editing ? t("relabel") : t("apply")}
      submitDisabled={!canApply}
    >
      <div className="flex flex-col gap-4 pb-2">
        <div className="flex flex-col gap-1">
          <span id={`${groupId}-phrase`} className="text-field-label text-text">
            {t("phrase")}
          </span>
          {hasRange ? (
            <p aria-labelledby={`${groupId}-phrase`} className="rounded-md border border-border bg-subtle px-3 py-2 text-body text-text">
              <u>{target.text}</u>
            </p>
          ) : (
            <p role="status" className="text-body text-text-muted">
              {t("phraseEmpty")}
            </p>
          )}
          {hasRange && !target.singleBlock && (
            <p role="alert" className="text-caption text-danger">
              {t("phraseMultiBlock")}
            </p>
          )}
        </div>

        <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0">
          <legend className="mb-1 text-field-label text-text">{t("labelLegend")}</legend>
          <div className="flex flex-wrap gap-2">
            {UNDERLINE_LABELS.map((candidate) => {
              const taken = target.usedByOthers.includes(candidate) && candidate !== target.label;
              return (
                <label
                  key={candidate}
                  className={cn(
                    "inline-flex size-10 cursor-pointer items-center justify-center rounded-md border border-border bg-surface text-body font-semibold text-text",
                    "has-checked:border-primary-600 has-checked:bg-primary-50 has-checked:text-primary-text",
                    "has-focus-visible:ring-3 has-focus-visible:ring-primary-text/15",
                    "has-disabled:cursor-not-allowed has-disabled:opacity-50",
                  )}
                >
                  <input
                    type="radio"
                    name={`${groupId}-label`}
                    value={candidate}
                    checked={label === candidate}
                    disabled={taken}
                    onChange={() => setLabel(candidate)}
                    className="sr-only"
                  />
                  <span aria-hidden>{candidate}</span>
                  <span className="sr-only">{taken ? t("labelUsed", { label: candidate }) : candidate}</span>
                </label>
              );
            })}
          </div>
          <p className="text-caption text-text-muted">
            {suggested ? t("nextHint", { label: suggested }) : t("allUsed")}
          </p>
        </fieldset>

        {editing && (
          <div>
            <Button type="button" variant="danger" onClick={remove}>
              {t("remove")}
            </Button>
          </div>
        )}
      </div>
    </InsertDialogShell>
  );
}
