"use client";

import { Button, cn, focusRing, LocaleSwitcher, ThemeToggle } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { withCallbackUrl } from "@/features/account/account-links";
import { StudentUserMenu, StudentUserMenuPlaceholder } from "@/features/home/student-user-menu";
import type { StudentSession } from "@/features/session/api";

/**
 * Tài khoản ở góc phải thanh đầu trang. `loading`: phiên đang tải (giữ chỗ cùng cỡ nút tài khoản); `guest`: khách
 * (Đăng nhập, Đăng ký); `user`: đã đăng nhập (mục điều hướng và menu tài khoản); `none`: không hiện gì (trang đòi
 * phiên mà tải phiên lỗi, thông báo lỗi nằm trong trang).
 */
export type HeaderAccount =
  | { kind: "loading" }
  | { kind: "guest" }
  | { kind: "none" }
  | { kind: "user"; session: StudentSession };

/** Mọi mục điều hướng của cả đợt 3 (plan B8): Bài thi của tôi, Lịch sử, Hồ sơ, Đơn của tôi. */
const NAV_ITEMS = [
  { key: "myExams", href: "/home" },
  { key: "history", href: "/history" },
  { key: "profile", href: "/account/profile" },
  { key: "orders", href: "/account/orders" },
] as const;

type NavKey = (typeof NAV_ITEMS)[number]["key"];

/** Mục trỏ tới trang chưa có thì ẩn ở đây: FS-3 bỏ "orders" khi làm `/account/orders`, FS-6 bỏ "history" khi làm `/history`. */
const HIDDEN_NAV_KEYS: readonly NavKey[] = ["history", "orders"];

const VISIBLE_NAV_ITEMS = NAV_ITEMS.filter((item) => !HIDDEN_NAV_KEYS.includes(item.key));

export function StudentHeader({ account }: { account: HeaderAccount }) {
  const common = useTranslations("common");
  const t = useTranslations("header");
  const pathname = usePathname();
  // Khách mở Đăng nhập, Đăng ký từ trang con (chi tiết bài thi) thì quay lại đúng trang đó; ở `/` về trang chủ theo vai trò.
  const here = pathname && pathname !== "/" ? pathname : null;

  return (
    <header className="border-b border-border bg-surface">
      {/* Đệm ngang 16px dưới `sm` để logo, hai nút tùy chọn và nút tài khoản vừa một hàng từ 320px (không xuống hàng khi phiên tải xong). */}
      <div className="mx-auto flex max-w-[1170px] flex-wrap items-center gap-x-6 gap-y-1 px-4 py-3 sm:px-6">
        <Link href="/" className="flex items-center gap-3">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
          >
            {common("logoMark")}
          </span>
          <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
        </Link>

        {/* Điều hướng là các trang của học viên: tài khoản quản trị chỉ có menu tài khoản, không có mục dẫn tới trang bị chặn. */}
        {account.kind === "user" && account.session.access === "EXAM" && (
          <nav aria-label={t("nav.label")} className="order-last basis-full md:order-none md:basis-auto md:flex-1">
            <ul className="flex flex-wrap gap-1">
              {VISIBLE_NAV_ITEMS.map((item) => {
                const active = pathname === item.href || pathname?.startsWith(`${item.href}/`);
                return (
                  <li key={item.key}>
                    <Link
                      href={item.href}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "flex min-h-11 items-center rounded-md border-b-2 px-3 text-label font-medium transition-colors duration-150 motion-reduce:transition-none",
                        active
                          ? "border-primary-text text-primary-text"
                          : "border-transparent text-text-secondary hover:text-text",
                        focusRing,
                      )}
                    >
                      {t(`nav.${item.key}`)}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
        )}

        <div className="ml-auto flex items-center gap-3">
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>
          {account.kind === "loading" && <StudentUserMenuPlaceholder />}
          {account.kind === "user" && <StudentUserMenu session={account.session} />}
          {account.kind === "guest" && (
            <div className="flex items-center gap-2">
              <Button asChild variant="secondary" className="h-11">
                <Link href={withCallbackUrl("/login", here)}>{t("login")}</Link>
              </Button>
              <Button asChild className="h-11">
                <Link href={withCallbackUrl("/register", here)}>{t("register")}</Link>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
