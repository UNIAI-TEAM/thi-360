"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { notify, Toaster } from "@thi360/ui";
import { buildLoginUrl } from "@thi360/web-kit/redirect";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useState } from "react";
import { ApiStatusError } from "@/lib/api/client";
import { webUrl } from "@/lib/env";

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

export function QueryProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("errors");
  const router = useRouter();
  const pathname = usePathname();

  const [client] = useState(() => {
    const forbiddenMessage = t("forbiddenToast");
    let redirecting = false;
    const handleError = (error: unknown) => {
      const status = getErrorStatus(error);
      if (status === 401 && pathname !== "/dang-nhap") {
        if (redirecting) return;
        redirecting = true;
        window.location.assign(buildLoginUrl(webUrl(), window.location.href));
      } else if (status === 403 && pathname !== "/dang-nhap" && pathname !== "/forbidden") {
        notify.error(forbiddenMessage);
        router.replace("/forbidden");
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
      <Toaster />
    </QueryClientProvider>
  );
}
