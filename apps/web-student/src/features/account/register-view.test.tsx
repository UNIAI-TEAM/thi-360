import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EmailCodeStatusError } from "@/features/account/api";
import { ApiStatusError } from "@/lib/api/client";
import en_messages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { RegisterView } from "./register-view";

const REG_ID = "0192f3a1-0000-7000-8000-000000000010";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  navigate: vi.fn(),
  startRegisterApi: vi.fn(),
  resendRegisterApi: vi.fn(),
  verifyRegisterApi: vi.fn(),
  currentTime: 1_700_000_000_000,
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/register",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

beforeEach(() => {
  mocks.callbackUrl = null;
  mocks.currentTime = 1_700_000_000_000;
});

afterEach(() => {
  mocks.navigate.mockReset();
  mocks.startRegisterApi.mockReset();
  mocks.resendRegisterApi.mockReset();
  mocks.verifyRegisterApi.mockReset();
});

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Tên đăng nhập/), "hocvien01");
  await user.type(screen.getByLabelText(/Họ và tên/), "Nguyễn Văn An");
  await user.type(screen.getByLabelText(/^Email/), "an@thi360.vn");
  await user.type(screen.getByLabelText(/^Mật khẩu/), "Thi360@abc");
}

function renderView() {
  return renderWithProviders(
    <RegisterView
      navigate={mocks.navigate}
      startRegisterApi={mocks.startRegisterApi}
      resendRegisterApi={mocks.resendRegisterApi}
      verifyRegisterApi={mocks.verifyRegisterApi}
      now={() => mocks.currentTime}
    />,
  );
}

describe("RegisterView (2-step OTP)", () => {
  it("hiện quy tắc tên đăng nhập dưới ô nhập ở cả vi và en (AC-8)", () => {
    renderView();
    const username = screen.getByLabelText(/Tên đăng nhập/);
    const hint = screen.getByText(/4–30 ký tự gồm chữ cái không dấu/);
    expect(username).toHaveAttribute("aria-describedby", expect.stringContaining(hint.id));

    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <RegisterView
          navigate={mocks.navigate}
          startRegisterApi={mocks.startRegisterApi}
          resendRegisterApi={mocks.resendRegisterApi}
          verifyRegisterApi={mocks.verifyRegisterApi}
        />
      </NextIntlClientProvider>,
    );
    const enHint = screen.getByText(/4–30 characters: unaccented letters/);
    expect(screen.getByLabelText(/^Username/)).toHaveAttribute("aria-describedby", expect.stringContaining(enHint.id));
  });

  it("Bước 1 gửi thông tin -> sang Bước 2 nhập OTP 6 số -> xác minh xong chuyển thẳng tới callbackUrl hợp lệ", async () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    mocks.startRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 60,
    });
    mocks.verifyRegisterApi.mockResolvedValue({
      redirectTo: "http://thi360.example:8443/checkout/BCA-FULL",
    });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await waitFor(() =>
      expect(mocks.startRegisterApi).toHaveBeenCalledWith({
        username: "hocvien01",
        fullName: "Nguyễn Văn An",
        email: "an@thi360.vn",
        password: "Thi360@abc",
        acceptTerms: true,
        termsVersion: "2026-11",
        callbackUrl: "/checkout/BCA-FULL",
      }),
    );

    expect(await screen.findByRole("heading", { name: "Xác minh email" })).toBeInTheDocument();
    expect(screen.getByText("an@thi360.vn")).toBeInTheDocument();
    expect(mocks.navigate).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/^Mã xác minh/), "123456");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    await waitFor(() => expect(mocks.verifyRegisterApi).toHaveBeenCalledWith(REG_ID, "123456"));
    expect(mocks.navigate).toHaveBeenCalledWith("/checkout/BCA-FULL");
  });

  it("không có callbackUrl: xác minh OTP xong về thẳng /home (không qua /verify-email)", async () => {
    mocks.startRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 60,
    });
    mocks.verifyRegisterApi.mockResolvedValue({ redirectTo: "http://localhost:3000/home" });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await screen.findByRole("heading", { name: "Xác minh email" });
    await user.type(screen.getByLabelText(/^Mã xác minh/), "654321");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("/home"));
  });

  it("callbackUrl trỏ ra ngoài hệ thống không được gửi đi (AC-7)", async () => {
    mocks.callbackUrl = "https://trang-la.example/x";
    mocks.startRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 60,
    });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await waitFor(() => expect(mocks.startRegisterApi).toHaveBeenCalled());
    expect(mocks.startRegisterApi.mock.calls[0]![0]).not.toHaveProperty("callbackUrl");
  });

  it("nhập sai mã OTP hiện số lần thử còn lại; hết hạn hiện cảnh báo; gửi lại mã và Quay lại sửa thông tin hoạt động đúng", async () => {
    mocks.startRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 1,
    });
    mocks.resendRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 60,
    });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await screen.findByRole("heading", { name: "Xác minh email" });
    const resendBtn = screen.getByRole("button", { name: "Gửi lại mã" });
    expect(resendBtn).toBeDisabled();

    mocks.verifyRegisterApi.mockRejectedValueOnce(
      new EmailCodeStatusError(new ApiStatusError(422, "invalid", { code: "EMAIL_CODE_INVALID" }), {
        remainingAttempts: 4,
      }),
    );
    await user.type(screen.getByLabelText(/^Mã xác minh/), "000000");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByText("Mã chưa đúng. Bạn còn 4 lần thử.")).toBeInTheDocument();

    mocks.verifyRegisterApi.mockRejectedValueOnce(
      new EmailCodeStatusError(new ApiStatusError(422, "expired", { code: "EMAIL_CODE_EXPIRED" })),
    );
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới.",
    );

    // Hết thời gian chờ gửi lại -> bấm Gửi lại mã được.
    mocks.currentTime += 2_000;
    await waitFor(() => expect(resendBtn).toBeEnabled());
    await user.click(resendBtn);
    await waitFor(() => expect(mocks.resendRegisterApi).toHaveBeenCalledWith(REG_ID));
    expect(await screen.findByText("Đã gửi mã mới. Mã cũ không còn dùng được.")).toBeInTheDocument();

    // Quay lại sửa thông tin giữ nguyên dữ liệu đã nhập và gửi kèm registrationId khi lưu lại.
    await user.click(screen.getByRole("button", { name: "Quay lại sửa thông tin" }));
    const fullNameInput = screen.getByLabelText(/Họ và tên/);
    expect(fullNameInput).toHaveValue("Nguyễn Văn An");
    await user.clear(fullNameInput);
    await user.type(fullNameInput, "Trần Thị Bích");
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await waitFor(() =>
      expect(mocks.startRegisterApi).toHaveBeenLastCalledWith(
        expect.objectContaining({
          registrationId: REG_ID,
          fullName: "Trần Thị Bích",
        }),
      ),
    );
  });

  it("409 USERNAME_TAKEN xảy ra ở Bước 2 tự quay về Bước 1 và focus ô Tên đăng nhập", async () => {
    mocks.startRegisterApi.mockResolvedValue({
      registrationId: REG_ID,
      email: "an@thi360.vn",
      resendAfterSeconds: 60,
    });
    mocks.verifyRegisterApi.mockRejectedValueOnce(new ApiStatusError(409, "taken", { code: "USERNAME_TAKEN" }));
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await screen.findByRole("heading", { name: "Xác minh email" });
    await user.type(screen.getByLabelText(/^Mã xác minh/), "123456");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    expect(await screen.findByText("Tên đăng nhập này đã được sử dụng. Hãy chọn tên khác.")).toBeInTheDocument();
    const field = screen.getByLabelText(/Tên đăng nhập/);
    expect(field).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(field).toHaveFocus());
  });

  it("chưa đồng ý điều khoản: báo lỗi tại ô, không gọi API (AC-12)", async () => {
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    expect(await screen.findByText("Bạn cần đồng ý với Điều khoản sử dụng để đăng ký.")).toBeInTheDocument();
    expect(mocks.startRegisterApi).not.toHaveBeenCalled();
  });

  it.each([
    ["USERNAME_TAKEN", "Tên đăng nhập này đã được sử dụng. Hãy chọn tên khác.", /Tên đăng nhập/],
    ["EMAIL_TAKEN", "Email này đã có tài khoản. Hãy đăng nhập hoặc dùng Quên mật khẩu.", /^Email/],
  ])("409 %s hiện lỗi tại ô tương ứng và giữ dữ liệu đã nhập (AC-12)", async (code, message, fieldLabel) => {
    mocks.startRegisterApi.mockRejectedValue(new ApiStatusError(409, "backend msg", { code }));
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.queryByText(/backend msg/)).toBeNull();
    const field = screen.getByLabelText(fieldLabel);
    expect(field).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(field).toHaveFocus());
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Nguyễn Văn An");
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it("429 quá giới hạn đăng ký hiện cảnh báo chung (AC-12)", async () => {
    mocks.startRegisterApi.mockRejectedValue(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }));
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Có quá nhiều lượt đăng ký từ mạng này. Vui lòng thử lại sau ít phút.",
    );
  });

  it("điều khoản mở trong hộp thoại và đóng được (AC-12)", async () => {
    const user = userEvent.setup();
    renderView();

    await user.click(screen.getByRole("button", { name: "Xem Điều khoản sử dụng" }));
    const dialog = await screen.findByRole("dialog", { name: "Điều khoản sử dụng" });
    expect(within(dialog).getByText(/NỘI DUNG ĐIỀU KHOẢN SỬ DỤNG/)).toBeInTheDocument();

    await user.click(within(dialog).getAllByRole("button", { name: "Đóng" })[0]!);
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("liên kết Đăng nhập giữ callbackUrl hợp lệ", () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    renderView();
    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute(
      "href",
      `/login?callbackUrl=${encodeURIComponent("/checkout/BCA-FULL")}`,
    );
  });
});
