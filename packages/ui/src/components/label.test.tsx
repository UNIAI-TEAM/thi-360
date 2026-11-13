import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Label } from "./label";

describe("Label", () => {
  it("bấm nhãn thì focus vào ô nhập gắn với nó", async () => {
    render(
      <>
        <Label htmlFor="ten">Họ tên</Label>
        <input id="ten" />
      </>,
    );
    await userEvent.click(screen.getByText("Họ tên"));
    expect(screen.getByLabelText("Họ tên")).toHaveFocus();
  });

  it("dấu * giấu khỏi trình đọc màn hình và không lẫn vào tên của ô nhập", () => {
    render(
      <>
        <Label htmlFor="ten" required>
          Họ tên
        </Label>
        <input id="ten" />
      </>,
    );
    expect(screen.getByText("*")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByRole("textbox", { name: "Họ tên" })).toBeInTheDocument();
  });
});
