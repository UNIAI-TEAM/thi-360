"use client";

import { useMutation } from "@tanstack/react-query";
import {
  Button,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  notify,
  StatusChip,
  Textarea,
} from "@thi360/ui";
import { CircleHelp } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Can } from "@/features/session/can";
import type {
  BulkUserActionPreviewResponse,
  BulkUserActionResponse,
  UsersApiDeps,
} from "../api";
import { mapUserErrorCodeToKey, resolveUsersErrorMessage } from "../utils";

export const RBAC_BULK_USER_IDS_STORAGE_KEY = "rbac.bulk.userIds";

/** Số tên đăng nhập nêu đích danh trong hộp thoại xác nhận; phần còn lại gộp thành "và N tài khoản khác". */
const AFFECTED_NAME_PREVIEW_LIMIT = 5;

export interface SelectedAccount {
  id: string;
  username: string;
}

type BulkActionKind = "DEACTIVATE" | "ACTIVATE" | "DELETE";

type BulkDialogState =
  | { kind: "none" }
  | {
      kind: "confirm";
      action: BulkActionKind;
      preview: BulkUserActionPreviewResponse;
      idempotencyKey: string;
    }
  | {
      kind: "result";
      result: BulkUserActionResponse;
    };

export interface UserBulkBarProps {
  /** Tài khoản đang chọn (có thể gồm dòng ở trang khác). Rỗng thì thanh vẫn giữ chỗ nhưng ẩn và không nhận thao tác. */
  selected: SelectedAccount[];
  onClearSelection: () => void;
  onBulkApplied: () => void;
  previewBulkUserActions: UsersApiDeps["previewBulkUserActions"];
  applyBulkUserActions: UsersApiDeps["applyBulkUserActions"];
  className?: string;
}

/**
 * Thanh thao tác hàng loạt. Thanh LUÔN có trong DOM và nằm chồng lên dải "N tài khoản / gợi ý" trong cùng một ô
 * lưới (bên gọi đặt `className`), nên khi tích dòng đầu tiên chỉ đổi phần đang hiện, chiều cao không đổi và các
 * dòng của bảng không dịch chuyển dưới con trỏ. Lúc chưa chọn dòng nào thanh ẩn (`invisible`) và `inert`.
 */
export function UserBulkBar({
  selected,
  onClearSelection,
  onBulkApplied,
  previewBulkUserActions,
  applyBulkUserActions,
  className,
}: UserBulkBarProps) {
  const t = useTranslations("users");
  const format = useFormatter();
  const router = useRouter();
  const [dialog, setDialog] = useState<BulkDialogState>({ kind: "none" });
  const [previewLoadingAction, setPreviewLoadingAction] = useState<BulkActionKind | null>(null);
  const [reason, setReason] = useState("");
  const [helpOpen, setHelpOpen] = useState(false);
  const helpId = useId();
  const active = selected.length > 0;
  const selectedIds = selected.map((account) => account.id);

  const applyMutation = useMutation({
    mutationFn: ({
      action,
      userIds,
      reasonText,
      idempotencyKey,
    }: {
      action: BulkActionKind;
      userIds: string[];
      reasonText: string;
      idempotencyKey: string;
    }) =>
      applyBulkUserActions(
        {
          action,
          userIds,
          ...(action !== "ACTIVATE" ? { reason: reasonText } : {}),
        },
        idempotencyKey,
      ),
    onSuccess: (result) => {
      setReason("");
      onClearSelection();
      onBulkApplied();
      if (result.skipped > 0 || result.failed > 0) {
        setDialog({ kind: "result", result });
      } else {
        setDialog({ kind: "none" });
        notify.success(
          t("bulk.resultSummary", {
            succeeded: result.succeeded,
            skipped: result.skipped,
            failed: result.failed,
          }),
        );
      }
    },
  });
  // Lỗi của lần áp dụng vừa rồi hiện ngay trong hộp thoại xác nhận (xem ghi chú ở user-action-dialogs).
  const applyError = applyMutation.isError
    ? resolveUsersErrorMessage(
        applyMutation.error,
        (code) => t(`errorCodes.${code}`),
        t("errorCodes.INTERNAL_ERROR"),
      )
    : null;

  function closeConfirmDialog() {
    applyMutation.reset();
    setDialog({ kind: "none" });
  }

  function handleAssignRolesAndPermissions() {
    if (typeof window !== "undefined") {
      window.sessionStorage.setItem(RBAC_BULK_USER_IDS_STORAGE_KEY, JSON.stringify(selectedIds));
    }
    router.push("/rbac/bulk-assign");
  }

  async function handleStartBulkAction(action: BulkActionKind) {
    setPreviewLoadingAction(action);
    try {
      const preview = await previewBulkUserActions({ action, userIds: selectedIds });
      applyMutation.reset();
      setReason("");
      setDialog({
        kind: "confirm",
        action,
        preview,
        idempotencyKey: globalThis.crypto.randomUUID(),
      });
    } catch (error) {
      notify.error(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    } finally {
      setPreviewLoadingAction(null);
    }
  }

  function handleClearSelection() {
    setHelpOpen(false);
    onClearSelection();
  }

  // Tài khoản thật sự bị tác động = đang chọn trừ các tài khoản bản xem trước báo bỏ qua.
  const affectedAccounts =
    dialog.kind === "confirm"
      ? selected.filter(
          (account) => !dialog.preview.skipped.some((skipped) => skipped.id === account.id),
        )
      : [];
  const affectedNames = affectedAccounts
    .slice(0, AFFECTED_NAME_PREVIEW_LIMIT)
    .map((account) => account.username)
    .join(", ");
  const affectedRest = Math.max(0, affectedAccounts.length - AFFECTED_NAME_PREVIEW_LIMIT);

  const requiresReason = dialog.kind === "confirm" && dialog.action !== "ACTIVATE";
  const trimmedReason = reason.trim();
  const canSubmitConfirm =
    dialog.kind === "confirm" &&
    dialog.preview.affected > 0 &&
    (!requiresReason || trimmedReason.length > 0);

  return (
    <>
        {/* Năm nút là năm điểm dừng Tab bình thường nên đây là một nhóm có tên, không phải role="toolbar". */}
        <div
          role="group"
          aria-label={t("bulk.toolbarLabel")}
          aria-hidden={!active || undefined}
          inert={!active}
          onKeyDown={(event) => {
            if (event.key === "Escape" && helpOpen) {
              event.stopPropagation();
              setHelpOpen(false);
            }
          }}
          className={cn(
            "relative flex flex-wrap items-center justify-between gap-x-3 gap-y-2 bg-primary-50 px-4 py-2.5",
            !active && "invisible",
            className,
          )}
        >
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-label font-semibold text-primary-text tabular-nums">
              {t("bulk.selectedCount", { count: format.number(selected.length) })}
            </span>
            <Button type="button" variant="link" size="sm" onClick={handleClearSelection}>
              {t("bulk.clearSelection")}
            </Button>
            <Button
              type="button"
              variant="link"
              size="sm"
              aria-expanded={helpOpen}
              aria-controls={helpOpen ? helpId : undefined}
              onClick={() => setHelpOpen((open) => !open)}
            >
              <CircleHelp />
              {/* Khung hẹp chỉ còn icon để hàng đầu vừa một dòng; chữ vẫn là tên của nút. */}
              <span className="@max-md:sr-only">{t("bulk.helpToggle")}</span>
            </Button>
          </div>

          {/*
            Khung hẹp (dưới 40rem): các nút thao tác nằm trên một hàng tự cuộn ngang thay vì xuống hai, ba dòng, để
            thanh luôn cao hai hàng, xấp xỉ dải "N tài khoản / gợi ý" mà nó thay chỗ: ô giữ sẵn không thừa một khoảng
            trống lớn trên bảng. Đệm 4px bù bằng lề âm để vùng cuộn không cắt mất vòng focus của nút.
          */}
          <div className="flex flex-wrap items-center gap-2 @max-[40rem]:-m-1 @max-[40rem]:w-[calc(100%+0.5rem)] @max-[40rem]:flex-nowrap @max-[40rem]:overflow-x-auto @max-[40rem]:p-1 @max-[40rem]:[scrollbar-width:thin]">
            <Can permission="USER.ASSIGN">
              <Button type="button" size="sm" onClick={handleAssignRolesAndPermissions}>
                {t("bulk.assignRolePermission")}
              </Button>
            </Can>

            <Can permission="USER.MODIFY">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                loading={previewLoadingAction === "DEACTIVATE"}
                disabled={previewLoadingAction !== null}
                onClick={() => void handleStartBulkAction("DEACTIVATE")}
              >
                {t("actions.deactivate")}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                loading={previewLoadingAction === "ACTIVATE"}
                disabled={previewLoadingAction !== null}
                onClick={() => void handleStartBulkAction("ACTIVATE")}
              >
                {t("actions.activate")}
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                loading={previewLoadingAction === "DELETE"}
                disabled={previewLoadingAction !== null}
                onClick={() => void handleStartBulkAction("DELETE")}
              >
                {t("actions.delete")}
              </Button>
            </Can>
          </div>

          {/* Ghi chú dài mở theo yêu cầu và nổi đè lên đầu bảng, không chiếm chỗ trong luồng nên không đẩy dòng. */}
          {helpOpen && active && (
            <p
              id={helpId}
              className="absolute inset-x-4 top-full z-20 mt-1 rounded-lg border border-border bg-surface px-3 py-2 text-caption text-text-secondary shadow-md"
            >
              {t("bulk.helperNote", { count: format.number(selected.length) })}
            </p>
          )}
        </div>

      <Dialog
        open={dialog.kind === "confirm"}
        onOpenChange={(open) => {
          if (!applyMutation.isPending && !open) {
            closeConfirmDialog();
          }
        }}
      >
        <DialogContent
          size="md"
          closeLabel={t("bulk.close")}
          onEscapeKeyDown={(event) => {
            if (applyMutation.isPending) event.preventDefault();
          }}
          onPointerDownOutside={(event) => {
            if (applyMutation.isPending) event.preventDefault();
          }}
        >
          {dialog.kind === "confirm" && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {dialog.action === "DEACTIVATE"
                    ? t("bulk.deactivateTitle")
                    : dialog.action === "ACTIVATE"
                      ? t("bulk.activateTitle")
                      : t("bulk.deleteTitle")}
                </DialogTitle>
                <DialogDescription>
                  {t("bulk.confirmSummary", {
                    affected: format.number(dialog.preview.affected),
                    total: format.number(dialog.preview.total),
                  })}
                </DialogDescription>
              </DialogHeader>

              {affectedAccounts.length > 0 && (
                <p className="text-body text-text">
                  {affectedRest > 0
                    ? t("bulk.affectedAccountsMore", {
                        names: affectedNames,
                        count: format.number(affectedRest),
                      })
                    : t("bulk.affectedAccounts", { names: affectedNames })}
                </p>
              )}

              {dialog.preview.affected === 0 && (
                <div className="rounded-lg border border-warning bg-warning-bg px-3 py-2 text-caption text-warning">
                  {t("bulk.noAffected")}
                </div>
              )}

              {dialog.preview.skipped.length > 0 && (
                <div className="flex flex-col gap-1.5">
                  <p className="text-caption font-semibold text-text-secondary">
                    {t("bulk.skippedHeading", { count: format.number(dialog.preview.skipped.length) })}
                  </p>
                  <ul className="flex max-h-40 flex-col gap-1 overflow-y-auto rounded-lg border border-border bg-subtle p-2">
                    {dialog.preview.skipped.map((item) => {
                      const mappedKey = mapUserErrorCodeToKey(item.reasonCode);
                      return (
                        <li key={item.id} className="text-caption text-text">
                          <span className="font-semibold">{item.username ?? item.id}: </span>
                          <span>
                            {mappedKey ? t(`errorCodes.${mappedKey}`) : t("errorCodes.INTERNAL_ERROR")}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              )}

              {requiresReason && dialog.preview.affected > 0 && (
                <FormField label={t("bulk.reasonLabel")} required>
                  {(fieldProps) => (
                    <Textarea
                      {...fieldProps}
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      disabled={applyMutation.isPending}
                      rows={2}
                    />
                  )}
                </FormField>
              )}

              {applyError && (
                <div
                  role="alert"
                  className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
                >
                  {applyError}
                </div>
              )}

              <DialogFooter>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={applyMutation.isPending}
                  onClick={closeConfirmDialog}
                >
                  {t("bulk.cancel")}
                </Button>
                <Button
                  type="button"
                  variant={dialog.action === "ACTIVATE" ? "primary" : "danger"}
                  loading={applyMutation.isPending}
                  disabled={!canSubmitConfirm}
                  onClick={() => {
                    applyMutation.mutate({
                      action: dialog.action,
                      userIds: selectedIds,
                      reasonText: trimmedReason,
                      idempotencyKey: dialog.idempotencyKey,
                    });
                  }}
                >
                  {dialog.action === "DEACTIVATE"
                    ? t("bulk.deactivateConfirm", { count: format.number(dialog.preview.affected) })
                    : dialog.action === "ACTIVATE"
                      ? t("bulk.activateConfirm", { count: format.number(dialog.preview.affected) })
                      : t("bulk.deleteConfirm", { count: format.number(dialog.preview.affected) })}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={dialog.kind === "result"}
        onOpenChange={(open) => {
          if (!open) setDialog({ kind: "none" });
        }}
      >
        <DialogContent size="md" closeLabel={t("bulk.close")}>
          {dialog.kind === "result" && (
            <>
              <DialogHeader>
                <DialogTitle>{t("bulk.resultTitle")}</DialogTitle>
                <DialogDescription>
                  {t("bulk.resultSummary", {
                    succeeded: format.number(dialog.result.succeeded),
                    skipped: format.number(dialog.result.skipped),
                    failed: format.number(dialog.result.failed),
                  })}
                </DialogDescription>
              </DialogHeader>

              <ul className="flex max-h-60 flex-col gap-1.5 overflow-y-auto">
                {dialog.result.results.map((item) => {
                  const mappedKey = mapUserErrorCodeToKey(item.reasonCode);
                  const tone =
                    item.outcome === "SUCCEEDED"
                      ? "success"
                      : item.outcome === "SKIPPED"
                        ? "warning"
                        : "danger";
                  return (
                    <li
                      key={item.id}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border bg-subtle px-3 py-2 text-caption"
                    >
                      <div className="min-w-0">
                        <span className="font-semibold text-text">
                          {item.username ?? item.id}
                        </span>
                        {(mappedKey || item.outcome !== "SUCCEEDED") && (
                          <p className="text-text-secondary">
                            {mappedKey ? t(`errorCodes.${mappedKey}`) : t("errorCodes.INTERNAL_ERROR")}
                          </p>
                        )}
                      </div>
                      <StatusChip tone={tone}>
                        {t(`bulk.resultItemStatus.${item.outcome}`)}
                      </StatusChip>
                    </li>
                  );
                })}
              </ul>

              <DialogFooter>
                <Button type="button" onClick={() => setDialog({ kind: "none" })}>
                  {t("bulk.resultClose")}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
