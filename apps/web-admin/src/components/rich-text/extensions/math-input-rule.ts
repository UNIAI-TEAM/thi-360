/**
 * Input rule `$…$` → công thức cùng dòng, `$$…$$` → công thức khối, `\$` → ký tự `$` (spec 4.1, ED-1).
 *
 * Luật chạy lúc gõ dấu `$` đóng. Dấu `$` mở phải dính liền chữ đầu công thức và dấu đóng dính liền chữ cuối
 * (không khoảng trắng ở hai mép), nên số tiền kiểu "5$" (không có dấu mở) hay "$5" (chưa có dấu đóng) giữ nguyên.
 * Bấm Backspace ngay sau khi đổi trả lại đúng chữ đã gõ (hành vi mặc định của input rule TipTap).
 */
import { Extension, InputRule } from "@tiptap/core";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "./names";

/** `\$` vừa gõ xong (`$` là ký tự đang gõ, chưa vào tài liệu): bỏ `\`, giữ `$`. */
const ESCAPED_DOLLAR = /\\\$$/;

/** `$$…$$` (không có `$` bên trong). Nhóm 1 là ký tự đứng trước dấu mở (không phải `$` hay `\`), nhóm 2 là LaTeX. */
const BLOCK_MATH = /(^|[^$\\])\$\$([^$]*[^$\s][^$]*)\$\$$/;

/**
 * `$…$`: nhóm 1 là ký tự đứng trước dấu mở (không phải `$`, `\`), nhóm 2 là LaTeX, không có `$` bên trong và không
 * bắt đầu hay kết thúc bằng khoảng trắng.
 */
const INLINE_MATH = /(^|[^$\\])\$([^$\s](?:[^$]*[^$\s])?)\$$/;

export const MathInputRule = Extension.create({
  name: "mathInputRule",

  addInputRules() {
    return [
      new InputRule({
        find: ESCAPED_DOLLAR,
        handler: ({ state, range }) => {
          state.tr.insertText("$", range.from, range.to);
        },
      }),
      new InputRule({
        find: BLOCK_MATH,
        handler: ({ state, range, match, chain }) => {
          const latex = (match[2] ?? "").trim();
          const from = range.from + (match[1]?.length ?? 0);
          if (!latex || !state.schema.nodes[MATH_BLOCK_NODE]) return null;
          chain()
            .deleteRange({ from, to: range.to })
            .insertContentAt(from, { type: MATH_BLOCK_NODE, attrs: { latex } })
            .run();
          return undefined;
        },
      }),
      new InputRule({
        find: INLINE_MATH,
        handler: ({ state, range, match }) => {
          const latex = match[2] ?? "";
          const type = state.schema.nodes[MATH_INLINE_NODE];
          if (!type) return null;
          const from = range.from + (match[1]?.length ?? 0);
          state.tr.replaceWith(from, range.to, type.create({ latex }));
          return undefined;
        },
      }),
    ];
  },
});
