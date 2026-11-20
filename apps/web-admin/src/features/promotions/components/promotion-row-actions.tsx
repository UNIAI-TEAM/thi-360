"use client";

import {
  ConfirmDialog,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  IconButton,
  notify,
} from "@thi360/ui";
import { Eye, MoreHorizontal, PauseCircle, Pencil, PlayCircle, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { promotionErrorCode, resolvePromotionErrorMessage } from "../errors";
import {
  useDeletePromotionMutation,
  useResumePromotionMutation,
  useStopPromotionMutation,
} from "../queries";
import type { PromotionSummary } from "../types";

type ActiveDialog = "stop" | "resume" | "delete" | null;

export function PromotionRowActions({
  promotion,
  onAnnounce,
  onMutationConflict,
}: {
  promotion: PromotionSummary;
  onAnnounce: (message: string) => void;
  onMutationConflict?: () => void;
}) {
  const t = useTranslations("promotions");
  const tErrors = useTranslations("promotions.errors");
  const router = useRouter();
  const stopMutation = useStopPromotionMutation();
  const resumeMutation = useResumePromotionMutation();
  const deleteMutation = useDeletePromotionMutation();
  const [dialog, setDialog] = useState<ActiveDialog>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const openDialog = (next: Exclude<ActiveDialog, null>) => {
    setDialogError(null);
    setDialog(next);
  };

  const canToggleStatus = promotion.state !== "ENDED";
  const isStopped = promotion.status === "STOPPED";
  const canDelete = promotion.usedCount === 0;
  const label = promotion.code ?? promotion.name;

  const handleConfirm = () => {
    setDialogError(null);
    if (dialog === "stop") {
      stopMutation.mutate(
        { id: promotion.id, expectedRevision: promotion.revision },
        {
          onSuccess: () => {
            setDialog(null);
            const msg = t("form.toast.stopped");
            notify.success(msg);
            onAnnounce(msg);
          },
          onError: (err) => {
            const msg = resolvePromotionErrorMessage(err, tErrors);
            setDialogError(msg);
            if (promotionErrorCode(err) === "VERSION_CONFLICT") {
              onMutationConflict?.();
            }
          },
        },
      );
      return;
    }

    if (dialog === "resume") {
      resumeMutation.mutate(
        { id: promotion.id, expectedRevision: promotion.revision },
        {
          onSuccess: () => {
            setDialog(null);
            const msg = t("form.toast.resumed");
            notify.success(msg);
            onAnnounce(msg);
          },
          onError: (err) => {
            const msg = resolvePromotionErrorMessage(err, tErrors);
            setDialogError(msg);
            if (promotionErrorCode(err) === "VERSION_CONFLICT") {
              onMutationConflict?.();
            }
          },
        },
      );
      return;
    }

    if (dialog === "delete") {
      deleteMutation.mutate(promotion.id, {
        onSuccess: () => {
          setDialog(null);
          const msg = t("form.toast.deleted");
          notify.success(msg);
          onAnnounce(msg);
        },
        onError: (err) => {
          if (promotionErrorCode(err) === "PROMOTION_IN_USE") {
            setDialogError(t("dialogs.delete.errorInUse"));
            onMutationConflict?.();
            return;
          }
          setDialogError(resolvePromotionErrorMessage(err, tErrors));
        },
      });
    }
  };

  const pending = stopMutation.isPending || resumeMutation.isPending || deleteMutation.isPending;

  const dialogConfig =
    dialog === "stop"
      ? {
          title: t("dialogs.stop.title"),
          description: t("dialogs.stop.description", { name: label }),
          confirmLabel: t("dialogs.stop.confirm"),
          tone: "danger" as const,
        }
      : dialog === "resume"
        ? {
            title: t("dialogs.resume.title"),
            description: t("dialogs.resume.description", { name: label }),
            confirmLabel: t("dialogs.resume.confirm"),
            tone: "primary" as const,
          }
        : dialog === "delete"
          ? {
              title: t("dialogs.delete.title"),
              description: t("dialogs.delete.description", { name: label }),
              confirmLabel: t("dialogs.delete.confirm"),
              tone: "danger" as const,
            }
          : null;

  return (
    <>
      <div className="flex items-center justify-end gap-1">
        <IconButton
          icon={<Pencil />}
          aria-label={`${t("actions.edit")}: ${label}`}
          tooltip={t("actions.edit")}
          onClick={() => router.push(`/promotions/${promotion.id}/edit`)}
        />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <IconButton icon={<MoreHorizontal />} aria-label={`${t("list.columns.actions")}: ${label}`} />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={() => router.push(`/promotions/${promotion.id}`)}>
              <Eye />
              {t("actions.viewDetail")}
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={() => router.push(`/promotions/${promotion.id}/edit`)}>
              <Pencil />
              {t("actions.edit")}
            </DropdownMenuItem>
            {canToggleStatus && (
              <DropdownMenuItem onSelect={() => openDialog(isStopped ? "resume" : "stop")}>
                {isStopped ? <PlayCircle /> : <PauseCircle />}
                {isStopped ? t("actions.resume") : t("actions.stop")}
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              destructive
              disabled={!canDelete}
              onSelect={() => {
                if (canDelete) openDialog("delete");
              }}
            >
              <Trash2 />
              {t("actions.delete")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {dialogConfig && (
        <ConfirmDialog
          open={dialog !== null}
          onOpenChange={(nextOpen) => {
            if (!pending && !nextOpen) {
              setDialogError(null);
              setDialog(null);
            }
          }}
          title={dialogConfig.title}
          description={dialogConfig.description}
          confirmLabel={dialogConfig.confirmLabel}
          cancelLabel={t("actions.cancel")}
          tone={dialogConfig.tone}
          loading={pending}
          error={dialogError}
          onConfirm={handleConfirm}
        />
      )}
    </>
  );
}
