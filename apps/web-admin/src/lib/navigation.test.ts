import { Home } from "lucide-react";
import { describe, expect, it } from "vitest";
import {
  filterNavigation,
  NAVIGATION,
  type NavGroup,
  resolveBreadcrumb,
  shortcutItems,
} from "./navigation";

/** Cây giả: mỗi dạng quyền (null, một mã, một trong các mã) có ít nhất một mục. */
const TREE: readonly NavGroup[] = [
  {
    id: "overview",
    items: [{ id: "dashboard", href: "/", icon: Home, permission: null, excludeFromShortcuts: true }],
  },
  {
    id: "users",
    items: [
      { id: "accounts", href: "/users", icon: Home, permission: "USER.VIEW" },
      { id: "classes", href: "/classes", icon: Home, permission: "CLASS.VIEW" },
    ],
  },
  {
    id: "system",
    items: [
      { id: "rbac", href: "/rbac", icon: Home, permission: ["ROLE.VIEW", "PERMISSION.VIEW"] },
      { id: "appearance", href: "/giao-dien", icon: Home, permission: "APPEARANCE.MODIFY" },
    ],
  },
];

const ids = (groups: readonly NavGroup[]) => groups.map((g) => [g.id, g.items.map((i) => i.id)]);

describe("filterNavigation", () => {
  it("bộ quyền quản trị tài khoản: thấy Tài khoản, không thấy Lớp học và nhóm Hệ thống", () => {
    const granted = new Set(["USER.VIEW", "USER.MODIFY"]);

    expect(ids(filterNavigation(TREE, granted))).toEqual([
      ["overview", ["dashboard"]],
      ["users", ["accounts"]],
    ]);
  });

  it("bộ quyền hệ thống: mục có danh sách quyền hiện khi có một trong các mã", () => {
    const granted = new Set(["PERMISSION.VIEW", "APPEARANCE.MODIFY"]);

    expect(ids(filterNavigation(TREE, granted))).toEqual([
      ["overview", ["dashboard"]],
      ["system", ["rbac", "appearance"]],
    ]);
  });

  it("không có quyền nào: chỉ còn mục không đòi quyền", () => {
    expect(ids(filterNavigation(TREE, new Set()))).toEqual([["overview", ["dashboard"]]]);
  });

  it("danh sách quyền rỗng không mở cho ai", () => {
    const tree: NavGroup[] = [
      { id: "g", items: [{ id: "x", href: "/x", icon: Home, permission: [] }] },
    ];

    expect(filterNavigation(tree, new Set(["USER.VIEW"]))).toEqual([]);
  });

  it("không đổi cây gốc", () => {
    filterNavigation(TREE, new Set());

    expect(TREE[1]?.items).toHaveLength(2);
  });
});

describe("shortcutItems", () => {
  it("theo thứ tự menu, đã lọc quyền, bỏ Bảng điều khiển", () => {
    const granted = new Set(["USER.VIEW", "CLASS.VIEW", "ROLE.VIEW"]);

    expect(shortcutItems(TREE, granted).map((i) => i.id)).toEqual(["accounts", "classes", "rbac"]);
  });

  it("không quyền nào: không có lối tắt", () => {
    expect(shortcutItems(TREE, new Set())).toEqual([]);
  });
});

describe("resolveBreadcrumb", () => {
  it("khớp mục theo đường dẫn, kể cả trang con", () => {
    expect(resolveBreadcrumb(TREE, "/users")?.item.id).toBe("accounts");
    expect(resolveBreadcrumb(TREE, "/users/abc")?.group.id).toBe("users");
  });

  it("'/' chỉ khớp Bảng điều khiển, không khớp mọi đường dẫn", () => {
    expect(resolveBreadcrumb(TREE, "/")?.item.id).toBe("dashboard");
    expect(resolveBreadcrumb(TREE, "/khong-co")).toBeNull();
  });

  it("không khớp tiền tố chuỗi ('/users-x' không thuộc '/users')", () => {
    expect(resolveBreadcrumb(TREE, "/users-x")).toBeNull();
  });
});

describe("NAVIGATION của đợt 1", () => {
  it("có đủ bốn nhóm Tổng quan, Người dùng, Nội dung, Hệ thống và mục Cấu hình chung", () => {
    expect(NAVIGATION.map((g) => g.id)).toEqual(["overview", "users", "content", "system"]);
    const contentGroup = NAVIGATION.find((g) => g.id === "content");
    expect(contentGroup?.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "catalog",
          href: "/catalog",
          permission: "CATALOG.VIEW",
        }),
      ]),
    );
  });

  it("có mục Tùy chỉnh giao diện trong nhóm Hệ thống với quyền APPEARANCE.MODIFY", () => {
    const systemGroup = NAVIGATION.find((g) => g.id === "system");
    expect(systemGroup?.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "appearance",
          href: "/appearance",
          permission: "APPEARANCE.MODIFY",
        }),
      ]),
    );
  });

  it("có mục Phân quyền trong nhóm Hệ thống với quyền ROLE.VIEW hoặc PERMISSION.VIEW", () => {
    const systemGroup = NAVIGATION.find((g) => g.id === "system");
    expect(systemGroup?.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "rbac",
          href: "/rbac",
          permission: ["ROLE.VIEW", "PERMISSION.VIEW"],
        }),
      ]),
    );
  });

  it("có mục Tài khoản trong nhóm Người dùng với quyền USER.VIEW", () => {
    const usersGroup = NAVIGATION.find((g) => g.id === "users");
    expect(usersGroup?.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "accounts",
          href: "/users",
          permission: "USER.VIEW",
        }),
      ]),
    );
  });

  it("có mục Lớp học trong nhóm Người dùng với quyền CLASS.VIEW và breadcrumb khớp /classes/[id]", () => {
    const usersGroup = NAVIGATION.find((g) => g.id === "users");
    expect(usersGroup?.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "classes",
          href: "/classes",
          permission: "CLASS.VIEW",
        }),
      ]),
    );
    const trail = resolveBreadcrumb(NAVIGATION, "/classes/123");
    expect(trail?.group.id).toBe("users");
    expect(trail?.item.id).toBe("classes");
  });
});
