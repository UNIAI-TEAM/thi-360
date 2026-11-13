"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { notify, Toaster } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import { refreshSession } from "@/features/session/api";
import { redirectToLogin } from "@/lib/auth-redirect";
import { createQueryClient, getErrorStatus } from "@/lib/api/query-client";

export const FORBIDDEN_PATH = "/forbidden";
const REFRESH_RETRY_KEY = "thi360:403-refresh-url";

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

function readRetryMarker(): string | null {
  try {
    return window.sessionStorage.getItem(REFRESH_RETRY_KEY);
  } catch {
    return null;
  }
}

function writeRetryMarker(value: string | null): void {
  try {
    if (value === null) {
      window.sessionStorage.removeItem(REFRESH_RETRY_KEY);
    } else {
      window.sessionStorage.setItem(REFRESH_RETRY_KEY, value);
    }
  } catch {
    // ignore storage failures
  }
}

/**
 * TanStack Query kèm xử lý lỗi toàn cục:
 * - 401 → trang đăng nhập
 * - 403 → làm mới `/auth/refresh` một lần rồi điều hướng lại; nếu vẫn 403 thì toast rồi `/forbidden`.
 */
export function QueryProvider({
  children,
  navigate = (url: string) => window.location.assign(url),
}: {
  children: ReactNode;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("errors");
  const router = useRouter();
  const pathname = usePathname();

  const [guard] = useState(() => createForbiddenGuard(pathname));
  useEffect(() => {
    guard.arrive(pathname);
    const marker = readRetryMarker();
    if (marker && typeof window !== "undefined" && marker !== window.location.href) {
      writeRetryMarker(null);
    }
  }, [guard, pathname]);

  const [client] = useState(() => {
    const forbiddenMessage = t("forbiddenToast");
    let redirecting = false;
    const onUnauthenticated = () => {
      if (redirecting) return;
      redirecting = true;
      redirectToLogin();
    };

    return createQueryClient({
      onUnauthenticated,
      onForbidden: () => {
        if (!guard.claim()) return;
        const currentHref = typeof window !== "undefined" ? window.location.href : pathname;
        const alreadyRetried = readRetryMarker() === currentHref;
        if (alreadyRetried) {
          writeRetryMarker(null);
          notify.error(forbiddenMessage);
          router.replace(FORBIDDEN_PATH);
          return;
        }

        writeRetryMarker(currentHref);
        void refreshSession()
          .then(() => {
            navigate(currentHref);
          })
          .catch((error: unknown) => {
            writeRetryMarker(null);
            if (getErrorStatus(error) === 401) {
              onUnauthenticated();
              return;
            }
            notify.error(forbiddenMessage);
            router.replace(FORBIDDEN_PATH);
          });
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
