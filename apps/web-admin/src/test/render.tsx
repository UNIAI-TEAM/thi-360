import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { AbstractIntlMessages } from "next-intl";
import type { ReactElement, ReactNode } from "react";
import vi from "@/messages/vi.json";

/** Render trong bối cảnh next-intl (mặc định vi) và một QueryClient không thử lại. */
export function renderWithProviders(
  ui: ReactElement,
  {
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
    locale = "vi",
    messages = vi,
  }: { client?: QueryClient; locale?: string; messages?: AbstractIntlMessages } = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <NextIntlClientProvider locale={locale} messages={messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      </NextIntlClientProvider>
    );
  }
  return { ...render(ui, { wrapper: Wrapper }), client };
}
