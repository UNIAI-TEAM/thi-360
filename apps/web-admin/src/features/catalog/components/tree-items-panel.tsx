"use client";

import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Card,
  Checkbox,
  Chip,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  IconButton,
  Input,
  Select,
  Skeleton,
  StatusChip,
  Tooltip,
  TreeView,
  notify,
  toast,
  type TreeViewItem,
} from "@thi360/ui";
import {
  ArrowDown,
  ArrowUp,
  Ban,
  ChevronDown,
  ChevronsDownUp,
  ChevronsUpDown,
  Download,
  Eye,
  FolderInput,
  FolderTree,
  Lock,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  catalogQueryKeys,
  type CatalogApi,
  type CatalogGroupResponse,
  type CatalogInheritedRefResponse,
  type CatalogItemRefResponse,
  type CatalogItemResponse,
  type CatalogStatus,
  type CatalogTreeNodeResponse,
  type ListCatalogTreeQuery,
} from "../api";
import {
  getCatalogErrorCode,
  pickName,
  resolveCatalogErrorMessage,
  resolveTreeLevelLabel,
} from "../utils";
import type { StatusFilter } from "./simple-items-panel";

export interface TreeItemsPanelProps {
  group: CatalogGroupResponse;
  availableSimpleGroups: CatalogGroupResponse[];
  api: CatalogApi;
  selectedNodeId: string | null;
  exporting?: boolean;
  canModify: boolean;
  onSelectNode: (nodeId: string | null) => void;
  onEditGroup: () => void;
  onDeleteGroup: () => void;
  onAddRootNode: () => void;
  onAddChildNode: (parentNode: CatalogTreeNodeResponse) => void;
  onEditNode: (node: CatalogItemResponse | CatalogTreeNodeResponse) => void;
  onMoveNode: (node: CatalogTreeNodeResponse) => void;
  onStopNode: (node: CatalogItemResponse | CatalogTreeNodeResponse) => void;
  onDeleteNode: (node: CatalogItemResponse | CatalogTreeNodeResponse) => void;
  onViewNodeDetail: (node: CatalogItemResponse | CatalogTreeNodeResponse) => void;
  onExportExcel: () => void;
  onImportExcel: () => void;
}

type VisibleTreeRow = {
  node: CatalogTreeNodeResponse;
  level: number;
  hasChildren: boolean;
  isExpanded: boolean;
  siblingIndex: number;
  siblingCount: number;
  siblings: CatalogTreeNodeResponse[];
};

const MAX_TREE_DEPTH = 6;

export function TreeItemsPanel({
  group,
  availableSimpleGroups,
  api,
  selectedNodeId,
  canModify,
  exporting = false,
  onSelectNode,
  onEditGroup,
  onDeleteGroup,
  onAddRootNode,
  onAddChildNode,
  onEditNode,
  onMoveNode,
  onStopNode,
  onDeleteNode,
  onViewNodeDetail,
  onExportExcel,
  onImportExcel,
}: TreeItemsPanelProps) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();
  const queryClient = useQueryClient();

  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [selectedRefByGroup, setSelectedRefByGroup] = useState<Record<string, string[]>>({});
  const [expandedOverrides, setExpandedOverrides] = useState<Record<string, boolean>>({});
  const [expandingAll, setExpandingAll] = useState(false);
  const [reordering, setReordering] = useState(false);
  const [refActionPending, setRefActionPending] = useState(false);
  const [addRefGroupValue, setAddRefGroupValue] = useState("");
  const [addItemRefByGroup, setAddItemRefByGroup] = useState<Record<string, string>>({});

  const selectedRefItemIds = useMemo(
    () => Object.values(selectedRefByGroup).flat().filter(Boolean),
    [selectedRefByGroup],
  );
  const trimmedSearch = searchQuery.trim();
  const isFiltered = Boolean(trimmedSearch) || statusFilter !== "ALL" || selectedRefItemIds.length > 0;

  // Fetch active options for every linked ref-group (used in filter bar + detail pane)
  const refGroupOptionQueries = useQueries({
    queries: group.refGroups.map((rg) => ({
      queryKey: catalogQueryKeys.options(rg.code),
      queryFn: () => api.getOptions(rg.code),
    })),
  });

  // Fetch selected node detail if any node is selected (also supplies path for deep-link expansion)
  const selectedNodeDetailQuery = useQuery({
    queryKey: selectedNodeId
      ? catalogQueryKeys.item(selectedNodeId)
      : (["catalog", "item", "none"] as const),
    queryFn: () => api.getItem(selectedNodeId!),
    enabled: Boolean(selectedNodeId),
  });

  const deepLinkAncestorIds = useMemo(() => {
    const path = selectedNodeDetailQuery.data?.path;
    if (!path) return [];
    const parts = path.split("/").filter(Boolean);
    return parts.slice(0, -1);
  }, [selectedNodeDetailQuery.data?.path]);

  // Root / filtered tree query
  const rootQueryParams = useMemo<ListCatalogTreeQuery>(
    () =>
      isFiltered
        ? {
            ...(trimmedSearch ? { q: trimmedSearch } : {}),
            ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
            ...(selectedRefItemIds.length > 0 ? { ref: selectedRefItemIds } : {}),
          }
        : {},
    [isFiltered, selectedRefItemIds, statusFilter, trimmedSearch],
  );

  const rootTreeQuery = useQuery({
    queryKey: catalogQueryKeys.tree(group.id, rootQueryParams),
    queryFn: () => api.listTree(group.id, rootQueryParams),
  });

  // Lazy child queries (only when not in filtered mode)
  const expandedCandidateIds = useMemo(() => {
    if (isFiltered) return [];
    const ids = new Set<string>(deepLinkAncestorIds);
    for (const [id, flag] of Object.entries(expandedOverrides)) {
      if (flag) {
        ids.add(id);
      } else {
        ids.delete(id);
      }
    }
    return [...ids];
  }, [deepLinkAncestorIds, expandedOverrides, isFiltered]);

  const childTreeQueries = useQueries({
    queries: expandedCandidateIds.map((parentId) => ({
      queryKey: catalogQueryKeys.tree(group.id, { parentId }),
      queryFn: () => api.listTree(group.id, { parentId }),
      enabled: !isFiltered,
    })),
  });

  const { visibleRows, knownNodeById, loadingIds } = useMemo(() => {
    const nodeMap = new Map<string, CatalogTreeNodeResponse>();
    const loadingSet = new Set<string>();

    if (isFiltered) {
      const flat = rootTreeQuery.data ?? [];
      const byParent = new Map<string, CatalogTreeNodeResponse[]>();
      const idsInSet = new Set(flat.map((n) => n.id));
      for (const n of flat) {
        nodeMap.set(n.id, n);
        const key = n.parentId && idsInSet.has(n.parentId) ? n.parentId : "__ROOT__";
        const bucket = byParent.get(key);
        if (bucket) {
          bucket.push(n);
        } else {
          byParent.set(key, [n]);
        }
      }

      const rows: VisibleTreeRow[] = [];
      function walkFiltered(parentKey: string, level: number) {
        const siblings = byParent.get(parentKey) ?? [];
        siblings.forEach((node, idx) => {
          const loadedKids = byParent.get(node.id) ?? [];
          const hasKids = loadedKids.length > 0 || node.hasChildren;
          const isExp =
            loadedKids.length > 0 && (expandedOverrides[node.id] ?? true);
          rows.push({
            node,
            level,
            hasChildren: hasKids,
            isExpanded: isExp,
            siblingIndex: idx,
            siblingCount: siblings.length,
            siblings,
          });
          if (isExp) {
            walkFiltered(node.id, level + 1);
          }
        });
      }
      walkFiltered("__ROOT__", 1);
      return { visibleRows: rows, knownNodeById: nodeMap, loadingIds: loadingSet };
    }

    const roots = rootTreeQuery.data ?? [];
    for (const r of roots) {
      nodeMap.set(r.id, r);
    }

    const childrenByParent = new Map<string, CatalogTreeNodeResponse[]>();
    expandedCandidateIds.forEach((parentId, index) => {
      const q = childTreeQueries[index];
      if (!q) return;
      if (q.isPending) {
        loadingSet.add(parentId);
      }
      if (q.data) {
        childrenByParent.set(parentId, q.data);
        for (const child of q.data) {
          nodeMap.set(child.id, child);
        }
      }
    });

    const expandedSet = new Set(expandedCandidateIds);
    const rows: VisibleTreeRow[] = [];

    function walkLazy(siblings: CatalogTreeNodeResponse[], level: number) {
      siblings.forEach((node, idx) => {
        const isExp = node.hasChildren && expandedSet.has(node.id);
        rows.push({
          node,
          level,
          hasChildren: node.hasChildren,
          isExpanded: isExp,
          siblingIndex: idx,
          siblingCount: siblings.length,
          siblings,
        });
        if (isExp) {
          const kids = childrenByParent.get(node.id) ?? [];
          walkLazy(kids, level + 1);
        }
      });
    }

    walkLazy(roots, 1);
    return { visibleRows: rows, knownNodeById: nodeMap, loadingIds: loadingSet };
  }, [
    childTreeQueries,
    expandedCandidateIds,
    expandedOverrides,
    isFiltered,
    rootTreeQuery.data,
  ]);

  const selectedTreeNode = selectedNodeId ? knownNodeById.get(selectedNodeId) ?? null : null;
  const selectedDetail = selectedNodeDetailQuery.data ?? null;

  const isSystemGroup = group.type === "SYSTEM" || group.system;
  const deleteGroupDisabled = group.itemCount > 0;
  const levels = useMemo(() => group.levels ?? [], [group.levels]);
  const displayLevels = useMemo(
    () =>
      levels.map((levelName) =>
        resolveTreeLevelLabel(levelName, {
          isSystemGroup,
          translateLevel: (key) => t(`tree.systemLevels.${key}`),
        }),
      ),
    [isSystemGroup, levels, t],
  );
  const levelsBreadcrumb = displayLevels.join(" → ");
  const rootLevelName = displayLevels[0] ?? t("tree.defaultLevelName");

  const availableRefGroupsToAdd = useMemo(() => {
    const linkedIds = new Set(group.refGroups.map((rg) => rg.refGroupId));
    return availableSimpleGroups.filter(
      (g) => g.kind === "SIMPLE" && g.status === "ACTIVE" && !linkedIds.has(g.id),
    );
  }, [availableSimpleGroups, group.refGroups]);

  async function invalidateTreeAndItem(itemId?: string) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["catalog", "tree", group.id] }),
      queryClient.invalidateQueries({ queryKey: ["catalog", "items", group.id] }),
      queryClient.invalidateQueries({ queryKey: ["catalog", "groups"] }),
      queryClient.invalidateQueries({ queryKey: ["catalog", "options"] }),
      ...(itemId
        ? [queryClient.invalidateQueries({ queryKey: catalogQueryKeys.item(itemId) })]
        : [queryClient.invalidateQueries({ queryKey: ["catalog", "item"] })]),
    ]);
  }

  function handleToggleExpand(id: string) {
    setExpandedOverrides((prev) => {
      const current = isFiltered
        ? (prev[id] ?? true)
        : (prev[id] ?? deepLinkAncestorIds.includes(id));
      return { ...prev, [id]: !current };
    });
  }

  async function handleExpandAll() {
    if (isFiltered) {
      const next: Record<string, boolean> = {};
      for (const node of knownNodeById.values()) {
        if (node.hasChildren) next[node.id] = true;
      }
      setExpandedOverrides(next);
      return;
    }

    setExpandingAll(true);
    try {
      const roots = await queryClient.fetchQuery({
        queryKey: catalogQueryKeys.tree(group.id, {}),
        queryFn: () => api.listTree(group.id, {}),
      });
      const nextOverrides: Record<string, boolean> = { ...expandedOverrides };
      let frontier = roots.filter((n) => n.hasChildren);
      for (let depth = 1; depth < MAX_TREE_DEPTH && frontier.length > 0; depth++) {
        for (const node of frontier) {
          nextOverrides[node.id] = true;
        }
        const childrenLists = await Promise.all(
          frontier.map((parent) =>
            queryClient.fetchQuery({
              queryKey: catalogQueryKeys.tree(group.id, { parentId: parent.id }),
              queryFn: () => api.listTree(group.id, { parentId: parent.id }),
            }),
          ),
        );
        frontier = childrenLists.flat().filter((n) => n.hasChildren);
      }
      setExpandedOverrides(nextOverrides);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        notify.error(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
      }
    } finally {
      setExpandingAll(false);
    }
  }

  function handleCollapseAll() {
    const next: Record<string, boolean> = {};
    for (const id of deepLinkAncestorIds) {
      next[id] = false;
    }
    setExpandedOverrides(next);
  }

  function handleResetFilters() {
    setSearchQuery("");
    setStatusFilter("ALL");
    setSelectedRefByGroup({});
  }

  async function handleReorderSiblings(
    parentId: string | null,
    siblings: CatalogTreeNodeResponse[],
    fromIndex: number,
    toIndex: number,
  ) {
    if (
      toIndex < 0 ||
      toIndex >= siblings.length ||
      fromIndex === toIndex ||
      reordering ||
      isFiltered
    ) {
      return;
    }
    const previousOrder = [...siblings];
    const nextOrder = [...siblings];
    const [moved] = nextOrder.splice(fromIndex, 1);
    if (!moved) return;
    nextOrder.splice(toIndex, 0, moved);

    const cacheKey = catalogQueryKeys.tree(
      group.id,
      parentId ? { parentId } : {},
    );
    const snapshot = queryClient.getQueryData<CatalogTreeNodeResponse[]>(cacheKey);
    queryClient.setQueryData<CatalogTreeNodeResponse[]>(cacheKey, nextOrder);
    setReordering(true);
    try {
      await api.reorderItems(group.id, { itemIds: nextOrder.map((n) => n.id) });
      await invalidateTreeAndItem();
      toast.success(t("reorderSuccess"), {
        action: {
          label: t("undoAction"),
          onClick: () => {
            void (async () => {
              try {
                await api.reorderItems(group.id, {
                  itemIds: previousOrder.map((n) => n.id),
                });
                await invalidateTreeAndItem();
                notify.success(t("undoSuccess"));
              } catch (undoErr) {
                const status = getErrorStatus(undoErr);
                if (status !== 401 && status !== 403) {
                  notify.error(t("undoError"));
                }
              }
            })();
          },
        },
      });
    } catch (error) {
      queryClient.setQueryData(cacheKey, snapshot);
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        notify.error(t("reorderError"));
      }
    } finally {
      setReordering(false);
    }
  }

  async function handleAddRefGroup(refGroupId: string) {
    if (!refGroupId || refActionPending) return;
    const targetGroup = availableSimpleGroups.find((g) => g.id === refGroupId);
    const label = targetGroup ? pickName(targetGroup, locale) : refGroupId;
    setRefActionPending(true);
    try {
      await api.addRefGroup(group.id, refGroupId);
      setAddRefGroupValue("");
      await invalidateTreeAndItem();
      notify.success(t("tree.refGroupAddedToast", { name: label }));
    } catch (error) {
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        notify.error(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
      }
    } finally {
      setRefActionPending(false);
    }
  }

  async function handleRemoveRefGroup(refGroupId: string, label: string) {
    if (refActionPending) return;
    setRefActionPending(true);
    try {
      await api.removeRefGroup(group.id, refGroupId);
      setSelectedRefByGroup((prev) => {
        const next = { ...prev };
        delete next[refGroupId];
        return next;
      });
      await invalidateTreeAndItem();
      toast.success(t("tree.refGroupRemovedToast", { name: label }), {
        action: {
          label: t("undoAction"),
          onClick: () => {
            void (async () => {
              try {
                await api.addRefGroup(group.id, refGroupId);
                await invalidateTreeAndItem();
                notify.success(t("undoSuccess"));
              } catch (undoErr) {
                const status = getErrorStatus(undoErr);
                if (status !== 401 && status !== 403) {
                  notify.error(t("undoError"));
                }
              }
            })();
          },
        },
      });
    } catch (error) {
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        const code = getCatalogErrorCode(error);
        if (code === "CATALOG_REF_GROUP_IN_USE" || code === "CATALOG_REF_GROUP_DEFAULT") {
          notify.error(t(`errorCodes.${code}`));
        } else {
          notify.error(resolveCatalogErrorMessage(error, (c) => t(`errorCodes.${c}`)));
        }
      }
    } finally {
      setRefActionPending(false);
    }
  }

  async function handleAddNodeRef(
    nodeId: string,
    refGroupId: string,
    refItemId: string,
    refLabel: string,
  ) {
    if (!refItemId || refActionPending) return;
    setRefActionPending(true);
    try {
      await api.addItemRef(nodeId, refItemId);
      setAddItemRefByGroup((prev) => ({ ...prev, [refGroupId]: "" }));
      await invalidateTreeAndItem(nodeId);
      notify.success(t("tree.refAddedToast", { name: refLabel }));
    } catch (error) {
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        notify.error(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
      }
    } finally {
      setRefActionPending(false);
    }
  }

  async function handleRemoveNodeRef(nodeId: string, refItemId: string, refLabel: string) {
    if (refActionPending) return;
    setRefActionPending(true);
    try {
      await api.removeItemRef(nodeId, refItemId);
      await invalidateTreeAndItem(nodeId);
      toast.success(t("tree.refRemovedToast", { name: refLabel }), {
        action: {
          label: t("undoAction"),
          onClick: () => {
            void (async () => {
              try {
                await api.addItemRef(nodeId, refItemId);
                await invalidateTreeAndItem(nodeId);
                notify.success(t("undoSuccess"));
              } catch (undoErr) {
                const status = getErrorStatus(undoErr);
                if (status !== 401 && status !== 403) {
                  notify.error(t("undoError"));
                }
              }
            })();
          },
        },
      });
    } catch (error) {
      const status = getErrorStatus(error);
      if (status !== 401 && status !== 403) {
        notify.error(resolveCatalogErrorMessage(error, (code) => t(`errorCodes.${code}`)));
      }
    } finally {
      setRefActionPending(false);
    }
  }

  const rowById = useMemo(
    () => new Map(visibleRows.map((row) => [row.node.id, row])),
    [visibleRows],
  );

  const expandedIdSet = useMemo(
    () => new Set(visibleRows.filter((row) => row.isExpanded).map((row) => row.node.id)),
    [visibleRows],
  );

  const treeViewItems: TreeViewItem[] = useMemo(
    () =>
      visibleRows.map((row) => {
        const node = row.node;
        const stoppedByParent =
          node.effectiveStatus === "INACTIVE" && node.status === "ACTIVE";
        const isLeafLevel = node.depth === levels.length;
        return {
          id: node.id,
          parentId: node.parentId ?? null,
          depth: row.level,
          code: node.code,
          name: pickName(node, locale),
          hasChildren: row.hasChildren,
          leafCount: isLeafLevel ? undefined : node.childCount,
          statusLabel: stoppedByParent
            ? t("tree.statusInheritedInactive")
            : node.status === "INACTIVE"
              ? t("statuses.INACTIVE")
              : null,
          statusTone: stoppedByParent ? "warning" : "neutral",
          refsSummary:
            node.ownRefs.length > 0
              ? node.ownRefs.map((ref) => pickName(ref, locale)).join(", ")
              : null,
          inactive: node.effectiveStatus === "INACTIVE",
        };
      }),
    [levels.length, locale, t, visibleRows],
  );

  const statusFilterOptions: Array<{ value: StatusFilter; label: string }> = [
    { value: "ALL", label: t("allStatuses") },
    { value: "ACTIVE", label: t("statuses.ACTIVE") },
    { value: "INACTIVE", label: t("statuses.INACTIVE") },
  ];

  const activeNode = selectedDetail ?? selectedTreeNode;
  const activeNodeLevelName = activeNode
    ? displayLevels[activeNode.depth - 1] ?? `${t("tree.defaultLevelName")} ${activeNode.depth}`
    : null;
  const activeNodeCanAddChild = activeNode ? activeNode.depth < levels.length : false;
  const activeNodeCanMove = activeNode ? activeNode.depth > 1 : false;
  const activeNodeHasChildren =
    selectedDetail ? selectedDetail.childCount > 0 : (selectedTreeNode?.hasChildren ?? false);
  const activeNodeNextLevelName = activeNode
    ? displayLevels[activeNode.depth] ?? t("tree.defaultLevelName")
    : "";

  return (
    <Card className="min-w-0 flex-1" padding="md">
      {/* Group header */}
      <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-h3 font-bold text-text">{pickName(group, locale)}</h2>
            <StatusChip tone={group.status === "ACTIVE" ? "success" : "neutral"}>
              {t(`statuses.${group.status}`)}
            </StatusChip>
            {isSystemGroup && <Badge>{t("systemBadge")}</Badge>}
            <Badge>{t("tree.kindBadge")}</Badge>
          </div>

          <p className="mt-1 text-caption font-medium text-primary-text">{levelsBreadcrumb}</p>

          {group.description && (
            <p className="mt-1 text-field-label text-text-secondary">{group.description}</p>
          )}
          {isSystemGroup && (
            <p className="mt-1 text-caption text-text-muted">{t("systemGroupHint")}</p>
          )}
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {canModify && !isSystemGroup && (
            <>
              <Button variant="secondary" size="sm" onClick={onEditGroup}>
                <Pencil aria-hidden="true" className="size-4" />
                {t("editGroup")}
              </Button>
              <Tooltip
                content={deleteGroupDisabled ? t("deleteGroupNotEmptyHint") : t("deleteGroup")}
              >
                <span className="inline-flex">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={deleteGroupDisabled}
                    onClick={onDeleteGroup}
                    className="text-danger hover:bg-danger-bg hover:text-danger"
                  >
                    <Trash2 aria-hidden="true" className="size-4" />
                    {t("deleteGroup")}
                  </Button>
                </span>
              </Tooltip>
            </>
          )}

          <Button variant="secondary" size="sm" loading={exporting} onClick={onExportExcel}>
            <Download aria-hidden="true" className="size-4" />
            {t("exportExcel")}
          </Button>

          {canModify &&
            (group.status === "INACTIVE" ? (
              <Tooltip content={t("inactiveGroupCreateHint")}>
                <span>
                  <Button variant="secondary" size="sm" disabled>
                    <Upload aria-hidden="true" className="size-4" />
                    {t("importExcel")}
                  </Button>
                </span>
              </Tooltip>
            ) : (
              <Button variant="secondary" size="sm" onClick={onImportExcel}>
                <Upload aria-hidden="true" className="size-4" />
                {t("importExcel")}
              </Button>
            ))}

          {canModify &&
            (group.status === "INACTIVE" ? (
              <Tooltip content={t("inactiveGroupCreateHint")}>
                <span>
                  <Button variant="primary" size="sm" disabled>
                    <Plus aria-hidden="true" className="size-4" />
                    {t("tree.addNodeAction", { levelName: rootLevelName })}
                  </Button>
                </span>
              </Tooltip>
            ) : (
              <Button variant="primary" size="sm" onClick={onAddRootNode}>
                <Plus aria-hidden="true" className="size-4" />
                {t("tree.addNodeAction", { levelName: rootLevelName })}
              </Button>
            ))}
        </div>
      </div>

      {/* Linked ref-groups strip */}
      <div className="mt-3 flex flex-wrap items-center gap-2 rounded-md border border-border bg-page px-3 py-2">
        <span className="text-caption font-semibold text-text-secondary">
          {t("tree.refGroupsLabel")}:
        </span>
        {group.refGroups.length === 0 ? (
          <span className="text-caption text-text-muted">{t("tree.refGroupsEmpty")}</span>
        ) : (
          group.refGroups.map((rg) => {
            const label = pickName(
              { nameVi: rg.nameVi, nameEn: rg.nameEn },
              locale,
            );
            return (
              <Chip key={rg.refGroupId} variant="tag">
                <span>{label}</span>
                {rg.isDefault && (
                  <span className="text-caption opacity-80">
                    ({t("tree.refGroupDefaultHint")})
                  </span>
                )}
                {canModify && !rg.isDefault && (
                  <button
                    type="button"
                    aria-label={t("tree.removeRefGroupLabel", { name: label })}
                    onClick={() => void handleRemoveRefGroup(rg.refGroupId, label)}
                    disabled={refActionPending}
                    className="-mr-1 inline-flex size-6 items-center justify-center rounded-full text-primary-text hover:bg-primary-100/60"
                  >
                    <X aria-hidden="true" className="size-3" />
                  </button>
                )}
              </Chip>
            );
          })
        )}

        {canModify && availableRefGroupsToAdd.length > 0 && (
          <div className="ml-auto w-56">
            <Select
              aria-label={t("tree.addRefGroupPlaceholder")}
              size="md"
              placeholder={t("tree.addRefGroupPlaceholder")}
              value={addRefGroupValue}
              onValueChange={(val) => {
                setAddRefGroupValue(val);
                void handleAddRefGroup(val);
              }}
              disabled={refActionPending}
              options={availableRefGroupsToAdd.map((g) => ({
                value: g.id,
                label: pickName(g, locale),
              }))}
            />
          </div>
        )}
      </div>

      {/* Search + filter + expand/collapse bar */}
      <form
        onSubmit={(e) => e.preventDefault()}
        className="mt-3 flex flex-col gap-3"
        aria-label={t("filterFormLabel")}
      >
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-xs">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            />
            <Input
              aria-label={t("tree.searchPlaceholder")}
              placeholder={t("tree.searchPlaceholder")}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div
              role="group"
              aria-label={t("statusFilterLabel")}
              className="inline-flex rounded-md border border-border bg-page p-0.5"
            >
              {statusFilterOptions.map((opt) => {
                const active = statusFilter === opt.value;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setStatusFilter(opt.value)}
                    className={cn(
                      "rounded-sm px-3 py-1.5 text-caption font-semibold transition-colors",
                      active
                        ? "bg-surface text-primary-text shadow-xs"
                        : "text-text-muted hover:text-text",
                    )}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>

            <Button
              variant="secondary"
              size="sm"
              loading={expandingAll}
              onClick={() => void handleExpandAll()}
            >
              <ChevronsUpDown aria-hidden="true" className="size-4" />
              {t("tree.expandAll")}
            </Button>

            <Button variant="secondary" size="sm" onClick={handleCollapseAll}>
              <ChevronsDownUp aria-hidden="true" className="size-4" />
              {t("tree.collapseAll")}
            </Button>
          </div>
        </div>

        {group.refGroups.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            {group.refGroups.map((rg, index) => {
              const rgLabel = pickName(
                { nameVi: rg.nameVi, nameEn: rg.nameEn },
                locale,
              );
              const opts = refGroupOptionQueries[index]?.data ?? [];
              const selectedVals = selectedRefByGroup[rg.refGroupId] ?? [];
              const selectedSet = new Set(selectedVals);
              const selectedOptionNames = opts
                .filter((opt) => selectedSet.has(opt.id))
                .map((opt) => pickName(opt, locale));
              const triggerLabel =
                selectedVals.length === 0
                  ? t("tree.allFilterLabel", { name: rgLabel })
                  : selectedVals.length === 1 && selectedOptionNames[0]
                    ? `${rgLabel}: ${selectedOptionNames[0]}`
                    : t("tree.filterSelectedCount", {
                        name: rgLabel,
                        count: selectedVals.length,
                      });

              return (
                <DropdownMenu key={rg.refGroupId}>
                  <DropdownMenuTrigger asChild>
                    <Button
                      type="button"
                      variant="secondary"
                      size="md"
                      aria-label={t("tree.filterByRefLabel", { name: rgLabel })}
                      className={cn(
                        "min-w-44 justify-between gap-2 font-normal",
                        selectedVals.length > 0 && "border-primary-600 bg-primary-50 text-primary-text",
                      )}
                    >
                      <span className="truncate">{triggerLabel}</span>
                      <ChevronDown aria-hidden="true" className="size-4 shrink-0 text-text-muted" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start" className="max-h-72 min-w-52 overflow-y-auto">
                    {opts.map((opt) => {
                      const checked = selectedSet.has(opt.id);
                      return (
                        <DropdownMenuItem
                          key={opt.id}
                          role="menuitemcheckbox"
                          aria-checked={checked}
                          onSelect={(event) => {
                            event.preventDefault();
                            setSelectedRefByGroup((prev) => {
                              const current = prev[rg.refGroupId] ?? [];
                              const nextList = current.includes(opt.id)
                                ? current.filter((id) => id !== opt.id)
                                : [...current, opt.id];
                              if (nextList.length === 0) {
                                const next = { ...prev };
                                delete next[rg.refGroupId];
                                return next;
                              }
                              return { ...prev, [rg.refGroupId]: nextList };
                            });
                          }}
                        >
                          <Checkbox
                            checked={checked}
                            tabIndex={-1}
                            aria-hidden="true"
                            className="pointer-events-none"
                          />
                          <span className="truncate">{pickName(opt, locale)}</span>
                        </DropdownMenuItem>
                      );
                    })}
                    {selectedVals.length > 0 && (
                      <>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          onSelect={() => {
                            setSelectedRefByGroup((prev) => {
                              const next = { ...prev };
                              delete next[rg.refGroupId];
                              return next;
                            });
                          }}
                        >
                          {t("tree.filterClearGroup")}
                        </DropdownMenuItem>
                      </>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              );
            })}

            {isFiltered && (
              <Button variant="outline" size="sm" onClick={handleResetFilters}>
                <X aria-hidden="true" className="size-4" />
                {t("resetFilters")}
              </Button>
            )}
          </div>
        )}
      </form>

      {/* Main split: Tree (left) + 340px Node Detail Pane (right) */}
      <div className="mt-4 flex flex-col gap-4 xl:flex-row xl:items-start">
        {/* Tree pane */}
        <div className="min-w-0 flex-1 rounded-md border border-border bg-surface p-2">
          {rootTreeQuery.isPending ? (
            <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-2 p-2">
              {Array.from({ length: 6 }).map((_, idx) => (
                <Skeleton key={idx} className="h-10 w-full" />
              ))}
            </div>
          ) : rootTreeQuery.isError ? (
            <div className="p-6 text-center">
              <p className="text-field-label font-medium text-danger">
                {t("tree.loadError")}
              </p>
              <Button
                variant="secondary"
                size="sm"
                className="mt-3"
                onClick={() => void rootTreeQuery.refetch()}
              >
                {commonT("retry")}
              </Button>
            </div>
          ) : visibleRows.length === 0 ? (
            <EmptyState
              icon={<FolderTree />}
              title={isFiltered ? t("emptyItemsFilteredTitle") : t("tree.emptyTitle")}
              description={
                isFiltered ? t("tree.emptyFiltered") : t("tree.emptyDescription")
              }
              action={
                isFiltered ? (
                  <Button variant="secondary" size="sm" onClick={handleResetFilters}>
                    {t("resetFilters")}
                  </Button>
                ) : canModify ? (
                  <Button variant="primary" size="sm" onClick={onAddRootNode}>
                    <Plus aria-hidden="true" className="size-4" />
                    {t("tree.addNodeAction", { levelName: rootLevelName })}
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <TreeView
              aria-label={t("tree.treeAriaLabel", { name: pickName(group, locale) })}
              items={treeViewItems}
              expandedIds={expandedIdSet}
              selectedId={selectedNodeId}
              loadingIds={loadingIds}
              onSelect={(item) => onSelectNode(item.id)}
              onToggleExpand={(item) => handleToggleExpand(item.id)}
              labels={{
                expandNode: (name) => t("tree.expandNode", { name }),
                collapseNode: (name) => t("tree.collapseNode", { name }),
                loadingChildren: commonT("loading"),
              }}
              renderTrailing={(treeItem) => {
                const row = rowById.get(treeItem.id);
                if (!row) return null;
                const node = row.node;
                const displayName = pickName(node, locale);
                const canAddChild = node.depth < levels.length;
                const canMove = node.depth > 1;
                const nextLevelName =
                  displayLevels[node.depth] ?? t("tree.defaultLevelName");

                return (
                  <div
                    className="flex shrink-0 items-center gap-0.5"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <IconButton
                      aria-label={t("detailItem", { name: displayName })}
                      tooltip={t("detailItem", { name: displayName })}
                      icon={<Eye />}
                      size="md"
                      onClick={() => onViewNodeDetail(node)}
                    />

                    {canModify && (
                      <>
                        {canAddChild && (
                          <IconButton
                            aria-label={t("tree.addChildLabel", {
                              levelName: nextLevelName,
                              name: displayName,
                            })}
                            tooltip={t("tree.addChildAction", { levelName: nextLevelName })}
                            icon={<Plus />}
                            size="md"
                            onClick={() => onAddChildNode(node)}
                          />
                        )}

                        {!isFiltered && row.siblingCount > 1 && (
                          <>
                            <IconButton
                              aria-label={t("moveUpLabel", { name: displayName })}
                              icon={<ArrowUp />}
                              size="md"
                              disabled={reordering || row.siblingIndex === 0}
                              onClick={() =>
                                void handleReorderSiblings(
                                  node.parentId ?? null,
                                  row.siblings,
                                  row.siblingIndex,
                                  row.siblingIndex - 1,
                                )
                              }
                            />
                            <IconButton
                              aria-label={t("moveDownLabel", { name: displayName })}
                              icon={<ArrowDown />}
                              size="md"
                              disabled={
                                reordering || row.siblingIndex === row.siblingCount - 1
                              }
                              onClick={() =>
                                void handleReorderSiblings(
                                  node.parentId ?? null,
                                  row.siblings,
                                  row.siblingIndex,
                                  row.siblingIndex + 1,
                                )
                              }
                            />
                          </>
                        )}

                        {canMove && (
                          <IconButton
                            aria-label={t("tree.moveBranchLabel", { name: displayName })}
                            tooltip={t("tree.moveBranchAction")}
                            icon={<FolderInput />}
                            size="md"
                            onClick={() => onMoveNode(node)}
                          />
                        )}

                        <IconButton
                          aria-label={t("editItem", { name: displayName })}
                          tooltip={t("tree.editAction")}
                          icon={<Pencil />}
                          size="md"
                          onClick={() => onEditNode(node)}
                        />

                        {node.status === "ACTIVE" && (
                          <IconButton
                            aria-label={t("stopItem", { name: displayName })}
                            tooltip={t("tree.stopAction")}
                            icon={<Ban />}
                            size="md"
                            onClick={() => onStopNode(node)}
                          />
                        )}

                        <IconButton
                          aria-label={t("deleteItem", { name: displayName })}
                          tooltip={
                            node.hasChildren
                              ? t("tree.deleteBlockedChildren")
                              : t("tree.deleteAction")
                          }
                          icon={<Trash2 />}
                          tone="danger"
                          size="md"
                          disabled={node.hasChildren}
                          onClick={() => onDeleteNode(node)}
                        />
                      </>
                    )}
                  </div>
                );
              }}
            />
          )}
        </div>

        {/* 340px Node detail pane (DESIGN.md §5.11) */}
        <aside
          aria-label={t("tree.detailTitle")}
          className="w-full shrink-0 rounded-md border border-border bg-page p-4 xl:w-[340px]"
        >
          {!selectedNodeId ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <FolderTree aria-hidden="true" className="size-8 text-text-muted" />
              <p className="mt-2 text-field-label font-medium text-text-secondary">
                {t("tree.noSelectionTitle")}
              </p>
              <p className="mt-1 text-caption text-text-muted">
                {t("tree.noSelectionDescription")}
              </p>
            </div>
          ) : selectedNodeDetailQuery.isPending && !selectedTreeNode ? (
            <div role="status" aria-label={commonT("loading")} className="flex flex-col gap-3">
              <Skeleton className="h-5 w-24" />
              <Skeleton className="h-7 w-48" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : activeNode ? (
            <div className="flex flex-col gap-4">
              {/* Node identity */}
              <div>
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge>{activeNodeLevelName}</Badge>
                  <span className="rounded-sm bg-subtle px-2 py-0.5 font-mono text-caption font-semibold text-text">
                    {activeNode.code}
                  </span>
                  {activeNode.effectiveStatus === "INACTIVE" &&
                  activeNode.status === "ACTIVE" ? (
                    <StatusChip tone="warning">{t("tree.statusInheritedInactive")}</StatusChip>
                  ) : (
                    <StatusChip
                      tone={activeNode.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {t(`statuses.${activeNode.status as CatalogStatus}`)}
                    </StatusChip>
                  )}
                </div>

                <h3 className="mt-2 text-h3 font-bold text-text">
                  {pickName(activeNode, locale)}
                </h3>
                {activeNode.nameEn && !locale.toLowerCase().startsWith("en") && (
                  <p className="text-caption text-text-muted">{activeNode.nameEn}</p>
                )}
                {activeNode.effectiveStatus === "INACTIVE" &&
                  activeNode.status === "ACTIVE" && (
                    <p className="mt-1 text-caption text-warning">
                      {t("tree.stoppedByParentHint")}
                    </p>
                  )}
              </div>

              {/* Metrics */}
              <dl className="grid grid-cols-2 gap-2 rounded-md border border-border bg-surface p-3 text-caption">
                <div>
                  <dt className="text-text-muted">{t("detailDrawer.displayOrderLabel")}</dt>
                  <dd className="font-semibold tabular-nums text-text">
                    {activeNode.displayOrder}
                  </dd>
                </div>
                <div>
                  <dt className="text-text-muted">{t("tree.childCountLabel")}</dt>
                  <dd className="font-semibold tabular-nums text-text">
                    {selectedDetail?.childCount ?? selectedTreeNode?.childCount ?? 0}
                  </dd>
                </div>
                {selectedDetail?.description && (
                  <div className="col-span-2 border-t border-border pt-2">
                    <dt className="text-text-muted">{t("itemDialog.descriptionLabel")}</dt>
                    <dd className="mt-0.5 text-text-secondary">{selectedDetail.description}</dd>
                  </div>
                )}
              </dl>

              {/* References section (own + inherited) */}
              <div className="flex flex-col gap-3 rounded-md border border-border bg-surface p-3">
                <h4 className="text-caption font-semibold uppercase tracking-wide text-text-muted">
                  {t("tree.linksHeader")}
                </h4>

                {group.refGroups.length === 0 ? (
                  <p className="text-caption text-text-muted">{t("tree.refGroupsEmpty")}</p>
                ) : (
                  group.refGroups.map((rg, index) => {
                    const rgName = pickName(
                      { nameVi: rg.nameVi, nameEn: rg.nameEn },
                      locale,
                    );
                    const opts = refGroupOptionQueries[index]?.data ?? [];
                    const inheritedRefs: CatalogInheritedRefResponse[] = (
                      selectedDetail?.inheritedRefs ?? []
                    ).filter((r) => r.refGroupId === rg.refGroupId);
                    const ownRefs: CatalogItemRefResponse[] = (
                      selectedDetail?.ownRefs ?? []
                    ).filter((r) => r.refGroupId === rg.refGroupId);
                    const takenIds = new Set([
                      ...inheritedRefs.map((r) => r.refItemId),
                      ...ownRefs.map((r) => r.refItemId),
                    ]);
                    const addableOpts = opts.filter((o) => !takenIds.has(o.id));

                    return (
                      <div key={rg.refGroupId} className="flex flex-col gap-1.5">
                        <div className="flex items-center justify-between">
                          <span className="text-caption font-semibold text-text">
                            {rgName}
                            {rg.isDefault && (
                              <span className="ml-1 font-normal text-text-muted">
                                ({t("tree.refGroupDefaultHint")})
                              </span>
                            )}
                          </span>
                        </div>

                        <div className="flex flex-wrap gap-1.5">
                          {inheritedRefs.map((ref) => {
                            const refName = pickName(
                              { nameVi: ref.nameVi, nameEn: ref.nameEn },
                              locale,
                            );
                            const fromName = pickName(
                              { nameVi: ref.fromItemNameVi, nameEn: ref.fromItemNameEn },
                              locale,
                            );
                            return (
                              <Tooltip
                                key={`inh-${ref.refItemId}`}
                                content={t("tree.inheritedFromHint", {
                                  code: ref.fromItemCode,
                                  name: fromName,
                                })}
                              >
                                <span className="inline-flex items-center gap-1 rounded-full border border-border bg-subtle px-2.5 py-0.5 text-caption text-text-secondary">
                                  <Lock aria-hidden="true" className="size-3 shrink-0" />
                                  <span>{refName}</span>
                                </span>
                              </Tooltip>
                            );
                          })}

                          {ownRefs.map((ref) => {
                            const refName = pickName(
                              { nameVi: ref.nameVi, nameEn: ref.nameEn },
                              locale,
                            );
                            return (
                              <Chip key={`own-${ref.refItemId}`} variant="tag">
                                <span>{refName}</span>
                                {canModify && activeNode && (
                                  <button
                                    type="button"
                                    aria-label={t("tree.removeRefValueLabel", { name: refName })}
                                    onClick={() =>
                                      void handleRemoveNodeRef(
                                        activeNode.id,
                                        ref.refItemId,
                                        refName,
                                      )
                                    }
                                    disabled={refActionPending}
                                    className="-mr-1 inline-flex size-6 items-center justify-center rounded-full text-primary-text hover:bg-primary-100/60"
                                  >
                                    <X aria-hidden="true" className="size-3" />
                                  </button>
                                )}
                              </Chip>
                            );
                          })}

                          {inheritedRefs.length === 0 && ownRefs.length === 0 && (
                            <span className="text-caption text-text-muted">
                              {t("tree.noNodeRefs")}
                            </span>
                          )}
                        </div>

                        {canModify && addableOpts.length > 0 && (
                          <Select
                            aria-label={t("tree.addRefToNodeLabel", { group: rgName })}
                            size="md"
                            placeholder={t("tree.addRefPlaceholder")}
                            value={addItemRefByGroup[rg.refGroupId] ?? ""}
                            onValueChange={(val) => {
                              setAddItemRefByGroup((prev) => ({
                                ...prev,
                                [rg.refGroupId]: val,
                              }));
                              const chosen = addableOpts.find((o) => o.id === val);
                              const chosenLabel = chosen ? pickName(chosen, locale) : val;
                              void handleAddNodeRef(
                                activeNode.id,
                                rg.refGroupId,
                                val,
                                chosenLabel,
                              );
                            }}
                            disabled={refActionPending}
                            options={addableOpts.map((opt) => ({
                              value: opt.id,
                              label: pickName(opt, locale),
                            }))}
                          />
                        )}
                      </div>
                    );
                  })
                )}
              </div>

              {/* Detail pane actions */}
              <div className="flex flex-col gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => onViewNodeDetail(activeNode)}
                >
                  <Eye aria-hidden="true" className="size-4" />
                  {t("tree.viewAuditAction")}
                </Button>

                {canModify && (
                  <div className="flex flex-wrap gap-2">
                    {activeNodeCanAddChild && selectedTreeNode && (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => onAddChildNode(selectedTreeNode)}
                      >
                        <Plus aria-hidden="true" className="size-4" />
                        {t("tree.addNodeAction", { levelName: activeNodeNextLevelName })}
                      </Button>
                    )}

                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onEditNode(activeNode)}
                    >
                      <Pencil aria-hidden="true" className="size-4" />
                      {t("tree.editAction")}
                    </Button>

                    {activeNodeCanMove && selectedTreeNode && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onMoveNode(selectedTreeNode)}
                      >
                        <FolderInput aria-hidden="true" className="size-4" />
                        {t("tree.moveBranchAction")}
                      </Button>
                    )}

                    {activeNode.status === "ACTIVE" && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onStopNode(activeNode)}
                      >
                        <Ban aria-hidden="true" className="size-4" />
                        {t("tree.stopAction")}
                      </Button>
                    )}

                    <Tooltip
                      content={
                        activeNodeHasChildren
                          ? t("tree.deleteBlockedChildren")
                          : t("tree.deleteAction")
                      }
                    >
                      <span className="inline-flex">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={activeNodeHasChildren}
                          onClick={() => onDeleteNode(activeNode)}
                          className="text-danger hover:bg-danger-bg hover:text-danger"
                        >
                          <Trash2 aria-hidden="true" className="size-4" />
                          {t("tree.deleteAction")}
                        </Button>
                      </span>
                    </Tooltip>
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </aside>
      </div>
    </Card>
  );
}
