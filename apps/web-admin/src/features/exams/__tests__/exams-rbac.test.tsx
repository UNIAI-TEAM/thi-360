import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NewExamPage from "@/app/(shell)/exams/new/page";
import { ExamGeneralForm } from "../components/exam-general-form";
import { ExamListView } from "../components/exam-list-view";
import { EXAMS_FIXTURE } from "./exam-fixture";
import { ExamsBackend } from "./fake-exams-backend";
import { FakeServer } from "./fake-exams-server";
import { renderExamsUi } from "./render-exams-ui";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn(), redirect: vi.fn((href: string) => {
  throw new Error(`NEXT_REDIRECT:${href}`);
}) }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => window.location.pathname,
  redirect: routerMock.redirect,
}));

let backend: ExamsBackend;

beforeEach(() => {
  routerMock.push.mockReset();
  routerMock.redirect.mockClear();
  backend = new ExamsBackend();
  backend.install();
});

describe("RBAC, điều hướng danh sách /exams và redirect /exams/new?exam=<id> (AC-05..AC-08)", () => {
  it("AC-05 (S1-3): /exams/new?exam=abc-123 gọi server redirect('/exams/abc-123') ngay trước PermissionGate", async () => {
    await expect(NewExamPage({ searchParams: Promise.resolve({ exam: "abc-123" }) })).rejects.toThrow(
      "NEXT_REDIRECT:/exams/abc-123",
    );
    expect(routerMock.redirect).toHaveBeenCalledWith("/exams/abc-123");
  });

  it("AC-08: tài khoản chỉ có EXAM.VIEW mở /exams/new (không có ?exam=) hiện ForbiddenView (403)", async () => {
    const element = await NewExamPage({ searchParams: Promise.resolve({}) });
    renderExamsUi(element, { permissions: ["EXAM.VIEW"] });
    expect(await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Tạo bài thi" })).toBeNull();
  });

  it("AC-08: tài khoản chỉ có EXAM.VIEW mở /exams/<id> xem chỉ đọc, ẩn Tự sinh mã / Lưu / Hoàn tác và không gọi suggested-code", async () => {
    const exam = backend.app("ex-hsa-f-001");
    window.history.replaceState(null, "", `/exams/${exam.id}`);
    renderExamsUi(<ExamGeneralForm mode="edit" examId={exam.id} />, { permissions: ["EXAM.VIEW"] });

    await screen.findByRole("form", { name: "Thông tin bài thi" });
    const table = await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });

    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toBeDisabled();
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: /^Kỳ thi/ })).toBeDisabled();
    expect(screen.getByRole("group", { name: /Phạm vi bài thi/ })).toBeDisabled();
    for (const input of within(table).getAllByRole("textbox")) {
      expect(input).toHaveAttribute("readonly");
    }

    expect(screen.queryByRole("button", { name: "Tự sinh mã" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Lưu thông tin" })).toBeNull();
    expect(screen.queryByText("Hoàn tác")).toBeNull();
    expect(backend.calls("GET", "/api/v1/admin/exams/suggested-code")).toHaveLength(0);
  });

  it("AC-06, AC-07, AC-08: danh sách /exams với chỉ EXAM.VIEW ẩn Tạo bài thi (PageHeader + EmptyState) & Sao chép, hiện Xem bài thi <mã>, menu ⋯ đủ 5 tab theo thứ tự và ẩn Ngừng bán khi thiếu EXAM.PUBLISH", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", "/exams");
    const view = renderExamsUi(<ExamListView />, { permissions: ["EXAM.VIEW"] });

    const published = EXAMS_FIXTURE.exams.find((item) => item.status === "PUBLISHED")!;
    const titleLinks = await screen.findAllByRole("link", { name: published.name });
    for (const link of titleLinks) {
      expect(link).toHaveAttribute("href", `/exams/${published.id}`);
    }

    // Ẩn Tạo bài thi trên PageHeader và ẩn Sao chép.
    expect(screen.queryByRole("link", { name: "Tạo bài thi" })).toBeNull();
    expect(screen.queryByRole("button", { name: `Sao chép bài thi ${published.code}` })).toBeNull();

    // Nút hành động chính là Xem bài thi <mã>, bấm điều hướng tới /exams/<id>.
    const viewBtns = screen.getAllByRole("button", { name: `Xem bài thi ${published.code}` });
    await user.click(viewBtns[0]!);
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${published.id}`);

    // Menu ⋯ liệt kê đúng 5 tab theo thứ tự và ẩn Ngừng bán khi thiếu EXAM.PUBLISH; bấm Thông tin bán mở /exams/<id>/listing.
    const moreBtns = screen.getAllByRole("button", { name: `Thao tác khác với bài thi ${published.code}` });
    await user.click(moreBtns[0]!);
    const menu = await screen.findByRole("menu");
    const items = within(menu).getAllByRole("menuitem").map((item) => item.textContent?.trim());
    expect(items).toEqual([
      "Thông tin chung",
      "Cấu trúc và ma trận",
      "Thông tin bán",
      "Kiểm tra và xem trước",
      "Phiên bản và công bố",
    ]);
    await user.click(within(menu).getByRole("menuitem", { name: "Thông tin bán" }));
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${published.id}/listing`);
    view.unmount();

    // EmptyState khi danh sách hoàn toàn rỗng cũng ẩn nút Tạo bài thi đối với tài khoản chỉ có EXAM.VIEW.
    new FakeServer()
      .on("GET", "/api/v1/admin/exams", {
        status: 200,
        body: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 },
      })
      .install();
    renderExamsUi(<ExamListView />, { permissions: ["EXAM.VIEW"] });
    expect(await screen.findByRole("heading", { name: "Chưa có bài thi" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo bài thi" })).toBeNull();
  });

  it("AC-06, AC-07: tài khoản có EXAM.MODIFY + EXAM.PUBLISH thấy Tạo bài thi, Sửa bài thi <mã>, Sao chép và Ngừng bán trên bài PUBLISHED", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", "/exams");
    renderExamsUi(<ExamListView />, { permissions: ["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"] });

    const published = EXAMS_FIXTURE.exams.find((item) => item.status === "PUBLISHED")!;
    await screen.findAllByRole("link", { name: published.name });

    expect(screen.getByRole("link", { name: "Tạo bài thi" })).toHaveAttribute("href", "/exams/new");
    expect(screen.getAllByRole("button", { name: `Sửa bài thi ${published.code}` }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: `Sao chép bài thi ${published.code}` }).length).toBeGreaterThan(0);

    const moreBtns = screen.getAllByRole("button", { name: `Thao tác khác với bài thi ${published.code}` });
    await user.click(moreBtns[0]!);
    const menu = await screen.findByRole("menu");
    const items = within(menu).getAllByRole("menuitem").map((item) => item.textContent?.trim());
    expect(items).toEqual([
      "Thông tin chung",
      "Cấu trúc và ma trận",
      "Thông tin bán",
      "Kiểm tra và xem trước",
      "Phiên bản và công bố",
      "Ngừng bán",
    ]);

    await user.click(within(menu).getByRole("menuitem", { name: "Thông tin chung" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${published.id}`));
  });
});
