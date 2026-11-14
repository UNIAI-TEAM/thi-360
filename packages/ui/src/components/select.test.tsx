import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Select } from "./select";

const options = [
  { value: "active", label: "Hoạt động" },
  { value: "locked", label: "Bị khóa" },
  { value: "draft", label: "Nháp", disabled: true },
];

describe("Select", () => {
  it("hiện placeholder khi chưa chọn", () => {
    render(<Select aria-label="Trạng thái" options={options} placeholder="Chọn trạng thái" />);
    expect(screen.getByRole("combobox", { name: "Trạng thái" })).toHaveTextContent(
      "Chọn trạng thái",
    );
  });

  it("mở danh sách, chọn một mục thì báo giá trị và hiện nhãn", async () => {
    const onValueChange = vi.fn();
    render(<Select aria-label="Trạng thái" options={options} onValueChange={onValueChange} />);
    await userEvent.click(screen.getByRole("combobox", { name: "Trạng thái" }));
    await userEvent.click(await screen.findByRole("option", { name: "Bị khóa" }));
    expect(onValueChange).toHaveBeenCalledWith("locked");
    expect(screen.getByRole("combobox", { name: "Trạng thái" })).toHaveTextContent("Bị khóa");
  });

  it("mục bị khóa không chọn được", async () => {
    const onValueChange = vi.fn();
    render(<Select aria-label="Trạng thái" options={options} onValueChange={onValueChange} />);
    await userEvent.click(screen.getByRole("combobox", { name: "Trạng thái" }));
    await userEvent.click(await screen.findByRole("option", { name: "Nháp" }));
    expect(onValueChange).not.toHaveBeenCalled();
  });

  it("aria-invalid đi tới nút chọn", () => {
    render(<Select aria-label="Trạng thái" options={options} aria-invalid />);
    expect(screen.getByRole("combobox", { name: "Trạng thái" })).toBeInvalid();
  });
});
