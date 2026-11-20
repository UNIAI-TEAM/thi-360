import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import { RowSplitPanel } from "../subject-forms/row-split-panel";
import { block, setBlock } from "./subject-form-builders";

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

  it("renders set and question split labels and validation messages in vi and en", async () => {
    const user = userEvent.setup();
    const enSet = renderWithProviders(
      <RowSplitPanel block={setBlock("read", 4, 10)} units={4} sectionId="s-set" rawInputs={createRawInputStore()} onApply={vi.fn()} onCancel={vi.fn()} />,
      { locale: "en", messages: en },
    );
    expect(screen.getByText(/The parent row \(4 sets\) is replaced/)).toBeInTheDocument();
    const setChild = screen.getByRole("textbox", { name: "Sets of child row 1" });
    await user.clear(setChild);
    await user.type(setChild, "0");
    expect(screen.getByText("Child row 1 needs at least 1 set")).toBeInTheDocument();
    expect(screen.getByText("The child rows add up to 2, the parent row has 4 sets")).toBeInTheDocument();
    enSet.unmount();

    renderWithProviders(
      <RowSplitPanel block={block("single", { questionCount: 10 })} units={10} sectionId="s-single" rawInputs={createRawInputStore()} onApply={vi.fn()} onCancel={vi.fn()} />,
      { locale: "en", messages: en },
    );
    expect(screen.getByText(/The parent row \(10 questions\) is replaced/)).toBeInTheDocument();
    const singleChild = screen.getByRole("textbox", { name: "Questions of child row 1" });
    await user.clear(singleChild);
    await user.type(singleChild, "0");
    expect(screen.getByText("Child row 1 needs at least 1 question")).toBeInTheDocument();
    expect(screen.getByText("The child rows add up to 5, the parent row has 10 questions")).toBeInTheDocument();
  });

  it("serializes raw split texts with JSON so '1 + 2' survives unmount without adding a third input", async () => {
    const user = userEvent.setup();
    const rawInputs = createRawInputStore();
    const props = { block: setBlock("read", 4, 10), units: 4, sectionId: "s-json", rawInputs, onApply: vi.fn(), onCancel: vi.fn() };
    const view = renderWithProviders(<RowSplitPanel {...props} />);
    const first = screen.getByRole("textbox", { name: "Số chùm của dòng con 1" });
    await user.clear(first);
    await user.type(first, "1 + 2");
    view.unmount();

    renderWithProviders(<RowSplitPanel {...props} />);
    const inputs = screen.getAllByRole("textbox");
    expect(inputs).toHaveLength(2);
    expect(inputs[0]).toHaveValue("1 + 2");
  });

  it("resyncs child inputs when units changes while open and links error descriptions via aria-describedby", async () => {
    const user = userEvent.setup();
    const rawInputs = createRawInputStore();
    const view = renderWithProviders(
      <RowSplitPanel block={setBlock("read", 4, 10)} units={4} sectionId="s-sync" rawInputs={rawInputs} onApply={vi.fn()} onCancel={vi.fn()} />,
    );
    const first = screen.getByRole("textbox", { name: "Số chùm của dòng con 1" });
    await user.clear(first);
    await user.type(first, "0");
    expect(first).toHaveAccessibleDescription("Dòng con 1 phải có ít nhất 1 chùm Tổng các dòng con là 2, dòng cha có 4 chùm");
    expect(rawInputs.hasRawInputs()).toBe(true);

    view.rerender(<RowSplitPanel block={setBlock("read", 3, 10)} units={3} sectionId="s-sync" rawInputs={rawInputs} onApply={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "Số chùm của dòng con 1" })).toHaveValue("2");
    expect(screen.getByRole("textbox", { name: "Số chùm của dòng con 2" })).toHaveValue("1");
    expect(rawInputs.hasRawInputs()).toBe(false);
  });
});
