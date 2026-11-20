import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { RowSplitPanel } from "../subject-forms/row-split-panel";
import { setBlock } from "./subject-form-builders";

describe("row split draft", () => {
  it("restores invalid children after unmount and clears them on cancel", async () => {
    const user = userEvent.setup();
    const rawInputs = createRawInputStore();
    const props = { block: setBlock("read", 4, 10), units: 4, sectionId: "section", rawInputs, onApply: vi.fn(), onCancel: vi.fn() };
    const view = renderWithProviders(<RowSplitPanel {...props} />);
    const first = screen.getByRole("textbox", { name: "Số chùm của dòng con 1" });
    await user.clear(first);
    await user.type(first, "0");
    expect(rawInputs.hasRawInputs()).toBe(true);
    view.unmount();
    expect(rawInputs.hasRawInputs()).toBe(true);
    renderWithProviders(<RowSplitPanel {...props} />);
    expect(screen.getByRole("textbox", { name: "Số chùm của dòng con 1" })).toHaveValue("0");
    await user.click(screen.getByRole("button", { name: "Hủy" }));
    expect(rawInputs.hasRawInputs()).toBe(false);
    expect(props.onCancel).toHaveBeenCalledOnce();
  });
});
