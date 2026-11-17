import { beforeEach, describe, expect, it, vi } from "vitest";

const ui = vi.hoisted(() => {
  let next = 0;
  return {
    error: vi.fn(() => ++next),
    dismiss: vi.fn(),
  };
});

vi.mock("@thi360/ui", () => ({ notify: { error: ui.error }, toast: { dismiss: ui.dismiss } }));

import { dismissImageUploadToasts, notifyImageUploadError } from "../editor/upload-toasts";

describe("toast lỗi tải ảnh (F-09)", () => {
  beforeEach(() => {
    ui.error.mockClear();
    ui.dismiss.mockClear();
    dismissImageUploadToasts();
    ui.dismiss.mockClear();
  });

  it("chỉ đóng các toast lỗi tải ảnh do chính nó tạo, rồi quên chúng", () => {
    notifyImageUploadError("a");
    notifyImageUploadError("b");
    dismissImageUploadToasts();
    expect(ui.dismiss.mock.calls.map(([id]) => id)).toEqual([1, 2]);
    dismissImageUploadToasts();
    expect(ui.dismiss).toHaveBeenCalledTimes(2);
  });
});
