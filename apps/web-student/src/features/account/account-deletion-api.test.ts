import { readFileSync } from "node:fs";
import path from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import {
  type AccountDeletionCheckResponse,
  deleteAccount,
  loadAccountDeletionCheck,
} from "./api";

const FIXTURES_DIR = path.resolve(import.meta.dirname, "../../../../../contracts/mobile/api");

function loadFixture<T>(filename: string): T {
  return JSON.parse(readFileSync(path.join(FIXTURES_DIR, filename), "utf8")) as T;
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function stubFetch(response: Response | Error): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async () => {
    if (response instanceof Error) throw response;
    return response;
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("account deletion API", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("loadAccountDeletionCheck trả dữ liệu 200 từ hợp đồng và ném ApiStatusError khi 500", async () => {
    const expected = loadFixture<AccountDeletionCheckResponse>(
      "me-account-deletion-check-200-unused-attempts.json",
    );
    const fetchMock = stubFetch(jsonResponse(expected, 200));

    await expect(loadAccountDeletionCheck()).resolves.toEqual(expected);
    const [request] = fetchMock.mock.calls[0] as [Request];
    expect(request.method).toBe("GET");
    expect(new URL(request.url).pathname).toBe("/api/v1/me/account/deletion-check");

    stubFetch(jsonResponse({ code: "INTERNAL_ERROR", message: "boom", fieldErrors: [], traceId: "t" }, 500));
    await expect(loadAccountDeletionCheck()).rejects.toMatchObject({ status: 500 });
  });

  it("deleteAccount trả { kind: 'deleted' } khi 204 không thân và gửi POST với { currentPassword }", async () => {
    const fetchMock = stubFetch(new Response(null, { status: 204 }));

    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).resolves.toEqual({ kind: "deleted" });
    expect(fetchMock).toHaveBeenCalledOnce();
    const [request] = fetchMock.mock.calls[0] as [Request];
    expect(request.method).toBe("POST");
    expect(new URL(request.url).pathname).toBe("/api/v1/me/account/deletion");
    await expect(request.json()).resolves.toEqual({ currentPassword: "Thi360@dev" });
  });

  it("deleteAccount tự đổi 401 thành { kind: 'deleted' } và không ném lỗi (AC-18, CR-13)", async () => {
    stubFetch(jsonResponse({ code: "UNAUTHORIZED", message: "Unauthorized", fieldErrors: [], traceId: "t" }, 401));

    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).resolves.toEqual({ kind: "deleted" });
  });

  it("deleteAccount tự đổi 403 thành { kind: 'notAllowed' } và không ném lỗi (AC-18)", async () => {
    const fixture = loadFixture("me-account-deletion-403-account-deletion-not-allowed.json");
    stubFetch(jsonResponse(fixture, 403));

    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).resolves.toEqual({ kind: "notAllowed" });
  });

  it("deleteAccount phân loại 400 WRONG_OLD_PASSWORD, 429 LOGIN_THROTTLED và 409 ACCOUNT_DELETION_BLOCKED", async () => {
    stubFetch(jsonResponse(loadFixture("me-account-deletion-400-wrong-old-password.json"), 400));
    await expect(deleteAccount({ currentPassword: "wrong" })).resolves.toEqual({ kind: "wrongPassword" });

    stubFetch(jsonResponse(loadFixture("me-account-deletion-429-login-throttled.json"), 429));
    await expect(deleteAccount({ currentPassword: "wrong" })).resolves.toEqual({ kind: "throttled" });

    stubFetch(jsonResponse(loadFixture("me-account-deletion-409-account-deletion-blocked.json"), 409));
    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).resolves.toEqual({
      kind: "blocked",
      blockers: ["ATTEMPT_IN_PROGRESS", "ORDER_BEING_CHECKED", "ORDER_PENDING_PAYMENT"],
    });
  });

  it("deleteAccount ném ApiStatusError khi 5xx và ném lỗi gốc khi mất mạng", async () => {
    stubFetch(jsonResponse({ code: "INTERNAL_ERROR", message: "x", fieldErrors: [], traceId: "t" }, 500));
    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).rejects.toBeInstanceOf(ApiStatusError);

    stubFetch(new TypeError("Failed to fetch"));
    await expect(deleteAccount({ currentPassword: "Thi360@dev" })).rejects.toThrow("Failed to fetch");
  });
});
