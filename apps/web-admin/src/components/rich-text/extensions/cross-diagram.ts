import { Node } from "@tiptap/core";
import { fillAtomView, innerHtmlAttribute, renderAtomDom } from "./atom-block";
import { TOP_LEVEL_GROUP } from "./names";
import { CROSS_DIAGRAM_NODE } from "./names";

/**
 * Sơ đồ lai `<figure class="cross">` (spec 3.2, E9): atom, bảng thế hệ trong thuộc tính
 * `html`, sửa qua hộp `insert/cross`. Chỉ đứng ở cấp đầu.
 */
export const CrossDiagram = Node.create<Record<string, never>>({
  name: CROSS_DIAGRAM_NODE,
  group: TOP_LEVEL_GROUP,
  atom: true,
  selectable: true,

  addAttributes() {
    return { html: innerHtmlAttribute };
  },

  parseHTML() {
    return [{ tag: "figure.cross" }];
  },

  renderHTML({ node }) {
    return renderAtomDom("figure", { class: "cross" }, String(node.attrs.html ?? ""));
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("figure");
      dom.className = "cross rich-text-atom";
      fillAtomView(dom, String(node.attrs.html ?? ""));
      return { dom };
    };
  },
});
