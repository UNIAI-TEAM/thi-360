import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { loadOptionalSession } from "@/features/session/api";
import { useOptionalSession } from "@/features/session/use-optional-session";
import { ApiStatusError } from "@/lib/api/client";
import viMessages from "@/messages/vi.json";
import { QueryProvider } from "@/providers/query-provider";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";

const mocks = vi.hoisted(() => ({ replace: vi.fn(), navigate: vi.fn(), refreshSession: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => "/exams/HSA-FULL-01",
}));
vi.mock("@/features/session/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/session/api")>()),
  refreshSession: mocks.refreshSession,
}));

const fetchMock = vi.fn<typeof fetch>();

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.sessionStorage.clear();
});

afterEach(() => {
  fetchMock.mockReset();
  mocks.replace.mockReset();
  mocks.navigate.mockReset();
  mocks.refreshSession.mockReset();
  vi.unstubAllGlobals();
});

describe("phiên tùy chọn cho trang công khai (AC-11)", () => {
  it("khách: /api/v1/me trả 401 thì nhận null, không ném lỗi", async () => {
    fetchMock.mockImplementation(async () => json(401, { code: "UNAUTHORIZED", message: "x", fieldErrors: [], traceId: "t" }));

    await expect(loadOptionalSession()).resolves.toBeNull();
  });

  it("đã đăng nhập: trả đúng phiên", async () => {
    fetchMock.mockImplementation(async () => json(200, STUDENT_SESSION));

    await expect(loadOptionalSession()).resolves.toMatchObject({ username: "student01", access: "EXAM" });
  });

  it("lỗi khác 401 vẫn là lỗi, để giao diện hiện trạng thái lỗi thay vì coi là khách", async () => {
    fetchMock.mockImplementation(async () => json(500, { code: "INTERNAL_ERROR", message: "x", fieldErrors: [], traceId: "t" }));

    await expect(loadOptionalSession()).rejects.toBeInstanceOf(ApiStatusError);
    await expect(loadOptionalSession()).rejects.toMatchObject({ status: 500 });
  });

  it("dưới QueryProvider: 401 của khách không đưa về /login, không thử làm mới phiên, không gọi lại", async () => {
    fetchMock.mockImplementation(async () => json(401, { code: "UNAUTHORIZED", message: "x", fieldErrors: [], traceId: "t" }));

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
    expect(mocks.refreshSession).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [request] = fetchMock.mock.calls[0]!;
    expect(new URL((request as Request).url).pathname).toBe("/api/v1/me");
  });
});
