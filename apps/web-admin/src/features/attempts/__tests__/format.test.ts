import { describe, expect, it } from "vitest";
import {
  extractEventDetailInfo,
  isIsoTimestamp,
  isKnownAttemptState,
  parseAttemptAnswerDisplay,
  stripHtmlToPlainText,
} from "../format";

describe("attempts format helpers", () => {
  it("decodes named & numeric HTML entities without double-unescaping (R1-42, R2-21)", () => {
    expect(
      stripHtmlToPlainText("<p>A &amp; B &lt; C &gt; D &quot;E&quot; &#39;F&#39;&nbsp;G</p>"),
    ).toBe('A & B < C > D "E" \'F\' G');
    expect(stripHtmlToPlainText("<p>&amp;lt;script&amp;gt;</p>")).toBe("&lt;script&gt;");
    expect(
      stripHtmlToPlainText("<p>X&#160;Y &#xA0; Z &#65; &#x42; &mdash; &ndash; &hellip; &amp;#65;</p>"),
    ).toBe("X Y Z A B — – … &#65;");
  });

  it("preserves original 1-based CLOZE blank indices including absent b1 and guards malformed blanks (R1-12, R1-13, R1-15, R2-20)", () => {
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

    const missingB1 = parseAttemptAnswerDisplay({
      itemId: "i-cloze-sparse",
      questionType: "CLOZE",
      answer: {
        type: "CLOZE",
        blanks: { b2: "Hà Nội" },
      },
    });
    expect(missingB1).toEqual({
      kind: "cloze",
      entries: [{ index: 2, value: "Hà Nội" }],
    });

    const unordered = parseAttemptAnswerDisplay({
      itemId: "i-cloze-sort",
      questionType: "CLOZE",
      answer: {
        type: "CLOZE",
        blanks: { b3: "Ba", "2": "Hai" },
      },
    });
    expect(unordered).toEqual({
      kind: "cloze",
      entries: [
        { index: 2, value: "Hai" },
        { index: 3, value: "Ba" },
      ],
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

  it("caps long ESSAY preview at 160 code points without splitting surrogate pairs (R1-42, R2-21)", () => {
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

    const emojiText = "😀".repeat(170);
    const emojiRes = parseAttemptAnswerDisplay({
      itemId: "i-emoji",
      questionType: "ESSAY",
      answer: {
        type: "ESSAY",
        html: `<p>${emojiText}</p>`,
        wordCount: 1,
      },
    });
    expect(emojiRes.kind).toBe("essay");
    if (emojiRes.kind === "essay") {
      expect(Array.from(emojiRes.preview).length).toBe(160);
      expect(emojiRes.preview.endsWith("…")).toBe(true);
      expect(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:^|[^\uD800-\uDBFF])[\uDC00-\uDFFF]/.test(emojiRes.preview)).toBe(false);
    }
  });

  it("formats MULTIPLE_CHOICE, TRUE_FALSE, MATCHING, QUESTION_SET, and state/ISO helpers (R2-15, R2-42)", () => {
    expect(
      parseAttemptAnswerDisplay({
        itemId: "i-mc",
        questionType: "MULTIPLE_CHOICE",
        answer: { type: "MULTIPLE_CHOICE", optionIds: ["o1", "o3"] },
      }),
    ).toEqual({ kind: "text", text: "o1, o3", isExamContent: false });

    expect(
      parseAttemptAnswerDisplay({
        itemId: "i-tf",
        questionType: "TRUE_FALSE",
        answer: { type: "TRUE_FALSE", statements: { s1: true, s2: false } },
      }),
    ).toEqual({
      kind: "trueFalse",
      entries: [
        { key: "s1", value: true },
        { key: "s2", value: false },
      ],
    });

    expect(
      parseAttemptAnswerDisplay({
        itemId: "i-match",
        questionType: "MATCHING",
        answer: { type: "MATCHING", pairs: { L1: "R2", L2: null as unknown as string, L3: "R1" } },
      }),
    ).toEqual({ kind: "text", text: "L1 → R2 · L3 → R1", isExamContent: false });

    expect(
      parseAttemptAnswerDisplay({
        itemId: "i-set",
        questionType: "QUESTION_SET",
        answer: { type: "QUESTION_SET", children: { q1: {}, q2: {} } } as never,
      }),
    ).toEqual({ kind: "notApplicable" });

    expect(isKnownAttemptState("SCORED")).toBe(true);
    expect(isKnownAttemptState("UNKNOWN")).toBe(false);
    expect(isIsoTimestamp("2026-11-17T02:20:00Z")).toBe(true);
    expect(isIsoTimestamp("not-a-date")).toBe(false);
  });

  it("flags known event fields including reopenedReason/comment, free-text fields, and unknown key fallback (R1-28, R1-29, R2-16)", () => {
    const parts = new Map([["p-1", "Phần Đọc"]]);
    const info = extractEventDetailInfo(
      {
        id: "ev-1",
        type: "REGRADED",
        partId: "p-1",
        data: {
          partCode: "P1",
          reason: "Regrade requested",
          reopenedReason: "Phúc khảo",
          comment: "Đã cộng điểm",
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
      { key: "reopenedReason", labelKey: "reopenedReason", isExamContent: true, value: "Phúc khảo" },
      { key: "comment", labelKey: "comment", isExamContent: true, value: "Đã cộng điểm" },
      { key: "error", labelKey: "error", isExamContent: true, value: "Null pointer" },
      { key: "seq", labelKey: "seq", isExamContent: false, value: "5" },
      { key: "customExtra", labelKey: undefined, isExamContent: false, value: "abc" },
    ]);
  });
});
