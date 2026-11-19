import { afterEach, describe, expect, it, vi } from "vitest";
import { requestEmailChange, sendVerificationCode } from "./api";

/**
 * Backend trả 202 không thân cho hai yêu cầu gửi mã; proxy `/api` bỏ `Content-Length`, nên phản hồi tới trình duyệt là
 * 202 thân rỗng không có độ dài. Hai hàm phải coi đó là thành công (trước đây `response.json()` ném SyntaxError và
 * màn hình báo "không gửi được mã" dù mã đã gửi).
 */
function stubFetch(response: Response): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async () => response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("account api: 202 không thân", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sendVerificationCode không lỗi khi 202 rỗng không có Content-Length", async () => {
    const fetchMock = stubFetch(new Response("", { status: 202 }));

    await expect(sendVerificationCode()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("requestEmailChange không lỗi khi 202 rỗng không có Content-Length", async () => {
    const fetchMock = stubFetch(new Response("", { status: 202 }));

    await expect(requestEmailChange("moi@thi360.vn", "Thi360@dev")).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("requestEmailChange vẫn ném lỗi trạng thái khi 409", async () => {
    stubFetch(
      new Response(JSON.stringify({ code: "EMAIL_TAKEN", message: "x" }), {
        status: 409,
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(requestEmailChange("moi@thi360.vn", "Thi360@dev")).rejects.toMatchObject({
      status: 409,
      code: "EMAIL_TAKEN",
    });
  });
});
