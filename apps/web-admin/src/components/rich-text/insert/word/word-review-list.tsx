"use client";

import { Button, Input, StatusChip, type StatusTone } from "@thi360/ui";
import { ArrowRight, Check, Pencil } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { FormulaChip } from "./formula-chip";
import { NumberBadge, type NumberBadgeTone } from "./number-badge";
import { fieldOf, type ReviewFormula, type ReviewItem, type ReviewRetype, type WordReview } from "./session";
import type { SplitFields } from "./types";

export interface WordReviewListProps {
  review: WordReview;
  /** Kết quả tách hiện tại: cho biết mỗi chỗ nằm ở đề bài, phương án nào hay lời giải. */
  fields: SplitFields;
  /** Đang tách theo nhãn và tìm thấy nhãn: hiện tên ô; ngược lại mọi chỗ ghi chung "Nội dung". */
  splitActive: boolean;
  /** "Chỉ hiện mục chưa xử lý". */
  onlyPending: boolean;
  onAccept: (id: string) => void;
  onEdit: (id: string, latex: string) => void;
  onRetype: (id: string) => void;
  onRemove: (id: string) => void;
}

/** Mục có gì để xem lại: chỗ đã tự sửa và chỗ cần gõ lại. Công thức đổi đúng không cần xem. */
export function isReviewable(item: ReviewItem): boolean {
  return item.kind === "retype" || item.fixes.length > 0;
}

/** Mục chưa xử lý: chỗ tự sửa chờ xác nhận, chỗ cần gõ lại chưa gõ/bỏ. */
export function isOpen(item: ReviewItem): boolean {
  return item.kind === "retype" ? item.resolution === null : item.status === "pending";
}

/** Số thứ tự cố định của mỗi chỗ cần xem lại (theo thứ tự trong nội dung), không đổi khi lọc "Chỉ hiện mục chưa xử lý". */
export function reviewNumbers(review: WordReview): ReadonlyMap<string, number> {
  return new Map(review.items.filter(isReviewable).map((item, index) => [item.id, index + 1]));
}

/** Màu số: cam cho chỗ tự sửa chờ xác nhận, đỏ cho chỗ cần gõ lại chưa xử lý, trung tính khi đã xử lý. */
export function badgeTone(item: ReviewItem): NumberBadgeTone {
  if (!isOpen(item)) return "neutral";
  return item.kind === "retype" ? "danger" : "warning";
}

function statusOf(item: ReviewItem): { tone: StatusTone; key: string } {
  if (item.kind === "retype") {
    if (item.resolution?.type === "typed") return { tone: "success", key: "typed" };
    if (item.resolution?.type === "removed") return { tone: "neutral", key: "removed" };
    return { tone: "danger", key: "retype" };
  }
  if (item.status === "accepted") return { tone: "success", key: "accepted" };
  if (item.status === "edited") return { tone: "success", key: "edited" };
  return { tone: "warning", key: "fixed" };
}

export function WordReviewList({ review, fields, splitActive, onlyPending, onAccept, onEdit, onRetype, onRemove }: WordReviewListProps) {
  const t = useTranslations("richText.insertDialog.word");
  const rows = review.items.filter(isReviewable).filter((item) => !onlyPending || isOpen(item));
  const allRows = review.items.filter(isReviewable);

  if (rows.length === 0) {
    return <p className="m-0 rounded-lg bg-subtle px-3 py-4 text-center text-body text-text-muted">{allRows.length === 0 ? t("review.empty") : t("review.emptyFiltered")}</p>;
  }

  const locationOf = (id: string): string => {
    if (!splitActive) return t("fields.all");
    const ref = fieldOf(fields, id);
    if (ref.field === "option") return t("fields.option", { label: ref.label });
    return ref.field === "solution" ? t("fields.solution") : t("fields.stem");
  };
  const numbers = reviewNumbers(review);

  return (
    <ol className="m-0 flex list-none flex-col gap-3 p-0">
      {rows.map((item) => {
        const status = statusOf(item);
        return (
          <li key={item.id} className="flex flex-col gap-2.5 rounded-xl border border-border bg-surface p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <NumberBadge number={numbers.get(item.id) ?? 0} tone={badgeTone(item)} />
                <strong className="text-body font-semibold text-text">{locationOf(item.id)}</strong>
              </div>
              <StatusChip tone={status.tone}>{t(`review.status.${status.key}`)}</StatusChip>
            </div>
            {item.kind === "formula" ? (
              <FormulaCard item={item} onAccept={onAccept} onEdit={onEdit} />
            ) : (
              <RetypeCard item={item} onRetype={onRetype} onRemove={onRemove} />
            )}
          </li>
        );
      })}
    </ol>
  );
}

function FormulaCard({ item, onAccept, onEdit }: { item: ReviewFormula; onAccept: (id: string) => void; onEdit: (id: string, latex: string) => void }) {
  const t = useTranslations("richText.insertDialog.word.review");
  const inputId = useId();
  const [draft, setDraft] = useState<string | null>(null);
  const editing = draft !== null;

  return (
    <>
      <div className="grid items-center gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-1 rounded-lg bg-subtle px-3 py-2">
          <span className="text-caption text-text-muted">{t("before")}</span>
          <FormulaChip latex={item.raw} />
        </div>
        <ArrowRight aria-hidden className="size-4 shrink-0 justify-self-center text-text-muted max-sm:rotate-90" />
        <div className="flex min-w-0 flex-col gap-1 rounded-lg bg-warning-bg px-3 py-2">
          <span className="text-caption text-text-muted">{t("after")}</span>
          <FormulaChip latex={item.latex} />
        </div>
      </div>
      <ul className="m-0 list-none p-0 text-caption text-text-secondary">
        {item.fixes.map((fix) => (
          <li key={fix}>{t(`fixes.${fix}`)}</li>
        ))}
      </ul>
      {editing ? (
        <div className="flex flex-col gap-2">
          <label htmlFor={inputId} className="sr-only">
            {t("editLabel")}
          </label>
          <Input
            id={inputId}
            value={draft}
            dir="ltr"
            autoFocus
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              // Enter không được gửi cả hộp: chỉ lưu bản sửa này.
              if (event.key !== "Enter") return;
              event.preventDefault();
              if (draft.trim()) {
                onEdit(item.id, draft);
                setDraft(null);
              }
            }}
          />
          {draft.trim() && (
            <div className="rounded-lg bg-subtle px-3 py-2">
              <FormulaChip latex={draft} />
            </div>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              size="sm"
              disabled={!draft.trim()}
              onClick={() => {
                onEdit(item.id, draft);
                setDraft(null);
              }}
            >
              {t("save")}
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={() => setDraft(null)}>
              {t("cancelEdit")}
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          {item.status === "pending" && (
            <Button type="button" size="sm" variant="soft" onClick={() => onAccept(item.id)}>
              <Check aria-hidden />
              {t("accept")}
            </Button>
          )}
          <Button type="button" size="sm" variant="secondary" onClick={() => setDraft(item.latex)}>
            {t("edit")}
          </Button>
        </div>
      )}
    </>
  );
}

function RetypeCard({ item, onRetype, onRemove }: { item: ReviewRetype; onRetype: (id: string) => void; onRemove: (id: string) => void }) {
  const t = useTranslations("richText.insertDialog.word.review");
  return (
    <>
      <p className="m-0 text-body text-text-secondary">{t(`retypeCause.${item.cause}`, { element: item.detail ?? "" })}</p>
      {item.context && <p className="m-0 text-caption text-text-muted">{t("retypeContext", { text: item.context })}</p>}
      {item.resolution?.type === "typed" && (
        <div className="rounded-lg bg-success-bg px-3 py-2">
          <FormulaChip latex={item.resolution.latex} />
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="soft" onClick={() => onRetype(item.id)}>
          <Pencil aria-hidden />
          {t("retype")}
        </Button>
        {item.resolution?.type !== "removed" && (
          <Button type="button" size="sm" variant="secondary" onClick={() => onRemove(item.id)}>
            {t("remove")}
          </Button>
        )}
      </div>
    </>
  );
}
