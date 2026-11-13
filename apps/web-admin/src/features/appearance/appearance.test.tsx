import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PRESET_PALETTES, Toaster } from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FAKE_SESSION } from "@/features/session/fake-session";
import { SessionProvider } from "@/features/session/session-provider";
import { ApiStatusError } from "@/lib/api/query-client";
import { renderWithProviders } from "@/test/render";
import {
  AppearanceApiError,
  loadAppearance as apiLoadAppearance,
  resetAppearance as apiResetAppearance,
  saveAppearance as apiSaveAppearance,
  type AppearanceLoader,
  type AppearanceResetter,
  type AppearanceResponse,
  type AppearanceSaver,
} from "./api";
import { AppearanceView } from "./appearance-view";

vi.mock("./actions", () => ({
  revalidateAppearance: vi.fn(async () => undefined),
}));

const DEFAULT_SERVER: AppearanceResponse = {
  palette: { presetId: "thi360-green" },
  mode: "light",
  version: 2,
};

function renderAppearance({
  permissions = FAKE_SESSION.permissions,
  loadAppearance = async () => DEFAULT_SERVER,
  saveAppearance = async (payload) => ({ ...payload, version: payload.version + 1 }),
  resetAppearance = async () => ({
    palette: { presetId: "thi360-green" },
    mode: "light",
    version: 3,
  }),
}: {
  permissions?: readonly string[];
  loadAppearance?: AppearanceLoader;
  saveAppearance?: AppearanceSaver;
  resetAppearance?: AppearanceResetter;
} = {}) {
  return renderWithProviders(
    <>
      <SessionProvider loadSession={async () => ({ ...FAKE_SESSION, permissions: [...permissions] })}>
        <AppearanceView
          loadAppearance={loadAppearance}
          saveAppearance={saveAppearance}
          resetAppearance={resetAppearance}
        />
      </SessionProvider>
      <Toaster />
    </>,
  );
}

afterEach(() => {
  delete document.documentElement.dataset.theme;
  document.documentElement.style.cssText = "";
  vi.unstubAllGlobals();
  document.cookie = "thi360_xsrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
});

describe("AppearanceView", () => {
  it("chặn khi thiếu quyền APPEARANCE.MODIFY", async () => {
    renderAppearance({ permissions: ["USER.VIEW"] });

    expect(
      await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" }),
    ).toBeInTheDocument();
  });

  it("đủ bốn trạng thái: đang tải, lỗi (Thử lại), rỗng (Khôi phục mặc định), có dữ liệu", async () => {
    // 1. Đang tải
    const deferred = Promise.withResolvers<AppearanceResponse | null>();
    const loadMock = vi.fn<AppearanceLoader>().mockReturnValueOnce(deferred.promise);
    const { unmount } = renderAppearance({ loadAppearance: loadMock });
    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    unmount();

    // 2. Lỗi -> Thử lại -> Rỗng -> Khôi phục mặc định -> Có dữ liệu
    const retryLoad = vi
      .fn<AppearanceLoader>()
      .mockRejectedValueOnce(new ApiStatusError(500))
      .mockResolvedValueOnce(null);
    const resetMock = vi.fn<AppearanceResetter>().mockResolvedValue(DEFAULT_SERVER);

    renderAppearance({ loadAppearance: retryLoad, resetAppearance: resetMock });

    const retryBtn = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retryBtn);

    expect(await screen.findByText("Chưa có cấu hình giao diện")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Khôi phục mặc định" }));

    await waitFor(() => expect(resetMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("heading", { name: "Tùy chỉnh giao diện" })).toBeInTheDocument();
  });

  it("hiển thị đủ 9 bộ phối màu + Tự tùy chỉnh, điền sẵn từ bộ đang chọn và giữ 2 màu khi đổi bộ rồi quay lại", async () => {
    renderAppearance();

    await screen.findByRole("heading", { name: "Tùy chỉnh giao diện" });
    expect(screen.getByRole("heading", { name: "Chế độ mặc định" })).toBeInTheDocument();
    expect(
      screen.getByText("Áp dụng cho người chưa tự chọn chế độ ở thanh trên cùng"),
    ).toBeInTheDocument();
    for (const preset of PRESET_PALETTES) {
      expect(screen.getByRole("button", { name: new RegExp(preset.name) })).toBeInTheDocument();
    }

    // Chọn "Ngọc lục bảo & Vàng" rồi mở "Tự tùy chỉnh" -> điền sẵn #00695C, #FFD740
    await userEvent.click(screen.getByRole("button", { name: /Ngọc lục bảo & Vàng/ }));
    await userEvent.click(screen.getByRole("button", { name: /Tự tùy chỉnh/ }));

    const primaryInput = screen.getByLabelText("Màu chính") as HTMLInputElement;
    const accentInput = screen.getByLabelText("Màu nhấn") as HTMLInputElement;
    expect(primaryInput.value).toBe("#00695C");
    expect(accentInput.value).toBe("#FFD740");

    // Sửa cả hai màu bằng bàn phím
    await userEvent.clear(primaryInput);
    await userEvent.type(primaryInput, "#7C3AED");
    await userEvent.clear(accentInput);
    await userEvent.type(accentInput, "#10B981");

    // Đồng bộ sang color picker
    expect((screen.getByLabelText("Chọn màu chính") as HTMLInputElement).value).toBe("#7c3aed");
    expect((screen.getByLabelText("Chọn màu nhấn") as HTMLInputElement).value).toBe("#10b981");

    // Chọn lại bộ cố định rồi quay về Tự tùy chỉnh -> giữ nguyên cả 2 màu đã nhập
    await userEvent.click(screen.getByRole("button", { name: /Chàm & Hổ phách/ }));
    expect(screen.queryByLabelText("Màu chính")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Tự tùy chỉnh/ }));
    expect((screen.getByLabelText("Màu chính") as HTMLInputElement).value).toBe("#7C3AED");
    expect((screen.getByLabelText("Màu nhấn") as HTMLInputElement).value).toBe("#10B981");
  });

  it("đồng bộ color picker, chặn lưu khi mã màu sai, hiện cảnh báo tương phản kèm tỉ lệ và chỉ xem trước cục bộ", async () => {
    renderAppearance();
    await screen.findByRole("heading", { name: "Tùy chỉnh giao diện" });

    // Đổi chế độ sang Tối -> chỉ cập nhật vùng xem trước cục bộ, chưa đổi <html>
    await userEvent.click(screen.getByRole("tab", { name: "Tối" }));
    const preview = screen.getByTestId("appearance-preview");
    expect(preview).toHaveAttribute("data-theme", "dark");
    expect(document.documentElement.dataset.theme).toBeUndefined();

    await userEvent.click(screen.getByRole("button", { name: /Tự tùy chỉnh/ }));

    const primaryInput = screen.getByLabelText("Màu chính") as HTMLInputElement;
    const primaryPicker = screen.getByLabelText("Chọn màu chính") as HTMLInputElement;

    // Mã màu sai -> chặn Lưu
    await userEvent.clear(primaryInput);
    await userEvent.type(primaryInput, "#12G");
    expect(screen.getByRole("button", { name: "Lưu và áp dụng" })).toBeDisabled();
    expect(screen.getByText(/Mã màu phải có dạng #RRGGBB/)).toBeInTheDocument();

    // Đổi qua color picker -> đồng bộ sang ô hex và cảnh báo chữ tối kèm tỉ lệ tương phản
    fireEvent.change(primaryPicker, { target: { value: "#fde047" } });
    expect(primaryInput.value).toBe("#FDE047");
    expect(screen.getByRole("button", { name: "Lưu và áp dụng" })).toBeEnabled();
    expect(screen.getByText(/Nút chính sẽ dùng chữ tối \(tương phản/)).toBeInTheDocument();
    expect(preview.style.getPropertyValue("--color-primary-600")).toBe("#fde047");
  });

  it("lưu và áp dụng, xử lý 412 nhắc tải lại, và khôi phục mặc định qua ConfirmDialog", async () => {
    const saveMock = vi
      .fn<AppearanceSaver>()
      .mockRejectedValueOnce(new ApiStatusError(412))
      .mockResolvedValueOnce({
        palette: { presetId: "navy-teal" },
        mode: "dark",
        version: 4,
      });
    const loadMock = vi
      .fn<AppearanceLoader>()
      .mockResolvedValueOnce(DEFAULT_SERVER)
      .mockResolvedValueOnce({
        palette: { presetId: "emerald-gold" },
        mode: "light",
        version: 3,
      });
    const resetMock = vi.fn<AppearanceResetter>().mockResolvedValue({
      palette: { presetId: "thi360-green" },
      mode: "light",
      version: 5,
    });

    renderAppearance({
      loadAppearance: loadMock,
      saveAppearance: saveMock,
      resetAppearance: resetMock,
    });

    await screen.findByRole("heading", { name: "Tùy chỉnh giao diện" });
    await userEvent.click(screen.getByRole("button", { name: /Xanh hải quân & Ngọc lam/ }));
    await userEvent.click(screen.getByRole("tab", { name: "Tối" }));

    // Lần lưu 1 trả 412 -> hiện prompt tải lại
    await userEvent.click(screen.getByRole("button", { name: "Lưu và áp dụng" }));
    expect(
      await screen.findByText(/Cấu hình giao diện đã được cập nhật từ nơi khác/),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Tải lại cấu hình mới nhất" }));
    await waitFor(() => expect(loadMock).toHaveBeenCalledTimes(2));

    // Chọn lại bộ màu & lưu thành công
    await userEvent.click(screen.getByRole("button", { name: /Xanh hải quân & Ngọc lam/ }));
    await userEvent.click(screen.getByRole("tab", { name: "Tối" }));
    await userEvent.click(screen.getByRole("button", { name: "Lưu và áp dụng" }));

    await waitFor(() => expect(saveMock).toHaveBeenCalledTimes(2));
    expect(saveMock).toHaveBeenLastCalledWith({
      palette: { presetId: "navy-teal" },
      mode: "dark",
      version: 3,
    });
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(document.documentElement.style.getPropertyValue("--color-primary-600")).toBe("#0f3c6e");

    // Khôi phục mặc định qua ConfirmDialog
    await userEvent.click(screen.getByRole("button", { name: "Khôi phục mặc định" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Khôi phục mặc định" }));

    await waitFor(() => expect(resetMock).toHaveBeenCalledWith({ version: 4 }));
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(document.documentElement.style.getPropertyValue("--color-primary-600")).toBe("#059669");
  });

  it("hiển thị thông báo lỗi theo mã lỗi next-intl, không dùng message thô từ backend", async () => {
    const saveMock = vi
      .fn<AppearanceSaver>()
      .mockRejectedValueOnce(
        new AppearanceApiError(400, "Raw backend error text", "VALIDATION_FAILED"),
      );

    renderAppearance({ saveAppearance: saveMock });
    await screen.findByRole("heading", { name: "Tùy chỉnh giao diện" });
    await userEvent.click(screen.getByRole("button", { name: "Lưu và áp dụng" }));

    const alerts = await screen.findAllByRole("alert");
    expect(
      alerts.some((node) =>
        node.textContent?.includes("Dữ liệu màu hoặc bộ phối màu không hợp lệ."),
      ),
    ).toBe(true);
    expect(screen.queryByText("Raw backend error text")).not.toBeInTheDocument();
  });
});

describe("appearance api client", () => {
  it("loadAppearance gọi GET /api/v1/appearance và trả null khi 204", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json(DEFAULT_SERVER))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiLoadAppearance()).resolves.toEqual(DEFAULT_SERVER);
    await expect(apiLoadAppearance()).resolves.toBeNull();

    const firstRequest = fetchMock.mock.calls[0]?.[0] as Request;
    expect(firstRequest.method).toBe("GET");
    expect(firstRequest.url).toMatch(/\/api\/v1\/appearance$/);
    expect(firstRequest.credentials).toBe("include");
    expect(firstRequest.cache).toBe("no-store");
  });

  it("saveAppearance gửi PUT kèm XSRF token và giữ xử lý lỗi 412 / 400", async () => {
    document.cookie = "thi360_xsrf=xsrf-token-123; path=/";
    const savedResponse: AppearanceResponse = {
      palette: { presetId: "navy-teal" },
      mode: "dark",
      version: 3,
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json(savedResponse))
      .mockResolvedValueOnce(
        Response.json(
          { code: "VERSION_CONFLICT", message: "Cấu hình đã thay đổi", fieldErrors: [], traceId: "t1" },
          { status: 412 },
        ),
      )
      .mockResolvedValueOnce(
        Response.json(
          { code: "VALIDATION_FAILED", message: "Mã màu không hợp lệ", fieldErrors: [], traceId: "t2" },
          { status: 400 },
        ),
      );
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiSaveAppearance({
      palette: { presetId: "navy-teal" },
      mode: "dark",
      version: 2,
    });
    expect(result).toEqual(savedResponse);

    const firstRequest = fetchMock.mock.calls[0]?.[0] as Request;
    expect(firstRequest.method).toBe("PUT");
    expect(firstRequest.url).toMatch(/\/api\/v1\/admin\/appearance$/);
    expect(firstRequest.headers.get("x-xsrf-token")).toBe("xsrf-token-123");
    expect(firstRequest.credentials).toBe("include");

    await expect(
      apiSaveAppearance({ palette: { presetId: "navy-teal" }, mode: "dark", version: 2 }),
    ).rejects.toMatchObject({
      name: "AppearanceApiError",
      status: 412,
      code: "VERSION_CONFLICT",
    } satisfies Partial<AppearanceApiError>);

    await expect(
      apiSaveAppearance({
        palette: { presetId: "custom", custom: { primary: "bad", accent: "#F59223" } },
        mode: "light",
        version: 2,
      }),
    ).rejects.toMatchObject({
      name: "AppearanceApiError",
      status: 400,
      code: "VALIDATION_FAILED",
      message: "Mã màu không hợp lệ",
    } satisfies Partial<AppearanceApiError>);
  });

  it("resetAppearance gửi POST /api/v1/admin/appearance/reset kèm XSRF token", async () => {
    document.cookie = "thi360_xsrf=xsrf-reset; path=/";
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json(DEFAULT_SERVER));
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiResetAppearance({ version: 2 });
    expect(result).toEqual(DEFAULT_SERVER);

    const request = fetchMock.mock.calls[0]?.[0] as Request;
    expect(request.method).toBe("POST");
    expect(request.url).toMatch(/\/api\/v1\/admin\/appearance\/reset$/);
    expect(request.headers.get("x-xsrf-token")).toBe("xsrf-reset");
    expect(await request.json()).toEqual({ version: 2 });
  });
});
