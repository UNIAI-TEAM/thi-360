import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";
import { UiStringsProvider } from "./ui-strings";

type SetupProps = { loading?: boolean; error?: string } & (
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

  // U-15: không khóa nút để chặn việc kiểm tra; cho bấm rồi chỉ vào ô còn thiếu.
  it("requireReason: nút xác nhận không bị khóa; bấm khi lý do trống thì báo lỗi ở ô lý do, focus vào ô và không gọi onConfirm", async () => {
    const { onConfirm } = setup({ reasonLabel: "Lý do", requireReason: true });
    const confirm = screen.getByRole("button", { name: "Khóa" });
    const reason = screen.getByRole("textbox", { name: /Lý do/ });
    expect(confirm).toBeEnabled();
    expect(reason).not.toHaveAttribute("aria-invalid");
    expect(reason).toHaveAttribute("aria-required", "true");
    expect(screen.queryByText("Vui lòng nhập lý do.")).toBeNull();

    await userEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();
    expect(reason).toHaveAttribute("aria-invalid", "true");
    expect(reason).toHaveFocus();
    // Dòng lỗi gắn với ô qua aria-describedby và được đọc lên (role="alert").
    expect(reason).toHaveAccessibleDescription("Vui lòng nhập lý do.");
    expect(screen.getByRole("alert")).toHaveTextContent("Vui lòng nhập lý do.");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("requireReason: chỉ có khoảng trắng vẫn là trống; nhập lý do thì hết lỗi và xác nhận được với lý do đã cắt khoảng trắng", async () => {
    const { onConfirm } = setup({ reasonLabel: "Lý do", requireReason: true });
    const confirm = screen.getByRole("button", { name: "Khóa" });
    const reason = screen.getByRole("textbox", { name: /Lý do/ });

    await userEvent.type(reason, "   ");
    await userEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();
    expect(reason).toHaveAttribute("aria-invalid", "true");

    await userEvent.type(reason, " nghỉ học ");
    expect(reason).not.toHaveAttribute("aria-invalid");
    expect(screen.queryByText("Vui lòng nhập lý do.")).toBeNull();

    await userEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith("nghỉ học");
  });

  it("requireReason: lỗi của ô lý do và lỗi của lần xác nhận (prop error) cùng hiện, mỗi cái một vùng alert", async () => {
    setup({ reasonLabel: "Lý do", requireReason: true, error: "Máy chủ từ chối." });
    await userEvent.click(screen.getByRole("button", { name: "Khóa" }));
    const alerts = screen.getAllByRole("alert").map((alert) => alert.textContent);
    expect(alerts).toEqual(expect.arrayContaining(["Vui lòng nhập lý do.", "Máy chủ từ chối."]));
    expect(screen.getByRole("textbox", { name: /Lý do/ })).toHaveAccessibleDescription("Vui lòng nhập lý do.");
  });

  it("requireReason: dòng lỗi theo ngôn ngữ của UiStringsProvider", async () => {
    const onConfirm = vi.fn();
    render(
      <UiStringsProvider locale="en">
        <ConfirmDialog
          open
          onOpenChange={() => {}}
          title="Lock account"
          description="Lock 1 account"
          confirmLabel="Lock"
          reasonLabel="Reason"
          requireReason
          onConfirm={onConfirm}
        />
      </UiStringsProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Lock" }));
    expect(screen.getByRole("textbox", { name: /Reason/ })).toHaveAccessibleDescription("Please enter a reason.");
    expect(onConfirm).not.toHaveBeenCalled();
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

  it("có lỗi: hiện trong hộp thoại dưới dạng alert, hộp thoại vẫn mở và thử lại được", async () => {
    const { onConfirm } = setup({ error: "Không khóa được: tài khoản đang được dùng." });
    const dialog = screen.getByRole("dialog");
    const alert = screen.getByRole("alert");
    expect(dialog).toContainElement(alert);
    expect(alert).toHaveTextContent("Không khóa được: tài khoản đang được dùng.");
    await userEvent.click(screen.getByRole("button", { name: "Khóa" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("không có lỗi: không có vùng alert", () => {
    setup();
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
