"use client";

import { Button, Checkbox, ConfirmDialog, DialogFooter, LiveStatus, StatusChip, focusRing, cn } from "@thi360/ui";
import { Check } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState, type ClipboardEvent, type ReactNode } from "react";
import { InsertDialogShell } from "../shared/insert-dialog-shell";
import { isWordHtml, parseWordHtml } from "./parse";
import { RetypeMathDialog } from "./retype-math-dialog";
import {
  acceptAllFixes,
  acceptFix,
  buildFields,
  editFormula,
  hasUnresolved,
  pendingLatexes,
  removeFormula,
  retypeFormula,
  splitReview,
  startReview,
  summarize,
  type WordFields,
  type WordReview,
} from "./session";
import { WordBlocks } from "./word-preview";
import { WordReviewList, reviewNumbers } from "./word-review-list";

/** Kết quả "Đưa vào biểu mẫu": HTML hợp đồng của từng ô và các chỗ tự sửa còn chờ xác nhận. */
export interface WordPasteResult {
  fields: WordFields;
  /** Có tách theo nhãn (ô chọn bật và tìm thấy nhãn). */
  split: boolean;
  /** LaTeX của chỗ tự sửa chưa Chấp nhận/Sửa tay: được đánh dấu bằng decoration sau khi điền. */
  pending: string[];
}

export interface WordPasteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** HTML clipboard đã nhận (dán vào ô soạn); `null` thì hộp hiện vùng dán để người soạn dán vào. */
  initialHtml: string | null;
  /** Dạng câu cho phép tách A./B./C./D./Lời giải (Trắc nghiệm 1 đáp án, Nhiều đáp án); `false` ẩn ô chọn. */
  splitAvailable: boolean;
  /** "Đưa vào biểu mẫu". Hộp không tự đóng: nơi gọi đóng sau khi điền xong (có thể hỏi ghi đè trước). */
  onApply: (result: WordPasteResult) => void;
}

function PasteZone({ onHtml }: { onHtml: (html: string) => void }) {
  const t = useTranslations("richText.insertDialog.word.paste");
  const [rejected, setRejected] = useState(false);
  const hintId = useId();
  const onPaste = (event: ClipboardEvent<HTMLDivElement>) => {
    event.preventDefault();
    const html = event.clipboardData.getData("text/html");
    if (isWordHtml(html)) onHtml(html);
    else setRejected(true);
  };
  return (
    <div className="flex flex-col gap-2">
      <div
        role="textbox"
        aria-readonly="true"
        aria-label={t("label")}
        aria-describedby={hintId}
        tabIndex={0}
        autoFocus
        onPaste={onPaste}
        className={cn(
          "grid min-h-40 place-items-center rounded-xl border border-dashed border-border-strong bg-subtle p-6 text-center text-body text-text-muted",
          focusRing,
        )}
      >
        <span id={hintId}>{t("hint")}</span>
      </div>
      {rejected && (
        <p role="alert" className="m-0 text-body text-warning">
          {t("notWord")}
        </p>
      )}
    </div>
  );
}

/**
 * Hộp "Dán từ Word" (spec 4.10, bản vẽ `SoanThaoDanTuWord`): thanh tóm tắt, nội dung đã dán, danh sách "Chỗ cần xem lại"
 * (tự sửa chờ xác nhận, công thức cần gõ lại), "Ảnh chưa đưa vào", Chấp nhận mọi chỗ tự sửa, Đưa vào biểu mẫu.
 */
export function WordPasteDialog({ open, onOpenChange, initialHtml, splitAvailable, onApply }: WordPasteDialogProps) {
  const tRoot = useTranslations("richText");
  const t = useTranslations("richText.insertDialog.word");
  const [review, setReview] = useState<WordReview | null>(() => (initialHtml ? startReview(parseWordHtml(initialHtml)) : null));
  const [split, setSplit] = useState(splitAvailable);
  const [onlyPending, setOnlyPending] = useState(true);
  const [retypingId, setRetypingId] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [liveMessage, setLiveMessage] = useState("");
  // Vùng dán biến mất khi dán xong: đưa focus tới tiêu đề danh sách xem lại thay vì để rơi về khung hộp (vòng focus quanh cả hộp).
  const [focusReview, setFocusReview] = useState(false);
  const reviewHeadingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (focusReview) reviewHeadingRef.current?.focus();
  }, [focusReview]);
  const splitId = useId();
  const onlyPendingId = useId();
  const noteId = useId();

  const reviewHtml = review?.html ?? "";
  const fields = useMemo(() => (reviewHtml ? splitReview({ html: reviewHtml }, split) : null), [reviewHtml, split]);
  const itemOf = useMemo(() => {
    const byId = new Map((review?.items ?? []).map((item) => [item.id, item]));
    return (id: string) => byId.get(id);
  }, [review]);

  const numbers = useMemo(() => (review ? reviewNumbers(review) : new Map<string, number>()), [review]);

  const update = (change: (current: WordReview) => WordReview, announcement: string) => {
    setReview((current) => (current ? change(current) : current));
    setLiveMessage(announcement);
  };

  const body = (() => {
    if (!review || !fields) {
      return (
        <PasteZone
          onHtml={(html) => {
            setReview(startReview(parseWordHtml(html)));
            setFocusReview(true);
          }}
        />
      );
    }
    const summary = summarize(review);
    const splitActive = split && fields.found;
    const removing = removingId ? review.items.find((item) => item.id === removingId) : undefined;
    const blocked = hasUnresolved(review);
    return (
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap gap-2">
            <span className="inline-flex h-6 items-center gap-1.5 rounded-full bg-success-bg px-2.5 text-caption font-medium whitespace-nowrap text-success">
              <Check size={14} strokeWidth={2.6} aria-hidden className="shrink-0" />
              {t("summary.converted", { count: summary.converted })}
            </span>
            <StatusChip tone="warning">{t("summary.pending", { count: summary.pending })}</StatusChip>
            <StatusChip tone="danger">{t("summary.retype", { count: summary.retype })}</StatusChip>
          </div>
          <p className="m-0 text-caption text-text-secondary">{t("summary.note")}</p>
        </div>

        <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
          <section aria-label={t("preview.title")} className="flex min-w-0 flex-col gap-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="m-0 text-field-label font-medium text-text-secondary">{t("preview.title")}</h3>
              {splitAvailable && (
                <label htmlFor={splitId} className="flex min-h-8 items-center gap-2 text-field-label text-text-secondary">
                  <Checkbox id={splitId} checked={split} onCheckedChange={(checked) => setSplit(checked === true)} />
                  {t("preview.split")}
                </label>
              )}
            </div>
            {split && !fields.found && <p className="m-0 text-caption text-text-muted">{t("preview.splitNoMatch")}</p>}
            <div className="flex min-w-0 flex-col gap-3 rounded-xl border border-border bg-surface p-3 lg:max-h-[48dvh] lg:overflow-y-auto">
              <PreviewSection label={splitActive ? t("fields.stem") : null}>
                <WordBlocks blocks={fields.stem} itemOf={itemOf} numbers={numbers} />
              </PreviewSection>
              {splitActive && fields.options.length > 0 && (
                <PreviewSection label={t("fields.options")}>
                  <div className="flex flex-col gap-1.5">
                    {fields.options.map((option) => (
                      <div key={option.label} className="flex gap-2">
                        <strong className="shrink-0">{option.label}.</strong>
                        <div className="min-w-0">
                          <WordBlocks blocks={option.blocks} itemOf={itemOf} numbers={numbers} />
                        </div>
                      </div>
                    ))}
                  </div>
                </PreviewSection>
              )}
              {splitActive && fields.solution.length > 0 && (
                <PreviewSection label={t("fields.solution")}>
                  <WordBlocks blocks={fields.solution} itemOf={itemOf} numbers={numbers} />
                </PreviewSection>
              )}
            </div>
          </section>

          <section aria-label={t("review.title")} className="flex min-w-0 flex-col gap-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3
                ref={reviewHeadingRef}
                tabIndex={-1}
                className="m-0 text-field-label font-medium text-text-secondary outline-hidden"
              >
                {t("review.title")}
              </h3>
              <label htmlFor={onlyPendingId} className="flex min-h-8 items-center gap-2 text-field-label text-text-secondary">
                <Checkbox id={onlyPendingId} checked={onlyPending} onCheckedChange={(checked) => setOnlyPending(checked === true)} />
                {t("review.onlyPending")}
              </label>
            </div>
            <div className="lg:max-h-[48dvh] lg:overflow-y-auto">
              <WordReviewList
                review={review}
                fields={fields}
                splitActive={splitActive}
                onlyPending={onlyPending}
                onAccept={(id) => update((current) => acceptFix(current, id), t("live.accepted"))}
                onEdit={(id, latex) => update((current) => editFormula(current, id, latex), t("live.edited"))}
                onRetype={setRetypingId}
                onRemove={setRemovingId}
              />
            </div>
            {review.images.length > 0 && (
              <section aria-label={t("images.title")} className="mt-2 flex flex-col gap-2">
                <h3 className="m-0 text-field-label font-medium text-text-secondary">{t("images.title")}</h3>
                <p className="m-0 text-caption text-text-muted">{t("images.description")}</p>
                <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
                  {review.images.map((image, index) => (
                    <li key={image.id} className="rounded-lg bg-subtle px-3 py-2 text-body text-text-secondary">
                      <strong className="font-semibold">{t("images.item", { index: index + 1 })}</strong>
                      {image.alt && <span>: {image.alt}</span>}
                      {image.context && <span className="block text-caption text-text-muted">{t("images.context", { text: image.context })}</span>}
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </section>
        </div>

        <DialogFooter className="items-center justify-between">
          <p id={noteId} className="m-0 min-w-0 flex-1 text-caption text-text-muted">
            {blocked ? t("footer.blocked", { count: summary.retype }) : t("footer.note")}
          </p>
          <div className="flex flex-wrap justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
              {t("footer.cancel")}
            </Button>
            <Button
              type="button"
              variant="soft"
              disabled={summary.pending === 0}
              onClick={() => update((current) => acceptAllFixes(current), t("live.acceptedAll", { count: summary.pending }))}
            >
              {t("footer.acceptAll")}
            </Button>
            <Button
              type="button"
              disabled={blocked}
              aria-describedby={noteId}
              onClick={() => {
                const built = buildFields(review, split);
                onApply({ fields: built, split: split && built.matched, pending: pendingLatexes(review) });
              }}
            >
              {t("footer.apply")}
            </Button>
          </div>
        </DialogFooter>

        {retypingId && (
          <RetypeMathDialog
            key={retypingId}
            onDone={(latex) => {
              const id = retypingId;
              setRetypingId(null);
              if (latex) update((current) => retypeFormula(current, id, latex), t("live.typed"));
            }}
          />
        )}
        <ConfirmDialog
          open={removing !== undefined}
          onOpenChange={(next) => {
            if (!next) setRemovingId(null);
          }}
          title={t("review.removeTitle")}
          description={removing?.kind === "retype" && removing.context ? `${t("review.removeDescription")} ${t("review.retypeContext", { text: removing.context })}` : t("review.removeDescription")}
          confirmLabel={t("review.removeConfirm")}
          cancelLabel={t("review.removeCancel")}
          closeLabel={tRoot("insertDialog.close")}
          onConfirm={() => {
            const id = removingId;
            setRemovingId(null);
            if (id) update((current) => removeFormula(current, id), t("live.removed"));
          }}
        />
      </div>
    );
  })();

  return (
    <InsertDialogShell open={open} onOpenChange={onOpenChange} title={tRoot("insert.word.label")} description={t("description")} hideFooter>
      {body}
      <LiveStatus message={liveMessage} />
    </InsertDialogShell>
  );
}

function PreviewSection({ label, children }: { label: string | null; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      {label && <div className="text-overline font-semibold tracking-overline text-text-secondary uppercase">{label}</div>}
      {children}
    </div>
  );
}
