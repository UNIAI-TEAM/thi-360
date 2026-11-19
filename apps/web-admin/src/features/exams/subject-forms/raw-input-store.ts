/**
 * Trạng thái nhập thô theo phiên của view (FTM R-15, plan B1): giá trị đã gõ mà chưa ghi được vào bản nháp, ví dụ tỷ lệ quy ra
 * không chia hết cỡ chùm. Khóa theo `sectionId`/`blockId`/`field`, không theo component đang mở, nên đóng màn sửa dòng hay
 * chuyển môn không làm mất. Không vào DTO và không bao giờ vào thân `PUT`.
 *
 * Mục có mặt trong kho nghĩa là giá trị đó **sai** (hình thức nhập đã bị chặn lưu): biểu mẫu ghi mục khi người dùng gõ giá trị
 * chưa hợp lệ và xóa mục khi giá trị trở nên hợp lệ và được ghi vào bản nháp.
 */
export interface RawInputEntry {
  sectionId: string;
  /** `null`: ô của cả môn (Cấu trúc câu, bảng phân bổ). */
  blockId: string | null;
  /** Tên ô, dùng để đưa focus tới đúng ô (`data-form-field` hoặc dòng của `blockId`). */
  field: string;
  /** Chữ người dùng đã gõ. */
  text: string;
  /** Mã lỗi để biểu mẫu hiện thông báo tại ô. */
  code: string;
}

export interface RawInputStore {
  /** Ghi (hoặc thay) mục của ô; mỗi ô một mục. */
  set(entry: RawInputEntry): void;
  /** Xóa mục của ô (giá trị đã hợp lệ). */
  clear(sectionId: string, blockId: string | null, field: string): void;
  /** "Bỏ thay đổi" và nạp mẫu đã xác nhận: bỏ hết, vì cả bản nháp bị bỏ. */
  clearAll(): void;
  list(): readonly RawInputEntry[];
  get(sectionId: string, blockId: string | null, field: string): RawInputEntry | null;
  hasRawInputs(): boolean;
  /** Cho `useSyncExternalStore`: đổi tham chiếu mỗi khi kho đổi. */
  subscribe(listener: () => void): () => void;
  getSnapshot(): readonly RawInputEntry[];
}

const keyOf = (sectionId: string, blockId: string | null, field: string) => `${sectionId}\u0000${blockId ?? ""}\u0000${field}`;

export function createRawInputStore(): RawInputStore {
  const entries = new Map<string, RawInputEntry>();
  const listeners = new Set<() => void>();
  let snapshot: readonly RawInputEntry[] = [];

  const publish = () => {
    snapshot = [...entries.values()];
    for (const listener of listeners) listener();
  };

  return {
    set(entry) {
      entries.set(keyOf(entry.sectionId, entry.blockId, entry.field), entry);
      publish();
    },
    clear(sectionId, blockId, field) {
      if (entries.delete(keyOf(sectionId, blockId, field))) publish();
    },
    clearAll() {
      if (entries.size === 0) return;
      entries.clear();
      publish();
    },
    list: () => snapshot,
    get: (sectionId, blockId, field) => entries.get(keyOf(sectionId, blockId, field)) ?? null,
    hasRawInputs: () => entries.size > 0,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    getSnapshot: () => snapshot,
  };
}
