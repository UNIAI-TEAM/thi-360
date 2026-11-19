import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { ResetPasswordView } from "./reset-password-view";

const mocks = vi.hoisted(() => ({
  token: null as string | null,
  navigate: vi.fn(),
  resetApi: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/reset-password",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.token ? { token: mocks.token } : {}),
}));

beforeEach(() => {
  mocks.token = "tok-abc123";
});

afterEach(() => {
  mocks.navigate.mockReset();
  mocks.resetApi.mockReset();
});

function renderView() {
  return renderWithProviders(<ResetPasswordView navigate={mocks.navigate} resetApi={mocks.resetApi} />);
}

describe("ResetPasswordView", () => {
  it("hiện quy tắc mật khẩu và đánh dấu điều kiện đạt theo ô nhập (AC-15)", async () => {
    const user = userEvent.setup();
    renderView();

    const rules = screen.getByRole("list", { name: "Yêu cầu mật khẩu" });
    expect(rules.querySelectorAll("li")).toHaveLength(5);
    expect(rules.querySelectorAll('[data-met="true"]')).toHaveLength(0);

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "Thi360@abc");
    expect(rules.querySelectorAll('[data-met="true"]')).toHaveLength(5);
  });

  it("hai mật khẩu không khớp báo lỗi tại ô nhập lại, không gọi API (AC-15)", async () => {
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "Thi360@abc");
    await user.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "Thi360@ab");
    await user.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));

    expect(await screen.findByText("Mật khẩu nhập lại chưa khớp.")).toBeInTheDocument();
    expect(mocks.resetApi).not.toHaveBeenCalled();
  });

  it("mật khẩu yếu báo lỗi tại ô mới, không gọi API", async () => {
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "abc");
    await user.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "abc");
    await user.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));

    expect(await screen.findByText("Mật khẩu mới chưa đáp ứng đủ các điều kiện.")).toBeInTheDocument();
    expect(mocks.resetApi).not.toHaveBeenCalled();
  });

  it("đặt lại thành công: gửi token trong URL và về /login không kèm callbackUrl, không tự đăng nhập", async () => {
    mocks.resetApi.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "Thi360@abc");
    await user.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "Thi360@abc");
    await user.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));

    await waitFor(() => expect(mocks.resetApi).toHaveBeenCalledWith("tok-abc123", "Thi360@abc"));
    expect(mocks.navigate).toHaveBeenCalledWith("/login?reset=done");
  });

  it("RESET_TOKEN_INVALID hiện trang liên kết hết hiệu lực kèm lối gửi yêu cầu mới (AC-15)", async () => {
    mocks.resetApi.mockRejectedValue(new ApiStatusError(422, "x", { code: "RESET_TOKEN_INVALID" }));
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "Thi360@abc");
    await user.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "Thi360@abc");
    await user.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));

    expect(await screen.findByRole("heading", { name: "Liên kết không còn dùng được" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Gửi yêu cầu mới" })).toHaveAttribute("href", "/forgot-password");
    expect(screen.getByRole("link", { name: "Quay lại đăng nhập" })).toHaveAttribute("href", "/login");
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).toBeNull();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it("mở trang không có token: hiện ngay trang liên kết hết hiệu lực, không dựng biểu mẫu", () => {
    mocks.token = null;
    renderView();

    expect(screen.getByRole("heading", { name: "Liên kết không còn dùng được" })).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).toBeNull();
  });

  it("lỗi máy chủ khác hiện cảnh báo chung và giữ biểu mẫu", async () => {
    mocks.resetApi.mockRejectedValue(new ApiStatusError(503, "x"));
    const user = userEvent.setup();
    renderView();

    await user.type(screen.getByLabelText(/^Mật khẩu mới/), "Thi360@abc");
    await user.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "Thi360@abc");
    await user.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Không đặt lại được mật khẩu. Vui lòng thử lại.");
    expect(screen.getByLabelText(/^Mật khẩu mới/)).toBeInTheDocument();
  });
});
