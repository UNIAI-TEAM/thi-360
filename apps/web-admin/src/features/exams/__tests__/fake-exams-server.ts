import type { components } from "@thi360/api-client";
import { vi } from "vitest";

/**
 * Máy chủ giả cho test nhánh API thật của Bài thi (`CONTENT_MOCK` tắt): thay `fetch` toàn cục, ghi lại từng yêu cầu và trả
 * phản hồi xếp sẵn theo `METHOD đường-dẫn`. Đi qua đúng `createApiClient` và các hàm chuyển đổi của sản phẩm, không dùng
 * kho dữ liệu mẫu. Không thêm thư viện (không dùng msw).
 */

export interface Reply {
  status: number;
  body?: unknown;
}

export interface Recorded {
  method: string;
  url: URL;
  headers: Headers;
  body: unknown;
}

type Handler = Reply | ((request: Recorded) => Reply);

interface Route {
  method: string;
  pattern: RegExp;
  handlers: Handler[];
}

export class FakeServer {
  readonly requests: Recorded[] = [];
  private readonly routes: Route[] = [];

  /**
   * Phản hồi cho `method path` (`:ten` khớp một đoạn đường dẫn). Nhiều phản hồi được trả lần lượt; phản hồi cuối giữ nguyên
   * cho các lần gọi sau. Đăng ký đường cụ thể trước đường chung.
   */
  on(method: string, path: string, ...handlers: Handler[]): this {
    const pattern = new RegExp(`^${path.replace(/:[A-Za-z]+/g, "[^/]+")}$`);
    this.routes.push({ method, pattern, handlers: [...handlers] });
    return this;
  }

  /** Các yêu cầu đã nhận cho `method path` (đường dẫn đầy đủ, không có query). */
  calls(method: string, path: string): Recorded[] {
    return this.requests.filter((request) => request.method === method && request.url.pathname === path);
  }

  install(): this {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: Request) => {
        const text = await input.clone().text();
        const recorded: Recorded = {
          method: input.method,
          url: new URL(input.url),
          headers: input.headers,
          body: text ? (JSON.parse(text) as unknown) : undefined,
        };
        this.requests.push(recorded);
        const route = this.routes.find(
          (candidate) => candidate.method === recorded.method && candidate.pattern.test(recorded.url.pathname),
        );
        const handler = route && (route.handlers.length > 1 ? route.handlers.shift()! : route.handlers[0]);
        const reply: Reply = !handler
          ? { status: 404, body: { code: "FAKE_SERVER_UNMATCHED", message: `${recorded.method} ${recorded.url.pathname}` } }
          : typeof handler === "function"
            ? handler(recorded)
            : handler;
        return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
          status: reply.status,
          headers: { "Content-Type": "application/json" },
        });
      }),
    );
    return this;
  }
}

export const EXAM_ID = "11111111-1111-1111-1111-111111111111";
export const RUN_ID = "22222222-2222-2222-2222-222222222222";
export const EXAM_PATH = `/api/v1/admin/exams/${EXAM_ID}`;

type ExamDetailWire = components["schemas"]["ExamDetailResponse"];

/** Phần cố định một môn, một khối: đủ để màn Cấu trúc dựng được và sửa được thời lượng. */
export const FIXED_PART = {
  id: "part-1",
  order: 1,
  name: "Part 1",
  durationMinutes: 60,
  questionCount: 10,
  participation: { mode: "ALL" },
  sections: [
    {
      id: "sec-1",
      subjectCode: "MATH",
      questionCount: 10,
      groupOrder: "FIXED",
      blocks: [
        { id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 10, requiredPointsPerQuestion: 1 },
      ],
    },
  ],
} as unknown as ExamDetailWire["parts"][number];

/** Bài HSA toàn phần, Nháp, `revision` 7, như `ExamDetailResponse` của máy chủ. */
export function examDetailWire(overrides: Partial<ExamDetailWire> = {}): ExamDetailWire {
  return {
    id: EXAM_ID,
    code: "HSA-F-001",
    name: "HSA exam 1",
    kind: "HSA",
    scope: "FULL",
    status: "DRAFT",
    revision: 7,
    hasUnpublishedChanges: true,
    questionCount: 10,
    maxScore: 150,
    hasEnglishOption: false,
    combinationCount: 0,
    timing: "PER_PART",
    practiceScoring: "RAW",
    hasEssay: false,
    attemptCount: 0,
    check: { status: "UNCHECKED", issueCount: 0 },
    updatedAt: "2026-11-17T01:00:00Z",
    yearsConfirmed: true,
    parts: [FIXED_PART],
    versions: [],
    ...overrides,
  };
}

/** Kết quả kiểm tra một đường (`ExamCheckResult`): `UNCONFIRMED` hiện nút Kiểm tra kỹ. */
export function checkResultWire(result: "VALID" | "INVALID" | "UNCONFIRMED" = "VALID") {
  return {
    examId: EXAM_ID,
    status: result === "VALID" ? "VALID" : "INVALID",
    stale: false,
    checkedAt: "2026-11-17T01:00:00Z",
    durationSeconds: 3,
    timeLimitSeconds: 30,
    issueCount: 0,
    paths: [{ pathId: "default", result, issues: [] }],
  };
}
