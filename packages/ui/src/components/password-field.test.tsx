import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_EN } from "../messages/ui-strings";
import {
  evaluatePasswordRules,
  generateStrongPassword,
  isPasswordStrong,
  PasswordField,
} from "./password-field";
import { UiStringsProvider } from "./ui-strings";

describe("PasswordField", () => {
  it("bật tắt hiển thị mật khẩu và cập nhật năm điều kiện khi gõ", async () => {
    render(<PasswordField aria-label="Mật khẩu mới" showRules />);
    const input = screen.getByLabelText("Mật khẩu mới");
    expect(input).toHaveAttribute("type", "password");

    const toggle = screen.getByRole("button", { name: "Hiện mật khẩu" });
    await userEvent.click(toggle);
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toBe(toggle);

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

  it("evaluatePasswordRules hỗ trợ locale tiếng Anh và truyền bộ chuỗi trực tiếp", () => {
    const byLocale = evaluatePasswordRules("Abc1!", "en");
    expect(byLocale.map((r) => r.label)).toEqual([
      UI_STRINGS_EN.passwordField.ruleLength,
      UI_STRINGS_EN.passwordField.ruleUpper,
      UI_STRINGS_EN.passwordField.ruleLower,
      UI_STRINGS_EN.passwordField.ruleDigit,
      UI_STRINGS_EN.passwordField.ruleSpecial,
    ]);
    expect(byLocale[0]?.met).toBe(false);
    expect(byLocale.slice(1).every((r) => r.met)).toBe(true);

    const byStrings = evaluatePasswordRules("Abc1!234", UI_STRINGS_EN.passwordField);
    expect(byStrings.every((r) => r.met)).toBe(true);
    expect(byStrings[0]?.label).toBe("8 to 64 characters");
  });

  it("tự hiện mật khẩu khi revealOnGenerate được bật", async () => {
    render(<PasswordField aria-label="Mật khẩu tạm" allowGenerate revealOnGenerate />);
    const input = screen.getByLabelText("Mật khẩu tạm");
    expect(input).toHaveAttribute("type", "password");

    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toBeInTheDocument();
  });

  it("nút hiện/ẩn báo trạng thái bằng một cách duy nhất: tên nút đổi theo hành động, không kèm aria-pressed", async () => {
    render(<PasswordField aria-label="Mật khẩu" />);
    const toggle = screen.getByRole("button", { name: "Hiện mật khẩu" });
    expect(toggle).not.toHaveAttribute("aria-pressed");

    await userEvent.click(toggle);
    expect(toggle).toHaveAccessibleName("Ẩn mật khẩu");
    expect(toggle).not.toHaveAttribute("aria-pressed");
    // Icon chỉ để nhìn: tên nút đến từ aria-label.
    expect(toggle.querySelector("svg")).toHaveAttribute("aria-hidden", "true");

    await userEvent.click(toggle);
    expect(toggle).toHaveAccessibleName("Hiện mật khẩu");
  });

  it("mỗi điều kiện nói rõ đã đạt hay chưa bằng chữ, và chữ đó nằm trong mô tả của ô nhập", async () => {
    render(<PasswordField aria-label="Mật khẩu mới" showRules />);
    const input = screen.getByLabelText("Mật khẩu mới");
    const upper = screen.getByText("Có ít nhất một chữ hoa").closest("li") as HTMLElement;
    const length = screen.getByText("Từ 8 đến 64 ký tự").closest("li") as HTMLElement;
    expect(upper).toHaveTextContent("Có ít nhất một chữ hoa: chưa đạt");
    expect(length).toHaveTextContent("Từ 8 đến 64 ký tự: chưa đạt");

    await userEvent.type(input, "A");
    expect(upper).toHaveTextContent("Có ít nhất một chữ hoa: đã đạt");
    expect(length).toHaveTextContent("Từ 8 đến 64 ký tự: chưa đạt");
    // Chữ trạng thái ẩn với người nhìn (đã có icon), icon ẩn với trình đọc màn hình.
    expect(upper.querySelector(".sr-only")).toHaveTextContent(": đã đạt");
    expect(upper.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(input).toHaveAccessibleDescription(expect.stringContaining("Có ít nhất một chữ hoa: đã đạt"));
    expect(input).toHaveAccessibleDescription(expect.stringContaining("Từ 8 đến 64 ký tự: chưa đạt"));
  });

  it("chữ trạng thái của điều kiện theo ngôn ngữ và nhận nhãn truyền vào", () => {
    const { unmount } = render(
      <UiStringsProvider locale="en">
        <PasswordField aria-label="New password" showRules defaultValue="abc" />
      </UiStringsProvider>,
    );
    expect(screen.getByText("At least one lowercase letter").closest("li")).toHaveTextContent(
      "At least one lowercase letter: met",
    );
    expect(screen.getByText("At least one digit").closest("li")).toHaveTextContent("At least one digit: not met");
    unmount();

    render(<PasswordField aria-label="Mật khẩu" showRules labels={{ ruleMet: "ổn", ruleUnmet: "thiếu" }} />);
    expect(screen.getByText("Có ít nhất một chữ số").closest("li")).toHaveTextContent("Có ít nhất một chữ số: thiếu");
  });

  it("rulesColumns={2} xếp danh sách điều kiện hai cột từ bề rộng sm", () => {
    const { rerender } = render(<PasswordField aria-label="Mật khẩu" showRules rulesColumns={2} />);
    expect(screen.getByRole("list", { name: "Điều kiện mật khẩu" })).toHaveClass("sm:grid-cols-2");
    rerender(<PasswordField aria-label="Mật khẩu" showRules />);
    expect(screen.getByRole("list", { name: "Điều kiện mật khẩu" })).not.toHaveClass("sm:grid-cols-2");
  });

  it("tạo ngẫu nhiên: báo qua vùng status có sẵn từ đầu, không đọc mật khẩu; focus ở lại nút", async () => {
    render(<PasswordField aria-label="Mật khẩu" allowGenerate />);
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();

    const generate = screen.getByRole("button", { name: "Tạo ngẫu nhiên" });
    await userEvent.click(generate);
    expect(status).toHaveTextContent("Đã tạo mật khẩu ngẫu nhiên");
    expect(status).not.toHaveTextContent("đang hiện");
    const value = (screen.getByLabelText("Mật khẩu") as HTMLInputElement).value;
    expect(value).toHaveLength(12);
    expect(status.textContent).not.toContain(value);
    expect(generate).toHaveFocus();
  });

  it("tạo ngẫu nhiên khi mật khẩu hiện ra (revealOnGenerate): câu báo nói rõ mật khẩu đang hiện", async () => {
    render(<PasswordField aria-label="Mật khẩu tạm" allowGenerate revealOnGenerate />);
    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(screen.getByRole("status")).toHaveTextContent(
      "Đã tạo mật khẩu ngẫu nhiên, mật khẩu đang hiện trong ô nhập",
    );
  });

  it("tạo ngẫu nhiên hai lần liền: nội dung vùng status đổi để được đọc lại; gõ tay thì xóa câu báo", async () => {
    render(<PasswordField aria-label="Mật khẩu" allowGenerate />);
    const status = screen.getByRole("status");
    const generate = screen.getByRole("button", { name: "Tạo ngẫu nhiên" });

    await userEvent.click(generate);
    const first = status.textContent;
    await userEvent.click(generate);
    const second = status.textContent;
    expect(second).not.toBe(first);
    expect(second?.trim()).toBe(first?.trim());
    await userEvent.click(generate);
    expect(status.textContent).toBe(first);

    await userEvent.type(screen.getByLabelText("Mật khẩu"), "x");
    expect(status).toBeEmptyDOMElement();
  });

  it("không bật allowGenerate thì không dựng vùng status", () => {
    render(<PasswordField aria-label="Mật khẩu" showRules />);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("câu báo tạo mật khẩu theo tiếng Anh", async () => {
    render(
      <UiStringsProvider locale="en">
        <PasswordField aria-label="Temporary password" allowGenerate revealOnGenerate />
      </UiStringsProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Generate random" }));
    expect(screen.getByRole("status")).toHaveTextContent("Random password generated and shown in the field");
  });
});
