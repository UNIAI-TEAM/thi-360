"use client";

import { initialsOf } from "@thi360/web-kit/client";
import { ChevronDown, KeyRound, LogOut } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  cn,
  focusRing,
  notify,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { webUrl } from "@/lib/env";
import { logout } from "@/features/session/api";
import { useSession } from "@/features/session/session-provider";

export { initialsOf };

/**
 * Nút avatar ở thanh trên: Đổi mật khẩu, Đăng xuất. Không lặp mục điều hướng đã có ở menu bên (UNI-2877).
 */
export function UserMenu() {
  const t = useTranslations("shell.userMenu");
  const state = useSession();
  if (state.status !== "ready") return null;
  const { session } = state;

  async function onLogout() {
    try {
      await logout();
    } catch {
      notify.error(t("logoutFailed"));
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t("trigger")}
        className={cn(
          "relative flex h-10 items-center gap-2 rounded-full border border-border bg-surface py-0 pr-1 pl-1 text-label font-medium text-text-secondary transition-colors duration-150 hover:border-border-strong motion-reduce:transition-none sm:pr-2.5",
          // Màn cảm ứng: vùng bấm nới ra 44px bằng phần tử giả, không đổi kích thước nhìn thấy (DESIGN mục 8).
          "pointer-coarse:after:absolute pointer-coarse:after:-inset-0.5",
          focusRing,
        )}
      >
        <span
          aria-hidden
          className="flex size-7.5 items-center justify-center rounded-full bg-primary-50 text-caption font-semibold text-primary-700"
        >
          {initialsOf(session.fullName, session.username)}
        </span>
        {/* Dưới `sm` chỉ còn avatar để thanh trên vừa một hàng ở 360px; tên đăng nhập hiện ở đầu menu (như web học viên). */}
        <span className="hidden sm:inline">{session.username}</span>
        <ChevronDown aria-hidden className="hidden size-4 text-text-muted sm:block" />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuLabel className="sm:hidden">{t("signedInAs", { username: session.username })}</DropdownMenuLabel>
        <DropdownMenuItem asChild>
          {/* `source=account-menu`: trang Đổi mật khẩu của web học viên hiện lối quay lại cho người tự mở từ menu. */}
          <a href={`${webUrl()}/change-password?source=account-menu`}>
            <KeyRound aria-hidden />
            {t("changePassword")}
          </a>
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
