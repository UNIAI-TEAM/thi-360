import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Tooltip } from "./tooltip";

describe("Tooltip", () => {
  it("hiện khi focus bàn phím và ẩn khi rời", async () => {
    render(
      <Tooltip content="Sửa vai trò" delayDuration={0}>
        <button>Mở</button>
      </Tooltip>,
    );
    expect(screen.queryByRole("tooltip")).toBeNull();
    await userEvent.tab();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Sửa vai trò");
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).toBeNull();
  });

  it("hiện khi rê chuột", async () => {
    render(
      <Tooltip content="Xóa" delayDuration={0}>
        <button>Mở</button>
      </Tooltip>,
    );
    await userEvent.hover(screen.getByRole("button", { name: "Mở" }));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Xóa");
  });

  it("không hiện khi focus do chương trình trả về, như sau khi đóng hộp thoại (UNI-2588)", async () => {
    render(
      <Tooltip content="Mở menu điều hướng" delayDuration={0}>
        <button>Menu</button>
      </Tooltip>,
    );
    const button = screen.getByRole("button", { name: "Menu" });
    // Đóng lớp phủ bằng Esc rồi focus về nút đã mở nó.
    await userEvent.keyboard("{Escape}");
    act(() => button.focus());
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole("tooltip")).toBeNull();
  });

  it("không hiện khi bấm vào nút (focus theo con trỏ)", async () => {
    render(
      <Tooltip content="Xóa" delayDuration={0}>
        <button>Mở</button>
      </Tooltip>,
    );
    const button = screen.getByRole("button", { name: "Mở" });
    await userEvent.pointer({ keys: "[TouchA]", target: button });
    expect(button).toHaveFocus();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole("tooltip")).toBeNull();
  });
});
