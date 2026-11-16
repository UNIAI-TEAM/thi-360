import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamVersionsView } from "../components/exam-versions-view";
import { getExam } from "../api";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReference } = await import("@/features/content/api.mock");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex-hsa-f-001/versions",
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const HSA = "ex-hsa-f-001";
const EN = "opt-30";
const SCIENCE = "opt-sci-1";

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
  const backend = new ExamsBackend();
  // Kiểm tra gần nhất: đường Tiếng Anh hợp lệ, đường Khoa học thiếu nguồn (hộp thoại chỉ cho chọn đường hợp lệ).
  backend.seedCheck(HSA, {
    examId: HSA,
    status: "INVALID",
    stale: false,
    checkedAt: "2026-11-15T09:28:00+07:00",
    durationSeconds: 31,
    timeLimitSeconds: 30,
    issueCount: 2,
    paths: [
      backend.checkPath(HSA, EN, "VALID"),
      backend.checkPath(HSA, SCIENCE, "INVALID"),
    ],
  });
  backend.install();
  window.history.replaceState(null, "", "/");
});

async function openPublishDialog() {
  const user = userEvent.setup();
  renderWithProviders(<ExamVersionsView examId={HSA} />);
  await user.click(await screen.findByRole("button", { name: "Công bố" }));
  const dialog = await screen.findByRole("dialog");
  return { user, dialog };
}

describe("ExamVersionsView", () => {
  it("liet ke lich su cong bo, moi nhat truoc", async () => {
    renderWithProviders(<ExamVersionsView examId={HSA} />);
    const table = await screen.findByRole("table", { name: "Các phiên bản đã công bố của HSA-F-001" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows.map((row) => within(row).getAllByRole("cell")[0]?.textContent)).toEqual([
      expect.stringContaining("v2"),
      expect.stringContaining("v1"),
    ]);
    expect(screen.getByRole("heading", { name: "Bản đang làm việc" })).toBeInTheDocument();
  });

  it("chi cho chon duong hop le trong hop thoai cong bo", async () => {
    const { dialog } = await openPublishDialog();
    expect(within(dialog).getByRole("heading", { name: "Công bố bản sửa thành v3?" })).toBeInTheDocument();
    const boxes = within(dialog).getAllByRole("checkbox");
    expect(boxes).toHaveLength(2);
    expect(boxes.filter((box) => box.getAttribute("aria-checked") === "true")).toHaveLength(1);
    expect(boxes.filter((box) => box.hasAttribute("disabled"))).toHaveLength(1);
    expect(within(dialog).getByRole("checkbox", { name: /^Khoa học/ })).toBeDisabled();
  });

  it("cong bo duong hop le tao v3, duong con lai giu ban cu", async () => {
    const { user, dialog } = await openPublishDialog();
    await user.click(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const table = await screen.findByRole("table", { name: "Các phiên bản đã công bố của HSA-F-001" });
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(4));
    const exam = await getExam(HSA);
    expect(exam.versions.some((item) => item.version === 3)).toBe(true);
    expect(exam.hasUnpublishedChanges).toBe(true);
  });

  it("bao loi khi ghi chu qua dai", async () => {
    const { user, dialog } = await openPublishDialog();
    fireEvent.change(within(dialog).getByRole("textbox", { name: /Ghi chú công bố/ }), {
      target: { value: "a".repeat(501) },
    });
    await user.click(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" }));
    expect(await within(dialog).findByText("Ghi chú tối đa 500 ký tự.")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
