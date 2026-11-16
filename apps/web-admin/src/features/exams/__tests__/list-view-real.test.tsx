import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamListView } from "../components/exam-list-view";
import { examDetailWire, FakeServer, type Recorded } from "./fake-exams-server";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

const LIST_PATH = "/api/v1/admin/exams";
const HSA_ID = "00000000-0000-4000-8000-000000000001";
const QDA_ID = "00000000-0000-4000-8000-000000000002";

function row(id: string, code: string, name: string, questionCount: number, durationMinutes?: number) {
  return {
    id,
    code,
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

const ROWS = [row(HSA_ID, "HSA-F-001", "HSA FULL · Đề luyện số 1", 150, 195), row(QDA_ID, "QDA-F-002", "QDA FULL · Đề luyện số 2", 120, 150)];

function page(items: unknown[]) {
  return { status: 200, body: { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length > 0 ? 1 : 0 } };
}

/** Máy chủ giả lọc theo `q` như máy chủ thật (mã hoặc tên chứa chuỗi tìm). */
function listServer() {
  return new FakeServer()
    .on("GET", LIST_PATH, (request: Recorded) => {
      const q = request.url.searchParams.get("q")?.toLowerCase();
      return page(q ? ROWS.filter((item) => `${item.code} ${item.name}`.toLowerCase().includes(q)) : ROWS);
    })
    .install();
}

beforeEach(() => {
  window.history.replaceState(null, "", "/exams");
});

describe("danh sách bài thi, nhánh thật", () => {
  it("hiện bài thi và cột cấu trúc; không có cột Đường lựa chọn; Tạo bài thi mở trang riêng", async () => {
    listServer();
    renderWithProviders(<ExamListView />);

    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    expect(await within(table).findByRole("link", { name: "HSA FULL · Đề luyện số 1" })).toHaveAttribute(
      "href",
      `/exams/${HSA_ID}/structure`,
    );
    expect(within(table).queryByRole("columnheader", { name: "Đường lựa chọn" })).not.toBeInTheDocument();
    // Máy chủ không cho biết cách tính thời gian: ghi thời lượng trung tính, không nói "chung".
    expect(within(table).getAllByText("150 câu · 195 phút").length).toBeGreaterThan(0);
    expect(within(table).queryByText(/chung/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tạo bài thi" })).toHaveAttribute("href", "/exams/new");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("thiếu thời lượng thì ghi —; thiếu phần và môn thì cột Lựa chọn rơi về gạch ngang, không vỡ", async () => {
    new FakeServer().on("GET", LIST_PATH, page([row("a1", "X-1", "Bài chưa có thời lượng", 8), row("b2", "X-2", "Bài theo phần", 10, 50)])).install();
    renderWithProviders(<ExamListView />);

    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    const rowOf = (name: string) => {
      const found = within(table).getByRole("link", { name }).closest("tr");
      if (!found) throw new Error(`không thấy dòng ${name}`);
      return within(found);
    };
    await within(table).findByRole("link", { name: "Bài theo phần" });

    expect(rowOf("Bài chưa có thời lượng").getByText("8 câu · —")).toBeInTheDocument();
    expect(rowOf("Bài theo phần").getByText("10 câu · 50 phút")).toBeInTheDocument();
    expect(rowOf("Bài theo phần").getByText("Không có phần tự chọn")).toBeInTheDocument();
  });

  it("tìm theo mã gửi q cho máy chủ và thu hẹp danh sách", async () => {
    const server = listServer();
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);
    await screen.findByRole("link", { name: "HSA FULL · Đề luyện số 1" });

    await user.type(screen.getByRole("searchbox", { name: "Tìm kiếm" }), "QDA-F-002");

    await waitFor(() => expect(screen.queryByRole("link", { name: "HSA FULL · Đề luyện số 1" })).not.toBeInTheDocument());
    expect(screen.getByRole("link", { name: "QDA FULL · Đề luyện số 2" })).toBeInTheDocument();
    expect(server.calls("GET", LIST_PATH).at(-1)!.url.searchParams.get("q")).toBe("QDA-F-002");
  }, 20_000);

  it("lọc theo bảy kỳ thi của danh mục và theo môn (gửi subject cho máy chủ)", async () => {
    const server = listServer();
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);
    await screen.findByRole("link", { name: "HSA FULL · Đề luyện số 1" });

    await user.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    const kindOptions = await screen.findAllByRole("option");
    expect(kindOptions.map((option) => option.textContent)).toEqual(["Tất cả", "HSA", "QDA", "BCA", "V-ACT", "SPT", "SP2E", "THPTQG"]);
    await user.keyboard("{Escape}");

    await user.click(screen.getByRole("combobox", { name: "Môn" }));
    await user.click(await screen.findByRole("option", { name: "Tin học" }));
    await waitFor(() => expect(server.calls("GET", LIST_PATH).at(-1)!.url.searchParams.get("subject")).toBe("INFORMATICS"));
  }, 20_000);

  it("sao chép gửi POST /duplicate với tên bản sao", async () => {
    const server = listServer().on("POST", `${LIST_PATH}/:id/duplicate`, {
      status: 201,
      body: examDetailWire({ id: "00000000-0000-4000-8000-000000000009", code: "QDA-F-002-COPY" }),
    });
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);

    await user.click(await screen.findByRole("button", { name: "Sao chép bài thi QDA-F-002" }));

    await waitFor(() => expect(server.calls("POST", `${LIST_PATH}/${QDA_ID}/duplicate`)).toHaveLength(1));
    expect(server.calls("POST", `${LIST_PATH}/${QDA_ID}/duplicate`)[0]!.body).toEqual({ name: "QDA FULL · Đề luyện số 2 (bản sao)" });
  });

  it("danh sách rỗng và lỗi máy chủ có trạng thái riêng", async () => {
    new FakeServer().on("GET", LIST_PATH, page([])).install();
    const { unmount } = renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("heading", { name: "Chưa có bài thi" })).toBeInTheDocument();
    unmount();

    new FakeServer().on("GET", LIST_PATH, { status: 500, body: { code: "INTERNAL" } }).install();
    renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("heading", { name: "Không tải được danh sách bài thi" })).toBeInTheDocument();
  });
});
