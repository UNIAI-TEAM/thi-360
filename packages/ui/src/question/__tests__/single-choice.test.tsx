import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Live, single } from "./fixtures";

describe("Câu Một đáp án (AC-7)", () => {
  it("chọn, đổi, bỏ chọn: giá trị {optionId}, bỏ chọn là null", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={single} onValue={onValue} />);
    await user.click(screen.getByRole("radio", { name: /Hà Nội/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionId: "o1" });
    await user.click(screen.getByRole("radio", { name: /Huế/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionId: "o2" });
    expect(screen.getByRole("radio", { name: /Huế/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /Hà Nội/ })).not.toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Huế/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionId: null });
    expect(screen.getByRole("radio", { name: /Huế/ })).not.toBeChecked();
  });

  it("đề bài và phương án nằm trong vùng data-exam-content", () => {
    const { container } = render(<Live question={single} />);
    const marked = [...container.querySelectorAll("[data-exam-content]")];
    expect(marked.some((el) => el.textContent?.includes("Thủ đô"))).toBe(true);
    expect(marked.filter((el) => el.textContent?.includes("Hà Nội")).length).toBeGreaterThan(0);
  });
});
