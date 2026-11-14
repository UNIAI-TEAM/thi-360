import { afterEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  createApiClient,
  type ApiError,
  type FieldError,
  type PingResponse,
} from "./index";

function createPingMockFetch() {
  return vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({ status: "ok", time: "2026-11-13T10:00:00Z" }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      },
    ),
  );
}

describe("createApiClient", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("throws a clear error when baseUrl is omitted in Node without globalThis.location", () => {
    expect(() => createApiClient()).toThrow(/explicit baseUrl origin/i);
    expect(() => createApiClient(undefined, { fetch: createPingMockFetch() })).toThrow(
      /explicit baseUrl origin/i,
    );
  });

  it("resolves omitted baseUrl to globalThis.location.origin in browser-like environments", async () => {
    vi.stubGlobal("location", { origin: "https://app.thi360.example" });
    const mockFetch = createPingMockFetch();

    const client = createApiClient(undefined, { fetch: mockFetch });
    const { data, error, response } = await client.GET("/api/v1/ping");

    expect(error).toBeUndefined();
    expect(response.status).toBe(200);
    expect(data).toEqual({ status: "ok", time: "2026-11-13T10:00:00Z" });
    expect(mockFetch).toHaveBeenCalledTimes(1);

    const requestedRequest = mockFetch.mock.calls[0]?.[0] as Request;
    expect(requestedRequest.url).toBe("https://app.thi360.example/api/v1/ping");
    expect(new Request(requestedRequest).url).toBe("https://app.thi360.example/api/v1/ping");
    expect(requestedRequest.clone().url).toBe("https://app.thi360.example/api/v1/ping");
  });

  it("passes explicit baseUrl straight to openapi-fetch and preserves native Request copy and clone URLs", async () => {
    const mockFetch = createPingMockFetch();

    const client = createApiClient("http://localhost:8080", { fetch: mockFetch });
    const { data, error, response } = await client.GET("/api/v1/ping");

    expect(error).toBeUndefined();
    expect(response.status).toBe(200);
    expect(data).toEqual({ status: "ok", time: "2026-11-13T10:00:00Z" });
    expect(mockFetch).toHaveBeenCalledTimes(1);

    const requestedRequest = mockFetch.mock.calls[0]?.[0] as Request;
    expect(requestedRequest.url).toBe("http://localhost:8080/api/v1/ping");
    expect(new Request(requestedRequest).url).toBe("http://localhost:8080/api/v1/ping");
    expect(requestedRequest.clone().url).toBe("http://localhost:8080/api/v1/ping");
  });

  it("supports client.use({ onRequest }) returning a native Request copy or clone and reaches fetch", async () => {
    const copyFetch = createPingMockFetch();
    const copyClient = createApiClient("http://localhost:8080", { fetch: copyFetch });
    copyClient.use({
      onRequest({ request }) {
        const headers = new Headers(request.headers);
        headers.set("X-Middleware-Copy", "native-copy");
        return new Request(request, { headers });
      },
    });

    const copyResult = await copyClient.GET("/api/v1/ping");
    expect(copyResult.error).toBeUndefined();
    expect(copyFetch).toHaveBeenCalledTimes(1);
    const copiedRequest = copyFetch.mock.calls[0]?.[0] as Request;
    expect(copiedRequest.url).toBe("http://localhost:8080/api/v1/ping");
    expect(copiedRequest.headers.get("X-Middleware-Copy")).toBe("native-copy");

    const cloneFetch = createPingMockFetch();
    const cloneClient = createApiClient("http://localhost:8080", { fetch: cloneFetch });
    cloneClient.use({
      onRequest({ request }) {
        const cloned = request.clone();
        cloned.headers.set("X-Middleware-Clone", "native-clone");
        return cloned;
      },
    });

    const cloneResult = await cloneClient.GET("/api/v1/ping");
    expect(cloneResult.error).toBeUndefined();
    expect(cloneFetch).toHaveBeenCalledTimes(1);
    const clonedRequest = cloneFetch.mock.calls[0]?.[0] as Request;
    expect(clonedRequest.url).toBe("http://localhost:8080/api/v1/ping");
    expect(clonedRequest.headers.get("X-Middleware-Clone")).toBe("native-clone");
  });
  it("exposes non-optional required properties on generated response and error types", () => {
    expectTypeOf<PingResponse["status"]>().toEqualTypeOf<string>();
    expectTypeOf<PingResponse["time"]>().toEqualTypeOf<string>();
    expectTypeOf<ApiError["code"]>().toEqualTypeOf<string>();
    expectTypeOf<ApiError["message"]>().toEqualTypeOf<string>();
    expectTypeOf<ApiError["fieldErrors"]>().toEqualTypeOf<FieldError[]>();
    expectTypeOf<ApiError["traceId"]>().toEqualTypeOf<string>();
    expectTypeOf<FieldError["field"]>().toEqualTypeOf<string>();
    expectTypeOf<FieldError["code"]>().toEqualTypeOf<string>();
    expectTypeOf<FieldError["message"]>().toEqualTypeOf<string>();
  });
});
