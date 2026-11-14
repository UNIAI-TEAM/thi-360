"use client";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  notify,
} from "@thi360/ui";
import { initialsOf } from "@thi360/web-kit/client";
import { ChevronDown, KeyRound, LayoutDashboard, LogOut, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { logoutSession, type StudentSession } from "@/features/session/api";
import { adminUrl } from "@/lib/env";

export { initialsOf };


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
      navigate("/dang-nhap");
    } catch {
      notify.error(t("logoutFailed"));
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t("trigger")}
        className="flex h-11 items-center gap-2 rounded-full border border-border bg-surface py-0 pr-2.5 pl-1.5 text-label font-medium text-text-secondary transition-colors duration-150 hover:border-border-strong focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none motion-reduce:transition-none"
      >
        <span
          aria-hidden
          className="flex size-8 items-center justify-center rounded-full bg-primary-50 text-caption font-semibold text-primary-text"
        >
          {initialsOf(session.fullName, session.username)}
        </span>
        <span>{session.username}</span>
        <ChevronDown aria-hidden className="size-4 text-text-muted" />
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
          <Link href="/home">
            <LayoutDashboard aria-hidden />
            {t("studentPortal")}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/doi-mat-khau">
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
