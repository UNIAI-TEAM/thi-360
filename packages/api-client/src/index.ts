import createClient, { type Client, type ClientOptions } from "openapi-fetch";
import type { components, operations, paths } from "./schema";

export type { components, operations, paths };
export type ApiError = components["schemas"]["ApiError"];
export type FieldError = components["schemas"]["FieldError"];
export type PingResponse = components["schemas"]["PingResponse"];
export type ApiClient = Client<paths>;

const RelativeSafeRequest: typeof Request = class extends Request {
  private readonly _rawUrl?: string;

  constructor(input: RequestInfo | URL, init?: RequestInit) {
    if (typeof input === "string" && input.startsWith("/")) {
      const origin =
        typeof location !== "undefined" && location?.origin
          ? location.origin
          : "http://localhost";
      super(new URL(input, origin), init);
      if (typeof location === "undefined") {
        this._rawUrl = input;
      }
    } else {
      super(input, init);
    }
  }

  override get url(): string {
    return this._rawUrl ?? super.url;
  }
};

export function createApiClient(
  baseUrl = "",
  options?: Omit<ClientOptions, "baseUrl">,
): ApiClient {
  return createClient<paths>({
    Request: RelativeSafeRequest,
    ...options,
    baseUrl,
  });
}
