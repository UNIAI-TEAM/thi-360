/**
 * Menu `/` mở bảng mục chèn của registry (spec 4.1, ED-1), dựng bằng `@tiptap/suggestion`.
 *
 * Gõ `/` ở đầu dòng hoặc sau khoảng trắng mở danh sách; chữ gõ tiếp sau `/` là ô lọc (so với tên, mô tả và mã mục,
 * không phân biệt hoa thường và dấu). Mũi tên di chuyển, Enter chọn, Esc đóng. Chọn một mục thì xóa `/…` rồi gọi
 * `onSelect(entry)` — trình soạn mở hộp của mục đó như bấm nút trên thanh công cụ.
 *
 * Extension không tự biết ngữ cảnh trường, ngôn ngữ hay cách mở hộp: trình soạn truyền qua `configure`. Chưa cấu hình
 * thì `getEntries` rỗng và menu không bao giờ mở.
 */
import { Extension } from "@tiptap/core";
import { ReactRenderer } from "@tiptap/react";
import { Suggestion, SuggestionPluginKey } from "@tiptap/suggestion";
import type { InsertEntry } from "../insert/registry";
import { SlashMenuList, type SlashMenuListHandle, type SlashMenuListProps } from "./slash-menu-list";

export interface SlashMenuOptions {
  /** Mục hiện trong menu cho trường đang soạn (đã lọc theo `fieldContext` và `allowImages`). */
  getEntries: () => InsertEntry[];
  /** Gọi sau khi đã xóa `/…` khỏi nội dung khi người dùng chọn một mục. */
  onSelect: (entry: InsertEntry) => void;
  /** Dịch một khóa dưới `richText` (nhãn/mô tả của mục: `entry.labelKey`, `entry.descriptionKey`). */
  translate: (key: string) => string;
  /** Khóa dưới `richText` đặt tên cho danh sách (`aria-label`). */
  listLabelKey: string;
}

/** Chữ thường, bỏ dấu tiếng Việt (kể cả đ) — để "cong thuc" khớp "Công thức". */
export function foldText(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").replace(/[\u0111\u0110]/g, "d").toLowerCase();
}

/** Lọc mục theo ô lọc: mọi từ của `query` phải xuất hiện trong tên, mô tả hoặc mã mục. Giữ thứ tự registry. */
export function filterSlashEntries(entries: InsertEntry[], query: string, translate: (key: string) => string): InsertEntry[] {
  const words = foldText(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return entries;
  return entries.filter((entry) => {
    const haystack = foldText(`${translate(entry.labelKey)} ${translate(entry.descriptionKey)} ${entry.id}`);
    return words.every((word) => haystack.includes(word));
  });
}

export const SlashMenu = Extension.create<SlashMenuOptions>({
  name: "slashMenu",

  addOptions() {
    return {
      getEntries: () => [],
      onSelect: () => {},
      translate: (key) => key,
      listLabelKey: "toolbar.groupInsert",
    };
  },

  addProseMirrorPlugins() {
    const options = this.options;
    const editor = this.editor;
    return [
      Suggestion<InsertEntry, InsertEntry>({
        editor,
        pluginKey: SuggestionPluginKey,
        char: "/",
        allowSpaces: false,
        startOfLine: false,
        allowedPrefixes: [" "],
        items: ({ query }) => filterSlashEntries(options.getEntries(), query, options.translate),
        command: ({ editor: current, range, props: entry }) => {
          current.chain().focus().deleteRange(range).run();
          options.onSelect(entry);
        },
        render: () => {
          let renderer: ReactRenderer<SlashMenuListHandle, SlashMenuListProps> | null = null;
          let unmount: (() => void) | null = null;
          const listProps = (items: InsertEntry[], command: (entry: InsertEntry) => void): SlashMenuListProps => ({
            editor,
            items,
            command,
            label: options.translate(options.listLabelKey),
            translate: options.translate,
          });
          return {
            onStart: (props) => {
              renderer = new ReactRenderer(SlashMenuList, { editor, props: listProps(props.items, props.command) });
              unmount = props.mount(renderer.element);
            },
            onUpdate: (props) => renderer?.updateProps(listProps(props.items, props.command)),
            onKeyDown: ({ event }) => renderer?.ref?.onKeyDown(event) ?? false,
            onExit: () => {
              unmount?.();
              unmount = null;
              renderer?.destroy();
              renderer = null;
            },
          };
        },
      }),
    ];
  },
});
