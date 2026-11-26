import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { VnpayReturnResponse } from "@/features/checkout/api";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { CheckoutAppReturnView } from "./checkout-app-return-view";

const navState = {
  search: "",
};

vi.mock("next/navigation", () => ({
  usePathname: () => "/checkout/result/app",
  useSearchParams: () => new URLSearchParams(navState.search),
}));

describe("CheckoutAppReturnView", () => {
  beforeEach(() => {
    navState.search = "";
    window.history.replaceState({}, "", "/");
  });

  it("sendsExactVnpParams", async () => {
    navState.search =
      "vnp_Amount=19900000&utm=ignore&vnp_TxnRef=DH261126APP101&vnp_ResponseCode=00&vnp_SecureHash=sig123";
    let resolveVerify!: (value: VnpayReturnResponse) => void;
    const verifyReturn = vi.fn(
      () =>
        new Promise<VnpayReturnResponse>((resolve) => {
          resolveVerify = resolve;
        }),
    );

    renderWithProviders(<CheckoutAppReturnView verifyReturn={verifyReturn} />);

    expect(screen.getByRole("status", { name: "Đang tải" })).toHaveAttribute("aria-busy", "true");
    expect(verifyReturn).toHaveBeenCalledTimes(1);
    expect(verifyReturn).toHaveBeenCalledWith({
      vnp_Amount: "19900000",
      vnp_TxnRef: "DH261126APP101",
      vnp_ResponseCode: "00",
      vnp_SecureHash: "sig123",
    });

    resolveVerify({ orderCode: "DH261126APP1", paymentState: "SUCCEEDED" });
    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeInTheDocument();
    expect(verifyReturn).toHaveBeenCalledTimes(1);
  });

  it("succeeded", async () => {
    navState.search = "vnp_TxnRef=DH261126APP201&vnp_ResponseCode=00&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DH261126APP2", paymentState: "SUCCEEDED" })}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeInTheDocument();
    expect(screen.getByText(/DH261126APP2/)).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();
  });

  it("failedReasons", async () => {
    navState.search = "vnp_TxnRef=DH261126APP301&vnp_ResponseCode=24&vnp_SecureHash=valid";
    const first = renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DH261126APP3", paymentState: "FAILED" })}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByText(/DH261126APP3/)).toBeInTheDocument();
    expect(screen.getByText("Bạn đã hủy giao dịch trên VNPAY.")).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();
    first.unmount();

    navState.search = "vnp_TxnRef=DH261126APP401&vnp_ResponseCode=88&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DH261126APP4", paymentState: "FAILED" })}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByText("Ngân hàng hoặc VNPAY từ chối giao dịch.")).toBeInTheDocument();
  });

  it("pending", async () => {
    navState.search = "vnp_TxnRef=DH261126APP501&vnp_ResponseCode=99&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DH261126APP5", paymentState: "UNKNOWN" })}
      />,
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/DH261126APP5/)).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();
  });

  it("openAppHrefIsCurrentUrl", async () => {
    const rawQuery = "vnp_SecureHash=zzz&vnp_TxnRef=DHORD99&vnp_Amount=20000000&vnp_ResponseCode=00&custom=keep";
    navState.search = rawQuery;
    window.history.replaceState({}, "", `/checkout/result/app?${rawQuery}`);

    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DHORD99", paymentState: "SUCCEEDED" })}
      />,
    );

    const openLink = await screen.findByRole("link", { name: "Mở ứng dụng Thi360" });
    expect(openLink).toHaveAttribute("href", `${window.location.origin}/checkout/result/app?${rawQuery}`);
  });

  it("invalidSignature", async () => {
    navState.search = "vnp_TxnRef=DHTAMPER01&vnp_ResponseCode=00&vnp_SecureHash=bad";
    const first = renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => {
          throw new ApiStatusError(422, "Invalid signature", { code: "VNPAY_SIGNATURE_INVALID" });
        }}
      />,
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: "Không xác minh được phản hồi thanh toán" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();
    first.unmount();

    navState.search = "vnp_TxnRef=DHBADREQ";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => {
          throw new ApiStatusError(400, "Bad request", { code: "VALIDATION_FAILED" });
        }}
      />,
    );
    expect(
      await screen.findByRole("heading", { level: 1, name: "Không xác minh được phản hồi thanh toán" }),
    ).toBeInTheDocument();
  });

  it("orderNotFound", async () => {
    navState.search = "vnp_TxnRef=DHMISSING01&vnp_ResponseCode=00&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => {
          throw new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" });
        }}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy đơn hàng" })).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();
  });

  it("loadErrorRetry", async () => {
    const user = userEvent.setup();
    navState.search = "vnp_TxnRef=DHNETERR01&vnp_ResponseCode=00&vnp_SecureHash=valid";
    const verifyReturn = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(503, "Service unavailable"))
      .mockResolvedValueOnce({ orderCode: "DHNETERR", paymentState: "SUCCEEDED" });

    renderWithProviders(<CheckoutAppReturnView verifyReturn={verifyReturn} />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "Không thể kiểm tra kết quả thanh toán" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Không thể tải thông tin đơn hàng lúc này. Vui lòng thử lại."),
    ).toBeInTheDocument();
    expect(screen.getByText("Quay lại ứng dụng Thi360 để xem bài thi của bạn.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở ứng dụng Thi360" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeInTheDocument();
    expect(verifyReturn).toHaveBeenCalledTimes(2);
    expect(verifyReturn).toHaveBeenNthCalledWith(1, {
      vnp_TxnRef: "DHNETERR01",
      vnp_ResponseCode: "00",
      vnp_SecureHash: "valid",
    });
    expect(verifyReturn).toHaveBeenNthCalledWith(2, {
      vnp_TxnRef: "DHNETERR01",
      vnp_ResponseCode: "00",
      vnp_SecureHash: "valid",
    });
  });

  it("noLoginNoOrderRead", async () => {
    document.cookie = "thi360_ctx=fake-session; path=/";
    document.cookie = "thi360_rt=fake-refresh; path=/";
    navState.search = "vnp_TxnRef=DHCOOKIE01&vnp_ResponseCode=00&vnp_SecureHash=valid";

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      expect(url).toContain("/api/v1/public/payments/vnpay/return");
      return new Response(JSON.stringify({ orderCode: "DHCOOKIE", paymentState: "SUCCEEDED" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders(<CheckoutAppReturnView />);

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    const calledUrls = fetchMock.mock.calls.map(([input]) =>
      typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url,
    );
    expect(calledUrls.every((url) => url.includes("/api/v1/public/payments/vnpay/return"))).toBe(true);
    expect(calledUrls.some((url) => url.includes("/api/v1/me"))).toBe(false);

    expect(screen.queryByRole("link", { name: /đăng nhập|sign in|login/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /đăng nhập|sign in|login/i })).not.toBeInTheDocument();
    const allLinks = screen.getAllByRole("link");
    expect(allLinks.some((link) => link.getAttribute("href")?.includes("/login"))).toBe(false);
  });

  it("english", async () => {
    navState.search = "vnp_TxnRef=DHEN01&vnp_ResponseCode=00&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutAppReturnView
        verifyReturn={async () => ({ orderCode: "DHEN", paymentState: "SUCCEEDED" })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Payment successful" })).toBeInTheDocument();
    expect(screen.getByText("Order DHEN")).toBeInTheDocument();
    expect(screen.getByText("Return to the Thi360 app to view your exam.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Thi360 app" })).toBeInTheDocument();
  });
});
