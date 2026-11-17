import { Mark, mergeAttributes } from "@tiptap/core";
import { UNDERLINE_LABEL_MARK } from "./names";

/**
 * Mark gạch chân đánh nhãn `<u data-label="A">` (spec 3.2, E8): độ ưu tiên phân tích cao hơn
 * `underline` (priority 200 trên mark và rule) và loại trừ nó — `<u data-label>` thành nhãn,
 * `<u>` thường vẫn là gạch chân thường.
 */
export const UnderlineLabel = Mark.create({
  name: UNDERLINE_LABEL_MARK,
  priority: 200,
  excludes: "underline",

  addAttributes() {
    return {
      label: {
        default: "A",
        parseHTML: (element) => element.getAttribute("data-label"),
        renderHTML: (attributes) => (attributes.label ? { "data-label": String(attributes.label) } : {}),
      },
    };
  },

  parseHTML() {
    return [{ tag: "u[data-label]", priority: 200 }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["u", mergeAttributes(HTMLAttributes), 0];
  },
});
