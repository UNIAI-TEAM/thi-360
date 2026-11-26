import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { PublicFooter } from "./public-footer";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("PublicFooter", () => {
  it("renders web logo, company details, 4 columns, legal links and copyright bar in Vietnamese without dead social links", () => {
    renderWithProviders(<PublicFooter variant="landing" />);

    expect(screen.getByRole("img", { name: "Thi360" })).toHaveAttribute("src", "/logo-full.png");
    expect(screen.getByText("CÔNG TY CỔ PHẦN ĐẦU TƯ VÀ PHÁT TRIỂN GIÁO DỤC HSA")).toBeInTheDocument();
    expect(screen.getByText(/0109600534/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "hsaeducation.jsc@gmail.com" })).toHaveAttribute(
      "href",
      "mailto:hsaeducation.jsc@gmail.com",
    );
    expect(screen.getByRole("link", { name: "0988.371.194" })).toHaveAttribute("href", "tel:0988371194");

    expect(screen.getByRole("heading", { name: "DANH MỤC" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "CHĂM SÓC HỌC VIÊN" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "CHÍNH SÁCH & ĐIỀU KHOẢN" })).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "Điều khoản giao dịch chung" })).toHaveAttribute(
      "href",
      "/dieu-khoan-giao-dich-chung",
    );
    expect(screen.getByRole("link", { name: "Chính sách bảo mật thông tin" })).toHaveAttribute(
      "href",
      "/chinh-sach-bao-mat-thong-tin",
    );

    expect(screen.queryByRole("link", { name: "Facebook" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Đã thông báo Bộ Công Thương" })).not.toBeInTheDocument();
    expect(screen.getByText("© 2026 Thi360 – All Rights Reserved.")).toBeInTheDocument();
  });

  it("renders MOIT badge as an external link only when NEXT_PUBLIC_THI360_MOIT_URL is configured", () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_MOIT_URL", "https://online.gov.vn/Home/WebDetails/12345");
    renderWithProviders(<PublicFooter />);

    const badgeLink = screen.getByRole("link", { name: "Đã thông báo Bộ Công Thương" });
    expect(badgeLink).toHaveAttribute("href", "https://online.gov.vn/Home/WebDetails/12345");
    expect(badgeLink).toHaveAttribute("target", "_blank");
  });

  it("renders localized labels in English without Vietnamese text", () => {
    renderWithProviders(<PublicFooter />, { locale: "en", messages: enMessages });

    expect(screen.getByRole("heading", { name: "CATEGORIES" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "STUDENT SUPPORT" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "POLICIES & TERMS" })).toBeInTheDocument();
  });
});
