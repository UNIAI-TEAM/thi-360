import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createProxyHandlers } from "./proxy";

const API = "http://api.internal:8080";
const fetchMock = vi.fn<typeof fetch>();

const handlers = createProxyHandlers({
  messages: {
    notFound: "Không tìm thấy.",
    proxyNotConfigured: "Chưa cấu hình API.",
    upstreamTimeout: "Quá thời gian.",
    upstreamUnavailable: "Không kết nối được.",
  },
});

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

function lastHeaders() {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) throw new Error("fetch chưa được gọi");
  return new Headers(call[1]?.headers);
}

beforeEach(() => {
  vi.stubEnv("THI360_API_URL", API);
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(Response.json({ ok: true }));
});

afterEach(() => {
  fetchMock.mockReset();
});

describe("proxyApiRequest X-Forwarded-For", () => {
  it("không bao giờ chuyển tiếp X-Forwarded-For do client gửi", async () => {
    await handlers.GET(
      new NextRequest("http://localhost:3000/api/v1/me", {
        headers: { "x-forwarded-for": "203.0.113.9", "x-real-ip": "203.0.113.10" },
      }),
      context("v1", "me"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBeNull();
  });

  it("ghi đè X-Forwarded-For bằng tùy chọn resolveClientIp và bỏ qua giá trị client gửi", async () => {
    const customHandlers = createProxyHandlers({
      messages: {
        notFound: "Không tìm thấy.",
        proxyNotConfigured: "Chưa cấu hình API.",
        upstreamTimeout: "Quá thời gian.",
        upstreamUnavailable: "Không kết nối được.",
      },
      resolveClientIp: () => "198.51.100.42",
    });

    await customHandlers.POST(
      new NextRequest("http://localhost:3000/api/v1/auth/login", {
        method: "POST",
        body: "{}",
        headers: { "x-forwarded-for": "203.0.113.9" },
      }),
      context("v1", "auth", "login"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBe("198.51.100.42");
  });

  it("ghi đè X-Forwarded-For bằng x-real-ip khi THI360_TRUST_REAL_IP=1", async () => {
    vi.stubEnv("THI360_TRUST_REAL_IP", "1");

    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/auth/login", {
        method: "POST",
        body: "{}",
        headers: { "x-forwarded-for": "203.0.113.9", "x-real-ip": "198.51.100.77" },
      }),
      context("v1", "auth", "login"),
    );

    expect(lastHeaders().get("x-forwarded-for")).toBe("198.51.100.77");
  });
});

describe("proxyApiRequest Idempotency-Key và multipart stream", () => {
  it("chuyển tiếp header Idempotency-Key tới upstream", async () => {
    await handlers.POST(
      new NextRequest("http://localhost:3000/api/v1/admin/catalog/groups/g1/items/import", {
        method: "POST",
        body: "{}",
        headers: {
          "content-type": "application/json",
          "idempotency-key": "idem-12345",
        },
      }),
      context("v1", "admin", "catalog", "groups", "g1", "items", "import"),
    );

    expect(lastHeaders().get("idempotency-key")).toBe("idem-12345");
  });

  it("stream body multipart/form-data 4 MB (không parse JSON) kèm content-type và idempotency-key", async () => {
    const fourMb = 4 * 1024 * 1024;
    const payload = new Uint8Array(fourMb);
    payload[0] = 0x50;
    payload[1] = 0x4b;
    payload[fourMb - 1] = 0x21;

    let receivedFile: unknown = null;
    let capturedInit: (RequestInit & { duplex?: string }) | undefined;
    fetchMock.mockImplementationOnce(async (_url: unknown, init?: RequestInit) => {
      capturedInit = init as RequestInit & { duplex?: string };
      const parsedForm = await new Response(init?.body ?? null, {
        headers: init?.headers,
      }).formData();
      receivedFile = parsedForm.get("file");
      return Response.json({ ok: true });
    });

    const form = new FormData();
    form.append(
      "file",
      new File([payload], "catalog-4mb.xlsx", {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
    );

    const req = new NextRequest(
      "http://localhost:3000/api/v1/admin/catalog/groups/g1/items/import/preview",
      {
        method: "POST",
        body: form,
        headers: {
          "idempotency-key": "idem-upload-4mb",
        },
      },
    );

    const res = await handlers.POST(
      req,
      context("v1", "admin", "catalog", "groups", "g1", "items", "import", "preview"),
    );

    expect(res.status).toBe(200);
    expect(capturedInit?.duplex).toBe("half");
    expect(capturedInit?.body).toBeInstanceOf(ReadableStream);
    expect(lastHeaders().get("content-type")).toMatch(/^multipart\/form-data;/);
    expect(lastHeaders().get("idempotency-key")).toBe("idem-upload-4mb");
    expect(receivedFile).toBeInstanceOf(File);
    expect((receivedFile as File).size).toBe(fourMb);
    expect((receivedFile as File).name).toBe("catalog-4mb.xlsx");
  });
});
