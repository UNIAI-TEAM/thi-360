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
