import { afterEach, describe, expect, it, vi } from "vitest";
import { readCookieValue, XSRF_HEADER_NAME, xsrfMiddleware } from "./client";

describe("xsrfMiddleware", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("đọc giá trị cookie theo tên và gắn X-XSRF-TOKEN cho request ghi", async () => {
    expect(readCookieValue("a=1; thi360_xsrf=tok%2D1; b=2", "thi360_xsrf")).toBe("tok-1");
    expect(readCookieValue("a=1", "thi360_xsrf")).toBeNull();

    vi.stubGlobal("document", { cookie: "thi360_xsrf=xsrf-abc" });

    const getReq = new Request("http://localhost:3000/api/v1/me", { method: "GET" });
    const getResult = await xsrfMiddleware.onRequest?.({
      request: getReq,
      schemaPath: "/api/v1/me",
      params: {},
      options: {} as never,
      id: "1",
    });
    expect((getResult as Request).headers.get(XSRF_HEADER_NAME)).toBeNull();

    const postReq = new Request("http://localhost:3000/api/v1/auth/logout", { method: "POST" });
    const postResult = await xsrfMiddleware.onRequest?.({
      request: postReq,
      schemaPath: "/api/v1/auth/logout",
      params: {},
      options: {} as never,
      id: "2",
    });
    expect((postResult as Request).headers.get(XSRF_HEADER_NAME)).toBe("xsrf-abc");
  });
});
