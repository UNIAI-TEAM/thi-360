import { Home } from "lucide-react";
import { describe, expect, it } from "vitest";
import {
  filterNavigation,
  HIDDEN_NAV_ITEMS,
  isActivePath,
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
  it("có đủ sáu nhóm Tổng quan, Người dùng, Nội dung, Thương mại, Học viên, Hệ thống và mục Cấu hình chung", () => {
    expect(NAVIGATION.map((g) => g.id)).toEqual(["overview", "users", "content", "commerce", "learners", "system"]);
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

  it("bốn trang con của Phân quyền vẫn thuộc mục Phân quyền: sidebar sáng và breadcrumb đúng", () => {
    const rbacItem = NAVIGATION.flatMap((g) => g.items).find((item) => item.id === "rbac")!;
    for (const path of ["/rbac", "/rbac/roles", "/rbac/permissions", "/rbac/users", "/rbac/bulk-assign"]) {
      expect(isActivePath(rbacItem, path), path).toBe(true);
      const trail = resolveBreadcrumb(NAVIGATION, path);
      expect(trail?.group.id, path).toBe("system");
      expect(trail?.item.id, path).toBe("rbac");
    }
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

describe("mục dữ liệu mẫu của đợt 2", () => {
  const contentIds = (granted: string[], flags: { grading: boolean }) =>
    filterNavigation(NAVIGATION, new Set(granted), flags)
      .find((g) => g.id === "content")
      ?.items.map((item) => item.id);

  it("Ngân hàng câu hỏi, Bài thi và Chấm tự luận đứng đầu nhóm Nội dung", () => {
    const contentGroup = NAVIGATION.find((g) => g.id === "content");
    expect(contentGroup?.items.map((item) => item.id)).toEqual(["questions", "exams", "grading", "catalog"]);
    // Màn chấm một lượt thi vẫn thuộc mục Chấm tự luận.
    expect(resolveBreadcrumb(NAVIGATION, "/grading/att-bca-001")?.item.id).toBe("grading");
  });

  it("chỉ /grading còn hiện theo cờ dữ liệu mẫu; các mục khác theo quyền (QA UNI-2621)", () => {
    const none = { grading: false };
    expect(contentIds(["CATALOG.VIEW"], none)).toEqual(["catalog"]);
    expect(contentIds(["CATALOG.VIEW"], { grading: true })).toEqual(["grading", "catalog"]);
    expect(contentIds(["QUESTION.VIEW", "EXAM.VIEW", "GRADING.VIEW"], none)).toEqual(["questions", "exams", "grading"]);
    expect(contentIds(["EXAM.VIEW"], none)).toEqual(["exams"]);
    expect(contentIds(["QUESTION.VIEW"], { grading: true })).toEqual(["questions", "grading"]);
  });
});

describe("menu của đợt 3 (FA-1)", () => {
  const none = { grading: false };
  /** Bỏ danh sách ẩn để kiểm quyền của từng mục; danh sách ẩn có ca riêng ở dưới. */
  const visible = (granted: string[]) => filterNavigation(NAVIGATION, new Set(granted), none, []);
  const idsOf = (groups: readonly NavGroup[], groupId: string) =>
    groups.find((group) => group.id === groupId)?.items.map((item) => item.id);

  it("menu thương mại theo quyền: mỗi mục chỉ hiện khi có đúng quyền của mục, kể cả vai trò tùy chỉnh", () => {
    const commerce = NAVIGATION.find((group) => group.id === "commerce");
    expect(commerce?.items.map((item) => [item.id, item.permission])).toEqual([
      ["promotions", "PROMOTION.VIEW"],
      ["orders", "ORDER.VIEW"],
      ["bundles", "EXAM.VIEW"],
    ]);

    expect(idsOf(visible(["PROMOTION.VIEW"]), "commerce")).toEqual(["promotions"]);
    expect(idsOf(visible(["ORDER.VIEW"]), "commerce")).toEqual(["orders"]);
    expect(idsOf(visible(["EXAM.VIEW"]), "commerce")).toEqual(["bundles"]);
    expect(idsOf(visible(["PROMOTION.VIEW", "EXAM.VIEW"]), "commerce")).toEqual(["promotions", "bundles"]);
    // Quyền sửa không thay quyền xem; quyền của mục khác không mở mục này.
    expect(idsOf(visible(["PROMOTION.MODIFY", "ORDER.REFUND", "USER.VIEW"]), "commerce")).toBeUndefined();
    expect(idsOf(visible([]), "commerce")).toBeUndefined();
  });

  it("nhóm Học viên: chứa mục Lượt thi (ATTEMPT.VIEW), ẩn khi vai trò không có quyền đó", () => {
    const learners = NAVIGATION.find((group) => group.id === "learners");
    expect(learners?.items).toEqual([
      expect.objectContaining({ id: "attempts", href: "/attempts", permission: "ATTEMPT.VIEW" }),
    ]);

    expect(idsOf(visible(["ATTEMPT.VIEW"]), "learners")).toEqual(["attempts"]);
    expect(idsOf(visible(["ATTEMPT.MODIFY", "GRADING.VIEW", "USER.VIEW"]), "learners")).toBeUndefined();
    // Lượt thi không còn nằm trong nhóm Người dùng.
    expect(idsOf(visible(["ATTEMPT.VIEW", "USER.VIEW"]), "users")).toEqual(["accounts"]);
    expect(resolveBreadcrumb(NAVIGATION, "/attempts/abc")?.group.id).toBe("learners");
  });

  it("danh sách ẩn: orders, bundles, attempts không hiện ở sidebar và lối tắt dù đủ quyền; nhóm hết mục thì bỏ", () => {
    expect([...HIDDEN_NAV_ITEMS].sort()).toEqual(["attempts", "bundles", "orders"]);

    const everything = new Set(["PROMOTION.VIEW", "ORDER.VIEW", "EXAM.VIEW", "ATTEMPT.VIEW", "USER.VIEW"]);
    const groups = filterNavigation(NAVIGATION, everything, none);
    const shown = groups.flatMap((group) => group.items.map((item) => item.id));
    for (const id of HIDDEN_NAV_ITEMS) expect(shown).not.toContain(id);
    expect(shown).toContain("promotions");
    expect(idsOf(groups, "commerce")).toEqual(["promotions"]);
    expect(groups.map((group) => group.id)).not.toContain("learners");
    // Mục khác trong cùng bộ quyền vẫn hiện: Bài thi (EXAM.VIEW) ở nhóm Nội dung.
    expect(idsOf(groups, "content")).toEqual(["exams"]);

    const shortcuts = shortcutItems(NAVIGATION, everything).map((item) => item.id);
    for (const id of HIDDEN_NAV_ITEMS) expect(shortcuts).not.toContain(id);
    expect(shortcuts).toContain("promotions");
    expect(shortcuts).toContain("exams");
  });

  it("mục khuyến mãi hiện ở sidebar và lối tắt khi có PROMOTION.VIEW", () => {
    const granted = new Set(["PROMOTION.VIEW"]);
    const groups = filterNavigation(NAVIGATION, granted, none);
    expect(idsOf(groups, "commerce")).toEqual(["promotions"]);
    expect(shortcutItems(NAVIGATION, granted).map((item) => item.id)).toContain("promotions");
  });

  it("bỏ một dòng ẩn thì mục đó hiện lại, các mục còn ẩn vẫn ẩn; nhóm hiện khi còn một mục", () => {
    const granted = new Set(["PROMOTION.VIEW", "ORDER.VIEW"]);
    const groups = filterNavigation(NAVIGATION, granted, none, ["bundles", "attempts"]);
    expect(idsOf(groups, "commerce")).toEqual(["promotions", "orders"]);
  });
});
