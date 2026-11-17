import { Node } from "@tiptap/core";
import { innerHtmlAttribute, renderAtomDom } from "./atom-block";
import { TOP_LEVEL_GROUP } from "./names";
import { QUOTE_NODE } from "./names";

/**
 * Đoạn trích `<blockquote class="quote" data-kind="…" data-line-numbers="…">` (spec 3.2, E7):
 * atom, khổ thơ/đoạn văn + `footer` + `p.figure-source` trong thuộc tính `html`.
 * Chỉ đứng ở cấp đầu.
 */
export const Quote = Node.create<Record<string, never>>({
  name: QUOTE_NODE,
  group: TOP_LEVEL_GROUP,
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      kind: {
        default: "prose",
        parseHTML: (element) => element.getAttribute("data-kind"),
      },
      lineNumbers: {
        default: "0",
        parseHTML: (element) => element.getAttribute("data-line-numbers"),
      },
      html: innerHtmlAttribute,
    };
  },

  parseHTML() {
    return [{ tag: "blockquote.quote" }];
  },

  renderHTML({ node }) {
    return renderAtomDom(
      "blockquote",
      {
        class: "quote",
        "data-kind": node.attrs.kind ?? undefined,
        "data-line-numbers": node.attrs.lineNumbers ?? undefined,
      },
      String(node.attrs.html ?? ""),
    );
  },

  addNodeView() {
    return ({ node }) => {
      const dom = document.createElement("blockquote");
      dom.className = "quote rich-text-atom";
      if (node.attrs.kind) dom.setAttribute("data-kind", String(node.attrs.kind));
      if (node.attrs.lineNumbers) dom.setAttribute("data-line-numbers", String(node.attrs.lineNumbers));
      dom.innerHTML = String(node.attrs.html ?? "");
      return { dom };
    };
  },
});
