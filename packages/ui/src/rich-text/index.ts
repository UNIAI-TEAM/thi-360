/**
 * Bộ hiển thị chỉ đọc của nội dung câu hỏi, port từ `apps/web-admin/src/components/rich-text`
 * (chỉ phần xem, không tiptap/mathlive — plan FS-4). Mặt xuất khớp mọi tên mà mã web-admin
 * đang import từ `rich-text/{view,sanitize,math-render,html-contract}`.
 */

export { RichTextView, type RichTextViewProps } from "./rich-text-view";
export { hasUnbalancedMath, questionHtmlText, sanitizeQuestionHtml } from "./sanitize";
export type { SanitizeResult, SanitizeViolation } from "./sanitize";
export { deferUntilMhchem, renderMath } from "./math-render";
export {
  ALLOWED_TAGS,
  attributeAllowed,
  attributeViolation,
  BLANK_NUMBER_PATTERN,
  checkElementStructure,
  closedAttributeViolations,
  DROP_SUBTREE_TAGS,
  FIGURE_CLASSES,
  FIGURE_WIDTHS,
  FIGURE_ZOOMS,
  MATH_CLASSES,
  PARAGRAPH_CLASSES,
  QUOTE_KINDS,
  QUOTE_LINE_NUMBERS,
  stripTrailingEmptyParagraph,
  tagViolation,
  TD_CLASSES,
  TH_SCOPES,
  UNDERLINE_LABEL_PATTERN,
  UUID_PATTERN,
} from "./html-contract";
