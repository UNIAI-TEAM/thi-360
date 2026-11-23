import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_BY_LOCALE, UI_STRINGS_EN, UI_STRINGS_VI } from "../../messages/ui-strings";

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      out.push(...walk(full));
    } else if (/\.(ts|tsx)$/.test(entry.name) && !/\.test\.(ts|tsx)$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

/** Bỏ chú thích nhưng giữ nguyên chuỗi, để chỉ bắt chữ có dấu nằm trong chuỗi ký tự hay JSX. */
function stripComments(source: string): string {
  let out = "";
  let i = 0;
  while (i < source.length) {
    const ch = source[i]!;
    const next = source[i + 1];
    if (ch === '"' || ch === "'" || ch === "`") {
      const quote = ch;
      out += ch;
      i += 1;
      while (i < source.length) {
        const c = source[i]!;
        out += c;
        i += 1;
        if (c === "\\" && i < source.length) {
          out += source[i]!;
          i += 1;
          continue;
        }
        if (c === quote) break;
      }
      continue;
    }
    if (ch === "/" && next === "/") {
      while (i < source.length && source[i] !== "\n") i += 1;
      continue;
    }
    if (ch === "/" && next === "*") {
      i += 2;
      while (i < source.length && !(source[i] === "*" && source[i + 1] === "/")) i += 1;
      i += 2;
      continue;
    }
    out += ch;
    i += 1;
  }
  return out;
}

function shape(value: unknown): unknown {
  if (typeof value === "string") return "string";
  if (typeof value === "function") return `function/${value.length}`;
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)]));
  }
  return typeof value;
}

describe("quét nguồn của thành phần câu hỏi và bộ hiển thị (AC-20)", () => {
  it("không chứa chữ tiếng Việt có dấu viết thẳng trong mã nguồn (ngoài tệp test và tệp dịch)", () => {
    const roots = [
      path.resolve(import.meta.dirname, ".."),
      path.resolve(import.meta.dirname, "../../rich-text"),
      path.resolve(import.meta.dirname, "../../essay-editor"),
    ];
    const files = roots.flatMap(walk);
    expect(files.length).toBeGreaterThan(10);
    const violations: string[] = [];
    for (const file of files) {
      stripComments(readFileSync(file, "utf8"))
        .split("\n")
        .forEach((line, index) => {
          if (VIETNAMESE_DIACRITIC_RE.test(line)) {
            violations.push(`${path.relative(path.resolve(import.meta.dirname, "../.."), file)}:${index + 1}: ${line.trim()}`);
          }
        });
    }
    expect(violations).toEqual([]);
  });

  it("mọi chuỗi của khối question có ở vi và en, cùng hình dạng", () => {
    expect(shape(UI_STRINGS_EN.question)).toEqual(shape(UI_STRINGS_VI.question));
    expect(Object.keys(UI_STRINGS_BY_LOCALE.vi.question.typeLabels)).toHaveLength(8);
  });

  it("chuỗi en của khối question không có chữ tiếng Việt, chuỗi vi không trống", () => {
    const strings = (value: unknown): string[] =>
      typeof value === "string"
        ? [value]
        : typeof value === "function"
          ? []
          : value && typeof value === "object"
            ? Object.values(value).flatMap(strings)
            : [];
    for (const text of strings(UI_STRINGS_EN.question)) expect(text).not.toMatch(VIETNAMESE_DIACRITIC_RE);
    for (const text of strings(UI_STRINGS_VI.question)) expect(text.trim()).not.toBe("");
  });
});
