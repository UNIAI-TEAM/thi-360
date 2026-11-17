import type { ApiPage } from "@/lib/api/page";
import { ApiStatusError } from "@/lib/api/query-client";

/**
 * Lớp dữ liệu mẫu còn lại của đợt 2: chỉ `/grading` (QA UNI-2621 gỡ mock của Ngân hàng câu hỏi, Nhập, Bài thi).
 *
 * Quy ước (DESIGN.md mục 5.14):
 * - Giao diện chỉ gọi hook TanStack Query trong `features/<x>/queries.ts`; hook gọi hàm trong `features/<x>/api.ts`.
 * - Hàm của lớp dữ liệu mẫu grading gọi `mockRequest(...)` đọc dữ liệu từ `mock/*.json` (dữ liệu tiếng Việt nằm
 *   trong JSON vì test quét mã nguồn chặn chữ có dấu trong `.ts`/`.tsx`).
 * - `grading/api.ts` chọn giữa bản mẫu và `notWired` theo cờ ở `lib/mock/flags.ts` (nhánh thật chưa nối: đợt chấm).
 */

export const MOCK_SCENARIOS = ["data", "empty", "error", "loading"] as const;
export type MockScenario = (typeof MOCK_SCENARIOS)[number];

/** Tham số trên URL để xem từng trạng thái của màn: `?mockState=empty|error|loading`. */
export const MOCK_SCENARIO_PARAM = "mockState";

export function readMockScenario(search?: string): MockScenario {
  const source = search ?? (typeof window === "undefined" ? "" : window.location.search);
  const value = new URLSearchParams(source).get(MOCK_SCENARIO_PARAM);
  return (MOCK_SCENARIOS as readonly string[]).includes(value ?? "") ? (value as MockScenario) : "data";
}

/** Lỗi giả lập, cùng dạng `ApiStatusError` mà lớp xử lý lỗi chung (401/403) và màn hình đọc. */
export class MockApiError extends ApiStatusError {
  readonly code?: string;

  constructor(status: number, code?: string, message = "") {
    super(status, message);
    this.name = "MockApiError";
    this.code = code;
  }
}

const DEFAULT_DELAY_MS = 350;

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export interface MockRequestOptions<T> {
  /** Độ trễ giả lập. Mặc định 350ms; trong test là 0. */
  delayMs?: number;
  /** Giá trị trả về khi `?mockState=empty`. Bỏ trống thì kịch bản `empty` trả dữ liệu như thường (màn chi tiết). */
  empty?: () => T;
  /** Bỏ qua `?mockState` (thao tác ghi, để nút bấm không lỗi theo kịch bản xem của trang). */
  ignoreScenario?: boolean;
}

/**
 * Trả dữ liệu mẫu như một lời gọi API: có độ trễ, theo kịch bản trên URL, và trả bản sao để màn hình không sửa được
 * kho dữ liệu mẫu.
 */
export async function mockRequest<T>(produce: () => T, options: MockRequestOptions<T> = {}): Promise<T> {
  const scenario = options.ignoreScenario ? "data" : readMockScenario();
  if (scenario === "loading") return new Promise<T>(() => {});
  const delay = options.delayMs ?? (process.env.NODE_ENV === "test" ? 0 : DEFAULT_DELAY_MS);
  if (delay > 0) await wait(delay);
  if (scenario === "error") throw new MockApiError(500, "MOCK_SCENARIO_ERROR");
  const value = scenario === "empty" && options.empty ? options.empty() : produce();
  return structuredClone(value);
}

/** Kho trong bộ nhớ cho dữ liệu mẫu: tạo, sửa trong phiên được giữ tới khi tải lại trang. */
export function createMockStore<T extends { id: string }>(seed: readonly T[]) {
  let items: T[] = structuredClone([...seed]);
  return {
    all: (): readonly T[] => items,
    get: (id: string): T | undefined => items.find((item) => item.id === id),
    require(id: string): T {
      const found = items.find((item) => item.id === id);
      if (!found) throw new MockApiError(404, "NOT_FOUND");
      return found;
    },
    insert(item: T): T {
      items = [item, ...items];
      return item;
    },
    update(id: string, patch: (current: T) => T): T {
      const current = this.require(id);
      const next = patch(current);
      items = items.map((item) => (item.id === id ? next : item));
      return next;
    },
    reset(): void {
      items = structuredClone([...seed]);
    },
  };
}

/** Cắt trang giống `PageResponse` của backend (trang tính từ 0). */
export function paginate<T>(items: readonly T[], page = 0, size = 20): ApiPage<T> {
  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const safePage = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: items.slice(safePage * size, safePage * size + size),
    page: safePage,
    size,
    totalElements,
    totalPages,
  };
}

let sequence = 0;
/** Mã tạm cho bản ghi tạo trong phiên dữ liệu mẫu. */
export function mockId(prefix: string): string {
  sequence += 1;
  return `${prefix}-mock-${Date.now().toString(36)}-${sequence}`;
}
