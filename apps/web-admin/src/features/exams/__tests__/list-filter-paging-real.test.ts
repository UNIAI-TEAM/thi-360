import { describe, expect, it, vi } from "vitest";
import { listExams } from "../api";
import { FakeServer } from "./fake-exams-server";

const LIST_PATH = "/api/v1/admin/exams";

function wireRow(index: number) {
  return {
    id: `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
    code: `HSA-${index}`,
    name: `Bài ${index}`,
    kind: "HSA",
    scope: "PART",
    status: "DRAFT",
    revision: 3 + index,
    timing: "SHARED",
    partCode: "MATH",
    subjectCode: "MATH",
    referenceMax: 1200,
    practiceScoring: "RAW",
    hasUnpublishedChanges: true,
    questionCount: 10,
    durationMinutes: 60,
    hasEnglishOption: false,
    combinationCount: 0,
    hasEssay: false,
    attemptCount: 0,
    check: { status: "INVALID", issueCount: 0 },
    updatedAt: "2026-11-17T01:00:00Z",
    updatedBy: { id: "u1", name: "Cô Lan" },
  };
}

describe("danh sách bài thi: bộ lọc và phân trang do máy chủ", () => {
  it("gửi checkStatus, hasUnpublishedChanges, page, size một lần và giữ nguyên tổng của máy chủ", async () => {
    const server = new FakeServer()
      .on("GET", LIST_PATH, () => ({
        status: 200,
        body: { items: [wireRow(0), wireRow(1)], page: 4, size: 20, totalItems: 1234, totalPages: 62 },
      }))
      .install();

    const result = await listExams({ checkStatus: "INVALID", hasUnpublishedChanges: true, page: 4, size: 20 });

    const calls = server.calls("GET", LIST_PATH);
    expect(calls).toHaveLength(1);
    const params = calls[0]!.url.searchParams;
    expect(params.get("checkStatus")).toBe("INVALID");
    expect(params.get("hasUnpublishedChanges")).toBe("true");
    expect(params.get("page")).toBe("4");
    expect(params.get("size")).toBe("20");
    expect(result).toMatchObject({ page: 4, size: 20, totalElements: 1234, totalPages: 62 });
  });

  it("bỏ tham số hasUnpublishedChanges khi bộ lọc tắt", async () => {
    const server = new FakeServer()
      .on("GET", LIST_PATH, () => ({ status: 200, body: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } }))
      .install();

    await listExams({ hasUnpublishedChanges: false });

    expect(server.calls("GET", LIST_PATH)[0]!.url.searchParams.has("hasUnpublishedChanges")).toBe(false);
  });

  it("dòng danh sách mang revision, cách tính thời gian, phần, môn, thang quy chiếu và cách chấm luyện tập", async () => {
    new FakeServer()
      .on("GET", LIST_PATH, () => ({ status: 200, body: { items: [wireRow(2)], page: 0, size: 20, totalItems: 1, totalPages: 1 } }))
      .install();

    const { content } = await listExams({});

    expect(content[0]).toMatchObject({
      revision: 5,
      timing: "SHARED",
      partCode: "MATH",
      subjectCode: "MATH",
      referenceMax: 1200,
      practiceScoring: "RAW",
      durationMinutes: 60,
    });
  });

  it("đổi truy vấn (hủy tín hiệu) hủy yêu cầu đang chờ", async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: Request) => {
        controller.abort();
        if (input.signal.aborted) throw new DOMException("aborted", "AbortError");
        return new Response("{}", { status: 200 });
      }),
    );

    await expect(listExams({ checkStatus: "INVALID" }, controller.signal)).rejects.toThrow();
  });
});
