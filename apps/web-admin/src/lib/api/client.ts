import { createApiClient } from "@thi360/api-client";
import { xsrfMiddleware } from "@thi360/web-kit/client";

export function createBrowserApiClient() {
  const client = createApiClient();
  client.use(xsrfMiddleware);
  return client;
}
