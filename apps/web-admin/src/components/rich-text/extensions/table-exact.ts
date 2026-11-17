import { mergeAttributes } from "@tiptap/core";
import { TableCell, TableHeader } from "@tiptap/extension-table";

/**
 * Ô bảng của TableKit bỏ `colspan`/`rowspan` khi bằng 1, nhưng nội dung bảng thường của FE-4
 * ghi đủ cả hai thuộc tính. Bản này xuất luôn hai thuộc tính để nội dung cũ qua trình soạn
 * vẫn ra đúng chuỗi (mẫu `legacy-plain-table`); giá trị 1 vẫn hợp lệ theo bộ lọc.
 */
export const TableCellExact = TableCell.extend({
  renderHTML({ HTMLAttributes }) {
    return ["td", mergeAttributes(this.options.HTMLAttributes, HTMLAttributes), 0];
  },
});

export const TableHeaderExact = TableHeader.extend({
  renderHTML({ HTMLAttributes }) {
    return ["th", mergeAttributes(this.options.HTMLAttributes, HTMLAttributes), 0];
  },
});
