import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";
import ErrorPage from "./error";

describe("web-student error page", () => {
  it("hiển thị mã yêu cầu traceId khi có", () => {
    const err = Object.assign(new Error("boom"), { traceId: "trace-xyz-123" });
    render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <ErrorPage error={err} reset={vi.fn()} />
      </NextIntlClientProvider>,
    );

    expect(screen.getByText("Đã có lỗi máy chủ")).toBeInTheDocument();
    expect(screen.getByText("Mã yêu cầu: trace-xyz-123")).toBeInTheDocument();
  });
});
