import { Node, mergeAttributes } from "@tiptap/core";
import { BLANK_NODE } from "./names";

/**
 * Chỗ trống đánh số `<span class="blank" data-blank="n">` (spec 3.2, E8): inline atom,
 * `data-blank` là thứ tự câu con (1–20); số hiển thị khi dựng = `blankNumberStart` + n − 1.
 */
export const Blank = Node.create<Record<string, never>>({
  name: BLANK_NODE,
  group: "inline",
  inline: true,
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      blank: {
        default: 1,
        parseHTML: (element) => {
          const value = Number.parseInt(element.getAttribute("data-blank") ?? "", 10);
          return Number.isInteger(value) ? value : null;
        },
        renderHTML: (attributes) => ({ "data-blank": String(attributes.blank) }),
      },
    };
  },

  parseHTML() {
    return [{ tag: "span.blank" }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["span", mergeAttributes({ class: "blank" }, HTMLAttributes)];
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("span");
      dom.className = "blank rich-text-blank";
      dom.textContent = `(${node.attrs.blank}) ____`;
      return { dom };
    };
  },
});
