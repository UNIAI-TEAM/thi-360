import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import type * as Ui from "@thi360/ui";
import { INSERT_REGISTRY, type InsertDialogModule } from "../insert/registry";
import { RichTextEditor } from "../rich-text-editor";

const ui = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock("@thi360/ui", async (importOriginal) => {
  const actual = await importOriginal<typeof Ui>();
  return { ...actual, notify: { ...actual.notify, error: ui.error } };
});

function entry(id: string) {
  const found = INSERT_REGISTRY.find((candidate) => candidate.id === id);
  if (!found) throw new Error(`no entry ${id}`);
  return found;
}

function renderEditor() {
  return renderWithProviders(<RichTextEditor value="" onChange={vi.fn()} />, { locale: "en", messages: en });
}

afterEach(() => {
  vi.restoreAllMocks();
  ui.error.mockReset();
});

describe("RichTextEditor insert dialog loading", () => {
  it("reports a failed dialog load with a translated error and opens nothing", async () => {
    const user = userEvent.setup();
    vi.spyOn(entry("symbols"), "load").mockRejectedValue(new Error("ChunkLoadError"));
    renderEditor();

    await user.click(screen.getByRole("button", { name: "Symbols and units" }));

    await waitFor(() => expect(ui.error).toHaveBeenCalledWith(en.richText.insertLoadError));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("opens the dialog of the last clicked entry when an earlier load resolves later", async () => {
    const user = userEvent.setup();
    const symbolsModule = await entry("symbols").load();
    const slow = Promise.withResolvers<InsertDialogModule>();
    vi.spyOn(entry("symbols"), "load").mockReturnValue(slow.promise);
    renderEditor();

    await user.click(screen.getByRole("button", { name: "Symbols and units" }));
    await user.click(screen.getByRole("button", { name: "Chemistry formula" }));
    expect(await screen.findByRole("dialog", { name: "Chemistry formula" })).toBeTruthy();

    // Lần tải chậm của lần bấm trước về sau cùng: không được thay hộp đang mở.
    slow.resolve(symbolsModule);
    await slow.promise;
    await Promise.resolve();
    expect(screen.getByRole("dialog", { name: "Chemistry formula" })).toBeTruthy();
    expect(screen.queryByRole("dialog", { name: "Symbols and units" })).toBeNull();
    expect(ui.error).not.toHaveBeenCalled();
  });
});
