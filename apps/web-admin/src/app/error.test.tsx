import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import ErrorPage from "./error";

describe("web-admin error page", () => {
  it("hiện trang lỗi 500 với một <main>, mã yêu cầu, nút Thử lại gọi reset và liên kết Về trang chủ", async () => {
    const reset = vi.fn();
    const err = Object.assign(new Error("boom"), { traceId: "trace-xyz-123" });
    renderWithProviders(<ErrorPage error={err} reset={reset} />);

    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Đã có lỗi máy chủ" })).toBeInTheDocument();
    expect(screen.getByText("Mã yêu cầu: trace-xyz-123")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute("href", "/");

    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("không có traceId thì dùng digest; bản tiếng Anh dùng chuỗi tiếng Anh", async () => {
    const en = (await import("@/messages/en.json")).default;
    const err = Object.assign(new Error("boom"), { digest: "digest-9" });
    renderWithProviders(<ErrorPage error={err} reset={vi.fn()} />, { locale: "en", messages: en });

    expect(screen.getByRole("heading", { level: 1, name: "Server error" })).toBeInTheDocument();
    expect(screen.getByText("Request ID: digest-9")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
