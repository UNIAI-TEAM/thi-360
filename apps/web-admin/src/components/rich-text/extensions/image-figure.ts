import { Node } from "@tiptap/core";
import { fillAtomView, innerHtmlAttribute, renderAtomDom } from "./atom-block";
import { TOP_LEVEL_GROUP } from "./names";
import { IMAGE_FIGURE_NODE } from "./names";

/**
 * Hình `<figure class="image" data-width="…" data-zoom="…">` (spec 3.2, E6): atom, `img` +
 * `figcaption` + `p.figure-source` nằm trong thuộc tính `html`. Chỉ đứng ở cấp đầu.
 */
export const ImageFigure = Node.create<Record<string, never>>({
  name: IMAGE_FIGURE_NODE,
  group: TOP_LEVEL_GROUP,
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      width: {
        default: "medium",
        parseHTML: (element) => element.getAttribute("data-width"),
      },
      zoom: {
        default: "true",
        parseHTML: (element) => element.getAttribute("data-zoom"),
      },
      html: innerHtmlAttribute,
    };
  },

  parseHTML() {
    return [{ tag: "figure.image" }];
  },

  renderHTML({ node }) {
    return renderAtomDom(
      "figure",
      {
        class: "image",
        "data-width": node.attrs.width ?? undefined,
        "data-zoom": node.attrs.zoom ?? undefined,
      },
      String(node.attrs.html ?? ""),
    );
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("figure");
      dom.className = "image rich-text-atom";
      if (node.attrs.width) dom.setAttribute("data-width", String(node.attrs.width));
      if (node.attrs.zoom) dom.setAttribute("data-zoom", String(node.attrs.zoom));
      fillAtomView(dom, String(node.attrs.html ?? ""));
      return { dom };
    };
  },
});
