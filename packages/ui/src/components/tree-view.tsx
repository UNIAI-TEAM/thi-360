"use client";

import { ChevronRight, Loader2 } from "lucide-react";
import { useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
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
  className?: string;
  renderTrailing?: (item: TreeViewItem) => ReactNode;
}

interface VisibleTreeRow {
  item: TreeViewItem;
  level: number;
  firstChildId: string | null;
  visibleParentId: string | null;
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
  className,
  renderTrailing,
}: TreeViewProps) {
  const rowRefs = useRef<Map<string, HTMLDivElement>>(new Map());
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
      for (const item of list) {
        const level = item.depth > 0 ? item.depth : depthOffset;
        const isExpanded = expandedIds.has(item.id);
        const children = childrenByParent.get(item.id);
        const firstChildId = isExpanded && children && children.length > 0 ? children[0]!.id : null;
        rows.push({
          item,
          level,
          firstChildId,
          visibleParentId,
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
      default:
        break;
    }
  }

  return (
    <div role="tree" aria-label={ariaLabel} className={cn("flex flex-col gap-0.5 p-2", className)}>
      {visibleRows.map(({ item, level }, rowIndex) => {
        const isExpanded = expandedIds.has(item.id);
        const isSelected = selectedId === item.id;
        const isLoading = Boolean(loadingIds?.has(item.id));
        const indentCount = Math.max(0, level - 1);

        const nameStyle =
          level === 1
            ? "text-body font-semibold"
            : level === 2
              ? "text-body font-medium"
              : "text-body font-normal text-text-secondary";

        return (
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
            aria-level={level}
            aria-selected={isSelected}
            aria-expanded={item.hasChildren ? isExpanded : undefined}
            tabIndex={activeFocusId === item.id ? 0 : -1}
            onKeyDown={(event) => handleKeyDown(event, rowIndex)}
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

              <span className="shrink-0 rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold whitespace-nowrap text-text">
                {item.code}
              </span>

              <span
                className={cn(
                  "min-w-0 flex-1 truncate",
                  nameStyle,
                  item.inactive ? "text-text-muted" : isSelected ? "text-primary-text" : "text-text",
                )}
              >
                {item.name}
              </span>

              {item.statusLabel && (
                <StatusChip tone={item.statusTone ?? "neutral"} className="shrink-0">
                  {item.statusLabel}
                </StatusChip>
              )}

              {item.refsSummary && (
                <span className="hidden shrink-0 text-caption whitespace-nowrap text-text-muted sm:inline">
                  {item.refsSummary}
                </span>
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
      })}
    </div>
  );
}
