import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import {
  evaluatePasswordRules,
  generateStrongPassword,
  isPasswordStrong,
  PasswordField,
} from "./password-field";

describe("PasswordField", () => {
  it("bật tắt hiển thị mật khẩu và cập nhật năm điều kiện khi gõ", async () => {
    render(<PasswordField aria-label="Mật khẩu mới" showRules />);
    const input = screen.getByLabelText("Mật khẩu mới");
    expect(input).toHaveAttribute("type", "password");

    const toggle = screen.getByRole("button", { name: "Hiện mật khẩu" });
    await userEvent.click(toggle);
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.type(input, "Abc1!");
    expect(screen.getByText("Từ 8 đến 64 ký tự").closest("li")).toHaveAttribute("data-met", "false");
    expect(screen.getByText("Có ít nhất một chữ hoa").closest("li")).toHaveAttribute("data-met", "true");
    expect(screen.getByText("Có ít nhất một chữ thường").closest("li")).toHaveAttribute("data-met", "true");
    expect(screen.getByText("Có ít nhất một chữ số").closest("li")).toHaveAttribute("data-met", "true");
    expect(screen.getByText("Có ít nhất một ký tự đặc biệt").closest("li")).toHaveAttribute("data-met", "true");

    await userEvent.type(input, "234");
    expect(screen.getByText("Từ 8 đến 64 ký tự").closest("li")).toHaveAttribute("data-met", "true");
  });

  it("nút Tạo ngẫu nhiên sinh mật khẩu đạt cả 5 điều kiện", async () => {
    const onValueChange = vi.fn();
    render(<PasswordField aria-label="Mật khẩu" showRules allowGenerate onValueChange={onValueChange} />);

    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(onValueChange).toHaveBeenCalledTimes(1);
    const generated = onValueChange.mock.calls[0]?.[0] as string;
    expect(generated.length).toBe(12);
    expect(generated).not.toMatch(/[0Oo1lI]/);
    expect(isPasswordStrong(generated)).toBe(true);
    expect(evaluatePasswordRules(generateStrongPassword(12)).every((r) => r.met)).toBe(true);
  });

  it("nút Tạo ngẫu nhiên cập nhật onChange khi dùng controlled và gắn aria-describedby tới danh sách điều kiện", async () => {
    const onChange = vi.fn();
    function ControlledProbe() {
      const [val, setVal] = useState("");
      return (
        <PasswordField
          aria-label="Mật khẩu mới"
          aria-describedby="field-error"
          value={val}
          onChange={(event) => {
            onChange(event.target.value);
            setVal(event.target.value);
          }}
          showRules
          allowGenerate
        />
      );
    }

    render(<ControlledProbe />);
    const input = screen.getByLabelText("Mật khẩu mới");
    const rulesList = screen.getByRole("list", { name: "Điều kiện mật khẩu" });
    expect(rulesList.id).not.toBe("");
    expect(input.getAttribute("aria-describedby")).toBe(`field-error ${rulesList.id}`);

    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(onChange).toHaveBeenCalledTimes(1);
    const generated = onChange.mock.calls[0]?.[0] as string;
    expect(input).toHaveValue(generated);
    expect(isPasswordStrong(generated)).toBe(true);
    for (const item of rulesList.querySelectorAll("li")) {
      expect(item).toHaveAttribute("data-met", "true");
    }
  });

  it("tự hiện mật khẩu khi revealOnGenerate được bật", async () => {
    render(<PasswordField aria-label="Mật khẩu tạm" allowGenerate revealOnGenerate />);
    const input = screen.getByLabelText("Mật khẩu tạm");
    expect(input).toHaveAttribute("type", "password");

    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toHaveAttribute("aria-pressed", "true");
  });
});
