import { useQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import vi_messages from "@/messages/vi.json";
import { ApiStatusError } from "@/lib/api/query-client";
import { QueryProvider } from "./query-provider";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  pathname: { current: "/" },
  toastError: vi.fn(),
  redirectToLogin: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => mocks.pathname.current,
}));
vi.mock("@thi360/ui", () => ({ notify: { error: mocks.toastError }, Toaster: () => null }));
vi.mock("@/lib/auth-redirect", () => ({ redirectToLogin: mocks.redirectToLogin }));

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
      <QueryProvider>
        <Probe keys={keys} />
      </QueryProvider>
    </NextIntlClientProvider>,
  );
}

beforeEach(() => {
  mocks.pathname.current = "/";
});

afterEach(() => {
  queryFn.mockReset();
  mocks.replace.mockReset();
  mocks.toastError.mockReset();
  mocks.redirectToLogin.mockReset();
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

  it("403 hiện toast lỗi rồi chuyển tới /forbidden, không chuyển về đăng nhập", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe();

    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/forbidden"));
    expect(mocks.toastError).toHaveBeenCalledWith(vi_messages.errors.forbiddenToast);
    expect(mocks.redirectToLogin).not.toHaveBeenCalled();
  });

  it("đang ở /forbidden thì 403 không lặp toast và điều hướng", async () => {
    mocks.pathname.current = "/forbidden";
    queryFn.mockRejectedValue(new ApiStatusError(403));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId("a")).toHaveTextContent("error"));
    expect(mocks.toastError).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("lỗi 4xx không được thử lại; lỗi khác không kích hoạt xử lý chung", async () => {
    queryFn.mockRejectedValue(new ApiStatusError(400));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId("a")).toHaveTextContent("error"));
    expect(queryFn).toHaveBeenCalledTimes(1);
    expect(mocks.redirectToLogin).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(mocks.toastError).not.toHaveBeenCalled();
  });
});
