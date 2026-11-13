import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Input } from "./input";

describe("Input", () => {
  it("nhận chữ gõ vào và báo qua onChange", async () => {
    const onChange = vi.fn();
    render(<Input aria-label="Tên đăng nhập" onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: "Tên đăng nhập" });
    await userEvent.type(input, "an");
    expect(input).toHaveValue("an");
    expect(onChange).toHaveBeenCalledTimes(2);
  });

  it("cao 40px, cỡ lg cao 48px", () => {
    render(
      <>
        <Input aria-label="md" />
        <Input aria-label="lg" size="lg" />
      </>,
    );
    expect(screen.getByLabelText("md")).toHaveClass("h-10");
    expect(screen.getByLabelText("lg")).toHaveClass("h-12");
  });

  it("không nhận chữ khi bị khóa", async () => {
    render(<Input aria-label="Mã" disabled />);
    const input = screen.getByLabelText("Mã");
    await userEvent.type(input, "x");
    expect(input).toHaveValue("");
  });

  it("có icon dẫn đầu thì chừa chỗ và giấu icon khỏi trình đọc màn hình", () => {
    render(<Input aria-label="Tìm" leadingIcon={<svg data-testid="icon" />} />);
    expect(screen.getByLabelText("Tìm")).toHaveClass("pl-10");
    expect(screen.getByTestId("icon").closest("[aria-hidden]")).not.toBeNull();
  });
});
