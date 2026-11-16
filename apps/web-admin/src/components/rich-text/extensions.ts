import { Extension, Node } from "@tiptap/core";
import type { CommandProps } from "@tiptap/core";
import type { KatexOptions } from "katex";
import { sanitizeQuestionHtml } from "./sanitize";

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
  name: "mathInline",
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
  name: "mathBlock",
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

/** Lệnh `setMath` dùng chung cho hai node; thay chip đang chọn hoặc chèn mới tại con trỏ. */
export const MathCommands = Extension.create({
  name: "mathCommands",

  addCommands() {
    return {
      setMath:
        ({ latex, display }: { latex: string; display: boolean }) =>
        ({ commands }: CommandProps) => {
          const trimmed = latex.trim();
          if (!trimmed) return false;
          return commands.insertContent({ type: display ? "mathBlock" : "mathInline", attrs: { latex: trimmed } });
        },
    };
  },
});

/**
 * Lọc HTML dán/thả vào soạn theo đúng danh sách cho phép của backend trước khi ProseMirror parse.
 * `transformPastedHTML` của TipTap cũng chạy cho HTML được thả (drop) — một điểm lọc duy nhất.
 */
export const HtmlSanitize = Extension.create<{ allowImages: boolean }>({
  name: "htmlSanitize",

  addOptions() {
    return { allowImages: true };
  },

  transformPastedHTML(html: string) {
    return sanitizeQuestionHtml(html, { allowImages: this.options.allowImages }).html;
  },
});
