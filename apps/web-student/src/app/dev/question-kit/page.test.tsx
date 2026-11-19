import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import QuestionKitPage from "./page";
import { KIT_SAMPLES } from "./samples";

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_HTTP_ERROR_FALLBACK;404");
  },
}));

describe("trang thử /dev/question-kit", () => {
  it("gọi notFound() khi NODE_ENV = production (AC-18)", () => {
    vi.stubEnv("NODE_ENV", "production");
    expect(() => QuestionKitPage()).toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
  });

  it("ở dev hiện đủ 8 dạng câu và đổi được sang chế độ xem lại", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const user = userEvent.setup();
    renderWithProviders(<QuestionKitPage />);
    expect(document.querySelectorAll("[data-sample]")).toHaveLength(KIT_SAMPLES.length);
    expect(new Set(KIT_SAMPLES.map((sample) => sample.question.body.type)).size).toBe(8);
    expect(screen.getAllByTestId(/^value-/)).toHaveLength(KIT_SAMPLES.length);
    await user.click(screen.getByRole("button", { name: "Xem lại" }));
    expect(screen.queryAllByTestId(/^value-/)).toHaveLength(0);
    expect(document.querySelectorAll("textarea, input[type=radio], input[type=checkbox]")).toHaveLength(0);
  });

  it("giá trị trả ra hiện đúng định dạng LT 4 khi chọn đáp án", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const user = userEvent.setup();
    renderWithProviders(<QuestionKitPage />);
    const single = document.querySelector('[data-sample="single"]') as HTMLElement;
    await user.click(single.querySelectorAll("input[type=radio]")[0]!);
    expect(screen.getByTestId("value-single").textContent).toMatch(/^\{"optionId":"s\d"\}$/);
  });
});
