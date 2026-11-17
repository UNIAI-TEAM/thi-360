import { describe, expect, it } from "vitest";
import { sanitizeQuestionHtml } from "../../../sanitize";
import { materializeBlocks, normalizeEditorHtml, topLevelBlocks, type FormulaResolver } from "../materialize";
import { isWordHtml } from "../detect";
import { parseWordHtml } from "../parse";
import { keepWhole, splitByLabels } from "../split";
import type { ParsedWord } from "../types";
import { wordFixture } from "./fixtures";

const CHOICE = wordFixture("synthetic-word365-choice.html");
const EXTRAS = wordFixture("synthetic-word365-extras.html");

/** Bọc thân HTML vào vỏ clipboard của Word (namespace + Generator) như bản dán thật. */
function wordHtml(body: string): string {
  return `<html xmlns:w="urn:schemas-microsoft-com:office:word" xmlns:m="http://schemas.microsoft.com/office/2004/12/omml"><head><meta name=Generator content="Microsoft Word 15"></head><body><!--StartFragment-->${body}<!--EndFragment--></body></html>`;
}

const equation = (omml: string) => `<!--[if gte msEquation 12]><m:oMath>${omml}</m:oMath><![endif]--><![if !msEquation]>fallback<![endif]>`;
const run = (text: string) => `<m:r><m:t>${text}</m:t></m:r>`;

/** Dựng `FormulaResolver` từ kết quả đọc: công thức đổi được giữ LaTeX thô, "cần gõ lại" bị bỏ. */
function rawResolver(parsed: ParsedWord): FormulaResolver {
  return (id) => {
    const item = parsed.formulas.find((formula) => formula.id === id);
    return item?.kind === "formula" ? { latex: item.latex, display: item.display } : null;
  };
}

describe("Word detection (spec 4.10)", () => {
  it("recognises the Word namespace and the Generator meta in either attribute order", () => {
    expect(isWordHtml(CHOICE)).toBe(true);
    expect(isWordHtml(EXTRAS)).toBe(true);
    expect(isWordHtml('<html xmlns:w="urn:schemas-microsoft-com:office:word"><body><p>x</p></body></html>')).toBe(true);
    expect(isWordHtml('<meta name="Generator" content="Microsoft Word 15"><p>x</p>')).toBe(true);
    expect(isWordHtml("<meta content='Microsoft Word 16' name=Generator><p>x</p>")).toBe(true);
  });

  it("rejects HTML from other sources", () => {
    expect(isWordHtml('<meta charset="utf-8"><b id="docs-internal-guid-1" style="font-weight:normal"><p>Google Docs</p></b>')).toBe(false);
    expect(isWordHtml('<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel"><body><table></table></body></html>')).toBe(false);
    expect(isWordHtml('<meta name="Generator" content="LibreOffice 7.6"><p>x</p>')).toBe(false);
    expect(isWordHtml("<p>plain</p>")).toBe(false);
    expect(isWordHtml("")).toBe(false);
    expect(isWordHtml(null)).toBe(false);
  });
});

describe("parseWordHtml: formulas", () => {
  const parsed = parseWordHtml(CHOICE);

  it("reads OMML from the msEquation comments, which the paste sanitizer would drop", () => {
    expect(sanitizeQuestionHtml(CHOICE).html).not.toMatch(/oMath|msEquation/);
    const converted = parsed.formulas.filter((formula) => formula.kind === "formula");
    expect(converted).toHaveLength(8);
    expect(converted.map((formula) => formula.latex)).toContain("(C):x^2+y^2+4x+4y+6=0");
    expect(converted.map((formula) => formula.latex)).toContain("m=\\frac{8}{15}");
    expect(converted.find((formula) => formula.latex.startsWith("S_"))?.latex).toBe(
      "S_{\\Delta IAB}=\\frac{1}{2}IA\\cdot IB\\cdot\\sin \\widehat{AIB}\\leq\\frac{1}{2}IA\\cdot IB=\\frac{1}{2}R^2=1",
    );
  });

  it("treats MathType picture equations as formulas to retype, with the text before each one as context", () => {
    const retype = parsed.formulas.filter((formula) => formula.kind === "retype");
    expect(retype).toHaveLength(3);
    expect(retype.map((formula) => formula.cause)).toEqual(["image", "image", "image"]);
    expect(retype.map((formula) => formula.context)).toEqual(["Đường tròn", "Đường tròn có tâm", "Đường tròn có tâm , bán kính"]);
    expect(parsed.images).toHaveLength(0);
    expect(parsed.html).not.toContain("<img");
  });

  it("sends a formula with an element outside the subset to \"Cần gõ lại\" and names the element", () => {
    const extras = parseWordHtml(EXTRAS);
    const unsupported = extras.formulas.find((formula) => formula.kind === "retype");
    expect(unsupported).toMatchObject({ kind: "retype", cause: "unsupported", detail: "m:sPre" });
    // Các công thức còn lại của bản dán vẫn đổi được: một công thức hỏng không làm hỏng cả bản dán.
    expect(extras.formulas.filter((formula) => formula.kind === "formula")).toHaveLength(3);
  });

  it("flags malformed OMML as a formula to retype instead of failing the paste", () => {
    const broken = parseWordHtml(wordHtml("<p>x <!--[if gte msEquation 12]><m:oMath><m:f><m:num><![endif]--> y</p>"));
    expect(broken.formulas).toEqual([expect.objectContaining({ kind: "retype", cause: "malformed" })]);
  });

  it("flags small inline pictures as equation images but keeps big or standalone pictures as ordinary images", () => {
    const html = wordHtml(
      `<p>Tính <img width=60 height=22 src="file:///C:/clip_image001.png"> rồi</p>` +
        `<p>Xem hình <img width=320 height=180 src="file:///C:/clip_image002.png" alt="Sơ đồ"></p>` +
        `<p><img width=60 height=22 src="file:///C:/clip_image003.png" alt="Chỉ có ảnh"></p>`,
    );
    const result = parseWordHtml(html);
    expect(result.formulas).toEqual([expect.objectContaining({ kind: "retype", cause: "image", context: "Tính" })]);
    expect(result.images).toEqual([
      expect.objectContaining({ alt: "Sơ đồ", context: "Xem hình" }),
      expect.objectContaining({ alt: "Chỉ có ảnh" }),
    ]);
  });

  it("recognises an equation image by its OLE ProgID even when it is large", () => {
    const html = wordHtml(
      `<p>Cho <img width=300 height=120 src="file:///C:/x.png" v:shapes="_x0000_i1025"> ok</p>` +
        `<!--[if gte mso 9]><xml><o:OLEObject Type="Embed" ProgID="Equation.3" ShapeID="_x0000_i1025" DrawAspect="Content"></o:OLEObject></xml><![endif]-->`,
    );
    expect(parseWordHtml(html).formulas).toEqual([expect.objectContaining({ cause: "image" })]);
  });
});

describe("parseWordHtml: text and structure", () => {
  const { html } = parseWordHtml(EXTRAS);

  it("keeps formatting, lists and tables in the allowed tags and drops Word's markup", () => {
    expect(html).toContain("<strong>Đậm</strong>");
    expect(html).toContain("<em>nghiêng</em>");
    expect(html).toContain("H<sub>2</sub>O, x<sup>2</sup>");
    expect(html).toContain("<u>gạch chân</u>");
    expect(html).toContain("<ul><li><p>Mục thứ nhất</p></li><li><p>Mục thứ hai</p></li></ul>");
    expect(html).toContain("<table><tbody><tr><td><p>Năm</p></td>");
    expect(html).not.toMatch(/style=|class=|<o:p|MsoNormal|·|&nbsp;/);
  });

  it("keeps the space around a formula", () => {
    const text = new DOMParser().parseFromString(parseWordHtml(CHOICE).html, "text/html").body.firstElementChild?.textContent;
    expect(text).toBe(
      "Câu 1. Trong mặt phẳng tọa độ Oxy cho đường tròn  có bán kính R, tâm I và đường thẳng  (m là tham số thực). Tìm m để Δ cắt (C) tại 2 điểm phân biệt sao cho diện tích ΔIAB đạt giá trị lớn nhất.",
    );
  });

  it("lists pictures that are not equations instead of importing them", () => {
    const extras = parseWordHtml(EXTRAS);
    expect(extras.images).toEqual([expect.objectContaining({ alt: "Hình vẽ tam giác ABC", context: "Hình vẽ minh họa:" })]);
    expect(extras.html).not.toContain("<img");
  });

  it("turns an auto-lettered option list into plain paragraphs that keep their labels", () => {
    const html = wordHtml(
      `<p class=MsoListParagraph style='mso-list:l1 level1 lfo2'><![if !supportLists]><span>A.<span>&nbsp;&nbsp;</span></span><![endif]>Một</p>` +
        `<p class=MsoListParagraph style='mso-list:l1 level1 lfo2'><![if !supportLists]><span>B.<span>&nbsp;&nbsp;</span></span><![endif]>Hai</p>`,
    );
    expect(parseWordHtml(html).html).toBe("<p>A. Một</p><p>B. Hai</p>");
  });

  it("makes numbered Word paragraphs an ordered list", () => {
    const html = wordHtml(
      `<p class=MsoListParagraph style='mso-list:l2 level1 lfo3'><![if !supportLists]><span>1.<span>&nbsp;</span></span><![endif]>Một</p>` +
        `<p class=MsoListParagraph style='mso-list:l2 level1 lfo3'><![if !supportLists]><span>2.<span>&nbsp;</span></span><![endif]>Hai</p>`,
    );
    expect(parseWordHtml(html).html).toBe("<ol><li><p>Một</p></li><li><p>Hai</p></li></ol>");
  });
});

describe("splitByLabels (A./B./C./D./Lời giải)", () => {
  const blocksOf = (body: string) => topLevelBlocks(parseWordHtml(wordHtml(body)).html);
  const htmlOf = (blocks: Element[], parsed: ParsedWord) => normalizeEditorHtml(materializeBlocks(blocks, rawResolver(parsed)));

  it("splits the question, four options and the solution; the labels themselves are removed", () => {
    const parsed = parseWordHtml(CHOICE);
    const split = splitByLabels(topLevelBlocks(parsed.html));
    expect(split.found).toBe(true);
    expect(split.options.map((option) => option.label)).toEqual(["A", "B", "C", "D"]);
    expect(htmlOf(split.stem, parsed)).toBe(
      '<p><strong>Câu 1.</strong> Trong mặt phẳng tọa độ Oxy cho đường tròn <span class="math-inline">\\((C):x^2+y^2+4x+4y+6=0\\)</span> có bán kính R, tâm I và đường thẳng <span class="math-inline">\\(\\Delta:x+my-2m+3=0\\)</span> (m là tham số thực). Tìm m để Δ cắt (C) tại 2 điểm phân biệt sao cho diện tích ΔIAB đạt giá trị lớn nhất.</p>',
    );
    expect(split.options.map((option) => htmlOf(option.blocks, parsed))).toEqual([
      '<p><span class="math-inline">\\(\\left[\\frac{m=0}{m=\\frac{8}{15}}\\right.\\)</span></p>',
      '<p><span class="math-inline">\\(m=0\\)</span></p>',
      '<p><span class="math-inline">\\(m=\\frac{8}{15}\\)</span></p>',
      '<p><span class="math-inline">\\(\\left[\\frac{m=1}{m=\\frac{8}{15}}\\right.\\)</span></p>',
    ]);
    // Đoạn "Lời giải" chỉ còn tiêu đề nên bị bỏ; ba đoạn lời giải còn lại (hai công thức, một đoạn có ảnh công thức).
    expect(split.solution).toHaveLength(3);
    expect(htmlOf(split.solution, parsed)).toContain('<p>Dấu “=” xảy ra: <span class="math-inline">\\(sin\\widehat{AIB}=1\\)</span></p>');
  });

  it("splits four options written on one line and cuts the formatting wrapper off the label", () => {
    const parsed = parseWordHtml(wordHtml("<p>Chọn đáp án.</p><p><b>A.</b> 1   <b>B.</b> 2   C. 3   D. <i>4</i></p>"));
    const split = splitByLabels(topLevelBlocks(parsed.html));
    expect(split.options.map((option) => htmlOf(option.blocks, parsed))).toEqual(["<p>1</p>", "<p>2</p>", "<p>3</p>", "<p><em>4</em></p>"]);
    expect(split.stem).toHaveLength(1);
  });

  it("splits formulas that share a line with their labels", () => {
    const parsed = parseWordHtml(wordHtml(`<p>A. ${equation(run("x=1"))} B. ${equation(run("x=2"))}</p>`));
    const split = splitByLabels(topLevelBlocks(parsed.html));
    expect(split.options.map((option) => htmlOf(option.blocks, parsed))).toEqual([
      '<p><span class="math-inline">\\(x=1\\)</span></p>',
      '<p><span class="math-inline">\\(x=2\\)</span></p>',
    ]);
  });

  it("only accepts labels in the order A, B, C, D, so a stray \"B.\" in the question stays in the question", () => {
    const split = splitByLabels(blocksOf("<p>B. Điểm B nằm ngoài</p><p>A. Một</p><p>C. Ba</p>"));
    expect(split.options.map((option) => option.label)).toEqual(["A"]);
    expect(split.stem).toHaveLength(1);
    // "C." sau "A." (thiếu B) là phần tiếp của phương án A, không phải phương án mới.
    expect(split.options[0]!.blocks).toHaveLength(2);
  });

  it("keeps the rest of a \"Lời giải: …\" line and starts the solution there", () => {
    const split = splitByLabels(blocksOf("<p>Đề</p><p>Lời giải: Ta có x = 1</p><p>Vậy x = 1</p>"));
    expect(split.solution.map((block) => block.textContent)).toEqual(["Ta có x = 1", "Vậy x = 1"]);
    expect(split.options).toEqual([]);
  });

  it("puts everything in the question when there are no labels, or when splitting is off", () => {
    const none = splitByLabels(blocksOf("<p>Chỉ có đề</p><p>Một đoạn nữa</p>"));
    expect(none.found).toBe(false);
    expect(none.stem).toHaveLength(2);
    const whole = keepWhole(blocksOf("<p>A. Một</p><p>Lời giải: x</p>"));
    expect(whole.stem).toHaveLength(2);
    expect(whole.options).toEqual([]);
    expect(whole.solution).toEqual([]);
  });
});

describe("materialize: output HTML equals the editor's fixture form", () => {
  it("writes formulas as span.math-inline and escapes < > & the way the editor does", () => {
    const parsed = parseWordHtml(wordHtml(`<p>Cho ${equation(run("a&lt;b"))} và ${equation(run("c&amp;d"))}.</p>`));
    const html = normalizeEditorHtml(materializeBlocks(topLevelBlocks(parsed.html), rawResolver(parsed)));
    expect(html).toBe('<p>Cho <span class="math-inline">\\(a&lt;b\\)</span> và <span class="math-inline">\\(c\\&amp;d\\)</span>.</p>');
    // Đúng dạng mẫu `math-inline-and-block` của bảng mẫu: mở qua bộ lọc không đổi một byte.
    expect(sanitizeQuestionHtml(html).html).toBe(html);
  });

  it("writes a paragraph that holds only a display equation as span.math-block", () => {
    const parsed = parseWordHtml(wordHtml(`<p>Tính</p><p><!--[if gte msEquation 12]><m:oMathPara><m:oMath>${run("x+1")}</m:oMath></m:oMathPara><![endif]--></p>`));
    const html = normalizeEditorHtml(materializeBlocks(topLevelBlocks(parsed.html), rawResolver(parsed)));
    expect(html).toBe('<p>Tính</p><span class="math-block">\\[x+1\\]</span>');
  });

  it("drops a formula the resolver refuses without leaving a gap", () => {
    const parsed = parseWordHtml(wordHtml(`<p>a ${equation(run("x"))} b</p>`));
    expect(normalizeEditorHtml(materializeBlocks(topLevelBlocks(parsed.html), () => null))).toBe("<p>a b</p>");
  });
});
