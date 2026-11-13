import { createProxyHandlers } from "@thi360/web-kit/proxy";
import vi from "@/messages/vi.json";

export const dynamic = "force-dynamic";

export const { GET, POST, PUT, PATCH, DELETE } = createProxyHandlers({
  messages: {
    notFound: vi.errors.notFound,
    proxyNotConfigured: vi.errors.proxyNotConfigured,
    upstreamTimeout: vi.errors.upstreamTimeout,
    upstreamUnavailable: vi.errors.upstreamUnavailable,
  },
});
