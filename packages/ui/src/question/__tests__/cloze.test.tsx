import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { cloze, Live } from "./fixtures";

describe("Câu Điền khuyết (AC-10)", () => {
  it("ô nhập nằm trong đoạn văn, mỗi ô đổi riêng trong {blanks}", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={cloze} onValue={onValue} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    const [first, second] = screen.getAllByRole("textbox");
    const paragraph = first!.closest("p")!;
    expect(paragraph.textContent).toContain("Nước sôi ở");
    expect(paragraph).toContainElement(second!);
    await user.type(first!, "100");
    expect(onValue).toHaveBeenLastCalledWith({ blanks: { b1: "100" } });
    await user.type(second!, "0");
    expect(onValue).toHaveBeenLastCalledWith({ blanks: { b1: "100", b2: "0" } });
  });

  it("tên truy cập của ô mang số câu", async () => {
    render(<Live question={cloze} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    const names = screen.getAllByRole("textbox").map((el) => el.getAttribute("aria-label"));
    expect(names[0]).toContain("4");
    expect(names[1]).toContain("5");
  });
});
