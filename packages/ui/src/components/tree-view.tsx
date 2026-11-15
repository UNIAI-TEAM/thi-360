"use client";

import { ChevronRight, Loader2 } from "lucide-react";
import { Fragment, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { StatusChip, type StatusTone } from "./status-chip";

export interface TreeViewItem {
  id: string;
  parentId?: string | null;
  /** 1-based depth (1 = root). */
  depth: number;
  code: string;
  name: string;
  hasChildren: boolean;
  leafCount?: number | null;
  /** Chỉ truyền khi trạng thái khác Hoạt động (DESIGN.md mục 5.11). */
  statusLabel?: string | null;
  statusTone?: StatusTone;
  /** Tóm tắt liên kết riêng của chính nút (hiển thị gọn cuối dòng). */
  refsSummary?: string | null;
  /**
   * Số liên kết riêng. Khi cây hẹp (< 48rem), `refsSummary` thu thành chip `+N`
   * (tên đầy đủ nằm ở `aria-label`/`title`) để tên nút giữ được chiều rộng.
   */
  refsCount?: number;
  /** Làm mờ chữ khi nút ngừng sử dụng hoặc ngừng theo cấp trên. */
  inactive?: boolean;
}

export interface TreeViewLabels {
  expandNode: (name: string) => string;
  collapseNode: (name: string) => string;
  loadingChildren?: string;
}

export interface TreeViewProps {
  items: readonly TreeViewItem[];
  expandedIds: ReadonlySet<string>;
  selectedId?: string | null;
  loadingIds?: ReadonlySet<string>;
  onToggleExpand: (item: TreeViewItem) => void;
  onSelect: (item: TreeViewItem) => void;
  labels: TreeViewLabels;
  "aria-label": string;
  /** Id của dòng hướng dẫn phím (nhìn thấy được) đặt cạnh cây. */
  "aria-describedby"?: string;
  className?: string;
  /**
   * Cây là MỘT điểm dừng Tab (mẫu tree của WAI-ARIA): điều khiển trong `renderLeading`/`renderTrailing` phải đặt
   * `tabIndex={-1}` và chỉ dành cho chuột. Người dùng bàn phím tới các thao tác đó qua `onRowMenu`.
   */
  renderLeading?: (item: TreeViewItem) => ReactNode;
  renderTrailing?: (item: TreeViewItem) => ReactNode;
  renderRowWrapper?: (item: TreeViewItem, row: ReactNode) => ReactNode;
  /**
   * Phím menu ngữ cảnh hoặc Shift+F10 trên dòng đang focus: mở thao tác của dòng (thường là menu "⋯" sẵn có).
   * Truyền thì dòng khai báo `aria-keyshortcuts` và menu chuột phải của trình duyệt không bật lên khi dùng phím.
   */
  onRowMenu?: (item: TreeViewItem) => void;
}

/** Phím mở thao tác của dòng, theo cú pháp `aria-keyshortcuts`. */
const TREE_ROW_MENU_KEYSHORTCUTS = "Shift+F10 ContextMenu";

interface VisibleTreeRow {
  item: TreeViewItem;
  level: number;
  firstChildId: string | null;
  visibleParentId: string | null;
  setSize: number;
  posInSet: number;
}

/**
 * Cây phân cấp hỗ trợ nạp con lười theo `parentId`, đường kẻ nối cấp,
 * điều hướng bàn phím chuẩn WAI-ARIA (`role="tree"` / `role="treeitem"`).
 */
export function TreeView({
  items,
  expandedIds,
  selectedId,
  loadingIds,
  onToggleExpand,
  onSelect,
  labels,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
  className,
  renderLeading,
  renderTrailing,
  renderRowWrapper,
  onRowMenu,
}: TreeViewProps) {
  const rowRefs = useRef<Map<string, HTMLDivElement>>(new Map());
  // Phím menu/Shift+F10 còn sinh sự kiện `contextmenu` (trên Windows là lúc nhả phím): cờ này chặn menu của trình
  // duyệt đúng lần đó. Cờ tự bỏ sau 1 giây hoặc khi bấm chuột, nên chuột phải vẫn giữ hành vi mặc định.
  const menuKeyPressedRef = useRef(false);
  const nameIdPrefix = useId();
  const [focusedId, setFocusedId] = useState<string | null>(null);

  const visibleRows = useMemo<VisibleTreeRow[]>(() => {
    const idSet = new Set(items.map((item) => item.id));
    const childrenByParent = new Map<string | null, TreeViewItem[]>();

    for (const item of items) {
      const parentKey = item.parentId && idSet.has(item.parentId) ? item.parentId : null;
      const list = childrenByParent.get(parentKey);
      if (list) {
        list.push(item);
      } else {
        childrenByParent.set(parentKey, [item]);
      }
    }

    const rows: VisibleTreeRow[] = [];

    function visit(parentKey: string | null, depthOffset: number, visibleParentId: string | null) {
      const list = childrenByParent.get(parentKey);
      if (!list) return;
      const setSize = list.length;
      for (let idx = 0; idx < list.length; idx++) {
        const item = list[idx]!;
        const level = item.depth > 0 ? item.depth : depthOffset;
        const isExpanded = expandedIds.has(item.id);
        const children = childrenByParent.get(item.id);
        const firstChildId = isExpanded && children && children.length > 0 ? children[0]!.id : null;
        rows.push({
          item,
          level,
          firstChildId,
          visibleParentId,
          setSize,
          posInSet: idx + 1,
        });
        if (isExpanded) {
          visit(item.id, level + 1, item.id);
        }
      }
    }

    visit(null, 1, null);
    return rows;
  }, [expandedIds, items]);

  const activeFocusId = useMemo(() => {
    if (focusedId && visibleRows.some((row) => row.item.id === focusedId)) {
      return focusedId;
    }
    if (selectedId && visibleRows.some((row) => row.item.id === selectedId)) {
      return selectedId;
    }
    return visibleRows[0]?.item.id ?? null;
  }, [focusedId, selectedId, visibleRows]);

  function focusRow(id: string) {
    setFocusedId(id);
    rowRefs.current.get(id)?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>, rowIndex: number) {
    if (event.target !== event.currentTarget) return;
    const current = visibleRows[rowIndex];
    if (!current) return;
    const { item, firstChildId, visibleParentId } = current;
    const isExpanded = expandedIds.has(item.id);

    switch (event.key) {
      case "ArrowDown": {
        const next = visibleRows[rowIndex + 1];
        if (next) {
          event.preventDefault();
          focusRow(next.item.id);
        }
        break;
      }
      case "ArrowUp": {
        const prev = visibleRows[rowIndex - 1];
        if (prev) {
          event.preventDefault();
          focusRow(prev.item.id);
        }
        break;
      }
      case "ArrowRight": {
        if (!item.hasChildren) return;
        event.preventDefault();
        if (!isExpanded) {
          onToggleExpand(item);
        } else if (firstChildId) {
          focusRow(firstChildId);
        }
        break;
      }
      case "ArrowLeft": {
        if (item.hasChildren && isExpanded) {
          event.preventDefault();
          onToggleExpand(item);
        } else if (visibleParentId) {
          event.preventDefault();
          focusRow(visibleParentId);
        }
        break;
      }
      case "Home": {
        const first = visibleRows[0];
        if (first) {
          event.preventDefault();
          focusRow(first.item.id);
        }
        break;
      }
      case "End": {
        const last = visibleRows[visibleRows.length - 1];
        if (last) {
          event.preventDefault();
          focusRow(last.item.id);
        }
        break;
      }
      case "Enter":
      case " ": {
        event.preventDefault();
        onSelect(item);
        break;
      }
      case "ContextMenu":
      case "F10": {
        if (!onRowMenu || (event.key === "F10" && !event.shiftKey)) return;
        event.preventDefault();
        menuKeyPressedRef.current = true;
        setTimeout(() => {
          menuKeyPressedRef.current = false;
        }, 1_000);
        onRowMenu(item);
        break;
      }
      default:
        break;
    }
  }

  return (
    <div
      role="tree"
      aria-label={ariaLabel}
      aria-describedby={ariaDescribedBy}
      className={cn("@container flex flex-col gap-0.5 p-2", className)}
    >
      {visibleRows.map(({ item, level, setSize, posInSet }, rowIndex) => {
        const isExpanded = expandedIds.has(item.id);
        const isSelected = selectedId === item.id;
        const isLoading = Boolean(loadingIds?.has(item.id));
        const indentCount = Math.max(0, level - 1);

        const nameStyle =
          level === 1
            ? "text-body font-semibold"
            : level === 2
              ? "text-body font-medium"
              : "text-body font-normal";

        const nameColor = item.inactive
          ? "text-text-muted"
          : isSelected
            ? "text-primary-text"
            : level >= 3
              ? "text-text-secondary"
              : "text-text";
        // Tên truy cập của dòng gồm mã rồi tên, đúng như chữ đang hiện (WCAG 2.5.3). Trạng thái khác Hoạt động và
        // liên kết riêng là mô tả của dòng: trình đọc màn hình đọc sau tên khi đi mũi tên qua cây (WCAG 1.3.1).
        const codeId = `${nameIdPrefix}-code-${item.id}`;
        const nameId = `${nameIdPrefix}-name-${item.id}`;
        const statusId = item.statusLabel ? `${nameIdPrefix}-status-${item.id}` : null;
        const refsId = item.refsSummary ? `${nameIdPrefix}-refs-${item.id}` : null;
        const describedBy = [statusId, refsId].filter(Boolean).join(" ") || undefined;
        const rowElement = (
          <div
            key={item.id}
            ref={(el) => {
              if (el) {
                rowRefs.current.set(item.id, el);
              } else {
                rowRefs.current.delete(item.id);
              }
            }}
            role="treeitem"
            data-tree-item-id={item.id}
            aria-labelledby={`${codeId} ${nameId}`}
            aria-describedby={describedBy}
            aria-level={level}
            aria-setsize={setSize}
            aria-posinset={posInSet}
            aria-selected={isSelected}
            aria-expanded={item.hasChildren ? isExpanded : undefined}
            aria-keyshortcuts={onRowMenu ? TREE_ROW_MENU_KEYSHORTCUTS : undefined}
            tabIndex={activeFocusId === item.id ? 0 : -1}
            onKeyDown={(event) => handleKeyDown(event, rowIndex)}
            onFocus={(event) => {
              // Focus trả về dòng bằng mã (sau khi đóng menu) cũng cập nhật điểm dừng Tab của cây.
              if (event.target === event.currentTarget) setFocusedId(item.id);
            }}
            onPointerDown={() => {
              menuKeyPressedRef.current = false;
            }}
            onContextMenu={(event) => {
              if (menuKeyPressedRef.current) {
                menuKeyPressedRef.current = false;
                event.preventDefault();
              }
            }}
            onClick={() => {
              setFocusedId(item.id);
              onSelect(item);
            }}
            className={cn(
              "group flex min-h-11 cursor-pointer items-stretch rounded-md pr-2 transition-colors duration-150 motion-reduce:transition-none",
              isSelected ? "bg-primary-50" : "hover:bg-subtle",
              focusRing,
            )}
          >
            {Array.from({ length: indentCount }, (_, indentIdx) => (
              <span
                key={indentIdx}
                aria-hidden
                className="flex w-7 shrink-0 justify-center"
              >
                <span className="h-full w-px bg-border" />
              </span>
            ))}

            <div className="flex min-w-0 flex-1 items-center gap-2 py-1.5 pl-1">
              {renderLeading && renderLeading(item)}
              {item.hasChildren ? (
                <button
                  type="button"
                  tabIndex={-1}
                  aria-label={
                    isExpanded ? labels.collapseNode(item.name) : labels.expandNode(item.name)
                  }
                  onClick={(event) => {
                    event.stopPropagation();
                    setFocusedId(item.id);
                    onToggleExpand(item);
                  }}
                  className="inline-flex size-7 shrink-0 items-center justify-center rounded-sm text-text-muted transition-colors hover:bg-subtle hover:text-text"
                >
                  {isLoading ? (
                    <Loader2
                      aria-label={labels.loadingChildren}
                      className="size-3.5 animate-spin motion-reduce:animate-none"
                    />
                  ) : (
                    <ChevronRight
                      aria-hidden
                      className={cn(
                        "size-3.5 transition-transform duration-150 motion-reduce:transition-none",
                        isExpanded && "rotate-90",
                      )}
                    />
                  )}
                </button>
              ) : (
                <span
                  aria-hidden
                  className="inline-flex size-7 shrink-0 items-center justify-center text-text-placeholder"
                >
                  <span className="size-1.5 rounded-full bg-current" />
                </span>
              )}

              <span
                id={codeId}
                title={item.code}
                className="max-w-24 shrink-0 truncate rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold whitespace-nowrap text-text"
              >
                {item.code}
              </span>

              {/*
                Tên có ưu tiên chiều rộng: không bao giờ hẹp hơn 8rem, phần còn lại co lại trước. Tên dài xuống tối
                đa hai dòng (DESIGN.md mục 5.13) thay vì cắt một dòng; dòng đang chọn hoặc đang được focus bằng bàn
                phím hiện đủ tên, nên không cần `title` (bàn phím và màn hình cảm ứng không tới được `title`).
              */}
              <span
                id={nameId}
                className={cn(
                  "min-w-[8rem] flex-1 wrap-break-word",
                  !isSelected && "line-clamp-2 group-focus-visible:line-clamp-none",
                  nameStyle,
                  nameColor,
                )}
              >
                {item.name}
              </span>

              {item.statusLabel && (
                <StatusChip
                  id={statusId ?? undefined}
                  tone={item.statusTone ?? "neutral"}
                  className="shrink-0"
                >
                  {item.statusLabel}
                </StatusChip>
              )}

              {item.refsSummary && (
                <>
                  {/* Dù bị ẩn khi cây hẹp, phần tử này vẫn là mô tả của dòng qua `aria-describedby`. */}
                  <span
                    id={refsId ?? undefined}
                    title={item.refsSummary}
                    className="hidden max-w-48 min-w-0 shrink truncate text-caption whitespace-nowrap text-text-muted @3xl:inline"
                  >
                    {item.refsSummary}
                  </span>
                  {item.refsCount !== undefined && (
                    <span
                      role="img"
                      aria-label={item.refsSummary}
                      title={item.refsSummary}
                      className="shrink-0 rounded-full bg-subtle px-1.5 text-caption font-medium whitespace-nowrap text-text-muted tabular-nums @3xl:hidden"
                    >
                      +{item.refsCount}
                    </span>
                  )}
                </>
              )}

              {item.leafCount !== undefined && item.leafCount !== null && (
                <span className="min-w-5 shrink-0 text-right text-caption font-medium text-text-muted tabular-nums">
                  {item.leafCount}
                </span>
              )}

              {renderTrailing && (
                <div
                  className="flex shrink-0 items-center gap-0.5"
                  onClick={(event) => event.stopPropagation()}
                >
                  {renderTrailing(item)}
                </div>
              )}
          </div>
          </div>
        );

        return renderRowWrapper ? (
          <Fragment key={item.id}>{renderRowWrapper(item, rowElement)}</Fragment>
        ) : (
          rowElement
        );
      })}
    </div>
  );
}
