import { Extension, Node } from "@tiptap/core";
import type { CommandProps } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import type { KatexOptions } from "katex";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "./names";

declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    math: {
      /** Chèn hoặc sửa công thức; `display` chọn `\[ … \]` (khối) hay `\( … \)` (cùng dòng). */
      setMath: (options: { latex: string; display: boolean }) => ReturnType;
    };
  }
}

export interface MathNodeOptions {
  /** Dựng công thức bên trong chip soạn thảo (KaTeX khi có DOM). */
  renderLatex?: (latex: string, displayMode: boolean, target: HTMLElement, options?: KatexOptions) => void;
}

const MATH_TAG = "span";

/** Thuộc tính chung của hai node công thức: nguồn LaTeX đi trong `data-latex` và nội dung chữ. */
function mathAttributes(display: boolean) {
  return {
    latex: {
      default: "",
      parseHTML: (element: HTMLElement) => stripDelimiters(element.textContent ?? "", display),
      renderHTML: () => ({}),
    },
  };
}

/** Bỏ cặp `\( … \)`/`\[ … \]` bao quanh nếu có — nội dung lưu trong span mang đủ dấu. */
function stripDelimiters(text: string, display: boolean): string {
  const trimmed = text.trim();
  const open = display ? "\\[" : "\\(";
  const close = display ? "\\]" : "\\)";
  if (trimmed.startsWith(open) && trimmed.endsWith(close)) {
    return trimmed.slice(open.length, -close.length);
  }
  return trimmed;
}

/** Công thức cùng dòng `<span class="math-inline">\( … \)</span>` — atom, sửa qua hộp thoại. */
export const MathInline = Node.create<MathNodeOptions>({
  name: MATH_INLINE_NODE,
  group: "inline",
  inline: true,
  atom: true,
  selectable: true,

  addOptions() {
    return { renderLatex: undefined };
  },

  addAttributes() {
    return mathAttributes(false);
  },

  parseHTML() {
    return [{ tag: `${MATH_TAG}.math-inline` }];
  },

  renderHTML({ node }) {
    const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
    return [MATH_TAG, { class: "math-inline" }, `\\(${latex}\\)`];
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("span");
      dom.className = "math-inline rich-text-math";
      const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
      if (this.options.renderLatex) {
        this.options.renderLatex(latex, false, dom);
      } else {
        dom.textContent = `\\(${latex}\\)`;
      }
      return { dom };
    };
  },
});

/** Công thức khối `<span class="math-block">\[ … \]</span>` — spec chỉ cho `span`, không `div`. */
export const MathBlock = Node.create<MathNodeOptions>({
  name: MATH_BLOCK_NODE,
  group: "block",
  atom: true,
  selectable: true,

  addOptions() {
    return { renderLatex: undefined };
  },

  addAttributes() {
    return mathAttributes(true);
  },

  parseHTML() {
    return [{ tag: `${MATH_TAG}.math-block`, priority: 60 }];
  },

  renderHTML({ node }) {
    const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
    return [MATH_TAG, { class: "math-block" }, `\\[${latex}\\]`];
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("span");
      dom.className = "math-block rich-text-math";
      const latex = typeof node.attrs.latex === "string" ? node.attrs.latex : "";
      if (this.options.renderLatex) {
        this.options.renderLatex(latex, true, dom);
      } else {
        dom.textContent = `\\[${latex}\\]`;
      }
      return { dom };
    };
  },
});

export const MATH_NODE_NAMES = new Set([MATH_INLINE_NODE, MATH_BLOCK_NODE]);

/**
 * Lệnh `setMath` dùng chung cho hai node; thay công thức đang chọn hoặc chèn mới tại con trỏ. Đang chọn một nút khác
 * (ví dụ ảnh) thì chèn ngay sau nút đó, không thay nó (F-05).
 */
export const MathCommands = Extension.create({
  name: "mathCommands",

  addCommands() {
    return {
      setMath:
        ({ latex, display }: { latex: string; display: boolean }) =>
        ({ commands, state }: CommandProps) => {
          const trimmed = latex.trim();
          if (!trimmed) return false;
          const content = { type: display ? MATH_BLOCK_NODE : MATH_INLINE_NODE, attrs: { latex: trimmed } };
          const { selection } = state;
          if (selection instanceof NodeSelection && !MATH_NODE_NAMES.has(selection.node.type.name)) {
            return commands.insertContentAt(selection.to, content);
          }
          return commands.insertContent(content);
        },
    };
  },
});
