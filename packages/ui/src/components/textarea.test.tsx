import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Textarea } from "./textarea";

describe("Textarea", () => {
  it("nhận nhiều dòng", async () => {
    render(<Textarea aria-label="Lý do" />);
    const area = screen.getByRole("textbox", { name: "Lý do" });
    await userEvent.type(area, "dòng 1{enter}dòng 2");
    expect(area).toHaveValue("dòng 1\ndòng 2");
  });

  it("đánh dấu lỗi bằng aria-invalid", () => {
    render(<Textarea aria-label="Lý do" aria-invalid />);
    expect(screen.getByLabelText("Lý do")).toBeInvalid();
  });
});
