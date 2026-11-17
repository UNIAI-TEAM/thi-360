import { act } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { RichTextEditor } from "../rich-text-editor";
import type * as Sanitize from "../sanitize";

const calls = vi.hoisted(() => ({ sanitize: 0, math: 0 }));
vi.mock("../sanitize", async (importOriginal) => {
  const actual = await importOriginal<typeof Sanitize>();
  return {
    ...actual,
    sanitizeQuestionHtml: (...args: Parameters<typeof actual.sanitizeQuestionHtml>) => {
      calls.sanitize += 1;
      return actual.sanitizeQuestionHtml(...args);
    },
    hasUnbalancedMath: (...args: Parameters<typeof actual.hasUnbalancedMath>) => {
      calls.math += 1;
      return actual.hasUnbalancedMath(...args);
    },
  };
});

describe("RichTextEditor per-value work", () => {
  it("re-renders with an unchanged value without re-sanitizing or re-scanning the math", async () => {
    const html = "<p>Một đoạn <span class=\"math-inline\" data-latex=\"x\"></span> dài</p>";
    const onChange = vi.fn();
    const { rerender } = renderWithProviders(<RichTextEditor value={html} onChange={onChange} />, {
      locale: "en",
      messages: en,
    });
    await act(async () => {});
    const before = { ...calls };

    rerender(<RichTextEditor value={html} onChange={onChange} className="other" />);
    rerender(<RichTextEditor value={html} onChange={onChange} className="another" invalid />);
    await act(async () => {});

    expect(calls.math).toBe(before.math);
    expect(calls.sanitize).toBe(before.sanitize);
  });
});
