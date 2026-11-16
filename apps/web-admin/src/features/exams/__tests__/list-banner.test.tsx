import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api.mock";
import { renderWithProviders } from "@/test/render";
import { ExamListView } from "../components/exam-list-view";
import { examStore } from "../mock/store";
import { FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

// F2 (visual-1): dải "Dữ liệu mẫu / chưa nối backend" chỉ được hiện khi màn thật sự chạy kho mẫu
// (CONTENT_MOCK bật); trên dữ liệu thật dải này nói sai.
describe("danh sách bài thi: dải dữ liệu mẫu theo cờ CONTENT_MOCK", () => {
  it("cờ tắt (API thật): không có dải dữ liệu mẫu", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    new FakeServer()
      .on("GET", "/api/v1/admin/exams", {
        status: 200,
        body: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 },
      })
      .install();

    renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("heading", { name: "Chưa có bài thi" })).toBeInTheDocument();
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });

  it("cờ bật (kho mẫu): hiện dải dữ liệu mẫu", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "true");
    examStore.reset();

    renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("table", { name: "Danh sách bài thi" })).toBeInTheDocument();
    expect(screen.getByRole("note")).toBeInTheDocument();
  });
});
