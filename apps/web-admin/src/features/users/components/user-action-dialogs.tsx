"use client";

import { useMutation } from "@tanstack/react-query";
import {
  Button,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  notify,
} from "@thi360/ui";
import { AlertTriangle } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import {
  defaultUsersApiDeps,
  type UsersApiDeps,
  UsersApiError,
} from "../api";
import { mapUserErrorCodeToKey, resolveUsersErrorMessage } from "../utils";
import { ResetPasswordDialog, type ResetPasswordTarget } from "./reset-password-dialog";

const KNOWN_BLOCKER_SOURCES = {
  COMMERCE: true,
  EXAM: true,
  IDENTITY: true,
} as const;

type KnownBlockerSource = keyof typeof KNOWN_BLOCKER_SOURCES;

function isKnownBlockerSource(source?: string): source is KnownBlockerSource {
  return Boolean(source && Object.hasOwn(KNOWN_BLOCKER_SOURCES, source));
}

export interface UserActionSubject {
  id: string;
  username: string;
}

export interface DeleteBlockerItem {
  source: string;
  code: string;
  message: string;
}

export type ActiveUserDialogState =
  | { kind: "none" }
  | { kind: "reset-password"; user: ResetPasswordTarget }
  | { kind: "deactivate"; user: UserActionSubject; activeAttempt: boolean }
  | { kind: "activate"; user: UserActionSubject }
  | { kind: "delete"; user: UserActionSubject }
  | { kind: "blocked-delete"; user: UserActionSubject; blockers: DeleteBlockerItem[] };

export function useUserActionController({
  deps,
  onMutated,
}: {
  deps: Pick<
    UsersApiDeps,
    | "checkUserDeactivation"
    | "checkUserDeletion"
    | "deactivateUser"
    | "activateUser"
    | "deleteUser"
    | "resetUserPassword"
  >;
  onMutated: () => void;
}) {
  const t = useTranslations("users");
  const [dialog, setDialog] = useState<ActiveUserDialogState>({ kind: "none" });
  const [checkingAction, setCheckingAction] = useState(false);

  const deactivateMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      deps.deactivateUser(id, { reason }),
    onSuccess: (updated) => {
      notify.success(t("deactivateDialog.success", { username: updated.username }));
      setDialog({ kind: "none" });
      onMutated();
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

  const activateMutation = useMutation({
    mutationFn: (id: string) => deps.activateUser(id),
    onSuccess: (updated) => {
      notify.success(t("activateDialog.success", { username: updated.username }));
      setDialog({ kind: "none" });
      onMutated();
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

  const deleteMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; username: string; reason: string }) =>
      deps.deleteUser(id, reason),
    onSuccess: (_data, variables) => {
      notify.success(t("deleteDialog.success", { username: variables.username }));
      setDialog({ kind: "none" });
      onMutated();
    },
    onError: (error, variables) => {
      if (error instanceof UsersApiError && error.code === "USER_HAS_TRANSACTIONS") {
        const user = { id: variables.id, username: variables.username };
        const blockers: DeleteBlockerItem[] =
          error.fieldErrors.length > 0
            ? error.fieldErrors.map((fe) => ({
                source: fe.field,
                code: fe.code,
                message: fe.message,
              }))
            : [
                {
                  source: t("blockedDeleteDialog.fallbackSource"),
                  code: "USER_HAS_TRANSACTIONS",
                  message: t("errorCodes.USER_HAS_TRANSACTIONS"),
                },
              ];
        setDialog({ kind: "blocked-delete", user, blockers });
        return;
      }

      notify.error(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  async function openDeactivate(user: UserActionSubject) {
    setCheckingAction(true);
    try {
      const check = await deps.checkUserDeactivation(user.id);
      setDialog({ kind: "deactivate", user, activeAttempt: check.activeAttempt });
    } catch (error) {
      notify.error(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    } finally {
      setCheckingAction(false);
    }
  }

  async function openDelete(user: UserActionSubject) {
    setCheckingAction(true);
    try {
      const check = await deps.checkUserDeletion(user.id);
      if (!check.deletable) {
        setDialog({
          kind: "blocked-delete",
          user,
          blockers:
            check.blockers.length > 0
              ? check.blockers
              : [
                  {
                    source: t("blockedDeleteDialog.fallbackSource"),
                    code: "USER_HAS_TRANSACTIONS",
                    message: t("errorCodes.USER_HAS_TRANSACTIONS"),
                  },
                ],
        });
        return;
      }
      setDialog({ kind: "delete", user });
    } catch (error) {
      notify.error(
        resolveUsersErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    } finally {
      setCheckingAction(false);
    }
  }

  return {
    dialog,
    checkingAction,
    openResetPassword: (user: ResetPasswordTarget) => setDialog({ kind: "reset-password", user }),
    openDeactivate,
    openActivate: (user: UserActionSubject) => setDialog({ kind: "activate", user }),
    openDelete,
    closeDialog: () => setDialog({ kind: "none" }),
    deactivateMutation,
    activateMutation,
    deleteMutation,
    resetUserPassword: deps.resetUserPassword,
    onMutated,
  };
}

export function UserActionDialogs({
  controller,
}: {
  controller: ReturnType<typeof useUserActionController>;
}) {
  const t = useTranslations("users");
  const { dialog } = controller;

  return (
    <>
      <ResetPasswordDialog
        target={dialog.kind === "reset-password" ? dialog.user : null}
        onClose={controller.closeDialog}
        onSuccess={() => controller.onMutated()}
        resetUserPassword={controller.resetUserPassword}
      />

      <ConfirmDialog
        open={dialog.kind === "deactivate"}
        onOpenChange={(open) => {
          if (!open) controller.closeDialog();
        }}
        title={t("deactivateDialog.title")}
        description={
          dialog.kind === "deactivate" ? (
            <span className="flex flex-col gap-3">
              <span>{t("deactivateDialog.description", { username: dialog.user.username })}</span>
              {dialog.activeAttempt && (
                <span
                  role="alert"
                  className="flex items-start gap-2 rounded-lg border border-warning bg-warning-bg px-3 py-2 text-caption font-medium text-warning"
                >
                  <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0" />
                  <span>{t("deactivateDialog.activeAttemptWarning")}</span>
                </span>
              )}
            </span>
          ) : null
        }
        reasonLabel={t("deactivateDialog.reasonLabel")}
        requireReason
        confirmLabel={t("deactivateDialog.confirm")}
        cancelLabel={t("deactivateDialog.cancel")}
        closeLabel={t("deactivateDialog.close")}
        tone="danger"
        loading={controller.deactivateMutation.isPending}
        onConfirm={(reason) => {
          if (dialog.kind === "deactivate") {
            controller.deactivateMutation.mutate({ id: dialog.user.id, reason });
          }
        }}
      />

      <ConfirmDialog
        open={dialog.kind === "activate"}
        onOpenChange={(open) => {
          if (!open) controller.closeDialog();
        }}
        title={t("activateDialog.title")}
        description={
          dialog.kind === "activate"
            ? t("activateDialog.description", { username: dialog.user.username })
            : null
        }
        confirmLabel={t("activateDialog.confirm")}
        cancelLabel={t("activateDialog.cancel")}
        closeLabel={t("activateDialog.close")}
        tone="primary"
        loading={controller.activateMutation.isPending}
        onConfirm={() => {
          if (dialog.kind === "activate") {
            controller.activateMutation.mutate(dialog.user.id);
          }
        }}
      />

      <ConfirmDialog
        open={dialog.kind === "delete"}
        onOpenChange={(open) => {
          if (!open) controller.closeDialog();
        }}
        title={t("deleteDialog.title")}
        description={
          dialog.kind === "delete"
            ? t("deleteDialog.description", { username: dialog.user.username })
            : null
        }
        reasonLabel={t("deleteDialog.reasonLabel")}
        requireReason
        confirmLabel={t("deleteDialog.confirm")}
        cancelLabel={t("deleteDialog.cancel")}
        closeLabel={t("deleteDialog.close")}
        tone="danger"
        loading={controller.deleteMutation.isPending}
        onConfirm={(reason) => {
          if (dialog.kind === "delete") {
            controller.deleteMutation.mutate({
              id: dialog.user.id,
              username: dialog.user.username,
              reason,
            });
          }
        }}
      />

      <Dialog
        open={dialog.kind === "blocked-delete"}
        onOpenChange={(open) => {
          if (!open) controller.closeDialog();
        }}
      >
        <DialogContent size="md" closeLabel={t("blockedDeleteDialog.close")}>
          <DialogHeader>
            <DialogTitle>{t("blockedDeleteDialog.title")}</DialogTitle>
            <DialogDescription>
              {dialog.kind === "blocked-delete"
                ? t("blockedDeleteDialog.description", { username: dialog.user.username })
                : ""}
            </DialogDescription>
          </DialogHeader>

          {dialog.kind === "blocked-delete" && (
            <ul className="flex flex-col gap-2">
              {dialog.blockers.map((blocker, index) => {
                const sourceLabel = isKnownBlockerSource(blocker.source)
                  ? t(`blockedDeleteDialog.sources.${blocker.source}`)
                  : t("blockedDeleteDialog.fallbackSource");
                const codeKey = mapUserErrorCodeToKey(blocker.code);
                const messageText = codeKey
                  ? t(`errorCodes.${codeKey}`)
                  : t("errorCodes.USER_HAS_TRANSACTIONS");
                return (
                  <li
                    key={`${blocker.source}-${blocker.code}-${index}`}
                    className="rounded-lg border border-border bg-subtle px-3 py-2 text-caption text-text"
                  >
                    <span className="font-semibold">{sourceLabel}: </span>
                    <span>{messageText}</span>
                  </li>
                );
              })}
            </ul>
          )}

          <DialogFooter>
            <Button variant="secondary" onClick={controller.closeDialog}>
              {t("blockedDeleteDialog.close")}
            </Button>
            {dialog.kind === "blocked-delete" && (
              <Button
                variant="danger"
                loading={controller.checkingAction}
                onClick={() => {
                  const target = dialog.user;
                  void controller.openDeactivate(target);
                }}
              >
                {t("blockedDeleteDialog.deactivateInstead")}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

export type UserActionController = ReturnType<typeof useUserActionController>;

export { defaultUsersApiDeps };
