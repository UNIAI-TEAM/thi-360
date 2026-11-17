/**
 * Decoration đánh dấu "chỗ tự sửa chưa xác nhận" của Dán từ Word (spec 4.10) trong mọi ô soạn của trang.
 *
 * Cầu nối với biểu mẫu là một kho nhỏ ở mức module: sau "Đưa vào biểu mẫu", nơi gọi đưa LaTeX của các chỗ còn chờ vào
 * `markWordPending`; plugin tô mọi node công thức có đúng LaTeX đó. Đánh dấu chỉ là decoration của ProseMirror:
 * không vào HTML (`getHTML` và `onChange` không thấy gì), mất khi tải lại trang, và không chặn lưu. Người soạn sửa công
 * thức thì LaTeX đổi nên dấu tự biến mất.
 */
import { Extension } from "@tiptap/core";
import type { Node as PMNode } from "@tiptap/pm/model";
import { Plugin, PluginKey } from "@tiptap/pm/state";
import { Decoration, DecorationSet } from "@tiptap/pm/view";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../../extensions/names";

const pendingLatex = new Set<string>();
let markLabel = "";
const listeners = new Set<() => void>();

function notify() {
  for (const listener of [...listeners]) listener();
}

/** Thêm các công thức chờ xác nhận; `label` là chữ giải thích (tooltip) đã dịch. */
export function markWordPending(latexes: readonly string[], label: string) {
  for (const latex of latexes) pendingLatex.add(latex.trim());
  markLabel = label;
  notify();
}

/** Bỏ mọi dấu (khi rời biểu mẫu). */
export function clearWordPending() {
  if (pendingLatex.size === 0) return;
  pendingLatex.clear();
  notify();
}

const key = new PluginKey<DecorationSet>("wordPendingMarks");

function decorate(doc: PMNode): DecorationSet {
  if (pendingLatex.size === 0) return DecorationSet.empty;
  const decorations: Decoration[] = [];
  doc.descendants((node, pos) => {
    if (node.type.name !== MATH_INLINE_NODE && node.type.name !== MATH_BLOCK_NODE) return;
    const latex = typeof node.attrs.latex === "string" ? node.attrs.latex.trim() : "";
    if (!pendingLatex.has(latex)) return;
    decorations.push(
      Decoration.node(pos, pos + node.nodeSize, {
        // Cờ `!` (important): `.rich-text-editor-content .rich-text-math` trong rich-text.css đặt nền và viền chip công
        // thức bằng hai lớp, thắng tiện ích một lớp nên dấu chờ xác nhận sẽ vô hình nếu không ép.
        class:
          "rounded-sm bg-warning-bg! outline-2! outline-solid! outline-warning! [&.ProseMirror-selectednode]:outline-primary-text!",
        "data-word-pending": "true",
        title: markLabel,
      }),
    );
  });
  return DecorationSet.create(doc, decorations);
}

/** Extension TipTap gắn qua `editorPlugins` của mục `word` ở registry. */
export const WordPendingMarks = Extension.create({
  name: "wordPendingMarks",
  addProseMirrorPlugins() {
    return [
      new Plugin<DecorationSet>({
        key,
        state: {
          init: (_config, state) => decorate(state.doc),
          apply: (tr, previous, _old, state) => (tr.docChanged || tr.getMeta(key) ? decorate(state.doc) : previous),
        },
        props: {
          decorations: (state) => key.getState(state),
        },
        view: (view) => {
          const refresh = () => {
            if (!view.isDestroyed) view.dispatch(view.state.tr.setMeta(key, true));
          };
          listeners.add(refresh);
          return { destroy: () => void listeners.delete(refresh) };
        },
      }),
    ];
  },
});
