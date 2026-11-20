import {
  ClipboardCheck,
  ClipboardList,
  FileQuestion,
  GraduationCap,
  LayoutDashboard,
  List,
  Package,
  Receipt,
  Shield,
  SlidersVertical,
  Tag,
  Timer,
  Users,
  type LucideIcon,
} from "lucide-react";
import { hasPermission, type PermissionRequirement } from "@/features/session/permissions";
import { isGradingMockEnabled } from "@/lib/mock/flags";

/**
 * Menu chính của web admin, nguồn duy nhất cho sidebar, breadcrumb và lối tắt ở Bảng điều khiển.
 *
 * Chữ hiển thị nằm trong `messages/vi.json` và `messages/en.json`: nhóm `nav.groups.<id>`,
 * mục `nav.items.<id>.label`, và `nav.items.<id>.description` cho mục hiện thành lối tắt.
 *
 * Mỗi task thêm đúng mục của mình vào nhóm (Người dùng: T09 Tài khoản, T10 Lớp học;
 * Nội dung: đợt 2 Ngân hàng câu hỏi, Bài thi, T15 Cấu hình chung; Hệ thống: T08 Phân quyền, T17 Tùy chỉnh giao diện).
 * Ẩn mục theo quyền chỉ để tiện dùng; backend mới là nơi chặn.
 */

export interface NavItem {
  id: string;
  href: string;
  icon: LucideIcon;
  /** Mã quyền hoặc danh sách "có một trong các mã". `null`: mọi người dùng trang quản trị đều thấy. */
  permission: PermissionRequirement | null;
  /** Đặt `true` cho mục không hiện thành thẻ lối tắt (chính trang Bảng điều khiển). */
  excludeFromShortcuts?: boolean;
  /**
   * Màn chạy được trên dữ liệu mẫu: hiện khi người dùng có `permission`, hoặc khi cờ
   * `NEXT_PUBLIC_THI360_GRADING_MOCK` bật (chỉ còn `/grading` sau QA UNI-2621).
   */
  mockFlag?: "grading";
}

export interface NavGroup {
  id: string;
  items: readonly NavItem[];
}

export const NAVIGATION: readonly NavGroup[] = [
  {
    id: "overview",
    items: [
      {
        id: "dashboard",
        href: "/",
        icon: LayoutDashboard,
        permission: null,
        excludeFromShortcuts: true,
      },
    ],
  },
  {
    id: "users",
    items: [
      {
        id: "accounts",
        href: "/users",
        icon: Users,
        permission: "USER.VIEW",
      },
      {
        id: "classes",
        href: "/classes",
        icon: GraduationCap,
        permission: "CLASS.VIEW",
      },
    ],
  },
  {
    id: "content",
    items: [
      {
        id: "questions",
        href: "/questions",
        icon: FileQuestion,
        permission: "QUESTION.VIEW",
      },
      {
        id: "exams",
        href: "/exams",
        icon: ClipboardList,
        permission: "EXAM.VIEW",
      },
      {
        id: "grading",
        href: "/grading",
        icon: ClipboardCheck,
        permission: "GRADING.VIEW",
        mockFlag: "grading",
      },
      {
        id: "catalog",
        href: "/catalog",
        icon: List,
        permission: "CATALOG.VIEW",
      },
    ],
  },
  {
    id: "commerce",
    items: [
      {
        id: "promotions",
        href: "/promotions",
        icon: Tag,
        permission: "PROMOTION.VIEW",
      },
      {
        id: "orders",
        href: "/orders",
        icon: Receipt,
        permission: "ORDER.VIEW",
      },
      {
        id: "bundles",
        href: "/bundles",
        icon: Package,
        permission: "EXAM.VIEW",
      },
    ],
  },
  {
    id: "learners",
    items: [
      {
        id: "attempts",
        href: "/attempts",
        icon: Timer,
        permission: "ATTEMPT.VIEW",
      },
    ],
  },
  {
    id: "system",
    items: [
      {
        id: "rbac",
        href: "/rbac",
        icon: Shield,
        permission: ["ROLE.VIEW", "PERMISSION.VIEW"],
      },
      {
        id: "appearance",
        href: "/appearance",
        icon: SlidersVertical,
        permission: "APPEARANCE.MODIFY",
      },
    ],
  },
];

/**
 * Mục đã có trong menu nhưng trang chưa có: ẩn ở sidebar và lối tắt ở Bảng điều khiển dù người dùng đủ quyền
 * (plan B8). Lane làm trang đó bỏ dòng của mình: Khuyến mãi (FA-2), Đơn hàng (FA-3), Lượt thi (FA-4), Bộ đề (FB).
 */
export const HIDDEN_NAV_ITEMS: readonly string[] = ["promotions", "orders", "bundles", "attempts"];

export interface MockFlags {
  readonly grading: boolean;
}

export function currentMockFlags(): MockFlags {
  return { grading: isGradingMockEnabled() };
}

/** Hiện khi cờ dữ liệu mẫu của mục bật, hoặc người dùng có quyền (mục không có quyền: mọi người dùng thấy). */
function isVisible(item: NavItem, granted: ReadonlySet<string>, flags: MockFlags): boolean {
  if (item.mockFlag && flags[item.mockFlag]) return true;
  return item.permission === null || hasPermission(granted, item.permission);
}

/**
 * Giữ các mục người dùng có quyền và không nằm trong danh sách ẩn; nhóm không còn mục nào thì bỏ. `hiddenItems`
 * mặc định là `HIDDEN_NAV_ITEMS`.
 */
export function filterNavigation(
  groups: readonly NavGroup[],
  granted: ReadonlySet<string>,
  mockFlags: MockFlags = currentMockFlags(),
  hiddenItems: readonly string[] = HIDDEN_NAV_ITEMS,
): NavGroup[] {
  return groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !hiddenItems.includes(item.id) && isVisible(item, granted, mockFlags)),
    }))
    .filter((group) => group.items.length > 0);
}

/** Các mục hiện thành thẻ lối tắt, đã lọc theo quyền, giữ thứ tự menu. */
export function shortcutItems(groups: readonly NavGroup[], granted: ReadonlySet<string>): NavItem[] {
  return filterNavigation(groups, granted)
    .flatMap((group) => group.items)
    .filter((item) => !item.excludeFromShortcuts);
}

export function isActivePath(item: NavItem, pathname: string): boolean {
  if (item.href === "/") return pathname === "/";
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
}

export interface BreadcrumbTrail {
  group: NavGroup;
  item: NavItem;
}

/** Nhóm và mục khớp đường dẫn (mục có `href` dài nhất thắng); `null` nếu không thuộc menu. */
export function resolveBreadcrumb(
  groups: readonly NavGroup[],
  pathname: string,
): BreadcrumbTrail | null {
  let best: BreadcrumbTrail | null = null;
  for (const group of groups) {
    for (const item of group.items) {
      if (!isActivePath(item, pathname)) continue;
      if (!best || item.href.length > best.item.href.length) best = { group, item };
    }
  }
  return best;
}
