import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api.mock";
import { renderWithProviders } from "@/test/render";
import { ExamListView } from "../components/exam-list-view";
import { FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

const LIST_PATH = "/api/v1/admin/exams";

function row(id: string, name: string, questionCount: number, durationMinutes?: number) {
  return {
    id,
    code: `HSA-${id}`,
    name,
    kind: "HSA",
    scope: "FULL",
    status: "DRAFT",
    hasUnpublishedChanges: true,
    questionCount,
    ...(durationMinutes === undefined ? {} : { durationMinutes }),
    hasEnglishOption: false,
    combinationCount: 0,
    hasEssay: false,
    attemptCount: 0,
    check: { status: "UNCHECKED", issueCount: 0 },
    updatedAt: "2026-11-17T01:00:00Z",
    updatedBy: { id: "u1", name: "Cô Lan" },
  };
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
  window.history.replaceState(null, "", "/exams");
});

describe("danh sách bài thi từ API thật", () => {
  it("không nói 'chung' khi máy chủ không cho biết cách tính thời gian; thiếu thời lượng thì ghi —", async () => {
    new FakeServer()
      .on("GET", LIST_PATH, {
        status: 200,
        body: {
          items: [row("a1", "Bài theo phần", 10, 50), row("b2", "Bài chưa có thời lượng", 8)],
          page: 0,
          size: 20,
          totalItems: 2,
          totalPages: 1,
        },
      })
      .install();

    renderWithProviders(<ExamListView />);
    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    const rowOf = (name: string) => {
      const found = within(table).getByRole("link", { name }).closest("tr");
      if (!found) throw new Error(`không thấy dòng ${name}`);
      return within(found);
    };

    const perPart = await screen.findByRole("link", { name: "Bài theo phần" });
    expect(perPart).toBeInTheDocument();
    expect(rowOf("Bài theo phần").getByText("10 câu · 50 phút")).toBeInTheDocument();
    expect(rowOf("Bài chưa có thời lượng").getByText("8 câu · —")).toBeInTheDocument();
    expect(within(table).queryByText(/chung/)).not.toBeInTheDocument();
    // Máy chủ không trả phần/môn: cột Lựa chọn rơi về gạch ngang, không vỡ.
    expect(rowOf("Bài theo phần").getByText("Không có phần tự chọn")).toBeInTheDocument();
  });
});
