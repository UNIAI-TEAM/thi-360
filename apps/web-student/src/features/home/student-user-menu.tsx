"use client";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  cn,
  focusRing,
  notify,
  Skeleton,
} from "@thi360/ui";
import { initialsOf } from "@thi360/web-kit/client";
import { ChevronDown, KeyRound, LogOut, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { CHANGE_PASSWORD_MENU_SOURCE } from "@/features/auth/change-password-view";
import { logoutSession, type StudentSession } from "@/features/session/api";
import { adminUrl } from "@/lib/env";

export { initialsOf };

/**
 * Ô giữ chỗ cho nút tài khoản khi phiên đang tải: cùng chiều cao và gần cùng bề rộng với nút thật, để thanh trên
 * cùng không xuống hàng hay xê dịch khi phiên tải xong (CLS).
 */
export function StudentUserMenuPlaceholder() {
  return <Skeleton data-testid="user-menu-placeholder" className="h-11 w-12.5 rounded-full sm:w-37" />;
}

export function StudentUserMenu({
  session,
  navigate = (url: string) => window.location.assign(url),
}: {
  session: StudentSession;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("userMenu");
  const home = useTranslations("home");

  async function onLogout() {
    try {
      await logoutSession();
      // Đăng xuất ở web học viên về trang giới thiệu (TM 5.2); phiên đã hết nên trang là bản dành cho khách.
      navigate("/");
    } catch {
      notify.error(t("logoutFailed"));
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t("trigger")}
        className={cn(
          "flex h-11 min-w-12.5 items-center gap-2 rounded-full border border-border bg-surface py-0 pr-2.5 pl-1.5 text-label font-medium text-text-secondary transition-colors duration-150 hover:border-border-strong motion-reduce:transition-none",
          focusRing,
        )}
      >
        <span
          aria-hidden
          className="flex size-8 items-center justify-center rounded-full bg-primary-50 text-caption font-semibold text-primary-text"
        >
          {initialsOf(session.fullName, session.username)}
        </span>
        {/* Dưới `sm` chỉ còn avatar để thanh trên cùng vừa một hàng; tên đầy đủ có trong menu và nút đã có `aria-label`. */}
        <span className="hidden sm:inline">{session.username}</span>
        <ChevronDown aria-hidden className="hidden size-4 text-text-muted sm:block" />
      </DropdownMenuTrigger>
      <DropdownMenuContent aria-label={t("menuLabel")} className="w-60">
        <div className="mb-1.5 border-b border-border px-3 pt-2 pb-2.5">
          <div className="font-semibold text-text">{session.fullName.trim() || session.username}</div>
          <div className="text-caption text-text-muted">
            {session.access === "MANAGE" ? home("roleAdmin") : home("roleStudent")}
          </div>
        </div>
        {session.access === "MANAGE" && (
          <DropdownMenuItem asChild>
            <a href={adminUrl()}>
              <ShieldCheck aria-hidden />
              {t("adminPortal")}
            </a>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem asChild>
          <Link href={`/change-password?source=${CHANGE_PASSWORD_MENU_SOURCE}`}>
            <KeyRound aria-hidden />
            {t("changePassword")}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem destructive onSelect={() => void onLogout()}>
          <LogOut aria-hidden />
          {t("logout")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
