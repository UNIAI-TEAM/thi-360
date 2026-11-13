import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FormField } from "./form-field";
import { Input } from "./input";

describe("FormField", () => {
  it("gắn nhãn với ô nhập", () => {
    render(<FormField label="Email">{(field) => <Input {...field} />}</FormField>);
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("có lỗi: ô nhập aria-invalid và được mô tả bởi dòng lỗi", () => {
    render(
      <FormField label="Email" error="Email không hợp lệ">
        {(field) => <Input {...field} />}
      </FormField>,
    );
    const input = screen.getByLabelText("Email");
    expect(input).toBeInvalid();
    expect(input).toHaveAccessibleDescription("Email không hợp lệ");
    expect(screen.getByRole("alert")).toHaveTextContent("Email không hợp lệ");
  });

  it("không có lỗi: không aria-invalid, không có dòng lỗi; gợi ý vẫn mô tả ô nhập", () => {
    render(
      <FormField label="Email" hint="Dùng email công việc">
        {(field) => <Input {...field} />}
      </FormField>,
    );
    const input = screen.getByLabelText("Email");
    expect(input).toBeValid();
    expect(input).toHaveAccessibleDescription("Dùng email công việc");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("required: ô nhập aria-required", () => {
    render(
      <FormField label="Email" required>
        {(field) => <Input {...field} />}
      </FormField>,
    );
    expect(screen.getByRole("textbox", { name: "Email" })).toBeRequired();
  });
});
