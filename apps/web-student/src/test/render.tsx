import { UiStringsProvider } from "@thi360/ui";
import { APP_TIME_ZONE, INTL_FORMATS, normalizeUiLocale, resolveIntlLocale } from "@thi360/web-kit/preferences";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { AbstractIntlMessages } from "next-intl";
import type { ReactElement, ReactNode } from "react";
import vi from "@/messages/vi.json";

export function renderWithProviders(
  ui: ReactElement,
  {
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
    locale = "vi",
    messages = vi,
  }: { client?: QueryClient; locale?: string; messages?: AbstractIntlMessages } = {},
) {
  const uiLocale = normalizeUiLocale(locale);
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <NextIntlClientProvider
        locale={resolveIntlLocale(uiLocale)}
        messages={messages}
        timeZone={APP_TIME_ZONE}
        formats={INTL_FORMATS}
      >
        <UiStringsProvider locale={uiLocale}>
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>
    );
  }
  return { ...render(ui, { wrapper: Wrapper }), client };
}
