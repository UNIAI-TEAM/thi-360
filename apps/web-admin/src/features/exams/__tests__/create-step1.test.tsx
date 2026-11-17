import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
const referenceMock = vi.hoisted(() => ({ error: null as Error | null }));
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async () => {
    if (referenceMock.error) throw referenceMock.error;
    return getContentReferenceFixture();
  };
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";
const SUBMIT = "Lưu và tiếp tục";

/** Kỳ thi chọn bằng ô chọn (không còn thẻ radio): mở danh sách rồi chọn mục có mã trong ngoặc. */
async function pickKind(user: UserEvent, code: string) {
  await user.click(screen.getByRole("combobox", { name: /^Kỳ thi/ }));
  await user.click(await screen.findByRole("option", { name: new RegExp(`\\(${code}\\)$`) }));
}
const kindSelect = () => screen.getByRole("combobox", { name: /^Kỳ thi/ });

let backend: ExamsBackend;

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
  referenceMock.error = null;
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

const summary = () => screen.getByRole("region", { name: "Tóm tắt" });
/** Giá trị của một mục trong khối Tóm tắt (nhãn `dt`, giá trị `dd` cùng một khung). */
const summaryValue = (label: string) => within(summary()).getByText(label).parentElement?.querySelector("dd")?.firstElementChild?.textContent;
const scopeGroup = () => screen.queryByRole("group", { name: /Phạm vi bài thi/ });
const countInputs = () => screen.getAllByRole("textbox", { name: /^Số câu của/ });
const submitButton = () => screen.getAllByRole("button", { name: SUBMIT })[0]!;

describe("trang Tạo bài thi, bước 1: phạm vi theo hồ sơ kỳ thi", () => {
  // Bảng 5.1 của đặc tả: chỉ hiện phạm vi hồ sơ cho phép; kỳ thi một phạm vi thì không có nhóm chọn.
  const CASES: Array<[kind: string, scopes: string[], only: string | null]> = [
    ["HSA", ["Toàn bài", "Theo phần", "Theo môn"], null],
    ["QDA", ["Toàn bài", "Theo phần", "Theo môn"], null],
    ["BCA", ["Toàn bài", "Theo phần", "Theo môn"], null],
    ["SP2E", ["Theo phần", "Theo môn"], null],
    ["SPT", ["Theo phần", "Theo môn"], null],
    ["V-ACT", [], "Toàn bài"],
    ["THPTQG", [], "Theo môn"],
  ];

  it.each(CASES)("kỳ thi %s: phạm vi chọn được khớp hồ sơ", async (kind, scopes, only) => {
    const user = await renderPage();
    await pickKind(user, kind);
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
    await pickKind(user, "SPT");
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
    expect(countInputs()[0]).toHaveAccessibleDescription("Số câu phải là số nguyên dương.");
    await user.click(submitButton());
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("khung cuộn ngang của bảng là vùng có tên, focus được bằng phím", async () => {
    await renderPage();
    const region = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    expect(region).toHaveAttribute("tabindex", "0");
    expect(within(region).getByRole("table")).toBeInTheDocument();
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

    await pickKind(user, "QDA");
    const dialog = await screen.findByRole("alert");
    expect(within(dialog).getByText(/Đổi sang/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Giữ lựa chọn cũ" }));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(kindSelect()).toHaveTextContent(/\(HSA\)/);
    expect(countInputs()[0]).toHaveValue("45");

    await pickKind(user, "QDA");
    await user.click(within(await screen.findByRole("alert")).getByRole("button", { name: "Đổi và nạp lại" }));
    await waitFor(() => expect(kindSelect()).toHaveTextContent(/\(QDA\)/));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(countInputs()[0]).toHaveValue("50");
  }, 20_000);

  it("đổi môn hoặc build_mode sau khi sửa bảng cũng hỏi xác nhận", async () => {
    const user = await renderPage();
    await pickKind(user, "SPT");
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
  it("lỗi tải mẫu: báo ngay trong khối tổng quan, biểu mẫu vẫn dùng được, thử lại nạp bảng", async () => {
    const failing = new ExamsBackend();
    failing.server.on("GET", TEMPLATES_PATH, { status: 500, body: { code: "BOOM" } }, { status: 200, body: [] });
    failing.install();
    const user = userEvent.setup();
    renderWithProviders(<CreateExamPage />);
    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("Không tải được bảng tổng quan.")).toBeInTheDocument();
    expect(screen.queryByText("Không tải được mẫu cấu trúc")).toBeNull();
    // Bài vẫn tạo được: nhập tên và số câu trên dòng trống.
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA không mẫu");
    expect(countInputs()).toHaveLength(1);

    await user.click(within(alert).getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(screen.queryByText("Không tải được bảng tổng quan.")).toBeNull());
    expect(await screen.findByText(/Không có mẫu mặc định cho lựa chọn này/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("HSA không mẫu");
  }, 20_000);

  it("lỗi tải dữ liệu kỳ thi: cả trang báo lỗi dữ liệu tham chiếu, không nhắc mẫu cấu trúc", async () => {
    referenceMock.error = new Error("boom");
    renderWithProviders(<CreateExamPage />);
    expect(await screen.findByText("Không tải được dữ liệu kỳ thi")).toBeInTheDocument();
    expect(screen.queryByText(/mẫu cấu trúc/)).toBeNull();
    expect(screen.queryByRole("form", { name: "Tạo bài thi" })).toBeNull();
    expect(screen.queryByRole("list", { name: "Các bước tạo bài thi" })).toBeNull();
  });

  it("403: nêu quyền EXAM.MODIFY cần có", async () => {
    const denied = new ExamsBackend();
    denied.server.on("GET", TEMPLATES_PATH, { status: 403, body: { code: "FORBIDDEN" } });
    denied.install();
    renderWithProviders(<CreateExamPage />);
    expect(await screen.findByText("Thiếu quyền tạo bài")).toBeInTheDocument();
    expect(screen.getByText(/Bạn cần quyền EXAM\.MODIFY để tạo bài thi/)).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Tạo bài thi" })).toBeNull();
    expect(screen.queryByRole("list", { name: "Các bước tạo bài thi" })).toBeNull();
  });

  it("mẫu còn đang tải: biểu mẫu hiện sẵn, khối tổng quan hiện khung chờ và không cho tạo", async () => {
    const slow = new ExamsBackend();
    slow.server.on("GET", TEMPLATES_PATH, () => new Promise(() => {}));
    slow.install();
    renderWithProviders(<CreateExamPage />);
    expect(await screen.findByRole("form", { name: "Tạo bài thi" })).toBeInTheDocument();
    expect(screen.getByText("Đang tải bảng tổng quan")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(submitButton()).toBeDisabled();
  });

  it("đang tải hiện khung chờ có aria-busy", () => {
    renderWithProviders(<CreateExamPage />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
  });
});

describe("trang Tạo bài thi, bước 1: khung trang theo bản vẽ TaoBaiThi2B", () => {
  it("thanh bước ở đầu trang: 1 Thông tin chung đang làm, 2 Cấu trúc và ma trận", async () => {
    await renderPage();
    const steps = screen.getByRole("list", { name: "Các bước tạo bài thi" });
    const items = within(steps).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("1Thông tin chung");
    expect(items[1]).toHaveTextContent("2Cấu trúc và ma trận");
    expect(items[0]!.querySelector('[aria-current="step"]')).not.toBeNull();
    expect(items[1]!.querySelector('[aria-current="step"]')).toBeNull();
  });

  it("kỳ thi là ô chọn trong khối Thông tin chung; đổi kỳ thi nạp lại bảng", async () => {
    const user = await renderPage();
    expect(screen.queryByRole("radio", { name: /^HSA/ })).toBeNull();
    expect(kindSelect()).toHaveTextContent(/\(HSA\)/);
    const info = screen.getByRole("region", { name: "Thông tin chung" });
    expect(within(info).getByRole("combobox", { name: /^Kỳ thi/ })).toBeInTheDocument();
    await pickKind(user, "QDA");
    await waitFor(() => expect(kindSelect()).toHaveTextContent(/\(QDA\)/));
    expect(countInputs()[0]).toHaveValue("50");
  }, 20_000);

  it("tóm tắt nằm dưới bảng tổng quan (không còn cột phải); Hủy về danh sách, Lưu và tiếp tục ở cuối", async () => {
    await renderPage();
    expect(screen.queryByRole("complementary")).toBeNull();
    const table = screen.getByRole("table");
    expect(table.compareDocumentPosition(summary()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const cancel = screen.getByRole("link", { name: "Hủy" });
    expect(cancel).toHaveAttribute("href", "/exams");
    const buttons = screen.getAllByRole("button", { name: SUBMIT });
    expect(buttons).toHaveLength(1);
    expect(summary().compareDocumentPosition(cancel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(cancel.compareDocumentPosition(buttons[0]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Tạo bài thi và soạn khối câu hỏi" })).toBeNull();
  });

  it("form đã sửa: bấm Hủy mở hộp thoại rời trang chưa lưu, Ở lại giữ nguyên trang", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "Bài đang soạn");
    await user.click(screen.getByRole("link", { name: "Hủy" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Rời trang khi chưa lưu?")).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("Bài đang soạn");
  });

  it("giữ ô Hướng dẫn làm bài (trình soạn văn bản)", async () => {
    await renderPage();
    expect(screen.getAllByText("Hướng dẫn làm bài").length).toBeGreaterThan(0);
  });
});
