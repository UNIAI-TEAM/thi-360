import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";

type SetupProps = { loading?: boolean } & (
  | { reasonLabel: string; requireReason?: boolean }
  | { reasonLabel?: undefined; requireReason?: undefined }
);

function setup(props: SetupProps = {}) {
  const onConfirm = vi.fn();
  const onOpenChange = vi.fn();
  render(
    <ConfirmDialog
      open
      onOpenChange={onOpenChange}
      title="Khóa tài khoản"
      description="Khóa 3 tài khoản đã chọn"
      confirmLabel="Khóa"
      cancelLabel="Hủy"
      closeLabel="Đóng"
      onConfirm={onConfirm}
      {...props}
    />,
  );
  return { onConfirm, onOpenChange };
}

describe("ConfirmDialog", () => {
  it("không có ô lý do: xác nhận gọi onConfirm với chuỗi rỗng", async () => {
    const { onConfirm } = setup();
    expect(screen.queryByRole("textbox")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Khóa" }));
    expect(onConfirm).toHaveBeenCalledWith("");
  });

  it("lý do tùy chọn: xác nhận được khi để trống", async () => {
    const { onConfirm } = setup({ reasonLabel: "Lý do" });
    expect(screen.getByRole("textbox", { name: "Lý do" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Khóa" }));
    expect(onConfirm).toHaveBeenCalledWith("");
  });

  it("requireReason: nút xác nhận khóa tới khi có lý do, và lý do được cắt khoảng trắng", async () => {
    const { onConfirm } = setup({ reasonLabel: "Lý do", requireReason: true });
    const confirm = screen.getByRole("button", { name: "Khóa" });
    expect(confirm).toBeDisabled();

    const reason = screen.getByRole("textbox", { name: /Lý do/ });
    await userEvent.type(reason, "   ");
    expect(confirm).toBeDisabled();

    await userEvent.type(reason, " nghỉ học ");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledWith("nghỉ học");
  });

  it("mở lại sau khi đóng thì ô lý do trống", async () => {
    const { rerender } = render(
      <ConfirmDialog
        open
        onOpenChange={() => {}}
        title="Khóa"
        description="Mô tả"
        confirmLabel="Khóa"
        cancelLabel="Hủy"
        closeLabel="Đóng"
        reasonLabel="Lý do"
        onConfirm={() => {}}
      />,
    );
    await userEvent.type(screen.getByRole("textbox", { name: "Lý do" }), "nghỉ học");
    const closed = (open: boolean) => (
      <ConfirmDialog
        open={open}
        onOpenChange={() => {}}
        title="Khóa"
        description="Mô tả"
        confirmLabel="Khóa"
        cancelLabel="Hủy"
        closeLabel="Đóng"
        reasonLabel="Lý do"
        onConfirm={() => {}}
      />
    );
    rerender(closed(false));
    rerender(closed(true));
    expect(screen.getByRole("textbox", { name: "Lý do" })).toHaveValue("");
  });

  it("hủy gọi onOpenChange(false) và không gọi onConfirm", async () => {
    const { onConfirm, onOpenChange } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("đang xử lý: nút xác nhận đặt aria-disabled/aria-busy và nút hủy bị khóa", () => {
    setup({ loading: true });
    const confirm = screen.getByRole("button", { name: "Khóa" });
    expect(confirm).toHaveAttribute("aria-disabled", "true");
    expect(confirm).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("button", { name: "Hủy" })).toBeDisabled();
  });

  it("đang xử lý: Escape, nút × và bấm ra ngoài không đóng hộp thoại", async () => {
    const { onOpenChange } = setup({ loading: true });
    await userEvent.keyboard("{Escape}");
    await userEvent.click(screen.getByRole("button", { name: "Đóng" }));
    fireEvent.pointerDown(document.body);
    expect(onOpenChange).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("đang xử lý: giữ nguyên lý do đã nhập và không cho sửa", async () => {
    const onOpenChange = vi.fn();
    const props = {
      open: true,
      onOpenChange,
      title: "Khóa",
      description: "Mô tả",
      confirmLabel: "Khóa",
      cancelLabel: "Hủy",
      closeLabel: "Đóng",
      reasonLabel: "Lý do",
      onConfirm: () => {},
    };
    const { rerender } = render(<ConfirmDialog {...props} />);
    await userEvent.type(screen.getByRole("textbox", { name: "Lý do" }), "nghỉ học");
    rerender(<ConfirmDialog {...props} loading />);
    const reason = screen.getByRole("textbox", { name: "Lý do" });
    expect(reason).toHaveValue("nghỉ học");
    expect(reason).toBeDisabled();
  });

  it("không xử lý: Escape đóng hộp thoại", async () => {
    const { onOpenChange } = setup();
    await userEvent.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
