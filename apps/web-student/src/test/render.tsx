import { UiStringsProvider } from "@thi360/ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactElement, ReactNode } from "react";
import vi from "@/messages/vi.json";

export function renderWithProviders(
  ui: ReactElement,
  { client = new QueryClient({ defaultOptions: { queries: { retry: false } } }) }: { client?: QueryClient } = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <NextIntlClientProvider locale="vi" messages={vi} timeZone="Asia/Ho_Chi_Minh">
        <UiStringsProvider locale="vi">
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>
    );
  }
  return { ...render(ui, { wrapper: Wrapper }), client };
}
