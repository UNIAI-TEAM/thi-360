/**
 * Kiểu dữ liệu của luồng "Dán từ Word" (spec 4.10): HTML clipboard → `ParsedWord` (HTML nội bộ sạch, công thức đã
 * đổi, ảnh) → bộ tự sửa → bộ tách trường → HTML hợp đồng điền vào biểu mẫu.
 */

/** Công thức mà bộ đổi OMML → LaTeX đã đọc được (LaTeX thô, chưa qua bộ tự sửa). */
export interface WordFormula {
  kind: "formula";
  id: string;
  /** LaTeX thô, chưa bọc `\( … \)`. */
  latex: string;
  /** `m:oMathPara`: công thức đứng riêng một dòng. */
  display: boolean;
}

/**
 * Công thức không có nội dung đọc được ("Cần gõ lại"): ảnh (MathType, ảnh chụp; HTML clipboard chỉ trỏ tới tệp cục bộ)
 * hoặc OMML có phần tử ngoài tập con.
 */
export interface WordRetype {
  kind: "retype";
  id: string;
  /** `image`: công thức dạng ảnh; `unsupported`: phần tử OMML ngoài tập con; `malformed`: XML hỏng. */
  cause: "image" | "unsupported" | "malformed";
  /** Tên phần tử OMML ngoài tập con (`m:sPre`…), để báo cho người soạn. */
  detail?: string;
  /** Chữ quanh chỗ này trong đoạn Word, giúp nhận ra công thức nào. */
  context: string;
}

/** Ảnh thường (không phải công thức) trong bản dán: không đọc được từ clipboard nên chỉ liệt kê. */
export interface WordImage {
  id: string;
  alt: string;
  /** Chữ của đoạn chứa ảnh. */
  context: string;
}

export type WordFormulaItem = WordFormula | WordRetype;

export interface ParsedWord {
  /**
   * HTML nội bộ đã làm sạch (chỉ thẻ trong danh sách cho phép) nhưng công thức là chỗ giữ `<span data-wf="id">` (đổi
   * được) hoặc `<span data-wr="id">` (cần gõ lại). Không phải HTML hợp đồng: chưa qua `materialize`.
   */
  html: string;
  formulas: WordFormulaItem[];
  images: WordImage[];
}

/** Tên các nhãn phương án mà bộ tách nhận. */
export const OPTION_LABELS = ["A", "B", "C", "D"] as const;
export type OptionLabel = (typeof OPTION_LABELS)[number];

/** Nội dung đã tách thành các ô của biểu mẫu (mỗi ô là danh sách khối nội bộ, chưa qua `materialize`). */
export interface SplitFields {
  stem: Element[];
  /** Phương án theo thứ tự A, B, C, D (chỉ những nhãn tìm thấy). */
  options: { label: OptionLabel; blocks: Element[] }[];
  solution: Element[];
  /** Có tìm thấy ít nhất một nhãn A./B./C./D. hoặc "Lời giải". */
  found: boolean;
}
