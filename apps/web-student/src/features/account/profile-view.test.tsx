import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as AccountApi from "@/features/account/api";
import { EmailCodeStatusError, type ProfileResponse } from "@/features/account/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { AVATAR_MAX_BYTES, ProfileView } from "./profile-view";

const mocks = vi.hoisted(() => ({
  loadProfile: vi.fn(),
  updateProfile: vi.fn(),
  uploadAvatar: vi.fn(),
  removeAvatar: vi.fn(),
  requestEmailChange: vi.fn(),
  confirmEmailChange: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/account/profile",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/features/account/api", async () => {
  const actual = await vi.importActual<typeof AccountApi>("@/features/account/api");
  return {
    ...actual,
    loadProfile: mocks.loadProfile,
    updateProfile: mocks.updateProfile,
    uploadAvatar: mocks.uploadAvatar,
    removeAvatar: mocks.removeAvatar,
    requestEmailChange: mocks.requestEmailChange,
    confirmEmailChange: mocks.confirmEmailChange,
  };
});

const SESSION = {
  id: "u-stu",
  username: "hocvien01",
  fullName: "Nguyễn Văn An",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

const profile = (overrides: Partial<ProfileResponse> = {}): ProfileResponse => ({
  username: "hocvien01",
  fullName: "Nguyễn Văn An",
  email: "an.nguyen@gmail.com",
  emailVerified: true,
  phone: "0912345678",
  avatarAssetId: "0192f3a1-0000-7000-8000-000000000001",
  avatarUrl: "/api/v1/avatars/0192f3a1-0000-7000-8000-000000000001",
  pendingEmail: null,
  ...overrides,
});

beforeEach(() => {
  mocks.loadProfile.mockResolvedValue(profile());
});

afterEach(() => {
  for (const mock of Object.values(mocks)) mock.mockReset();
});

function renderView() {
  return renderWithProviders(<ProfileView loadCurrentSession={async () => SESSION} />);
}

function bigImage() {
  return new File([new ArrayBuffer(AVATAR_MAX_BYTES + 1)], "anh-the.png", { type: "image/png" });
}

describe("ProfileView trạng thái màn", () => {
  it("đang tải, lỗi có nút Thử lại, rồi có dữ liệu", async () => {
    mocks.loadProfile.mockRejectedValueOnce(new ApiStatusError(500, "x"));
    const user = userEvent.setup();
    renderView();

    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    expect(await screen.findByText("Không tải được hồ sơ")).toBeInTheDocument();

    mocks.loadProfile.mockResolvedValueOnce(profile());
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Hồ sơ", level: 1 })).toBeInTheDocument();
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Nguyễn Văn An");
  });

  it("trạng thái rỗng: chưa có ảnh hiện chữ cái đầu, chưa có số điện thoại hiện ghi chú, không có nút Xóa ảnh", async () => {
    mocks.loadProfile.mockResolvedValue(profile({ avatarUrl: null, avatarAssetId: null, phone: null }));
    renderView();

    expect(await screen.findByRole("img", { name: "Chưa có ảnh đại diện, đang hiện chữ cái đầu tên" })).toHaveTextContent(
      "NA",
    );
    expect(screen.getByText("Bạn chưa nhập số điện thoại.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa ảnh" })).toBeNull();
  });
});

describe("ProfileView thông tin cá nhân (AC-16)", () => {
  it("tên đăng nhập chỉ đọc; lưu họ tên và số điện thoại gửi giá trị đã cắt khoảng trắng", async () => {
    mocks.updateProfile.mockImplementation(async (body: { fullName?: string; phone?: string }) =>
      profile({ fullName: body.fullName ?? "", phone: body.phone ?? null }),
    );
    const user = userEvent.setup();
    renderView();

    const username = await screen.findByLabelText("Tên đăng nhập");
    expect(username).toHaveAttribute("readonly");
    expect(username).toHaveValue("hocvien01");

    const name = screen.getByLabelText(/Họ và tên/);
    await user.clear(name);
    await user.type(name, "  Nguyễn Văn Bình ");
    const phone = screen.getByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "0987654321");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() =>
      expect(mocks.updateProfile).toHaveBeenCalledWith({ fullName: "Nguyễn Văn Bình", phone: "0987654321" }),
    );
    // Giá trị đã lưu là mốc mới của biểu mẫu: nút Lưu khóa lại vì không còn thay đổi chưa lưu.
    await waitFor(() => expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Nguyễn Văn Bình"));
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
  });

  it("lỗi tại ô nhập: số điện thoại sai quy tắc và họ tên trống, không gọi API", async () => {
    const user = userEvent.setup();
    renderView();

    const phone = await screen.findByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "091234567");
    await user.tab();
    expect(await screen.findByText("Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.")).toBeInTheDocument();
    expect(phone).toHaveAttribute("aria-invalid", "true");

    const name = screen.getByLabelText(/Họ và tên/);
    await user.clear(name);
    await user.tab();
    expect(await screen.findByText("Vui lòng nhập họ và tên.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    expect(mocks.updateProfile).not.toHaveBeenCalled();
  });

  it("máy chủ từ chối số điện thoại: báo lỗi tại ô và đưa focus về ô đó", async () => {
    mocks.updateProfile.mockRejectedValue(
      new ApiStatusError(422, "x", {
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "phone", code: "PHONE_INVALID", message: "x" }],
      }),
    );
    const user = userEvent.setup();
    renderView();

    const name = await screen.findByLabelText(/Họ và tên/);
    await user.type(name, " B");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByText("Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(/Số điện thoại/)).toHaveFocus());
  });
});

describe("ProfileView ảnh đại diện (AC-5)", () => {
  it("ảnh quá 5 MB: báo rõ tên tệp và dung lượng, không gọi API, ảnh hiện tại giữ nguyên", async () => {
    const user = userEvent.setup();
    renderView();

    const avatar = await screen.findByRole("img", { name: "Ảnh đại diện hiện tại" });
    const before = avatar.getAttribute("src");
    await user.upload(screen.getByLabelText("Tải ảnh lên"), bigImage());

    expect(await screen.findByRole("alert")).toHaveTextContent("Ảnh anh-the.png nặng 5 MB, lớn hơn 5 MB.");
    expect(mocks.uploadAvatar).not.toHaveBeenCalled();
    expect(screen.getByRole("img", { name: "Ảnh đại diện hiện tại" })).toHaveAttribute("src", before);
  });

  it.each([
    ["413 không có mã (tầng web chặn)", new ApiStatusError(413, ""), "Ảnh lớn hơn 5 MB. Hãy chọn ảnh nhỏ hơn."],
    [
      "AVATAR_DIMENSIONS_EXCEEDED",
      new ApiStatusError(422, "x", { code: "AVATAR_DIMENSIONS_EXCEEDED" }),
      "Ảnh lớn hơn 4.096 × 4.096 px. Hãy chọn ảnh nhỏ hơn.",
    ],
    [
      "AVATAR_INVALID_IMAGE",
      new ApiStatusError(422, "x", { code: "AVATAR_INVALID_IMAGE" }),
      "Không đọc được ảnh. Hãy chọn tệp JPG, PNG hoặc WEBP hợp lệ.",
    ],
  ])("máy chủ từ chối ảnh (%s): hiện lỗi rõ và giữ ảnh hiện tại", async (_label, error, message) => {
    mocks.uploadAvatar.mockRejectedValue(error);
    const user = userEvent.setup();
    renderView();

    const avatar = await screen.findByRole("img", { name: "Ảnh đại diện hiện tại" });
    const before = avatar.getAttribute("src");
    await user.upload(
      screen.getByLabelText("Tải ảnh lên"),
      new File([new Uint8Array(10)], "anh.png", { type: "image/png" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("img", { name: "Ảnh đại diện hiện tại" })).toHaveAttribute("src", before);
  });
});

describe("ProfileView email (AC-17)", () => {
  it("email đã xác minh hiện chip Đã xác minh, không có dải cảnh báo", async () => {
    renderView();

    const section = (await screen.findByRole("heading", { name: "Email", level: 2 })).closest("section")!;
    expect(within(section).getByText("an.nguyen@gmail.com")).toBeInTheDocument();
    expect(within(section).getByText("Đã xác minh")).toBeInTheDocument();
    expect(screen.queryByText("Email chưa xác minh.")).toBeNull();
  });

  it("email chưa xác minh hiện dải cảnh báo kèm nút Xác minh email và chip Chưa xác minh", async () => {
    mocks.loadProfile.mockResolvedValue(profile({ emailVerified: false }));
    renderView();

    expect(await screen.findByText("Email chưa xác minh.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xác minh email" })).toHaveAttribute(
      "href",
      "/verify-email?callbackUrl=%2Faccount%2Fprofile",
    );
    expect(screen.getByText("Chưa xác minh")).toBeInTheDocument();
  });

  it("email mới đã thuộc tài khoản khác: lỗi tại ô email mới, dữ liệu giữ nguyên", async () => {
    mocks.requestEmailChange.mockRejectedValue(
      new EmailCodeStatusError(new ApiStatusError(409, "x", { code: "EMAIL_TAKEN" })),
    );
    const user = userEvent.setup();
    renderView();

    await user.click(await screen.findByRole("button", { name: "Đổi email" }));
    await user.type(screen.getByLabelText(/Email mới/), "da.co@thi360.vn");
    await user.type(screen.getByLabelText(/^Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("button", { name: "Gửi mã xác minh" }));

    expect(await screen.findByText("Email này đã thuộc tài khoản khác.")).toBeInTheDocument();
    expect(mocks.requestEmailChange).toHaveBeenCalledWith("da.co@thi360.vn", "Thi360@dev");
    expect(screen.getByLabelText(/Email mới/)).toHaveValue("da.co@thi360.vn");
  });

  it("gửi mã đổi email xong hiện khối đang chờ xác minh; xác nhận đúng mã đổi sang email mới đã xác minh", async () => {
    mocks.requestEmailChange.mockResolvedValue(undefined);
    mocks.confirmEmailChange.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderView();

    await user.click(await screen.findByRole("button", { name: "Đổi email" }));
    await user.type(screen.getByLabelText(/Email mới/), "an.nguyen.2008@gmail.com");
    await user.type(screen.getByLabelText(/^Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("button", { name: "Gửi mã xác minh" }));

    expect(
      await screen.findByRole("heading", { name: "Đang chờ xác minh email mới: an.nguyen.2008@gmail.com" }),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Mã xác minh/), "482915");
    await user.click(screen.getByRole("button", { name: "Xác nhận" }));

    await waitFor(() => expect(mocks.confirmEmailChange).toHaveBeenCalledWith("482915"));
    const section = screen.getByRole("heading", { name: "Email", level: 2 }).closest("section")!;
    await waitFor(() => expect(within(section).getByText("an.nguyen.2008@gmail.com")).toBeInTheDocument());
    expect(within(section).getByText("Đã xác minh")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Đang chờ xác minh email mới/ })).toBeNull();
  });

  it("xác nhận email mới gặp lỗi hệ thống (503): hiện lỗi và email hiện tại giữ nguyên", async () => {
    mocks.loadProfile.mockResolvedValue(profile({ pendingEmail: "an.nguyen.2008@gmail.com" }));
    mocks.confirmEmailChange.mockRejectedValue(
      new EmailCodeStatusError(new ApiStatusError(503, "x", { code: "EXTERNAL_SERVICE_UNAVAILABLE" })),
    );
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mã xác minh/), "482915");
    await user.click(screen.getByRole("button", { name: "Xác nhận" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa đổi được email, email hiện tại giữ nguyên. Vui lòng thử lại sau.",
    );
    const section = screen.getByRole("heading", { name: "Email", level: 2 }).closest("section")!;
    expect(within(section).getByText("an.nguyen@gmail.com")).toBeInTheDocument();
  });

  it("mã đổi email sai hiện số lần còn lại", async () => {
    mocks.loadProfile.mockResolvedValue(profile({ pendingEmail: "an.nguyen.2008@gmail.com" }));
    mocks.confirmEmailChange.mockRejectedValue(
      new EmailCodeStatusError(new ApiStatusError(422, "x", { code: "EMAIL_CODE_INVALID" }), { remainingAttempts: 2 }),
    );
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mã xác minh/), "000000");
    await user.click(screen.getByRole("button", { name: "Xác nhận" }));

    expect(await screen.findByText("Mã chưa đúng. Bạn còn 2 lần thử.")).toBeInTheDocument();
  });
});
