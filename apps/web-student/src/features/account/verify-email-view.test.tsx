import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EmailCodeStatusError, type ProfileResponse } from "@/features/account/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { VerifyEmailView } from "./verify-email-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  navigate: vi.fn(),
  loadProfile: vi.fn(),
  sendCode: vi.fn(),
  verifyCode: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/verify-email",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

const profile = (overrides: Partial<ProfileResponse> = {}): ProfileResponse => ({
  username: "hocvien01",
  fullName: "Nguyễn Văn An",
  email: "an@thi360.vn",
  emailVerified: false,
  phone: null,
  avatarAssetId: null,
  avatarUrl: null,
  pendingEmail: null,
  ...overrides,
});

let nowMs = 0;

beforeEach(() => {
  mocks.callbackUrl = null;
  nowMs = 1_000_000;
  mocks.loadProfile.mockResolvedValue(profile());
});

afterEach(() => {
  mocks.navigate.mockReset();
  mocks.loadProfile.mockReset();
  mocks.sendCode.mockReset();
  mocks.verifyCode.mockReset();
});

function renderView() {
  return renderWithProviders(
    <VerifyEmailView
      navigate={mocks.navigate}
      loadProfileApi={mocks.loadProfile}
      sendCodeApi={mocks.sendCode}
      verifyCodeApi={mocks.verifyCode}
      now={() => nowMs}
    />,
  );
}

async function submitCode(user: ReturnType<typeof userEvent.setup>, code: string) {
  await user.type(await screen.findByLabelText(/Mã xác minh/), code);
  await user.click(screen.getByRole("button", { name: "Xác minh" }));
}

describe("VerifyEmailView", () => {
  it("hiện email nhận mã và trạng thái đang tải rồi biểu mẫu", async () => {
    renderView();
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    expect(await screen.findByText("an@thi360.vn")).toBeInTheDocument();
  });

  it("OTP sai hiện số lần còn lại theo remainingAttempts (AC-4)", async () => {
    mocks.verifyCode.mockRejectedValue(
      new EmailCodeStatusError(new ApiStatusError(422, "x", { code: "EMAIL_CODE_INVALID" }), { remainingAttempts: 3 }),
    );
    const user = userEvent.setup();
    renderView();

    await submitCode(user, "482915");

    expect(await screen.findByText("Mã chưa đúng. Bạn còn 3 lần thử.")).toBeInTheDocument();
    expect(screen.getByLabelText(/Mã xác minh/)).toHaveValue("482915");
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it("mã chưa đủ 6 chữ số: báo tại ô, không gọi API (AC-4)", async () => {
    const user = userEvent.setup();
    renderView();

    await submitCode(user, "123");

    expect(await screen.findByText("Vui lòng nhập mã gồm 6 chữ số.")).toBeInTheDocument();
    expect(mocks.verifyCode).not.toHaveBeenCalled();
  });

  it("các trạng thái mã: hết hạn, đếm ngược gửi lại theo retryAfter, hết lượt trong ngày, thành công (AC-13)", async () => {
    const user = userEvent.setup();
    mocks.callbackUrl = "/checkout/BCA-FULL";
    renderView();

    // Mã hết hạn / đã dùng / sai quá 5 lần.
    mocks.verifyCode.mockRejectedValueOnce(
      new EmailCodeStatusError(new ApiStatusError(422, "x", { code: "EMAIL_CODE_EXPIRED" })),
    );
    await submitCode(user, "111111");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới.",
    );

    // Gửi lại thành công: nút chuyển sang đếm ngược 60 giây.
    mocks.sendCode.mockResolvedValueOnce(undefined);
    await user.click(screen.getByRole("button", { name: "Gửi lại mã" }));
    expect(await screen.findByText("Đã gửi mã mới. Mã cũ không còn dùng được.")).toBeInTheDocument();
    // Đếm ngược là chữ thường dưới nút (đủ tương phản), nút giữ nhãn thường và bị khóa.
    expect(screen.getByText("Gửi lại mã sau 01:00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gửi lại mã" })).toBeDisabled();
    expect(screen.getByText("Có thể gửi lại mã sau 01:00.")).toBeInTheDocument();
    nowMs += 42_000;
    await waitFor(() => expect(screen.getByText("Gửi lại mã sau 00:18")).toBeInTheDocument(), {
      timeout: 2000,
    });
    nowMs += 18_000;
    await waitFor(() => expect(screen.getByRole("button", { name: "Gửi lại mã" })).toBeEnabled(), { timeout: 2000 });
    expect(screen.getByText("Bạn có thể gửi lại mã bây giờ.")).toBeInTheDocument();

    // 429 theo retryAfter của máy chủ (30 giây).
    mocks.sendCode.mockRejectedValueOnce(
      new EmailCodeStatusError(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }), { retryAfter: 30 }),
    );
    await user.click(screen.getByRole("button", { name: "Gửi lại mã" }));
    expect(await screen.findByText("Gửi lại mã sau 00:30")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gửi lại mã" })).toBeDisabled();

    // Hết lượt trong ngày: retryAfter cỡ nhiều giờ, hiện cảnh báo kèm thời điểm thử lại.
    nowMs += 31_000;
    await waitFor(() => expect(screen.getByRole("button", { name: "Gửi lại mã" })).toBeEnabled(), { timeout: 2000 });
    mocks.sendCode.mockRejectedValueOnce(
      new EmailCodeStatusError(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }), { retryAfter: 6 * 3600 }),
    );
    await user.click(screen.getByRole("button", { name: "Gửi lại mã" }));
    expect(await screen.findByText(/Bạn đã gửi lại mã quá số lần cho phép\. Thử lại sau/)).toBeInTheDocument();

    // Xác minh thành công: hiện trạng thái đã xác minh, đi tiếp theo callbackUrl.
    mocks.verifyCode.mockResolvedValueOnce(undefined);
    await user.clear(screen.getByLabelText(/Mã xác minh/));
    await user.type(screen.getByLabelText(/Mã xác minh/), "482915");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("/checkout/BCA-FULL"));
    expect(await screen.findByText("Email đã được xác minh. Bạn có thể tiếp tục.")).toBeInTheDocument();
  });

  it("giới hạn theo ngày (retryAfter 86000 s): nút khóa nhưng không đếm ngược mm:ss quá lớn (R1-10)", async () => {
    mocks.sendCode.mockRejectedValue(
      new EmailCodeStatusError(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }), { retryAfter: 86_000 }),
    );
    const user = userEvent.setup();
    renderView();

    await user.click(await screen.findByRole("button", { name: "Gửi lại mã" }));

    expect(await screen.findByText(/Bạn đã gửi lại mã quá số lần cho phép\. Thử lại sau/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gửi lại mã" })).toBeDisabled();
    expect(document.body.textContent).not.toMatch(/\d{3,}:\d{2}/);
    expect(screen.queryByText(/Có thể gửi lại mã sau/)).toBeNull();
  });

  it("nút Để sau đi theo callbackUrl hợp lệ, callbackUrl ngoài hệ thống bị bỏ qua về /home (AC-7)", async () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    const { unmount } = renderView();
    expect(await screen.findByRole("link", { name: "Để sau" })).toHaveAttribute("href", "/checkout/BCA-FULL");
    unmount();

    mocks.callbackUrl = "https://trang-la.example";
    renderView();
    expect(await screen.findByRole("link", { name: "Để sau" })).toHaveAttribute("href", "/home");
  });

  it("tài khoản đã xác minh mở trang: hiện trạng thái đã xác minh và lối đi tiếp", async () => {
    mocks.loadProfile.mockResolvedValue(profile({ emailVerified: true }));
    mocks.callbackUrl = "/checkout/BCA-FULL";
    renderView();

    expect(await screen.findByText("Email của bạn đã được xác minh.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/checkout/BCA-FULL");
    expect(screen.queryByLabelText(/Mã xác minh/)).toBeNull();
  });

  it("lỗi tải hồ sơ hiện thông báo và nút Thử lại", async () => {
    mocks.loadProfile.mockRejectedValueOnce(new ApiStatusError(500, "x"));
    const user = userEvent.setup();
    renderView();

    expect(await screen.findByText("Không tải được trang")).toBeInTheDocument();
    mocks.loadProfile.mockResolvedValueOnce(profile());
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByLabelText(/Mã xác minh/)).toBeInTheDocument();
  });
});
