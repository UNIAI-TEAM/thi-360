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
  it("gõ tìm: nhãn nút khớp textValue trước chữ hiện, không textValue thì theo chữ ItemText; nhãn chuỗi khớp chính nó", async () => {
    const onValueChange = vi.fn();
    render(
      <Select
        aria-label="Đường"
        onValueChange={onValueChange}
        options={[
          { value: "a", label: <span data-exam-content>KHTN – đường A</span> },
          { value: "b", label: <span data-exam-content>Đường B · KHXH</span>, textValue: "Beta" },
          { value: "c", label: "Đường C" },
        ]}
      />,
    );
    const user = userEvent.setup();

    // "b" không đứng đầu chữ hiện ("Đường B · KHXH" bắt đầu bằng Đ): chỉ khớp được nhờ textValue.
    await user.click(screen.getByRole("combobox", { name: "Đường" }));
    await user.keyboard("b");
    expect(screen.getByRole("option", { name: "Đường B · KHXH" })).toHaveAttribute("data-highlighted");
    await user.keyboard("{Enter}");
    expect(onValueChange).toHaveBeenCalledWith("b");

    // Nhãn nút không textValue: gõ tìm theo chữ của ItemText.
    await user.click(screen.getByRole("combobox", { name: "Đường" }));
    await user.keyboard("K{Enter}");
    expect(onValueChange).toHaveBeenLastCalledWith("a");

    // Nhãn chuỗi: textValue mặc định là chính nhãn.
    await user.click(screen.getByRole("combobox", { name: "Đường" }));
    await user.keyboard("Đường C{Enter}");
    expect(onValueChange).toHaveBeenLastCalledWith("c");
  });
  it("mục trong danh sách tắt viền ngoài bằng outline-hidden để còn thấy ở chế độ tương phản cao", async () => {
    render(<Select aria-label="Trạng thái" options={options} />);
    await userEvent.click(screen.getByRole("combobox", { name: "Trạng thái" }));
    const option = await screen.findByRole("option", { name: "Hoạt động" });
    expect(option).toHaveClass("outline-hidden");
    expect(option).not.toHaveClass("outline-none");
  });

  it("nút chọn và mục danh sách dùng chiều cao tối thiểu để nhãn dài nhiều dòng không bị cắt (R1-2/F-VIS-01)", async () => {
    const { rerender } = render(<Select aria-label="Kỳ thi" options={options} />);
    const trigger = screen.getByRole("combobox", { name: "Kỳ thi" });
    expect(trigger).toHaveClass("min-h-10", "py-1.5");
    expect(trigger).not.toHaveClass("h-10");

    rerender(<Select aria-label="Kỳ thi" size="lg" options={options} />);
    expect(trigger).toHaveClass("min-h-12", "py-2");
    expect(trigger).not.toHaveClass("h-12");

    await userEvent.click(trigger);
    const option = await screen.findByRole("option", { name: "Hoạt động" });
    expect(option).toHaveClass("min-h-9", "py-1.5");
    expect(option).not.toHaveClass("h-9");
  });
});
