import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CheckoutResultView } from "@/features/checkout/checkout-result-view";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";

vi.mock("next/navigation", () => ({
  usePathname: () => "/checkout/result",
  useSearchParams: () =>
    new URLSearchParams("vnp_TxnRef=DHTAMPER01&vnp_ResponseCode=00&vnp_SecureHash=corrupted"),
}));

describe("AC-24: VNPAY signature invalid", () => {
  it("từ chối chữ ký VNPAY lỗi 422 và không gọi /me/orders", async () => {
    const verifyReturn = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(422, "Invalid signature", { code: "VNPAY_SIGNATURE_INVALID" }));
    const loadOrderDetail = vi.fn();

    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        verifyReturn={verifyReturn}
        loadOrderDetail={loadOrderDetail}
      />,
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: "Không xác minh được phản hồi thanh toán" }),
    ).toBeInTheDocument();
    expect(verifyReturn).toHaveBeenCalledWith({
      vnp_TxnRef: "DHTAMPER01",
      vnp_ResponseCode: "00",
      vnp_SecureHash: "corrupted",
    });
    expect(loadOrderDetail).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "Xem đơn của tôi" })).toHaveAttribute("href", "/account/orders");
  });
});
