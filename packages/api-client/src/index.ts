import createClient, { type Client, type ClientOptions } from "openapi-fetch";
import type { components, operations, paths } from "./schema";

export type { components, operations, paths };
export type ApiError = components["schemas"]["ApiError"];
export type FieldError = components["schemas"]["FieldError"];
export type PingResponse = components["schemas"]["PingResponse"];
export type ApiClient = Client<paths>;

export function createApiClient(
  baseUrl = "/api",
  options?: Omit<ClientOptions, "baseUrl">,
): ApiClient {
  return createClient<paths>({
    ...options,
    baseUrl,
  });
}
