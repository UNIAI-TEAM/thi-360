import { useQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import vi_messages from "@/messages/vi.json";
import { ApiStatusError } from "@/lib/api/query-client";
import { FORBIDDEN_PATH, QueryProvider } from "./query-provider";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  pathname: { current: "/" },
  toastError: vi.fn(),
  redirectToLogin: vi.fn(),
  refreshSession: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => mocks.pathname.current,
}));
vi.mock("@thi360/ui", () => ({ notify: { error: mocks.toastError }, Toaster: () => null }));
vi.mock("@/lib/auth-redirect", () => ({ redirectToLogin: mocks.redirectToLogin }));
vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return { ...actual, refreshSession: mocks.refreshSession };
});

const queryFn = vi.fn<() => Promise<unknown>>();

function Probe({ keys = ["a"] }: { keys?: string[] }) {
  return (
    <>
      {keys.map((key) => (
        <Query key={key} name={key} />
      ))}
    </>
  );
}

function Query({ name }: { name: string }) {
  const { status } = useQuery({ queryKey: [name], queryFn });
  return <p data-testid={name}>{status}</p>;
}

function renderProbe(keys?: string[]) {
  return render(
    <NextIntlClientProvider locale="vi" messages={vi_messages}>
      <QueryProvider navigate={mocks.navigate}>
        <Probe keys={keys} />
      </QueryProvider>
    </NextIntlClientProvider>,
  );
}

beforeEach(() => {
  mocks.pathname.current = "/";
  mocks.refreshSession.mockResolvedValue(undefined);
  window.sessionStorage.clear();
});

afterEach(() => {
  queryFn.mockReset();
  mocks.replace.mockReset();
  mocks.toastError.mockReset();
  mocks.redirectToLogin.mockReset();
  mocks.refreshSession.mockReset();
  mocks.navigate.mockReset();
  window.sessionStorage.clear();
});

describe("QueryProvider: lỗi toàn cục", () => {
  it("401 chuyển về trang đăng nhập, không toast và không vào /forbidden", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(401));
    renderProbe();

    await waitFor(() => expect(mocks.redirectToLogin).toHaveBeenCalledTimes(1));
    expect(mocks.toastError).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("nhiều request cùng 401 chỉ chuyển hướng một lần", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(401));
    renderProbe(["a", "b", "c"]);

    await waitFor(() => {
      for (const key of ["a", "b", "c"]) expect(screen.getByTestId(key)).toHaveTextContent("error");
    });
    expect(mocks.redirectToLogin).toHaveBeenCalledTimes(1);
  });

  it("403 lần đầu gọi /auth/refresh một lần rồi điều hướng lại; nhiều 403 đồng thời chỉ gọi một lần", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe(["a", "b", "c"]);

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith(window.location.href));
    expect(mocks.refreshSession).toHaveBeenCalledTimes(1);
    expect(mocks.navigate).toHaveBeenCalledTimes(1);
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("403 sau khi đã refresh và điều hướng lại thì hiện toast lỗi rồi chuyển tới /forbidden", async () => {
    window.sessionStorage.setItem("thi360:403-refresh-url", window.location.href);
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe();

    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith(FORBIDDEN_PATH));
    expect(mocks.refreshSession).not.toHaveBeenCalled();
    expect(mocks.toastError).toHaveBeenCalledWith(vi_messages.errors.forbiddenToast);
    expect(mocks.redirectToLogin).not.toHaveBeenCalled();
  });

  it("403 khi /auth/refresh trả 401 thì chuyển về trang đăng nhập", async () => {
    mocks.refreshSession.mockRejectedValue(new ApiStatusError(401));
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe();

    await waitFor(() => expect(mocks.redirectToLogin).toHaveBeenCalledTimes(1));
    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("đang ở /forbidden thì 403 không lặp refresh, toast và điều hướng", async () => {
    mocks.pathname.current = FORBIDDEN_PATH;
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId("a")).toHaveTextContent("error"));
    expect(mocks.refreshSession).not.toHaveBeenCalled();
    expect(mocks.toastError).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("lỗi 4xx khác không được thử lại; không kích hoạt xử lý chung", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(400));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId("a")).toHaveTextContent("error"));
    expect(queryFn).toHaveBeenCalledTimes(1);
    expect(mocks.redirectToLogin).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(mocks.toastError).not.toHaveBeenCalled();
  });
});
