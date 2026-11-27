import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { matchesFilter, normalizeFilterText } from "../lib/filter-text";
import { Button } from "./button";
import { Combobox, ComboboxMenu } from "./combobox";
import { SELECT_FILTER_THRESHOLD, Select } from "./select";
import { UiStringsProvider } from "./ui-strings";

const tags = [
  { value: "nlxh", label: "Nghị luận xã hội" },
  { value: "vbtt", label: "Văn bản thông tin" },
  { value: "dh1", label: "Đọc hiểu 1" },
  { value: "dh2", label: "Đọc hiểu 2", disabled: true },
  { value: "dh3", label: "Đọc hiểu 3" },
];

describe("normalizeFilterText / matchesFilter", () => {
  it("bỏ dấu tiếng Việt, đ thành d, không phân biệt hoa thường, gộp khoảng trắng", () => {
    expect(normalizeFilterText("  Văn   BẢN Đọc ")).toBe("van ban doc");
    expect(matchesFilter("Văn bản thông tin", "van ban")).toBe(true);
    expect(matchesFilter("Đọc hiểu 1", "DOC HIEU")).toBe(true);
    expect(matchesFilter("Đọc hiểu 1", "hiểu đọc")).toBe(true);
    expect(matchesFilter("Nghị luận xã hội", "van")).toBe(false);
    expect(matchesFilter("Bất kỳ", "")).toBe(true);
  });
});

describe("ComboboxMenu", () => {
  function renderMenu(onSelect = vi.fn()) {
    render(
      <ComboboxMenu
        aria-label="Nhãn"
        options={tags}
        onSelect={onSelect}
        trigger={<Button type="button">Thêm nhãn</Button>}
      />,
    );
    return onSelect;
  }

  it("mở thì focus ô lọc; lọc không dấu (van ban → Văn bản thông tin), Enter chọn, đóng và trả focus về nút mở", async () => {
    const onSelect = renderMenu();
    const user = userEvent.setup();
    const trigger = screen.getByRole("button", { name: "Thêm nhãn" });
    expect(trigger).toHaveAttribute("aria-haspopup", "listbox");
    await user.click(trigger);
    const input = await screen.findByRole("combobox", { name: "Lọc danh sách" });
    expect(input).toHaveFocus();
    expect(input).toHaveAttribute("placeholder", "Lọc…");
    expect(within(screen.getByRole("listbox", { name: "Nhãn" })).getAllByRole("option")).toHaveLength(5);

    await user.keyboard("van ban");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Văn bản thông tin"]);
    await user.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledWith("vbtt");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("↑/↓ chuyển mục tô sáng qua aria-activedescendant, bỏ qua mục bị khóa và vòng lại", async () => {
    const onSelect = renderMenu();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Thêm nhãn" }));
    const input = await screen.findByRole("combobox", { name: "Lọc danh sách" });
    await user.keyboard("doc hieu");
    const active = () => document.getElementById(input.getAttribute("aria-activedescendant") ?? "")?.textContent;
    expect(active()).toBe("Đọc hiểu 1");
    await user.keyboard("{ArrowDown}");
    expect(active()).toBe("Đọc hiểu 3");
    await user.keyboard("{ArrowDown}");
    expect(active()).toBe("Đọc hiểu 1");
    await user.keyboard("{ArrowUp}");
    expect(active()).toBe("Đọc hiểu 3");
    await user.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledWith("dh3");
  });

  it("bấm chuột chọn mục; mục bị khóa không chọn được", async () => {
    const onSelect = renderMenu();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Thêm nhãn" }));
    await user.click(await screen.findByRole("option", { name: "Đọc hiểu 2" }));
    expect(onSelect).not.toHaveBeenCalled();
    await user.click(screen.getByRole("option", { name: "Nghị luận xã hội" }));
    expect(onSelect).toHaveBeenCalledWith("nlxh");
  });

  it("không mục nào khớp thì hiện trạng thái rỗng; Esc đóng và trả focus về nút mở", async () => {
    renderMenu();
    const user = userEvent.setup();
    const trigger = screen.getByRole("button", { name: "Thêm nhãn" });
    await user.click(trigger);
    await screen.findByRole("combobox", { name: "Lọc danh sách" });
    await user.keyboard("xyz");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Không có mục khớp");
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("combobox", { name: "Lọc danh sách" })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("khung cao tối đa bằng chỗ trống; ô lọc cố định, chỉ danh sách cuộn và cao tối đa 20rem", async () => {
    renderMenu();
    await userEvent.click(screen.getByRole("button", { name: "Thêm nhãn" }));
    const listbox = await screen.findByRole("listbox");
    expect(listbox).toHaveClass("max-h-80", "overflow-y-auto", "min-h-0");
    const content = listbox.parentElement!;
    expect(content).toHaveClass("flex-col", "overflow-hidden", "max-h-[calc(var(--radix-popover-content-available-height)-0.5rem)]");
    expect(screen.getByRole("combobox", { name: "Lọc danh sách" }).parentElement).toHaveClass("shrink-0");
  });

  it("chữ theo ngôn ngữ của UiStringsProvider, props labels ghi đè", async () => {
    render(
      <UiStringsProvider locale="en">
        <ComboboxMenu
          options={tags}
          onSelect={vi.fn()}
          labels={{ filterLabel: "Filter tags" }}
          trigger={<Button type="button">Add tag</Button>}
        />
      </UiStringsProvider>,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Add tag" }));
    expect(await screen.findByRole("combobox", { name: "Filter tags" })).toHaveAttribute("placeholder", "Filter…");
    await user.keyboard("zzz");
    expect(screen.getByRole("status")).toHaveTextContent("No matching items");
  });
});

describe("Combobox", () => {
  it("chọn một giá trị: hiện nhãn trên nút, mở lại thì mục đang chọn được đánh dấu và tô sáng", async () => {
    const onValueChange = vi.fn();
    render(<Combobox aria-label="Nhãn" options={tags} placeholder="Chọn nhãn" onValueChange={onValueChange} name="tag" />);
    const user = userEvent.setup();
    const trigger = screen.getByRole("combobox", { name: "Nhãn" });
    expect(trigger).toHaveTextContent("Chọn nhãn");
    expect(trigger).toHaveAttribute("data-placeholder");
    await user.click(trigger);
    await user.click(await screen.findByRole("option", { name: "Đọc hiểu 3" }));
    expect(onValueChange).toHaveBeenCalledWith("dh3");
    expect(trigger).toHaveTextContent("Đọc hiểu 3");
    expect(trigger).toHaveFocus();
    expect(document.querySelector<HTMLInputElement>('input[type="hidden"][name="tag"]')?.value).toBe("dh3");

    await user.click(trigger);
    const selected = await screen.findByRole("option", { name: "Đọc hiểu 3" });
    expect(selected).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("combobox", { name: "Lọc danh sách" })).toHaveAttribute("aria-activedescendant", selected.id);
  });

  it("gõ chữ trên nút đóng thì mở và điền chữ đó vào ô lọc; ↓ cũng mở", async () => {
    render(<Combobox aria-label="Nhãn" options={tags} value="nlxh" />);
    const user = userEvent.setup();
    const trigger = screen.getByRole("combobox", { name: "Nhãn" });
    trigger.focus();
    await user.keyboard("v");
    const input = await screen.findByRole("combobox", { name: "Lọc danh sách" });
    expect(input).toHaveValue("v");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Văn bản thông tin"]);
    await user.keyboard("{Escape}");
    expect(trigger).toHaveFocus();
    await user.keyboard("{ArrowDown}");
    expect(await screen.findByRole("combobox", { name: "Lọc danh sách" })).toHaveValue("");
  });

  it("lọc theo textValue hoặc chữ của nhãn là nút React", async () => {
    render(
      <Combobox
        aria-label="Đề"
        options={[
          { value: "a", label: <span data-exam-content>Đề Toán</span> },
          { value: "b", label: <span>Mã X</span>, textValue: "Ngữ văn" },
        ]}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole("combobox", { name: "Đề" }));
    await user.keyboard("toan");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Đề Toán"]);
    await user.clear(screen.getByRole("combobox", { name: "Lọc danh sách" }));
    await user.keyboard("ngu van");
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Mã X"]);
  });

  it("nút bị khóa không mở; aria-invalid đi tới nút", async () => {
    render(<Combobox aria-label="Nhãn" options={tags} disabled aria-invalid />);
    const trigger = screen.getByRole("combobox", { name: "Nhãn" });
    expect(trigger).toBeDisabled();
    expect(trigger).toBeInvalid();
    await userEvent.click(trigger);
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });
});

describe("Select tự có ô lọc khi danh sách dài", () => {
  const many = Array.from({ length: SELECT_FILTER_THRESHOLD + 1 }, (_, index) => ({
    value: `v${index}`,
    label: `Mục ${index}`,
  }));

  it("dài hơn ngưỡng: có ô lọc; đúng ngưỡng hoặc searchable={false}: danh sách thường", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Select aria-label="Dài" options={many} />);
    await user.click(screen.getByRole("combobox", { name: "Dài" }));
    expect(await screen.findByRole("combobox", { name: "Lọc danh sách" })).toHaveFocus();
    unmount();

    const { unmount: unmountShort } = render(<Select aria-label="Ngắn" options={many.slice(1)} />);
    await user.click(screen.getByRole("combobox", { name: "Ngắn" }));
    await screen.findAllByRole("option");
    expect(screen.queryByRole("combobox", { name: "Lọc danh sách" })).not.toBeInTheDocument();
    unmountShort();

    render(<Select aria-label="Tắt" options={many} searchable={false} />);
    await user.click(screen.getByRole("combobox", { name: "Tắt" }));
    await screen.findAllByRole("option");
    expect(screen.queryByRole("combobox", { name: "Lọc danh sách" })).not.toBeInTheDocument();
  });

  it("searchable luôn có ô lọc, kể cả danh sách ngắn", async () => {
    render(<Select aria-label="Ngắn" options={tags} searchable />);
    await userEvent.click(screen.getByRole("combobox", { name: "Ngắn" }));
    expect(await screen.findByRole("combobox", { name: "Lọc danh sách" })).toBeInTheDocument();
  });
});
