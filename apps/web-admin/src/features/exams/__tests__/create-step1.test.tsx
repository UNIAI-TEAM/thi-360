import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ContentReference } from "@/features/content/types";
import en from "@/messages/en.json";
import { ExamEditorLayout } from "../components/exam-editor-layout";
import { ExamGeneralForm } from "../components/exam-general-form";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, templateUuid } from "./fake-exams-backend";
import { examDetailWire } from "./fake-exams-server";
import { renderExamsUi } from "./render-exams-ui";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
const referenceMock = vi.hoisted(() => ({
  error: null as Error | null,
  /** Sửa danh mục trước khi trả (hồ sơ kỳ thi mới của BE-13 chưa có trong dữ liệu mẫu). */
  patch: null as ((reference: ContentReference, locale: string) => void) | null,
}));
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: "vi" | "en") => {
    if (referenceMock.error) throw referenceMock.error;
    const reference = await getContentReferenceFixture(locale);
    referenceMock.patch?.(reference, locale ?? "vi");
    return reference;
  };
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";
const SUBMIT = "Tiếp tục: Cấu trúc và ma trận";

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
  referenceMock.patch = null;
  backend = new ExamsBackend();
  backend.install();
});

async function renderPage() {
  const user = userEvent.setup();
  renderExamsUi(
    <ExamEditorLayout>
      <ExamGeneralForm mode="create" />
    </ExamEditorLayout>,
  );
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
    expect(screen.queryByText("Phiên bản cấu trúc chọn ở bước 2")).toBeNull();
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

  // R1-01: `id` của phần, môn trong mẫu (UUID từ BE-6) đi thẳng vào `partId`/`sectionId` của `overview`.
  it("FULL: dòng phần đã sửa gửi partId là UUID của phần trong mẫu", async () => {
    const user = await renderPage();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA FULL sửa phần");
    await user.clear(countInputs()[1]!);
    await user.type(countInputs()[1]!, "40");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const body = backend.calls("POST", EXAMS_PATH)[0]!.body as { overview: Array<Record<string, unknown>> };
    expect(body.overview).toEqual([{ partId: templateUuid("HSA_FULL/part/1"), questionCount: 40 }]);
  }, 20_000);

  it("FULL: dòng môn đã sửa gửi cả partId và sectionId của mẫu", async () => {
    const user = await renderPage();
    await pickKind(user, "BCA");
    const sectionCount = (await screen.findAllByRole("textbox", { name: /^Số câu của/ })).at(-2)!;
    await user.clear(sectionCount);
    await user.type(sectionCount, "7");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "BCA FULL sửa môn");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const body = backend.calls("POST", EXAMS_PATH)[0]!.body as { overview: Array<Record<string, unknown>> };
    expect(body.overview).toHaveLength(1);
    expect(body.overview[0]).toMatchObject({
      partId: templateUuid("BCA_FULL/part/1"),
      sectionId: templateUuid("BCA_FULL/part/1/section/2"),
      questionCount: 7,
    });
  }, 20_000);

  it.each([
    ["Theo phần", async (user: UserEvent) => user.click(screen.getByRole("radio", { name: /^Theo phần/ }))],
    ["Theo môn", async (user: UserEvent) => pickKind(user, "SPT")],
  ])("%s: dòng đã sửa gửi partId là UUID của phần trong mẫu", async (scope, choose) => {
    const user = await renderPage();
    await choose(user);
    const count = (await screen.findAllByRole("textbox", { name: /^Số câu của/ }))[0]!;
    await user.clear(count);
    await user.type(count, "5");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), `${scope} sửa số`);
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const body = backend.calls("POST", EXAMS_PATH)[0]!.body as { overview: Array<{ partId?: string; questionCount?: number }> };
    const templateIds = FIXTURE_TEMPLATES.flatMap((item) => item.parts.map((_, index) => templateUuid(`${item.code}/part/${index}`)));
    expect(body.overview).toHaveLength(1);
    expect(body.overview[0]!.questionCount).toBe(5);
    expect(templateIds).toContain(body.overview[0]!.partId);
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
  const renderCreateShell = () =>
    renderExamsUi(
      <ExamEditorLayout>
        <ExamGeneralForm mode="create" />
      </ExamEditorLayout>,
    );

  it("lỗi tải mẫu: báo ngay trong khối tổng quan, biểu mẫu vẫn dùng được, thử lại nạp bảng", async () => {
    const failing = new ExamsBackend();
    failing.server.on("GET", TEMPLATES_PATH, { status: 500, body: { code: "BOOM" } }, { status: 200, body: [] });
    failing.install();
    const user = userEvent.setup();
    renderCreateShell();
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
    renderCreateShell();
    expect(await screen.findByText("Không tải được dữ liệu kỳ thi")).toBeInTheDocument();
    expect(screen.queryByText(/mẫu cấu trúc/)).toBeNull();
    expect(screen.queryByRole("form", { name: "Tạo bài thi" })).toBeNull();
    expect(screen.queryByRole("list", { name: "Các bước tạo bài thi" })).toBeNull();
  });

  it("403: nêu quyền EXAM.MODIFY cần có", async () => {
    const denied = new ExamsBackend();
    denied.server.on("GET", TEMPLATES_PATH, { status: 403, body: { code: "FORBIDDEN" } });
    denied.install();
    renderCreateShell();
    expect(await screen.findByText("Thiếu quyền tạo bài")).toBeInTheDocument();
    expect(screen.getByText(/Bạn cần quyền EXAM\.MODIFY để tạo bài thi/)).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Tạo bài thi" })).toBeNull();
    expect(screen.queryByRole("list", { name: "Các bước tạo bài thi" })).toBeNull();
  });

  it("mẫu còn đang tải: biểu mẫu hiện sẵn, khối tổng quan hiện khung chờ và không cho tạo", async () => {
    const slow = new ExamsBackend();
    slow.server.on("GET", TEMPLATES_PATH, () => new Promise(() => {}));
    slow.install();
    renderCreateShell();
    expect(await screen.findByRole("form", { name: "Tạo bài thi" })).toBeInTheDocument();
    expect(screen.getByText("Đang tải bảng tổng quan")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(submitButton()).toBeDisabled();
  });

  it("đang tải hiện khung chờ có aria-busy", () => {
    renderCreateShell();
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
  });
});

describe("trang Tạo bài thi, bước 1: khung trang theo bản vẽ TaoBaiThi2B", () => {
  it("thanh 4 tab ở đầu trang: 1. Thông tin chung đang chọn, 3 tab sau khóa tĩnh (UNI-1720)", async () => {
    await renderPage();
    const nav = screen.getByRole("navigation", { name: "Các phần của bài thi" });
    expect(within(nav).getByRole("link", { name: "1. Thông tin chung" })).toHaveAttribute("aria-current", "page");
    for (const label of ["2. Cấu trúc và ma trận", "3. Thông tin bán", "4. Kiểm tra, xem trước và công bố"]) {
      const disabledTab = within(nav).getByText(label);
      expect(disabledTab).toHaveAttribute("aria-disabled", "true");
      expect(disabledTab.closest("a")).toBeNull();
    }
  });

  it("kỳ thi là ô chọn đặt đầu khối Phạm vi bài thi; đổi kỳ thi nạp lại bảng", async () => {
    const user = await renderPage();
    expect(screen.queryByRole("radio", { name: /^HSA/ })).toBeNull();
    expect(kindSelect()).toHaveTextContent(/\(HSA\)/);
    const scope = screen.getByRole("region", { name: "Phạm vi bài thi" });
    expect(within(scope).getByRole("combobox", { name: /^Kỳ thi/ })).toBeInTheDocument();
    await pickKind(user, "QDA");
    await waitFor(() => expect(kindSelect()).toHaveTextContent(/\(QDA\)/));
    expect(countInputs()[0]).toHaveValue("50");
  }, 20_000);

  it("Hủy, Lưu và tiếp tục ở hàng thao tác dính đầu biểu mẫu (trên bảng tổng quan), Tóm tắt đứng sau các nút (UNI-2876)", async () => {
    await renderPage();
    const table = screen.getByRole("table");
    const cancel = screen.getByRole("link", { name: "Hủy" });
    expect(cancel).toHaveAttribute("href", "/exams");
    const buttons = screen.getAllByRole("button", { name: SUBMIT });
    expect(buttons).toHaveLength(1);
    const form = screen.getByRole("form", { name: "Tạo bài thi" });
    expect(form).toContainElement(cancel);
    expect(form).toContainElement(buttons[0]!);
    expect(form).not.toContainElement(summary());
    expect(screen.getByTestId("general-toolbar")).toContainElement(cancel);
    expect(screen.getByTestId("general-toolbar")).toContainElement(buttons[0]!);
    expect(buttons[0]!.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(cancel.compareDocumentPosition(buttons[0]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(buttons[0]!.compareDocumentPosition(summary()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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

/* ------------------------------------------------ V-ACT, THPTQG (FE-5, UNI-2677) ------------------------------ */

type Wire = Record<string, unknown>;
const block = (name: string, count: number, extra: Wire = {}) => ({ id: null, name, questionType: "SINGLE_CHOICE", questionCount: count, ...extra });
const section = (subjectCode: string, count: number, extra: Wire = {}, blocks: Wire[] = [block("Trắc nghiệm", count)]) => ({
  id: null,
  subjectCode,
  questionCount: count,
  blocks,
  ...extra,
});
const competency = (code: string) => ({ knowledgeScope: { competencyCodes: [code] } });
const part = (order: number, name: string, count: number, minutes: number | null, sections: Wire[], extra: Wire = {}) => ({
  id: null,
  order,
  name,
  durationMinutes: minutes,
  questionCount: count,
  participation: { mode: "ALL" },
  mode: "FIXED",
  sections,
  ...extra,
});
const wireTemplate = (code: string, fields: Wire, parts: Wire[]): Wire => ({
  active: true,
  scope: "SUBJECT",
  timing: "SHARED",
  buildMode: "STANDARD",
  isDefault: true,
  publishable: true,
  fixedCounts: false,
  ...fields,
  code,
  structure: { parts },
  parts: [],
});

const VIETNAMESE = section("VIETNAMESE", 30, competency("VACT-NN-TV"));
const ENGLISH = section("ANH", 30, competency("VACT-NN-TA"));
const MATH = section("TOAN", 30, competency("VACT-TOAN"));
const LOGIC = section("SCIENTIFIC_THINKING", 12, competency("VACT-TD-LOGIC"));
const REASONING = section("SCIENTIFIC_THINKING", 18, competency("VACT-TD-SL"));
const PRIVATE = { timing: "SHARED", durationMinutes: null, fixedCounts: false };
const VACT_TEMPLATES = [
  wireTemplate("VACT_FULL", { kindCode: "VACT", scope: "FULL", durationMinutes: 150, maxScore: 1200, fixedCounts: true }, [
    part(1, "Sử dụng ngôn ngữ", 60, null, [VIETNAMESE, ENGLISH], { maxPoints: 600 }),
    part(2, "Toán học", 30, null, [MATH], { maxPoints: 300 }),
    part(3, "Tư duy khoa học", 30, null, [LOGIC, REASONING], { maxPoints: 300 }),
  ]),
  wireTemplate("VACT_PART_LANGUAGE", { kindCode: "VACT", scope: "PART", partCode: "LANGUAGE", maxScore: 600, ...PRIVATE }, [
    part(1, "Sử dụng ngôn ngữ", 60, null, [VIETNAMESE, ENGLISH], { maxPoints: 600 }),
  ]),
  wireTemplate("VACT_PART_THINKING", { kindCode: "VACT", scope: "PART", partCode: "THINKING", maxScore: 300, ...PRIVATE }, [
    part(1, "Tư duy khoa học", 30, null, [LOGIC, REASONING], { maxPoints: 300 }),
  ]),
  wireTemplate("VACT_PART_LOGIC", { kindCode: "VACT", scope: "PART", partCode: "LOGIC", maxScore: 120, ...PRIVATE }, [
    part(1, "Logic và phân tích số liệu", 12, null, [LOGIC], { maxPoints: 120 }),
  ]),
  wireTemplate("VACT_PART_REASONING", { kindCode: "VACT", scope: "PART", partCode: "REASONING", maxScore: 180, ...PRIVATE }, [
    part(1, "Suy luận khoa học", 18, null, [REASONING], { maxPoints: 180 }),
  ]),
  wireTemplate("VACT_SUBJECT_VIETNAMESE", { kindCode: "VACT", subjectCode: "VIETNAMESE", maxScore: 300, ...PRIVATE }, [
    part(1, "Tiếng Việt", 30, null, [VIETNAMESE], { maxPoints: 300 }),
  ]),
  wireTemplate("VACT_SUBJECT_ANH", { kindCode: "VACT", subjectCode: "ANH", maxScore: 300, ...PRIVATE }, [
    part(1, "Tiếng Anh", 30, null, [ENGLISH], { maxPoints: 300 }),
  ]),
  wireTemplate("VACT_SUBJECT_TOAN", { kindCode: "VACT", subjectCode: "TOAN", maxScore: 300, ...PRIVATE }, [
    part(1, "Toán học", 30, null, [MATH], { maxPoints: 300 }),
  ]),
];

const THPT_SUBJECT = { kindCode: "THPTQG", timing: "PER_PART", maxScore: 10 };
const THPTQG_TEMPLATES = [
  wireTemplate("THPTQG_MATH", { ...THPT_SUBJECT, subjectCode: "MATH" }, [
    part(1, "Phần I", 12, 20, [section("MATH", 12)]),
    part(2, "Phần II", 4, 20, [section("MATH", 4)]),
    part(3, "Phần III", 6, 20, [section("MATH", 6)]),
  ]),
  wireTemplate("THPTQG_HISTORY", { ...THPT_SUBJECT, subjectCode: "HISTORY" }, [
    part(1, "Phần I", 24, 25, [section("HISTORY", 24)]),
    part(2, "Phần II", 4, 25, [section("HISTORY", 4)]),
  ]),
  wireTemplate("THPTQG_LITERATURE", { ...THPT_SUBJECT, subjectCode: "LITERATURE", publishable: false }, [
    part(1, "Ngữ văn", 3, 120, [
      section("LITERATURE", 3, {}, [block("Tự luận", 3, { questionType: "ESSAY", omitted: true })]),
    ]),
  ]),
];
/** BCA FULL của seed V202611171200: Phần 2 có điểm nhưng mẫu không chia điểm cho từng môn. */
const BCA_TEMPLATES = [
  wireTemplate("BCA_FULL", { kindCode: "BCA", scope: "FULL", durationMinutes: 180, maxScore: 100 }, [
    part(1, "Tự luận Văn", 1, null, [section("LITERATURE", 1)], { maxPoints: 25 }),
    part(2, "Trắc nghiệm bắt buộc", 65, null, [section("MATH", 35), section("HISTORY", 10), section("ENGLISH", 20)], { maxPoints: 60 }),
    part(3, "Trắc nghiệm tự chọn", 15, null, [section("PHYSICS", 15)], { maxPoints: 15 }),
  ]),
];


/** Hồ sơ kỳ thi mới của BE-13 (chưa có trong dữ liệu mẫu): V-ACT ba phạm vi, THPTQG thêm Theo phần, hai môn V-ACT. */
function patchNewProfiles(reference: ContentReference, locale: string) {
  reference.examKinds.find((kind) => kind.code === "VACT")!.scopes = ["FULL", "PART", "SUBJECT"];
  reference.examKinds.find((kind) => kind.code === "THPTQG")!.scopes = ["SUBJECT", "PART"];
  const en = locale === "en";
  reference.subjects.push(
    { ...reference.subjects[0]!, code: "ANH", name: en ? "English" : "Tiếng Anh" },
    { ...reference.subjects[0]!, code: "TOAN", name: en ? "Mathematics" : "Toán học" },
  );
}

/** Trang với mẫu máy chủ giả đưa vào; POST /exams trả 201 để thấy bài vẫn tạo được. */
async function renderWithTemplates(templates: Wire[], locale: "vi" | "en" = "vi") {
  referenceMock.patch = patchNewProfiles;
  backend = new ExamsBackend();
  backend.server.on("GET", TEMPLATES_PATH, { status: 200, body: templates });
  backend.server.on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() });
  backend.install();
  const user = userEvent.setup();
  renderExamsUi(
    <ExamEditorLayout>
      <ExamGeneralForm mode="create" />
    </ExamEditorLayout>,
    locale === "en" ? { locale, messages: en } : {},
  );
  await screen.findByRole("form", { name: locale === "en" ? "Create exam" : "Tạo bài thi" });
  return user;
}
const chooseScope = (name: RegExp) => screen.getByRole("radio", { name });
const partLabels = async () =>
  within(await screen.findByRole("group", { name: /^Phần thi/ }))
    .getAllByRole("radio")
    .map((radio) => radio.closest("label")?.textContent);
const pickSubject = async (user: UserEvent, label: string) => {
  await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
  await user.click(await screen.findByRole("option", { name: label }));
};

describe("trang Tạo bài thi, bước 1: V-ACT và THPTQG (UNI-2677)", () => {
  it("V-ACT Theo phần: bốn phần có tên, không hiện mã thô; cột tỷ lệ là \"Tỷ lệ trong bài\"", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    await user.click(chooseScope(/^Theo phần/));
    expect(await partLabels()).toEqual(["Sử dụng ngôn ngữ", "Tư duy khoa học", "Logic và phân tích số liệu", "Suy luận khoa học"]);
    for (const code of ["LANGUAGE", "THINKING", "LOGIC", "REASONING"]) expect(screen.queryByText(code)).toBeNull();
    expect(await screen.findByRole("columnheader", { name: "Tỷ lệ trong bài" })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Tỷ lệ toàn bài" })).toBeNull();
  }, 20_000);

  it("V-ACT Theo môn: Tiếng Việt, Tiếng Anh, Toán học", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    await user.click(chooseScope(/^Theo môn/));
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    const names = (await screen.findAllByRole("option")).map((option) => option.textContent);
    expect(names).toEqual(["Tiếng Việt", "Tiếng Anh", "Toán học"]);
  }, 20_000);

  it("V-ACT toàn bài: ô số câu của 5 tiểu mục chỉ đọc; tỷ lệ trong phần 50/50/100/40/60%, toàn bài 25/25/25/10/15%", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    const EXPECTED: Array<[name: string, inPart: string, inExam: string]> = [
      ["Tiếng Việt", "50%", "25%"],
      ["Tiếng Anh", "50%", "25%"],
      ["Toán học", "100%", "25%"],
      ["Logic và phân tích số liệu", "40%", "10%"],
      ["Suy luận khoa học", "60%", "15%"],
    ];
    expect(await screen.findByRole("columnheader", { name: "Tỷ lệ trong phần" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Tỷ lệ toàn bài" })).toBeInTheDocument();
    for (const [label, inPart, inExam] of EXPECTED) {
      const input = await screen.findByRole("textbox", { name: `Số câu của ${label}` });
      expect(input).toHaveAttribute("readonly");
      const cells = within(input.closest("tr")!).getAllByRole("cell");
      expect([cells[2]!.textContent, cells[3]!.textContent]).toEqual([inPart, inExam]);
    }
  }, 20_000);

  it("bài riêng V-ACT: số câu sửa được; để trống thời lượng báo lỗi ở ô thời lượng và không gửi yêu cầu tạo", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    await user.click(chooseScope(/^Theo phần/));
    const count = (await screen.findAllByRole("textbox", { name: /^Số câu của/ }))[0]!;
    expect(count).not.toHaveAttribute("readonly");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "V-ACT ngôn ngữ");
    await user.click(submitButton());
    const error = await screen.findByText(/Chưa nhập thời lượng\. Bài riêng của .+ không có thời lượng mặc định\./, { selector: "p" });
    const duration = screen.getByRole("textbox", { name: "Thời lượng chung" });
    expect(duration).toHaveAttribute("aria-invalid", "true");
    expect(duration).toHaveAccessibleDescription(error.textContent!);
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
  }, 20_000);

  it("bài riêng V-ACT để trống thời lượng: danh sách Cần xử lý hiện đúng câu của ô thời lượng (FE5-V1-03)", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    await user.click(chooseScope(/^Theo phần/));
    await screen.findAllByRole("textbox", { name: /^Số câu của/ });
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "V-ACT ngôn ngữ");
    await user.click(submitButton());
    const message = "Chưa nhập thời lượng. Bài riêng của V-ACT không có thời lượng mặc định.";
    await screen.findByText(message, { selector: "p" });
    expect(screen.getByRole("textbox", { name: "Thời lượng chung" })).toHaveAccessibleDescription(message);
    expect(within(summary()).getByText("Cần xử lý")).toBeInTheDocument();
    expect(within(summary()).getAllByText(message)).toHaveLength(1);
    expect(within(summary()).queryByText(/phải là số phút nguyên dương/)).toBeNull();
  }, 20_000);

  it("English: the To fix list shows the duration field's message, not the generic one (FE5-V1-03)", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES, "en");
    await user.click(screen.getByRole("combobox", { name: /^Exam type/ }));
    await user.click(await screen.findByRole("option", { name: /\(V-ACT\)$/ }));
    await user.click(screen.getByRole("radio", { name: /^By part/ }));
    await screen.findAllByRole("textbox", { name: /^Questions of/ });
    await user.type(screen.getByRole("textbox", { name: /^Exam name/ }), "V-ACT language");
    await user.click(screen.getAllByRole("button", { name: "Continue: Structure and matrix" })[0]!);
    const message = "Duration missing. Custom V-ACT exams have no default duration.";
    await screen.findByText(message, { selector: "p" });
    expect(within(screen.getByRole("region", { name: "Summary" })).getAllByText(message)).toHaveLength(1);
  }, 20_000);

  describe("tên phần và dòng V-ACT theo ngôn ngữ (FE5-V1-01)", () => {
    // Tên của mẫu V-ACT thật là tiếng Việt; chữ hiện ra phải lấy từ danh mục (môn, năng lực) và bốn thông điệp phần.
    const VI_NAMES = /Sử dụng ngôn ngữ|Tư duy khoa học|Logic và phân tích|Suy luận khoa học|Toán học|Tiếng Việt|Tiếng Anh/;
    const pickKindEn = async (user: UserEvent) => {
      await user.click(screen.getByRole("combobox", { name: /^Exam type/ }));
      await user.click(await screen.findByRole("option", { name: /\(V-ACT\)$/ }));
    };
    /** Chữ của bảng tổng quan: ô tên dòng cùng nhãn truy cập của mọi ô số trong bảng. */
    const tableText = (table: HTMLElement) => [
      ...Array.from(table.querySelectorAll("tbody tr")).map((row) => row.querySelector("td")?.textContent ?? ""),
      ...Array.from(table.querySelectorAll("[aria-label]")).map((node) => node.getAttribute("aria-label") ?? ""),
    ];

    it("tiếng Anh, toàn bài: dòng phần, dòng tiểu mục và nhãn truy cập không còn tên tiếng Việt", async () => {
      const user = await renderWithTemplates(VACT_TEMPLATES, "en");
      await pickKindEn(user);
      const table = (await screen.findByRole("region", { name: "Exam structure overview table" })).querySelector("table")!;
      await within(table).findByRole("textbox", { name: "Points of Language use" });
      const text = tableText(table);
      for (const name of ["Language use", "Mathematics", "Scientific thinking", "Vietnamese", "English", "Logic and data analysis", "Scientific reasoning"]) {
        expect(text.some((entry) => entry.includes(name)), name).toBe(true);
      }
      expect(text.filter((entry) => VI_NAMES.test(entry))).toEqual([]);
    }, 20_000);

    it("tiếng Anh, Theo phần: thẻ phần, dòng phần và tiểu mục không còn tên tiếng Việt", async () => {
      const user = await renderWithTemplates(VACT_TEMPLATES, "en");
      await pickKindEn(user);
      await user.click(screen.getByRole("radio", { name: /^By part/ }));
      const cards = within(await screen.findByRole("group", { name: /^Part/ })).getAllByRole("radio").map((radio) => radio.closest("label")?.textContent);
      expect(cards).toEqual(["Language use", "Scientific thinking", "Logic and data analysis", "Scientific reasoning"]);
      const table = screen.getByRole("region", { name: "Exam structure overview table" }).querySelector("table")!;
      await within(table).findByRole("textbox", { name: "Points of Language use" });
      expect(tableText(table).filter((entry) => VI_NAMES.test(entry))).toEqual([]);
      await user.click(screen.getByRole("radio", { name: "Logic and data analysis" }));
      await within(table).findByRole("textbox", { name: "Points of Logic and data analysis" });
      expect(tableText(table).filter((entry) => VI_NAMES.test(entry))).toEqual([]);
    }, 20_000);

    it("tiếng Anh, Theo môn: dòng phần của môn lấy tên môn từ danh mục", async () => {
      const user = await renderWithTemplates(VACT_TEMPLATES, "en");
      await pickKindEn(user);
      await user.click(screen.getByRole("radio", { name: /^By subject/ }));
      await user.click(screen.getByRole("combobox", { name: /^Subject/ }));
      await user.click(await screen.findByRole("option", { name: "Mathematics" }));
      const table = screen.getByRole("region", { name: "Exam structure overview table" }).querySelector("table")!;
      await within(table).findByRole("textbox", { name: "Questions of Mathematics" });
      expect(tableText(table).filter((entry) => VI_NAMES.test(entry))).toEqual([]);
    }, 20_000);

    it("tiếng Việt giữ tên tiếng Việt ở dòng phần, tiểu mục và nhãn truy cập", async () => {
      const user = await renderWithTemplates(VACT_TEMPLATES);
      await pickKind(user, "V-ACT");
      const table = (await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" })).querySelector("table")!;
      await within(table).findByRole("textbox", { name: "Điểm của Sử dụng ngôn ngữ" });
      const text = tableText(table);
      for (const name of ["Sử dụng ngôn ngữ", "Toán học", "Tư duy khoa học", "Tiếng Việt", "Tiếng Anh", "Logic và phân tích số liệu", "Suy luận khoa học"]) {
        expect(text.some((entry) => entry.includes(name)), name).toBe(true);
      }
    }, 20_000);
  });

  it("THPTQG Ngữ văn: báo toàn bộ môn là tự luận, bài nháp vẫn tạo được", async () => {
    const user = await renderWithTemplates(THPTQG_TEMPLATES);
    await pickKind(user, "THPTQG");
    await pickSubject(user, "Ngữ văn");
    expect(await screen.findByText("Toàn bộ môn Ngữ văn là tự luận – tạm bỏ qua trong V1")).toBeInTheDocument();
    expect(screen.getByText(/chỉ lưu nháp, không công bố được/)).toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "THPTQG Ngữ văn nháp");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(backend.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ kind: "THPTQG", scope: "SUBJECT", subjectCode: "LITERATURE" });
  }, 20_000);

  it("THPTQG Theo phần: chọn môn rồi chọn phần; Toán có 3 phần, Lịch sử 2 phần; không có cột tỷ lệ hay thông báo Ngữ văn", async () => {
    const user = await renderWithTemplates(THPTQG_TEMPLATES);
    await pickKind(user, "THPTQG");
    await user.click(chooseScope(/^Theo phần/));
    await pickSubject(user, "Toán");
    expect(await partLabels()).toHaveLength(3);
    expect(screen.queryByText(/tạm bỏ qua trong V1/)).toBeNull();
    expect(screen.queryByRole("columnheader", { name: /Tỷ lệ/ })).toBeNull();
    await pickSubject(user, "Lịch sử");
    await waitFor(async () => expect(await partLabels()).toHaveLength(2));
  }, 20_000);

  it("BCA toàn bài: ô Điểm của dòng môn để trống (—), điểm chỉ theo phần (mẫu không ghi điểm từng môn)", async () => {
    const user = await renderWithTemplates(BCA_TEMPLATES);
    await pickKind(user, "BCA");
    await screen.findByText("Trắc nghiệm bắt buộc");
    expect(screen.getByRole("columnheader", { name: "Điểm" })).toBeInTheDocument();
    for (const subject of ["Toán", "Lịch sử", "Tiếng Anh"]) {
      const row = screen.getAllByText(subject).map((el) => el.closest("tr")).find((tr) => tr !== null)!;
      expect(within(row).getAllByRole("cell").at(-1)!.textContent).toBe("—");
    }
  }, 20_000);
});

describe("trang Tạo bài thi, bước 1: bỏ ghi chú nạp mẫu, năm và chip phiên bản (UNI-2750)", () => {
  const REMOVED = [/Hệ thống nạp mẫu mới nhất/, /Muốn dùng phiên bản năm khác/, /Năm cấu trúc$/, /Năm ma trận$/, /Phiên bản cấu trúc chọn ở bước 2/];
  const expectNoRemovedBlocks = () => {
    for (const text of REMOVED) expect(screen.queryByText(text), String(text)).toBeNull();
  };
  const overviewSection = () => screen.getByRole("heading", { level: 2, name: "Cấu trúc tổng quan" }).closest("section")!;

  it("HSA toàn bài: bảng tổng quan hiện, ba khối ghi chú không còn", async () => {
    await renderPage();
    expect(within(overviewSection()).getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(3);
    expectNoRemovedBlocks();
  });

  it("THPTQG Theo môn: không còn ba khối ghi chú", async () => {
    const user = await renderWithTemplates(THPTQG_TEMPLATES);
    await pickKind(user, "THPTQG");
    await pickSubject(user, "Toán");
    await screen.findAllByRole("textbox", { name: /^Số câu của/ });
    expectNoRemovedBlocks();
  }, 20_000);

  it("mẫu có năm cấu trúc khác năm ma trận: vẫn hiện cảnh báo", async () => {
    const [bca] = BCA_TEMPLATES as [Wire];
    const differing = { ...bca, structureYear: 2026, matrixYear: 2025 };
    const user = await renderWithTemplates([differing]);
    await pickKind(user, "BCA");
    await screen.findByText("Trắc nghiệm bắt buộc");
    expect(screen.getByText(/Cấu trúc 2026 dùng ma trận 2025/)).toBeInTheDocument();
    expectNoRemovedBlocks();
  }, 20_000);

  it("mẫu khóa số câu: vẫn hiện chip khóa", async () => {
    const user = await renderWithTemplates(VACT_TEMPLATES);
    await pickKind(user, "V-ACT");
    await screen.findByRole("textbox", { name: /^Điểm của Sử dụng ngôn ngữ/ });
    expect(screen.getByText("Mẫu đã khóa số câu")).toBeInTheDocument();
    expectNoRemovedBlocks();
  }, 20_000);

  it("mẫu có ghi chú chuyên gia (expertNote): không còn hộp \"Lưu ý của mẫu\" ở bước 1 (UNI-2750)", async () => {
    const [bca] = BCA_TEMPLATES as [Wire];
    const user = await renderWithTemplates([{ ...bca, expertNote: "Chờ chuyên gia xác nhận" }]);
    await pickKind(user, "BCA");
    await screen.findByText("Trắc nghiệm bắt buộc");
    expect(screen.queryByText("Lưu ý của mẫu")).toBeNull();
    expect(screen.queryByText("Chờ chuyên gia xác nhận")).toBeNull();
  }, 20_000);
});
