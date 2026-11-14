"use client";

import type { Announcements, ScreenReaderInstructions } from "@dnd-kit/core";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

export type CatalogDndMessageKey =
  | "pickedUp"
  | "backAtStart"
  | "draggedOverTarget"
  | "draggedOverNoTarget"
  | "dropped"
  | "droppedOnTarget"
  | "dragCancelled"
  | "instructions";

export interface CatalogDndAccessibility {
  announcements: Announcements;
  screenReaderInstructions: ScreenReaderInstructions;
}

/**
 * Lời đọc và hướng dẫn cho trình đọc màn hình của một `DndContext`, thay cho bản tiếng Anh mặc
 * định của dnd-kit (vốn đọc UUID). `resolveName` đổi id đang kéo thành tên hiển thị của mục.
 */
export function buildCatalogDndAccessibility(
  translate: (key: CatalogDndMessageKey, values?: { name: string; target?: string }) => string,
  resolveName: (id: string) => string,
): CatalogDndAccessibility {
  const name = (id: unknown) => resolveName(String(id));
  // dnd-kit fires onDragOver right after onDragStart with `over` = the picked-up item itself, which
  // would read "Item X is over X" straight after "Picked up item X". Stay silent until the item has
  // actually moved over another one; moving back onto itself then says it is back at its start.
  let movedAway = false;
  return {
    announcements: {
      onDragStart: ({ active }) => {
        movedAway = false;
        return translate("pickedUp", { name: name(active.id) });
      },
      onDragOver: ({ active, over }) => {
        if (!over) return translate("draggedOverNoTarget", { name: name(active.id) });
        if (over.id === active.id) {
          return movedAway ? translate("backAtStart", { name: name(active.id) }) : undefined;
        }
        movedAway = true;
        return translate("draggedOverTarget", { name: name(active.id), target: name(over.id) });
      },
      onDragEnd: ({ active, over }) => {
        movedAway = false;
        return over && over.id !== active.id
          ? translate("droppedOnTarget", { name: name(active.id), target: name(over.id) })
          : translate("dropped", { name: name(active.id) });
      },
      onDragCancel: ({ active }) => {
        movedAway = false;
        return translate("dragCancelled", { name: name(active.id) });
      },
    },
    screenReaderInstructions: { draggable: translate("instructions") },
  };
}

/**
 * `aria-roledescription` của tay nắm sắp xếp (mặc định của dnd-kit là chữ "sortable" tiếng Anh):
 * `useSortable({ id, attributes: useCatalogSortableAttributes() })`.
 */
export function useCatalogSortableAttributes(): { roleDescription: string } {
  const t = useTranslations("catalog");
  const roleDescription = t("dnd.roleDescription");
  return useMemo(() => ({ roleDescription }), [roleDescription]);
}

/** Dùng cho mọi `DndContext` của màn Cấu hình chung: `<DndContext accessibility={...} />`. */
export function useCatalogDndAccessibility(
  resolveName: (id: string) => string,
): CatalogDndAccessibility {
  const t = useTranslations("catalog");
  return useMemo(
    () => buildCatalogDndAccessibility((key, values) => t(`dnd.${key}`, values), resolveName),
    [resolveName, t],
  );
}
