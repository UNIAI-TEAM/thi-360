import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { PermissionsTab } from "../components/permissions-tab";
import type { PermissionResponse } from "../types";

/*
 * UNI-2587: tab Quyền ở vùng nội dung hẹp hơn 672px là danh sách thẻ gom theo đối tượng. `useCardMode` đo vùng chứa
 * của tab lúc dựng; jsdom không có layout nên giả bề rộng bằng `getBoundingClientRect`.
 */

const PERMISSIONS: PermissionResponse[] = [
  {
    id: "perm-user-view",
    code: "USER.VIEW",
    bitIndex: 0,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 1,
  },
  {
    id: "perm-user-reset",
    code: "USER.RESET_PASSWORD",
    bitIndex: 4,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Đặt lại mật khẩu",
    scope: "MANAGE",
    roleCount: 0,
    userCount: 0,
  },
  {
    id: "perm-catalog-view",
    code: "CATALOG.VIEW",
    bitIndex: 9,
    groupCode: "CATALOG",
    groupName: "Danh mục",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 3,
    userCount: 2,
  },
];

function mockContainerWidth(width: number) {
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    () =>
      ({ width, height: 0, top: 0, left: 0, right: width, bottom: 0, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect,
  );
}

function renderTab(onSelectPermissionUsers?: (code: string) => void) {
  return renderWithProviders(
    <SessionProvider
      loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions: ["PERMISSION.VIEW", "USER.VIEW"] })}
    >
      <PermissionsTab listPermissions={async () => PERMISSIONS} onSelectPermissionUsers={onSelectPermissionUsers} />
    </SessionProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Tab Quyền: danh sách thẻ khi vùng nội dung hẹp (UNI-2587)", () => {
  it("vùng nội dung 328px: mỗi đối tượng một tiêu đề và một danh sách, mỗi quyền một thẻ", async () => {
    mockContainerWidth(328);
    const onSelectPermissionUsers = vi.fn();
    renderTab(onSelectPermissionUsers);

    const userList = await screen.findByRole("list", { name: "Tài khoản" });
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByRole("heading", { level: 2, name: "Tài khoản" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Danh mục" })).toBeInTheDocument();

    const cards = within(userList).getAllByRole("listitem");
    expect(cards).toHaveLength(2);
    const view = cards[0]!;
    expect(view).toHaveClass("relative", "flex", "items-start", "gap-3", "px-4", "py-3");
    expect(within(view).getByText("Xem")).toHaveClass("font-semibold");
    expect(within(view).getByText("USER.VIEW")).toHaveClass("wrap-anywhere", "text-caption");
    expect(within(view).getByText("Quản trị")).toBeInTheDocument();
    expect(within(view).getByText("2 vai trò")).toBeInTheDocument();

    // Quyền riêng có tiền tố; số vai trò 0 vẫn ghi rõ.
    expect(within(cards[1]!).getByText("Quyền riêng: Đặt lại mật khẩu")).toBeInTheDocument();
    expect(within(cards[1]!).getByText("0 vai trò")).toBeInTheDocument();

    // Số tài khoản là nút mở tab Người dùng theo quyền, giữ tên truy cập của bảng.
    await userEvent.click(within(view).getByRole("button", { name: "Xem tài khoản đang có quyền Xem tài khoản" }));
    expect(onSelectPermissionUsers).toHaveBeenCalledWith("USER.VIEW");
    expect(screen.getByText("3 quyền trên 2 đối tượng")).toBeInTheDocument();
  });

  it("không có quyền xem tài khoản: số tài khoản là chữ, không phải nút", async () => {
    mockContainerWidth(328);
    renderWithProviders(
      <SessionProvider loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions: ["PERMISSION.VIEW"] })}>
        <PermissionsTab listPermissions={async () => PERMISSIONS} />
      </SessionProvider>,
    );
    const userList = await screen.findByRole("list", { name: "Tài khoản" });
    expect(within(userList).getByText("1 tài khoản").tagName).toBe("SPAN");
    expect(within(userList).queryByRole("button")).toBeNull();
  });

  it("vùng nội dung 728px (cửa sổ 1024px): vẫn là bảng năm cột như trước", async () => {
    mockContainerWidth(728);
    renderTab(vi.fn());
    const table = await screen.findByRole("table");
    expect(screen.queryByRole("list", { name: "Tài khoản" })).toBeNull();
    expect(table).toHaveClass("min-w-[780px]");
    const headers = within(table).getAllByRole("columnheader").slice(0, 5);
    expect(headers.map((cell) => cell.textContent)).toEqual([
      "Đối tượng và mức",
      "Mã",
      "Phạm vi",
      "Vai trò đang dùng",
      "Tài khoản đang có",
    ]);
    for (const header of headers) expect(header).not.toHaveClass("hidden");
    expect(within(table).getByText("USER.VIEW", { selector: "td > code" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Tài khoản" })).toHaveAttribute("scope", "colgroup");
  });
});
