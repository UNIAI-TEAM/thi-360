import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import type { KnowledgeRef } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";

const LIMIT_REF: KnowledgeRef = { nodeId: "kn-1D4-2-3", code: "1D4-2-3", name: "x", primary: true };

describe("KnowledgePickerDialog (canvas ChonKienThuc)", () => {
  it("mo san nhanh chua muc da chon, tick them va chon kien thuc chinh", async () => {
    const onApply = vi.fn();
    renderWithProviders(
      <KnowledgePickerDialog
        open
        onOpenChange={() => {}}
        value={[LIMIT_REF]}
        onApply={onApply}
        defaultSubjectCode="MATH"
        defaultGradeCode="GRADE_11"
      />,
    );
    const dialog = await screen.findByRole("dialog", { name: "Chọn kiến thức (MAPID)" });
    const tree = within(dialog).getByRole("region", { name: /Cấu trúc kiến thức/ });
    // Nhanh 1D4 › 1D4-2 mo san vi chua muc da chon; so kien thuc ben duoi nhanh hien ben phai.
    expect(await within(tree).findByRole("button", { name: "Thu gọn 1D4-2 Giới hạn của hàm số" })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(within(tree).getByText("1D4-2-3").closest("label")).toBeInTheDocument();

    await userEvent.click(within(tree).getByRole("checkbox", { name: /1D4-2-4/ }));
    const selected = within(dialog).getByRole("region", { name: "Đã chọn (2)" });
    await userEvent.click(within(selected).getAllByRole("radio", { name: /Kiến thức chính/ })[1]!);
    await userEvent.click(within(dialog).getByRole("button", { name: "Áp dụng (2)" }));

    expect(onApply).toHaveBeenCalledWith([
      expect.objectContaining({ nodeId: "kn-1D4-2-3", primary: false }),
      expect.objectContaining({ nodeId: "kn-1D4-2-4", primary: true }),
    ]);
  }, 20_000);

  it("che do loc: tick duoc Chuong, Bai va khong co kien thuc chinh", async () => {
    const onApply = vi.fn();
    renderWithProviders(
      <KnowledgePickerDialog
        open
        onOpenChange={() => {}}
        value={[]}
        onApply={onApply}
        defaultSubjectCode="MATH"
        withPrimary={false}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        applyLabel={(count) => `OK ${count}`}
      />,
    );
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByRole("searchbox", { name: "Tìm kiến thức" }), "1D4-2");
    await userEvent.click(await within(dialog).findByRole("checkbox", { name: /1D4-2 Giới hạn của hàm số/ }));
    expect(within(dialog).queryByRole("radio")).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "OK 1" }));
    expect(onApply).toHaveBeenCalledWith([expect.objectContaining({ nodeId: "kn-1D4-2", primary: false })]);
  });
});
