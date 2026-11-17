import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Node as PMNode } from "@tiptap/pm/model";
import { NodeSelection } from "@tiptap/pm/state";
import type { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import type * as QuestionsApi from "@/features/questions/api";
import { renderWithProviders } from "@/test/render";
import { MATH_INLINE_NODE } from "../extensions";
import { INSERT_REGISTRY, slashMenuEntries, toolbarInsertEntries, type InsertEntry } from "../insert/registry";
import { RichTextEditor } from "../rich-text-editor";

vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  return { ...actual, uploadQuestionAsset: vi.fn() };
});

const editable = () => document.querySelector<HTMLElement>(".ProseMirror");

function tiptap(): Editor {
  const el = editable() as (HTMLElement & { editor?: Editor }) | null;
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

function mathPos(editor: Editor): number {
  let found = -1;
  editor.state.doc.descendants((node, pos) => {
    if (node.type.name === MATH_INLINE_NODE) found = pos;
  });
  return found;
}

describe("insert registry", () => {
  it("declares the ten entries with unique ids and i18n keys that exist in both locales", () => {
    const ids = INSERT_REGISTRY.map((entry) => entry.id);
    expect(ids).toEqual(["math", "chemistry", "symbols", "table", "image", "quote", "underline-label", "blank", "cross", "word"]);
    expect(new Set(ids).size).toBe(ids.length);
    const richText = en.richText as Record<string, unknown>;
    for (const entry of INSERT_REGISTRY) {
      const [, id = "", leaf = ""] = entry.labelKey.split(".");
      const [, , descriptionLeaf = ""] = entry.descriptionKey.split(".");
      const block = (richText.insert as Record<string, Record<string, unknown>>)[id];
      expect(block?.[leaf]).toBeTruthy();
      expect(block?.[descriptionLeaf]).toBeTruthy();
    }
  });

  it("keeps richText.insert for entry blocks only (shared dialog keys live in richText.insertDialog)", () => {
    const ids = INSERT_REGISTRY.map((entry) => entry.id).sort();
    for (const messages of [en, viMessages]) {
      expect(Object.keys(messages.richText.insert).sort()).toEqual(ids);
      // Khóa chung (nút, hộp tạm) cộng một khối `richText.insertDialog.<id>` cho mỗi mục đã có hộp thật (wave 2).
      const shared = ["cancel", "close", "insert", "stub"];
      const own = Object.keys(messages.richText.insertDialog).filter((key) => !shared.includes(key));
      expect(Object.keys(messages.richText.insertDialog)).toEqual(expect.arrayContaining(shared));
      expect(own.every((key) => ids.includes(key))).toBe(true);
    }
  });

  it("filters entries per fieldContext (spec 3.3, 4.1, 4.6)", () => {
    const ids = (context: Parameters<typeof toolbarInsertEntries>[0]) => toolbarInsertEntries(context).map((entry) => entry.id);
    expect(ids("default")).toEqual(["math", "chemistry", "symbols", "table", "image", "quote"]);
    expect(ids("stem-single-choice")).toEqual(["math", "chemistry", "symbols", "table", "image", "quote", "underline-label"]);
    expect(ids("set-passage")).toEqual(["math", "chemistry", "symbols", "table", "image", "quote", "blank"]);
    expect(ids("instructions")).toEqual(["math", "chemistry", "symbols", "table", "quote"]);
    // Sơ đồ lai chỉ có trong menu `/`; `word` không phải thao tác chèn.
    expect(slashMenuEntries("default").map((entry) => entry.id)).toContain("cross");
    expect(slashMenuEntries("default").map((entry) => entry.id)).not.toContain("word");
  });

  it("routes a \\ce{…} formula to chemistry and any other formula to math", () => {
    const math = INSERT_REGISTRY.find((entry) => entry.id === "math")!;
    const chemistry = INSERT_REGISTRY.find((entry) => entry.id === "chemistry")!;
    const fake = (latex: string) => Object.assign(Object.create(PMNode.prototype), { type: { name: MATH_INLINE_NODE }, attrs: { latex } }) as PMNode;
    expect(math.openForSelection(fake("x^2"))).toEqual({ latex: "x^2", display: false });
    expect(chemistry.openForSelection(fake("x^2"))).toBeNull();
    expect(math.openForSelection(fake("\\ce{H2O}"))).toBeNull();
    expect(chemistry.openForSelection(fake("\\ce{H2O}"))).toBe(true);
  });
});

describe("selection panel and openForSelection (test-only entry)", () => {
  const original: InsertEntry[] = [...INSERT_REGISTRY];
  afterEach(() => {
    INSERT_REGISTRY.splice(0, INSERT_REGISTRY.length, ...original);
  });

  it("shows the entry's selectionPanel when its node is selected and reopens its dialog from the panel and on double click", async () => {
    const opened = vi.fn();
    // Entry thử: chọn công thức `MARK-ME` → khung đang chọn riêng + hộp riêng nhận `editing` từ openForSelection.
    const probe: InsertEntry = {
      ...INSERT_REGISTRY.find((entry) => entry.id === "symbols")!,
      id: "probe",
      labelKey: "insert.symbols.label",
      descriptionKey: "insert.symbols.description",
      showIn: () => false,
      openForSelection: (target) =>
        target instanceof PMNode && target.type.name === MATH_INLINE_NODE && target.attrs.latex === "MARK-ME" ? { token: "probe-data" } : null,
      load: async () => ({
        default: ({ editing, open }) => {
          opened(editing);
          return open ? <div role="dialog" aria-label="probe dialog" /> : null;
        },
      }),
      selectionPanel: ({ target, openDialog }) => (
        <div role="group" aria-label="probe panel">
          <span>{(target as PMNode).attrs.latex}</span>
          <button type="button" onClick={openDialog}>
            Reopen probe
          </button>
        </div>
      ),
    };
    // Đứng trước `math` để thắng lượt khớp đầu tiên cho node `MARK-ME`.
    INSERT_REGISTRY.unshift(probe);

    const user = userEvent.setup();
    renderWithProviders(<RichTextEditor value={'<p>a<span class="math-inline">\\(MARK-ME\\)</span>b</p>'} onChange={() => {}} />, {
      locale: "en",
      messages: en,
    });
    await waitFor(() => expect(editable()).toBeTruthy());
    expect(screen.queryByRole("group", { name: "probe panel" })).toBeNull();

    // Chọn node → khung xuất hiện với node đó.
    const editor = tiptap();
    editor.view.dispatch(editor.state.tr.setSelection(NodeSelection.create(editor.state.doc, mathPos(editor))));
    const panel = await screen.findByRole("group", { name: "probe panel" });
    expect(panel).toHaveTextContent("MARK-ME");

    // Nút trong khung mở lại hộp, `editing` lấy từ openForSelection.
    await user.click(screen.getByRole("button", { name: "Reopen probe" }));
    expect(await screen.findByRole("dialog", { name: "probe dialog" })).toBeTruthy();
    expect(opened).toHaveBeenLastCalledWith({ token: "probe-data" });

    // Nhấp đúp node cũng mở lại hộp.
    await user.keyboard("{Escape}");
    opened.mockClear();
    // jsdom không có hit-test nên ProseMirror không đổi nhấp đúp thành vị trí: gọi thẳng `handleDoubleClick` đã đăng ký.
    const handled = editor.view.someProp("handleDoubleClick", (handler) =>
      handler(editor.view, mathPos(editor), new MouseEvent("mousedown", { detail: 2 })),
    );
    expect(handled).toBe(true);
    await waitFor(() => expect(opened).toHaveBeenCalledWith({ token: "probe-data" }));
  });
});
