import { createProxyHandlers } from "@thi360/web-kit/proxy";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

export const dynamic = "force-dynamic";

export const { GET, POST, PUT, PATCH, DELETE } = createProxyHandlers({
  messages: {
    notFound: vi.errors.notFound,
    proxyNotConfigured: vi.errors.proxyNotConfigured,
    upstreamTimeout: vi.errors.upstreamTimeout,
    upstreamUnavailable: vi.errors.upstreamUnavailable,
  },
  messagesByLocale: {
    vi: {
      notFound: vi.errors.notFound,
      proxyNotConfigured: vi.errors.proxyNotConfigured,
      upstreamTimeout: vi.errors.upstreamTimeout,
      upstreamUnavailable: vi.errors.upstreamUnavailable,
    },
    en: {
      notFound: en.errors.notFound,
      proxyNotConfigured: en.errors.proxyNotConfigured,
      upstreamTimeout: en.errors.upstreamTimeout,
      upstreamUnavailable: en.errors.upstreamUnavailable,
    },
  },
});
