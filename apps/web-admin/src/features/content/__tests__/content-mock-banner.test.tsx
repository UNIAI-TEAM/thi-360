import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ContentMockBanner } from "../components/content-mock-banner";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("dải dữ liệu mẫu của Ngân hàng câu hỏi và Nhập câu hỏi", () => {
  it("chỉ hiện khi cờ CONTENT_MOCK bật", () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "true");
    const { unmount } = renderWithProviders(<ContentMockBanner />);
    expect(screen.getByRole("note")).toBeInTheDocument();
    unmount();

    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    renderWithProviders(<ContentMockBanner />);
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });
});
