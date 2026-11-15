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
  LiveStatus,
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
import { useInlineOverflow } from "../scroll-region";
import { DRAG_HANDLE, focusRingInset, STATIC_CHIP } from "../styles";
import { usePrefersReducedMotion } from "../use-reduced-motion";
import { pickName } from "../utils";

/**
 * Bảng giá trị chọn cột theo bề rộng của chính thẻ (truy vấn vùng chứa trên `<section>`), không theo bề rộng màn
 * hình: thẻ hẹp hơn màn hình khoảng 593px vì có sidebar và cột danh mục. Mỗi cột có bề rộng tối thiểu đủ đọc
 * (px): sắp xếp 104 · mã 112 · tên 112 · kỳ thi 80 (chỉ mã) hoặc 176 (mã + tên, tối đa hai dòng) · các môn 168 ·
 * phiên bản 96 · giá trị 80 · trạng thái 152 · thao tác 186 (bốn nút) hoặc 96 (chỉ nút xem).
 *
 * Thẻ không đủ chỗ thì bỏ dần cột ít quan trọng thay vì bóp cột: Tên tiếng Anh, Giá trị, Phiên bản, tên kỳ thi,
 * Kỳ thi, Các môn. Các cột đó vẫn có trong ngăn chi tiết; riêng kỳ thi và các môn còn hiện thành dòng phụ dưới
 * tên khi cột của chúng bị bỏ. Mỗi ngưỡng bên dưới là tổng bề rộng tối thiểu của bộ cột đang hiện cộng 6px dự
 * phòng, nên từ ngưỡng `stickyActions` trở lên bảng luôn vừa thẻ và không ô nào nằm dưới cột Thao tác.
 *
 * Dưới ngưỡng `stickyActions` (tablet) bộ cột tối thiểu không vừa: bảng cuộn ngang trong vùng có tên, nhận focus
 * bàn phím, và cột Thao tác thôi dính mép phải để không che dữ liệu ở vị trí cuộn đầu.
 */
type ColumnLayout = {
  stickyActions: string;
  nameEn: string;
  value: string;
  version: string;
  subjects: string;
  exam: string;
  /** Bề rộng tối thiểu của cột Kỳ thi: hẹp khi chỉ hiện mã, rộng khi hiện cả tên. */
  examWidth: string;
  examName: string;
  /** Dòng phụ dưới tên (mã kỳ thi · các môn): ẩn khi cột Kỳ thi đã hiện. */
  summary: string;
  /** Phần các môn của dòng phụ: ẩn khi cột Các môn đã hiện. */
  summarySubjects: string;
};

/** Chip môn học trong ô bảng: bản gọn cao 24px của chip tĩnh. */
const STATIC_CHIP_COMPACT = `${STATIC_CHIP} h-6 px-2`;

const SIMPLE_ONLY = {
  version: "",
  subjects: "",
  exam: "",
  examWidth: "",
  examName: "",
  summary: "",
  summarySubjects: "",
};

const COLUMN_LAYOUTS: Record<"manage" | "view", Record<"simple" | "combination", ColumnLayout>> = {
  // Có cột sắp xếp (104) và bốn nút thao tác (186).
  manage: {
    simple: {
      ...SIMPLE_ONLY,
      stickyActions: "@min-[672px]:sticky @min-[672px]:right-0",
      value: "hidden @min-[752px]:table-cell",
      nameEn: "hidden @min-[864px]:table-cell",
    },
    combination: {
      stickyActions: "@min-[672px]:sticky @min-[672px]:right-0",
      subjects: "hidden @min-[840px]:table-cell",
      summarySubjects: "@min-[840px]:hidden",
      exam: "hidden @min-[920px]:table-cell",
      summary: "@min-[920px]:hidden",
      examWidth: "min-w-20 @min-[1016px]:min-w-44",
      examName: "hidden @min-[1016px]:inline",
      version: "hidden @min-[1112px]:table-cell",
      value: "hidden @min-[1192px]:table-cell",
      nameEn: "hidden @min-[1304px]:table-cell",
    },
  },
  // Chỉ xem: không có cột sắp xếp, cột thao tác chỉ còn nút xem (96).
  view: {
    simple: {
      ...SIMPLE_ONLY,
      stickyActions: "@min-[480px]:sticky @min-[480px]:right-0",
      value: "hidden @min-[560px]:table-cell",
      nameEn: "hidden @min-[672px]:table-cell",
    },
    combination: {
      stickyActions: "@min-[480px]:sticky @min-[480px]:right-0",
      subjects: "hidden @min-[648px]:table-cell",
      summarySubjects: "@min-[648px]:hidden",
      exam: "hidden @min-[728px]:table-cell",
      summary: "@min-[728px]:hidden",
      examWidth: "min-w-20 @min-[824px]:min-w-44",
      examName: "hidden @min-[824px]:inline",
      version: "hidden @min-[920px]:table-cell",
      value: "hidden @min-[1000px]:table-cell",
      nameEn: "hidden @min-[1112px]:table-cell",
    },
  },
};

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

  const showReorder = canModify && supportsReorder;
  const layout =
    COLUMN_LAYOUTS[showReorder ? "manage" : "view"][isCombinationGroup ? "combination" : "simple"];
  // Lớp ẩn/hiện của từng cột theo đúng thứ tự cột, để hàng khung xương ẩn cùng lúc với tiêu đề.
  const columnVisibility = [
    ...(showReorder ? [""] : []),
    "",
    "",
    layout.nameEn,
    ...(isCombinationGroup ? [layout.exam, layout.subjects, layout.version] : []),
    layout.value,
    "",
    "",
  ];
  const colCount = columnVisibility.length;
  // Câu đọc cho trình đọc màn hình sau khi tìm/lọc xong (WCAG 4.1.3). Để rỗng trong lúc đang tải lại: khi kết
  // quả về, vùng status đổi từ rỗng sang có chữ nên được đọc, kể cả khi số lượng trùng với lần trước.
  const resultStatus =
    loading || refreshing || error ? "" : t("itemsCountStatus", { count: totalItems });
  const [scrollerRef, tableOverflows] = useInlineOverflow<HTMLDivElement>();
  return (
    <section
      aria-labelledby="catalog-items-heading"
      className="@container min-w-0 flex-1 overflow-hidden rounded-lg border border-border bg-surface shadow-xs"
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

      <LiveStatus message={resultStatus} />

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
        <div
          ref={scrollerRef}
          {...(tableOverflows
            ? { role: "region", "aria-label": t("itemsTableLabel"), tabIndex: 0 }
            : null)}
          className={cn("overflow-x-auto", focusRingInset)}
        >
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
                  {showReorder && (
                    <th scope="col" className="w-26 min-w-26 py-2.5 pr-1 pl-3">
                      <span className="sr-only">{t("dragColumnLabel")}</span>
                    </th>
                  )}
                  <th scope="col" className="w-28 min-w-28 px-3 py-2.5">
                    {t("columns.code")}
                  </th>
                  <th scope="col" className="min-w-28 px-3 py-2.5">
                    {t("columns.nameVi")}
                  </th>
                  <th scope="col" className={cn("min-w-28 px-3 py-2.5", layout.nameEn)}>
                    {t("columns.nameEn")}
                  </th>
                  {isCombinationGroup && (
                    <>
                      <th scope="col" className={cn("px-3 py-2.5", layout.examWidth, layout.exam)}>
                        {t("columns.exam")}
                      </th>
                      <th scope="col" className={cn("min-w-42 px-3 py-2.5", layout.subjects)}>
                        {t("columns.subjects")}
                      </th>
                      <th
                        scope="col"
                        className={cn(
                          "w-24 min-w-24 px-3 py-2.5 whitespace-nowrap",
                          layout.version,
                        )}
                      >
                        {t("columns.version")}
                      </th>
                    </>
                  )}
                  <th
                    scope="col"
                    className={cn("w-20 min-w-20 px-3 py-2.5 whitespace-nowrap", layout.value)}
                  >
                    {t("columns.value")}
                  </th>
                  <th scope="col" className="w-38 min-w-38 px-3 py-2.5 whitespace-nowrap">
                    {t("columns.status")}
                  </th>
                  <th
                    scope="col"
                    className={cn(
                      "w-24 min-w-24 bg-table-head py-2.5 pr-5 pl-3 text-right whitespace-nowrap",
                      layout.stickyActions,
                    )}
                  >
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
                      {columnVisibility.map((visibility, colIndex) => (
                        <td key={colIndex} className={cn("h-13 px-3", visibility)}>
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
                        layout={layout}
                        canModify={canModify}
                        showReorder={showReorder}
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
  layout,
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
  layout: ColumnLayout;
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
  const reduceMotion = usePrefersReducedMotion();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !reorderEnabled,
    attributes: sortableAttributes,
    // C-17: giảm chuyển động thì bỏ hiệu ứng trượt 200ms của dnd-kit, dòng nhảy thẳng tới chỗ mới.
    transition: reduceMotion ? null : undefined,
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
                  DRAG_HANDLE,
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
      <td className="px-3 py-2">
        <span className="inline-block rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold whitespace-nowrap text-text">
          {item.code}
        </span>
      </td>
      <td className="px-3 py-2 font-medium text-text">
        {/* Tối đa hai dòng (DESIGN.md mục 5.13); tên đầy đủ có trong ngăn chi tiết. */}
        <span className="line-clamp-2 wrap-break-word">{item.nameVi}</span>
        {item.combination && (
          <span
            className={cn(
              "mt-0.5 line-clamp-2 text-caption font-normal wrap-break-word text-text-muted",
              layout.summary,
            )}
          >
            <span className="font-medium text-text-secondary">{item.combination.examCode}</span>
            {orderedSubjects.length > 0 && (
              <span className={layout.summarySubjects}>
                {` · ${orderedSubjects.map((subject) => pickName(subject, locale)).join(", ")}`}
              </span>
            )}
          </span>
        )}
      </td>
      <td className={cn("px-3 py-2 text-text-secondary", layout.nameEn)}>
        <span className="line-clamp-2 wrap-break-word">{item.nameEn || "—"}</span>
      </td>
      {isCombinationGroup && (
        <>
          <td className={cn("px-3 py-2 text-text-secondary", layout.exam)}>
            {item.combination ? (
              <div className="flex flex-col items-start gap-1">
                {/* Mã và tên kỳ thi là một dòng chữ liền, tối đa hai dòng; thẻ hẹp thì chỉ còn mã. */}
                <span className="line-clamp-2 wrap-break-word">
                  <span className="font-medium whitespace-nowrap text-text">
                    {item.combination.examCode}
                  </span>
                  <span className={layout.examName}>
                    {` ${pickName(
                      {
                        nameVi: item.combination.examNameVi,
                        nameEn: item.combination.examNameEn,
                      },
                      locale,
                    )}`}
                  </span>
                </span>
                {item.combination.examStatus === "INACTIVE" && (
                  <Badge>{t("statuses.INACTIVE")}</Badge>
                )}
              </div>
            ) : (
              "—"
            )}
          </td>
          <td className={cn("px-3 py-2", layout.subjects)}>
            {orderedSubjects.length > 0 ? (
              <div className="flex flex-wrap items-center gap-1">
                {orderedSubjects.map((subject) => (
                  <Chip key={subject.subjectItemId} variant="tag" className={STATIC_CHIP_COMPACT}>
                    {pickName(subject, locale)}
                    {subject.status === "INACTIVE" ? ` (${t("statuses.INACTIVE")})` : ""}
                  </Chip>
                ))}
              </div>
            ) : (
              "—"
            )}
          </td>
          <td className={cn("px-3 py-2", layout.version)}>
            {item.combination ? (
              <Badge>{t("versionBadge", { version: item.combination.versionNo })}</Badge>
            ) : (
              "—"
            )}
          </td>
        </>
      )}
      <td
        className={cn("px-3 py-2 whitespace-nowrap text-text-secondary tabular-nums", layout.value)}
      >
        {item.value}
      </td>
      <td className="px-3 py-2">
        <StatusChip tone={item.status === "ACTIVE" ? "success" : "neutral"}>
          {t(`statuses.${item.status}`)}
        </StatusChip>
      </td>
      <td
        className={cn(
          // Cột thao tác dính mép phải để vẫn bấm được khi một mã dài làm bảng rộng hơn thẻ. Chỉ dính khi thẻ đủ
          // rộng cho bộ cột tối thiểu; hẹp hơn thì cuộn cùng bảng để không che dữ liệu (xem COLUMN_LAYOUTS).
          "bg-surface py-1 pr-3 pl-2 text-right whitespace-nowrap transition-colors duration-150 group-hover/row:bg-subtle motion-reduce:transition-none",
          layout.stickyActions,
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
