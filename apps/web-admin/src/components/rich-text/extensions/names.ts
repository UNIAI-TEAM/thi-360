/**
 * Tên node/mark của trình soạn nội dung (UNI-2640). Mọi node/mark đăng ký trong `extensions/`
 * dùng đúng một hằng ở đây để registry, trình soạn và test so khớp cùng một chuỗi.
 */

/** Công thức cùng dòng `<span class="math-inline">\( … \)</span>`. */
export const MATH_INLINE_NODE = "mathInline";

/** Công thức khối `<span class="math-block">\[ … \]</span>` — span ở cấp đầu nội dung. */
export const MATH_BLOCK_NODE = "mathBlock";

/** Bảng thường của TableKit (FE-4, giữ cho nội dung cũ). */
export const TABLE_NODE = "table";

/** Bảng số liệu `<figure class="data-table">` (E5). */
export const DATA_TABLE_NODE = "dataTable";

/** Hình `<figure class="image">` (E6). */
export const IMAGE_FIGURE_NODE = "imageFigure";

/** Sơ đồ lai `<figure class="cross">` (E9). */
export const CROSS_DIAGRAM_NODE = "crossDiagram";

/** Đoạn trích `<blockquote class="quote">` (E7). */
export const QUOTE_NODE = "quote";

/** Mark gạch chân đánh nhãn `<u data-label="A">` (E8) — ưu tiên cao hơn `underline`. */
export const UNDERLINE_LABEL_MARK = "underlineLabel";

/** Chỗ trống đánh số `<span class="blank" data-blank="n">` (E8). */
export const BLANK_NODE = "blank";

/**
 * Nhóm của các node atom chỉ được đứng ở cấp đầu tài liệu (spec 3.3): figure, blockquote.
 * Không dùng `block` để chúng không lọt vào ô bảng hay mục danh sách; `doc` mở rộng
 * `content` nhận thêm nhóm này.
 */
export const TOP_LEVEL_GROUP = "topLevel";

/** Tên node chỉ nằm ở cấp đầu — dùng cho kiểm tra "đang chọn" và openForSelection. */
export const TOP_LEVEL_NODE_NAMES = new Set([DATA_TABLE_NODE, IMAGE_FIGURE_NODE, CROSS_DIAGRAM_NODE, QUOTE_NODE]);
