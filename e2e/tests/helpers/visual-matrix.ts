import { chromium, expect, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";

/**
 * Trợ thủ kiểm giao diện cho các lane web của đợt 3 (plan FS-4, A7 mục 7, team-rules "Headless visual").
 *
 * Một ô của ma trận = khổ rộng (1280 hoặc 390) x chế độ (sáng, tối) x ngôn ngữ (vi, en). Với mỗi ô:
 * chụp ảnh, kiểm tràn ngang, độ tương phản chữ >= 4,5, lẫn ngôn ngữ, `lang` của trang và nền theo chế độ.
 *
 * Quy ước `data-exam-content`: vùng nội dung đề (đề bài, phương án, ngữ liệu, tên bài/phần/mục/phân đoạn của
 * đề) luôn ở ngôn ngữ của người soạn, nên phép kiểm lẫn ngôn ngữ bỏ qua cả cây con của thuộc tính này. Nhãn
 * giao diện nằm ngoài vùng đó mà sai ngôn ngữ thì vẫn bị báo. Độ tương phản và tràn ngang tính cả vùng đó.
 *
 * Lane sau chỉ gọi; muốn đổi hành vi thì qua Advisor.
 */

export type MatrixMode = "light" | "dark";
export type MatrixLang = "vi" | "en";

export interface MatrixCell {
  width: 1280 | 390;
  height: 900 | 844;
  mode: MatrixMode;
  lang: MatrixLang;
}

const VIEWPORTS: ReadonlyArray<Pick<MatrixCell, "width" | "height">> = [
  { width: 1280, height: 900 },
  { width: 390, height: 844 },
];

/** 8 ô: khổ x chế độ x ngôn ngữ, theo thứ tự cố định để ảnh và báo cáo so được giữa các lần chạy. */
export function matrixCells(): MatrixCell[] {
  const cells: MatrixCell[] = [];
  for (const viewport of VIEWPORTS) {
    for (const mode of ["light", "dark"] as const) {
      for (const lang of ["vi", "en"] as const) {
        cells.push({ ...viewport, mode, lang } as MatrixCell);
      }
    }
  }
  return cells;
}

export function cellLabel(cell: MatrixCell): string {
  return `${cell.width}-${cell.mode}-${cell.lang}`;
}

/** Trình duyệt cho ma trận: giữ thanh cuộn (`--hide-scrollbars` che mất lệch bố cục do thanh cuộn). */
export function launchMatrixBrowser(): Promise<Browser> {
  return chromium.launch({ ignoreDefaultArgs: ["--hide-scrollbars"] });
}

/** Context đúng khổ, `deviceScaleFactor` 1, cookie giao diện và ngôn ngữ của ô. Đóng context ở nơi gọi. */
export async function openMatrixContext(
  browser: Browser,
  cell: MatrixCell,
  baseURL: string,
): Promise<BrowserContext> {
  const context = await browser.newContext({
    viewport: { width: cell.width, height: cell.height },
    deviceScaleFactor: 1,
    colorScheme: cell.mode,
    locale: cell.lang === "vi" ? "vi-VN" : "en-US",
  });
  await applyMatrixCookies(context, cell, baseURL);
  return context;
}

/** Đặt lại cookie `thi360_theme`, `thi360_locale` (gọi lại sau khi đăng nhập nếu phiên ghi đè). */
export async function applyMatrixCookies(context: BrowserContext, cell: MatrixCell, baseURL: string): Promise<void> {
  await context.addCookies([
    { name: "thi360_theme", value: cell.mode, url: baseURL },
    { name: "thi360_locale", value: cell.lang, url: baseURL },
  ]);
}

/** Thư mục ảnh: `THI360_VISUAL_DIR` (thư mục `visual/` của báo cáo lane, không commit) hoặc `test-results/visual`. */
export function visualDir(): string {
  const dir = process.env.THI360_VISUAL_DIR ?? path.join("test-results", "visual");
  mkdirSync(dir, { recursive: true });
  return dir;
}

/** Chụp toàn trang vào `<màn>-<khổ>-<chế độ>-<ngôn ngữ>.png` và trả đường dẫn. */
export async function captureMatrixShot(page: Page, screen: string, cell: MatrixCell): Promise<string> {
  const file = path.join(visualDir(), `${screen}-${cell.width}-${cell.mode}-${cell.lang}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

export interface OverflowReport {
  scrollWidth: number;
  clientWidth: number;
  offenders: string[];
}

/** Trang có cuộn ngang không: `scrollWidth` của `html` hoặc `body` lớn hơn bề rộng khung nhìn. */
export function auditHorizontalOverflow(page: Page): Promise<OverflowReport> {
  return page.evaluate(() => {
    const root = document.documentElement;
    const clientWidth = root.clientWidth;
    const scrollWidth = Math.max(root.scrollWidth, document.body.scrollWidth);
    const offenders: string[] = [];
    if (scrollWidth > clientWidth) {
      for (const el of document.body.querySelectorAll("*")) {
        const rect = el.getBoundingClientRect();
        if (rect.width > 0 && rect.right > clientWidth + 1 && offenders.length < 10) {
          offenders.push(`${el.tagName.toLowerCase()}${el.className ? `.${String(el.className).split(/\s+/)[0]}` : ""} right=${Math.round(rect.right)}`);
        }
      }
    }
    return { scrollWidth, clientWidth, offenders };
  });
}

export interface ContrastFinding {
  element: string;
  text: string;
  ratio: number;
  foreground: string;
  background: string;
}

/**
 * Tương phản chữ theo WCAG: mọi nút chữ nhìn thấy, trừ phần tử bị khóa (`disabled`, `aria-disabled`) và chữ
 * trên nền ảnh hay gradient (không tính được chắc). Màu đọc qua canvas nên đúng với mọi cú pháp CSS, kể cả
 * `color-mix` và `oklch`. Tính cả vùng `data-exam-content`.
 */
export function auditContrast(page: Page, options: { min?: number; scope?: string } = {}): Promise<ContrastFinding[]> {
  return page.evaluate(
    ({ min, scope }) => {
      const root = scope ? document.querySelector(scope) : document.body;
      if (!root) return [];
      const canvas = document.createElement("canvas");
      canvas.width = 1;
      canvas.height = 1;
      const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
      type Rgba = [number, number, number, number];
      const parse = (css: string): Rgba => {
        ctx.clearRect(0, 0, 1, 1);
        ctx.fillStyle = "#000";
        ctx.fillStyle = css;
        ctx.fillRect(0, 0, 1, 1);
        const data = ctx.getImageData(0, 0, 1, 1).data;
        return [data[0]!, data[1]!, data[2]!, data[3]! / 255];
      };
      const over = (top: Rgba, bottom: Rgba): Rgba => {
        const alpha = top[3] + bottom[3] * (1 - top[3]);
        if (alpha === 0) return [0, 0, 0, 0];
        const mix = (i: 0 | 1 | 2) => (top[i] * top[3] + bottom[i] * bottom[3] * (1 - top[3])) / alpha;
        return [mix(0), mix(1), mix(2), alpha];
      };
      const channel = (value: number) => {
        const s = value / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
      };
      const luminance = (c: Rgba) => 0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2]);
      const ratioOf = (a: Rgba, b: Rgba) => {
        const la = luminance(a);
        const lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
      };
      const background = (el: Element): Rgba | null => {
        const chain: Element[] = [];
        for (let node: Element | null = el; node; node = node.parentElement) chain.push(node);
        let result: Rgba = [255, 255, 255, 1];
        for (const node of chain.reverse()) {
          const style = getComputedStyle(node);
          if (style.backgroundImage !== "none") return null;
          result = over(parse(style.backgroundColor), result);
        }
        return result;
      };
      const opacityOf = (el: Element) => {
        let value = 1;
        for (let node: Element | null = el; node; node = node.parentElement) value *= Number(getComputedStyle(node).opacity);
        return value;
      };
      const describe = (el: Element) => {
        const id = el.id ? `#${el.id}` : "";
        const cls = typeof el.className === "string" && el.className ? `.${el.className.split(/\s+/)[0]}` : "";
        return `${el.tagName.toLowerCase()}${id}${cls}`;
      };
      const rgb = (c: Rgba) => `rgb(${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])})`;
      const checked = new Set<Element>();
      const findings: ContrastFinding[] = [];
      const check = (el: Element, sample: string) => {
        if (checked.has(el)) return;
        checked.add(el);
        if (el.closest("[disabled],[aria-disabled='true'],script,style,noscript,template")) return;
        const style = getComputedStyle(el);
        if (style.visibility === "hidden" || style.display === "none") return;
        const rect = el.getBoundingClientRect();
        if (rect.width < 1 || rect.height < 1) return;
        if (style.clip && style.clip !== "auto" && rect.width <= 1) return;
        const bg = background(el);
        if (!bg) return;
        const fgRaw = parse(style.color);
        const fgAlpha = fgRaw[3] * opacityOf(el);
        const fg = over([fgRaw[0], fgRaw[1], fgRaw[2], fgAlpha], bg);
        const ratio = ratioOf(fg, bg);
        if (ratio < min) {
          findings.push({
            element: describe(el),
            text: sample.trim().slice(0, 60),
            ratio: Math.round(ratio * 100) / 100,
            foreground: rgb(fg),
            background: rgb(bg),
          });
        }
      };
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      while (walker.nextNode()) {
        const node = walker.currentNode as Text;
        if (!node.data.trim() || !node.parentElement) continue;
        check(node.parentElement, node.data);
      }
      for (const field of root.querySelectorAll("input:not([type=radio]):not([type=checkbox]),textarea")) {
        const value = (field as HTMLInputElement).value;
        if (value) check(field, value);
      }
      return findings;
    },
    { min: options.min ?? 4.5, scope: options.scope ?? null },
  );
}

export interface LanguageFinding {
  kind: "text" | "attribute";
  value: string;
}

const VIETNAMESE_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

/** Từ giao diện tiếng Anh thường gặp: xuất hiện ở trang `vi` ngoài vùng `data-exam-content` là lẫn ngôn ngữ. */
const ENGLISH_UI_WORDS_RE =
  /\b(Submit|Cancel|Save|Loading|Next|Previous|Close|Answer|Review|Search|Back|Delete|Edit|Retry|Continue|Confirm|Required|Question|Flag|Matching|Essay)\b/;

/**
 * Lẫn ngôn ngữ: chữ nhìn thấy và `aria-label`, `placeholder`, `title`, `alt` ngoài mọi vùng `data-exam-content`.
 * Trang `en` có chữ Việt có dấu, hoặc trang `vi` có từ giao diện tiếng Anh, là lỗi. `allow` bỏ qua các chuỗi
 * hợp lệ (tên riêng, thuật ngữ).
 */
export async function auditMixedLanguage(
  page: Page,
  lang: MatrixLang,
  options: { allow?: RegExp[] } = {},
): Promise<LanguageFinding[]> {
  const collected = await page.evaluate(() => {
    const out: LanguageFinding[] = [];
    const skip = "[data-exam-content],script,style,noscript,template";
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const node = walker.currentNode as Text;
      const parent = node.parentElement;
      if (!parent || !node.data.trim() || parent.closest(skip)) continue;
      out.push({ kind: "text", value: node.data.replace(/\s+/g, " ").trim() });
    }
    for (const el of document.body.querySelectorAll("[aria-label],[placeholder],[title],[alt]")) {
      if (el.closest(skip)) continue;
      for (const name of ["aria-label", "placeholder", "title", "alt"]) {
        const value = el.getAttribute(name);
        if (value?.trim()) out.push({ kind: "attribute", value: value.trim() });
      }
    }
    return out;
  });
  const allow = options.allow ?? [];
  return collected.filter((item) => {
    if (allow.some((pattern) => pattern.test(item.value))) return false;
    return lang === "en" ? VIETNAMESE_RE.test(item.value) : ENGLISH_UI_WORDS_RE.test(item.value);
  });
}

/** `<html lang>` đúng ngôn ngữ của ô. */
export function auditHtmlLang(page: Page): Promise<string> {
  return page.evaluate(() => document.documentElement.lang);
}

/** Nền trang theo chế độ: tối thì nền tối, sáng thì nền sáng (độ chói tương đối của `body`). */
export function auditThemeApplied(page: Page): Promise<{ theme: string | null; luminance: number }> {
  return page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1;
    canvas.height = 1;
    const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
    ctx.fillStyle = getComputedStyle(document.body).backgroundColor;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data as unknown as [number, number, number];
    const channel = (v: number) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    };
    return {
      theme: document.documentElement.getAttribute("data-theme"),
      luminance: 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b),
    };
  });
}

/**
 * Một lần kiểm đủ năm phép cho trang đang mở ở ô `cell`, sau khi trang đã dựng xong: chụp ảnh `<màn>-<khổ>-
 * <chế độ>-<ngôn ngữ>.png`, rồi khẳng định `lang`, nền, tràn ngang, tương phản và lẫn ngôn ngữ. Trả đường dẫn ảnh.
 */
export async function expectMatrixClean(
  page: Page,
  screen: string,
  cell: MatrixCell,
  options: { minContrast?: number; allowLanguage?: RegExp[] } = {},
): Promise<string> {
  const shot = await captureMatrixShot(page, screen, cell);
  const tag = `${screen} ${cellLabel(cell)}`;
  expect(await auditHtmlLang(page), `${tag}: html lang`).toBe(cell.lang);
  const theme = await auditThemeApplied(page);
  if (cell.mode === "dark") expect(theme.luminance, `${tag}: nền tối (${theme.theme})`).toBeLessThan(0.2);
  else expect(theme.luminance, `${tag}: nền sáng (${theme.theme})`).toBeGreaterThan(0.6);
  const overflow = await auditHorizontalOverflow(page);
  expect(overflow.scrollWidth, `${tag}: tràn ngang ${overflow.offenders.join("; ")}`).toBeLessThanOrEqual(
    overflow.clientWidth,
  );
  const contrast = await auditContrast(page, { min: options.minContrast ?? 4.5 });
  expect(contrast, `${tag}: tương phản < ${options.minContrast ?? 4.5}`).toEqual([]);
  const mixed = await auditMixedLanguage(page, cell.lang, { allow: options.allowLanguage });
  expect(mixed, `${tag}: lẫn ngôn ngữ`).toEqual([]);
  return shot;
}
