"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Badge,
  Button,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  Input,
  Skeleton,
} from "@thi360/ui";
import { Search, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type CatalogTreeNodeResponse,
} from "../api";
import { getErrorStatus } from "@/lib/api/query-client";
import { pickName, resolveCatalogErrorMessage } from "../utils";

export type MoveBranchDialogProps = {
  open: boolean;
  group: CatalogGroupResponse;
  node: CatalogItemResponse | CatalogTreeNodeResponse | null;
  api: CatalogApi;
  onOpenChange: (open: boolean) => void;
  onMoved: (updated: CatalogItemResponse) => Promise<void>;
};

export function MoveBranchDialog({
  open,
  group,
  node,
  api,
  onOpenChange,
  onMoved,
}: MoveBranchDialogProps) {
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
      {open && node && (
        <DialogContent closeLabel={t("close")} size="md">
          <DialogHeader>
            <DialogTitle>{t("moveBranchDialog.title")}</DialogTitle>
            <DialogDescription>{pickName(group, locale)}</DialogDescription>
          </DialogHeader>
          <MoveBranchBody
            key={node.id}
            group={group}
            node={node}
            api={api}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onMoved={onMoved}
          />
        </DialogContent>
      )}
    </Dialog>
  );
}

function MoveBranchBody({
  group,
  node,
  api,
  onCancel,
  onSubmittingChange,
  onMoved,
}: {
  group: CatalogGroupResponse;
  node: CatalogItemResponse | CatalogTreeNodeResponse;
  api: CatalogApi;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onMoved: (updated: CatalogItemResponse) => Promise<void>;
}) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedParentId, setSelectedParentId] = useState<string>("");
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const targetDepth = Math.max(1, node.depth - 1);
  const candidatesQuery = useQuery({
    queryKey: catalogQueryKeys.tree(group.id, { depth: targetDepth }),
    queryFn: () => api.listTree(group.id, { depth: targetDepth }),
    enabled: node.depth > 1,
  });

  const filteredCandidates = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return (candidatesQuery.data ?? []).filter((candidate) => {
      if (candidate.id === node.id) return false;
      if (!q) return true;
      return (
        candidate.code.toLowerCase().includes(q) ||
        candidate.nameVi.toLowerCase().includes(q) ||
        (candidate.nameEn ?? "").toLowerCase().includes(q)
      );
    });
  }, [candidatesQuery.data, node.id, searchQuery]);

  const levels = group.levels ?? [];
  const levelName = levels[node.depth - 1] ?? t("tree.rootNodeBadge");

  async function handleSubmit() {
    if (!selectedParentId || submitting) return;
    setServerError(null);
    setSubmitting(true);
    onSubmittingChange(true);
    try {
      const updated = await api.moveItem(node.id, {
        parentId: selectedParentId,
        version: node.version,
      });
      await onMoved(updated);
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
      <div className="rounded-md border border-border bg-page px-3.5 py-2.5 text-field-label text-text-secondary">
        {node.childCount > 0
          ? t("moveBranchDialog.summaryWithChildren", {
              levelName,
              code: node.code,
              name: pickName(node, locale),
              count: node.childCount,
            })
          : t("moveBranchDialog.summaryLeaf", {
              levelName,
              code: node.code,
              name: pickName(node, locale),
            })}
      </div>

      <Input
        type="search"
        aria-label={t("moveBranchDialog.searchPlaceholder")}
        placeholder={t("moveBranchDialog.searchPlaceholder")}
        leadingIcon={<Search />}
        value={searchQuery}
        onChange={(event) => setSearchQuery(event.target.value)}
        disabled={submitting}
      />

      {candidatesQuery.isPending ? (
        <div className="flex flex-col gap-2" role="status" aria-label={commonT("loading")}>
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : candidatesQuery.isError ? (
        <EmptyState
          icon={<TriangleAlert />}
          title={t("itemsErrorTitle")}
          action={
            <Button variant="secondary" onClick={() => void candidatesQuery.refetch()}>
              {commonT("retry")}
            </Button>
          }
        />
      ) : filteredCandidates.length === 0 ? (
        <p className="rounded-md border border-border bg-page px-3.5 py-4 text-center text-field-label text-text-muted">
          {t("moveBranchDialog.noCandidates")}
        </p>
      ) : (
        <div
          role="radiogroup"
          aria-label={t("moveBranchDialog.parentListLabel")}
          className="flex max-h-60 flex-col gap-2 overflow-y-auto pr-1"
        >
          {filteredCandidates.map((candidate) => {
            const isCurrent = candidate.id === node.parentId;
            const isInactive = candidate.effectiveStatus !== "ACTIVE";
            const disabled = isCurrent || isInactive || submitting;
            const checked = selectedParentId === candidate.id;

            return (
              <label
                key={candidate.id}
                className={cn(
                  "flex min-h-14 items-center gap-2.5 rounded-md border px-3 py-2 transition-colors",
                  disabled
                    ? "cursor-not-allowed border-border bg-page text-text-muted"
                    : checked
                      ? "cursor-pointer border-primary-600 bg-primary-50"
                      : "cursor-pointer border-border bg-surface hover:bg-subtle",
                )}
              >
                <input
                  type="radio"
                  name="targetParentId"
                  value={candidate.id}
                  checked={checked}
                  disabled={disabled}
                  onChange={() => setSelectedParentId(candidate.id)}
                  className="size-4 accent-primary-fill"
                />
                <span className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
                  {candidate.code}
                </span>
                <span className="min-w-0 flex-1 truncate font-medium text-text">
                  {pickName(candidate, locale)}
                </span>
                {isCurrent && <Badge>{t("moveBranchDialog.currentBadge")}</Badge>}
                {!isCurrent && isInactive && (
                  <Badge>{t("moveBranchDialog.inactiveParentBadge")}</Badge>
                )}
              </label>
            );
          })}
        </div>
      )}

      <div
        role="note"
        className="rounded-md border border-warning bg-warning-bg px-3.5 py-2.5 text-field-label text-warning"
      >
        {t("moveBranchDialog.inheritedNotice")}
      </div>

      <p className="text-caption text-text-muted">{t("moveBranchDialog.rulesHint")}</p>

      {serverError && (
        <div
          role="alert"
          className="rounded-md border border-danger bg-danger-bg px-3.5 py-2.5 text-field-label font-medium text-danger"
        >
          {serverError}
        </div>
      )}

      <DialogFooter className="pt-1">
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          {t("cancel")}
        </Button>
        <Button
          variant="primary"
          loading={submitting}
          disabled={!selectedParentId || submitting}
          onClick={() => void handleSubmit()}
        >
          {t("moveBranchDialog.submit")}
        </Button>
      </DialogFooter>
    </div>
  );
}
