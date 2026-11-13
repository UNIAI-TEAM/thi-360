"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Card,
  ConfirmDialog,
  EmptyState,
  notify,
  PageHeader,
  Skeleton,
  toast,
} from "@thi360/ui";
import { FolderOpen } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { ForbiddenView } from "@/features/session/forbidden-view";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  catalogQueryKeys,
  defaultCatalogApi,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogItemResponse,
  type CreateCatalogGroupRequest,
  type CreateCatalogItemRequest,
  type PageResponseCatalogItemResponse,
  type UpdateCatalogGroupRequest,
  type UpdateCatalogItemRequest,
} from "../api";
import { pickName, resolveCatalogErrorMessage } from "../utils";
import { GroupFormDialog } from "./group-form-dialog";
import { GroupSidebar } from "./group-sidebar";
import { ItemFormDialog } from "./item-form-dialog";
import { SimpleItemsPanel, type StatusFilter } from "./simple-items-panel";

export interface CatalogViewProps {
  api?: CatalogApi;
}

type GroupDialogState =
  | { open: false }
  | { open: true; mode: "create"; group: null }
  | { open: true; mode: "edit"; group: CatalogGroupResponse };

type ItemDialogState =
  | { open: false }
  | { open: true; mode: "create"; item: null }
  | { open: true; mode: "edit"; item: CatalogItemResponse };

type DeleteTarget =
  | { type: "group"; group: CatalogGroupResponse }
  | { type: "item"; item: CatalogItemResponse }
  | null;

export function CatalogView({ api = defaultCatalogApi }: CatalogViewProps) {
  const sessionState = useSession();
  const { can } = usePermissions();

  if (sessionState.status === "loading") {
    return <CatalogPageSkeleton />;
  }

  if (sessionState.status === "ready" && !can("CATALOG.VIEW")) {
    return <ForbiddenView />;
  }

  return <CatalogWorkspace api={api} canModify={can("CATALOG.MODIFY")} />;
}

export function CatalogPageSkeleton() {
  const commonT = useTranslations("common");
  return (
    <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-4">
      <Skeleton className="h-8 w-56" />
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <Skeleton className="h-112 w-full shrink-0 lg:w-66" />
        <Skeleton className="h-112 min-w-0 flex-1" />
      </div>
    </div>
  );
}

function CatalogWorkspace({ api, canModify }: { api: CatalogApi; canModify: boolean }) {
  const t = useTranslations("catalog");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  const selectedGroupParam = searchParams.get("group");

  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [groupDialog, setGroupDialog] = useState<GroupDialogState>({ open: false });
  const [itemDialog, setItemDialog] = useState<ItemDialogState>({ open: false });
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null);

  const groupsQuery = useQuery({
    queryKey: catalogQueryKeys.groups({ size: 100 }),
    queryFn: () => api.listGroups({ size: 100 }),
  });

  const groups = useMemo(() => groupsQuery.data?.items ?? [], [groupsQuery.data]);

  const selectedGroup = useMemo(() => {
    if (!selectedGroupParam) return null;
    const normalized = selectedGroupParam.trim().toLowerCase();
    return (
      groups.find(
        (g) => g.code.toLowerCase() === normalized || g.id.toLowerCase() === normalized,
      ) ?? null
    );
  }, [groups, selectedGroupParam]);

  const itemsQueryParams = useMemo(
    () => ({
      ...(searchQuery.trim() ? { q: searchQuery.trim() } : {}),
      ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
      page,
      size: pageSize,
    }),
    [page, pageSize, searchQuery, statusFilter],
  );

  const itemsQueryKey = selectedGroup
    ? catalogQueryKeys.items(selectedGroup.id, itemsQueryParams)
    : (["catalog", "items", "none"] as const);

  const itemsQuery = useQuery({
    queryKey: itemsQueryKey,
    queryFn: () => api.listItems(selectedGroup!.id, itemsQueryParams),
    enabled: Boolean(selectedGroup),
  });

  function updateGroupParam(groupCode: string | null) {
    const next = new URLSearchParams(searchParams.toString());
    if (groupCode) {
      next.set("group", groupCode);
    } else {
      next.delete("group");
    }
    next.delete("node");
    const qs = next.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  function handleSelectGroup(group: CatalogGroupResponse) {
    setSearchQuery("");
    setStatusFilter("ALL");
    setPage(0);
    updateGroupParam(group.code);
  }

  function handleSearchChange(q: string) {
    setSearchQuery(q);
    setPage(0);
  }

  function handleStatusFilterChange(status: StatusFilter) {
    setStatusFilter(status);
    setPage(0);
  }

  function handleResetFilters() {
    setSearchQuery("");
    setStatusFilter("ALL");
    setPage(0);
  }

  const createGroupMutation = useMutation({
    mutationFn: (data: CreateCatalogGroupRequest) => api.createGroup(data),
  });

  const updateGroupMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCatalogGroupRequest }) =>
      api.updateGroup(id, data),
  });

  const deleteGroupMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => api.deleteGroup(id, reason),
  });

  const createItemMutation = useMutation({
    mutationFn: ({ groupId, data }: { groupId: string; data: CreateCatalogItemRequest }) =>
      api.createItem(groupId, data),
  });

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, data }: { itemId: string; data: UpdateCatalogItemRequest }) =>
      api.updateItem(itemId, data),
  });

  const deleteItemMutation = useMutation({
    mutationFn: ({ itemId, reason }: { itemId: string; reason: string }) =>
      api.deleteItem(itemId, reason),
  });

  const reorderMutation = useMutation({
    mutationFn: ({ groupId, itemIds }: { groupId: string; itemIds: string[] }) =>
      api.reorderItems(groupId, { itemIds }),
  });

  async function handleSubmitGroup(
    payload:
      | { mode: "create"; data: CreateCatalogGroupRequest }
      | { mode: "edit"; id: string; data: UpdateCatalogGroupRequest },
  ) {
    if (payload.mode === "create") {
      const created = await createGroupMutation.mutateAsync(payload.data);
      await queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] });
      setGroupDialog({ open: false });
      setSearchQuery("");
      setStatusFilter("ALL");
      setPage(0);
      updateGroupParam(created.code);
      notify.success(t("groupDialog.createSuccess", { code: created.code }));
    } else {
      const updated = await updateGroupMutation.mutateAsync({
        id: payload.id,
        data: payload.data,
      });
      await queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] });
      setGroupDialog({ open: false });
      if (selectedGroup && selectedGroup.id === updated.id && selectedGroup.code !== updated.code) {
        updateGroupParam(updated.code);
      }
      notify.success(t("groupDialog.updateSuccess", { code: updated.code }));
    }
  }

  async function handleSubmitItem(
    payload:
      | { mode: "create"; groupId: string; data: CreateCatalogItemRequest; addAnother: boolean }
      | { mode: "edit"; itemId: string; data: UpdateCatalogItemRequest },
  ) {
    if (payload.mode === "create") {
      const created = await createItemMutation.mutateAsync({
        groupId: payload.groupId,
        data: payload.data,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["catalog", "items", payload.groupId] }),
        queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] }),
      ]);
      if (!payload.addAnother) {
        setItemDialog({ open: false });
      }
      notify.success(t("itemDialog.createSuccess", { code: created.code }));
    } else {
      const updated = await updateItemMutation.mutateAsync({
        itemId: payload.itemId,
        data: payload.data,
      });
      if (selectedGroup) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ["catalog", "items", selectedGroup.id] }),
          queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] }),
        ]);
      }
      setItemDialog({ open: false });
      notify.success(t("itemDialog.updateSuccess", { code: updated.code }));
    }
  }

  async function handleConfirmDelete(reason: string) {
    if (!deleteTarget) return;
    try {
      if (deleteTarget.type === "group") {
        await deleteGroupMutation.mutateAsync({ id: deleteTarget.group.id, reason });
        await queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] });
        if (selectedGroup?.id === deleteTarget.group.id) {
          updateGroupParam(null);
        }
        notify.success(t("deleteGroupDialog.success", { code: deleteTarget.group.code }));
      } else {
        await deleteItemMutation.mutateAsync({ itemId: deleteTarget.item.id, reason });
        if (selectedGroup) {
          await Promise.all([
            queryClient.invalidateQueries({ queryKey: ["catalog", "items", selectedGroup.id] }),
            queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] }),
          ]);
        }
        notify.success(t("deleteItemDialog.success", { code: deleteTarget.item.code }));
      }
      setDeleteTarget(null);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      const msg = resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`));
      notify.error(msg);
      setDeleteTarget(null);
    }
  }

  async function handleUndoReorder(previousItems: CatalogItemResponse[]) {
    if (!selectedGroup) return;
    const currentSnapshot =
      queryClient.getQueryData<PageResponseCatalogItemResponse>(itemsQueryKey);
    queryClient.setQueryData<PageResponseCatalogItemResponse | undefined>(itemsQueryKey, (old) =>
      old ? { ...old, items: previousItems } : old,
    );
    try {
      await reorderMutation.mutateAsync({
        groupId: selectedGroup.id,
        itemIds: previousItems.map((item) => item.id),
      });
      await queryClient.invalidateQueries({ queryKey: ["catalog", "items", selectedGroup.id] });
      notify.success(t("undoSuccess"));
    } catch (error) {
      queryClient.setQueryData<PageResponseCatalogItemResponse | undefined>(
        itemsQueryKey,
        currentSnapshot,
      );
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      notify.error(t("undoError"));
    }
  }

  async function handleReorder(nextItems: CatalogItemResponse[]) {
    if (!selectedGroup) return;
    const previousSnapshot =
      queryClient.getQueryData<PageResponseCatalogItemResponse>(itemsQueryKey);
    const previousItems = previousSnapshot?.items ?? [];

    queryClient.setQueryData<PageResponseCatalogItemResponse | undefined>(itemsQueryKey, (old) =>
      old ? { ...old, items: nextItems } : old,
    );

    try {
      const updated = await reorderMutation.mutateAsync({
        groupId: selectedGroup.id,
        itemIds: nextItems.map((item) => item.id),
      });
      queryClient.setQueryData<PageResponseCatalogItemResponse | undefined>(itemsQueryKey, (old) =>
        old ? { ...old, items: updated } : old,
      );
      toast.success(t("reorderSuccess"), {
        action: {
          label: t("undoAction"),
          onClick: () => {
            void handleUndoReorder(previousItems);
          },
        },
      });
    } catch (error) {
      queryClient.setQueryData<PageResponseCatalogItemResponse | undefined>(
        itemsQueryKey,
        previousSnapshot,
      );
      const status = getErrorStatus(error);
      if (status === 401 || status === 403) return;
      notify.error(t("reorderError"));
    }
  }

  const deleting = deleteGroupMutation.isPending || deleteItemMutation.isPending;

  return (
    <>
      <PageHeader title={t("title")} />

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <GroupSidebar
          groups={groups}
          selectedGroupId={selectedGroup?.id ?? null}
          loading={groupsQuery.isPending}
          error={groupsQuery.isError}
          canModify={canModify}
          onRetry={() => void groupsQuery.refetch()}
          onSelectGroup={handleSelectGroup}
          onAddGroup={() => setGroupDialog({ open: true, mode: "create", group: null })}
        />

        {!selectedGroup ? (
          <Card className="min-w-0 flex-1" padding="lg">
            <EmptyState
              icon={<FolderOpen />}
              title={t("noSelectionTitle")}
              description={t("noSelectionDescription")}
            />
          </Card>
        ) : (
          <SimpleItemsPanel
            group={selectedGroup}
            items={itemsQuery.data?.items ?? []}
            totalItems={itemsQuery.data?.totalItems ?? selectedGroup.itemCount}
            page={page}
            pageSize={pageSize}
            totalPages={itemsQuery.data?.totalPages ?? 1}
            searchQuery={searchQuery}
            statusFilter={statusFilter}
            loading={itemsQuery.isPending}
            error={itemsQuery.isError}
            reordering={reorderMutation.isPending}
            canModify={canModify}
            onSearchChange={handleSearchChange}
            onStatusFilterChange={handleStatusFilterChange}
            onResetFilters={handleResetFilters}
            onPageChange={setPage}
            onPageSizeChange={(nextSize) => {
              setPageSize(nextSize);
              setPage(0);
            }}
            onRetry={() => void itemsQuery.refetch()}
            onEditGroup={() =>
              setGroupDialog({ open: true, mode: "edit", group: selectedGroup })
            }
            onDeleteGroup={() => setDeleteTarget({ type: "group", group: selectedGroup })}
            onAddItem={() => setItemDialog({ open: true, mode: "create", item: null })}
            onEditItem={(item) => setItemDialog({ open: true, mode: "edit", item })}
            onDeleteItem={(item) => setDeleteTarget({ type: "item", item })}
            onReorder={(nextItems) => void handleReorder(nextItems)}
          />
        )}
      </div>

      {canModify && (
        <>
          <GroupFormDialog
            open={groupDialog.open}
            mode={groupDialog.open ? groupDialog.mode : "create"}
            group={groupDialog.open && groupDialog.mode === "edit" ? groupDialog.group : null}
            onOpenChange={(open) => {
              if (!open) setGroupDialog({ open: false });
            }}
            onSubmit={handleSubmitGroup}
          />

          {selectedGroup && (
            <ItemFormDialog
              open={itemDialog.open}
              mode={itemDialog.open ? itemDialog.mode : "create"}
              group={selectedGroup}
              item={itemDialog.open && itemDialog.mode === "edit" ? itemDialog.item : null}
              onOpenChange={(open) => {
                if (!open) setItemDialog({ open: false });
              }}
              onSubmit={handleSubmitItem}
            />
          )}

          <ConfirmDialog
            open={Boolean(deleteTarget)}
            onOpenChange={(open) => {
              if (!open) setDeleteTarget(null);
            }}
            title={
              deleteTarget?.type === "group"
                ? t("deleteGroupDialog.title")
                : t("deleteItemDialog.title")
            }
            description={
              deleteTarget?.type === "group"
                ? t("deleteGroupDialog.description", {
                    name: pickName(deleteTarget.group, locale),
                    code: deleteTarget.group.code,
                  })
                : deleteTarget?.type === "item"
                  ? t("deleteItemDialog.description", {
                      name: pickName(deleteTarget.item, locale),
                      code: deleteTarget.item.code,
                    })
                  : ""
            }
            confirmLabel={
              deleteTarget?.type === "group"
                ? t("deleteGroupDialog.confirm")
                : t("deleteItemDialog.confirm")
            }
            cancelLabel={t("cancel")}
            closeLabel={t("close")}
            reasonLabel={
              deleteTarget?.type === "group"
                ? t("deleteGroupDialog.reasonLabel")
                : t("deleteItemDialog.reasonLabel")
            }
            requireReason
            tone="danger"
            loading={deleting}
            onConfirm={(reason) => void handleConfirmDelete(reason)}
          />
        </>
      )}
    </>
  );
}
