import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { notify } from "@thi360/ui";
import type * as UiKit from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "@/features/shell/app-shell";
import vi_messages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import type * as SessionApi from "./api";
import { ForbiddenView } from "./forbidden-view";
import { SessionProvider } from "./session-provider";

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  statusPageProps: [] as Array<Record<string, unknown>>,
}));
vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return { ...actual, logout: mocks.logout };
});
vi.mock("next/navigation", () => ({ usePathname: () => "/appearance" }));
// Ghi lại prop truyền cho StatusPage của kit để kiểm lựa chọn "không dựng <main> riêng" khi nằm trong khung trang.
vi.mock("@thi360/ui", async (importOriginal) => {
  const actual = await importOriginal<typeof UiKit>();
  return {
    ...actual,
    StatusPage: (props: UiKit.StatusPageProps) => {
      mocks.statusPageProps.push({ ...props });
      return actual.StatusPage(props);
    },
  };
});
afterEach(() => {
  mocks.logout.mockReset();
  mocks.statusPageProps.length = 0;
  document.title = "";
});

describe("ForbiddenView", () => {
  it("nêu tên tài khoản đang đăng nhập, liên kết Về trang chủ theo homeUrl và nút Đăng nhập bằng tài khoản khác", async () => {
    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          username: "student01",
          access: "EXAM",
          homeUrl: "http://localhost:3000/home",
          permissions: [],
        })}
      >
        <ForbiddenView />
      </SessionProvider>,
    );
    expect(await screen.findByText("student01")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute(
      "href",
      "http://localhost:3000/home",
    );

    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" }));
    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
  });

  it("reason=origin (G9): nêu lỗi địa chỉ quản trị thay vì lời thiếu quyền", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <ForbiddenView reason="origin" />
      </SessionProvider>,
    );
    expect(await screen.findByText(/THI360_ADMIN_URL/)).toBeInTheDocument();
    expect(screen.queryByText("ops.admin")).not.toBeInTheDocument();
  });

  it("trang riêng /forbidden: tự dựng <main>, không đổi tiêu đề thẻ trình duyệt", async () => {
    document.title = "Bạn không có quyền vào trang này · Thi360";
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <ForbiddenView />
      </SessionProvider>,
    );
    await screen.findByText("ops.admin");

    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(mocks.statusPageProps.at(-1)).not.toHaveProperty("embedded");
    expect(document.title).toBe("Bạn không có quyền vào trang này · Thi360");
  });

  it("trong khung trang: yêu cầu StatusPage không dựng <main> riêng, đổi tiêu đề thẻ trình duyệt và trả lại khi rời đi", async () => {
    document.title = "Tùy chỉnh giao diện · Thi360";
    const view = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <ForbiddenView />
        </AppShell>
      </SessionProvider>,
    );
    await screen.findByText("ops.admin", { selector: "strong" });

    expect(mocks.statusPageProps.at(-1)).toMatchObject({ embedded: true });
    expect(screen.getByRole("heading", { level: 1, name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    await waitFor(() => expect(document.title).toBe("Bạn không có quyền vào trang này · Thi360"));

    view.unmount();
    expect(document.title).toBe("Tùy chỉnh giao diện · Thi360");
  });

  it("trong khung trang: rời đi sau khi trang mới đã đặt tiêu đề thì không ghi đè tiêu đề đó", async () => {
    document.title = "Tùy chỉnh giao diện · Thi360";
    const view = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <ForbiddenView />
        </AppShell>
      </SessionProvider>,
    );
    await waitFor(() => expect(document.title).toBe("Bạn không có quyền vào trang này · Thi360"));

    document.title = "Bảng điều khiển · Thi360";
    view.unmount();
    expect(document.title).toBe("Bảng điều khiển · Thi360");
  });

  it("báo lỗi bằng toast khi đăng xuất đổi tài khoản thất bại", async () => {
    const toastError = vi.spyOn(notify, "error").mockImplementation(() => "");
    mocks.logout.mockRejectedValueOnce(new Error("network"));

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <ForbiddenView />
      </SessionProvider>,
    );
    await screen.findByText("ops.admin");

    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" }));
    await waitFor(() => expect(toastError).toHaveBeenCalledWith(vi_messages.errors.logoutFailed));
    toastError.mockRestore();
  });
});
