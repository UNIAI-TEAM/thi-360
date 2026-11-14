import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { PermissionUsersTab } from "../components/permission-users-tab";
import type { PageResponsePermissionUserResponse, PermissionResponse } from "../types";

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
    id: "perm-user-learning-view",
    code: "USER.LEARNING_VIEW",
    bitIndex: 3,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem dữ liệu học tập",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 12,
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

const NO_USERS: PageResponsePermissionUserResponse = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

async function renderTab() {
  renderWithProviders(
    <SessionProvider
      loadSession={async () => ({
        ...TEST_ADMIN_SESSION,
        permissions: ["PERMISSION.VIEW", "USER.VIEW"],
      })}
    >
      <PermissionUsersTab
        selectedPermissionCode="USER.VIEW"
        listPermissions={async () => PERMISSIONS}
        listUsersByPermission={async () => NO_USERS}
      />
    </SessionProvider>,
  );
  const picker = await screen.findByRole("region", { name: "Chọn quyền" });
  const list = await within(picker).findByRole("list");
  return { picker, list };
}

describe("PermissionUsersTab: bố cục cột chọn quyền", () => {
  it("danh sách quyền có bóng báo còn cuộn và vẫn chỉ gồm các mục quyền", async () => {
    const { list } = await renderTab();
    expect(list).toHaveClass("scroll-shadows-y", "overflow-y-auto");
    // Bóng là phần tử giả của danh sách: không thêm phần tử nào vào giữa các mục.
    expect(within(list).getAllByRole("listitem")).toHaveLength(PERMISSIONS.length);
    expect(Array.from(list.children).every((child) => child.tagName === "LI")).toBe(true);
  });

  it("tên quyền dài xuống tối đa hai dòng thay vì bị cắt một dòng, không dựa vào title", async () => {
    const { list } = await renderTab();
    const name = within(list).getByText("Tài khoản · Xem dữ liệu học tập");
    expect(name).toHaveClass("line-clamp-2");
    expect(name).not.toHaveClass("truncate");
    expect(name).not.toHaveAttribute("title");

    // Mã quyền vẫn là dòng riêng ngay dưới tên, số người vẫn nằm trong cùng nút.
    const button = name.closest("button") as HTMLButtonElement;
    expect(name.nextElementSibling).toHaveTextContent("USER.LEARNING_VIEW");
    expect(within(button).getByText("12 người")).toBeInTheDocument();
    expect(button).toHaveClass("items-center");
  });

  it("hai khung đổi sang hai cột theo bề rộng vùng chứa, không theo bề rộng cửa sổ", async () => {
    const { picker } = await renderTab();
    const container = picker.parentElement as HTMLElement;
    const usersPane = container.querySelector('[aria-labelledby="rbac-perm-users-heading"]') as HTMLElement;

    expect(container).toHaveClass("@container", "flex", "flex-wrap");
    expect(picker).toHaveClass("w-full", "@5xl:w-[320px]", "@5xl:shrink-0");
    expect(usersPane).toHaveClass("min-w-0", "flex-[1_1_100%]", "@5xl:flex-1");
    for (const element of [container, picker, usersPane]) {
      expect(element.className).not.toMatch(/(^|\s)(sm|md|lg|xl|2xl):/);
    }
  });
});
