import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { MyExamsResponse } from "@/features/my-exams/api";
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
  it("hiện trạng thái tải, lỗi phiên, thử lại, Bài thi của tôi rỗng và thanh đầu trang học viên", async () => {
    const loadingView = renderWithProviders(
      <StudentHomeView loadCurrentSession={() => new Promise(() => {})} loadMyExams={async () => ({ items: [] })} />,
    );
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    const placeholder = screen.getByTestId("user-menu-placeholder");
    expect(screen.getByRole("banner")).toContainElement(placeholder);
    expect(placeholder).toHaveClass("h-11", "w-12.5");
    expect(screen.queryByRole("button", { name: "Menu tài khoản" })).toBeNull();
    loadingView.unmount();

    const failSession = vi.fn().mockRejectedValueOnce(new Error("boom")).mockResolvedValueOnce(STUDENT);
    const loadExams = vi.fn<() => Promise<MyExamsResponse>>().mockResolvedValue({ items: [] });
    renderWithProviders(<StudentHomeView loadCurrentSession={failSession} loadMyExams={loadExams} />);

    expect(await screen.findByText("Không tải được trang")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeInTheDocument();
    expect(screen.getByText(/Chào Học viên 01\./)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Bạn chưa có bài thi nào" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Khám phá bài thi" })).toHaveAttribute("href", "/#exams");

    const banner = screen.getByRole("banner");
    const nav = within(banner).getByRole("navigation", { name: "Điều hướng chính" });
    expect(within(nav).getByRole("link", { name: "Đơn của tôi" })).toHaveAttribute("href", "/account/orders");

    const themeButton = within(banner).getByRole("button", { name: "Chuyển sang chế độ tối" });
    const localeButton = within(banner).getByRole("button", { name: "Chọn ngôn ngữ" });
    const menuButton = within(banner).getByRole("button", { name: "Menu tài khoản" });
    expect(themeButton.compareDocumentPosition(localeButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(localeButton.compareDocumentPosition(menuButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(themeButton.compareDocumentPosition(menuButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(menuButton).toHaveTextContent("HV");
    expect(menuButton).toHaveClass("h-11", "min-w-12.5");
    expect(screen.queryByTestId("user-menu-placeholder")).toBeNull();
    expect(menuButton).toHaveClass("focus-visible:ring-primary-text");
    expect(menuButton.className).not.toContain("ring-primary-600/30");

    await userEvent.click(menuButton);
    expect(screen.queryByRole("menuitem", { name: "Trang quản trị" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Hồ sơ" })).toHaveAttribute("href", "/account/profile");
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("menuitem", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "/change-password?source=account-menu",
    );
  });

  it("tài khoản quản trị (access = MANAGE): hiện thông báo dẫn về trang quản trị, không gọi GET /me/exams và menu có Trang quản trị", async () => {
    const loadExams = vi.fn();
    renderWithProviders(<StudentHomeView loadCurrentSession={async () => ADMIN} loadMyExams={loadExams} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở trang quản trị" })).toHaveAttribute("href", "http://localhost:3001");
    expect(loadExams).not.toHaveBeenCalled();

    const trigger = screen.getByRole("button", { name: "Menu tài khoản" });
    await userEvent.click(trigger);
    expect(screen.getByRole("menuitem", { name: "Trang quản trị" })).toHaveAttribute("href", "http://localhost:3001");
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute("href", "/home");
  });
});
