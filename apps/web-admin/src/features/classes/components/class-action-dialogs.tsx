"use client";

import { useMutation } from "@tanstack/react-query";
import { ConfirmDialog, notify } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { type ClassesApiDeps } from "../api";
import { resolveClassesErrorMessage } from "../utils";

export interface ClassActionSubject {
  id: string;
  code: string;
  name: string;
  version: number;
}

export type ActiveClassDialogState =
  | { kind: "none" }
  | { kind: "deactivate"; target: ClassActionSubject }
  | { kind: "activate"; target: ClassActionSubject }
  | { kind: "delete"; target: ClassActionSubject };

export function useClassActionController({
  deps,
  onMutated,
  onDeleted,
}: {
  deps: Pick<ClassesApiDeps, "updateClassStatus" | "deleteClass">;
  onMutated: () => void;
  onDeleted?: (deletedId: string) => void;
}) {
  const t = useTranslations("classes");
  const [dialog, setDialog] = useState<ActiveClassDialogState>({ kind: "none" });

  const deactivateMutation = useMutation({
    mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) =>
      deps.updateClassStatus(id, { status: "INACTIVE", reason, version }),
    onSuccess: (updated) => {
      notify.success(t("deactivateDialog.success", { code: updated.code }));
      setDialog({ kind: "none" });
      onMutated();
    },
    onError: (error) => {
      notify.error(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const activateMutation = useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      deps.updateClassStatus(id, { status: "ACTIVE", version }),
    onSuccess: (updated) => {
      notify.success(t("activateDialog.success", { code: updated.code }));
      setDialog({ kind: "none" });
      onMutated();
    },
    onError: (error) => {
      notify.error(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  const deleteMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; code: string; reason: string }) =>
      deps.deleteClass(id, reason),
    onSuccess: (_data, variables) => {
      notify.success(t("deleteDialog.success", { code: variables.code }));
      setDialog({ kind: "none" });
      onMutated();
      onDeleted?.(variables.id);
    },
    onError: (error) => {
      notify.error(
        resolveClassesErrorMessage(
          error,
          (code) => t(`errorCodes.${code}`),
          t("errorCodes.INTERNAL_ERROR"),
        ),
      );
    },
  });

  return {
    dialog,
    openDeactivate: (target: ClassActionSubject) => setDialog({ kind: "deactivate", target }),
    openActivate: (target: ClassActionSubject) => setDialog({ kind: "activate", target }),
    openDelete: (target: ClassActionSubject) => setDialog({ kind: "delete", target }),
    closeDialog: () => setDialog({ kind: "none" }),
    deactivatePending: deactivateMutation.isPending,
    activatePending: activateMutation.isPending,
    deletePending: deleteMutation.isPending,
    confirmDeactivate: (id: string, version: number, reason: string) =>
      deactivateMutation.mutate({ id, version, reason }),
    confirmActivate: (id: string, version: number) => activateMutation.mutate({ id, version }),
    confirmDelete: (id: string, code: string, reason: string) =>
      deleteMutation.mutate({ id, code, reason }),
  };
}

export interface ClassActionController {
  dialog: ActiveClassDialogState;
  openDeactivate: (target: ClassActionSubject) => void;
  openActivate: (target: ClassActionSubject) => void;
  openDelete: (target: ClassActionSubject) => void;
  closeDialog: () => void;
  deactivatePending: boolean;
  activatePending: boolean;
  deletePending: boolean;
  confirmDeactivate: (id: string, version: number, reason: string) => void;
  confirmActivate: (id: string, version: number) => void;
  confirmDelete: (id: string, code: string, reason: string) => void;
}

export function ClassActionDialogs({ controller }: { controller: ClassActionController }) {
  const t = useTranslations("classes");
  const { dialog } = controller;

  return (
    <>
      <ConfirmDialog
        open={dialog.kind === "deactivate"}
        onOpenChange={(open) => {
          if (!open) controller.closeDialog();
        }}
        title={t("deactivateDialog.title")}
        description={
          dialog.kind === "deactivate"
            ? t("deactivateDialog.description", {
                code: dialog.target.code,
                name: dialog.target.name,
              })
            : null
        }
        reasonLabel={t("deactivateDialog.reasonLabel")}
        requireReason
        confirmLabel={t("deactivateDialog.confirm")}
        cancelLabel={t("deactivateDialog.cancel")}
        closeLabel={t("deactivateDialog.close")}
        tone="danger"
        loading={controller.deactivatePending}
        onConfirm={(reason) => {
          if (dialog.kind === "deactivate") {
            controller.confirmDeactivate(dialog.target.id, dialog.target.version, reason);
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
            ? t("activateDialog.description", {
                code: dialog.target.code,
                name: dialog.target.name,
              })
            : null
        }
        confirmLabel={t("activateDialog.confirm")}
        cancelLabel={t("activateDialog.cancel")}
        closeLabel={t("activateDialog.close")}
        tone="primary"
        loading={controller.activatePending}
        onConfirm={() => {
          if (dialog.kind === "activate") {
            controller.confirmActivate(dialog.target.id, dialog.target.version);
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
            ? t("deleteDialog.description", {
                code: dialog.target.code,
                name: dialog.target.name,
              })
            : null
        }
        reasonLabel={t("deleteDialog.reasonLabel")}
        requireReason
        confirmLabel={t("deleteDialog.confirm")}
        cancelLabel={t("deleteDialog.cancel")}
        closeLabel={t("deleteDialog.close")}
        tone="danger"
        loading={controller.deletePending}
        onConfirm={(reason) => {
          if (dialog.kind === "delete") {
            controller.confirmDelete(dialog.target.id, dialog.target.code, reason);
          }
        }}
      />
    </>
  );
}
