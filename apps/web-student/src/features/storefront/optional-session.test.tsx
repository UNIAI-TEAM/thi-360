import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { loadOptionalSession } from "@/features/session/api";
import { useOptionalSession } from "@/features/session/use-optional-session";
import { ApiStatusError } from "@/lib/api/client";
import viMessages from "@/messages/vi.json";
import { QueryProvider } from "@/providers/query-provider";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";

const mocks = vi.hoisted(() => ({ replace: vi.fn(), navigate: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/exams/HSA-FULL-01",
}));

const fetchMock = vi.fn<typeof fetch>();

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function requestPath(input: RequestInfo | URL): string {
  return new URL(input instanceof Request ? input.url : String(input)).pathname;
}

const UNAUTHORIZED = { code: "UNAUTHORIZED", message: "x", fieldErrors: [], traceId: "t" };

/** Trả `json(401)` cho `/api/v1/auth/refresh` và lời gọi khác tùy `me`. */
function stubMeThenRefreshFail(me: Response) {
  fetchMock.mockImplementation(async (input) =>
    json(requestPath(input) === "/api/v1/me" ? me.status : 401, UNAUTHORIZED),
  );
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.sessionStorage.clear();
});

afterEach(() => {
  fetchMock.mockReset();
  mocks.replace.mockReset();
  mocks.navigate.mockReset();
  vi.unstubAllGlobals();
});

describe("phiên tùy chọn cho trang công khai (AC-11)", () => {
  it("khách: /api/v1/me trả 401 và làm mới cũng 401 thì nhận null, không ném lỗi", async () => {
    stubMeThenRefreshFail(json(401, UNAUTHORIZED));

    await expect(loadOptionalSession()).resolves.toBeNull();
    expect(fetchMock.mock.calls.map(([input]) => requestPath(input))).toEqual([
      "/api/v1/me",
      "/api/v1/auth/refresh",
    ]);
  });

  it("đã đăng nhập: trả đúng phiên", async () => {
    fetchMock.mockImplementation(async () => json(200, STUDENT_SESSION));

    await expect(loadOptionalSession()).resolves.toMatchObject({ username: "student01", access: "EXAM" });
  });

  it("cookie truy cập hết hạn nhưng phiên còn: làm mới xong gọi lại /me và trả đúng phiên", async () => {
    // Một lượt duy nhất: /me 401 -> refresh 200 -> /me 200. Đếm lời gọi trong chính implementation, không đọc
    // `mock.calls` (bản ghi được thêm trước khi implementation chạy, dễ đếm lệch).
    let meCalls = 0;
    fetchMock.mockImplementation(async (input) => {
      const path = requestPath(input);
      if (path === "/api/v1/auth/refresh") return json(200, {});
      meCalls += 1;
      return meCalls > 1 ? json(200, STUDENT_SESSION) : json(401, UNAUTHORIZED);
    });

    await expect(loadOptionalSession()).resolves.toMatchObject({ username: "student01", access: "EXAM" });
    expect(fetchMock.mock.calls.map(([input]) => requestPath(input))).toEqual([
      "/api/v1/me",
      "/api/v1/auth/refresh",
      "/api/v1/me",
    ]);
  });
  it("làm mới xong mà /me vẫn 401 thì coi là khách, không gọi lại lần nữa", async () => {
    fetchMock.mockImplementation(async (input) =>
      json(requestPath(input) === "/api/v1/auth/refresh" ? 200 : 401, UNAUTHORIZED),
    );

    await expect(loadOptionalSession()).resolves.toBeNull();
    expect(fetchMock.mock.calls.map(([input]) => requestPath(input))).toEqual([
      "/api/v1/me",
      "/api/v1/auth/refresh",
      "/api/v1/me",
    ]);
  });

  it("lỗi khác 401 vẫn là lỗi, để giao diện hiện trạng thái lỗi thay vì coi là khách", async () => {
    fetchMock.mockImplementation(async () => json(500, { code: "INTERNAL_ERROR", message: "x", fieldErrors: [], traceId: "t" }));

    await expect(loadOptionalSession()).rejects.toBeInstanceOf(ApiStatusError);
    await expect(loadOptionalSession()).rejects.toMatchObject({ status: 500 });
  });

  it("dưới QueryProvider: 401 của khách thử làm mới một lần, vẫn nhận null, không đưa về /login", async () => {
    stubMeThenRefreshFail(json(401, UNAUTHORIZED));

    function Probe() {
      const { data, isSuccess } = useOptionalSession();
      return <p data-testid="probe">{isSuccess ? String(data) : "pending"}</p>;
    }
    render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <Probe />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("probe")).toHaveTextContent("null"));
    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls.map(([input]) => requestPath(input))).toEqual([
      "/api/v1/me",
      "/api/v1/auth/refresh",
    ]);
  });
});
