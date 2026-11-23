import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

function walkSourceFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      results.push(...walkSourceFiles(full));
    } else if (/\.(ts|tsx)$/.test(entry.name) && !/\.test\.(ts|tsx)$/.test(entry.name)) {
      results.push(full);
    }
  }
  return results;
}

/**
 * Ranh giới bundle (plan FS-4 AC-5, S1-2, P1-8): tệp gốc `src/index.ts` được mọi trang import — kéo
 * rich-text/question/essay-editor vào đó là kéo KaTeX hoặc Tiptap vào chunk đầu của `/` và `/login`.
 * Các cổng con `./rich-text`, `./question`, và `./essay-editor` nằm ngoài tệp gốc; đồng thời
 * `./question` và `./rich-text` tuyệt đối không kéo `./essay-editor` hay `@tiptap/*`.
 */
describe("packages/ui barrel boundary", () => {
  const srcDir = path.resolve(import.meta.dirname, "../..");
  const pkgJsonPath = path.resolve(srcDir, "../package.json");

  it("src/index.ts does not re-export or import rich-text, question, or essay-editor", () => {
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    const specifiers = [...barrel.matchAll(/(?:from|import)\s*\(?\s*["']([^"']+)["']/g)].map(
      (match) => match[1]!,
    );
    expect(specifiers.length).toBeGreaterThan(0);
    for (const specifier of specifiers) {
      expect(specifier).not.toMatch(/(^|[/\\])(rich-text|question|essay-editor)([/\\]|$)/);
      expect(specifier).not.toMatch(/^@tiptap\//);
    }
    // Cả `export * from` lẫn `export { X } from` đều nằm trong `specifiers` ở trên;
    // dòng này chặn thêm cú pháp `export ... = require`/re-export viết tắt nếu có.
    expect(barrel).not.toMatch(/export\s+\{[^}]*(RichTextView|TiptapEssayEditor)/);
  });

  it("src/index.ts does not import katex or @tiptap transitively", () => {
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    expect(barrel).not.toMatch(/katex|@tiptap/);
  });

  it("root barrel only imports from existing shared dirs, never renderer/question/essay-editor dirs", () => {
    // Tệp gốc chỉ được phép import tương đối vào các thư mục dùng chung đã có — kiểm
    // duyệt tên thư mục con của từng specifier để mã mới không lách qua danh sách trên.
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    for (const match of barrel.matchAll(/from\s+["']\.\/([^/]+)\//g)) {
      const segment = match[1]!;
      expect(["components", "lib", "messages", "theme", "styles"]).toContain(segment);
    }
  });

  it("src/question/** and src/rich-text/** never import essay-editor or @tiptap/*", () => {
    const isolatedDirs = [path.join(srcDir, "question"), path.join(srcDir, "rich-text")];
    const files = isolatedDirs.flatMap(walkSourceFiles);
    expect(files.length).toBeGreaterThan(0);
    for (const file of files) {
      const source = readFileSync(file, "utf8");
      const specifiers = [...source.matchAll(/(?:from|import)\s*\(?\s*["']([^"']+)["']/g)].map(
        (match) => match[1]!,
      );
      for (const specifier of specifiers) {
        expect(specifier).not.toMatch(/(^|[/\\])essay-editor([/\\]|$)/);
        expect(specifier).not.toMatch(/^@tiptap\//);
      }
    }
  });

  it("packages/ui/package.json exposes ./essay-editor as an explicit subpath export", () => {
    const pkg = JSON.parse(readFileSync(pkgJsonPath, "utf8")) as {
      exports?: Record<string, unknown>;
    };
    expect(pkg.exports?.["./essay-editor"]).toBe("./src/essay-editor/index.ts");
  });
});
