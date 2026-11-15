"use client";

import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  Badge,
  Button,
  Chip,
  cn,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  EmptyState,
  IconButton,
  Input,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Tooltip,
} from "@thi360/ui";
import {
  ArrowDown,
  ArrowUp,
  Ban,
  Download,
  Eye,
  GripVertical,
  List,
  Lock,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Trash2,
  TriangleAlert,
  Upload,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useMemo, type CSSProperties } from "react";
import type { CatalogGroupResponse, CatalogItemResponse, CatalogStatus } from "../api";
import { useCatalogDndAccessibility, useCatalogSortableAttributes } from "../dnd-accessibility";
import { pickName } from "../utils";

/** Cột phụ (Tên tiếng Anh, Giá trị) chỉ hiện từ 1360px; dưới đó bảng vừa thẻ, chi tiết nằm trong ngăn chi tiết. */
const WIDE_ONLY_COLUMN = "hidden min-[1360px]:table-cell";

export type StatusFilter = "ALL" | CatalogStatus;

export type SimpleItemsPanelProps = {
  group: CatalogGroupResponse;
  items: CatalogItemResponse[];
  totalItems: number;
  page: number;
  pageSize: number;
  totalPages: number;
  searchQuery: string;
  statusFilter: StatusFilter;
  loading: boolean;
  /** Đang tải lại trong khi vẫn hiện dữ liệu cũ (đổi tìm kiếm, bộ lọc, trang): giữ các dòng, không hiện khung xương. */
  refreshing?: boolean;
  error: boolean;
  reordering: boolean;
  exporting?: boolean;
  canModify: boolean;
  onSearchChange: (q: string) => void;
  onStatusFilterChange: (status: StatusFilter) => void;
  onResetFilters: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onRetry: () => void;
  onEditGroup: () => void;
  onDeleteGroup: () => void;
  onAddItem: () => void;
  onViewItem: (item: CatalogItemResponse) => void;
  onEditItem: (item: CatalogItemResponse) => void;
  onStopItem: (item: CatalogItemResponse) => void;
  onDeleteItem: (item: CatalogItemResponse) => void;
  onReorder: (nextItems: CatalogItemResponse[]) => void;
  onExportExcel: () => void;
  onImportExcel: () => void;
};
export function SimpleItemsPanel({
  group,
  items,
  totalItems,
  page,
  pageSize,
  totalPages,
  searchQuery,
  statusFilter,
  loading,
  refreshing = false,
  error,
  reordering,
  exporting = false,
  canModify,
  onSearchChange,
  onStatusFilterChange,
  onResetFilters,
  onPageChange,
  onPageSizeChange,
  onRetry,
  onEditGroup,
  onDeleteGroup,
  onAddItem,
  onViewItem,
  onEditItem,
  onStopItem,
  onDeleteItem,
  onReorder,
  onExportExcel,
  onImportExcel,
}: SimpleItemsPanelProps) {
  const t = useTranslations("catalog");
  const commonT = useTranslations("common");
  const locale = useLocale();

  const isSystemGroup = group.type === "SYSTEM" || group.system;
  const isCombinationGroup = group.kind === "COMBINATION";
  const isFiltered = searchQuery.trim().length > 0 || statusFilter !== "ALL";
  const supportsReorder = group.kind !== "TREE";
  const reorderEnabled =
    canModify &&
    supportsReorder &&
    !isFiltered &&
    !reordering &&
    !refreshing &&
    totalPages <= 1 &&
    items.length > 1;
  const groupDisplayName = pickName(group, locale);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const resolveDndName = useCallback(
    (id: string) => {
      const item = items.find((candidate) => candidate.id === id);
      return item ? pickName(item, locale) : id;
    },
    [items, locale],
  );
  const dndAccessibility = useCatalogDndAccessibility(resolveDndName);

  function handleDragEnd(event: DragEndEvent) {
    if (!reorderEnabled) return;
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = items.findIndex((item) => item.id === active.id);
    const newIndex = items.findIndex((item) => item.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    onReorder(arrayMove(items, oldIndex, newIndex));
  }

  function handleMoveByIndex(index: number, direction: -1 | 1) {
    if (!reorderEnabled) return;
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= items.length) return;
    onReorder(arrayMove(items, index, targetIndex));
  }

  const statusOptions = [
    { value: "ALL", label: t("allStatuses") },
    { value: "ACTIVE", label: t("statuses.ACTIVE") },
    { value: "INACTIVE", label: t("statuses.INACTIVE") },
  ];

  const dragCols = canModify && supportsReorder ? 1 : 0;
  const colCount = dragCols + 5 + (isCombinationGroup ? 3 : 0) + 1;
  // Chỉ số cột (0-based) của Tên tiếng Anh và Giá trị, để ẩn cùng lúc ở hàng khung xương.
  const nameEnColIndex = dragCols + 2;
  const valueColIndex = dragCols + 3 + (isCombinationGroup ? 3 : 0);
  return (
    <section
      aria-labelledby="catalog-items-heading"
      className="min-w-0 flex-1 overflow-hidden rounded-lg border border-border bg-surface shadow-xs"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="catalog-items-heading" className="text-h3 font-bold text-text">
              {groupDisplayName}
            </h2>
            {group.status === "INACTIVE" && (
              <StatusChip tone="neutral">{t("statuses.INACTIVE")}</StatusChip>
            )}
          </div>
          <p className="text-caption text-text-muted">
            {t("groupMeta", {
              code: group.code,
              kind: t(`kinds.${group.kind}`),
              count: totalItems,
            })}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {isSystemGroup ? (
            <Tooltip content={t("systemGroupHint")}>
              <span
                tabIndex={0}
                className="inline-flex h-10 items-center gap-1.5 rounded-lg bg-subtle px-3 text-label font-semibold text-text-secondary"
              >
                <Lock aria-hidden className="size-3.5" />
                {t("systemBadge")}
              </span>
            </Tooltip>
          ) : (
            canModify && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <IconButton
                    icon={<MoreHorizontal />}
                    aria-label={t("groupMenuLabel")}
                    tooltip={t("groupMenuLabel")}
                    className="border border-border bg-surface"
                  />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onSelect={onEditGroup}>
                    <Pencil aria-hidden />
                    <span>{t("editGroup")}</span>
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    destructive
                    disabled={group.itemCount > 0}
                    onSelect={onDeleteGroup}
                  >
                    <Trash2 aria-hidden />
                    <span>
                      {group.itemCount > 0 ? t("deleteGroupNotEmptyHint") : t("deleteGroup")}
                    </span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )
          )}

          <Button variant="secondary" loading={exporting} onClick={onExportExcel}>
            <Download aria-hidden className="mr-1.5 size-4" />
            {t("exportExcel")}
          </Button>

          {canModify &&
            !isCombinationGroup &&
            (group.status === "INACTIVE" ? (
              <Tooltip content={t("inactiveGroupCreateHint")}>
                <span>
                  <Button variant="secondary" disabled>
                    <Upload aria-hidden className="mr-1.5 size-4" />
                    {t("importExcel")}
                  </Button>
                </span>
              </Tooltip>
            ) : (
              <Button variant="secondary" onClick={onImportExcel}>
                <Upload aria-hidden className="mr-1.5 size-4" />
                {t("importExcel")}
              </Button>
            ))}

          {canModify &&
            (group.status === "INACTIVE" ? (
              <Tooltip content={t("inactiveGroupCreateHint")}>
                <span>
                  <Button variant="primary" disabled>
                    <Plus aria-hidden className="mr-1.5 size-4" />
                    {t("addValue")}
                  </Button>
                </span>
              </Tooltip>
            ) : (
              <Button variant="primary" onClick={onAddItem}>
                <Plus aria-hidden className="mr-1.5 size-4" />
                {t("addValue")}
              </Button>
            ))}
        </div>
      </div>

      <div
        role="search"
        aria-label={t("filterFormLabel")}
        className="flex flex-wrap items-center gap-2.5 px-5 pb-3.5"
      >
        <div className="min-w-56 flex-1">
          <Input
            type="search"
            aria-label={t("searchItemsPlaceholder")}
            placeholder={t("searchItemsPlaceholder")}
            leadingIcon={<Search />}
            value={searchQuery}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>
        <div className="w-45 shrink-0">
          <Select
            aria-label={t("statusFilterLabel")}
            options={statusOptions}
            value={statusFilter}
            onValueChange={(value) => onStatusFilterChange(value as StatusFilter)}
          />
        </div>
      </div>

      {isFiltered && canModify && supportsReorder && (
        <div className="border-t border-border bg-page px-5 py-2 text-caption text-text-muted">
          {t("dragDisabledFiltered")}
        </div>
      )}

      {error ? (
        <EmptyState
          className="border-t border-border"
          icon={<TriangleAlert />}
          title={t("itemsErrorTitle")}
          description={t("itemsErrorDescription")}
          action={
            <Button variant="secondary" onClick={onRetry}>
              {commonT("retry")}
            </Button>
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <DndContext
            accessibility={dndAccessibility}
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <table
              aria-label={t("itemsTableLabel")}
              aria-busy={loading || refreshing || undefined}
              className="w-full border-collapse tabular-nums"
            >
              <thead>
                <tr className="border-t border-b border-border bg-table-head text-left text-field-label font-semibold text-text-muted">
                  {canModify && supportsReorder && (
                    <th scope="col" className="w-24 py-2.5 pr-1 pl-3">
                      <span className="sr-only">{t("dragColumnLabel")}</span>
                    </th>
                  )}
                  <th scope="col" className="w-32 px-3 py-2.5">
                    {t("columns.code")}
                  </th>
                  <th scope="col" className="px-3 py-2.5">
                    {t("columns.nameVi")}
                  </th>
                  <th scope="col" className={cn("px-3 py-2.5", WIDE_ONLY_COLUMN)}>
                    {t("columns.nameEn")}
                  </th>
                  {isCombinationGroup && (
                    <>
                      <th scope="col" className="px-3 py-2.5">
                        {t("columns.exam")}
                      </th>
                      <th scope="col" className="px-3 py-2.5">
                        {t("columns.subjects")}
                      </th>
                      <th scope="col" className="w-24 px-3 py-2.5">
                        {t("columns.version")}
                      </th>
                    </>
                  )}
                  <th scope="col" className={cn("w-20 px-3 py-2.5", WIDE_ONLY_COLUMN)}>
                    {t("columns.value")}
                  </th>
                  <th scope="col" className="w-36 px-3 py-2.5">
                    {t("columns.status")}
                  </th>
                  <th scope="col" className="sticky right-0 w-36 bg-table-head py-2.5 pr-5 pl-3 text-right">
                    {t("columns.actions")}
                  </th>
                </tr>
              </thead>
              <tbody
                className={
                  refreshing && !loading
                    ? "opacity-60 transition-opacity duration-150 motion-reduce:transition-none"
                    : "transition-opacity duration-150 motion-reduce:transition-none"
                }
              >
                {loading ? (
                  Array.from({ length: 5 }, (_, rowIndex) => (
                    <tr key={rowIndex} className="border-b border-border last:border-b-0">
                      {Array.from({ length: colCount }, (__, colIndex) => (
                        <td
                          key={colIndex}
                          className={cn(
                            "h-13 px-3",
                            (colIndex === nameEnColIndex || colIndex === valueColIndex) &&
                              WIDE_ONLY_COLUMN,
                          )}
                        >
                          <Skeleton className="h-4 w-full max-w-28" />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : items.length === 0 ? (
                  <tr>
                    <td colSpan={colCount}>
                      <EmptyState
                        icon={<List />}
                        title={t("emptyItemsTitle")}
                        description={
                          isFiltered
                            ? t("emptyItemsFilteredDescription")
                            : t("emptyItemsDescription")
                        }
                        action={
                          isFiltered ? (
                            <Button variant="secondary" onClick={onResetFilters}>
                              {t("resetFilters")}
                            </Button>
                          ) : canModify && group.status === "ACTIVE" ? (
                            <Button variant="primary" onClick={onAddItem}>
                              <Plus aria-hidden className="mr-1.5 size-4" />
                              {t("addValue")}
                            </Button>
                          ) : undefined
                        }
                      />
                    </td>
                  </tr>
                ) : (
                  <SortableContext
                    items={items.map((item) => item.id)}
                    strategy={verticalListSortingStrategy}
                  >
                    {items.map((item, index) => (
                      <SortableItemRow
                        key={item.id}
                        item={item}
                        index={index}
                        total={items.length}
                        locale={locale}
                        isCombinationGroup={isCombinationGroup}
                        canModify={canModify}
                        showReorder={canModify && supportsReorder}
                        reorderEnabled={reorderEnabled}
                        isFiltered={isFiltered}
                        onMoveUp={() => handleMoveByIndex(index, -1)}
                        onMoveDown={() => handleMoveByIndex(index, 1)}
                        onView={() => onViewItem(item)}
                        onEdit={() => onEditItem(item)}
                        onStop={() => onStopItem(item)}
                        onDelete={() => onDeleteItem(item)}
                      />
                    ))}
                  </SortableContext>
                )}
              </tbody>
            </table>
          </DndContext>
        </div>
      )}

      {!loading && !error && totalItems > 0 && (
        <div className="border-t border-border px-5 py-3">
          <Pagination
            page={page + 1}
            pageSize={pageSize}
            total={totalItems}
            onPageChange={(nextOneBased) => onPageChange(nextOneBased - 1)}
            pageSizeOptions={[10, 20, 50, 100]}
            onPageSizeChange={onPageSizeChange}
            labels={{
              nav: t("pagination.nav"),
              previous: t("pagination.previous"),
              next: t("pagination.next"),
              rowsPerPage: t("pagination.rowsPerPage"),
              page: (pageNo) => t("pagination.page", { page: pageNo }),
              range: ({ from, to, total }) => t("pagination.range", { from, to, total }),
            }}
          />
        </div>
      )}

      {!error && canModify && supportsReorder && (
        <div className="border-t border-border px-5 py-3 text-caption text-text-muted">
          {t("reorderFootnote")}
        </div>
      )}
    </section>
  );
}

function SortableItemRow({
  item,
  index,
  total,
  locale,
  isCombinationGroup,
  canModify,
  showReorder,
  reorderEnabled,
  isFiltered,
  onMoveUp,
  onMoveDown,
  onView,
  onEdit,
  onStop,
  onDelete,
}: {
  item: CatalogItemResponse;
  index: number;
  total: number;
  locale: string;
  isCombinationGroup: boolean;
  canModify: boolean;
  showReorder: boolean;
  reorderEnabled: boolean;
  isFiltered: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onView: () => void;
  onEdit: () => void;
  onStop: () => void;
  onDelete: () => void;
}) {
  const t = useTranslations("catalog");
  const sortableAttributes = useCatalogSortableAttributes();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !reorderEnabled,
    attributes: sortableAttributes,
  });

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const displayName = pickName(item, locale);
  const dragTooltip = isFiltered ? t("dragDisabledFiltered") : t("dragHandleTitle");
  const orderedSubjects = useMemo(
    () =>
      item.combination
        ? [...item.combination.subjects].sort((a, b) => a.position - b.position)
        : [],
    [item.combination],
  );

  return (
    <tr
      ref={setNodeRef}
      style={style}
      className={cn(
        "group/row h-13 border-b border-border transition-colors duration-150 last:border-b-0 hover:bg-subtle motion-reduce:transition-none",
        isDragging && "relative z-10 bg-primary-50 shadow-sm",
      )}
    >
      {showReorder && (
        <td className="py-1 pr-1 pl-2">
          <div className="flex items-center gap-0.5">
            <Tooltip content={dragTooltip}>
              <button
                type="button"
                aria-label={t("dragHandleLabel", { name: displayName })}
                className={cn(
                  "inline-flex size-8 items-center justify-center rounded-sm text-text-placeholder transition-colors",
                  reorderEnabled
                    ? "cursor-grab hover:bg-subtle hover:text-text-secondary active:cursor-grabbing"
                    : "cursor-not-allowed opacity-40",
                )}
                {...(reorderEnabled ? attributes : {})}
                {...(reorderEnabled ? listeners : {})}
                aria-disabled={!reorderEnabled || undefined}
                onClick={(event) => {
                  if (!reorderEnabled) {
                    event.preventDefault();
                  }
                }}
              >
                <GripVertical aria-hidden className="size-4" />
              </button>
            </Tooltip>
            <IconButton
              icon={<ArrowUp className="size-3.5" />}
              aria-label={t("moveUpLabel", { name: displayName })}
              tooltip={isFiltered ? t("dragDisabledFiltered") : t("moveUpLabel", { name: displayName })}
              disabled={!reorderEnabled || index === 0}
              onClick={onMoveUp}
              className="size-7 [&_svg]:size-3.5"
            />
            <IconButton
              icon={<ArrowDown className="size-3.5" />}
              aria-label={t("moveDownLabel", { name: displayName })}
              tooltip={
                isFiltered ? t("dragDisabledFiltered") : t("moveDownLabel", { name: displayName })
              }
              disabled={!reorderEnabled || index >= total - 1}
              onClick={onMoveDown}
              className="size-7 [&_svg]:size-3.5"
            />
          </div>
        </td>
      )}
      <td className="px-3">
        <span className="inline-block rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
          {item.code}
        </span>
      </td>
      <td className="px-3 font-medium text-text">{item.nameVi}</td>
      <td className={cn("px-3 text-text-secondary", WIDE_ONLY_COLUMN)}>{item.nameEn || "—"}</td>
      {isCombinationGroup && (
        <>
          <td className="px-3 text-text-secondary">
            {item.combination ? (
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="font-medium text-text">{item.combination.examCode}</span>
                <span>
                  {pickName(
                    {
                      nameVi: item.combination.examNameVi,
                      nameEn: item.combination.examNameEn,
                    },
                    locale,
                  )}
                </span>
                {item.combination.examStatus === "INACTIVE" && (
                  <Badge>{t("statuses.INACTIVE")}</Badge>
                )}
              </div>
            ) : (
              "—"
            )}
          </td>
          <td className="px-3">
            {orderedSubjects.length > 0 ? (
              <div className="flex flex-wrap items-center gap-1">
                {orderedSubjects.map((subject) => (
                  <Chip key={subject.subjectItemId} size="sm">
                    {pickName(subject, locale)}
                    {subject.status === "INACTIVE" ? ` (${t("statuses.INACTIVE")})` : ""}
                  </Chip>
                ))}
              </div>
            ) : (
              "—"
            )}
          </td>
          <td className="px-3">
            {item.combination ? (
              <Badge>{t("versionBadge", { version: item.combination.versionNo })}</Badge>
            ) : (
              "—"
            )}
          </td>
        </>
      )}
      <td className={cn("px-3 text-text-secondary tabular-nums", WIDE_ONLY_COLUMN)}>
        {item.value}
      </td>
      <td className="px-3">
        <StatusChip tone={item.status === "ACTIVE" ? "success" : "neutral"}>
          {t(`statuses.${item.status}`)}
        </StatusChip>
      </td>
      <td
        className={cn(
          // Cột thao tác dính mép phải để luôn bấm được khi bảng rộng hơn thẻ (màn hình ~1280px).
          "sticky right-0 bg-surface py-1 pr-3 pl-2 text-right whitespace-nowrap transition-colors duration-150 group-hover/row:bg-subtle motion-reduce:transition-none",
          isDragging && "bg-primary-50",
        )}
      >
        <div className="inline-flex items-center justify-end gap-0.5">
          <IconButton
            icon={<Eye />}
            aria-label={t("detailItem", { name: displayName })}
            tooltip={t("detailItem", { name: displayName })}
            onClick={onView}
            className="text-primary-text"
          />
          {canModify && (
            <>
              <IconButton
                icon={<Pencil />}
                aria-label={t("editItem", { name: displayName })}
                tooltip={t("editItem", { name: displayName })}
                onClick={onEdit}
                className="text-primary-text"
              />
              {item.status === "ACTIVE" && (
                <IconButton
                  icon={<Ban />}
                  aria-label={t("stopItem", { name: displayName })}
                  tooltip={t("stopItem", { name: displayName })}
                  onClick={onStop}
                  className="text-warning"
                />
              )}
              <IconButton
                icon={<Trash2 />}
                tone="danger"
                aria-label={t("deleteItem", { name: displayName })}
                tooltip={
                  item.system ? t("systemItemLocked") : t("deleteItem", { name: displayName })
                }
                disabled={item.system}
                onClick={onDelete}
              />
            </>
          )}
        </div>
      </td>
    </tr>
  );
}
