"use client";

import { cn } from "@thi360/ui";
import type { Editor } from "@tiptap/core";
import { useEffect, useId, useImperativeHandle, useState, type Ref } from "react";
import type { InsertEntry } from "../insert/registry";

/** Tay cầm để `onKeyDown` của @tiptap/suggestion chuyển phím vào danh sách. Trả `true` khi danh sách đã xử lý phím. */
export interface SlashMenuListHandle {
  onKeyDown: (event: KeyboardEvent) => boolean;
}

export interface SlashMenuListProps {
  editor: Editor;
  items: InsertEntry[];
  /** Chọn một mục: xóa `/…` và mở hộp của mục. */
  command: (entry: InsertEntry) => void;
  /** `aria-label` của danh sách. */
  label: string;
  translate: (key: string) => string;
  ref?: Ref<SlashMenuListHandle>;
}

/**
 * Danh sách mục chèn của menu `/` (bản vẽ `SoanThaoTongQuan`): mỗi hàng một ô icon, tên và một dòng mô tả. Focus ở lại
 * vùng soạn; mục đang chọn báo qua `aria-activedescendant` của vùng soạn.
 */
export function SlashMenuList({ editor, items, command, label, translate, ref }: SlashMenuListProps) {
  const [active, setActive] = useState(0);
  const listId = useId();
  // Danh sách mới (đổi ô lọc) thì về mục đầu; `items` là mảng mới mỗi lần nên so theo mã mục.
  const itemsKey = items.map((entry) => entry.id).join("|");
  const [prevKey, setPrevKey] = useState(itemsKey);
  if (prevKey !== itemsKey) {
    setPrevKey(itemsKey);
    setActive(0);
  }
  const current = items[Math.min(active, items.length - 1)];
  const activeId = current ? `${listId}-${current.id}` : null;

  useEffect(() => {
    const dom = editor.view.dom;
    dom.setAttribute("aria-controls", listId);
    dom.setAttribute("aria-expanded", "true");
    return () => {
      dom.removeAttribute("aria-controls");
      dom.removeAttribute("aria-expanded");
      dom.removeAttribute("aria-activedescendant");
    };
  }, [editor, listId]);

  useEffect(() => {
    const dom = editor.view.dom;
    if (activeId) dom.setAttribute("aria-activedescendant", activeId);
    else dom.removeAttribute("aria-activedescendant");
    document.getElementById(activeId ?? "")?.scrollIntoView?.({ block: "nearest" });
  }, [editor, activeId]);

  useImperativeHandle(
    ref,
    () => ({
      onKeyDown(event) {
        // Không có mục khớp: để Enter/mũi tên chạy như thường (menu chỉ là gợi ý, không chặn gõ).
        if (items.length === 0) return false;
        if (event.key === "ArrowDown") {
          setActive((index) => (index + 1) % items.length);
          return true;
        }
        if (event.key === "ArrowUp") {
          setActive((index) => (index - 1 + items.length) % items.length);
          return true;
        }
        if (event.key === "Enter") {
          if (current) command(current);
          return true;
        }
        return false;
      },
    }),
    [items, current, command],
  );

  if (items.length === 0) return null;

  return (
    <div
      id={listId}
      role="listbox"
      aria-label={label}
      className="z-50 flex max-h-80 w-[min(26rem,calc(100vw-2rem))] flex-col gap-0.5 overflow-y-auto rounded-xl border border-border bg-surface p-1.5 shadow-md"
    >
      {items.map((entry, index) => {
        const Icon = entry.icon;
        const selected = entry === current;
        return (
          <div
            key={entry.id}
            id={`${listId}-${entry.id}`}
            role="option"
            aria-selected={selected}
            className={cn(
              "flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-left text-body text-text",
              selected && "bg-primary-soft",
            )}
            // mousedown giữ focus ở vùng soạn; mất focus thì Suggestion đóng menu trước khi nhận click.
            onMouseDown={(event) => event.preventDefault()}
            onMouseEnter={() => setActive(index)}
            onClick={() => command(entry)}
          >
            <span aria-hidden className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary-text">
              <Icon size={16} aria-hidden />
            </span>
            <span className="flex min-w-0 flex-col">
              <span className="font-semibold">{translate(entry.labelKey)}</span>
              <span className="text-caption text-text-muted">{translate(entry.descriptionKey)}</span>
            </span>
          </div>
        );
      })}
    </div>
  );
}
