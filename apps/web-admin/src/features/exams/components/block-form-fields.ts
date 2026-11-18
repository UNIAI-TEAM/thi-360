/**
 * Mỏ neo trường của biểu mẫu nhóm (FE-3a). Mỗi trường của nhóm có một điều khiển nhận focus được mang
 * `data-field="block-<blockId>-<field>"`; chỗ khác của màn (bấm lỗi ở vùng tổng hợp, kết quả kiểm tra) chọn đúng trường
 * bằng `focusBlockField(blockId, field)` hoặc bộ chọn `blockFieldSelector(blockId, field)`.
 *
 * Với trường là một nhóm điều khiển (radio, danh sách nhãn, danh sách chùm) mỏ neo nằm trên điều khiển đầu tiên của nhóm.
 */
export const BLOCK_FIELDS = [
  "name",
  "mode",
  "questionType",
  "childAnswerType",
  "setMode",
  "setCount",
  "setSize",
  "questionCount",
  "scope",
  "everyChildInScope",
  "conditions",
  "tags",
  "itemShape",
  "answerFormats",
  "points",
  "slots",
  "childConditions",
  "shuffle",
  "omitted",
] as const;

export type BlockField = (typeof BLOCK_FIELDS)[number];

export function blockFieldAnchor(blockId: string, field: BlockField): string {
  return `block-${blockId}-${field}`;
}

export function blockFieldSelector(blockId: string, field: BlockField): string {
  return `[data-field="${CSS.escape(blockFieldAnchor(blockId, field))}"]`;
}

/** Đưa focus tới trường của nhóm đang mở; trả `false` khi màn không có trường đó (ví dụ công tắc đảo ẩn). */
export function focusBlockField(blockId: string, field: BlockField, root: ParentNode = document): boolean {
  const target = root.querySelector<HTMLElement>(blockFieldSelector(blockId, field));
  if (!target) return false;
  target.scrollIntoView({ block: "center" });
  target.focus();
  return true;
}
