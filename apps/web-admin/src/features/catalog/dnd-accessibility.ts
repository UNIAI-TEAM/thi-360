"use client";

import type { Announcements, ScreenReaderInstructions } from "@dnd-kit/core";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

export type CatalogDndMessageKey =
  | "pickedUp"
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
  return {
    announcements: {
      onDragStart: ({ active }) => translate("pickedUp", { name: name(active.id) }),
      onDragOver: ({ active, over }) =>
        over
          ? translate("draggedOverTarget", { name: name(active.id), target: name(over.id) })
          : translate("draggedOverNoTarget", { name: name(active.id) }),
      onDragEnd: ({ active, over }) =>
        over
          ? translate("droppedOnTarget", { name: name(active.id), target: name(over.id) })
          : translate("dropped", { name: name(active.id) }),
      onDragCancel: ({ active }) => translate("dragCancelled", { name: name(active.id) }),
    },
    screenReaderInstructions: { draggable: translate("instructions") },
  };
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
