"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { notify, Toaster } from "@thi360/ui";
import {
  clearStaleUnauthorizedMarker,
  createRouteGuard,
  createUnauthorizedRecovery,
  navigateOrReload,
} from "@thi360/web-kit/client";
import { buildLoginUrl } from "@thi360/web-kit/redirect";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { clearSessionBestEffort, refreshSession } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { webUrl } from "@/lib/env";

export const FORBIDDEN_PATH = "/forbidden";
export const LOGIN_PATH = "/dang-nhap";

function getErrorStatus(error: unknown): number | null {
  if (error instanceof ApiStatusError) return error.status;
  if (typeof error === "object" && error !== null && "status" in error && typeof error.status === "number") {
    return error.status;
  }
  return null;
}

function shouldRetry(failureCount: number, error: unknown): boolean {
  const status = getErrorStatus(error);
  if (status !== null && status >= 400 && status < 500) return false;
  return failureCount < 2;
}

export function QueryProvider({
  children,
  navigate = navigateOrReload,
}: {
  children: ReactNode;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("errors");
  const commonT = useTranslations("common");
  const router = useRouter();
  const pathname = usePathname();

  const [guard] = useState(() =>
    createRouteGuard(pathname, { forbiddenPath: FORBIDDEN_PATH, ignoredPaths: [LOGIN_PATH] }),
  );
  useEffect(() => {
    guard.arrive(pathname);
    if (typeof window !== "undefined") {
      clearStaleUnauthorizedMarker(window.location.href);
    }
  }, [guard, pathname]);

  const forbiddenMessage = t("forbiddenToast");
  const forbiddenMessageRef = useRef(forbiddenMessage);
  useEffect(() => {
    forbiddenMessageRef.current = forbiddenMessage;
  }, [forbiddenMessage]);

  const [client] = useState(() => {
    const recovery = createUnauthorizedRecovery({
      refreshSession,
      clearSessionBestEffort,
      getErrorStatus,
      onReload: (url) => navigate(url),
      onRedirectLogin: (currentHref) => navigate(buildLoginUrl(webUrl(), currentHref)),
    });

    const handleError = (error: unknown) => {
      const status = getErrorStatus(error);
      if (status === 401 && guard.pathname() !== LOGIN_PATH) {
        void recovery.handleUnauthorized();
      } else if (status === 403 && guard.claimForbidden()) {
        notify.error(forbiddenMessageRef.current);
        router.replace(FORBIDDEN_PATH);
      }
    };
    return new QueryClient({
      queryCache: new QueryCache({ onError: handleError }),
      mutationCache: new MutationCache({ onError: handleError }),
      defaultOptions: {
        queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      },
    });
  });

  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster
        containerAriaLabel={commonT("notificationsLabel")}
        closeButtonAriaLabel={commonT("closeToast")}
      />
    </QueryClientProvider>
  );
}
