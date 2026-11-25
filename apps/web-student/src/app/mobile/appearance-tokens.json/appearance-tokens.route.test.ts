// @vitest-environment node
import { readFileSync } from "node:fs";
import { deriveTokens, PRESET_PALETTES, toMobilePalette } from "@thi360/ui";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

const API = "http://api.internal:8080";
const fetchMock = vi.fn<typeof fetch>();

function buildRequest(path = "/mobile/appearance-tokens.json", headers?: HeadersInit) {
  return new NextRequest(`http://localhost:3000${path}`, headers ? { headers } : undefined);
}

beforeEach(() => {
  vi.stubEnv("THI360_API_URL", API);
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("GET /mobile/appearance-tokens.json", () => {
  it("trả 200 khớp hợp đồng mặc định thi360-green kèm ETag và Cache-Control: no-cache", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({
        version: 0,
        mode: "light",
        palette: { presetId: "thi360-green" },
      }),
    );

    const response = await GET(buildRequest());

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("etag")).toBe('"0"');
    expect(response.headers.get("cache-control")).toBe("no-cache");

    const expectedContract = JSON.parse(
      readFileSync("../../contracts/mobile/appearance/tokens-thi360-green.json", "utf8"),
    );
    expect(await response.json()).toEqual(expectedContract);
  });

  it("suy ra đúng bảng màu sáng/tối cho bộ phối màu có sẵn và cấu hình custom", async () => {
    const navyPreset = PRESET_PALETTES.find((preset) => preset.id === "navy-teal")!;
    fetchMock.mockResolvedValueOnce(
      Response.json({
        version: 4,
        mode: "dark",
        palette: { presetId: "navy-teal" },
      }),
    );

    const presetResponse = await GET(buildRequest());
    expect(presetResponse.status).toBe(200);
    expect(presetResponse.headers.get("etag")).toBe('"4"');
    expect(await presetResponse.json()).toEqual({
      version: 4,
      mode: "dark",
      presetId: "navy-teal",
      light: toMobilePalette(
        deriveTokens({ primary: navyPreset.primary, accent: navyPreset.accent, mode: "light" }),
      ),
      dark: toMobilePalette(
        deriveTokens({ primary: navyPreset.primary, accent: navyPreset.accent, mode: "dark" }),
      ),
    });

    fetchMock.mockResolvedValueOnce(
      Response.json({
        version: 7,
        mode: "light",
        palette: {
          presetId: "custom",
          custom: { primary: "#1D4ED8", accent: "#D97706" },
        },
      }),
    );

    const customResponse = await GET(buildRequest());
    expect(customResponse.status).toBe(200);
    expect(customResponse.headers.get("etag")).toBe('"7"');
    expect(await customResponse.json()).toEqual({
      version: 7,
      mode: "light",
      presetId: "custom",
      light: toMobilePalette(deriveTokens({ primary: "#1d4ed8", accent: "#d97706", mode: "light" })),
      dark: toMobilePalette(deriveTokens({ primary: "#1d4ed8", accent: "#d97706", mode: "dark" })),
    });
  });

  it.each(['"5"', 'W/"5"', 'w/"5"', "5", '"1", "5"', "*"])(
    "trả 304 thân rỗng khi If-None-Match (%s) khớp version hiện tại",
    async (ifNoneMatch) => {
      fetchMock.mockResolvedValueOnce(
        Response.json({
          version: 5,
          mode: "light",
          palette: { presetId: "emerald-gold" },
        }),
      );

      const response = await GET(buildRequest("/mobile/appearance-tokens.json", { "if-none-match": ifNoneMatch }));

      expect(response.status).toBe(304);
      expect(response.headers.get("etag")).toBe('"5"');
      expect(response.headers.get("cache-control")).toBe("no-cache");
      expect(await response.text()).toBe("");
    },
  );

  it("trả 200 khi If-None-Match không khớp version hiện tại", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({
        version: 5,
        mode: "light",
        palette: { presetId: "emerald-gold" },
      }),
    );

    const response = await GET(buildRequest("/mobile/appearance-tokens.json", { "if-none-match": '"4"' }));

    expect(response.status).toBe(200);
    expect(response.headers.get("etag")).toBe('"5"');
    const body = (await response.json()) as { version: number; presetId: string };
    expect(body.version).toBe(5);
    expect(body.presetId).toBe("emerald-gold");
  });

  it("luôn gọi backend với cache: no-store và trả version mới dù có tham số truy vấn bất kỳ", async () => {
    fetchMock
      .mockResolvedValueOnce(
        Response.json({
          version: 2,
          mode: "light",
          palette: { presetId: "thi360-green" },
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          version: 3,
          mode: "dark",
          palette: { presetId: "indigo-coral" },
        }),
      );

    const first = await GET(buildRequest("/mobile/appearance-tokens.json?v=2"));
    expect(first.status).toBe(200);
    expect(((await first.json()) as { version: number }).version).toBe(2);

    const second = await GET(buildRequest("/mobile/appearance-tokens.json?v=2&cachebust=x"));
    expect(second.status).toBe(200);
    expect(second.headers.get("etag")).toBe('"3"');
    const secondBody = (await second.json()) as { version: number; mode: string; presetId: string };
    expect(secondBody.version).toBe(3);
    expect(secondBody.mode).toBe("dark");
    expect(secondBody.presetId).toBe("indigo-coral");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    for (const call of fetchMock.mock.calls) {
      const [request, init] = call as unknown as [
        Request,
        RequestInit & { next?: { revalidate?: number; tags?: string[] } },
      ];
      expect(request.url).toBe(`${API}/api/v1/appearance`);
      expect(init.cache).toBe("no-store");
      expect(init.next).toBeUndefined();
    }
  });

  it("trả 503 khi backend trả 500 hoặc lỗi mạng", async () => {
    fetchMock.mockResolvedValueOnce(new Response("internal error", { status: 500 }));

    const httpError = await GET(buildRequest());
    expect(httpError.status).toBe(503);
    expect(httpError.headers.get("content-type")).toBe("application/json");
    expect(httpError.headers.get("cache-control")).toBe("no-store");
    expect(await httpError.json()).toEqual({
      code: "APPEARANCE_UNAVAILABLE",
      message: "Appearance configuration is unavailable.",
    });

    fetchMock.mockRejectedValueOnce(new Error("ECONNREFUSED"));

    const networkError = await GET(buildRequest());
    expect(networkError.status).toBe(503);
    expect(networkError.headers.get("cache-control")).toBe("no-store");
    expect(await networkError.json()).toEqual({
      code: "APPEARANCE_UNAVAILABLE",
      message: "Appearance configuration is unavailable.",
    });
  });

  it("trả 503 khi thiếu THI360_API_URL ở production mà không gọi fetch", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("THI360_API_URL", "");

    const response = await GET(buildRequest());

    expect(response.status).toBe(503);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["JSON hỏng", () => new Response("{broken", { status: 200, headers: { "content-type": "application/json" } })],
    ["body không phải object", () => Response.json(["unexpected"])],
    ["mode không hợp lệ", () => Response.json({ version: 1, mode: "sepia", palette: { presetId: "thi360-green" } })],
    ["version âm", () => Response.json({ version: -1, mode: "light", palette: { presetId: "thi360-green" } })],
    ["version không phải số nguyên", () => Response.json({ version: 1.5, mode: "light", palette: { presetId: "thi360-green" } })],
    ["presetId không tồn tại", () => Response.json({ version: 5, mode: "light", palette: { presetId: "unknown-preset" } })],
    ["custom thiếu trường custom", () => Response.json({ version: 2, mode: "light", palette: { presetId: "custom" } })],
    [
      "custom có mã màu sai",
      () =>
        Response.json({
          version: 2,
          mode: "light",
          palette: { presetId: "custom", custom: { primary: "#123", accent: "#f59223" } },
        }),
    ],
  ])("trả 503 (không rơi về mặc định) khi payload sai: %s", async (_label, makeResponse) => {
    fetchMock.mockResolvedValueOnce(makeResponse());

    const response = await GET(buildRequest("/mobile/appearance-tokens.json", { "if-none-match": '"5"' }));

    expect(response.status).toBe(503);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(await response.json()).toEqual({
      code: "APPEARANCE_UNAVAILABLE",
      message: "Appearance configuration is unavailable.",
    });
  });

  it("trả 503 khi presetId lạ ngay cả khi If-None-Match là *", async () => {
    fetchMock.mockResolvedValueOnce(
      Response.json({
        version: 9,
        mode: "dark",
        palette: { presetId: "future-theme" },
      }),
    );

    const response = await GET(buildRequest("/mobile/appearance-tokens.json", { "if-none-match": "*" }));

    expect(response.status).toBe(503);
    expect(response.headers.get("cache-control")).toBe("no-store");
  });
});
