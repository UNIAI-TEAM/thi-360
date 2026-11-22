import { afterEach, describe, expect, it, vi } from "vitest";
import { countWords } from "./count-words";

const CASES: [name: string, html: string, expected: number][] = [
  ["chữ tiếng Việt có dấu", "<p>Người lính trẻ ra trận</p>", 5],
  ["dạng dựng sẵn và dạng tổ hợp (NFC) là cùng một chữ", "<p>e\u0302 \u00ea</p>", 2],
  ["khoảng trắng không ngắt", "<p>một\u00a0hai\u00a0\u00a0ba</p>", 3],
  ["thực thể khoảng trắng không ngắt", "<p>một&nbsp;hai</p>", 2],
  ["xuống dòng", "một\nhai\n\nba", 3],
  ["ranh giới đoạn là khoảng trắng", "<p>một</p><p>hai</p>", 2],
  ["thẻ nội dòng không tách chữ", "<p>ng<strong>ười</strong> lính</p>", 2],
  ["thẻ rỗng", "<p></p><p> </p><p><br></p>", 0],
  ["công thức đứng riêng không tính", '<p>Ta có <span class="math-inline" data-latex="x^2">x^2</span> bằng</p>', 3],
  ["khối công thức không tính", '<p>Xét</p><span class="math-block" data-latex="E=mc^2">E=mc^2</span>', 1],
  ["ký hiệu đứng riêng không tính", "<p>một - hai ... — ba</p>", 3],
  ["chữ số là một chữ", "<p>năm 2026</p>", 2],
  ["rỗng", "", 0],
];

describe("cách đếm chữ", () => {
  it.each(CASES)("%s", (_name, html, expected) => {
    expect(countWords(html)).toBe(expected);
  });

  describe("nhánh server (không có DOMParser) cho cùng kết quả", () => {
    afterEach(() => vi.unstubAllGlobals());
    it.each(CASES)("%s", (_name, html, expected) => {
      vi.stubGlobal("DOMParser", undefined);
      expect(countWords(html)).toBe(expected);
    });
  });
});
