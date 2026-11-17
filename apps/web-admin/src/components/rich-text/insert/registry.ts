/**
 * Danh sách mục chèn của trình soạn (plan B1, spec 4.1). Thanh công cụ và menu `/` lọc mục theo
 * `showIn(fieldContext)`; mỗi mục khai báo thêm khung "đang chọn" (`selectionPanel`), cách mở lại
 * hộp (`openForSelection`) và plugin soạn thảo riêng (`editorPlugins`) nếu có.
 *
 * Hợp đồng cho lane sóng 2: mỗi lane sửa `insert/<id>/` của mình (thay `dialog.tsx` tạm bằng hộp
 * thật, có thể thêm `selectionPanel`/`editorPlugins` ở đây — sửa registry thì hỏi Advisor).
 */
import type { Editor, Extension } from "@tiptap/core";
import { Mark as PMMark, Node as PMNode } from "@tiptap/pm/model";
import type { LucideIcon } from "lucide-react";
import {
  ClipboardPaste,
  FlaskConical,
  Image,
  LetterText,
  Omega,
  Quote,
  Sigma,
  SquareDashed,
  Table,
  TextCursorInput,
} from "lucide-react";
import type { ComponentType } from "react";
import {
  BLANK_NODE,
  CROSS_DIAGRAM_NODE,
  DATA_TABLE_NODE,
  IMAGE_FIGURE_NODE,
  MATH_BLOCK_NODE,
  MATH_INLINE_NODE,
  QUOTE_NODE,
  UNDERLINE_LABEL_MARK,
} from "../extensions";
import MathSelectionPanel from "./math/selection-panel";
import { WordPendingMarks } from "./word/pending-marks";

/** Ngữ cảnh của trường đang soạn (spec 3.3/4.1): quyết định mục chèn nào hiện. */
export type FieldContext = "stem-single-choice" | "set-passage" | "instructions" | "default";

/** Thuộc tính mà mọi hộp thoại chèn nhận: editor để chèn/sửa và cờ mở của `Dialog`. */
export interface InsertDialogProps {
  editor: Editor;
  /** Trạng thái mở của hộp — truyền thẳng vào `InsertDialogShell`/`Dialog`. */
  open: boolean;
  /** `onOpenChange` của `Dialog`; hộp gọi `onOpenChange(false)` sau khi chèn hoặc hủy nội bộ. */
  onOpenChange: (open: boolean) => void;
  /** Dữ liệu khởi tạo do `openForSelection` trả về khi mở lại hộp của node/mark đang chọn; `null` khi chèn mới. */
  editing: unknown;
}

/** Module mà `load()` trả về — `insert/<id>/dialog.tsx` export mặc định component hộp thoại. */
export interface InsertDialogModule {
  default: ComponentType<InsertDialogProps>;
}
/** Thuộc tính khung "đang chọn" dưới thanh công cụ (spec 4.1). */
export interface SelectionPanelProps {
  editor: Editor;
  /** Node hoặc mark đang khớp `openForSelection` của mục. */
  target: PMNode | PMMark;
  /** Mở lại hộp chèn/sửa của mục (giống bấm nút trên thanh công cụ). */
  openDialog: () => void;
}

export interface InsertEntry {
  /** Định danh ổn định, trùng tên thư mục `insert/<id>/` và khóa i18n `richText.insert.<id>`. */
  id: string;
  /** Khóa i18n của nhãn và mô tả (`richText.insert.<id>.label|.description`). */
  labelKey: string;
  descriptionKey: string;
  icon: LucideIcon;
  /** Mục có hiện trên nhóm Chèn của thanh công cụ (và menu `/`) ở ngữ cảnh này không. */
  showIn: (fieldContext: FieldContext) => boolean;
  /** Tải lười module hộp thoại: `() => import("./<id>/dialog")`. */
  load: () => Promise<InsertDialogModule>;
  /**
   * Node/mark đang chọn có mở lại hộp của mục này không. Trả `null` khi không khớp; khớp thì trả
   * dữ liệu khởi tạo cho hộp (truyền qua `InsertDialogProps.editing`), `true` khi chỉ cần báo khớp.
   */
  openForSelection: (target: PMNode | PMMark) => unknown;
  /** Khung "đang chọn" của loại node/mark này; hiện dưới thanh công cụ khi khớp. */
  selectionPanel?: ComponentType<SelectionPanelProps>;
  /** Extension ProseMirror/TipTap riêng của mục (hàm để mỗi editor một bản), ví dụ decoration của ED-5. */
  editorPlugins?: () => Extension[];
}

/** Nội dung LaTeX của một node công thức (`data-latex` ở attrs). */
function mathLatex(node: PMNode): string {
  return typeof node.attrs.latex === "string" ? node.attrs.latex : "";
}

function isChemistry(node: PMNode): boolean {
  const latex = mathLatex(node);
  return latex.startsWith("\\ce{") || latex.startsWith("\\ce ");
}

function isMath(node: PMNode): boolean {
  return node.type.name === MATH_INLINE_NODE || node.type.name === MATH_BLOCK_NODE;
}

function nodeIs(name: string) {
  return (target: PMNode | PMMark) => target instanceof PMNode && target.type.name === name;
}

/**
 * Thứ tự quyết định trên thanh công cụ và menu `/`. `word` không có nút (`showIn` = false): nó chỉ
 * mang `editorPlugins` (decoration của ED-5) và là điểm nêu tính năng dán từ Word — nút "Dán từ
 * Word" nằm ở đầu form soạn câu (spec 4.10), không phải trên thanh công cụ của mọi ô soạn.
 * `cross` cũng không có nút trên thanh công cụ: sơ đồ lai chỉ có trong menu `/` (spec 4.1) nên
 * thanh công cụ lọc bỏ nó và `word` qua `TOOLBAR_HIDDEN`; `showIn` của nó vẫn báo hiện ở mọi ngữ
 * cảnh cho menu `/`.
 */
export const INSERT_REGISTRY: InsertEntry[] = [
  {
    id: "math",
    labelKey: "insert.math.label",
    descriptionKey: "insert.math.description",
    icon: Sigma,
    showIn: () => true,
    load: () => import("./math/dialog"),
    // Công thức `\ce{…}` mở hộp Hóa học thay hộp Toán (spec 4.1 khung đang chọn, 4.3 mở lại).
    openForSelection: (target) =>
      target instanceof PMNode && isMath(target) && !isChemistry(target)
        ? { latex: mathLatex(target), display: target.type.name === MATH_BLOCK_NODE }
        : null,
    selectionPanel: MathSelectionPanel,
  },
  {
    id: "chemistry",
    labelKey: "insert.chemistry.label",
    descriptionKey: "insert.chemistry.description",
    icon: FlaskConical,
    showIn: () => true,
    load: () => import("./chemistry/dialog"),
    openForSelection: (target) => (target instanceof PMNode && isMath(target) && isChemistry(target) ? true : null),
  },
  {
    id: "symbols",
    labelKey: "insert.symbols.label",
    descriptionKey: "insert.symbols.description",
    icon: Omega,
    showIn: () => true,
    load: () => import("./symbols/dialog"),
    // Ký hiệu chèn là chữ Unicode thường: không có node/mark riêng để mở lại.
    openForSelection: () => null,
  },
  {
    id: "table",
    labelKey: "insert.table.label",
    descriptionKey: "insert.table.description",
    icon: Table,
    showIn: () => true,
    load: () => import("./table/dialog"),
    openForSelection: nodeIs(DATA_TABLE_NODE),
  },
  {
    id: "image",
    labelKey: "insert.image.label",
    descriptionKey: "insert.image.description",
    icon: Image,
    // Ô chỉ chữ (Hướng dẫn làm bài, `allowImages=false`) không có mục Hình (spec 4.6).
    showIn: (fieldContext) => fieldContext !== "instructions",
    load: () => import("./image/dialog"),
    openForSelection: nodeIs(IMAGE_FIGURE_NODE),
  },
  {
    id: "quote",
    labelKey: "insert.quote.label",
    descriptionKey: "insert.quote.description",
    icon: Quote,
    showIn: () => true,
    load: () => import("./quote/dialog"),
    openForSelection: nodeIs(QUOTE_NODE),
  },
  {
    id: "underline-label",
    labelKey: "insert.underline-label.label",
    descriptionKey: "insert.underline-label.description",
    icon: LetterText,
    // Chỉ đề bài câu Trắc nghiệm 1 đáp án (kể cả câu con dạng này) — spec 3.3, 4.8.
    showIn: (fieldContext) => fieldContext === "stem-single-choice",
    load: () => import("./underline-label/dialog"),
    openForSelection: (target) => (target instanceof PMMark && target.type.name === UNDERLINE_LABEL_MARK ? true : null),
  },
  {
    id: "blank",
    labelKey: "insert.blank.label",
    descriptionKey: "insert.blank.description",
    icon: SquareDashed,
    // Chỉ ngữ liệu câu chùm — spec 3.3, 4.8.
    showIn: (fieldContext) => fieldContext === "set-passage",
    load: () => import("./blank/dialog"),
    openForSelection: nodeIs(BLANK_NODE),
  },
  {
    id: "cross",
    labelKey: "insert.cross.label",
    descriptionKey: "insert.cross.description",
    icon: TextCursorInput,
    showIn: () => true,
    load: () => import("./cross/dialog"),
    openForSelection: nodeIs(CROSS_DIAGRAM_NODE),
  },
  {
    id: "word",
    labelKey: "insert.word.label",
    descriptionKey: "insert.word.description",
    icon: ClipboardPaste,
    showIn: () => false,
    load: () => import("./word/dialog"),
    openForSelection: () => null,
    // Decoration "chỗ tự sửa chưa xác nhận" của Dán từ Word (ED-5): không vào HTML, xem word/pending-marks.ts.
    editorPlugins: () => [WordPendingMarks.configure()],
  },
];

/** Mục không có nút trên thanh công cụ: `cross` chỉ ở menu `/` (spec 4.1), `word` ở nút của form. */
const TOOLBAR_HIDDEN: Record<string, true> = { cross: true, word: true };

/** Mục của nhóm Chèn trên thanh công cụ cho một `fieldContext`. */
export function toolbarInsertEntries(fieldContext: FieldContext): InsertEntry[] {
  return INSERT_REGISTRY.filter((entry) => !TOOLBAR_HIDDEN[entry.id] && entry.showIn(fieldContext));
}

/** Mục cho menu `/`: bỏ `word` (không phải thao tác chèn thủ công), giữ `cross`. */
export function slashMenuEntries(fieldContext: FieldContext): InsertEntry[] {
  return INSERT_REGISTRY.filter((entry) => entry.id !== "word" && entry.showIn(fieldContext));
}

/** Extension `editorPlugins` gộp từ mọi mục khai báo. */
export function registryEditorPlugins(): Extension[] {
  return INSERT_REGISTRY.flatMap((entry) => entry.editorPlugins?.() ?? []);
}
