import type { AnyExtension } from "@tiptap/core";
import { Subscript } from "@tiptap/extension-subscript";
import { Superscript } from "@tiptap/extension-superscript";
import { TableKit } from "@tiptap/extension-table";
import { Placeholder } from "@tiptap/extensions";
import StarterKit from "@tiptap/starter-kit";
import type { KatexOptions } from "katex";
import { Blank } from "./blank";
import { CrossDiagram } from "./cross-diagram";
import { DataTable } from "./data-table";
import { RichTextDocument } from "./document";
import { HtmlSanitize } from "./html-sanitize";
import { RichTextImage } from "./image";
import { ImageFigure } from "./image-figure";
import { MathBlock, MathCommands, MathInline } from "./math";
import { Quote } from "./quote";
import { TableCellExact, TableHeaderExact } from "./table-exact";
import { UnderlineLabel } from "./underline-label";

export { Blank } from "./blank";
export { CrossDiagram } from "./cross-diagram";
export { DataTable } from "./data-table";
export { HtmlSanitize } from "./html-sanitize";
export { RichTextImage } from "./image";
export { ImageFigure } from "./image-figure";
export { MathBlock, MathCommands, MathInline, MATH_NODE_NAMES, type MathNodeOptions } from "./math";
export {
  BLANK_NODE,
  CROSS_DIAGRAM_NODE,
  DATA_TABLE_NODE,
  IMAGE_FIGURE_NODE,
  MATH_BLOCK_NODE,
  MATH_INLINE_NODE,
  QUOTE_NODE,
  TABLE_NODE,
  TOP_LEVEL_GROUP,
  TOP_LEVEL_NODE_NAMES,
  UNDERLINE_LABEL_MARK,
} from "./names";
export { Quote } from "./quote";
export { TableCellExact, TableHeaderExact } from "./table-exact";
export { UnderlineLabel } from "./underline-label";

export interface RichTextExtensionsOptions {
  /** `false` chặn dán/thả và bỏ `img` trong nội dung (trường chỉ chữ như hướng dẫn làm bài). */
  allowImages?: boolean;
  /** Nhãn của vùng soạn trống (Placeholder của @tiptap/extensions). */
  placeholder?: string;
  /** Dựng công thức trong chip soạn thảo; bỏ qua thì hiện mã LaTeX thô. */
  renderLatex?: (latex: string, displayMode: boolean, target: HTMLElement, options?: KatexOptions) => void;
  /** Số bắt đầu của chỗ trống đánh số (spec 3.4): chỗ trống n hiện `blankNumberStart + n − 1`; mặc định 1. */
  blankNumberStart?: number;
}

/**
 * Toàn bộ extension của trình soạn nội dung (schema khớp danh sách cho phép của spec 4.2/3.x).
 * `rich-text-editor.tsx` dùng danh sách này; lane khác thêm extension bằng `Extension.create`
 * rồi nối vào mảng trả về.
 */
export function createRichTextExtensions(options: RichTextExtensionsOptions = {}): AnyExtension[] {
  const { allowImages = true, placeholder, renderLatex, blankNumberStart = 1 } = options;
  return [
    StarterKit.configure({
      // Document tùy biến nhận nhóm topLevel; schema chỉ gồm các thẻ của danh sách cho phép.
      document: false,
      blockquote: false,
      code: false,
      codeBlock: false,
      heading: false,
      horizontalRule: false,
      link: false,
    }),
    RichTextDocument,
    Placeholder.configure({ placeholder: () => placeholder ?? "" }),
    Subscript,
    Superscript,
    // TableKit giữ bảng/ hàng; ô thay bằng bản xuất đủ colspan/rowspan để khớp nội dung cũ.
    TableKit.configure({ table: { resizable: false }, tableCell: false, tableHeader: false }),
    TableCellExact,
    TableHeaderExact,
    RichTextImage.configure({ allowBase64: false }),
    MathInline.configure({ renderLatex }),
    MathBlock.configure({ renderLatex }),
    MathCommands,
    DataTable,
    ImageFigure,
    CrossDiagram,
    Quote,
    UnderlineLabel,
    Blank.configure({ blankNumberStart }),
    HtmlSanitize.configure({ allowImages }),
  ];
}
