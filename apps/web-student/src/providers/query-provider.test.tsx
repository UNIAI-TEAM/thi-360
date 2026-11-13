import { useQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import en_messages from "@/messages/en.json";
import vi_messages from "@/messages/vi.json";
import { FORBIDDEN_PATH, QueryProvider } from "./query-provider";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  pathname: { current: "/home" },
  toastError: vi.fn(),
  toaster: vi.fn(),
  refreshSession: vi.fn(),
  clearSessionBestEffort: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => mocks.pathname.current,
}));
vi.mock("@thi360/ui", () => ({
  notify: { error: mocks.toastError },
  Toaster: (props: Record<string, unknown>) => {
    mocks.toaster(props);
    return null;
  },
}));
vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return {
    ...actual,
    refreshSession: mocks.refreshSession,
    clearSessionBestEffort: mocks.clearSessionBestEffort,
  };
});

const queryFn = vi.fn<() => Promise<unknown>>();

function Query({ name }: { name: string }) {
  const { status } = useQuery({ queryKey: [name], queryFn });
  return <p data-testid={name}>{status}</p>;
}

function Probe({ keys = ["a"] }: { keys?: string[] }) {
  return (
    <>
      {keys.map((key) => (
        <Query key={key} name={key} />
      ))}
    </>
  );
}

function renderProbe(keys?: string[]) {
  return render(
    <NextIntlClientProvider locale="vi" messages={vi_messages} timeZone="Asia/Ho_Chi_Minh">
      <QueryProvider navigate={mocks.navigate}>
        <Probe keys={keys} />
      </QueryProvider>
    </NextIntlClientProvider>,
  );
}

beforeEach(() => {
  mocks.pathname.current = "/home";
  mocks.refreshSession.mockResolvedValue(undefined);
  mocks.clearSessionBestEffort.mockResolvedValue(undefined);
  window.sessionStorage.clear();
});

afterEach(() => {
  queryFn.mockReset();
  mocks.replace.mockReset();
  mocks.toastError.mockReset();
  mocks.toaster.mockReset();
  mocks.refreshSession.mockReset();
  mocks.clearSessionBestEffort.mockReset();
  mocks.navigate.mockReset();
  window.sessionStorage.clear();
});

describe("web-student QueryProvider", () => {
  it("401 đồng thời gọi /auth/refresh một lần (single-flight) rồi tải lại khi thành công", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(401));
    renderProbe(["a", "b", "c"]);

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith(window.location.href));
    expect(mocks.refreshSession).toHaveBeenCalledTimes(1);
    expect(mocks.navigate).toHaveBeenCalledTimes(1);
  });

  it("401 khi /auth/refresh trả 401 thì chuyển về /dang-nhap kèm callbackUrl", async () => {
    mocks.refreshSession.mockRejectedValue(new ApiStatusError(401));
    queryFn.mockRejectedValue(new ApiStatusError(401));
    renderProbe();

    await waitFor(() =>
      expect(mocks.navigate).toHaveBeenCalledWith(
        `http://localhost:3000/dang-nhap?callbackUrl=${encodeURIComponent(window.location.href)}`,
      ),
    );
    expect(mocks.clearSessionBestEffort).not.toHaveBeenCalled();
  });

  it("401 khi /auth/refresh lỗi mạng hoặc 5xx thì gọi logout tối đa trước khi về /dang-nhap", async () => {
    mocks.refreshSession.mockRejectedValue(new ApiStatusError(500));
    queryFn.mockRejectedValue(new ApiStatusError(401));
    renderProbe();

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledTimes(1));
    expect(mocks.clearSessionBestEffort).toHaveBeenCalledTimes(1);
  });

  it("cập nhật pathname khi chuyển từ /dang-nhap sang /home và khử trùng lặp 403", async () => {
    mocks.pathname.current = "/dang-nhap";
    queryFn.mockRejectedValueOnce(new ApiStatusError(403));

    const { rerender } = render(
      <NextIntlClientProvider locale="vi" messages={vi_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <Probe keys={["init"]} />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("init")).toHaveTextContent("error"));
    expect(mocks.toastError).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();

    mocks.pathname.current = "/home";
    queryFn.mockRejectedValue(new ApiStatusError(403));
    rerender(
      <NextIntlClientProvider locale="vi" messages={vi_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <Probe keys={["a", "b"]} />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith(FORBIDDEN_PATH));
    expect(mocks.replace).toHaveBeenCalledTimes(1);
    expect(mocks.toastError).toHaveBeenCalledTimes(1);
    expect(mocks.toastError).toHaveBeenCalledWith(vi_messages.errors.forbiddenToast);

    mocks.pathname.current = FORBIDDEN_PATH;
    rerender(
      <NextIntlClientProvider locale="vi" messages={vi_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <Probe keys={["c"]} />
        </QueryProvider>
      </NextIntlClientProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("c")).toHaveTextContent("error"));
    expect(mocks.replace).toHaveBeenCalledTimes(1);
    expect(mocks.toastError).toHaveBeenCalledTimes(1);
  });

  it("truyền nhãn aria đã dịch cho Toaster theo locale vi và en", () => {
    render(
      <NextIntlClientProvider locale="vi" messages={vi_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <span />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    expect(mocks.toaster).toHaveBeenCalledWith(
      expect.objectContaining({
        containerAriaLabel: vi_messages.common.notificationsLabel,
        closeButtonAriaLabel: vi_messages.common.closeToast,
      }),
    );

    mocks.toaster.mockClear();

    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <span />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    expect(mocks.toaster).toHaveBeenCalledWith(
      expect.objectContaining({
        containerAriaLabel: en_messages.common.notificationsLabel,
        closeButtonAriaLabel: en_messages.common.closeToast,
      }),
    );
  });
});
