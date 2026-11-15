"use client";

import {
  Button,
  cn,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  focusRing,
  notify,
  StatusChip,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import type { QuestionBankApiDeps } from "../api";
import { useBulkMarkQuestionsReady, useBulkRetireQuestions } from "../queries";
import type { BulkQuestionStatusResult } from "../types";

/** Số mã câu nêu đích danh trong hộp thoại xác nhận; phần còn lại gộp thành "và N câu khác" (DESIGN.md mục 5.10). */
const NAMED_LIMIT = 5;

export interface SelectedQuestion {
  id: string;
  code: string;
  usedInExamCount: number;
}

type BulkAction = "READY" | "RETIRE";

export interface QuestionBankBulkBarProps {
  /** Câu đang chọn (có thể gồm dòng ở trang khác). Rỗng thì thanh vẫn giữ chỗ nhưng ẩn và không nhận thao tác. */
  selected: SelectedQuestion[];
  onClearSelection: () => void;
  /** Sau khi áp dụng (thành công một phần hoặc toàn bộ): bên gọi bỏ chọn và chuyển focus. */
  onApplied: (result: BulkQuestionStatusResult) => void;
  deps?: Partial<Pick<QuestionBankApiDeps, "bulkMarkQuestionsReady" | "bulkRetireQuestions">>;
  className?: string;
}

/**
 * Thanh thao tác hàng loạt (canvas `NganHangCauHoi`: Chuyển sang Sẵn sàng, Ngừng sử dụng). Luôn có trong DOM, chồng
 * lên dải "N câu hỏi khớp bộ lọc" trong cùng một ô lưới, ẩn bằng `invisible` + `inert` khi chưa chọn dòng nào, nên
 * bảng không nhảy (DESIGN.md mục 5.9).
 */
export function QuestionBankBulkBar({
  selected,
  onClearSelection,
  onApplied,
  deps,
  className,
}: QuestionBankBulkBarProps) {
  const t = useTranslations("questions.bulk");
  const [confirm, setConfirm] = useState<BulkAction | null>(null);
  const [result, setResult] = useState<{ action: BulkAction; result: BulkQuestionStatusResult } | null>(null);
  const readyMutation = useBulkMarkQuestionsReady(
    deps?.bulkMarkQuestionsReady ? { bulkMarkQuestionsReady: deps.bulkMarkQuestionsReady } : undefined,
  );
  const retireMutation = useBulkRetireQuestions(
    deps?.bulkRetireQuestions ? { bulkRetireQuestions: deps.bulkRetireQuestions } : undefined,
  );
  const mutation = confirm === "RETIRE" ? retireMutation : readyMutation;
  const active = selected.length > 0;

  const codes = selected.slice(0, NAMED_LIMIT).map((item) => item.code).join(", ");
  const rest = Math.max(0, selected.length - NAMED_LIMIT);
  const named = rest > 0 ? t("affectedMore", { codes, count: rest }) : t("affected", { codes });
  const usedCount = selected.filter((item) => item.usedInExamCount > 0).length;

  function closeConfirm(open: boolean) {
    if (open || mutation.isPending) return;
    readyMutation.reset();
    retireMutation.reset();
    setConfirm(null);
  }

  function apply(action: BulkAction) {
    const ids = selected.map((item) => item.id);
    const target = action === "READY" ? readyMutation : retireMutation;
    target.mutate(
      { ids },
      {
        onSuccess: (response) => {
          setConfirm(null);
          target.reset();
          onApplied(response);
          if (response.skipped.length > 0) {
            setResult({ action, result: response });
          } else {
            notify.success(
              action === "READY"
                ? t("markReadyDone", { count: response.succeeded.length })
                : t("retireDone", { count: response.succeeded.length }),
            );
          }
        },
      },
    );
  }

  return (
    <>
      <div
        role="group"
        aria-label={t("groupLabel")}
        aria-hidden={!active || undefined}
        inert={!active}
        className={cn(
          "flex flex-wrap items-center justify-between gap-x-3 gap-y-2 bg-primary-50 px-4 py-2.5",
          !active && "invisible",
          className,
        )}
      >
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-label font-semibold text-primary-text tabular-nums">
            {t("selectedCount", { count: selected.length })}
          </span>
          <Button type="button" variant="link" size="sm" onClick={onClearSelection}>
            {t("clearSelection")}
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-2 @max-[40rem]:-m-1 @max-[40rem]:w-[calc(100%+0.5rem)] @max-[40rem]:flex-nowrap @max-[40rem]:overflow-x-auto @max-[40rem]:p-1 @max-[40rem]:[scrollbar-width:thin]">
          <Button type="button" size="sm" onClick={() => setConfirm("READY")}>
            {t("markReady")}
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={() => setConfirm("RETIRE")}>
            {t("retire")}
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirm === "READY"}
        onOpenChange={closeConfirm}
        tone="primary"
        title={t("markReadyTitle", { count: selected.length })}
        description={
          <>
            {named} {t("markReadyDescription")}
          </>
        }
        confirmLabel={t("confirmMarkReady")}
        cancelLabel={t("cancel")}
        loading={readyMutation.isPending}
        error={readyMutation.isError ? t("error") : undefined}
        onConfirm={() => apply("READY")}
      />

      <ConfirmDialog
        open={confirm === "RETIRE"}
        onOpenChange={closeConfirm}
        title={t("retireTitle", { count: selected.length })}
        description={
          <>
            {named} {t("retireDescription")}
            {usedCount > 0 && (
              <>
                {" "}
                <strong className="font-semibold text-text">{t("retireUsedWarning", { count: usedCount })}</strong>
              </>
            )}
          </>
        }
        confirmLabel={t("confirmRetire")}
        cancelLabel={t("cancel")}
        loading={retireMutation.isPending}
        error={retireMutation.isError ? t("error") : undefined}
        onConfirm={() => apply("RETIRE")}
      />

      <Dialog open={result !== null} onOpenChange={(open) => !open && setResult(null)}>
        <DialogContent size="md" closeLabel={t("close")}>
          {result && (
            <>
              <DialogHeader>
                <DialogTitle>{t("resultTitle")}</DialogTitle>
                <DialogDescription>
                  {t("resultSummary", {
                    succeeded: result.result.succeeded.length,
                    skipped: result.result.skipped.length,
                  })}
                </DialogDescription>
              </DialogHeader>
              <ul className="scroll-shadows-y m-0 flex max-h-72 list-none flex-col gap-1.5 overflow-y-auto p-0">
                {result.result.skipped.map((item) => (
                  <li
                    key={item.id}
                    className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-caption"
                  >
                    <div className="flex min-w-0 flex-col gap-0.5">
                      {item.reason === "NOT_FOUND" ? (
                        <span className="font-semibold text-text">{item.code}</span>
                      ) : (
                        <Link
                          href={
                            item.reason === "NOT_READY"
                              ? `/questions/${encodeURIComponent(item.id)}/edit`
                              : `/questions/${encodeURIComponent(item.id)}`
                          }
                          className={cn("w-fit rounded-sm font-semibold text-primary-text hover:underline", focusRing)}
                        >
                          {item.code}
                        </Link>
                      )}
                      <span className="text-text-secondary">{t(`skipReason.${item.reason}`)}</span>
                    </div>
                    <StatusChip tone={item.reason === "NOT_READY" ? "warning" : "neutral"}>{t("skipped")}</StatusChip>
                  </li>
                ))}
              </ul>
              <p className="text-caption text-text-muted">
                {result.action === "READY" ? t("resultReadyHint") : t("resultRetireHint")}
              </p>
              <DialogFooter>
                <Button onClick={() => setResult(null)}>{t("close")}</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
