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
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { Ban } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogItemResponse,
  type CatalogTreeNodeResponse,
  type FieldError,
} from "../api";
import {
  pickName,
  resolveCatalogErrorMessage,
  resolveCatalogUsageTypeKey,
} from "../utils";

export type DeleteInUseDialogProps = {
  open: boolean;
  item: CatalogItemResponse | CatalogTreeNodeResponse | null;
  fieldErrors: FieldError[];
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onSuggestStop: (item: CatalogItemResponse | CatalogTreeNodeResponse) => void;
};

export function DeleteInUseDialog({
  open,
  item,
  fieldErrors,
  api,
  onOpenChange,
  onSuggestStop,
}: DeleteInUseDialogProps) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();

  const usagesQuery = useQuery({
    queryKey: catalogQueryKeys.itemUsages(item?.id ?? "none"),
    queryFn: () => api.getItemUsages(item!.id),
    enabled: open && Boolean(item?.id),
  });

  const usages = usagesQuery.data?.usages ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && item && (
        <DialogContent closeLabel={t("close")} size="md">
          <DialogHeader>
            <DialogTitle>{t("deleteInUseDialog.title")}</DialogTitle>
            <DialogDescription>
              {t("deleteInUseDialog.description", {
                name: pickName(item, locale),
                code: item.code,
              })}
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-3">
            {usagesQuery.isPending ? (
              <div className="flex flex-col gap-2" role="status" aria-label={commonT("loading")}>
                <Skeleton className="h-12 w-full" />
              </div>
            ) : usages.length > 0 ? (
              <ul className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface">
                {usages.map((usage) => (
                  <li
                    key={`${usage.source}-${usage.objectType}`}
                    className="flex flex-col gap-1 px-3.5 py-2.5"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div>
                        <p className="text-field-label font-medium text-text">
                          {t(`usageTypes.${resolveCatalogUsageTypeKey(usage.objectType)}`)}
                        </p>
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
            ) : fieldErrors.length > 0 ? (
              <ul className="list-disc space-y-1 rounded-md border border-border bg-page py-2.5 pr-3.5 pl-8 text-field-label text-text">
                {fieldErrors.map((fe, idx) => (
                  <li key={`${fe.field}-${idx}`}>
                    {resolveCatalogErrorMessage(
                      { code: fe.code },
                      (code) => t(`errorCodes.${code}`),
                    )}
                  </li>
                ))}
              </ul>
            ) : null}

            {item.status === "ACTIVE" && (
              <p className="rounded-md border border-warning bg-warning-bg px-3.5 py-2.5 text-field-label text-warning">
                {t("deleteInUseDialog.suggestStopNote")}
              </p>
            )}
          </div>

          <DialogFooter className="pt-1">
            <Button variant="secondary" onClick={() => onOpenChange(false)}>
              {t("close")}
            </Button>
            {item.status === "ACTIVE" && (
              <Button
                variant="primary"
                onClick={() => {
                  onOpenChange(false);
                  onSuggestStop(item);
                }}
              >
                <Ban aria-hidden className="mr-1.5 size-4" />
                {t("deleteInUseDialog.suggestStop")}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      )}
    </Dialog>
  );
}
