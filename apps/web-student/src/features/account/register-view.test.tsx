import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import en_messages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { RegisterView } from "./register-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  navigate: vi.fn(),
  registerApi: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/register",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

beforeEach(() => {
  mocks.callbackUrl = null;
});

afterEach(() => {
  mocks.navigate.mockReset();
  mocks.registerApi.mockReset();
});

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Tên đăng nhập/), "hocvien01");
  await user.type(screen.getByLabelText(/Họ và tên/), "Nguyễn Văn An");
  await user.type(screen.getByLabelText(/^Email/), "an@thi360.vn");
  await user.type(screen.getByLabelText(/^Mật khẩu/), "Thi360@abc");
}

function renderView() {
  return renderWithProviders(<RegisterView navigate={mocks.navigate} registerApi={mocks.registerApi} />);
}

describe("RegisterView", () => {
  it("hiện quy tắc tên đăng nhập dưới ô nhập ở cả vi và en (AC-8)", () => {
    renderView();
    const username = screen.getByLabelText(/Tên đăng nhập/);
    const hint = screen.getByText(/4–30 ký tự gồm chữ cái không dấu/);
    expect(username).toHaveAttribute("aria-describedby", expect.stringContaining(hint.id));

    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <RegisterView navigate={mocks.navigate} registerApi={mocks.registerApi} />
      </NextIntlClientProvider>,
    );
    const enHint = screen.getByText(/4–30 characters: unaccented letters/);
    expect(screen.getByLabelText(/^Username/)).toHaveAttribute("aria-describedby", expect.stringContaining(enHint.id));
  });

  it("gửi đủ trường kèm termsVersion và callbackUrl hợp lệ, rồi sang /verify-email theo redirectTo", async () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    mocks.registerApi.mockResolvedValue({ redirectTo: "http://localhost:3000/checkout/BCA-FULL" });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await waitFor(() =>
      expect(mocks.registerApi).toHaveBeenCalledWith({
        username: "hocvien01",
        fullName: "Nguyễn Văn An",
        email: "an@thi360.vn",
        password: "Thi360@abc",
        acceptTerms: true,
        termsVersion: "2026-11",
        callbackUrl: "/checkout/BCA-FULL",
      }),
    );
    expect(mocks.navigate).toHaveBeenCalledWith(
      `/verify-email?callbackUrl=${encodeURIComponent("http://localhost:3000/checkout/BCA-FULL")}`,
    );
  });

  it("callbackUrl trỏ ra ngoài hệ thống không được gửi đi (AC-7)", async () => {
    mocks.callbackUrl = "https://trang-la.example/x";
    mocks.registerApi.mockResolvedValue({ redirectTo: "http://localhost:3000/home" });
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    await waitFor(() => expect(mocks.registerApi).toHaveBeenCalled());
    expect(mocks.registerApi.mock.calls[0]![0]).not.toHaveProperty("callbackUrl");
    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute("href", "/login");
  });

  it("chưa đồng ý điều khoản: báo lỗi tại ô, không gọi API (AC-12)", async () => {
    const user = userEvent.setup();
    renderView();

    await fillValid(user);
    await user.click(screen.getByRole("button", { name: "Đăng ký" }));

    expect(await screen.findByText("Bạn cần đồng ý với Điều khoản sử dụng để đăng ký.")).toBeInTheDocument();
    expect(mocks.registerApi).not.toHaveBeenCalled();
  });

  it.each([
    ["USERNAME_TAKEN", "Tên đăng nhập này đã được sử dụng. Hãy chọn tên khác.", /Tên đăng nhập/],
    ["EMAIL_TAKEN", "Email này đã có tài khoản. Hãy đăng nhập hoặc dùng Quên mật khẩu.", /^Email/],
  ])("409 %s hiện lỗi tại ô tương ứng và giữ dữ liệu đã nhập (AC-12)", async (code, message, fieldLabel) => {
    mocks.registerApi.mockRejectedValue(new ApiStatusError(409, "backend msg", { code }));
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
    mocks.registerApi.mockRejectedValue(new ApiStatusError(429, "x", { code: "RATE_LIMITED" }));
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
