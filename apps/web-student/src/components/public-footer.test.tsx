import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { PublicFooter } from "./public-footer";

describe("PublicFooter", () => {
  it("renders web logo, company details, 4 columns, MOIT badge and copyright bar in Vietnamese", () => {
    renderWithProviders(<PublicFooter variant="landing" />);

    expect(screen.getByRole("img", { name: "Thi 360" })).toHaveAttribute("src", "/logo-full.png");
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

    expect(screen.getByRole("img", { name: "Đã thông báo Bộ Công Thương" })).toBeInTheDocument();
    expect(screen.getByText("© 2026 Thi360 – All Rights Reserved.")).toBeInTheDocument();
  });

  it("renders localized labels in English without Vietnamese text", () => {
    renderWithProviders(<PublicFooter />, { locale: "en", messages: enMessages });

    expect(screen.getByRole("heading", { name: "CATEGORIES" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "STUDENT SUPPORT" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "POLICIES & TERMS" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Registered with the Ministry of Industry and Trade" })).toBeInTheDocument();
  });
});
