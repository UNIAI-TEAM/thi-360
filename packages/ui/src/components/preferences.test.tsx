import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "./dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "./drawer";
import { EmptyState } from "./empty-state";
import { LocaleSwitcher } from "./locale-switcher";
import { Pagination } from "./pagination";
import { PasswordField } from "./password-field";
import { ThemeToggle } from "./theme-toggle";
import { UiPreferencesProvider } from "./ui-preferences";
import { UiStringsProvider } from "./ui-strings";

afterEach(() => {
  delete document.documentElement.dataset.theme;
  document.cookie = "thi360_theme=; Max-Age=0; Path=/";
  document.cookie = "thi360_locale=; Max-Age=0; Path=/";
});

describe("ThemeToggle", () => {
  it("đổi data-theme, ghi cookie và cập nhật icon/nhãn theo ngôn ngữ khi bấm hoặc dùng bàn phím", async () => {
    const user = userEvent.setup();
    render(
      <UiStringsProvider locale="vi">
        <UiPreferencesProvider initialTheme="light" cookieDomain="">
          <ThemeToggle />
        </UiPreferencesProvider>
      </UiStringsProvider>,
    );

    const button = screen.getByRole("button", { name: "Chuyển sang chế độ tối" });
    expect(button).toHaveAttribute("aria-pressed", "false");

    await user.click(button);
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(document.cookie).toContain("thi360_theme=dark");
    expect(screen.getByRole("button", { name: "Chuyển sang chế độ sáng" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    await user.keyboard("{Enter}");
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(document.cookie).toContain("thi360_theme=light");
  });

  it("hiện tooltip khi focus bàn phím và dùng nhãn tiếng Anh khi locale=en", async () => {
    const user = userEvent.setup();
    render(
      <UiStringsProvider locale="en">
        <UiPreferencesProvider initialTheme="dark">
          <ThemeToggle />
        </UiPreferencesProvider>
      </UiStringsProvider>,
    );

    const button = screen.getByRole("button", { name: "Switch to light mode" });
    await user.tab();
    expect(button).toHaveFocus();
    await waitFor(() => {
      expect(screen.getByRole("tooltip")).toHaveTextContent("Switch to light mode");
    });
  });
});

describe("LocaleSwitcher", () => {
  it("mở menu Tiếng Việt / English, hỗ trợ bàn phím, ghi cookie và gọi onLocaleChange", async () => {
    const user = userEvent.setup();
    const onLocaleChange = vi.fn();
    render(
      <UiStringsProvider locale="vi">
        <LocaleSwitcher locale="vi" onLocaleChange={onLocaleChange} />
      </UiStringsProvider>,
    );

    const button = screen.getByRole("button", { name: "Chọn ngôn ngữ" });
    await user.tab();
    expect(button).toHaveFocus();
    await waitFor(() => {
      expect(screen.getByRole("tooltip")).toHaveTextContent("Chọn ngôn ngữ");
    });

    await user.keyboard("{Enter}");
    const viItem = await screen.findByRole("menuitem", { name: "Tiếng Việt" });
    const enItem = screen.getByRole("menuitem", { name: "English" });
    expect(viItem).toHaveAttribute("aria-current", "true");
    expect(enItem).toBeInTheDocument();

    await user.click(enItem);
    expect(onLocaleChange).toHaveBeenCalledWith("en");
  });

  it("ghi cookie thi360_locale=en khi không truyền onLocaleChange", async () => {
    const user = userEvent.setup();
    render(
      <UiStringsProvider locale="en">
        <LocaleSwitcher locale="en" />
      </UiStringsProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Change language" }));
    await user.click(await screen.findByRole("menuitem", { name: "Tiếng Việt" }));
    expect(document.cookie).toContain("thi360_locale=vi");
  });
});

describe("UiStringsProvider defaults", () => {
  it("cung cấp nhãn tiếng Anh mặc định cho Pagination, PasswordField, EmptyState, Dialog, Drawer, ConfirmDialog", () => {
    const { unmount } = render(
      <UiStringsProvider locale="en">
        <Pagination page={1} pageSize={10} total={30} onPageChange={() => {}} />
        <PasswordField aria-label="New password" showRules allowGenerate />
        <EmptyState description="Nothing here yet" />
      </UiStringsProvider>,
    );

    expect(screen.getByRole("navigation", { name: "Pagination" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous page" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next page" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show password" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate random" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Password requirements" })).toBeInTheDocument();
    expect(screen.getByText("8 to 64 characters")).toBeInTheDocument();
    expect(screen.getByText("No data")).toBeInTheDocument();
    unmount();

    const dialogRender = render(
      <UiStringsProvider locale="en">
        <Dialog open>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Dialog Title</DialogTitle>
              <DialogDescription>Dialog Desc</DialogDescription>
            </DialogHeader>
          </DialogContent>
        </Dialog>
      </UiStringsProvider>,
    );
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
    dialogRender.unmount();

    const drawerRender = render(
      <UiStringsProvider locale="en">
        <Drawer open>
          <DrawerContent>
            <DrawerHeader>
              <DrawerTitle>Drawer Title</DrawerTitle>
              <DrawerDescription>Drawer Desc</DrawerDescription>
            </DrawerHeader>
          </DrawerContent>
        </Drawer>
      </UiStringsProvider>,
    );
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
    drawerRender.unmount();

    render(
      <UiStringsProvider locale="en">
        <ConfirmDialog open title="Confirm?" description="Proceed" onOpenChange={() => {}} onConfirm={() => {}} />
      </UiStringsProvider>,
    );
    expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirm" })).toBeInTheDocument();
  });
});
