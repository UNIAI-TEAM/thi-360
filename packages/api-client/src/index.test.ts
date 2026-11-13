import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./index";

describe("createApiClient", () => {
  it("requests /api/v1/ping against the configured baseUrl and returns typed PingResponse", async () => {
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
});
