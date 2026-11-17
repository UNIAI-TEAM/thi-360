import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { QueryClient } from "@tanstack/react-query";
import { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createRichTextExtensions, MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../../../extensions";
import { stripTrailingEmptyParagraph } from "../../../html-contract";
import { sanitizeQuestionHtml } from "../../../sanitize";
import { INSERT_REGISTRY } from "../../registry";
import ChemistryInsertDialog from "../dialog";

const t = en.richText.insertDialog.chemistry;

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: { name: string; html: string }[] };
const chemistrySample = fixture.samples.find((sample) => sample.name === "chemistry-mhchem")!.html;

const mounted: { editor: Editor; element: HTMLElement }[] = [];

function createEditor(content = ""): Editor {
  const element = document.createElement("div");
  document.body.append(element);
  const editor = new Editor({ element, extensions: createRichTextExtensions(), content });
  mounted.push({ editor, element });
  return editor;
}

/** HTML đi qua đường `onChange` của trình soạn: getHTML → lọc → bỏ đoạn rỗng cuối. */
function emitted(editor: Editor): string {
  return stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML(), { allowImages: true }).html);
}

function selectFirstMath(editor: Editor) {
  let found = -1;
  editor.state.doc.descendants((node, pos) => {
    if (found === -1 && (node.type.name === MATH_INLINE_NODE || node.type.name === MATH_BLOCK_NODE)) found = pos;
  });
  editor.commands.setNodeSelection(found);
}

function renderDialog(editor: Editor, client?: QueryClient) {
  const onOpenChange = vi.fn();
  renderWithProviders(
    <ChemistryInsertDialog editor={editor} open={true} onOpenChange={onOpenChange} editing={null} />,
    { locale: "en", messages: en, client },
  );
  return { onOpenChange };
}

async function fill(user: UserEvent, label: string, value: string) {
  const input = screen.getByLabelText(label);
  await user.clear(input);
  if (value) {
    await user.click(input);
    await user.paste(value);
  }
}

const insertButton = () => screen.getByRole("button", { name: t.insert });

afterEach(() => {
  for (const { editor, element } of mounted.splice(0)) {
    editor.destroy();
    element.remove();
  }
  window.localStorage.clear();
});

describe("chemistry dialog: quick typing", () => {
  it("inserts the typed mhchem text unchanged as \\ce{…}", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    const { onOpenChange } = renderDialog(editor);

    await fill(user, t.quickLabel, "Fe^3+ + 3OH- -> Fe(OH)3 v");
    await user.click(insertButton());

    expect(emitted(editor)).toBe(
      '<p><span class="math-inline">\\(\\ce{Fe^3+ + 3OH- -&gt; Fe(OH)3 v}\\)</span></p>',
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("emits the chemistry-mhchem fixture: equation plus the note as a separate inline formula", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "CaCO3(s) <=> CaO(s) + CO2(g)");
    await fill(user, t.afterLabel, "\\Delta_r H^\\circ_{298} = 179,2\\ \\mathrm{kJ}");
    await user.click(insertButton());

    expect(emitted(editor)).toBe(chemistrySample);
  });

  it("keeps Insert disabled for an empty or invalid equation and for an invalid note", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);
    expect(insertButton()).toBeDisabled();

    await fill(user, t.quickLabel, "H2O");
    expect(insertButton()).toBeEnabled();

    await fill(user, t.quickLabel, "Fe^{3+");
    expect(insertButton()).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(t.errors.equation);

    await fill(user, t.quickLabel, "H2O");
    await fill(user, t.afterLabel, "\\badcmd{x}");
    expect(insertButton()).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(t.errors.note);
  });
});

describe("chemistry dialog: arrow conditions", () => {
  it("attaches above and below conditions to the first arrow only", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "C6H12O6 -> 2C2H5OH + 2CO2 -> x");
    await fill(user, t.topLabel, "enzyme");
    await fill(user, t.bottomLabel, "30 °C");
    await user.click(insertButton());

    expect(emitted(editor)).toBe(
      '<p><span class="math-inline">\\(\\ce{C6H12O6 -&gt;[enzyme][30 °C] 2C2H5OH + 2CO2 -&gt; x}\\)</span></p>',
    );
  });

  it("writes only the lower condition as ->[][below] and still renders", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "A <=> B");
    await fill(user, t.bottomLabel, "cat.");

    expect(insertButton()).toBeEnabled();
    await user.click(insertButton());
    expect(emitted(editor)).toContain("\\ce{A &lt;=&gt;[][cat.] B}");
  });

  it("ignores conditions when the text has no arrow, and replaces ones typed by hand when a field is filled", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "H2O");
    await fill(user, t.topLabel, "t°");
    await user.click(insertButton());
    expect(emitted(editor)).toBe('<p><span class="math-inline">\\(\\ce{H2O}\\)</span></p>');
  });
});

describe("chemistry dialog: buttons", () => {
  it("inserts the mhchem fragment at the caret and keeps the caret after it", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    const quick = screen.getByLabelText(t.quickLabel) as HTMLInputElement;
    await fill(user, t.quickLabel, "H2O");
    quick.setSelectionRange(1, 1);
    await user.click(screen.getByRole("button", { name: t.buttons.chargeTwoPlus }));
    expect(quick.value).toBe("H^2+2O");
    expect(quick.selectionStart).toBe(4);

    await fill(user, t.quickLabel, "Fe");
    await user.click(screen.getByRole("button", { name: t.buttons.concentration }));
    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).value).toBe("Fe[]");
    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).selectionStart).toBe(3);

    await user.click(screen.getByRole("button", { name: t.buttons.arrowReversible }));
    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).value).toContain("<=>");
  });

  it("puts the enthalpy button's LaTeX into the note field, not the equation", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await user.click(screen.getByRole("button", { name: t.buttons.enthalpy }));
    expect((screen.getByLabelText(t.afterLabel) as HTMLInputElement).value).toBe("\\Delta_r H^\\circ_{298} = ");
    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).value).toBe("");
  });

  it("renders the preview with KaTeX and mhchem", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "H2SO4");
    await waitFor(() => expect(within(screen.getByTestId("chemistry-preview")).getByText("H", { selector: ".mord" })).toBeTruthy());
    expect(screen.getByTestId("chemistry-preview").querySelector(".katex")).toBeTruthy();
    expect(screen.getByTestId("chemistry-preview").querySelector(".math-error")).toBeNull();
  });
});

describe("chemistry dialog: display and placement", () => {
  it("inserts inline by default and as a block formula with the note in the next paragraph", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p></p>");
    renderDialog(editor);

    await fill(user, t.quickLabel, "A -> B");
    await fill(user, t.afterLabel, "x = 1");
    await user.click(screen.getByRole("button", { name: t.block }));
    await user.click(insertButton());

    expect(emitted(editor)).toBe(
      '<span class="math-block">\\[\\ce{A -&gt; B}\\]</span><p><span class="math-inline">\\(x = 1\\)</span></p>',
    );
  });

  it("does not replace a plain math formula that is selected: inserts after it", async () => {
    const user = userEvent.setup();
    const editor = createEditor('<p><span class="math-inline">\\(x^2\\)</span></p>');
    selectFirstMath(editor);
    renderDialog(editor);

    await fill(user, t.quickLabel, "H2O");
    await user.click(insertButton());

    const html = emitted(editor);
    expect(html).toContain("\\(x^2\\)");
    expect(html).toContain("\\(\\ce{H2O}\\)");
  });
});

describe("chemistry dialog: reopen", () => {
  it("is routed by the registry as true for a \\ce formula and the dialog reads the selected node itself", () => {
    const entry = INSERT_REGISTRY.find((candidate) => candidate.id === "chemistry")!;
    const editor = createEditor('<p><span class="math-inline">\\(\\ce{H2O}\\)</span></p>');
    let node = editor.state.doc.firstChild!.firstChild!;
    expect(entry.openForSelection(node)).toBe(true);
    node = editor.state.doc.firstChild!;
    expect(entry.openForSelection(node)).toBeNull();
  });

  it("splits the first arrow's conditions back into the Above and Below fields, replaces the node on update", async () => {
    const user = userEvent.setup();
    const editor = createEditor(
      '<p>a <span class="math-inline">\\(\\ce{C6H12O6 -&gt;[enzyme][30 °C] 2C2H5OH + 2CO2}\\)</span> b</p>',
    );
    selectFirstMath(editor);
    renderDialog(editor);

    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).value).toBe("C6H12O6 -> 2C2H5OH + 2CO2");
    expect((screen.getByLabelText(t.topLabel) as HTMLInputElement).value).toBe("enzyme");
    expect((screen.getByLabelText(t.bottomLabel) as HTMLInputElement).value).toBe("30 °C");
    expect((screen.getByLabelText(t.afterLabel) as HTMLInputElement).value).toBe("");

    await fill(user, t.topLabel, "yeast");
    await user.click(screen.getByRole("button", { name: t.update }));

    expect(emitted(editor)).toBe(
      '<p>a <span class="math-inline">\\(\\ce{C6H12O6 -&gt;[yeast][30 °C] 2C2H5OH + 2CO2}\\)</span> b</p>',
    );
  });

  it("does not take the separate note formula back, and leaves it in place", async () => {
    const user = userEvent.setup();
    const editor = createEditor(chemistrySample);
    selectFirstMath(editor);
    renderDialog(editor);

    expect((screen.getByLabelText(t.afterLabel) as HTMLInputElement).value).toBe("");
    await fill(user, t.quickLabel, "CaCO3(s) -> CaO(s) + CO2(g)");
    await user.click(screen.getByRole("button", { name: t.update }));

    expect(emitted(editor)).toBe(
      '<p><span class="math-inline">\\(\\ce{CaCO3(s) -&gt; CaO(s) + CO2(g)}\\)</span> <span class="math-inline">\\(\\Delta_r H^\\circ_{298} = 179,2\\ \\mathrm{kJ}\\)</span></p>',
    );
  });

  it("reopens a block formula as Own line", () => {
    const editor = createEditor('<span class="math-block">\\[\\ce{A -&gt; B}\\]</span>');
    selectFirstMath(editor);
    renderDialog(editor);
    expect(screen.getByRole("button", { name: t.block })).toHaveAttribute("aria-pressed", "true");
  });
});

describe("chemistry dialog: recent", () => {
  it("stores the inserted equation per user and offers it next time", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, { id: "user-1" });
    const editor = createEditor("<p></p>");
    const first = renderWithProviders(
      <ChemistryInsertDialog editor={editor} open={true} onOpenChange={vi.fn()} editing={null} />,
      { locale: "en", messages: en, client },
    );
    await fill(user, t.quickLabel, "A ->[x] B");
    await user.click(insertButton());
    first.unmount();

    renderDialog(createEditor("<p></p>"), client);
    expect(screen.getByText(t.recentLabel)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "A ->[x] B" }));
    expect((screen.getByLabelText(t.quickLabel) as HTMLInputElement).value).toBe("A -> B");
    expect((screen.getByLabelText(t.topLabel) as HTMLInputElement).value).toBe("x");
  });

  it("shows no recent list without a session", () => {
    renderDialog(createEditor("<p></p>"));
    expect(screen.queryByText(t.recentLabel)).toBeNull();
  });
});
