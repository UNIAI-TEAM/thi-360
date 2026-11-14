import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { StudentHomeView } from "./student-home-view";

const STUDENT = {
  id: "u-stu",
  username: "student01",
  fullName: "Học viên 01",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

const ADMIN = {
  id: "u-adm",
  username: "ops.admin",
  fullName: "Vận hành",
  realm: "APP" as const,
  access: "MANAGE" as const,
  permissions: ["USER.VIEW"],
  homeUrl: "http://localhost:3001",
};

describe("StudentHomeView", () => {
  it("hiện trạng thái tải, lỗi, thử lại và dữ liệu rỗng cho học viên", async () => {
    const loadingView = renderWithProviders(
      <StudentHomeView loadCurrentSession={() => new Promise(() => {})} />,
    );
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    loadingView.unmount();

    const failLoader = vi.fn().mockRejectedValueOnce(new Error("boom")).mockResolvedValueOnce(STUDENT);
    renderWithProviders(<StudentHomeView loadCurrentSession={failLoader} />);
    expect(await screen.findByText("Không tải được trang")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Chào Học viên 01" })).toBeInTheDocument();
    expect(screen.getByText("Chưa có bài thi nào")).toBeInTheDocument();

    const banner = screen.getByRole("banner");
    const themeButton = within(banner).getByRole("button", { name: "Chuyển sang chế độ tối" });
    const localeButton = within(banner).getByRole("button", { name: "Chọn ngôn ngữ" });
    const menuButton = within(banner).getByRole("button", { name: "Menu tài khoản" });
    expect(themeButton.compareDocumentPosition(localeButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(localeButton.compareDocumentPosition(menuButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(themeButton.compareDocumentPosition(menuButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(menuButton).toHaveTextContent("HV");
    await userEvent.click(menuButton);
    expect(screen.queryByRole("menuitem", { name: "Trang quản trị" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Hồ sơ" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute("href", "/home");
  });

  it("menu avatar hiện Trang quản trị khi access = MANAGE", async () => {
    renderWithProviders(<StudentHomeView loadCurrentSession={async () => ADMIN} />);
    const trigger = await screen.findByRole("button", { name: "Menu tài khoản" });
    await userEvent.click(trigger);
    expect(screen.getByRole("menuitem", { name: "Trang quản trị" })).toHaveAttribute("href", "http://localhost:3001");
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute("href", "/home");
  });
});
