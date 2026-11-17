import { Node } from "@tiptap/core";
import { TOP_LEVEL_GROUP } from "./names";

/**
 * Nút gốc của tài liệu: nhận cả `block` lẫn nhóm `topLevel` (figure, blockquote của spec 3.3)
 * ở cấp đầu, để các khối atom không lọt vào ô bảng hay mục danh sách.
 */
export const RichTextDocument = Node.create<Record<string, never>>({
  name: "doc",
  topNode: true,
  content: `(block | ${TOP_LEVEL_GROUP})+`,
});
