import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";
const SUBMIT = "Tạo bài thi và soạn khối câu hỏi";

let backend: ExamsBackend;

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
});

async function renderPage() {
  const user = userEvent.setup();
  renderWithProviders(<CreateExamPage />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-F-003"));
  return user;
}

const summary = () => screen.getByRole("complementary", { name: "Tóm tắt" });
/** Giá trị của một mục trong khối Tóm tắt (nhãn `dt`, giá trị `dd` cùng một khung). */
const summaryValue = (label: string) => within(summary()).getByText(label).parentElement?.querySelector("dd")?.firstElementChild?.textContent;
const scopeGroup = () => screen.queryByRole("group", { name: /Phạm vi bài thi/ });
const countInputs = () => screen.getAllByRole("textbox", { name: /^Số câu của/ });
const submitButton = () => screen.getAllByRole("button", { name: SUBMIT })[0]!;

describe("trang Tạo bài thi, bước 1: phạm vi theo hồ sơ kỳ thi", () => {
  // Bảng 5.1 của đặc tả: chỉ hiện phạm vi hồ sơ cho phép; kỳ thi một phạm vi thì không có nhóm chọn.
  const CASES: Array<[kind: RegExp, scopes: string[], only: string | null]> = [
    [/^HSA/, ["Toàn bài", "Theo phần", "Theo môn"], null],
    [/^QDA/, ["Toàn bài", "Theo phần", "Theo môn"], null],
    [/^BCA/, ["Toàn bài", "Theo phần", "Theo môn"], null],
    [/^SP2E/, ["Theo phần", "Theo môn"], null],
    [/^SPT/, ["Theo phần", "Theo môn"], null],
    [/^V-ACT/, [], "Toàn bài"],
    [/^THPTQG/, [], "Theo môn"],
  ];

  it.each(CASES)("kỳ thi %s: phạm vi chọn được khớp hồ sơ", async (kind, scopes, only) => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: kind }));
    if (scopes.length === 0) {
      expect(scopeGroup()).toBeNull();
      expect(screen.getByText("Kỳ thi này chỉ có phạm vi này")).toBeInTheDocument();
      expect(screen.getByText(only!)).toBeInTheDocument();
      return;
    }
    const group = scopeGroup()!;
    const names = within(group)
      .getAllByRole("radio")
      .map((radio) => scopes.find((scope) => radio.closest("label")?.textContent?.startsWith(scope)) ?? "?");
    expect(names).toEqual(scopes);
  }, 20_000);

  it("hai thẻ build_mode chỉ hiện khi Theo môn; bài ôn gửi buildMode REVIEW", async () => {
    const user = await renderPage();
    expect(screen.queryByRole("radio", { name: /Tự thiết lập bài ôn/ })).toBeNull();
    await user.click(screen.getByRole("radio", { name: /^SPT/ }));
    expect(screen.getByRole("radio", { name: /Dùng cấu trúc môn SPT/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Tự thiết lập bài ôn/ }));
    // Mẫu bài ôn chưa có trong dữ liệu mẫu: bảng một dòng trống, nhập số câu và thời lượng.
    await user.type(await screen.findByRole("textbox", { name: /^Số câu của/ }), "10");
    await user.type(screen.getByRole("textbox", { name: /^Thời lượng của/ }), "15");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "SPT bài ôn Toán");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(backend.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({
      kind: "SPT",
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      overview: [{ questionCount: 10, durationMinutes: 15 }],
    });
  }, 20_000);
});

describe("trang Tạo bài thi, bước 1: bảng tổng quan", () => {
  it("nạp mẫu mặc định HSA FULL: 3 phần, 150 câu, 195 phút; không có ô chọn phiên bản", async () => {
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Tạo bài thi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("href", "/exams");
    expect(countInputs()).toHaveLength(3);
    expect(summaryValue("Tổng số câu")).toBe("150");
    expect(summaryValue("Tổng thời gian")).toContain("195 phút");
    expect(screen.getByText("Phiên bản cấu trúc chọn ở bước 2")).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /phiên bản/i })).toBeNull();
  });

  it("đổi số câu trên dòng không nạp lại mẫu và cộng lại tổng (R-09)", async () => {
    const user = await renderPage();
    const loads = backend.calls("GET", TEMPLATES_PATH).length;
    const first = countInputs()[0]!;
    await user.clear(first);
    await user.type(first, "45");
    expect(first).toHaveValue("45");
    expect(summaryValue("Tổng số câu")).toBe("145");
    expect(backend.calls("GET", TEMPLATES_PATH)).toHaveLength(loads);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("số câu không hợp lệ báo ngay ở ô và chặn tạo", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA thử");
    await user.clear(countInputs()[0]!);
    await user.type(countInputs()[0]!, "0");
    expect((await screen.findAllByText("Số câu phải là số nguyên dương.")).length).toBeGreaterThan(0);
    await user.click(submitButton());
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("không sửa bảng thì không gửi overview; sửa thì chỉ gửi dòng khác mẫu rồi mở bước 2", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA FULL thử");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledTimes(1));
    const plain = backend.calls("POST", EXAMS_PATH)[0]!.body as Record<string, unknown>;
    expect(plain).toMatchObject({ kind: "HSA", scope: "FULL", name: "HSA FULL thử" });
    expect(plain).not.toHaveProperty("overview");
    expect(plain).not.toHaveProperty("parts");
    expect(routerMock.push).toHaveBeenCalledWith(expect.stringMatching(/^\/exams\/[^/]+\/structure$/));
  }, 20_000);

  it("gửi overview chỉ gồm dòng đã sửa", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA FULL sửa số");
    await user.clear(countInputs()[1]!);
    await user.type(countInputs()[1]!, "40");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const body = backend.calls("POST", EXAMS_PATH)[0]!.body as { overview: Array<Record<string, unknown>> };
    expect(body.overview).toHaveLength(1);
    expect(body.overview[0]).toMatchObject({ questionCount: 40 });
  }, 20_000);
});

describe("trang Tạo bài thi, bước 1: đổi lựa chọn nạp lại bảng", () => {
  it("chưa sửa bảng thì đổi phạm vi nạp ngay, không hỏi", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByRole("radio", { name: /^Theo phần/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Toán" })).toBeInTheDocument();
  });

  it("đã sửa bảng thì hỏi: Giữ lựa chọn cũ giữ nguyên số đã sửa, Đổi và nạp lại thay bằng mẫu mới", async () => {
    const user = await renderPage();
    const first = countInputs()[0]!;
    await user.clear(first);
    await user.type(first, "45");

    await user.click(screen.getByRole("radio", { name: /^QDA/ }));
    const dialog = await screen.findByRole("alert");
    expect(within(dialog).getByText(/Đổi sang/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Giữ lựa chọn cũ" }));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByRole("radio", { name: /^HSA/ })).toBeChecked();
    expect(countInputs()[0]).toHaveValue("45");

    await user.click(screen.getByRole("radio", { name: /^QDA/ }));
    await user.click(within(await screen.findByRole("alert")).getByRole("button", { name: "Đổi và nạp lại" }));
    await waitFor(() => expect(screen.getByRole("radio", { name: /^QDA/ })).toBeChecked());
    expect(screen.queryByRole("alert")).toBeNull();
    expect(countInputs()[0]).toHaveValue("50");
  }, 20_000);

  it("đổi môn hoặc build_mode sau khi sửa bảng cũng hỏi xác nhận", async () => {
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SPT/ }));
    const count = await screen.findByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "30");
    await user.click(screen.getByRole("radio", { name: /Tự thiết lập bài ôn/ }));
    const dialog = await screen.findByRole("alert");
    expect(within(dialog).getByText(/Đổi sang/)).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Dùng cấu trúc môn SPT/ })).toBeChecked();
  }, 20_000);
});

describe("trang Tạo bài thi, bước 1: bốn trạng thái", () => {
  it("lỗi tải mẫu: báo lỗi và thử lại được", async () => {
    const failing = new ExamsBackend();
    failing.server.on("GET", TEMPLATES_PATH, { status: 500, body: { code: "BOOM" } }, { status: 200, body: [] });
    failing.install();
    const user = userEvent.setup();
    renderWithProviders(<CreateExamPage />);
    expect(await screen.findByText("Không tải được mẫu cấu trúc")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(screen.queryByText("Không tải được mẫu cấu trúc")).toBeNull());
  });

  it("đang tải hiện khung chờ có aria-busy", () => {
    renderWithProviders(<CreateExamPage />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
  });
});
