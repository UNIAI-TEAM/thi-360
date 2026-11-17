import { readFileSync } from "node:fs";
import { join } from "node:path";

const FIXTURE_DIR = join(process.cwd(), "src/components/rich-text/insert/word/__fixtures__");

/** Nội dung một tệp trong `__fixtures__` (clipboard HTML đã lưu). */
export function wordFixture(name: string): string {
  return readFileSync(join(FIXTURE_DIR, name), "utf8");
}
