import { render, screen } from "@testing-library/react";
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
});
