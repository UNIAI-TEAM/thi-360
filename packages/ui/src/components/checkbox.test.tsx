import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Checkbox } from "./checkbox";

describe("Checkbox", () => {
  it("bấm để chọn rồi bỏ chọn, báo giá trị mới", async () => {
    const onCheckedChange = vi.fn();
    render(<Checkbox aria-label="Chọn dòng" onCheckedChange={onCheckedChange} />);
    const box = screen.getByRole("checkbox", { name: "Chọn dòng" });
    await userEvent.click(box);
    expect(box).toBeChecked();
    await userEvent.click(box);
    expect(box).not.toBeChecked();
    expect(onCheckedChange.mock.calls.map(([value]) => value)).toEqual([true, false]);
  });

  it("chọn một phần: aria-checked=mixed", () => {
    render(<Checkbox aria-label="Chọn tất cả" checked="indeterminate" />);
    expect(screen.getByRole("checkbox", { name: "Chọn tất cả" })).toBePartiallyChecked();
  });

  it("bị khóa thì không đổi", async () => {
    const onCheckedChange = vi.fn();
    render(<Checkbox aria-label="Chọn dòng" disabled onCheckedChange={onCheckedChange} />);
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn dòng" }));
    expect(onCheckedChange).not.toHaveBeenCalled();
  });
});
