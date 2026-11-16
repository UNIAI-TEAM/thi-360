import type { components } from "@thi360/api-client";
import { afterEach, beforeEach, vi } from "vitest";

/**
 * Client giả cho test của các màn đã nối API (plan B7): các hàm `api.real.ts` gọi
 * `createBrowserApiClient()` của `@/lib/api/client`, nên test chặn ở đó thay vì dữ liệu mẫu.
 *
 * Cách dùng trong một file test:
 *
 * ```ts
 * vi.mock("@/lib/api/client", async () => {
 *   // await import bắt buộc: factory của vi.mock được hoist lên trước static imports.
 *   const fake = await import("@/test/fake-api");
 *   return { createBrowserApiClient: fake.fakeBrowserApiClient };
 * });
 * useRealApi(); // stubEnv CONTENT_MOCK=false + dọn registry mỗi test
 * fakeApi.get("/api/v1/admin/questions", ok(apiPage));
 * ```
 *
 * `response` là `Response` thật của fetch nên `.ok`, `.status`, `.headers.get(...)` dùng được
 * (ví dụ `downloadQuestionTemplate` đọc Content-Disposition). Handler trả `Promise` thì lời gọi
 * treo — dùng cho trạng thái "đang tải". Lời gọi chưa đăng ký trả 500 `UNSTUBBED` (và vẫn nằm
 * trong `calls` để assert tham số).
 */

type Schemas = components["schemas"];

export type ContentReferenceResponse = Schemas["ContentReferenceResponse"];

export interface ApiCall {
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  path: string;
  /** Toàn bộ options truyền cho client (params, body, bodySerializer, parseAs…). */
  options?: Record<string, unknown>;
}

export interface FakeResult {
  data?: unknown;
  error?: unknown;
  response: Response;
}

type Handler = (call: ApiCall) => FakeResult | Promise<FakeResult>;

const handlers = new Map<string, Handler>();
const registry: { calls: ApiCall[] } = { calls: [] };

function keyOf(method: string, path: string): string {
  return `${method} ${path}`;
}

/** Gán phản hồi cho `METHOD path` (path đúng chuỗi openapi-fetch, ví dụ `/api/v1/admin/questions/{id}`). */
function on(method: ApiCall["method"], path: string, result: FakeResult | Handler): void {
  handlers.set(keyOf(method, path), typeof result === "function" ? (result as Handler) : () => result);
}

export const fakeApi = {
  calls: registry.calls,
  on,
  get: (path: string, result: FakeResult | Handler) => on("GET", path, result),
  post: (path: string, result: FakeResult | Handler) => on("POST", path, result),
  put: (path: string, result: FakeResult | Handler) => on("PUT", path, result),
  patch: (path: string, result: FakeResult | Handler) => on("PATCH", path, result),
  delete: (path: string, result: FakeResult | Handler) => on("DELETE", path, result),
  /** Phản hồi treo vô hạn cho trạng thái đang tải. */
  pending(path: string, method: ApiCall["method"] = "GET"): void {
    on(method, path, () => new Promise<FakeResult>(() => {}));
  },
  reset(): void {
    handlers.clear();
    registry.calls.length = 0;
  },
};

/** Trả về client giả có cùng chữ ký `createBrowserApiClient`. */
export function fakeBrowserApiClient() {
  const invoke = (method: ApiCall["method"]) => async (path: string, options?: Record<string, unknown>) => {
    const call: ApiCall = { method, path, options };
    registry.calls.push(call);
    const handler = handlers.get(keyOf(method, path));
    if (!handler) return fail(500, { code: "UNSTUBBED", message: `No handler for ${method} ${path}` });
    return handler(call);
  };
  return {
    GET: invoke("GET"),
    POST: invoke("POST"),
    PUT: invoke("PUT"),
    PATCH: invoke("PATCH"),
    DELETE: invoke("DELETE"),
  };
}

/** Phản hồi thành công: `data` là thân JSON đã giải mã (openapi-fetch). */
export function ok(data: unknown, init?: { status?: number; headers?: Record<string, string> }): FakeResult {
  return {
    data,
    error: undefined,
    response: new Response(null, { status: init?.status ?? 200, headers: init?.headers }),
  };
}

/**
 * Phản hồi lỗi. `error` là thân `{code, message, fieldErrors, traceId}` chuẩn; bỏ qua thì thân rỗng
 * (giống 413 do proxy/web chặn trước, không có mã nghiệp vụ).
 */
export function fail(
  status: number,
  error?: { code?: string; message?: string; fieldErrors?: unknown[] },
): FakeResult {
  return {
    data: undefined,
    error: error === undefined ? undefined : { traceId: "test-trace", fieldErrors: [], message: "", ...error },
    response: new Response(null, { status }),
  };
}

/** Thân lỗi 422 có lỗi theo trường (mapping `field -> path` của form). */
export function fieldErrors(code: string, errors: { field: string; message?: string }[]): FakeResult {
  return fail(422, { code, message: "Validation failed", fieldErrors: errors });
}

/**
 * Cài cho một file test: cả hai cờ dữ liệu mẫu tắt (nhánh API thật chạy, như trong production; danh mục dùng chung
 * cũng đọc dữ liệu mẫu khi `GRADING_MOCK` bật) và dọn registry trước mỗi test. Gọi sau `vi.mock("@/lib/api/client", …)`.
 */
export function useRealApi(): void {
  beforeEach(() => {
    fakeApi.reset();
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
  });
}

const option = (code: string, name: string): Schemas["CatalogOption"] => ({ code, name });

/**
 * `ContentReferenceResponse` tối thiểu đủ cho các màn dùng `useContentReference`: môn Toán, khối
 * 10–12, bốn mức độ, đủ 8 dạng câu. Mở rộng bằng `overrides` khi test cần thêm giá trị.
 */
export function apiContentReference(overrides: Partial<ContentReferenceResponse> = {}): ContentReferenceResponse {
  return {
    combinations: [],
    competencies: [],
    difficulties: [
      option("NB", "Nhận biết"),
      option("TH", "Thông hiểu"),
      option("VD", "Vận dụng"),
      option("VDC", "Vận dụng cao"),
    ],
    examKinds: [],
    grades: [option("10", "Lớp 10"), option("11", "Lớp 11"), option("12", "Lớp 12")],
    knowledgeTree: [
      {
        active: true,
        children: [
          {
            active: true,
            children: [],
            code: "TOAN.11.01.01.01",
            id: "kn-leaf-1",
            level: "KNOWLEDGE",
            name: "Hàm số lượng giác",
            subjectCode: "TOAN",
          },
        ],
        code: "TOAN.11",
        id: "kn-chapter-1",
        level: "CHAPTER",
        name: "Chương 1: Hàm số lượng giác",
        subjectCode: "TOAN",
      },
    ],
    questionTypes: [
      { active: true, code: "SINGLE_CHOICE", name: "Một đáp án" },
      { active: true, code: "MULTIPLE_CHOICE", name: "Nhiều đáp án" },
      { active: true, code: "SHORT_ANSWER", name: "Trả lời ngắn" },
      { active: true, code: "CLOZE", name: "Điền khuyết" },
      { active: true, code: "QUESTION_SET", name: "Câu chùm" },
      { active: true, code: "TRUE_FALSE", name: "Đúng / Sai" },
      { active: true, code: "MATCHING", name: "Ghép đôi" },
      { active: true, code: "ESSAY", name: "Tự luận" },
    ],
    subjects: [option("TOAN", "Toán"), option("VAN", "Ngữ văn")],
    tags: [option("DE_THI", "Đề thi"), option("ON_TAP", "Ôn tập")],
    taskTypes: [option("TINH_HOA", "Tinh hoa"), option("CUNG_CO", "Củng cố")],
    ...overrides,
  };
}
