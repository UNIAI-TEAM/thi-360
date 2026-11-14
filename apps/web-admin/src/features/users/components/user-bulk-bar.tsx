"use client";

import { useMutation } from "@tanstack/react-query";
import {
  Button,
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
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Can } from "@/features/session/can";
import type {
  BulkUserActionPreviewResponse,
  BulkUserActionResponse,
  UsersApiDeps,
} from "../api";
import { mapUserErrorCodeToKey, resolveUsersErrorMessage } from "../utils";

export const RBAC_BULK_USER_IDS_STORAGE_KEY = "rbac.bulk.userIds";

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
  selectedIds: string[];
  onClearSelection: () => void;
  onBulkApplied: () => void;
  previewBulkUserActions: UsersApiDeps["previewBulkUserActions"];
  applyBulkUserActions: UsersApiDeps["applyBulkUserActions"];
}

export function UserBulkBar({
  selectedIds,
  onClearSelection,
  onBulkApplied,
  previewBulkUserActions,
  applyBulkUserActions,
}: UserBulkBarProps) {
  const t = useTranslations("users");
  const router = useRouter();
  const [dialog, setDialog] = useState<BulkDialogState>({ kind: "none" });
  const [previewLoadingAction, setPreviewLoadingAction] = useState<BulkActionKind | null>(null);
  const [reason, setReason] = useState("");

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
    onError: (error) => {
      notify.error(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  function handleAssignRolesAndPermissions() {
    if (typeof window !== "undefined") {
      window.sessionStorage.setItem(RBAC_BULK_USER_IDS_STORAGE_KEY, JSON.stringify(selectedIds));
    }
    router.push("/phan-quyen?tab=gan-hang-loat");
  }

  async function handleStartBulkAction(action: BulkActionKind) {
    setPreviewLoadingAction(action);
    try {
      const preview = await previewBulkUserActions({ action, userIds: selectedIds });
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

  const requiresReason = dialog.kind === "confirm" && dialog.action !== "ACTIVATE";
  const trimmedReason = reason.trim();
  const canSubmitConfirm =
    dialog.kind === "confirm" &&
    dialog.preview.affected > 0 &&
    (!requiresReason || trimmedReason.length > 0);

  return (
    <>
      {selectedIds.length > 0 && (
        <div
          role="toolbar"
          aria-label={t("bulk.toolbarLabel")}
          className="flex flex-wrap items-center justify-between gap-3 border-b border-primary-200 bg-primary-50 px-4 py-2.5"
        >
          <div className="flex items-center gap-3">
            <span className="text-label font-semibold text-primary-text tabular-nums">
              {t("bulk.selectedCount", { count: selectedIds.length })}
            </span>
            <Button type="button" variant="link" size="sm" onClick={onClearSelection}>
              {t("bulk.clearSelection")}
            </Button>
          </div>

          <div className="flex flex-wrap items-center gap-2">
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
        </div>
      )}

      <Dialog
        open={dialog.kind === "confirm"}
        onOpenChange={(open) => {
          if (!applyMutation.isPending && !open) {
            setDialog({ kind: "none" });
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
                    affected: dialog.preview.affected,
                    total: dialog.preview.total,
                  })}
                </DialogDescription>
              </DialogHeader>

              {dialog.preview.affected === 0 && (
                <div className="rounded-lg border border-warning bg-warning-bg px-3 py-2 text-caption text-warning">
                  {t("bulk.noAffected")}
                </div>
              )}

              {dialog.preview.skipped.length > 0 && (
                <div className="flex flex-col gap-1.5">
                  <p className="text-caption font-semibold text-text-secondary">
                    {t("bulk.skippedHeading", { count: dialog.preview.skipped.length })}
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

              <DialogFooter>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={applyMutation.isPending}
                  onClick={() => setDialog({ kind: "none" })}
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
                    ? t("bulk.deactivateConfirm", { count: dialog.preview.affected })
                    : dialog.action === "ACTIVATE"
                      ? t("bulk.activateConfirm", { count: dialog.preview.affected })
                      : t("bulk.deleteConfirm", { count: dialog.preview.affected })}
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
                    succeeded: dialog.result.succeeded,
                    skipped: dialog.result.skipped,
                    failed: dialog.result.failed,
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
