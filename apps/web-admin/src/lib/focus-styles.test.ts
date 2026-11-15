import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * DESIGN.md mục 5.13 và 8: vòng focus lấy từ `focusRing` / `focusRingInset` của kit, tắt viền ngoài bằng
 * `outline-hidden`. `outline-none` bỏ cả viền ở chế độ tương phản cao của hệ điều hành, còn vòng `ring-*` là bóng đổ
 * nên chế độ đó cũng bỏ: điều khiển mất dấu focus. jsdom không dựng được chế độ này nên kiểm ở mức mã nguồn.
 */
const SRC = join(__dirname, "..");
const BANNED = [/\boutline-none\b/, /\bfocus-visible:ring-/];

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

describe("lớp focus trong mã nguồn", () => {
  it("không còn outline-none hay vòng focus-visible:ring-* viết tay", () => {
    const offenders = sourceFiles(SRC).flatMap((file) =>
      readFileSync(file, "utf8")
        .split("\n")
        .flatMap((line, index) =>
          // Bỏ qua dòng chú thích nhắc tới tên lớp để giải thích quy ước.
          !/^\s*(\*|\/\/)/.test(line) && BANNED.some((re) => re.test(line))
            ? [`${relative(SRC, file)}:${index + 1}`]
            : [],
        ),
    );
    expect(offenders).toEqual([]);
  });
});
