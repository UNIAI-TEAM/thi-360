import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamListView } from "../components/exam-list-view";
import { examStore } from "../mock/store";
import { pinInMemoryBackend } from "./in-memory-backend";

pinInMemoryBackend();

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

beforeEach(() => {
  window.history.replaceState(null, "", "/exams");
});

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
});

describe("danh sách bài thi", () => {
  it("hiện bài thi, cột cấu trúc; không có cột Đường lựa chọn; Tạo bài thi mở trang riêng", async () => {
    renderWithProviders(<ExamListView />);
    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    expect(await within(table).findByRole("link", { name: "HSA FULL · Đề luyện số 1" })).toHaveAttribute(
      "href",
      "/exams/ex-hsa-f-001/structure",
    );
    expect(within(table).queryByRole("columnheader", { name: "Đường lựa chọn" })).not.toBeInTheDocument();
    expect(within(table).getAllByText("150 câu · 195 phút").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Tạo bài thi" })).toHaveAttribute("href", "/exams/new");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("tìm theo mã thu hẹp danh sách", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);
    await screen.findByRole("link", { name: "HSA FULL · Đề luyện số 1" });
    await user.type(screen.getByRole("searchbox", { name: "Tìm kiếm" }), "QDA-F-002");
    await waitFor(() => expect(screen.queryByRole("link", { name: "HSA FULL · Đề luyện số 1" })).not.toBeInTheDocument());
    expect(screen.getByRole("link", { name: "QDA FULL · Đề luyện số 2" })).toBeInTheDocument();
  }, 20_000);

  it("sao chép tạo bài Nháp mới với mã -COPY", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);
    await user.click(await screen.findByRole("button", { name: "Sao chép bài thi QDA-F-001" }));
    await waitFor(() => expect(examStore.all().some((exam) => exam.code === "QDA-F-001-COPY")).toBe(true));
    expect(examStore.all().find((exam) => exam.code === "QDA-F-001-COPY")?.status).toBe("DRAFT");
  });

  it("trạng thái rỗng và lỗi theo ?mockState", async () => {
    window.history.replaceState(null, "", "/exams?mockState=empty");
    const { unmount } = renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("heading", { name: "Chưa có bài thi" })).toBeInTheDocument();
    unmount();

    window.history.replaceState(null, "", "/exams?mockState=error");
    renderWithProviders(<ExamListView />);
    expect(await screen.findByRole("heading", { name: "Không tải được danh sách bài thi" })).toBeInTheDocument();
  });
});
