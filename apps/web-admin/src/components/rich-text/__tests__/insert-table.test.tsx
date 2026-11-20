import { readFileSync } from "node:fs";
import { join } from "node:path";
import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { sanitizeQuestionHtml, stripTrailingEmptyParagraph } from "@thi360/ui/rich-text";
import { createRichTextExtensions } from "../extensions";
import TableInsertDialog from "../insert/table/dialog";
import {
  createDraft,
  deleteCol,
  deleteRow,
  isCovered,
  isNumericText,
  parseClipboardGrid,
  parseTable,
  serializeTable,
  setHeaderCol,
  setHeaderRow,
  unwrapFigure,
} from "../insert/table/table-model";

interface Sample {
  name: string;
  html: string;
}
const samples = (
  JSON.parse(readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8")) as {
    samples: Sample[];
  }
).samples;
const fixture = (name: string) => samples.find((sample) => sample.name === name)!.html;

const editors: Editor[] = [];
function createEditor(content: string): Editor {
  const element = document.createElement("div");
  document.body.append(element);
  const editor = new Editor({ element, extensions: createRichTextExtensions(), content });
  editors.push(editor);
  return editor;
}
const emitted = (editor: Editor) => stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML()).html);

afterEach(() => {
  for (const editor of editors.splice(0)) editor.destroy();
});

function renderDialog(editor: Editor) {
  const onOpenChange = vi.fn();
  renderWithProviders(<TableInsertDialog editor={editor} open onOpenChange={onOpenChange} editing={null} />);
  return onOpenChange;
}

const cell = (row: number, col: number) => screen.getByLabelText(`Ô hàng ${row}, cột ${col}`);

describe("table model", () => {
  it("detects number cells as spec 4.5 describes", () => {
    for (const text of ["1096,1", "2 268,5", "-3,2%", "2000", "1.234.567", "0,5%"]) {
      expect(isNumericText(text), text).toBe(true);
    }
    for (const text of ["", "abc", "12a", "1,2,3", "1 23", "--1", "3.5", "10 %"]) {
      expect(isNumericText(text), text).toBe(false);
    }
  });

  it.each(["data-table", "data-table-merged-cell-with-math"])("reads and writes fixture %s unchanged", (name) => {
    const html = fixture(name);
    const draft = parseTable(unwrapFigure(html)!);
    expect(serializeTable(draft)).toBe(html);
  });

  it("parses Excel text with quoted multi-line cells and a trailing newline", () => {
    expect(parseClipboardGrid('a\tb\r\n"c\nd"\t"e ""q"""\r\n')).toEqual([
      ["a", "b"],
      ["c\nd", 'e "q"'],
    ]);
  });

  it("puts class num only on columns whose body cells are all numbers", () => {
    const draft = createDraft(3, 3);
    draft.cells = [
      ["Năm", "A", "B"],
      ["x", "1", "2"],
      ["y", "3", "n/a"],
    ].map((row) => row.map((text) => ({ text, rich: false })));
    expect(serializeTable(draft)).toBe(
      '<figure class="data-table"><table><thead><tr><th scope="col">Năm</th><th scope="col">A</th><th scope="col">B</th></tr></thead>' +
        '<tbody><tr><th scope="row">x</th><td class="num">1</td><td>2</td></tr><tr><th scope="row">y</th><td class="num">3</td><td>n/a</td></tr></tbody></table></figure>',
    );
    expect(serializeTable({ ...draft, alignNumbers: false })).not.toContain("num");
  });

  it("escapes plain cell text", () => {
    const draft = createDraft(1, 1);
    draft.headerRow = false;
    draft.headerCol = false;
    draft.cells = [[{ text: "a < b & c", rich: false }]];
    expect(serializeTable(draft)).toContain("<td>a &lt; b &amp; c</td>");
  });

  it("moves the origin of a vertical merge down with its text when the origin row is deleted", () => {
    const draft = createDraft(4, 2);
    draft.cells[1]![0] = { text: "gộp", rich: false };
    draft.merges = [{ row: 1, col: 0, rowSpan: 3, colSpan: 1 }];

    const after = deleteRow(draft, 1);

    expect(after.merges).toEqual([{ row: 1, col: 0, rowSpan: 2, colSpan: 1 }]);
    expect(after.cells[1]![0]).toEqual({ text: "gộp", rich: false });
    expect(isCovered(after, 2, 0)).toBe(true);
    expect(deleteRow(draft, 3).merges).toEqual([{ row: 1, col: 0, rowSpan: 2, colSpan: 1 }]);
    expect(deleteRow(draft, 3).cells[1]![0]!.text).toBe("gộp");
  });

  it("moves the origin of a horizontal merge right with its text when the origin column is deleted", () => {
    const draft = createDraft(2, 4);
    draft.cells[0]![1] = { text: "gộp", rich: false };
    draft.merges = [{ row: 0, col: 1, rowSpan: 1, colSpan: 3 }];

    const after = deleteCol(draft, 1);

    expect(after.merges).toEqual([{ row: 0, col: 1, rowSpan: 1, colSpan: 2 }]);
    expect(after.cells[0]![1]).toEqual({ text: "gộp", rich: false });
  });

  it("splits merges that cross the header boundary when the header row or column is switched on", () => {
    const draft = createDraft(4, 4);
    draft.headerRow = false;
    draft.headerCol = false;
    const keep = { row: 2, col: 2, rowSpan: 2, colSpan: 2 };
    draft.merges = [{ row: 0, col: 0, rowSpan: 2, colSpan: 1 }, { row: 0, col: 2, rowSpan: 1, colSpan: 2 }, keep];

    const withRow = setHeaderRow(draft, true);
    expect(withRow.headerRow).toBe(true);
    expect(withRow.merges).toEqual([{ row: 0, col: 2, rowSpan: 1, colSpan: 2 }, keep]);

    const withCol = setHeaderCol(draft, true);
    expect(withCol.headerCol).toBe(true);
    expect(withCol.merges).toEqual([{ row: 0, col: 0, rowSpan: 2, colSpan: 1 }, { row: 0, col: 2, rowSpan: 1, colSpan: 2 }, keep]);
    expect(setHeaderRow(withRow, false).merges).toEqual(withRow.merges);
  });
});

describe("table dialog", () => {
  it("builds the data-table fixture from a pasted Excel block", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    const onOpenChange = renderDialog(editor);

    await user.type(screen.getByLabelText("Tên bảng"), "TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ");
    await user.type(screen.getByLabelText("Đơn vị"), "Tỉ USD");
    await user.type(screen.getByLabelText("Nguồn"), "World Bank, 2022");
    await user.click(cell(1, 1));
    await user.paste("Năm\t2000\t2020\nXuất khẩu\t1096,1\t2148,6\r\nNhập khẩu\t1477,2\t2776,1\r\n");
    // Lưới mặc định có 4 cột: bỏ cột thừa.
    await user.click(cell(1, 4));
    await user.click(screen.getByRole("button", { name: "Xóa cột" }));
    expect(screen.getAllByRole("textbox", { name: /^Ô hàng/ })).toHaveLength(9);

    await user.click(screen.getByRole("button", { name: "Chèn bảng" }));

    expect(emitted(editor)).toBe(fixture("data-table"));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("stores the Vietnamese unit and source prefixes even when the interface is English", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderWithProviders(<TableInsertDialog editor={editor} open onOpenChange={vi.fn()} editing={null} />, {
      locale: "en",
      messages: en,
    });

    await user.type(screen.getByLabelText("Unit"), "Tỉ USD");
    await user.type(screen.getByLabelText("Source"), "World Bank, 2022");
    await user.click(screen.getByLabelText("Cell row 1, column 1"));
    await user.paste("a");
    await user.click(screen.getByRole("button", { name: "Insert table" }));

    expect(emitted(editor)).toContain('<p class="figure-note">Đơn vị: Tỉ USD</p><p class="figure-source">Nguồn: World Bank, 2022</p>');
  });

  it("right-aligns number columns in the grid only while the switch is on", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor(""));
    await user.click(cell(1, 1));
    await user.paste("Năm\tGiá\nA\t1096,1\nB\t2148,6");

    expect(cell(2, 2)).toHaveClass("text-right");
    expect(cell(3, 2)).toHaveClass("text-right");
    expect(cell(1, 2)).not.toHaveClass("text-right");
    expect(cell(2, 1)).not.toHaveClass("text-right");

    await user.click(screen.getByRole("checkbox", { name: "Căn phải cột số" }));
    expect(cell(2, 2)).not.toHaveClass("text-right");
  });

  it("honours the header row, header column and number-alignment switches", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderDialog(editor);
    await user.click(cell(1, 1));
    await user.paste("a\t1\nb\t2");
    await user.click(screen.getByRole("checkbox", { name: "Hàng đầu là tiêu đề" }));
    await user.click(screen.getByRole("checkbox", { name: "Cột đầu là tiêu đề" }));
    await user.click(screen.getByRole("checkbox", { name: "Căn phải cột số" }));
    await user.click(cell(1, 3));
    await user.click(screen.getByRole("button", { name: "Xóa cột" }));
    await user.click(cell(1, 3));
    await user.click(screen.getByRole("button", { name: "Xóa cột" }));
    await user.click(cell(3, 1));
    await user.click(screen.getByRole("button", { name: "Xóa hàng" }));

    await user.click(screen.getByRole("button", { name: "Chèn bảng" }));

    expect(emitted(editor)).toBe(
      '<figure class="data-table"><table><tbody><tr><td>a</td><td>1</td></tr><tr><td>b</td><td>2</td></tr></tbody></table></figure>',
    );
  });

  it("merges a shift-selected range into one cell with colspan", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderDialog(editor);
    await user.click(cell(1, 1));
    await user.paste("Giá trị\t\nx\ty");
    await user.click(cell(1, 3));
    await user.click(screen.getByRole("button", { name: "Xóa cột" }));
    await user.click(cell(1, 3));
    await user.click(screen.getByRole("button", { name: "Xóa cột" }));
    await user.click(cell(3, 1));
    await user.click(screen.getByRole("button", { name: "Xóa hàng" }));
    await user.click(screen.getByRole("checkbox", { name: "Cột đầu là tiêu đề" }));

    expect(screen.getByRole("button", { name: "Gộp ô" })).toBeDisabled();
    await user.click(cell(1, 1));
    await user.keyboard("{Shift>}");
    await user.click(cell(1, 2));
    await user.keyboard("{/Shift}");
    expect(cell(1, 1).closest("td")).toHaveAttribute("aria-selected", "true");
    expect(cell(1, 2).closest("td")).toHaveAttribute("aria-selected", "true");
    expect(cell(2, 1).closest("td")).toHaveAttribute("aria-selected", "false");
    await user.click(screen.getByRole("button", { name: "Gộp ô" }));
    expect(screen.queryByLabelText("Ô hàng 1, cột 2")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Chèn bảng" }));

    expect(emitted(editor)).toBe(
      '<figure class="data-table"><table><thead><tr><th scope="col" colspan="2">Giá trị</th></tr></thead><tbody><tr><td>x</td><td>y</td></tr></tbody></table></figure>',
    );
  });

  it("does not allow a merge across the header boundary and can split a merged cell", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor(""));
    await user.click(cell(2, 2));
    await user.keyboard("{Shift>}");
    await user.click(cell(2, 3));
    await user.keyboard("{/Shift}");
    await user.click(screen.getByRole("button", { name: "Gộp ô" }));
    expect(screen.queryByLabelText("Ô hàng 2, cột 3")).toBeNull();
    expect(screen.getByRole("button", { name: "Tách ô" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "Tách ô" }));
    expect(screen.getByLabelText("Ô hàng 2, cột 3")).toBeTruthy();

    await user.click(cell(1, 1));
    await user.keyboard("{Shift>}");
    await user.click(cell(2, 1));
    await user.keyboard("{/Shift}");
    expect(screen.getByRole("button", { name: "Gộp ô" })).toBeDisabled();
  });

  it("limits the grid to 30 rows by 15 columns and says so", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor(""));
    const wide = Array.from({ length: 31 }, (_, r) => Array.from({ length: 16 }, (_, c) => `${r}-${c}`).join("\t")).join("\n");
    await user.click(cell(1, 1));
    await user.paste(wide);

    const grid = screen.getByRole("grid", { name: "Lưới ô của bảng" });
    expect(within(grid).getAllByRole("row")).toHaveLength(30);
    expect(within(grid).getAllByRole("textbox")).toHaveLength(30 * 15);
    expect(screen.getByRole("status").textContent).toContain("30 hàng, 15 cột");
    expect(screen.getByRole("button", { name: "Thêm hàng" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Thêm cột" })).toBeDisabled();
    expect((cell(30, 15) as HTMLInputElement).value).toBe("29-14");
  });

  it("reopens a selected data table with its state and writes it back unchanged", async () => {
    const user = userEvent.setup();
    const editor = createEditor(`<p>Trước</p>${fixture("data-table")}`);
    act(() => {
      editor.commands.setNodeSelection(editor.state.doc.firstChild!.nodeSize);
    });
    const onOpenChange = renderDialog(editor);

    expect((screen.getByLabelText("Tên bảng") as HTMLInputElement).value).toBe("TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ");
    expect((screen.getByLabelText("Đơn vị") as HTMLInputElement).value).toBe("Tỉ USD");
    expect((screen.getByLabelText("Nguồn") as HTMLInputElement).value).toBe("World Bank, 2022");
    expect((cell(2, 2) as HTMLInputElement).value).toBe("1096,1");
    for (const name of ["Hàng đầu là tiêu đề", "Cột đầu là tiêu đề", "Căn phải cột số"]) {
      expect(screen.getByRole("checkbox", { name })).toBeChecked();
    }

    await user.clear(cell(2, 2));
    await user.type(cell(2, 2), "1100,5");
    await user.click(screen.getByRole("button", { name: "Cập nhật bảng" }));

    expect(emitted(editor)).toBe(`<p>Trước</p>${fixture("data-table").replace("1096,1", "1100,5")}`);
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("reopens a table with a merged header cell and inline markup without losing either", async () => {
    const user = userEvent.setup();
    const html = fixture("data-table-merged-cell-with-math");
    const editor = createEditor(html);
    act(() => {
      editor.commands.setNodeSelection(0);
    });
    renderDialog(editor);

    expect(screen.queryByLabelText("Ô hàng 1, cột 2")).toBeNull();
    expect((cell(2, 2) as HTMLInputElement).value).toBe("H<sub>2</sub>O");
    expect(screen.getByRole("checkbox", { name: "Hàng đầu là tiêu đề" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Cột đầu là tiêu đề" })).not.toBeChecked();

    await user.click(screen.getByRole("button", { name: "Cập nhật bảng" }));
    expect(emitted(editor)).toBe(html);
  });

  it("inserts after a non-empty paragraph and after the top-level block holding a list item", async () => {
    const user = userEvent.setup();
    const editor = createEditor("<p>Trước</p>");
    act(() => {
      editor.commands.focus("end");
    });
    renderDialog(editor);
    await user.click(cell(1, 1));
    await user.paste("a\tb");
    await user.click(screen.getByRole("button", { name: "Chèn bảng" }));
    expect(emitted(editor)).toMatch(/^<p>Trước<\/p><figure class="data-table">.*<\/figure>$/);

    const inList = createEditor("<ul><li><p>mục</p></li></ul>");
    act(() => {
      inList.commands.focus("end");
    });
    renderDialog(inList);
    await user.click(screen.getAllByLabelText("Ô hàng 1, cột 1").at(-1)!);
    await user.paste("a\tb");
    await user.click(screen.getAllByRole("button", { name: "Chèn bảng" }).at(-1)!);
    expect(emitted(inList)).toMatch(/^<ul><li><p>mục<\/p><\/li><\/ul><figure class="data-table">/);
  });

  it("keeps the Insert button disabled until a cell has content", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor(""));
    expect(screen.getByRole("button", { name: "Chèn bảng" })).toBeDisabled();
    await user.type(cell(1, 1), "x");
    expect(screen.getByRole("button", { name: "Chèn bảng" })).toBeEnabled();
  });
});
