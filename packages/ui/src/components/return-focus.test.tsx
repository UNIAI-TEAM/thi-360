import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from "./dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "./drawer";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "./dropdown-menu";

// C-02 = U-01 = S-01: mọi hộp thoại của kit mở bằng state `open`, không có Trigger của Radix.
// FocusScope của Radix trả focus trong `setTimeout(0)` sau khi gỡ nội dung, nên các kiểm tra dùng `waitFor`.

function ControlledDialog({ onCloseAutoFocus }: { onCloseAutoFocus?: (event: Event) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Tạo lớp</button>
      <button>Nút khác</button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent closeLabel="Đóng" onCloseAutoFocus={onCloseAutoFocus}>
          <DialogTitle>Tạo lớp</DialogTitle>
          <DialogDescription>Nhập tên lớp</DialogDescription>
          <button onClick={() => setOpen(false)}>Hủy</button>
        </DialogContent>
      </Dialog>
    </>
  );
}

describe("trả focus về nơi mở khi đóng (C-02 / U-01 / S-01)", () => {
  it("Dialog có điều khiển, không có Trigger: Escape trả focus về nút đã mở", async () => {
    render(<ControlledDialog />);
    const opener = screen.getByRole("button", { name: "Tạo lớp" });
    await userEvent.click(opener);
    await screen.findByRole("dialog");
    expect(opener).not.toHaveFocus();

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("Dialog: đóng bằng nút trong hộp thoại và bằng nút × cũng trả focus", async () => {
    render(<ControlledDialog />);
    const opener = screen.getByRole("button", { name: "Tạo lớp" });

    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(opener).toHaveFocus());

    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Đóng" }));
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("Dialog mở bằng bàn phím: Enter mở, Escape đóng, focus về lại nút", async () => {
    render(<ControlledDialog />);
    const opener = screen.getByRole("button", { name: "Tạo lớp" });
    opener.focus();
    await userEvent.keyboard("{Enter}");
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("Dialog dựng có điều kiện (gỡ cả cây khi đóng) vẫn trả focus", async () => {
    function Conditional() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>Sửa</button>
          {open && (
            <Dialog open onOpenChange={setOpen}>
              <DialogContent closeLabel="Đóng">
                <DialogTitle>Sửa lớp</DialogTitle>
                <DialogDescription>Mô tả</DialogDescription>
              </DialogContent>
            </Dialog>
          )}
        </>
      );
    }
    render(<Conditional />);
    const opener = screen.getByRole("button", { name: "Sửa" });
    await userEvent.click(opener);
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("ConfirmDialog: hủy và Escape trả focus về nút đã mở", async () => {
    function Example() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>Khóa tài khoản</button>
          <ConfirmDialog
            open={open}
            onOpenChange={setOpen}
            title="Khóa"
            description="Khóa 1 tài khoản"
            confirmLabel="Đồng ý"
            cancelLabel="Hủy"
            closeLabel="Đóng"
            onConfirm={() => setOpen(false)}
          />
        </>
      );
    }
    render(<Example />);
    const opener = screen.getByRole("button", { name: "Khóa tài khoản" });

    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(opener).toHaveFocus());

    await userEvent.click(opener);
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());

    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Đồng ý" }));
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("Drawer có điều khiển: Escape và nút × trả focus về nút đã mở", async () => {
    function Example() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>Xem chi tiết</button>
          <Drawer open={open} onOpenChange={setOpen}>
            <DrawerContent closeLabel="Đóng">
              <DrawerTitle>Chi tiết</DrawerTitle>
              <DrawerDescription>Mô tả</DrawerDescription>
            </DrawerContent>
          </Drawer>
        </>
      );
    }
    render(<Example />);
    const opener = screen.getByRole("button", { name: "Xem chi tiết" });

    await userEvent.click(opener);
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());

    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Đóng" }));
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("hộp thoại lồng nhau: đóng xác nhận về nút trong hộp thoại ngoài, đóng hộp thoại ngoài về nút ban đầu", async () => {
    function Nested() {
      const [outer, setOuter] = useState(false);
      const [inner, setInner] = useState(false);
      return (
        <>
          <button onClick={() => setOuter(true)}>Quản lý thành viên</button>
          <Dialog open={outer} onOpenChange={setOuter}>
            <DialogContent closeLabel="Đóng ngoài">
              <DialogTitle>Thành viên</DialogTitle>
              <DialogDescription>Danh sách</DialogDescription>
              <button onClick={() => setInner(true)}>Loại khỏi lớp</button>
            </DialogContent>
          </Dialog>
          <ConfirmDialog
            open={inner}
            onOpenChange={setInner}
            title="Loại học viên"
            description="Loại 1 học viên"
            confirmLabel="Loại"
            cancelLabel="Hủy"
            closeLabel="Đóng trong"
            onConfirm={() => setInner(false)}
          />
        </>
      );
    }
    render(<Nested />);
    const opener = screen.getByRole("button", { name: "Quản lý thành viên" });
    await userEvent.click(opener);
    const innerOpener = await screen.findByRole("button", { name: "Loại khỏi lớp" });
    await userEvent.click(innerOpener);
    await screen.findByRole("dialog", { name: "Loại học viên" });

    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Loại học viên" })).toBeNull());
    await waitFor(() => expect(innerOpener).toHaveFocus());
    expect(screen.getByRole("dialog", { name: "Thành viên" })).toBeInTheDocument();

    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("hộp thoại ngoài đóng cùng lúc với xác nhận: focus về nút đã mở hộp thoại ngoài", async () => {
    function Both() {
      const [outer, setOuter] = useState(false);
      const [inner, setInner] = useState(false);
      return (
        <>
          <button onClick={() => setOuter(true)}>Sửa giá trị</button>
          <Dialog open={outer} onOpenChange={setOuter}>
            <DialogContent closeLabel="Đóng ngoài">
              <DialogTitle>Sửa</DialogTitle>
              <DialogDescription>Mô tả</DialogDescription>
              <button onClick={() => setInner(true)}>Xóa</button>
            </DialogContent>
          </Dialog>
          <ConfirmDialog
            open={inner}
            onOpenChange={setInner}
            title="Xóa giá trị"
            description="Xóa 1 giá trị"
            confirmLabel="Xóa hẳn"
            cancelLabel="Hủy"
            closeLabel="Đóng trong"
            onConfirm={() => {
              setInner(false);
              setOuter(false);
            }}
          />
        </>
      );
    }
    render(<Both />);
    const opener = screen.getByRole("button", { name: "Sửa giá trị" });
    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Xóa" }));
    await userEvent.click(await screen.findByRole("button", { name: "Xóa hẳn" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("hộp thoại A mở hộp thoại B rồi tự đóng: đóng B về nơi đã mở A", async () => {
    function Chained() {
      const [step, setStep] = useState<"none" | "a" | "b">("none");
      return (
        <>
          <button onClick={() => setStep("a")}>Đặt lại mật khẩu</button>
          <Dialog open={step === "a"} onOpenChange={(next) => !next && setStep("none")}>
            <DialogContent closeLabel="Đóng A">
              <DialogTitle>Đặt lại</DialogTitle>
              <DialogDescription>Mô tả</DialogDescription>
              <button onClick={() => setStep("b")}>Tiếp tục</button>
            </DialogContent>
          </Dialog>
          <Dialog open={step === "b"} onOpenChange={(next) => !next && setStep("none")}>
            <DialogContent closeLabel="Đóng B">
              <DialogTitle>Mật khẩu mới</DialogTitle>
              <DialogDescription>Mô tả</DialogDescription>
            </DialogContent>
          </Dialog>
        </>
      );
    }
    render(<Chained />);
    const opener = screen.getByRole("button", { name: "Đặt lại mật khẩu" });
    await userEvent.click(opener);
    await userEvent.click(await screen.findByRole("button", { name: "Tiếp tục" }));
    const second = await screen.findByRole("dialog", { name: "Mật khẩu mới" });
    // A đóng trong lúc B đang mở: không giành focus khỏi B.
    await waitFor(() => expect(second.contains(document.activeElement)).toBe(true));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(second.contains(document.activeElement)).toBe(true);

    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(opener).toHaveFocus());
  });

  function MenuThenDialog({ removeTriggerOnConfirm = false }: { removeTriggerOnConfirm?: boolean }) {
    const [open, setOpen] = useState(false);
    const [rowVisible, setRowVisible] = useState(true);
    return (
      <main tabIndex={-1} aria-label="Nội dung">
        {rowVisible && (
          <DropdownMenu>
            <DropdownMenuTrigger>Thao tác khác</DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onSelect={() => setOpen(true)}>Ngừng sử dụng</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        <ConfirmDialog
          open={open}
          onOpenChange={setOpen}
          title="Ngừng sử dụng"
          description="Ngừng 1 lớp"
          confirmLabel="Ngừng"
          cancelLabel="Hủy"
          closeLabel="Đóng"
          onConfirm={() => {
            if (removeTriggerOnConfirm) setRowVisible(false);
            setOpen(false);
          }}
        />
      </main>
    );
  }

  it("mở từ mục menu thả (bàn phím): mục menu đã mất khi đóng, focus về nút mở menu", async () => {
    render(<MenuThenDialog />);
    const trigger = screen.getByRole("button", { name: "Thao tác khác" });
    trigger.focus();
    await userEvent.keyboard("{Enter}");
    const item = await screen.findByRole("menuitem", { name: "Ngừng sử dụng" });
    await waitFor(() => expect(item).toHaveFocus());
    await userEvent.keyboard("{Enter}");
    const dialog = await screen.findByRole("dialog", { name: "Ngừng sử dụng" });
    expect(screen.queryByRole("menu")).toBeNull();
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("mở từ mục menu thả (chuột): hủy trả focus về nút mở menu", async () => {
    render(<MenuThenDialog />);
    const trigger = screen.getByRole("button", { name: "Thao tác khác" });
    await userEvent.click(trigger);
    await userEvent.click(await screen.findByRole("menuitem", { name: "Ngừng sử dụng" }));
    await userEvent.click(await screen.findByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("nơi mở bị xóa khi xác nhận (xóa dòng): focus về vùng chứa gần nhất nhận được focus, không rơi về body", async () => {
    render(<MenuThenDialog removeTriggerOnConfirm />);
    const trigger = screen.getByRole("button", { name: "Thao tác khác" });
    await userEvent.click(trigger);
    await userEvent.click(await screen.findByRole("menuitem", { name: "Ngừng sử dụng" }));
    await userEvent.click(await screen.findByRole("button", { name: "Ngừng" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(trigger).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("main")).toHaveFocus());
  });

  it("nơi gọi chặn sự kiện trong onCloseAutoFocus thì kit không tự đặt focus", async () => {
    render(
      <ControlledDialog
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          screen.getByRole("button", { name: "Nút khác" }).focus();
        }}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Tạo lớp" }));
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.getByRole("button", { name: "Nút khác" })).toHaveFocus());
  });

  it("không giành focus khi nơi gọi đã tự chuyển focus sang chỗ khác lúc đóng", async () => {
    function MovesFocus() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>Thêm giá trị</button>
          <input aria-label="Tìm kiếm" />
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogContent closeLabel="Đóng">
              <DialogTitle>Thêm</DialogTitle>
              <DialogDescription>Mô tả</DialogDescription>
              <button
                onClick={() => {
                  setOpen(false);
                  queueMicrotask(() => screen.getByRole("textbox", { name: "Tìm kiếm" }).focus());
                }}
              >
                Lưu
              </button>
            </DialogContent>
          </Dialog>
        </>
      );
    }
    render(<MovesFocus />);
    await userEvent.click(screen.getByRole("button", { name: "Thêm giá trị" }));
    await userEvent.click(await screen.findByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.getByRole("textbox", { name: "Tìm kiếm" })).toHaveFocus();
  });

  it("Dialog mở bằng DialogTrigger của Radix vẫn trả focus về Trigger như trước", async () => {
    render(
      <Dialog>
        <DialogTrigger>Mở</DialogTrigger>
        <DialogContent closeLabel="Đóng">
          <DialogTitle>Tiêu đề</DialogTitle>
          <DialogDescription>Mô tả</DialogDescription>
        </DialogContent>
      </Dialog>,
    );
    const trigger = screen.getByRole("button", { name: "Mở" });
    await userEvent.click(trigger);
    await screen.findByRole("dialog");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(trigger).toHaveFocus());
  });
});
