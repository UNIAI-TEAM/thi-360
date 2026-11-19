"use client";

import { Checkbox, Input, Textarea } from "@thi360/ui";
import { RichTextView, sanitizeQuestionHtml } from "@thi360/ui/rich-text";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { QUOTE_NODE } from "../../extensions";
import type { InsertDialogProps } from "../registry";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { selectedAtom, upsertAtom } from "../shared/upsert-atom";
import {
  createQuoteDraft,
  hasQuoteContent,
  parseQuote,
  quoteAttributes,
  serializeQuote,
  unwrapQuote,
  type QuoteDraft,
  type QuoteKind,
} from "./quote-model";

const KINDS: { kind: QuoteKind; labelKey: "kindPoem" | "kindProse" | "kindSource" }[] = [
  { kind: "poem", labelKey: "kindPoem" },
  { kind: "prose", labelKey: "kindProse" },
  { kind: "source", labelKey: "kindSource" },
];

/**
 * Hộp "Đoạn trích" (spec 4.7, bản vẽ `SoanThaoDoanTrich`): kiểu Thơ/Văn xuôi/Tư liệu, nội dung, giữ ngắt dòng,
 * đánh số dòng, tác giả, tác phẩm, ghi chú nguồn, xem trước dựng bằng chính `RichTextView`. Lưu thành
 * `blockquote.quote` (node atom `quote`); đang chọn một đoạn trích thì mở lại với dữ liệu hiện có.
 */
export default function QuoteInsertDialog({ editor, open, onOpenChange }: InsertDialogProps) {
  const t = useTranslations("richText.insertDialog.quote");
  const kindName = useId();
  const [initial] = useState(() => {
    const existing = selectedAtom(editor, QUOTE_NODE);
    return { existing, draft: existing ? parseQuote(existing.node.attrs) : createQuoteDraft() };
  });
  const [draft, setDraft] = useState<QuoteDraft>(initial.draft);
  const isPoem = draft.kind === "poem";
  const valid = hasQuoteContent(draft);

  function change(patch: Partial<QuoteDraft>) {
    setDraft((current) => ({ ...current, ...patch }));
  }

  function submit() {
    if (!valid) return;
    const inner = unwrapQuote(sanitizeQuestionHtml(serializeQuote(draft)).html);
    if (inner === null) return;
    upsertAtom(editor, QUOTE_NODE, { ...quoteAttributes(draft), html: inner }, initial.existing);
    onOpenChange(false);
  }

  return (
    <InsertDialogShell
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      description={t("description")}
      onSubmit={submit}
      submitLabel={initial.existing ? t("update") : t("submit")}
      submitDisabled={!valid}
    >
      <div className="mb-4 grid max-h-[70dvh] gap-6 overflow-y-auto lg:grid-cols-2">
        <div className="flex min-w-0 flex-col gap-4">
          <fieldset>
            <legend className="mb-1.5 text-field-label text-text-secondary">{t("kindLabel")}</legend>
            <div className="flex flex-wrap gap-2">
              {KINDS.map(({ kind, labelKey }) => (
                <label
                  key={kind}
                  className="flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-body text-text has-checked:border-primary-text has-checked:bg-primary-50"
                >
                  <input
                    type="radio"
                    name={kindName}
                    value={kind}
                    checked={draft.kind === kind}
                    onChange={() => change({ kind })}
                  />
                  {t(labelKey)}
                </label>
              ))}
            </div>
          </fieldset>

          <label className="block">
            <span className="mb-1.5 block text-field-label text-text-secondary">
              {t("contentLabel")} <span aria-hidden>*</span>
            </span>
            <Textarea
              rows={8}
              required
              value={draft.content}
              onChange={(event) => change({ content: event.target.value })}
              autoFocus
            />
          </label>

          <label className="flex items-center gap-2 text-body text-text-secondary">
            <Checkbox
              checked={isPoem && draft.keepBreaks}
              disabled={!isPoem}
              onCheckedChange={(value) => change({ keepBreaks: value === true })}
            />
            {t("keepBreaks")}
          </label>
          <label className="flex items-center gap-2 text-body text-text-secondary">
            <Checkbox
              checked={isPoem && draft.numberLines}
              disabled={!isPoem}
              onCheckedChange={(value) => change({ numberLines: value === true })}
            />
            {t("numberLines")}
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="mb-1.5 block text-field-label text-text-secondary">{t("authorLabel")}</span>
              <Input value={draft.author} onChange={(event) => change({ author: event.target.value })} />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-field-label text-text-secondary">{t("workLabel")}</span>
              <Input value={draft.work} onChange={(event) => change({ work: event.target.value })} />
            </label>
          </div>
          <label className="block">
            <span className="mb-1.5 block text-field-label text-text-secondary">{t("sourceLabel")}</span>
            <Input
              value={draft.source}
              placeholder={t("sourcePlaceholder")}
              onChange={(event) => change({ source: event.target.value })}
            />
          </label>
        </div>

        <div className="flex min-w-0 flex-col gap-2">
          <span className="text-field-label text-text-secondary">{t("previewLabel")}</span>
          {valid ? (
            <RichTextView html={serializeQuote(draft)} />
          ) : (
            <p className="rounded-lg border border-dashed border-border px-4 py-6 text-body text-text-muted">
              {t("previewEmpty")}
            </p>
          )}
          <p className="text-caption text-text-muted">{t("previewNote")}</p>
        </div>
      </div>
    </InsertDialogShell>
  );
}
