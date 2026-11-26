import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichTextView } from "../../rich-text-view";

describe("RichTextView broken block formula fallback", () => {
  it("renders a warning frame for broken block LaTeX while keeping surrounding paragraphs", () => {
    const { container } = render(
      <RichTextView html={'<p>Trước</p><span class="math-block">\\[\\frac{1}{\\]</span><p>Sau</p>'} />,
    );
    const error = container.querySelector("span.math-error");
    expect(error).toBeTruthy();
    expect(error!.textContent).toBe("\\[\\frac{1}{\\]");
    const paragraphs = [...container.querySelectorAll("p")].map((p) => p.textContent);
    expect(paragraphs).toEqual(["Trước", "Sau"]);
  });
});
