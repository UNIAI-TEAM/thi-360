import { Node } from "@tiptap/core";
import { fillAtomView, innerHtmlAttribute, renderAtomDom } from "./atom-block";
import { TOP_LEVEL_GROUP } from "./names";
import { DATA_TABLE_NODE } from "./names";

/**
 * Bảng số liệu `<figure class="data-table">` (spec 3.2, E5): atom nguyên khối, dữ liệu trong
 * thuộc tính `html` (innerHTML đã lọc), sửa qua hộp `insert/table`. Chỉ đứng ở cấp đầu.
 */
export const DataTable = Node.create<Record<string, never>>({
  name: DATA_TABLE_NODE,
  group: TOP_LEVEL_GROUP,
  atom: true,
  selectable: true,

  addAttributes() {
    return { html: innerHtmlAttribute };
  },

  parseHTML() {
    return [{ tag: "figure.data-table" }];
  },

  renderHTML({ node }) {
    return renderAtomDom("figure", { class: "data-table" }, String(node.attrs.html ?? ""));
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("figure");
      dom.className = "data-table rich-text-atom";
      fillAtomView(dom, String(node.attrs.html ?? ""));
      return { dom };
    };
  },
});
