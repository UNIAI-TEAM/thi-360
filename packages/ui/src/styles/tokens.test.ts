import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = readFileSync("src/styles/tokens.css", "utf8");

/** Trích `--name: value` trong một khối CSS (khối đầu tiên khớp selector). */
function blockVars(selector: string): Map<string, string> {
  const start = css.indexOf(selector);
  expect(start, `không thấy khối ${selector}`).toBeGreaterThanOrEqual(0);
  const open = css.indexOf("{", start);
  const close = css.indexOf("}", open);
  const body = css.slice(open + 1, close);
  const vars = new Map<string, string>();
  for (const m of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    vars.set(m[1]!, m[2]!.trim());
  }
  return vars;
}

const light = blockVars("@theme static");
const dark = blockVars('[data-theme="dark"]');

/** Giá trị cuối cùng của biến: khối dark ghi đè khối light. */
function token(name: string, theme: "light" | "dark"): string {
  const vars = theme === "dark" ? dark : light;
  const value = vars.get(name) ?? light.get(name);
  expect(value, `thiếu token ${name}`).toBeDefined();
  return value!;
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255) as [number, number, number];
}

function luminance(hex: string): number {
  const [r = 0, g = 0, b = 0] = hexToRgb(hex).map((c) =>
    c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4,
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Tỉ lệ tương phản WCAG 2.x giữa hai màu hex. */
function contrast(fg: string, bg: string): number {
  const [a = 0, b = 0] = [luminance(fg), luminance(bg)].sort((x, y) => y - x);
  return (a + 0.05) / (b + 0.05);
}

/**
 * Các cặp chữ/nền mà thành phần thực sự dùng trong chế độ tối (đọc từ class trong
 * packages/ui và apps/web-admin). Mỗi cặp phải đạt >= 4.5:1 (WCAG AA, DESIGN.md mục 8/9).
 * Nút chính trắng trên `primary-600` (#059669) được giữ nguyên theo lựa chọn thương hiệu
 * (DESIGN 8) nên không nằm trong danh sách này.
 */
const DARK_TEXT_PAIRS: Array<{ fg: string; bg: string; note: string }> = [
  // Chữ thương hiệu / tiêu đề trên nền surface
  { fg: "--color-primary-900", bg: "--color-surface", note: "wordmark sidebar" },
  { fg: "--color-primary-700", bg: "--color-surface", note: "icon/initials" },
  { fg: "--color-primary-800", bg: "--color-surface", note: "nút outline" },
  // primary-600 (#059669) làm màu chữ trên các nền
  { fg: "--color-primary-600", bg: "--color-page", note: "link trên nền trang" },
  { fg: "--color-primary-600", bg: "--color-surface", note: "link/tab trên card" },
  { fg: "--color-primary-600", bg: "--color-primary-50", note: "chip tag, tab/mục/nav đang chọn" },
  { fg: "--color-primary-600", bg: "--color-primary-100", note: "nút soft" },
  { fg: "--color-primary-600", bg: "--color-primary-200", note: "nút soft hover" },
  // primary-700/800 trên nền primary-50 (icon box, avatar, chip tag)
  { fg: "--color-primary-700", bg: "--color-primary-50", note: "icon/avatar trên primary-50" },
  { fg: "--color-primary-800", bg: "--color-primary-50", note: "chip tag" },
  // primary-600 trên primary-50 (empty-state icon, pagination hiện tại)
  { fg: "--color-primary-600", bg: "--color-primary-50", note: "empty-state/pagination" },
  // Nút outline hover: primary-800 trên primary-50
  { fg: "--color-primary-800", bg: "--color-primary-50", note: "outline hover" },
  // Trạng thái: chữ trên nền trạng thái
  { fg: "--color-success", bg: "--color-success-bg", note: "StatusChip success" },
  { fg: "--color-warning", bg: "--color-warning-bg", note: "StatusChip warning" },
  { fg: "--color-danger", bg: "--color-danger-bg", note: "StatusChip danger, eyebrow lỗi" },
  { fg: "--color-info", bg: "--color-info-bg", note: "StatusChip info" },
  { fg: "--color-neutral", bg: "--color-neutral-bg", note: "StatusChip neutral, tab thường" },
  // Trung tính
  { fg: "--color-text", bg: "--color-surface", note: "chữ chính" },
  { fg: "--color-text-secondary", bg: "--color-surface", note: "chữ phụ, menu" },
  { fg: "--color-text-muted", bg: "--color-surface", note: "mô tả" },
];

describe("tokens.css chế độ tối", () => {
  it("mọi cặp chữ/nền thành phần dùng đều đạt >= 4.5:1", () => {
    const failures: string[] = [];
    const rows: string[] = [];
    for (const { fg, bg, note } of DARK_TEXT_PAIRS) {
      const ratio = contrast(token(fg, "dark"), token(bg, "dark"));
      rows.push(`${note}: ${token(fg, "dark")} trên ${token(bg, "dark")} = ${ratio.toFixed(2)}:1`);
      if (ratio < 4.5) failures.push(`${note} chỉ đạt ${ratio.toFixed(2)}:1`);
    }
    expect(failures, `\n${rows.join("\n")}\n`).toEqual([]);
  });
});
