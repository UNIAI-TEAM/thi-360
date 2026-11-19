import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichTextView } from "../rich-text-view";
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
const validSamples = fixture.samples.filter((sample) => sample.valid);
const invalidSamples = fixture.samples.filter((sample) => !sample.valid);

function violationKey(violation: { tag: string; attribute?: string | null }) {
  return violation.attribute === undefined || violation.attribute === null
    ? violation.tag
    : `${violation.tag}.${violation.attribute}`;
}

/**
 * Bảng mẫu là hợp đồng hai phía (spec 3, plan B2): mẫu hợp lệ phải lọc không vi phạm,
 * kết quả bằng đúng chuỗi html và hiển thị qua `RichTextView` không lỗi; mẫu không hợp lệ
 * bị bộ lọc từ chối với đúng các vi phạm liệt kê (so như tập hợp `tag`/`tag.attr`).
 */
describe("rich-text-samples.json qua packages/ui", () => {
  it("sanitizes every valid sample without violations and emits the identical string", () => {
    for (const sample of validSamples) {
      const { html, violations } = sanitizeQuestionHtml(sample.html, { allowImages: sample.allowImages });
      expect(violations, `${sample.name}: ${JSON.stringify(violations)}`).toEqual([]);
      expect(html, sample.name).toBe(sample.html);
    }
  });

  it("renders every valid sample through RichTextView without a warning frame", async () => {
    for (const sample of validSamples) {
      const { container, unmount } = render(<RichTextView html={sample.html} allowImages={sample.allowImages} />);
      const root = container.querySelector(".rich-text-body");
      expect(root, `${sample.name}: root`).toBeTruthy();
      expect(root!.innerHTML.length, `${sample.name}: empty render`).toBeGreaterThan(0);
      // `\ce` dựng sau khi mhchem tải xong: chờ khi không còn giữ chỗ rồi kiểm khung cảnh báo.
      await waitFor(() => {
        expect(root!.querySelector("[aria-busy]"), `${sample.name}: pending mhchem`).toBeNull();
      });
      expect(root!.querySelector(".math-error"), `${sample.name}: math error frame`).toBeNull();
      unmount();
    }
  });

  for (const sample of invalidSamples) {
    it(`rejects ${sample.name} with the listed violations`, () => {
      const { violations } = sanitizeQuestionHtml(sample.html, { allowImages: sample.allowImages });
      const actual = new Set(violations.map(violationKey));
      const expected = new Set((sample.violations ?? []).map(violationKey));
      expect(actual, `${sample.name}: ${JSON.stringify(violations)}`).toEqual(expected);
    });
  }
});
