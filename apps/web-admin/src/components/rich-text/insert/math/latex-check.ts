import katex from "katex";

/** Lý do một công thức chưa dựng được, đã rút gọn từ thông báo của KaTeX (spec 4.2). */
export type LatexErrorKind = { kind: "missingClose" } | { kind: "unsupportedCommand"; command: string } | { kind: "generic" };

export type LatexCheck =
  | { status: "empty" }
  | { status: "valid" }
  /** Còn ô giữ chỗ `\placeholder` của mathlive chưa điền: chưa phải công thức hoàn chỉnh. */
  | { status: "placeholder" }
  | { status: "error"; error: LatexErrorKind };

const PLACEHOLDER_COMMAND = /\\placeholder(?![A-Za-z])/;

/** Dịch thông báo `ParseError` của KaTeX sang ba nhóm dễ hiểu; còn lại là "chưa dựng được". */
export function describeKatexError(message: string): LatexErrorKind {
  const unsupported = /Undefined control sequence: (\\[A-Za-z]+|\\[^A-Za-z])/.exec(message);
  if (unsupported?.[1]) return { kind: "unsupportedCommand", command: unsupported[1] };
  if (/got 'EOF'|Unexpected end of input/.test(message)) return { kind: "missingClose" };
  return { kind: "generic" };
}

/**
 * Dựng thử bằng KaTeX (`throwOnError: true`) để biết công thức có lưu và hiển thị được không. Công thức trống hoặc còn
 * ô giữ chỗ không dựng thử: nút Chèn tắt trong cả hai trường hợp.
 */
export function checkLatex(latex: string, displayMode: boolean): LatexCheck {
  if (!latex.trim()) return { status: "empty" };
  if (PLACEHOLDER_COMMAND.test(latex)) return { status: "placeholder" };
  try {
    katex.renderToString(latex, { displayMode, throwOnError: true, strict: "ignore" });
    return { status: "valid" };
  } catch (error) {
    return { status: "error", error: describeKatexError(error instanceof Error ? error.message : String(error)) };
  }
}
