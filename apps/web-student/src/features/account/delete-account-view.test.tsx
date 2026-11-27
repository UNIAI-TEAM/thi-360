import { readFileSync } from "node:fs";
import path from "node:path";
import { UiStringsProvider } from "@thi360/ui";
import { APP_TIME_ZONE, INTL_FORMATS } from "@thi360/web-kit/preferences";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  type AccountDeletionBlocker,
  type AccountDeletionCheckResponse,
  deleteAccount,
  type DeleteAccountResult,
} from "@/features/account/api";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { FORBIDDEN_PATH, QueryProvider } from "@/providers/query-provider";
import { renderWithProviders } from "@/test/render";
import { DeleteAccountView } from "./delete-account-view";

const navMocks = vi.hoisted(() => ({
  replace: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/account/delete",
  useRouter: () => ({ replace: navMocks.replace, refresh: navMocks.refresh }),
  useSearchParams: () => new URLSearchParams(),
}));

const FIXTURES_DIR = path.resolve(import.meta.dirname, "../../../../../contracts/mobile/api");

function loadFixture<T>(filename: string): T {
  return JSON.parse(readFileSync(path.join(FIXTURES_DIR, filename), "utf8")) as T;
}

const SESSION = {
  id: "u-stu",
  username: "hocvien01",
  fullName: "Nguyễn Văn An",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

let loadCheckMock: ReturnType<typeof vi.fn<() => Promise<AccountDeletionCheckResponse>>>;
let submitDeletionMock: ReturnType<
  typeof vi.fn<(body: { currentPassword: string }) => Promise<DeleteAccountResult>>
>;
let clearSessionMock: ReturnType<typeof vi.fn<() => Promise<void>>>;

beforeEach(() => {
  loadCheckMock = vi.fn(async () =>
    loadFixture<AccountDeletionCheckResponse>("me-account-deletion-check-200-unused-attempts.json"),
  );
  submitDeletionMock = vi.fn(async () => ({ kind: "deleted" as const }));
  clearSessionMock = vi.fn(async () => undefined);
  navMocks.replace.mockReset();
  navMocks.refresh.mockReset();
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.sessionStorage.clear();
});

function renderView(options?: { locale?: "vi" | "en" }) {
  const locale = options?.locale ?? "vi";
  return renderWithProviders(
    <DeleteAccountView
      loadCurrentSession={async () => SESSION}
      loadCheck={loadCheckMock}
      submitDeletion={submitDeletionMock}
      clearSession={clearSessionMock}
    />,
    {
      locale,
      messages: locale === "en" ? enMessages : viMessages,
    },
  );
}

describe("DeleteAccountView", () => {
  it("#ready: hiện tiêu đề, giải thích, cảnh báo 3 lượt thi, ô mật khẩu, ô tích và bật/tắt nút theo đúng điều kiện (AC-1)", async () => {
    const user = userEvent.setup();
    renderView();

    expect(await screen.findByText(viMessages.account.deletion.explanation)).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Xóa tài khoản" })).toBeInTheDocument();
    expect(
      screen.getByText("Bạn còn 3 lượt thi chưa dùng. Các lượt này sẽ mất và không được hoàn tiền."),
    ).toBeInTheDocument();
    expect(screen.queryByText(viMessages.account.deletion.pendingGradingWarning)).toBeNull();

    const passwordInput = screen.getByLabelText(/Mật khẩu hiện tại/);
    const checkbox = screen.getByRole("checkbox", {
      name: "Tôi hiểu tài khoản bị xóa sẽ không khôi phục được.",
    });
    const submitButton = screen.getByRole("button", { name: "Xóa tài khoản" });

    expect(passwordInput).toHaveAttribute("autocomplete", "current-password");
    expect(submitButton).toBeDisabled();

    await user.click(checkbox);
    expect(submitButton).toBeDisabled();

    await user.type(passwordInput, "Thi360@dev");
    expect(submitButton).toBeEnabled();

    await user.click(checkbox);
    expect(submitButton).toBeDisabled();

    await user.click(checkbox);
    expect(submitButton).toBeEnabled();

    await user.clear(passwordInput);
    expect(submitButton).toBeDisabled();
  });

  it("#noWarning: unusedAttempts = 0 thì không hiện dòng cảnh báo lượt thi (AC-2)", async () => {
    loadCheckMock.mockResolvedValue(
      loadFixture<AccountDeletionCheckResponse>("me-account-deletion-check-200.json"),
    );
    renderView();

    expect(await screen.findByText(viMessages.account.deletion.explanation)).toBeInTheDocument();
    expect(screen.queryByText(/lượt thi chưa dùng/)).toBeNull();
    expect(screen.queryByText(viMessages.account.deletion.pendingGradingWarning)).toBeNull();
  });

  it("#pendingGrading: pendingGrading = true hiện cảnh báo chờ chấm và vẫn có biểu mẫu; false thì ẩn (AC-17)", async () => {
    loadCheckMock.mockResolvedValue(
      loadFixture<AccountDeletionCheckResponse>("me-account-deletion-check-200-pending-grading.json"),
    );
    const { unmount } = renderView();

    expect(
      await screen.findByText(
        "Bạn có bài đang chờ chấm. Xóa tài khoản thì bạn sẽ không xem được kết quả.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Mật khẩu hiện tại/)).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: "Tôi hiểu tài khoản bị xóa sẽ không khôi phục được." }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xóa tài khoản" })).toBeDisabled();

    unmount();

    loadCheckMock.mockResolvedValue(
      loadFixture<AccountDeletionCheckResponse>("me-account-deletion-check-200.json"),
    );
    renderView();
    expect(await screen.findByText(viMessages.account.deletion.explanation)).toBeInTheDocument();
    expect(screen.queryByText(viMessages.account.deletion.pendingGradingWarning)).toBeNull();
  });

  const allBlockedFixture = loadFixture<AccountDeletionCheckResponse>(
    "me-account-deletion-check-200-blocked.json",
  );
  const twoBlockers = (subset: AccountDeletionBlocker[]): AccountDeletionCheckResponse => ({
    ...allBlockedFixture,
    blockers: allBlockedFixture.blockers.filter((b) => subset.includes(b)),
  });

  it.each<{
    name: string;
    response: AccountDeletionCheckResponse;
    expectedMessages: string[];
    expectedLinks: Array<{ name: string; href: string }>;
  }>([
    {
      name: "ATTEMPT_IN_PROGRESS",
      response: loadFixture("me-account-deletion-check-200-blocked-attempt.json"),
      expectedMessages: [viMessages.account.deletion.blockedAttempt],
      expectedLinks: [{ name: "Bài thi của tôi", href: "/home" }],
    },
    {
      name: "ORDER_BEING_CHECKED",
      response: loadFixture("me-account-deletion-check-200-blocked-order.json"),
      expectedMessages: [viMessages.account.deletion.blockedOrder],
      expectedLinks: [],
    },
    {
      name: "ORDER_PENDING_PAYMENT",
      response: loadFixture("me-account-deletion-check-200-blocked-pending-payment.json"),
      expectedMessages: [viMessages.account.deletion.blockedPendingPayment],
      expectedLinks: [{ name: "Đơn của tôi", href: "/account/orders" }],
    },
    {
      name: "ATTEMPT_IN_PROGRESS + ORDER_BEING_CHECKED",
      response: twoBlockers(["ATTEMPT_IN_PROGRESS", "ORDER_BEING_CHECKED"]),
      expectedMessages: [
        viMessages.account.deletion.blockedAttempt,
        viMessages.account.deletion.blockedOrder,
      ],
      expectedLinks: [{ name: "Bài thi của tôi", href: "/home" }],
    },
    {
      name: "ATTEMPT_IN_PROGRESS + ORDER_PENDING_PAYMENT",
      response: twoBlockers(["ATTEMPT_IN_PROGRESS", "ORDER_PENDING_PAYMENT"]),
      expectedMessages: [
        viMessages.account.deletion.blockedAttempt,
        viMessages.account.deletion.blockedPendingPayment,
      ],
      expectedLinks: [
        { name: "Bài thi của tôi", href: "/home" },
        { name: "Đơn của tôi", href: "/account/orders" },
      ],
    },
    {
      name: "ORDER_BEING_CHECKED + ORDER_PENDING_PAYMENT",
      response: twoBlockers(["ORDER_BEING_CHECKED", "ORDER_PENDING_PAYMENT"]),
      expectedMessages: [
        viMessages.account.deletion.blockedOrder,
        viMessages.account.deletion.blockedPendingPayment,
      ],
      expectedLinks: [{ name: "Đơn của tôi", href: "/account/orders" }],
    },
    {
      name: "all three blockers",
      response: allBlockedFixture,
      expectedMessages: [
        viMessages.account.deletion.blockedAttempt,
        viMessages.account.deletion.blockedOrder,
        viMessages.account.deletion.blockedPendingPayment,
      ],
      expectedLinks: [
        { name: "Bài thi của tôi", href: "/home" },
        { name: "Đơn của tôi", href: "/account/orders" },
      ],
    },
  ])("#blocked ($name): hiện đủ câu chặn và liên kết, không có ô mật khẩu, ô tích hay nút xóa (AC-3)", async ({
    response,
    expectedMessages,
    expectedLinks,
  }) => {
    loadCheckMock.mockResolvedValue(response);
    renderView();

    const firstMessage = await screen.findByText(expectedMessages[0]!);
    const blockedSection = firstMessage.closest("section")!;
    for (const message of expectedMessages) {
      expect(within(blockedSection).getByText(message)).toBeInTheDocument();
    }
    const links = within(blockedSection).queryAllByRole("link");
    expect(links).toHaveLength(expectedLinks.length);
    for (const expectedLink of expectedLinks) {
      expect(within(blockedSection).getByRole("link", { name: expectedLink.name })).toHaveAttribute(
        "href",
        expectedLink.href,
      );
    }

    expect(screen.queryByLabelText(/Mật khẩu hiện tại/)).toBeNull();
    expect(screen.queryByRole("checkbox")).toBeNull();
    expect(screen.queryByRole("button", { name: "Xóa tài khoản" })).toBeNull();
  });

  it("#confirm: Hủy đóng hộp không gọi API; đồng ý gọi đúng một lần, khóa nút đang xử lý và bấm lần hai không gửi thêm (AC-5)", async () => {
    let resolveSubmit!: (value: DeleteAccountResult) => void;
    submitDeletionMock.mockImplementation(
      () =>
        new Promise<DeleteAccountResult>((resolve) => {
          resolveSubmit = resolve;
        }),
    );
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));

    let dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Xóa tài khoản vĩnh viễn?" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Xóa tài khoản" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Hủy" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(submitDeletionMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    dialog = await screen.findByRole("dialog");
    const confirmButton = within(dialog).getByRole("button", { name: "Xóa tài khoản" });

    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);

    await waitFor(() => expect(submitDeletionMock).toHaveBeenCalledTimes(1));
    expect(submitDeletionMock).toHaveBeenCalledWith(
      { currentPassword: "Thi360@dev" },
      expect.anything(),
    );
    expect(confirmButton).toHaveAttribute("aria-disabled", "true");
    expect(confirmButton).toHaveAttribute("aria-busy", "true");

    resolveSubmit({ kind: "deleted" });
    await waitFor(() => expect(navMocks.replace).toHaveBeenCalledWith("/login?deleted=1"));
    expect(submitDeletionMock).toHaveBeenCalledTimes(1);
  });

  it("#checkFailed: deletion-check lỗi 500 hoặc mạng hiện nút Thử lại và bấm thì gọi lại (AC-6)", async () => {
    loadCheckMock
      .mockRejectedValueOnce(new ApiStatusError(500, "boom"))
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(
        loadFixture<AccountDeletionCheckResponse>("me-account-deletion-check-200.json"),
      );
    const user = userEvent.setup();
    renderView();

    await user.click(await screen.findByRole("button", { name: "Thử lại" }));
    await user.click(await screen.findByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText(viMessages.account.deletion.explanation)).toBeInTheDocument();
    expect(loadCheckMock).toHaveBeenCalledTimes(3);
  });

  it("#deleted: 204 dọn phiên, làm rỗng bộ đệm React Query và chuyển sang /login?deleted=1; lỗi clearSessionBestEffort vẫn chuyển trang (AC-7)", async () => {
    const user = userEvent.setup();
    const { client, unmount } = renderView();
    client.setQueryData(["profile", "me"], { username: "hocvien01" });
    const clearSpy = vi.spyOn(client, "clear");

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    await waitFor(() => expect(navMocks.replace).toHaveBeenCalledWith("/login?deleted=1"));
    expect(clearSessionMock).toHaveBeenCalledTimes(1);
    expect(clearSpy).toHaveBeenCalledTimes(1);
    expect(client.getQueryCache().find({ queryKey: ["profile", "me"] })).toBeUndefined();
    expect(client.getQueryCache().getAll().every((query) => query.state.data === undefined)).toBe(true);
    expect(loadCheckMock).toHaveBeenCalledTimes(1);

    unmount();
    navMocks.replace.mockReset();
    clearSessionMock.mockReset();
    loadCheckMock.mockClear();
    clearSessionMock.mockRejectedValueOnce(new Error("network down"));

    const second = renderView();
    second.client.setQueryData(["profile", "me"], { username: "hocvien01" });
    const secondClearSpy = vi.spyOn(second.client, "clear");
    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const secondDialog = await screen.findByRole("dialog");
    await user.click(within(secondDialog).getByRole("button", { name: "Xóa tài khoản" }));

    await waitFor(() => expect(navMocks.replace).toHaveBeenCalledWith("/login?deleted=1"));
    expect(clearSessionMock).toHaveBeenCalledTimes(1);
    expect(secondClearSpy).toHaveBeenCalledTimes(1);
    expect(second.client.getQueryCache().find({ queryKey: ["profile", "me"] })).toBeUndefined();
    expect(second.client.getQueryCache().getAll().every((query) => query.state.data === undefined)).toBe(true);
  });

  it("#wrongPassword: 400 WRONG_OLD_PASSWORD báo lỗi tại ô mật khẩu, xóa trắng ô và giữ phiên (AC-9)", async () => {
    submitDeletionMock.mockResolvedValue({ kind: "wrongPassword" });
    const user = userEvent.setup();
    renderView();

    const passwordInput = await screen.findByLabelText(/Mật khẩu hiện tại/);
    await user.type(passwordInput, "WrongPass1!");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await screen.findByText("Mật khẩu hiện tại chưa đúng.")).toBeInTheDocument();
    expect(passwordInput).toHaveValue("");
    expect(passwordInput).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(passwordInput).toHaveFocus());
    expect(screen.getByRole("button", { name: "Xóa tài khoản" })).toBeDisabled();
    expect(clearSessionMock).not.toHaveBeenCalled();
    expect(navMocks.replace).not.toHaveBeenCalled();
  });

  it("#throttled: 429 LOGIN_THROTTLED hiện câu báo khóa tạm và giữ phiên (AC-10)", async () => {
    submitDeletionMock.mockResolvedValue({ kind: "throttled" });
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau.",
    );
    expect(clearSessionMock).not.toHaveBeenCalled();
    expect(navMocks.replace).not.toHaveBeenCalled();
  });

  it("#blockedOnSubmit: 409 ACCOUNT_DELETION_BLOCKED chuyển sang khối chặn theo blockers của thân 409 (AC-11)", async () => {
    const blocked409 = loadFixture<{ blockers: AccountDeletionBlocker[] }>(
      "me-account-deletion-409-account-deletion-blocked.json",
    );
    submitDeletionMock.mockResolvedValue({ kind: "blocked", blockers: blocked409.blockers });
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    const attemptMsg = await screen.findByText(viMessages.account.deletion.blockedAttempt);
    const blockedSection = attemptMsg.closest("section")!;
    expect(within(blockedSection).getByText(viMessages.account.deletion.blockedOrder)).toBeInTheDocument();
    expect(
      within(blockedSection).getByText(viMessages.account.deletion.blockedPendingPayment),
    ).toBeInTheDocument();
    expect(within(blockedSection).getByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute(
      "href",
      "/home",
    );
    expect(within(blockedSection).getByRole("link", { name: "Đơn của tôi" })).toHaveAttribute(
      "href",
      "/account/orders",
    );
    expect(screen.queryByLabelText(/Mật khẩu hiện tại/)).toBeNull();
    expect(screen.queryByRole("checkbox")).toBeNull();
    expect(clearSessionMock).not.toHaveBeenCalled();
  });

  it.each([
    ["mất mạng", async () => Promise.reject(new TypeError("Failed to fetch"))],
    ["500", async () => Promise.reject(new ApiStatusError(500, "Internal"))],
    ["403", async () => ({ kind: "notAllowed" as const })],
  ])("#failed (%s): báo lỗi chung, không dọn phiên và nút bấm lại được (AC-12)", async (_label, impl) => {
    submitDeletionMock.mockImplementation(impl);
    const user = userEvent.setup();
    renderView();

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa xóa được tài khoản. Vui lòng thử lại.",
    );
    expect(clearSessionMock).not.toHaveBeenCalled();
    expect(navMocks.replace).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Xóa tài khoản" })).toBeEnabled();
  });

  it("#retryGets401: dưới QueryProvider thật, lần thử lại nhận 401 xử lý như 204 không qua chuyển hướng /login của QueryProvider; 403 không sang /forbidden (AC-18)", async () => {
    const providerNavigate = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(
        jsonResponse({ code: "UNAUTHORIZED", message: "Unauthorized", fieldErrors: [], traceId: "t" }, 401),
      );
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    const { unmount } = render(
      <NextIntlClientProvider
        locale="vi-VN"
        messages={viMessages}
        timeZone={APP_TIME_ZONE}
        formats={INTL_FORMATS}
      >
        <UiStringsProvider locale="vi">
          <QueryProvider navigate={providerNavigate}>
            <DeleteAccountView
              loadCurrentSession={async () => SESSION}
              loadCheck={loadCheckMock}
              submitDeletion={deleteAccount}
              clearSession={clearSessionMock}
            />
          </QueryProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>,
    );

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    let dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa xóa được tài khoản. Vui lòng thử lại.",
    );
    expect(clearSessionMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    await waitFor(() => expect(navMocks.replace).toHaveBeenCalledWith("/login?deleted=1"));
    expect(clearSessionMock).toHaveBeenCalledTimes(1);
    expect(providerNavigate).not.toHaveBeenCalled();
    expect(navMocks.replace).not.toHaveBeenCalledWith(FORBIDDEN_PATH);

    unmount();
    navMocks.replace.mockReset();
    clearSessionMock.mockReset();
    providerNavigate.mockReset();

    const forbiddenFixture = loadFixture("me-account-deletion-403-account-deletion-not-allowed.json");
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(jsonResponse(forbiddenFixture, 403)),
    );

    render(
      <NextIntlClientProvider
        locale="vi-VN"
        messages={viMessages}
        timeZone={APP_TIME_ZONE}
        formats={INTL_FORMATS}
      >
        <UiStringsProvider locale="vi">
          <QueryProvider navigate={providerNavigate}>
            <DeleteAccountView
              loadCurrentSession={async () => SESSION}
              loadCheck={loadCheckMock}
              submitDeletion={deleteAccount}
              clearSession={clearSessionMock}
            />
          </QueryProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>,
    );

    await user.type(await screen.findByLabelText(/Mật khẩu hiện tại/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Xóa tài khoản" }));
    dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa xóa được tài khoản. Vui lòng thử lại.",
    );
    expect(navMocks.replace).not.toHaveBeenCalledWith(FORBIDDEN_PATH);
    expect(providerNavigate).not.toHaveBeenCalled();
    expect(clearSessionMock).not.toHaveBeenCalled();
  });

  it("giao diện tiếng Anh không còn chữ tiếng Việt ở cả trạng thái Ready, hộp xác nhận và Blocked (AC-16)", async () => {
    const user = userEvent.setup();
    const { container, unmount } = renderView({ locale: "en" });

    expect(await screen.findByText(enMessages.account.deletion.explanation)).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Delete account" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "You have 3 unused exam attempts left. These attempts will be lost and will not be refunded.",
      ),
    ).toBeInTheDocument();
    expect(VIETNAMESE_DIACRITIC_RE.test(container.querySelector("main")!.textContent ?? "")).toBe(false);

    await user.type(screen.getByLabelText(/Current password/), "Thi360@dev");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Delete account permanently?" })).toBeInTheDocument();
    expect(VIETNAMESE_DIACRITIC_RE.test(dialog.textContent ?? "")).toBe(false);

    unmount();

    loadCheckMock.mockResolvedValue(allBlockedFixture);
    const blocked = renderView({ locale: "en" });
    expect(await screen.findByText(enMessages.account.deletion.blockedAttempt)).toBeInTheDocument();
    expect(VIETNAMESE_DIACRITIC_RE.test(blocked.container.querySelector("main")!.textContent ?? "")).toBe(
      false,
    );
  });
});
