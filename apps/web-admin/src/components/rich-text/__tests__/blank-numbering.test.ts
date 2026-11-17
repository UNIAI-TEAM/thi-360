import { describe, expect, it } from "vitest";
import {
  blankNumbersOfHtml,
  firstChildWithoutBlank,
  renumberBlanksAfterMove,
  renumberBlanksAfterRemove,
} from "../insert/blank/blank-numbering";

const blank = (n: number) => `<span class="blank" data-blank="${n}"></span>`;
const passage = (...numbers: number[]) => `<p>${numbers.map((n) => `w${n} ${blank(n)}`).join(" ")} end</p>`;

describe("đánh lại số chỗ trống theo câu con (spec 3.5)", () => {
  it("đọc số theo thứ tự xuất hiện", () => {
    expect(blankNumbersOfHtml(passage(2, 1, 3))).toEqual([2, 1, 3]);
    expect(blankNumbersOfHtml("<p>no blanks</p>")).toEqual([]);
  });

  it("chỗ trống gắn câu con đầu tiên chưa có chỗ trống", () => {
    expect(firstChildWithoutBlank(3, [])).toBe(1);
    expect(firstChildWithoutBlank(3, [1, 2])).toBe(3);
    expect(firstChildWithoutBlank(3, [2, 1, 3])).toBeNull();
    expect(firstChildWithoutBlank(0, [])).toBeNull();
    // Chỗ trống số lớn hơn số câu con không chiếm chỗ của câu con nào.
    expect(firstChildWithoutBlank(2, [1, 5])).toBe(2);
  });

  it("đổi thứ tự câu con: chỗ trống của câu đi theo, các câu bị dịch chỗ được đánh lại", () => {
    // Số đọc theo thứ tự xuất hiện trong ngữ liệu; chữ quanh chỗ trống không đổi.
    const moved = (html: string, from: number, to: number) => blankNumbersOfHtml(renumberBlanksAfterMove(html, from, to));
    // Câu 1 xuống vị trí 3: câu 2→1, câu 3→2, câu 1→3.
    expect(moved(passage(1, 2, 3), 0, 2)).toEqual([3, 1, 2]);
    // Câu 3 lên vị trí 1.
    expect(moved(passage(1, 2, 3), 2, 0)).toEqual([2, 3, 1]);
    // Hai câu liền nhau đổi chỗ.
    expect(moved(passage(1, 2, 3), 0, 1)).toEqual([2, 1, 3]);
    // Câu 2 (chưa có chỗ trống) xuống vị trí 3: chỉ chỗ trống của câu 3 bị dịch.
    expect(moved(passage(1, 3), 1, 2)).toEqual([1, 2]);
    expect(renumberBlanksAfterMove(passage(1, 2, 3), 0, 2).replace(/<span[^>]*><\/span>/g, "")).toBe(
      passage(1, 2, 3).replace(/<span[^>]*><\/span>/g, ""),
    );
  });

  it("xóa câu con k: xóa chỗ trống của nó, giảm 1 các số lớn hơn k, chữ quanh giữ nguyên", () => {
    expect(renumberBlanksAfterRemove(passage(1, 2, 3), 1)).toBe(`<p>w1 ${blank(1)} w2  w3 ${blank(2)} end</p>`);
    expect(renumberBlanksAfterRemove(passage(1, 2, 3), 0)).toBe(`<p>w1  w2 ${blank(1)} w3 ${blank(2)} end</p>`);
    // Câu con không có chỗ trống bị xóa: không xóa chỗ trống nào, chỉ giảm số sau nó.
    expect(blankNumbersOfHtml(renumberBlanksAfterRemove(passage(1, 3), 1))).toEqual([1, 2]);
    expect(blankNumbersOfHtml(renumberBlanksAfterRemove(passage(1, 4), 1))).toEqual([1, 3]);
  });
});
