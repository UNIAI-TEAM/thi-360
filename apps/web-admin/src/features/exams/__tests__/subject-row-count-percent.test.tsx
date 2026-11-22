import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CountPercentInput } from "../subject-forms/count-percent-input";

describe("count and percent raw input", () => {
  it("restores CR-2 count text by default with an accessible error", () => {
    renderWithProviders(<CountPercentInput label="Row" entry="COUNT_OR_PERCENT" total={40} count={12} fieldName="COUNT" rawText="abc" onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "Số câu" })).toHaveValue("abc");
    expect(screen.getByRole("textbox", { name: "Tỷ lệ (%)" })).toHaveValue("");
    expect(screen.getByRole("alert")).toHaveTextContent("Nhập một số không âm.");
  });

  it("restores percent text and the whole-set error", () => {
    renderWithProviders(<CountPercentInput label="Row" entry="COUNT_OR_PERCENT" total={40} count={12} setSize={4} fieldName="COUNT" rawText="25" rawSource="PERCENT" onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "Số câu" })).toHaveValue("12");
    expect(screen.getByRole("textbox", { name: "Tỷ lệ (%)" })).toHaveValue("25");
    expect(screen.getByRole("alert")).toHaveTextContent("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu");
  });

  it("reports the source only for an opted-in consumer", () => {
    const onRawInput = vi.fn();
    renderWithProviders(<CountPercentInput label="Row" entry="COUNT_OR_PERCENT" total={40} count={12} fieldName="COUNT" rawSource="COUNT" onChange={vi.fn()} onRawInput={onRawInput} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tỷ lệ (%)" }), { target: { value: "abc" } });
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "abc", code: "invalidNumber", source: "PERCENT" });
    fireEvent.change(screen.getByRole("textbox", { name: "Số câu" }), { target: { value: "abc" } });
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "abc", code: "invalidNumber", source: "COUNT" });
  });
});
