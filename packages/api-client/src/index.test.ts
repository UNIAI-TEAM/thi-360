import { describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  createApiClient,
  type ApiError,
  type FieldError,
  type PingResponse,
} from "./index";
describe("createApiClient", () => {
  it("uses root default baseUrl so /api/v1/ping is not prefixed with a duplicate /api", async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ status: "ok", time: "2026-11-13T10:00:00Z" }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const client = createApiClient(undefined, { fetch: mockFetch });
    const { data, error, response } = await client.GET("/api/v1/ping");

    expect(error).toBeUndefined();
    expect(response.status).toBe(200);
    expect(data).toEqual({ status: "ok", time: "2026-11-13T10:00:00Z" });
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const requestedRequest = mockFetch.mock.calls[0]?.[0] as Request;
    expect(requestedRequest.url).toBe("/api/v1/ping");
  });

  it("requests /api/v1/ping against an explicit baseUrl and returns typed PingResponse", async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ status: "ok", time: "2026-11-13T10:00:00Z" }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const client = createApiClient("http://localhost:8080", { fetch: mockFetch });
    const { data, error, response } = await client.GET("/api/v1/ping");

    expect(error).toBeUndefined();
    expect(response.status).toBe(200);
    expect(data).toEqual({ status: "ok", time: "2026-11-13T10:00:00Z" });
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const requestedRequest = mockFetch.mock.calls[0]?.[0] as Request;
    expect(requestedRequest.url).toBe("http://localhost:8080/api/v1/ping");
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
