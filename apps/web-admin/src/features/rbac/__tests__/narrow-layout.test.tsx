import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { PermissionUsersTab } from "../components/permission-users-tab";
import { PermissionsTab } from "../components/permissions-tab";
import { RolesTab } from "../components/roles-tab";
import { UserRbacTab } from "../components/user-rbac-tab";
import type {
  EffectivePermissionsResponse,
  PageResponsePermissionUserResponse,
  PageResponseRoleSummaryResponse,
  PermissionResponse,
} from "../types";

/*
 * UNI-2586: bốn trang con của Phân quyền dùng được từ cửa sổ 360px (vùng nội dung khoảng 328px). jsdom không có
 * layout nên các test này kiểm hợp đồng lớp: cột nào ẩn theo bề rộng vùng chứa, nội dung của cột ẩn hiện ở đâu.
 */

const PERMISSIONS: PermissionResponse[] = [
  {
    id: "perm-user-reset",
    code: "USER.RESET_PASSWORD",
    bitIndex: 4,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Đặt lại mật khẩu",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 1,
  },
];

const PERMISSION_USERS: PageResponsePermissionUserResponse = {
  items: [
    {
      userId: "u-1",
      code: "U000001",
      username: "content.admin",
      fullName: "Quản trị nội dung",
      status: "INACTIVE",
      sources: [
        {
          type: "ROLE",
          roleId: "role-content",
          roleCode: "CONTENT_ADMIN",
          roleName: "Quản trị nội dung",
        },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

const ROLES: PageResponseRoleSummaryResponse = {
  items: [
    {
      id: "role-ops",
      code: "OPERATIONS",
      name: "Vận hành",
      access: "MANAGE",
      system: false,
      status: "INACTIVE",
      permissionCount: 5,
      userCount: 3,
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    },
  ],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

function withSession(children: React.ReactNode, permissions: string[]) {
  return (
    <SessionProvider loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions })}>
      {children}
    </SessionProvider>
  );
}

describe("Phân quyền ở bề rộng điện thoại (UNI-2586)", () => {
  it("Quyền: thanh lọc xếp lưới khi hẹp; cột Mã và Vai trò ẩn, mã đọc đủ dưới tên quyền", async () => {
    renderWithProviders(
      withSession(<PermissionsTab listPermissions={async () => PERMISSIONS} />, ["PERMISSION.VIEW"]),
    );
    const table = await screen.findByRole("table");

    const searchLabel = screen.getByLabelText("Tìm kiếm").closest("label")!;
    expect(searchLabel).toHaveClass("col-span-full", "@2xl:min-w-[220px]");
    expect(searchLabel).not.toHaveClass("min-w-[220px]");
    const filterBar = searchLabel.parentElement!;
    expect(filterBar).toHaveClass("grid", "@min-[300px]:grid-cols-2", "@2xl:flex", "@2xl:flex-wrap");
    expect(filterBar.closest(".\\@container")).not.toBeNull();

    expect(table).toHaveClass("@2xl:min-w-[780px]");
    expect(table).not.toHaveClass("min-w-[780px]");
    expect(within(table).getByRole("columnheader", { name: "Mã" })).toHaveClass(
      "hidden",
      "@2xl:table-cell",
    );
    expect(within(table).getByRole("columnheader", { name: "Vai trò đang dùng" })).toHaveClass(
      "hidden",
      "@lg:table-cell",
    );

    const codeLine = within(table).getByTestId("permission-code-line");
    expect(codeLine).toHaveClass("@2xl:hidden");
    expect(within(codeLine).getByText("USER.RESET_PASSWORD")).toHaveClass("wrap-anywhere");
    expect(within(codeLine).getByText(/2 vai trò/)).toHaveClass("@lg:hidden");
  });

  it("Người dùng theo quyền: dưới 36rem cột Nguồn, Chi tiết, Trạng thái nhập vào ô tài khoản", async () => {
    renderWithProviders(
      withSession(
        <PermissionUsersTab
          selectedPermissionCode="USER.RESET_PASSWORD"
          listPermissions={async () => PERMISSIONS}
          listUsersByPermission={async () => PERMISSION_USERS}
        />,
        ["PERMISSION.VIEW", "USER.VIEW"],
      ),
    );
    const table = await screen.findByRole("table");
    const accountCell = (await within(table).findByText("content.admin")).closest("td, th")!;

    const wrapper = table.closest(".\\@container")!;
    for (const col of ["source", "detail", "status"]) {
      expect(wrapper.className).toContain(`@max-[36rem]:[&_:is(th,td):has([data-col=${col}])]:hidden`);
      expect(table.querySelectorAll(`thead [data-col=${col}]`)).toHaveLength(1);
      expect(table.querySelectorAll(`tbody [data-col=${col}]`)).toHaveLength(1);
    }
    // Cột Chi tiết giữ bề rộng tối thiểu, không bị ép xuống mỗi từ một dòng.
    expect(table.querySelector("tbody [data-col=detail]")).toHaveClass("min-w-48");

    const summary = accountCell.querySelector("[data-col-summary]")!;
    expect(summary).toHaveClass("@min-[36rem]:hidden");
    expect(within(summary as HTMLElement).getByText("Từ vai trò")).toBeInTheDocument();
    expect(within(summary as HTMLElement).getByText("Vai trò: Quản trị nội dung")).toBeInTheDocument();
    expect(within(summary as HTMLElement).getByText("Ngừng hoạt động")).toBeInTheDocument();

    // Thanh lọc: ô tìm kiếm hết chiều ngang, hai ô chọn hai cột; từ @xl về hàng flex.
    const searchLabel = screen.getByLabelText("Tìm tài khoản").closest("label")!;
    expect(searchLabel).toHaveClass("col-span-full", "@xl:flex-[2_1_220px]");
    expect(searchLabel.parentElement).toHaveClass("grid", "@min-[300px]:grid-cols-2", "@xl:flex");
  });


  it("Quyền hiệu lực của tài khoản: dưới 36rem cột Đối tượng và Mã nhập vào dòng phụ dưới tên quyền", async () => {
    const effective: EffectivePermissionsResponse = {
      userId: "u-1",
      effectiveAccess: "MANAGE",
      hasManageRole: true,
      inactiveDirectPermissions: [],
      permissions: [
        {
          permissionId: "perm-user-reset",
          code: "USER.RESET_PASSWORD",
          name: "Đặt lại mật khẩu",
          groupCode: "USER",
          groupName: "Tài khoản",
          sources: [{ type: "DIRECT" }],
        },
      ],
    };
    renderWithProviders(
      withSession(
        <UserRbacTab
          userId="u-1"
          readOnly
          getUserRoles={async () => ({ userId: "u-1", access: "MANAGE", roles: [] })}
          getUserPermissions={async () => ({ userId: "u-1", hasManageRole: true, permissions: [] })}
          getEffectivePermissions={async () => effective}
          listRoles={async () => ROLES}
          listPermissions={async () => PERMISSIONS}
        />,
        ["USER.VIEW", "ROLE.VIEW", "PERMISSION.VIEW"],
      ),
    );
    const table = await screen.findByRole("table");
    const wrapper = table.closest(".\\@container")!;
    expect(wrapper.className).toContain("@max-[36rem]:[&_:is(th,td):has([data-col=object])]:hidden");
    expect(wrapper.className).toContain("@max-[36rem]:[&_:is(th,td):has([data-col=code])]:hidden");

    const summary = table.querySelector("tbody [data-col-summary]") as HTMLElement;
    expect(summary).toHaveClass("@min-[36rem]:hidden");
    expect(summary).toHaveTextContent("Tài khoản · USER.RESET_PASSWORD");
    expect(within(summary).getByText("USER.RESET_PASSWORD")).toHaveClass("wrap-anywhere");
  });
});

/**
 * jsdom không có layout: cho mọi phần tử cùng một bề rộng (khung đo của bảng) và thay `ResizeObserver` bằng bản
 * rỗng, như test danh sách thẻ của kit (`packages/ui/src/components/table-cards.test.tsx`).
 */
function installContainerWidth(width: number) {
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    () =>
      ({ width, height: 0, top: 0, left: 0, right: width, bottom: 0, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect,
  );
}

describe("Phân quyền: bảng thành danh sách thẻ ở khung hẹp (UNI-2587)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  function renderRoles() {
    renderWithProviders(
      withSession(
        <RolesTab listRoles={async () => ROLES} listPermissions={async () => PERMISSIONS} />,
        ["ROLE.VIEW", "ROLE.MODIFY"],
      ),
    );
  }

  it("Vai trò: khung hẹp hơn 640px, mỗi vai trò một thẻ: tên và nút thao tác, mã, chip trạng thái rồi phạm vi và loại, số quyền", async () => {
    installContainerWidth(358);
    renderRoles();
    const list = await screen.findByRole("list", { name: "Vai trò" });
    expect(screen.queryByRole("table")).toBeNull();
    const title = await within(list).findByText("Vận hành");
    const [card] = within(list).getAllByRole("listitem");

    expect(title).toHaveClass("font-semibold", "line-clamp-2");
    expect(within(card!).getByText("OPERATIONS")).toHaveClass("text-caption", "wrap-anywhere");
    const chips = within(card!).getByText("Ngừng hoạt động").parentElement!;
    expect(Array.from(chips.children, (chip) => chip.textContent)).toEqual([
      "Ngừng hoạt động",
      "Quản trị",
      "Tự tạo",
    ]);
    expect(within(card!).getByText("Số quyền: 5 · Tài khoản đang giữ: 3")).toBeInTheDocument();

    // Nút thao tác giống bảng, nằm trên vùng bấm của thẻ; vai trò đang có người giữ thì không xóa được.
    const edit = within(card!).getByRole("button", { name: "Sửa vai trò Vận hành" });
    expect(edit.closest(".z-1")).toHaveClass("relative");
    expect(within(card!).getByRole("button", { name: "Xóa vai trò Vận hành" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("Vai trò: chip Hoạt động vẫn hiện trên thẻ; không có quyền sửa thì chỉ có nút Xem", async () => {
    installContainerWidth(358);
    renderWithProviders(
      withSession(
        <RolesTab
          listRoles={async () => ({ ...ROLES, items: [{ ...ROLES.items[0]!, status: "ACTIVE" }] })}
          listPermissions={async () => PERMISSIONS}
        />,
        ["ROLE.VIEW"],
      ),
    );
    const list = await screen.findByRole("list", { name: "Vai trò" });
    await within(list).findByText("Vận hành");
    expect(within(list).getByText("Hoạt động")).toBeInTheDocument();
    expect(within(list).getAllByRole("button").map((button) => button.getAttribute("aria-label"))).toEqual([
      "Xem vai trò Vận hành",
    ]);
  });

  it("Vai trò: khung rộng vẫn là bảng đủ cột, không còn dòng tóm tắt dưới tên", async () => {
    installContainerWidth(900);
    renderRoles();
    await screen.findByText("Vận hành");
    const table = screen.getByRole("table", { name: "Vai trò" });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(7);
    expect(table.querySelector("[data-col-summary]")).toBeNull();
    expect(screen.queryByRole("list", { name: "Vai trò" })).toBeNull();
  });
});
