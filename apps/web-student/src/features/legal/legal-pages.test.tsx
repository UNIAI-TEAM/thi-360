import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { formatSiteBase, resolveSiteBase } from "@/lib/site-url";
import { renderWithProviders } from "@/test/render";
import { PrivacyPolicyView } from "./privacy-policy-view";
import { TermsOfServiceView } from "./terms-of-service-view";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("Legal pages", () => {
  const site = formatSiteBase("https://thi360.example.vn");

  it("renders PrivacyPolicyView with dynamic web base URL, unified hotline and zero hardcoded hsavnu.edu.vn", () => {
    const { container } = renderWithProviders(<PrivacyPolicyView site={site} />);

    expect(screen.getByRole("heading", { level: 1, name: "CHÍNH SÁCH BẢO MẬT THÔNG TIN" })).toBeInTheDocument();
    expect(screen.getByText(/\(thi360\.example\.vn\)/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "0988.371.194" })).toHaveAttribute("href", "tel:0988371194");
    expect(screen.getByRole("link", { name: "https://thi360.example.vn/" })).toHaveAttribute(
      "href",
      "https://thi360.example.vn/",
    );
    expect(container.textContent).not.toContain("hsavnu.edu.vn");
  });

  it("resolveSiteBase uses trusted env configuration only", async () => {
    vi.stubEnv("THI360_WEB_URL", "https://thi360.zone17.click/");
    await expect(resolveSiteBase()).resolves.toEqual({
      siteUrl: "https://thi360.zone17.click/",
      siteHost: "thi360.zone17.click",
    });
  });

  it("renders TermsOfServiceView using Thi360 brand, dynamic web base URL, and refund table", () => {
    const { container } = renderWithProviders(<TermsOfServiceView site={site} />);

    expect(screen.getByRole("heading", { level: 1, name: "ĐIỀU KIỆN GIAO DỊCH CHUNG" })).toBeInTheDocument();
    expect(screen.getByText(/áp dụng đối với tất cả.*của Thi360\./)).toBeInTheDocument();
    expect(screen.getByText(/website https:\/\/thi360\.example\.vn\//)).toBeInTheDocument();
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/HSA Education/i);
    expect(container.textContent).not.toContain("hsavnu.edu.vn");
  });

  it("supports English locale cleanly for both legal views", () => {
    const p = renderWithProviders(<PrivacyPolicyView site={site} />, { locale: "en", messages: enMessages });
    expect(screen.getByRole("heading", { level: 1, name: "INFORMATION PRIVACY POLICY" })).toBeInTheDocument();
    p.unmount();

    renderWithProviders(<TermsOfServiceView site={site} />, { locale: "en", messages: enMessages });
    expect(screen.getByRole("heading", { level: 1, name: "GENERAL TRANSACTION TERMS" })).toBeInTheDocument();
  });
});
