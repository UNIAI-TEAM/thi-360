import { describe, expect, it } from "vitest";
import * as richText from "../index";
import type { RichTextViewProps, SanitizeResult, SanitizeViolation } from "../index";

/**
 * `@thi360/ui/rich-text` phải xuất đủ mọi hàm và hằng mà mã trình soạn và các màn của
 * web-admin đang import từ `components/rich-text/{view/rich-text-view,sanitize,math-render,html-contract}`
 * (plan FS-4 "Ảnh hưởng ngoài task"): RichTextView (+ kiểu props), sanitizeQuestionHtml,
 * questionHtmlText, hasUnbalancedMath, renderMath, deferUntilMhchem và toàn bộ hằng/hàm
 * của html-contract mà các import hiện có nhắc tới (FIGURE_WIDTHS, stripTrailingEmptyParagraph…)
 * kèm các hằng còn lại để FA-1 đổi import không thiếu tên.
 */
const VALUE_EXPORTS = [
  "RichTextView",
  "sanitizeQuestionHtml",
  "questionHtmlText",
  "hasUnbalancedMath",
  "renderMath",
  "deferUntilMhchem",
  "ALLOWED_TAGS",
  "DROP_SUBTREE_TAGS",
  "MATH_CLASSES",
  "FIGURE_CLASSES",
  "PARAGRAPH_CLASSES",
  "TD_CLASSES",
  "FIGURE_WIDTHS",
  "FIGURE_ZOOMS",
  "QUOTE_KINDS",
  "QUOTE_LINE_NUMBERS",
  "TH_SCOPES",
  "UNDERLINE_LABEL_PATTERN",
  "BLANK_NUMBER_PATTERN",
  "UUID_PATTERN",
  "attributeAllowed",
  "tagViolation",
  "attributeViolation",
  "closedAttributeViolations",
  "checkElementStructure",
  "stripTrailingEmptyParagraph",
] as const;

describe("@thi360/ui/rich-text exports", () => {
  it("exports every name web-admin imports from the read-only renderer", () => {
    const mod = richText as Record<string, unknown>;
    for (const name of VALUE_EXPORTS) {
      expect(mod[name], `missing export: ${name}`).toBeDefined();
    }
  });

  it("exports the renderer types", () => {
    const props: RichTextViewProps = { html: "<p>x</p>", allowImages: false, blankNumberStart: 2 };
    expect(props.blankNumberStart).toBe(2);
    const violation: SanitizeViolation = { tag: "p", attribute: null, value: null };
    const result: SanitizeResult = { html: "", violations: [violation] };
    expect(result.violations).toHaveLength(1);
    expect(typeof richText.sanitizeQuestionHtml).toBe("function");
  });
});
