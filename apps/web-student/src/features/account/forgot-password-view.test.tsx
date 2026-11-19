import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { ForgotPasswordView } from "./forgot-password-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  requestApi: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/forgot-password",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

let nowMs = 0;

beforeEach(() => {
  mocks.callbackUrl = null;
  nowMs = 5_000_000;
});

afterEach(() => {
  mocks.requestApi.mockReset();
});

function renderView() {
  return renderWithProviders(<ForgotPasswordView requestApi={mocks.requestApi} now={() => nowMs} />);
}

describe("ForgotPasswordView", () => {
  it("email sai định dạng báo lỗi tại ô khi rời ô và không gọi API (AC-14)", async () => {
    const user = userEvent.setup();
    renderView();

    const email = screen.getByLabelText(/Email/);
    await user.type(email, "an.nguyen@gmail");
    await user.tab();
    expect(await screen.findByText("Email chưa đúng định dạng.")).toBeInTheDocument();
    expect(email).toHaveAttribute("aria-invalid", "true");

    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));
    expect(mocks.requestApi).not.toHaveBeenCalled();
  });

  it("sau khi gửi hiện cùng một nội dung với mọi email, kể cả khi API trả 202 cho email lạ (AC-14)", async () => {
    mocks.requestApi.mockResolvedValue(undefined);
    const user = userEvent.setup();

    const first = renderView();
    await user.type(screen.getByLabelText(/Email/), "co.tai.khoan@thi360.vn");
    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));
    const firstMessage = (await screen.findByText(/^Nếu email này đủ điều kiện/)).textContent;
    const firstHtml = screen.getByRole("region", { name: "Hãy kiểm tra hộp thư" }).innerHTML;
    first.unmount();

    renderView();
    await user.type(screen.getByLabelText(/Email/), "khong.ton.tai@thi360.vn");
    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));
    expect((await screen.findByText(/^Nếu email này đủ điều kiện/)).textContent).toBe(firstMessage);
    expect(screen.getByRole("region", { name: "Hãy kiểm tra hộp thư" }).innerHTML).toBe(firstHtml);

    expect(firstMessage).toBe(
      "Nếu email này đủ điều kiện khôi phục tài khoản, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.",
    );
    expect(mocks.requestApi).toHaveBeenNthCalledWith(1, "co.tai.khoan@thi360.vn");
    expect(mocks.requestApi).toHaveBeenNthCalledWith(2, "khong.ton.tai@thi360.vn");
  });

  it("nút gửi lại chờ 60 giây rồi gửi lại cùng email", async () => {
    mocks.requestApi.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/Email/), "an@thi360.vn");
    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));
    // Đếm ngược là chữ thường dưới nút (đủ tương phản), nút giữ nhãn thường và bị khóa.
    expect(await screen.findByText("Gửi lại sau 01:00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gửi lại" })).toBeDisabled();
    // Một vùng thông báo: câu lúc bắt đầu chờ (không đọc từng giây).
    expect(screen.getByText("Có thể gửi lại sau 01:00.")).toBeInTheDocument();

    nowMs += 60_000;
    const resend = await screen.findByRole("button", { name: "Gửi lại" }, { timeout: 2000 });
    await waitFor(() => expect(resend).toBeEnabled());
    expect(screen.getByText("Bạn có thể gửi lại bây giờ.")).toBeInTheDocument();
    expect(screen.queryByText("Có thể gửi lại sau 01:00.")).toBeNull();
    await user.click(resend);
    await waitFor(() => expect(mocks.requestApi).toHaveBeenCalledTimes(2));
    expect(mocks.requestApi).toHaveBeenLastCalledWith("an@thi360.vn");
  });

  it("429 hiện cảnh báo, giữ biểu mẫu và dữ liệu đã nhập", async () => {
    mocks.requestApi.mockRejectedValue(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }));
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/Email/), "an@thi360.vn");
    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Bạn gửi yêu cầu quá nhanh.");
    expect(screen.getByLabelText(/Email/)).toHaveValue("an@thi360.vn");
  });

  it("liên kết Quay lại đăng nhập và Tạo tài khoản mới giữ callbackUrl hợp lệ (AC-7)", () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    renderView();

    const encoded = encodeURIComponent("/checkout/BCA-FULL");
    expect(screen.getByRole("link", { name: /Quay lại đăng nhập/ })).toHaveAttribute("href", `/login?callbackUrl=${encoded}`);
    expect(screen.getByRole("link", { name: "Tạo tài khoản mới" })).toHaveAttribute(
      "href",
      `/register?callbackUrl=${encoded}`,
    );
  });
});
