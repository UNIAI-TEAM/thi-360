"use client";

import { ChevronDown, GraduationCap, KeyRound, LogOut, ShieldCheck, UserRound } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  notify,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { webUrl } from "@/lib/env";
import { logout } from "@/features/session/api";
import { useSession } from "@/features/session/session-provider";
/** Hai chữ cái đầu của họ tên, hoặc của tên đăng nhập khi họ tên trống. */
export function initialsOf(fullName: string, username: string): string {
  const words = (fullName.trim() || username).split(/[\s._-]+/).filter(Boolean);
  const first = words[0]?.[0] ?? "";
  const last = words.length > 1 ? (words.at(-1)?.[0] ?? "") : (words[0]?.[1] ?? "");
  return (first + last).toLocaleUpperCase("vi");
}

/**
 * Nút avatar ở thanh trên: Trang quản trị (khi access = MANAGE), Trang học viên, Hồ sơ, Đổi mật khẩu, Đăng xuất.
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
        className="flex h-10 items-center gap-2 rounded-full border border-border bg-surface py-0 pr-2.5 pl-1 text-label font-medium text-text-secondary transition-colors duration-150 hover:border-border-strong focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none motion-reduce:transition-none"
      >
        <span
          aria-hidden
          className="flex size-7.5 items-center justify-center rounded-full bg-primary-50 text-caption font-semibold text-primary-700"
        >
          {initialsOf(session.fullName, session.username)}
        </span>
        <span>{session.username}</span>
        <ChevronDown aria-hidden className="size-4 text-text-muted" />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {session.access === "MANAGE" && (
          <DropdownMenuItem asChild>
            <Link href="/">
              <ShieldCheck aria-hidden />
              {t("adminPortal")}
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem asChild>
          <a href={`${webUrl()}/home`}>
            <GraduationCap aria-hidden />
            {t("studentPortal")}
          </a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a href={`${webUrl()}/ho-so`}>
            <UserRound aria-hidden />
            {t("profile")}
          </a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a href={`${webUrl()}/doi-mat-khau`}>
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
