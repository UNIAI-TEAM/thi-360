import { readFileSync } from "node:fs";
import { join } from "node:path";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createRichTextExtensions } from "../../../extensions";
import { stripTrailingEmptyParagraph } from "../../../html-contract";
import { sanitizeQuestionHtml } from "../../../sanitize";
import { INSERT_REGISTRY } from "../../registry";
import CrossInsertDialog from "../dialog";

const t = en.richText.insertDialog.cross;

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: { name: string; html: string }[] };
const crossSample = fixture.samples.find((sample) => sample.name === "cross-diagram")!.html;

const mounted: { editor: Editor; element: HTMLElement }[] = [];

function createEditor(content = "<p></p>"): Editor {
  const element = document.createElement("div");
  document.body.append(element);
  const editor = new Editor({ element, extensions: createRichTextExtensions(), content });
  mounted.push({ editor, element });
  return editor;
}

function emitted(editor: Editor): string {
  return stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML(), { allowImages: true }).html);
}

function renderDialog(editor: Editor) {
  const onOpenChange = vi.fn();
  renderWithProviders(<CrossInsertDialog editor={editor} open={true} onOpenChange={onOpenChange} editing={null} />, {
    locale: "en",
    messages: en,
  });
  return { onOpenChange };
}

const field = (label: string) => screen.getByLabelText(label) as HTMLInputElement;
const insertButton = () => screen.getByRole("button", { name: t.insert });

async function fill(user: UserEvent, label: string, value: string) {
  const input = field(label);
  await user.clear(input);
  await user.click(input);
  await user.paste(value);
}

async function fillFixture(user: UserEvent) {
  await fill(user, t.fields.genotypeA, "AaBb");
  await fill(user, t.fields.genotypeB, "aabb");
  await fill(user, t.fields.gametesA, "AB, Ab, aB, ab");
  await fill(user, t.fields.gametesB, "ab");
  await fill(user, t.fields.filialGenotype.replace("{n}", "1"), "1 AaBb : 1 Aabb : 1 aaBb : 1 aabb");
}

afterEach(() => {
  for (const { editor, element } of mounted.splice(0)) {
    editor.destroy();
    element.remove();
  }
});

describe("cross dialog: output", () => {
  it("emits exactly the cross-diagram fixture", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    const { onOpenChange } = renderDialog(editor);
    await fillFixture(user);

    await user.click(insertButton());

    expect(emitted(editor)).toBe(crossSample);
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("shows the preview rows as the user types, with subscripts", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    await fillFixture(user);

    const preview = within(screen.getByTestId("cross-preview"));
    expect(preview.getByText("♂ AaBb × ♀ aabb")).toBeTruthy();
    expect(preview.getByText("AB, Ab, aB, ab ; ab")).toBeTruthy();
    expect(screen.getByTestId("cross-preview").querySelector("dt sub")?.textContent).toBe("1");
  });

  it("keeps Insert disabled until some cell has text", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    expect(insertButton()).toBeDisabled();
    await fill(user, t.fields.genotypeA, "Aa");
    expect(insertButton()).toBeEnabled();
  });

  it("refuses a cell that would read back into the wrong place, and says which cell", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    await fill(user, t.fields.genotypeA, "Aa");

    await fill(user, t.fields.gametesA, "A; a");
    expect(insertButton()).toBeDisabled();
    expect(field(t.fields.gametesA)).toBeInvalid();
    expect(screen.getByRole("alert")).toHaveTextContent(
      t.errors.separator.replace("{field}", t.fields.gametesA),
    );

    await fill(user, t.fields.gametesA, "A, a");
    await fill(user, t.fields.genotypeB, "a × a");
    expect(insertButton()).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(t.errors.parent.replace("{field}", t.fields.genotypeB));

    await fill(user, t.fields.genotypeB, "aa");
    expect(screen.queryByRole("alert")).toBeNull();
    expect(insertButton()).toBeEnabled();
  });

  it("changes the sex shown for a parent, or hides it", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);
    await fill(user, t.fields.genotypeA, "Aa");
    await fill(user, t.fields.genotypeB, "aa");

    await user.click(screen.getByRole("combobox", { name: t.fields.sexA }));
    await user.click(await screen.findByRole("option", { name: "♀" }));
    await user.click(screen.getByRole("combobox", { name: t.fields.sexB }));
    await user.click(await screen.findByRole("option", { name: t.sexNone }));
    await user.click(insertButton());

    expect(emitted(editor)).toContain("<td>♀ Aa × aa</td>");
  });
});

describe("cross dialog: generations and scripts", () => {
  it("adds F2 and F3, and drops the empty ones from the output", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);
    await fillFixture(user);

    await user.click(screen.getByRole("button", { name: t.addGeneration }));
    await user.click(screen.getByRole("button", { name: t.addGeneration }));
    await fill(user, t.fields.filialGenotype.replace("{n}", "2"), "9 : 3 : 3 : 1");
    await fill(user, t.fields.filialPhenotype.replace("{n}", "2"), "9 A-B- : 1 aabb");
    await user.click(insertButton());

    const html = emitted(editor);
    expect(html).toContain('<th scope="row">F<sub>2</sub></th><td>9 : 3 : 3 : 1 ; 9 A-B- : 1 aabb</td>');
    expect(html).not.toContain("F<sub>3</sub>");
  });

  it("removes a generation from F2 on", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    await user.click(screen.getByRole("button", { name: t.addGeneration }));
    expect(screen.queryByRole("button", { name: t.removeGeneration.replace("{n}", "1") })).toBeNull();
    await user.click(screen.getByRole("button", { name: t.removeGeneration.replace("{n}", "2") }));
    expect(screen.queryByLabelText(t.fields.filialGenotype.replace("{n}", "2"))).toBeNull();
  });

  it("types _ and ^ in a cell as sub and sup", async () => {
    const user = userEvent.setup();
    const editor = createEditor();
    renderDialog(editor);
    await fill(user, t.fields.genotypeA, "X^AX^a");
    await fill(user, t.fields.filialGenotype.replace("{n}", "1"), "1 F_1 : 2 A_{12}");
    await user.click(insertButton());

    const html = emitted(editor);
    expect(html).toContain("<td>♂ X<sup>A</sup>X<sup>a</sup></td>");
    expect(html).toContain("<td>1 F<sub>1</sub> : 2 A<sub>12</sub></td>");
  });

  it("inserts _ or ^ at the caret of the cell that was being typed in", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor());
    await fill(user, t.fields.gametesB, "XY");
    field(t.fields.gametesB).setSelectionRange(1, 1);
    await user.click(field(t.fields.gametesB));
    field(t.fields.gametesB).setSelectionRange(1, 1);

    await user.click(screen.getByRole("button", { name: t.subscript }));
    expect(field(t.fields.gametesB).value).toBe("X_Y");
    expect(field(t.fields.gametesB).selectionStart).toBe(2);
    expect(document.activeElement).toBe(field(t.fields.gametesB));

    await user.click(screen.getByRole("button", { name: t.superscript }));
    expect(field(t.fields.gametesB).value).toBe("X_^Y");
  });
});

describe("cross dialog: reopen", () => {
  it("is routed by the registry as true for a cross node", () => {
    const entry = INSERT_REGISTRY.find((candidate) => candidate.id === "cross")!;
    const editor = createEditor(crossSample);
    expect(entry.openForSelection(editor.state.doc.firstChild!)).toBe(true);
  });

  it("restores the state from the saved HTML and replaces the same node on update", async () => {
    const user = userEvent.setup();
    const editor = createEditor(`<p>before</p>${crossSample}<p>after</p>`);
    editor.commands.setNodeSelection(editor.state.doc.firstChild!.nodeSize);
    renderDialog(editor);

    expect(field(t.fields.genotypeA).value).toBe("AaBb");
    expect(field(t.fields.genotypeB).value).toBe("aabb");
    expect(field(t.fields.gametesA).value).toBe("AB, Ab, aB, ab");
    expect(field(t.fields.gametesB).value).toBe("ab");
    expect(field(t.fields.filialGenotype.replace("{n}", "1")).value).toBe("1 AaBb : 1 Aabb : 1 aaBb : 1 aabb");
    expect(screen.getByRole("combobox", { name: t.fields.sexA })).toHaveTextContent("♂");

    await fill(user, t.fields.gametesB, "ab, ab");
    await user.click(screen.getByRole("button", { name: t.addGeneration }));
    await fill(user, t.fields.filialGenotype.replace("{n}", "2"), "x");
    await user.click(screen.getByRole("button", { name: t.update }));

    expect(emitted(editor)).toBe(
      "<p>before</p>" +
        '<figure class="cross"><table><tbody>' +
        '<tr><th scope="row">P</th><td>♂ AaBb × ♀ aabb</td></tr>' +
        '<tr><th scope="row">G</th><td>AB, Ab, aB, ab ; ab, ab</td></tr>' +
        '<tr><th scope="row">F<sub>1</sub></th><td>1 AaBb : 1 Aabb : 1 aaBb : 1 aabb</td></tr>' +
        '<tr><th scope="row">F<sub>2</sub></th><td>x</td></tr>' +
        "</tbody></table></figure><p>after</p>",
    );
  });

  it("reopens with no change and emits the same fixture", async () => {
    const user = userEvent.setup();
    const editor = createEditor(crossSample);
    editor.commands.setNodeSelection(0);
    renderDialog(editor);

    await user.click(screen.getByRole("button", { name: t.update }));

    expect(emitted(editor)).toBe(crossSample);
  });
});

describe("cross dialog: placement", () => {
  it("inserts after the top-level block that holds the cursor, not inside a list", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<ul><li><p>item</p></li></ul>");
    editor.commands.setTextSelection(4);
    renderDialog(editor);
    await fill(user, t.fields.genotypeA, "Aa");

    await user.click(insertButton());

    expect(emitted(editor)).toMatch(/^<ul><li><p>item<\/p><\/li><\/ul><figure class="cross">/);
  });
});
