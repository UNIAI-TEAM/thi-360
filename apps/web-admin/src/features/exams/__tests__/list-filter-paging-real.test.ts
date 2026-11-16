import { beforeEach, describe, expect, it, vi } from "vitest";
import { listExams } from "../api.http";
import { FakeServer, type Recorded } from "./fake-exams-server";

const LIST_PATH = "/api/v1/admin/exams";
const PAGE_SIZE = 100;

function wireRow(index: number) {
  return {
    id: `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
    code: `HSA-${index}`,
    name: `Bài ${index}`,
    kind: "HSA",
    scope: "FULL",
    status: "DRAFT",
    hasUnpublishedChanges: false,
    questionCount: 10,
    hasEnglishOption: false,
    combinationCount: 0,
    hasEssay: false,
    attemptCount: 0,
    // Cứ ba bài thì một bài INVALID.
    check: { status: index % 3 === 0 ? "INVALID" : "VALID", issueCount: 0 },
    updatedAt: "2026-11-17T01:00:00Z",
    updatedBy: { id: "u1", name: "Cô Lan" },
  };
}

/** Máy chủ giả có `total` bài, trả theo `page` và `size` của yêu cầu. */
function pagedServer(total: number) {
  return new FakeServer()
    .on("GET", LIST_PATH, (request: Recorded) => {
      const page = Number(request.url.searchParams.get("page"));
      const size = Number(request.url.searchParams.get("size"));
      const items = Array.from({ length: Math.min(size, Math.max(total - page * size, 0)) }, (_, offset) => wireRow(page * size + offset));
      return { status: 200, body: { items, page, size, totalItems: total, totalPages: Math.ceil(total / size) } };
    })
    .install();
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
});

describe("danh sách lọc phía máy khách (checkStatus)", () => {
  it("đọc từng trang 100 bài, lọc rồi cắt trang; đủ trang thì không báo chưa đủ", async () => {
    const server = pagedServer(250);

    const result = await listExams({ checkStatus: "INVALID", page: 1, size: 20 });

    expect(server.calls("GET", LIST_PATH).map((request) => request.url.searchParams.get("page"))).toEqual(["0", "1", "2"]);
    expect(server.calls("GET", LIST_PATH).every((request) => request.url.searchParams.get("size") === String(PAGE_SIZE))).toBe(true);
    // 250 bài, cứ ba bài một INVALID (chỉ số chia hết cho 3): 84 bài; trang 2 (chỉ số 1) có 20 bài.
    expect(result.totalElements).toBe(84);
    expect(result.content).toHaveLength(20);
    expect(result.content.every((row) => row.check.status === "INVALID")).toBe(true);
    expect(result.incomplete).toBeUndefined();
  });

  it("dừng ở 10 trang (1.000 bài) và báo kết quả có thể chưa đủ", async () => {
    const server = pagedServer(2500);

    const result = await listExams({ checkStatus: "INVALID" });

    expect(server.calls("GET", LIST_PATH)).toHaveLength(10);
    expect(result.incomplete).toBe(true);
    expect(result.totalElements).toBe(334);
  });

  it("đổi truy vấn (hủy tín hiệu) dừng đọc các trang còn lại", async () => {
    const controller = new AbortController();
    const abortedAtCall: boolean[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: Request) => {
        abortedAtCall.push(input.signal.aborted);
        if (input.signal.aborted) throw new DOMException("aborted", "AbortError");
        if (abortedAtCall.length === 1) controller.abort();
        const body = { items: [wireRow(0)], page: abortedAtCall.length - 1, size: PAGE_SIZE, totalItems: 500, totalPages: 5 };
        return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
      }),
    );

    await expect(listExams({ checkStatus: "INVALID" }, controller.signal)).rejects.toThrow();

    expect(abortedAtCall).toEqual([false, true]);
  });
});
