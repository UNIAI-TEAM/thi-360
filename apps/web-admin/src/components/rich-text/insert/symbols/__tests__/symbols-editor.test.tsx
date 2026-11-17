import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RichTextEditor } from "../../../rich-text-editor";

const t = en.richText.insertDialog.symbols;

describe("symbols dialog inside the editor", () => {
  it("opens from the toolbar, inserts into the editor without closing, and the content reaches onChange", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(<RichTextEditor value="" onChange={onChange} />, { locale: "en", messages: en });

    await user.click(screen.getByRole("button", { name: en.richText.insert.symbols.label }));
    const dialog = await screen.findByRole("dialog", { name: t.title }, { timeout: 4000 });
    await user.click(within(within(dialog).getByRole("group", { name: t.groups.units })).getByRole("button", { name: t.names.unitMetrePerSecondSquared }));
    await user.click(within(within(dialog).getByRole("group", { name: t.groups.greek })).getByRole("button", { name: t.names.greekOmega }));

    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith("<p>m/s<sup>2</sup>ω</p>"));
    expect(screen.getByRole("dialog", { name: t.title })).toBeTruthy();
  });
});
