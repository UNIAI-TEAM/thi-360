import { SESSION_QUERY_KEY } from "@/features/session/api";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { QueryClient } from "@tanstack/react-query";
import { cleanup, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it, vi as vitest } from "vitest";
import { createRichTextExtensions } from "../../../extensions";
import { INSERT_REGISTRY } from "../../registry";
import SymbolsInsertDialog from "../dialog";
import { SYMBOLS, symbolText } from "../symbol-data";

const t = en.richText.insertDialog.symbols;
const names = t.names as Record<string, string>;

const mounted: { editor: Editor; element: HTMLElement }[] = [];

function createEditor(content = "<p></p>"): Editor {
  const element = document.createElement("div");
  document.body.append(element);
  const editor = new Editor({ element, extensions: createRichTextExtensions(), content });
  mounted.push({ editor, element });
  return editor;
}

function renderDialog(editor: Editor, options: { client?: QueryClient; locale?: "en" | "vi" } = {}) {
  const onOpenChange = vitest.fn();
  const locale = options.locale ?? "en";
  renderWithProviders(
    <SymbolsInsertDialog editor={editor} open={true} onOpenChange={onOpenChange} editing={null} />,
    { locale, messages: locale === "en" ? en : vi, client: options.client },
  );
  return { onOpenChange };
}

/** Nút của một nhóm theo tên nhóm (một ký hiệu có thể có mặt ở "Dùng gần đây" và nhóm gốc). */
function inGroup(group: string, name: string) {
  return within(screen.getByRole("group", { name: group })).getByRole("button", { name });
}

afterEach(() => {
  for (const { editor, element } of mounted.splice(0)) {
    editor.destroy();
    element.remove();
  }
  window.localStorage.clear();
});

describe("symbols dialog: inserting", () => {
  it("inserts the exact characters and stays open after every click", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    const { onOpenChange } = renderDialog(editor);

    await user.click(inGroup(t.groups.greek, names.greekAlpha!));
    await user.click(inGroup(t.groups.operators, names.lessOrEqual!));
    await user.click(inGroup(t.groups.arrows, names.arrowReversible!));
    await user.click(inGroup(t.groups.ipa, names.ipaLongA!));

    expect(editor.getHTML()).toBe("<p>α≤⇌ɑː</p>");
    expect(onOpenChange).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: t.title })).toBeTruthy();
  });

  it("covers every symbol of the design with the characters it displays", () => {
    const characters = SYMBOLS.map(symbolText);
    expect(characters).toHaveLength(100);
    for (const expected of ["α", "Ω", "±", "∠", "°", "⇔", "⇌", "m/s2", "W/m2", "°C", "μF", "♂", "F1", "XA", "Xa", "5′", "3′", "iː", "tʃ", "dʒ", "ˈ", "ˌ"]) {
      expect(characters).toContain(expected);
    }
  });

  it("inserts units and biology entries with real sup/sub marks, not Unicode scripts", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);

    await user.click(inGroup(t.groups.units, names.unitMetrePerSecondSquared!));
    await user.click(inGroup(t.groups.units, names.unitWattPerSquareMetre!));
    await user.click(inGroup(t.groups.biology, names.bioFilial1!));
    await user.click(inGroup(t.groups.biology, names.bioAlleleXA!));

    expect(editor.getHTML()).toBe("<p>m/s<sup>2</sup>W/m<sup>2</sup>F<sub>1</sub>X<sup>A</sup></p>");
    expect(editor.getText()).not.toMatch(/[²₁ᴬ]/);
  });

  it("does not carry the superscript on to text typed after a unit", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);

    await user.click(inGroup(t.groups.units, names.unitMetrePerSecondSquared!));
    editor.commands.insertContent("x");

    expect(editor.getHTML()).toBe("<p>m/s<sup>2</sup>x</p>");
  });

  it("replaces a selected text range with the symbol", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p>abc</p>");
    editor.commands.setTextSelection({ from: 2, to: 3 });
    renderDialog(editor);

    await user.click(inGroup(t.groups.greek, names.greekMu!));

    expect(editor.getHTML()).toBe("<p>aμc</p>");
  });

  it("inserts after a selected formula instead of replacing it", async () => {
    const user = userEvent.setup();
    const editor = createEditor('<p><span class="math-inline">\\(x^2\\)</span></p>');
    editor.commands.setNodeSelection(1);
    renderDialog(editor);

    await user.click(inGroup(t.groups.operators, names.degree!));

    const html = editor.getHTML();
    expect(html).toContain("\\(x^2\\)");
    expect(html).toContain("°");
  });

  it("closes only from the Close button", async () => {
    const user = userEvent.setup();
    const { onOpenChange } = renderDialog(createEditor());

    await user.click(screen.getAllByRole("button", { name: t.close }).at(-1)!);

    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("offers every symbol group in the design order", () => {
    renderDialog(createEditor());
    const headings = screen.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent);
    expect(headings).toEqual([
      t.groups.greek,
      t.groups.operators,
      t.groups.arrows,
      t.groups.units,
      t.groups.biology,
      t.groups.ipa,
    ]);
  });
});

describe("symbols dialog: search", () => {
  it("finds a symbol by its English name while the UI is Vietnamese, and by its Vietnamese name in English", async () => {
    const user = userEvent.setup();
    const viSymbols = vi.richText.insertDialog.symbols;
    renderDialog(createEditor(), { locale: "vi" });
    await user.type(screen.getByRole("searchbox", { name: viSymbols.searchLabel }), "ohm");
    expect(screen.getByRole("button", { name: viSymbols.names.unitOhm })).toBeTruthy();
    expect(screen.queryByRole("button", { name: viSymbols.names.unitNewton })).toBeNull();
    cleanup();

    renderDialog(createEditor());
    await user.type(screen.getByRole("searchbox", { name: t.searchLabel }), "ôm");
    expect(screen.getByRole("button", { name: names.unitOhm })).toBeTruthy();
    expect(screen.queryByRole("button", { name: names.unitNewton })).toBeNull();
  });

  it("matches Vietnamese names with or without diacritics and any case", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    const box = screen.getByRole("searchbox", { name: t.searchLabel });

    await user.type(box, "độ C");
    expect(screen.getByRole("button", { name: names.unitCelsius })).toBeTruthy();
    expect(screen.queryByRole("button", { name: names.unitOhm })).toBeNull();

    await user.clear(box);
    await user.type(box, "MUI TEN");
    for (const id of ["arrowRight", "arrowLeft", "arrowUp", "arrowDown"]) {
      expect(screen.getByRole("button", { name: names[id] })).toBeTruthy();
    }
    expect(screen.queryByRole("button", { name: names.greekAlpha })).toBeNull();
  });

  it("finds symbols by an accented Vietnamese query while the UI is English", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    const box = screen.getByRole("searchbox", { name: t.searchLabel });

    await user.type(box, "góc");
    expect(screen.getByRole("button", { name: names.angle })).toBeTruthy();
    expect(screen.getByRole("button", { name: names.perpendicular })).toBeTruthy();
    expect(screen.queryByRole("button", { name: names.greekAlpha })).toBeNull();

    await user.clear(box);
    await user.type(box, "Đơn vị mét trên giây");
    expect(screen.getByRole("button", { name: names.unitMetrePerSecond })).toBeTruthy();
    expect(screen.queryByRole("button", { name: names.unitOhm })).toBeNull();
  });

  it("announces an empty result in a status region that was already in the page", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();

    await user.type(screen.getByRole("searchbox", { name: t.searchLabel }), "zzzz");

    expect(screen.getByRole("status")).toBe(status);
    expect(status).toHaveTextContent(t.noResults);

    await user.clear(screen.getByRole("searchbox", { name: t.searchLabel }));
    expect(status).toBeEmptyDOMElement();
  });

  it("matches English names and the character itself, and says so when nothing matches", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    const box = screen.getByRole("searchbox", { name: t.searchLabel });

    await user.type(box, "alpha");
    expect(screen.getByRole("button", { name: names.greekAlpha })).toBeTruthy();
    expect(screen.queryByRole("button", { name: names.greekBeta })).toBeNull();

    await user.clear(box);
    await user.type(box, "ω");
    expect(screen.getByRole("button", { name: names.greekOmega })).toBeTruthy();

    await user.clear(box);
    await user.type(box, "zzzz");
    expect(screen.getByRole("status")).toHaveTextContent(t.noResults);
  });

  it("still inserts from a filtered result", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);

    await user.type(screen.getByRole("searchbox", { name: t.searchLabel }), "celsius");
    await user.click(screen.getByRole("button", { name: names.unitCelsius }));

    expect(editor.getHTML()).toBe("<p>°C</p>");
  });
});

describe("symbols dialog: recent", () => {
  it("lists the symbols just used first, newest on top, per user", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, { id: "user-1" });
    renderDialog(createEditor(), { client });
    expect(screen.queryByRole("group", { name: t.groups.recent })).toBeNull();

    await user.click(inGroup(t.groups.greek, names.greekAlpha!));
    await user.click(inGroup(t.groups.arrows, names.arrowRight!));
    await user.click(inGroup(t.groups.greek, names.greekAlpha!));

    const recent = within(screen.getByRole("group", { name: t.groups.recent })).getAllByRole("button");
    expect(recent.map((button) => button.getAttribute("aria-label"))).toEqual([names.greekAlpha, names.arrowRight]);
  });

  it("restores recent symbols for the same user on the next open and has none for others", () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, { id: "user-1" });
    window.localStorage.setItem("thi360-rich-text-recent:user-1:symbols", JSON.stringify(["greekPi", "gone"]));
    renderDialog(createEditor(), { client });
    const recent = within(screen.getByRole("group", { name: t.groups.recent })).getAllByRole("button");
    expect(recent.map((button) => button.getAttribute("aria-label"))).toEqual([names.greekPi]);
  });

  it("shows no recent group without a session", () => {
    renderDialog(createEditor());
    expect(screen.queryByRole("group", { name: t.groups.recent })).toBeNull();
  });
});

describe("symbols registry entry", () => {
  it("is not reopened from the selection (symbols are plain text)", () => {
    const entry = INSERT_REGISTRY.find((candidate) => candidate.id === "symbols")!;
    const editor = createEditor("<p>abc</p>");
    expect(entry.openForSelection(editor.state.doc.firstChild!)).toBeNull();
  });
});
