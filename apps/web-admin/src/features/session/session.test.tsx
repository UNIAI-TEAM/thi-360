import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/query-client";
import type { SessionLoader } from "./api";
import { Can } from "./can";
import { SessionProvider, usePermissions, useSession } from "./session-provider";
import type { Session } from "./types";

const ADMIN: Session = {
  id: "u1",
  username: "content.admin",
  fullName: "Quản trị nội dung",
  realm: "THI360",
  access: "MANAGE",
  permissions: ["USER.VIEW", "ROLE.VIEW"],
};

const loadMock = vi.fn<SessionLoader>();

function wrapper(client: QueryClient, loadSession?: SessionLoader) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <SessionProvider loadSession={loadSession}>{children}</SessionProvider>
      </QueryClientProvider>
    );
  };
}

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Probe() {
  const state = useSession();
  const { can, isLoading } = usePermissions();
  return (
    <div>
      <p data-testid="status">{state.status}</p>
      <p data-testid="loading">{String(isLoading)}</p>
      {state.status === "ready" && <p data-testid="name">{state.session.fullName}</p>}
      {state.status === "error" && <button onClick={state.retry}>retry</button>}
      <p data-testid="can-user-view">{String(can("USER.VIEW"))}</p>
      <p data-testid="can-any">{String(can(["USER.MODIFY", "ROLE.VIEW"]))}</p>
      <p data-testid="can-none">{String(can(["USER.MODIFY", "CLASS.VIEW"]))}</p>
    </div>
  );
}

afterEach(() => {
  loadMock.mockReset();
});

describe("SessionProvider", () => {
  it("nạp phiên từ nguồn đã cấu hình và cấp quyền theo mã", async () => {
    loadMock.mockResolvedValue(ADMIN);
    render(<Probe />, { wrapper: wrapper(newClient(), loadMock) });

    expect(screen.getByTestId("status")).toHaveTextContent("loading");
    expect(screen.getByTestId("can-user-view")).toHaveTextContent("false");
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
    expect(loadMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("name")).toHaveTextContent("Quản trị nội dung");
    expect(screen.getByTestId("loading")).toHaveTextContent("false");
    expect(screen.getByTestId("can-user-view")).toHaveTextContent("true");
    expect(screen.getByTestId("can-any")).toHaveTextContent("true");
    expect(screen.getByTestId("can-none")).toHaveTextContent("false");
  });

  it("lỗi tải phiên: trạng thái error, thử lại thành công thì ready", async () => {
    loadMock.mockRejectedValueOnce(new ApiStatusError(500));
    loadMock.mockResolvedValueOnce(ADMIN);
    render(<Probe />, { wrapper: wrapper(newClient(), loadMock) });

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("error"));
    expect(screen.getByTestId("can-user-view")).toHaveTextContent("false");

    await userEvent.click(screen.getByRole("button", { name: "retry" }));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
  });

  it("nguồn mặc định dùng phiên giả khi NEXT_PUBLIC_DEV_FAKE_SESSION=1 ở môi trường dev", async () => {
    vi.stubEnv("NEXT_PUBLIC_DEV_FAKE_SESSION", "1");
    render(<Probe />, { wrapper: wrapper(newClient()) });

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
    expect(screen.getByTestId("can-user-view")).toHaveTextContent("true");
  });

  it("nguồn mặc định bỏ qua phiên giả ở production và báo 401 để QueryProvider chuyển đăng nhập", async () => {
    vi.stubEnv("NEXT_PUBLIC_DEV_FAKE_SESSION", "1");
    vi.stubEnv("NODE_ENV", "production");
    const client = newClient();
    render(<Probe />, { wrapper: wrapper(client) });

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("error"));
    const error = client.getQueryState(["session", "me"])?.error;
    expect(error).toBeInstanceOf(ApiStatusError);
    expect((error as ApiStatusError).status).toBe(401);
  });

  it("hook ngoài provider báo lỗi rõ ràng", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Probe />)).toThrow(/SessionProvider/);
  });
});

describe("<Can>", () => {
  function renderCan(permission: string | string[]) {
    loadMock.mockResolvedValue(ADMIN);
    return render(
      <Can permission={permission} fallback={<span>không có quyền</span>}>
        <button>Tạo tài khoản</button>
      </Can>,
      { wrapper: wrapper(newClient(), loadMock) },
    );
  }

  it("hiện nội dung khi có quyền (một mã hoặc một trong các mã)", async () => {
    renderCan("USER.VIEW");
    expect(await screen.findByRole("button", { name: "Tạo tài khoản" })).toBeInTheDocument();
  });

  it("danh sách mã: một mã trùng là đủ", async () => {
    renderCan(["USER.MODIFY", "ROLE.VIEW"]);
    expect(await screen.findByRole("button", { name: "Tạo tài khoản" })).toBeInTheDocument();
  });

  it("thiếu quyền: hiện fallback, không hiện nội dung", async () => {
    renderCan("USER.MODIFY");
    await waitFor(() => expect(loadMock).toHaveBeenCalled());
    expect(await screen.findByText("không có quyền")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo tài khoản" })).not.toBeInTheDocument();
  });

  it("chưa tải xong phiên: chưa hiện nội dung cần quyền", () => {
    loadMock.mockReturnValue(Promise.withResolvers<Session>().promise);
    render(
      <Can permission="USER.VIEW">
        <button>Tạo tài khoản</button>
      </Can>,
      { wrapper: wrapper(newClient(), loadMock) },
    );

    expect(screen.queryByRole("button", { name: "Tạo tài khoản" })).not.toBeInTheDocument();
  });
});
