import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Live, single } from "./fixtures";
import type { KitQuestion } from "../types";

function names(): string[] {
  return screen.getAllByRole("radio").map((radio) => radio.closest("label")!.textContent!.replace(/\s+/g, " ").trim().replace(/^([A-D])\.\s*/, "$1. "));
}

describe("Chữ cái phương án theo option_order (AC-15)", () => {
  it("chữ cái gán theo thứ tự đã nhận của lượt, đáp án lưu id không lưu chữ cái", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={{ ...single, optionOrder: ["o3", "o1", "o4", "o2"] }} onValue={onValue} />);
    expect(names()).toEqual(["A. Đà Nẵng", "B. Hà Nội", "C. Cần Thơ", "D. Huế"]);
    await user.click(screen.getByRole("radio", { name: /Hà Nội/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionId: "o1" });
  });

  it("thứ tự soạn (shuffleOptions = false) vẫn lấy theo option_order đã nhận", () => {
    const authored: KitQuestion = {
      ...single,
      optionOrder: ["o1", "o2", "o3", "o4"],
      body: { ...(single.body as Extract<KitQuestion["body"], { type: "SINGLE_CHOICE" }>), shuffleOptions: false },
    };
    render(<Live question={authored} />);
    expect(names()).toEqual(["A. Hà Nội", "B. Huế", "C. Đà Nẵng", "D. Cần Thơ"]);
  });

  it("thiếu option_order thì theo thứ tự mảng", () => {
    render(<Live question={single} />);
    expect(names()[0]).toBe("A. Hà Nội");
  });
});
