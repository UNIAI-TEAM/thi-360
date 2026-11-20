import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { SupportSection } from "./landing-sections";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("SupportSection (AC-16)", () => {
  it("có NEXT_PUBLIC_SUPPORT_*: hiện điện thoại, email, giờ hỗ trợ và nút liên hệ", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "0900 000 000");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "ho-tro@example.test");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_HOURS", "8:00 - 17:00, thứ Hai đến thứ Sáu");
    renderWithProviders(<SupportSection />);

    const section = screen.getByRole("region", { name: "Bạn cần hỗ trợ?" });
    expect(within(section).getByText("Điện thoại").nextElementSibling).toHaveTextContent("0900 000 000");
    expect(within(section).getByText("Email").nextElementSibling).toHaveTextContent("ho-tro@example.test");
    expect(within(section).getByText("Giờ hỗ trợ").nextElementSibling).toHaveTextContent("thứ Hai đến thứ Sáu");
    expect(within(section).getByRole("link", { name: "Liên hệ hỗ trợ" })).toHaveAttribute(
      "href",
      "mailto:ho-tro@example.test",
    );
    expect(section).not.toHaveTextContent("đang được cập nhật");
  });

  it("chỉ cấu hình một kênh: chỉ hiện kênh đó, không bịa kênh còn lại", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "0900 000 000");
    renderWithProviders(<SupportSection />);

    const section = screen.getByRole("region", { name: "Bạn cần hỗ trợ?" });
    expect(within(section).getByText("Điện thoại")).toBeInTheDocument();
    expect(within(section).queryByText("Email")).toBeNull();
    expect(within(section).queryByText("Giờ hỗ trợ")).toBeNull();
    expect(within(section).getByRole("link", { name: "Liên hệ hỗ trợ" })).toHaveAttribute("href", "tel:0900 000 000");
  });

  it("biến trống: 'Thông tin liên hệ đang được cập nhật', không có nút hay số liên hệ", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "  ");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_HOURS", "");
    renderWithProviders(<SupportSection />);

    const section = screen.getByRole("region", { name: "Bạn cần hỗ trợ?" });
    expect(section).toHaveTextContent("Thông tin liên hệ đang được cập nhật.");
    expect(within(section).queryByRole("link")).toBeNull();
    expect(within(section).queryByRole("term")).toBeNull();
  });

  it("tiếng Anh: tiêu đề và nhãn bằng tiếng Anh", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "ho-tro@example.test");
    renderWithProviders(<SupportSection />, { locale: "en", messages: enMessages });

    const section = screen.getByRole("region", { name: "Need help?" });
    expect(within(section).getByText("Email")).toBeInTheDocument();
    expect(within(section).getByRole("link", { name: "Contact support" })).toBeInTheDocument();
  });
});
