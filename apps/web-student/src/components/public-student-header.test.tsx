import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { PublicStudentHeader } from "./public-student-header";

vi.mock("next/navigation", () => ({ usePathname: () => "/" }));

const STUDENT = {
  id: "u-stu",
  username: "student01",
  fullName: "Học viên 01",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

describe("PublicStudentHeader", () => {
  it("giữ chỗ nút tài khoản khi phiên đang tải, rồi hiện bản khách khi chưa đăng nhập", async () => {
    renderWithProviders(<PublicStudentHeader loadCurrentSession={async () => null} />);

    expect(screen.getByTestId("user-menu-placeholder")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Đăng nhập" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng ký" })).toBeInTheDocument();
    expect(screen.queryByTestId("user-menu-placeholder")).toBeNull();
  });

  it("hiện điều hướng và menu tài khoản khi đã đăng nhập", async () => {
    renderWithProviders(<PublicStudentHeader loadCurrentSession={async () => STUDENT} />);

    expect(await screen.findByRole("button", { name: "Menu tài khoản" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute("href", "/home");
    expect(screen.queryByRole("link", { name: "Đăng nhập" })).toBeNull();
  });

  it("lỗi tải phiên (không phải 401) hiện như khách, không văng lỗi", async () => {
    renderWithProviders(
      <PublicStudentHeader
        loadCurrentSession={async () => {
          throw new ApiStatusError(500, "boom");
        }}
      />,
    );

    expect(await screen.findByRole("link", { name: "Đăng nhập" })).toBeInTheDocument();
  });
});
