import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Switch } from "./switch";

describe("Switch", () => {
  it("bấm để bật rồi tắt", async () => {
    const onCheckedChange = vi.fn();
    render(<Switch aria-label="Kích hoạt" onCheckedChange={onCheckedChange} />);
    const toggle = screen.getByRole("switch", { name: "Kích hoạt" });
    expect(toggle).not.toBeChecked();
    await userEvent.click(toggle);
    expect(toggle).toBeChecked();
    await userEvent.click(toggle);
    expect(toggle).not.toBeChecked();
    expect(onCheckedChange.mock.calls.map(([value]) => value)).toEqual([true, false]);
  });

  it("bật bằng phím Space", async () => {
    render(<Switch aria-label="Kích hoạt" />);
    await userEvent.tab();
    await userEvent.keyboard(" ");
    expect(screen.getByRole("switch", { name: "Kích hoạt" })).toBeChecked();
  });

  it("bị khóa thì không đổi", async () => {
    render(<Switch aria-label="Kích hoạt" disabled />);
    const toggle = screen.getByRole("switch", { name: "Kích hoạt" });
    await userEvent.click(toggle);
    expect(toggle).not.toBeChecked();
  });
});
