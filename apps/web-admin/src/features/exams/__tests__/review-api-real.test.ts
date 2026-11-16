import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  checkRequiredReason,
  errorCodeOf,
  generatePreview,
  getCheckRun,
  publishExam,
  runExamCheck,
  startDeepCheck,
} from "../review-api";

const EXAM = "11111111-1111-1111-1111-111111111111";
const RUN = "22222222-2222-2222-2222-222222222222";

interface Recorded {
  method: string;
  url: URL;
  headers: Headers;
  body: unknown;
}

let requests: Recorded[] = [];

/** Nhanh API that (co mock tat): moi lan goi `fetch` tra ve phan hoi duoc xep san theo thu tu. */
function respondWith(...replies: Array<{ status: number; body?: unknown }>) {
  const queue = [...replies];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: Request) => {
      const text = await input.clone().text();
      requests.push({
        method: input.method,
        url: new URL(input.url),
        headers: input.headers,
        body: text ? JSON.parse(text) : undefined,
      });
      const reply = queue.shift() ?? { status: 500 };
      return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
        status: reply.status,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
}

beforeEach(() => {
  requests = [];
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("review-api nhanh that", () => {
  it("kiem tra dong bo map ma moi, stale va invalidReason cua API", async () => {
    respondWith({
      status: 200,
      body: {
        examId: EXAM,
        status: "INVALID",
        stale: true,
        invalidReason: "SOURCE_CHANGED",
        checkedAt: "2026-11-17T01:00:00Z",
        paths: [
          {
            pathId: "default",
            result: "INVALID",
            issues: [
              { code: "MATRIX_NON_INTEGER", severity: "ERROR", group: "MATRIX", blockId: "b1" },
              { code: "ANSWER_QUOTA_DUPLICATE", group: "MATRIX", answerType: "SINGLE_CHOICE" },
              { code: "PART_EMPTY", group: "STRUCTURE" },
            ],
          },
        ],
      },
    });

    const result = await runExamCheck({ examId: EXAM });

    expect(requests[0]).toMatchObject({ method: "POST" });
    expect(requests[0]!.url.pathname).toBe(`/api/v1/admin/exams/${EXAM}/check`);
    expect(result.stale).toBe(true);
    expect(result.invalidReason).toBe("SOURCE_CHANGED");
    expect(result.paths[0]!.issues.map((issue) => issue.code)).toEqual([
      "MATRIX_NON_INTEGER",
      "ANSWER_QUOTA_DUPLICATE",
      "PART_EMPTY",
    ]);
    expect(result.paths[0]!.issues[1]!.answerType).toBe("SINGLE_CHOICE");
  });

  it("kiem tra ky: 202 tra runId, hoi trang thai theo runId", async () => {
    respondWith(
      { status: 202, body: { runId: RUN } },
      { status: 200, body: { runId: RUN, state: "RUNNING" } },
      { status: 200, body: { runId: RUN, state: "FAILED", errorCode: "CHECK_RUN_ABANDONED" } },
    );

    const started = await startDeepCheck(EXAM);
    const running = await getCheckRun(EXAM, started.runId);
    const failed = await getCheckRun(EXAM, started.runId);

    expect(started.runId).toBe(RUN);
    expect(requests[0]!.url.searchParams.get("deep")).toBe("true");
    expect(requests[1]!.url.pathname).toBe(`/api/v1/admin/exams/${EXAM}/check/runs/${RUN}`);
    expect(running).toMatchObject({ state: "RUNNING", result: null });
    expect(failed).toMatchObject({ state: "FAILED", errorCode: "CHECK_RUN_ABANDONED" });
  });

  it("xem truoc: 422 PREVIEW_SUBJECTS_INVALID mang ma loi de man hinh dich", async () => {
    respondWith({ status: 422, body: { code: "PREVIEW_SUBJECTS_INVALID", message: "x" } });

    const error = await generatePreview({ examId: EXAM, pathId: "p", seed: 1 }).catch((caught: unknown) => caught);

    expect(errorCodeOf(error)).toBe("PREVIEW_SUBJECTS_INVALID");
  });

  it("cong bo gui Idempotency-Key da cho, 409 CHECK_REQUIRED mang ly do", async () => {
    respondWith({
      status: 409,
      body: { code: "CHECK_REQUIRED", fieldErrors: [{ field: "reason", code: "SOURCE_CHANGED" }] },
    });

    const error = await publishExam({
      examId: EXAM,
      pathIds: ["default"],
      note: null,
      expectedRevision: 7,
      idempotencyKey: "key-1",
    }).catch((caught: unknown) => caught);

    expect(requests[0]!.headers.get("Idempotency-Key")).toBe("key-1");
    expect(requests[0]!.body).toEqual({ pathIds: ["default"], expectedRevision: 7 });
    expect(checkRequiredReason(error)).toBe("SOURCE_CHANGED");
  });

  it("checkRequiredReason bo qua loi khac 409 CHECK_REQUIRED", async () => {
    respondWith({ status: 412, body: { code: "EXAM_REVISION_CONFLICT" } });

    const error = await publishExam({
      examId: EXAM,
      pathIds: ["default"],
      note: "ghi chu",
      expectedRevision: 1,
    }).catch((caught: unknown) => caught);

    expect(checkRequiredReason(error)).toBeNull();
    expect(errorCodeOf(error)).toBe("EXAM_REVISION_CONFLICT");
    expect(requests[0]!.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
  });
});
