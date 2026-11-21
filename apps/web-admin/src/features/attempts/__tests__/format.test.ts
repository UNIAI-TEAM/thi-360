import { describe, expect, it } from "vitest";
import {
  extractEventDetailInfo,
  parseAttemptAnswerDisplay,
  stripHtmlToPlainText,
} from "../format";

describe("attempts format helpers", () => {
  it("decodes common HTML entities without double-unescaping (R1-42)", () => {
    expect(
      stripHtmlToPlainText("<p>A &amp; B &lt; C &gt; D &quot;E&quot; &#39;F&#39;&nbsp;G</p>"),
    ).toBe('A & B < C > D "E" \'F\' G');
    expect(stripHtmlToPlainText("<p>&amp;lt;script&amp;gt;</p>")).toBe("&lt;script&gt;");
  });

  it("preserves original 1-based CLOZE blank indices and guards malformed runtime blanks (R1-12, R1-13, R1-15)", () => {
    const sparse = parseAttemptAnswerDisplay({
      itemId: "i-cloze",
      questionType: "CLOZE",
      answer: {
        type: "CLOZE",
        blanks: { b1: "", b2: "  Hà Nội  ", b3: "   " },
      },
    });
    expect(sparse).toEqual({
      kind: "cloze",
      entries: [{ index: 2, value: "Hà Nội" }],
    });

    const malformed = parseAttemptAnswerDisplay({
      itemId: "i-bad",
      questionType: "CLOZE",
      answer: {
        type: "CLOZE",
        blanks: { b1: null as unknown as string, b2: 123 as unknown as string, b3: "OK" },
      },
    });
    expect(malformed).toEqual({
      kind: "cloze",
      entries: [{ index: 3, value: "OK" }],
    });
  });

  it("caps long ESSAY preview at 160 characters (R1-42)", () => {
    const longText = "a ".repeat(120).trim();
    const res = parseAttemptAnswerDisplay({
      itemId: "i-essay",
      questionType: "ESSAY",
      answer: {
        type: "ESSAY",
        html: `<p>${longText}</p>`,
        wordCount: 120,
      },
    });
    expect(res.kind).toBe("essay");
    if (res.kind === "essay") {
      expect(res.preview.length).toBeLessThanOrEqual(160);
      expect(res.preview.endsWith("…")).toBe(true);
    }
  });

  it("flags known event fields, free-text fields, and unknown key fallback (R1-28, R1-29)", () => {
    const parts = new Map([["p-1", "Phần Đọc"]]);
    const info = extractEventDetailInfo(
      {
        id: "ev-1",
        type: "REGRADED",
        partId: "p-1",
        data: {
          partCode: "P1",
          reason: "Regrade requested",
          error: "Null pointer",
          seq: 5,
          customExtra: "abc",
        },
      },
      parts,
    );
    expect(info.partName).toBe("Phần Đọc");
    expect(info.entries).toEqual([
      { key: "partCode", labelKey: "partCode", isExamContent: false, value: "P1" },
      { key: "reason", labelKey: "reason", isExamContent: true, value: "Regrade requested" },
      { key: "error", labelKey: "error", isExamContent: true, value: "Null pointer" },
      { key: "seq", labelKey: "seq", isExamContent: false, value: "5" },
      { key: "customExtra", labelKey: undefined, isExamContent: false, value: "abc" },
    ]);
  });
});
