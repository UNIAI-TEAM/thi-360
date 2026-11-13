"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { notify, Toaster } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import { redirectToLogin } from "@/lib/auth-redirect";
import { createQueryClient } from "@/lib/api/query-client";

export const FORBIDDEN_PATH = "/forbidden";

/**
 * TanStack Query kèm xử lý lỗi toàn cục: 401 → trang đăng nhập, 403 → toast rồi `/forbidden`.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("errors");
  const router = useRouter();
  const pathname = usePathname();

  const [managed] = useState(() => {
    let redirecting = false;
    return createQueryClient({
      onUnauthenticated: () => {
        if (redirecting) return;
        redirecting = true;
        redirectToLogin();
      },
      onForbidden: () => {},
    });
  });

  useEffect(() => {
    managed.setHandlers({
      onForbidden: () => {
        if (pathname === FORBIDDEN_PATH) return;
        notify.error(t("forbiddenToast"));
        router.replace(FORBIDDEN_PATH);
      },
    });
  }, [managed, pathname, router, t]);

  return (
    <QueryClientProvider client={managed.client}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
