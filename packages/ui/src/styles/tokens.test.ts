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

type Pair = { fg: string; bg: string; note: string };

/**
 * Cặp chữ/nền của chế độ sáng mà thành phần thực sự dùng (đọc từ class trong packages/ui và
 * apps/web-admin). T06b (UNI-2559): trước đó chữ primary-600 trên nền nhạt chỉ đạt 3,4–3,6:1,
 * trắng trên nút chính 3,77:1, trắng trên nút nhấn 2,33:1.
 */
const LIGHT_PAIRS: Pair[] = [
  // Chữ màu chính trên nền nhạt primary-50/100/200
  { fg: "--color-primary-text", bg: "--color-primary-50", note: "tab/mục nav/trang hiện tại, empty-state icon" },
  { fg: "--color-primary-text", bg: "--color-primary-100", note: "nút nhạt" },
  { fg: "--color-primary-800", bg: "--color-primary-200", note: "nút nhạt hover (chữ primary-800)" },
  { fg: "--color-primary-text", bg: "--color-surface", note: "link, tab phân đoạn, mục select đang chọn" },
  { fg: "--color-primary-text", bg: "--color-page", note: "link trên nền trang" },
  { fg: "--color-primary-text", bg: "--color-subtle", note: "mục select đang chọn được tô sáng" },
  // Chữ trên nền đặc của màu chính
  { fg: "--color-on-primary", bg: "--color-primary-fill", note: "nút chính, chip lọc đang chọn, ô logo" },
  { fg: "--color-on-primary", bg: "--color-primary-fill-hover", note: "nút chính hover" },
  { fg: "--color-on-primary", bg: "--color-primary-fill-active", note: "nút chính pressed" },
  // Nút nhấn (thi) và nút nguy hiểm
  { fg: "--color-on-accent", bg: "--color-accent-500", note: "nút exam" },
  { fg: "--color-on-primary", bg: "--color-danger-solid", note: "nút nguy hiểm" },
  { fg: "--color-on-primary", bg: "--color-danger-solid-hover", note: "nút nguy hiểm hover" },
  // Cặp còn lại mà thành phần dùng (R1-2)
  { fg: "--color-text-muted", bg: "--color-subtle", note: "tab phân đoạn thường" },
  { fg: "--color-text-muted", bg: "--color-table-head", note: "tiêu đề bảng" },
  { fg: "--color-danger", bg: "--color-surface", note: "lỗi form, dấu * bắt buộc, nút icon nguy hiểm, mục menu hủy" },
  { fg: "--color-neutral", bg: "--color-surface", note: "tab nội dung thường" },
  { fg: "--color-text-secondary", bg: "--color-subtle", note: "mục sidebar/dropdown hover" },
  { fg: "--color-text-placeholder", bg: "--color-surface", note: "placeholder và icon dẫn đầu trên nền input" },
  { fg: "--color-text-placeholder", bg: "--color-subtle", note: "placeholder trên nền input bị khóa" },
];

/**
 * Cặp chữ/nền đã kiểm cho chế độ tối (A6, F1) và dùng chung cho cả hai chế độ: chữ thương hiệu,
 * trạng thái, trung tính. Chữ màu chính dùng `primary-text`, không dùng `primary-600` trực tiếp.
 */
const DARK_PAIRS: Pair[] = [
  // Chữ thương hiệu / tiêu đề trên nền surface
  { fg: "--color-primary-900", bg: "--color-surface", note: "wordmark sidebar" },
  { fg: "--color-primary-700", bg: "--color-surface", note: "icon/initials" },
  { fg: "--color-primary-800", bg: "--color-surface", note: "nút outline" },
  // primary-700/800 trên nền primary-50 (icon box, avatar, chip tag, nút outline hover)
  { fg: "--color-primary-700", bg: "--color-primary-50", note: "icon/avatar trên primary-50, chip tag" },
  { fg: "--color-primary-800", bg: "--color-primary-50", note: "nút outline hover" },
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

function check(pairs: Pair[], theme: "light" | "dark") {
  const failures: string[] = [];
  const rows: string[] = [];
  for (const { fg, bg, note } of pairs) {
    const ratio = contrast(token(fg, theme), token(bg, theme));
    rows.push(`${note}: ${token(fg, theme)} trên ${token(bg, theme)} = ${ratio.toFixed(2)}:1`);
    if (ratio < 4.5) failures.push(`${note} chỉ đạt ${ratio.toFixed(2)}:1`);
  }
  expect(failures, `\n${rows.join("\n")}\n`).toEqual([]);
}

describe("tokens.css tương phản chữ/nền >= 4.5:1", () => {
  it.each(["light", "dark"] as const)("cặp chữ/nền của chế độ sáng đạt trong chế độ %s", (theme) => {
    check(LIGHT_PAIRS, theme);
  });

  it.each(["light", "dark"] as const)("cặp chữ/nền của chế độ tối đạt trong chế độ %s", (theme) => {
    check(DARK_PAIRS, theme);
  });
});
