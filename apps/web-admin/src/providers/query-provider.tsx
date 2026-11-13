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
 * Chặn trùng xử lý 403: `claim()` chỉ trả `true` một lần cho tới khi `pathname` đổi, và không bao giờ
 * khi đang ở `/forbidden`. `pathname` chỉ đổi sau khi chuyển trang xong nên không tự nó chặn được
 * đợt 403 đồng thời của nhiều query.
 */
function createForbiddenGuard(initialPathname: string) {
  let pathname = initialPathname;
  let handled = false;
  return {
    arrive(next: string) {
      pathname = next;
      handled = false;
    },
    claim(): boolean {
      if (handled || pathname === FORBIDDEN_PATH) return false;
      handled = true;
      return true;
    },
  };
}

/**
 * TanStack Query kèm xử lý lỗi toàn cục: 401 → trang đăng nhập, 403 → toast rồi `/forbidden`.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("errors");
  const router = useRouter();
  const pathname = usePathname();

  // Handler dựng một lần ở lần render đầu và dùng được ngay, không phải chờ effect. `pathname` đổi
  // theo trang nên đi qua `guard`, được cập nhật trong effect.
  const [guard] = useState(() => createForbiddenGuard(pathname));
  useEffect(() => guard.arrive(pathname), [guard, pathname]);

  const [client] = useState(() => {
    const forbiddenMessage = t("forbiddenToast");
    let redirecting = false;
    return createQueryClient({
      onUnauthenticated: () => {
        if (redirecting) return;
        redirecting = true;
        redirectToLogin();
      },
      onForbidden: () => {
        if (!guard.claim()) return;
        notify.error(forbiddenMessage);
        router.replace(FORBIDDEN_PATH);
      },
    });
  });

  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
