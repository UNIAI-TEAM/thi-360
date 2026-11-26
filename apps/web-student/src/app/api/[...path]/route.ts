import { createProxyHandlers } from "@thi360/web-kit/proxy";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

export const dynamic = "force-dynamic";

export const { GET, POST, PUT, PATCH, DELETE } = createProxyHandlers({
  // App học viên native gọi API qua tên miền này bằng `Authorization: Bearer` (UNI-2816).
  forwardBearerAuthorization: true,
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
