"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  FormField,
  Skeleton,
  StatusChip,
  Textarea,
} from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
} from "../api";
import { getErrorStatus } from "@/lib/api/query-client";
import { pickName, resolveCatalogErrorMessage } from "../utils";

export type StopItemDialogProps = {
  open: boolean;
  group: CatalogGroupResponse;
  item: CatalogItemResponse | null;
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onConfirmStop: (item: CatalogItemResponse, reason: string) => Promise<void>;
};

export function StopItemDialog({
  open,
  group,
  item,
  api,
  onOpenChange,
  onConfirmStop,
}: StopItemDialogProps) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const [submitting, setSubmitting] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (submitting && !next) return;
        if (!next) setSubmitting(false);
        onOpenChange(next);
      }}
    >
      {open && item && (
        <DialogContent closeLabel={t("close")} size="md">
          <DialogHeader>
            <DialogTitle>{t("stopItemDialog.title")}</DialogTitle>
            <DialogDescription>
              {t("stopItemDialog.description", {
                name: pickName(item, locale),
                code: item.code,
              })}
            </DialogDescription>
          </DialogHeader>
          <StopItemBody
            key={item.id}
            group={group}
            item={item}
            api={api}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onConfirmStop={onConfirmStop}
          />
        </DialogContent>
      )}
    </Dialog>
  );
}

function StopItemBody({
  group,
  item,
  api,
  onCancel,
  onSubmittingChange,
  onConfirmStop,
}: {
  group: CatalogGroupResponse;
  item: CatalogItemResponse;
  api: CatalogApi;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onConfirmStop: (item: CatalogItemResponse, reason: string) => Promise<void>;
}) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const usagesQuery = useQuery({
    queryKey: catalogQueryKeys.itemUsages(item.id),
    queryFn: () => api.getItemUsages(item.id),
  });

  const isBlocked = Boolean(usagesQuery.data?.blocking);
  const canSubmit =
    !usagesQuery.isPending && !usagesQuery.isError && !isBlocked && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonError(t("validation.reasonRequired"));
      return;
    }
    setReasonError(null);
    setServerError(null);
    setSubmitting(true);
    onSubmittingChange(true);
    try {
      await onConfirmStop(item, trimmed);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      setServerError(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
    } finally {
      setSubmitting(false);
      onSubmittingChange(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-page px-3.5 py-2.5">
        <span className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
          {item.code}
        </span>
        <span className="font-semibold text-text">{pickName(item, locale)}</span>
        <span className="ml-auto text-caption text-text-muted">{pickName(group, locale)}</span>
      </div>

      <div className="flex flex-col gap-1.5">
        <h3 className="text-field-label font-semibold text-text">
          {t("stopItemDialog.impactTitle")}
        </h3>
        <ul className="list-disc space-y-1 pl-5 text-field-label text-text-secondary">
          <li>{t("stopItemDialog.impactBullet1")}</li>
          <li>{t("stopItemDialog.impactBullet2")}</li>
        </ul>
      </div>

      <div className="flex flex-col gap-1.5">
        <h3 className="text-field-label font-semibold text-text">
          {t("stopItemDialog.usagesTitle")}
        </h3>

        {usagesQuery.isPending ? (
          <div className="flex flex-col gap-2" role="status" aria-label={commonT("loading")}>
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : usagesQuery.isError ? (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("stopItemDialog.usagesLoadError")}
            action={
              <Button variant="secondary" onClick={() => void usagesQuery.refetch()}>
                {commonT("retry")}
              </Button>
            }
          />
        ) : !usagesQuery.data || usagesQuery.data.usages.length === 0 ? (
          <p className="rounded-md border border-border bg-page px-3.5 py-2.5 text-field-label text-text-secondary">
            {t("detailDrawer.usagesEmpty")}
          </p>
        ) : (
          <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
            {usagesQuery.data.usages.map((usage) => (
              <li
                key={`${usage.source}-${usage.objectType}`}
                className="flex flex-col gap-1 px-3.5 py-2.5"
              >
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <p className="text-field-label font-medium text-text">{usage.source}</p>
                    <p className="text-caption text-text-muted">
                      {t("detailDrawer.usageCount", { count: usage.count })}
                    </p>
                  </div>
                  <StatusChip tone={usage.blocking ? "danger" : "neutral"}>
                    {usage.blocking
                      ? t("detailDrawer.usagesBlocking")
                      : t("detailDrawer.usagesNonBlocking")}
                  </StatusChip>
                </div>
                {usage.sampleLabels.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 pt-0.5">
                    {usage.sampleLabels.map((label) => (
                      <Badge key={label}>{label}</Badge>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      {isBlocked && (
        <div
          role="alert"
          className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
        >
          {t("stopItemDialog.blockedAlert")}
        </div>
      )}

      {serverError && (
        <div
          role="alert"
          className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
        >
          {serverError}
        </div>
      )}

      {!isBlocked && !usagesQuery.isPending && !usagesQuery.isError && (
        <FormField
          label={t("stopItemDialog.reasonLabel")}
          required
          error={reasonError ?? undefined}
        >
          {(fieldProps) => (
            <Textarea
              {...fieldProps}
              rows={2}
              value={reason}
              placeholder={t("stopItemDialog.reasonPlaceholder")}
              onChange={(event) => {
                setReason(event.target.value);
                if (reasonError) setReasonError(null);
              }}
              disabled={submitting}
            />
          )}
        </FormField>
      )}

      <DialogFooter className="pt-1">
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          {t("cancel")}
        </Button>
        <Button
          variant="primary"
          loading={submitting}
          disabled={!canSubmit}
          onClick={() => void handleSubmit()}
        >
          {t("stopItemDialog.confirm")}
        </Button>
      </DialogFooter>
    </div>
  );
}
