import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { sanitizeQuestionHtml } from "../sanitize";

interface Sample {
  name: string;
  valid: boolean;
  allowImages: boolean;
  html: string;
  violations?: { tag: string; attribute?: string }[];
}

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: Sample[] };

const invalidSamples = fixture.samples.filter((sample) => !sample.valid);

function violationKey(violation: { tag: string; attribute?: string | null }) {
  return violation.attribute === undefined || violation.attribute === null
    ? violation.tag
    : `${violation.tag}.${violation.attribute}`;
}

/** So vi phạm như tập hợp theo đúng quy ước của bảng mẫu: `tag` hoặc `tag.thuộc-tính`, không kể thứ tự. */
function expectViolations(sample: Sample) {
  const { violations } = sanitizeQuestionHtml(sample.html, { allowImages: sample.allowImages });
  const actual = new Set(violations.map(violationKey));
  const expected = new Set((sample.violations ?? []).map(violationKey));
  expect(actual, `${sample.name}: ${JSON.stringify(violations)}`).toEqual(expected);
}

describe("sanitizeQuestionHtml với các mẫu vi phạm", () => {
  for (const sample of invalidSamples) {
    it(`reports the listed violations for ${sample.name}`, () => {
      expectViolations(sample);
    });
  }

  describe("removes the offending parts", () => {
    it("keeps text but drops the violating figure/blockquote block", () => {
      const { html } = sanitizeQuestionHtml(
        '<p>a</p><figure class="gallery"><figcaption>x</figcaption></figure><p>b</p>',
      );
      expect(html).toBe("<p>a</p><p>b</p>");
    });

    it("drops a nested figure but keeps the surrounding cell", () => {
      const { html } = sanitizeQuestionHtml(
        '<table><tbody><tr><td><figure class="cross"><table><tbody><tr><td>x</td></tr></tbody></table></figure></td></tr></tbody></table>',
      );
      expect(html).toBe('<table><tbody><tr><td></td></tr></tbody></table>');
    });

    it("drops stray figcaption, footer and cite elements at the wrong place", () => {
      const { html } = sanitizeQuestionHtml("<p>a</p><figcaption>loose</figcaption><footer><cite>x</cite></footer>");
      expect(html).toBe("<p>a</p>");
      expect(sanitizeQuestionHtml("<p>Theo <cite>Truyện Kiều</cite></p>").html).toBe("<p>Theo </p>");
    });

    it("keeps a p.figure-source that lost its figure parent by dropping class only", () => {
      const { html } = sanitizeQuestionHtml('<p class="figure-source">Nguồn</p>');
      expect(html).toBe("<p>Nguồn</p>");
    });

    it("drops bad blank spans entirely", () => {
      const { html } = sanitizeQuestionHtml(
        '<p>a <span class="blank" data-blank="21"></span> <span class="blank" data-blank="2">x</span> <span class="blank"></span> b</p>',
      );
      expect(html).toBe("<p>a    b</p>");
    });

    it("drops the u data-label attribute with a bad value, keeping plain underline", () => {
      const { html } = sanitizeQuestionHtml('<p><u data-label="Z">x</u> <u data-label="A">y</u></p>');
      expect(html).toBe("<p><u>x</u> <u data-label=\"A\">y</u></p>");
    });

    it("drops th scope outside col/row and td class outside num", () => {
      const { html } = sanitizeQuestionHtml(
        '<figure class="data-table"><table><tbody><tr><th scope="rowgroup">a</th><td class="highlight">1</td></tr></tbody></table></figure>',
      );
      expect(html).toBe('<figure class="data-table"><table><tbody><tr><th>a</th><td>1</td></tr></tbody></table></figure>');
    });

    it("drops a blockquote whose children are not paragraphs", () => {
      const { html } = sanitizeQuestionHtml(
        '<p>x</p><blockquote class="quote" data-kind="prose" data-line-numbers="0"><ul><li>y</li></ul></blockquote>',
      );
      expect(html).toBe("<p>x</p>");
    });
  });

  describe("legacy-plain-table (TableKit)", () => {
    const legacy = '<table><tbody><tr><td colspan="1" rowspan="1"><p>a</p></td></tr></tbody></table>';

    it("round-trips: TableKit's style and colgroup are dropped", () => {
      const emitted =
        '<table style="min-width: 50px"><colgroup><col style="width: 25px"><col style="width: 25px"></colgroup><tbody><tr><td colspan="1" rowspan="1"><p>a</p></td><td colspan="1" rowspan="1"><p>b</p></td></tr></tbody></table>';
      const { html, violations } = sanitizeQuestionHtml(emitted);
      expect(html).toBe('<table><tbody><tr><td colspan="1" rowspan="1"><p>a</p></td><td colspan="1" rowspan="1"><p>b</p></td></tr></tbody></table>');
      expect(violations.some((violation) => violation.tag === "colgroup")).toBe(true);
      expect(violations.some((violation) => violation.tag === "table" && violation.attribute === "style")).toBe(true);
    });

    it("accepts colspan=1 rowspan=1 in stored content", () => {
      const { html, violations } = sanitizeQuestionHtml(legacy);
      expect(violations).toEqual([]);
      expect(html).toBe(legacy);
    });
  });
});
