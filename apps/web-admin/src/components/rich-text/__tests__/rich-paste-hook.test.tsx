import { fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { RichTextEditor } from "../rich-text-editor";
import type * as Sanitize from "@thi360/ui/rich-text";

// Đếm số lần chạy bộ lọc: `onRichPaste` phải chạy trước lần lọc do việc dán gây ra (spec 4.10).
const sanitizeCalls = vi.hoisted(() => ({ count: 0 }));
vi.mock("@thi360/ui/rich-text", async (importOriginal) => {
  const actual = await importOriginal<typeof Sanitize>();
  return {
    ...actual,
    sanitizeQuestionHtml: (...args: Parameters<typeof actual.sanitizeQuestionHtml>) => {
      sanitizeCalls.count += 1;
      return actual.sanitizeQuestionHtml(...args);
    },
  };
});

// Chú thích kiểu Word chứa OMML và thẻ bị bộ lọc bỏ: chỉ HTML thô mới còn nguyên.
const WORD_HTML = '<p>Câu 1</p><!--[if gte msEquation 12]><m:oMath>x</m:oMath><![endif]--><script>alert(1)</script>';

function paste(onRichPaste: (data: DataTransfer | null) => boolean) {
  const onChange = vi.fn();
  renderWithProviders(<RichTextEditor value="" onChange={onChange} onRichPaste={onRichPaste} />, {
    locale: "en",
    messages: en,
  });
  const target = document.querySelector<HTMLElement>(".ProseMirror")!;
  target.focus();
  const transfer = {
    getData: (format: string) => (format === "text/html" ? WORD_HTML : ""),
    types: ["text/html"],
    files: [],
    items: [],
  };
  const sanitizeBefore = sanitizeCalls.count;
  fireEvent.paste(target, { clipboardData: transfer });
  return { onChange, target, transfer, sanitizeBefore };
}

describe("RichTextEditor onRichPaste", () => {
  it("runs before the sanitize step and sees the raw, unsanitized HTML", () => {
    let sanitizeSeenByHook = -1;
    let htmlSeenByHook = "";
    const hook = vi.fn((data: DataTransfer | null) => {
      sanitizeSeenByHook = sanitizeCalls.count;
      htmlSeenByHook = data?.getData("text/html") ?? "";
      return false;
    });
    const { sanitizeBefore, target } = paste(hook);

    expect(hook).toHaveBeenCalledTimes(1);
    expect(htmlSeenByHook).toBe(WORD_HTML);
    expect(sanitizeSeenByHook).toBe(sanitizeBefore);
    // Trả false: dán mặc định vẫn chạy, và bộ lọc chỉ chạy sau hook.
    expect(sanitizeCalls.count).toBeGreaterThan(sanitizeBefore);
    expect(target.textContent).toContain("Câu 1");
    expect(target.innerHTML).not.toContain("script");
  });

  it("returning true takes over the paste: nothing is inserted and the sanitize step never runs", () => {
    const hook = vi.fn(() => true);
    const { onChange, target, sanitizeBefore } = paste(hook);

    expect(hook).toHaveBeenCalledTimes(1);
    expect(target.textContent).toBe("");
    expect(onChange).not.toHaveBeenCalled();
    expect(sanitizeCalls.count).toBe(sanitizeBefore);
  });
});
