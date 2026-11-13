import createClient, { type Client, type ClientOptions } from "openapi-fetch";
import type { components, operations, paths } from "./schema";

export type { components, operations, paths };
export type ApiError = components["schemas"]["ApiError"];
export type FieldError = components["schemas"]["FieldError"];
export type PingResponse = components["schemas"]["PingResponse"];
export type ApiClient = Client<paths>;

function resolveBaseUrl(baseUrl?: string): string {
  if (baseUrl !== undefined) {
    return baseUrl;
  }
  const locationOrigin =
    typeof globalThis.location !== "undefined" ? globalThis.location?.origin : undefined;
  if (locationOrigin && locationOrigin !== "null") {
    return locationOrigin;
  }
  throw new Error(
    "createApiClient requires an explicit baseUrl origin when globalThis.location is unavailable",
  );
}

export function createApiClient(
  baseUrl?: string,
  options?: Omit<ClientOptions, "baseUrl">,
): ApiClient {
  return createClient<paths>({
    ...options,
    baseUrl: resolveBaseUrl(baseUrl),
  });
}
