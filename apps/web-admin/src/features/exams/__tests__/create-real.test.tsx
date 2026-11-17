import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { EXAM_ID, examDetailWire, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
vi.mock(import("@/features/content/api"), async (importOriginal) => ({
  ...(await importOriginal()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";
const SUBMIT = "Tạo bài thi và soạn khối câu hỏi";

type Wire = Record<string, unknown>;

const block = (name: string, count: number, extra: Wire = {}) => ({
  id: null,
  name,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  ...extra,
});

const section = (subjectCode: string, count: number, blocks: Wire[] = [block("Trắc nghiệm", count)]) => ({
  id: null,
  subjectCode,
  questionCount: count,
  blocks,
});

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

/** Mẫu theo dạng máy chủ trả: `structure` là tài liệu `{ parts }`. */
function template(code: string, fields: Wire, parts: Wire[]): Wire {
  return { active: true, scope: "SUBJECT", timing: "PER_PART", buildMode: "STANDARD", isDefault: true, ...fields, code, structure: { parts } };
}

const HSA_FULL = template("HSA_FULL", { kindCode: "HSA", scope: "FULL", label: "HSA · Toàn bài" }, [
  part(1, "Phần 1", 10, 60, [section("MATH", 10)]),
]);
const BCA_FULL = template(
  "BCA_FULL",
  { kindCode: "BCA", scope: "FULL", timing: "SHARED", durationMinutes: 180, maxScore: 100 },
  [
    part(1, "Tự luận Văn", 1, null, [section("LITERATURE", 1)], { maxPoints: 25 }),
    part(2, "Trắc nghiệm bắt buộc", 65, null, [section("MATH", 35), section("HISTORY", 10), section("ENGLISH", 20)], { maxPoints: 60 }),
    part(3, "Trắc nghiệm tự chọn", 15, null, [section("PHYSICS", 15)], { maxPoints: 15 }),
  ],
);
const SPT_MATH = template(
  "SPT_MATH_V1",
  { kindCode: "SPT", subjectCode: "MATH", structureYear: 2025, timing: "SHARED", maxScore: 10 },
  [part(1, "Toán", 22, null, [section("MATH", 22, [block("Trắc nghiệm", 22), block("Tự luận", 3, { questionType: "ESSAY", omitted: true })])])],
);
const SP2E_GEO_SUBJECT = template("SP2E_GEOGRAPHY_2026", { kindCode: "SP2E", subjectCode: "GEOGRAPHY", structureYear: 2026 }, [
  part(1, "Địa lí", 28, 60, [section("GEOGRAPHY", 28)]),
]);
const SP2E_GEO_PART_I = template(
  "SP2E_GEOGRAPHY_2026_PHAN_I",
  { kindCode: "SP2E", scope: "PART", subjectCode: "GEOGRAPHY", partCode: "PHAN_I", structureYear: 2026 },
  [part(1, "Phần I", 16, 20, [section("GEOGRAPHY", 16)])],
);
// Văn SP2E: mặc định là đề minh họa cũ (K15), dù mẫu 2026 mới hơn.
const SP2E_LIT_2026 = template("SP2E_LITERATURE_2026", { kindCode: "SP2E", subjectCode: "LITERATURE", structureYear: 2026, isDefault: false }, [
  part(1, "Văn 2026", 22, 90, [section("LITERATURE", 22)]),
]);
const SP2E_LIT_OLD = template("SP2E_LITERATURE_2025", { kindCode: "SP2E", subjectCode: "LITERATURE", structureYear: 2025, isDefault: true }, [
  part(1, "Văn đề minh họa cũ", 20, 80, [section("LITERATURE", 20)]),
]);
const SPT_REVIEW = template("SPT_REVIEW", { kindCode: "SPT", subjectCode: "MATH", buildMode: "REVIEW", structureYear: 2025, timing: "PER_PART", maxScore: 10 }, [
  part(1, "Bài ôn", 10, 15, [section("MATH", 10)]),
]);

const ALL_TEMPLATES = [HSA_FULL, BCA_FULL, SPT_MATH, SPT_REVIEW, SP2E_GEO_SUBJECT, SP2E_GEO_PART_I, SP2E_LIT_2026, SP2E_LIT_OLD];

function createServer(templates: Wire[] = ALL_TEMPLATES) {
  return new FakeServer()
    .on("GET", TEMPLATES_PATH, { status: 200, body: templates })
    .on("GET", `${EXAMS_PATH}/suggested-code`, (request) => ({
      status: 200,
      body: { code: `${request.url.searchParams.get("kind") ?? "X"}-${request.url.searchParams.get("scope") ?? "F"}-001` },
    }));
}

async function renderPage() {
  const user = userEvent.setup();
  renderWithProviders(<CreateExamPage />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-FULL-001"));
  return user;
}

const submit = () => screen.getAllByRole("button", { name: SUBMIT })[0]!;
const name = () => screen.getByRole("textbox", { name: /Tên bài thi/ });
const rowOf = (text: string) => screen.getByText(text, { exact: false }).closest("tr")!;

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
});

describe("trang Tạo bài thi, nhánh thật: mẫu mặc định và phạm vi", () => {
  it("Văn SP2E: nạp mẫu isDefault (đề minh họa cũ) thay vì mẫu năm mới hơn", async () => {
    createServer().install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SP2E/ }));
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: "Ngữ văn" }));
    expect(await screen.findByText("Văn đề minh họa cũ")).toBeInTheDocument();
    expect(screen.queryByText("Văn 2026")).toBeNull();
  }, 20_000);

  it("SP2E Theo phần: chọn môn trước rồi phần; gửi môn, phần, không gửi build_mode", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SP2E/ }));
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(screen.queryByRole("radio", { name: /Tự thiết lập bài ôn/ })).toBeNull();
    expect(screen.getByText("Chọn môn trước, rồi chọn phần của môn đó")).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: "Địa lí" }));
    expect(await screen.findByRole("radio", { name: "PHAN_I" })).toBeChecked();
    expect(within(rowOf("Phần I")).getByRole("textbox", { name: /^Số câu của/ })).toHaveValue("16");
    await user.type(name(), "SP2E Địa lí Phần I");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    const body = server.calls("POST", EXAMS_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ kind: "SP2E", scope: "PART", partCode: "PHAN_I", subjectCode: "GEOGRAPHY" });
    expect(body).not.toHaveProperty("buildMode");
    expect(body).not.toHaveProperty("overview");
  }, 20_000);

  it("bài ôn có mẫu <KỲ>_REVIEW: nạp mẫu bài ôn, gửi build_mode REVIEW", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SPT/ }));
    expect(await screen.findByRole("radio", { name: /Dùng cấu trúc môn SPT/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Tự thiết lập bài ôn/ }));
    expect(await screen.findByText("Bài ôn")).toBeInTheDocument();
    await user.type(name(), "SPT ôn Toán");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ kind: "SPT", scope: "SUBJECT", subjectCode: "MATH", buildMode: "REVIEW" });
  }, 20_000);
});

describe("trang Tạo bài thi, nhánh thật: đổi lựa chọn sau khi sửa bảng", () => {
  const HSA_PART_MATH = template("HSA_PART_MATH", { kindCode: "HSA", scope: "PART", partCode: "MATH", label: "HSA · Toán" }, [
    part(1, "Toán", 50, 75, [section("MATH", 50)]),
  ]);
  const HSA_PART_LIT = template("HSA_PART_LIT", { kindCode: "HSA", scope: "PART", partCode: "LITERATURE", label: "HSA · Văn" }, [
    part(1, "Văn", 50, 60, [section("LITERATURE", 50)]),
  ]);

  // [nhãn, ô chọn đổi]: cả hai đi qua `requestScope`, bảng đã sửa thì hỏi trước khi nạp lại.
  it.each([
    ["kỳ thi", () => screen.getByRole("radio", { name: /^QDA/ })],
    ["phần", () => screen.getByRole("radio", { name: "Văn" })],
  ])("đổi %s sau khi sửa bảng: hỏi xác nhận, Giữ lựa chọn cũ không mất số đã sửa", async (_label, target) => {
    createServer([...ALL_TEMPLATES, HSA_PART_MATH, HSA_PART_LIT]).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(await screen.findByRole("radio", { name: "Toán" })).toBeChecked();
    const count = screen.getByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "7");
    await user.click(target());
    const dialog = await screen.findByRole("alert");
    expect(within(dialog).getByText(/Đổi sang/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Giữ lựa chọn cũ" }));
    expect(screen.getByRole("radio", { name: /^HSA/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Toán" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: /^Số câu của/ })).toHaveValue("7");
  }, 20_000);
});

describe("trang Tạo bài thi, nhánh thật: bảng tổng quan theo kỳ thi", () => {
  it("SPT Toán: nhóm Tự luận tạm bỏ qua hiện mờ, không có ô nhập, không tính vào tổng; thời lượng chung phải nhập", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SPT/ }));
    const omitted = (await screen.findByText("Tự luận")).closest("tr")!;
    expect(within(omitted).getByText("tạm bỏ qua")).toBeInTheDocument();
    expect(within(omitted).queryByRole("textbox")).toBeNull();
    expect(omitted.className).not.toContain("opacity");
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(1);
    expect(within(screen.getByRole("complementary", { name: "Tóm tắt" })).getByText("Tổng số câu").parentElement).toHaveTextContent("22");

    // Mẫu V1 chưa ghi thời lượng: một đồng hồ chung, quản trị viên nhập.
    await user.type(name(), "SPT Toán V1");
    await user.click(submit());
    expect(await screen.findAllByText("Thời lượng chung phải là số phút nguyên dương.")).not.toHaveLength(0);
    expect(server.calls("POST", EXAMS_PATH)).toHaveLength(0);
    const shared = screen.getByRole("textbox", { name: /^Thời lượng chung/ });
    expect(shared).toBeInvalid();
    expect(shared).toHaveAccessibleDescription("Thời lượng chung phải là số phút nguyên dương.");
    await user.type(shared, "90");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ durationMinutes: 90 });
  }, 20_000);

  it("BCA: thời lượng chung, cột Điểm 25/60/15, phần nhiều môn có dòng môn thụt vào; sửa điểm thì thang của bài là tổng các phần", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^BCA/ }));
    await screen.findByText("Trắc nghiệm bắt buộc");
    expect(screen.queryByRole("columnheader", { name: /Thời lượng \(phút\)/ })).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Điểm" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Thời lượng chung/ })).toHaveValue("180");
    const group = rowOf("Trắc nghiệm bắt buộc");
    expect(within(group).getByText("tổng các môn")).toBeInTheDocument();
    for (const subject of ["Toán", "Lịch sử", "Tiếng Anh"]) expect(screen.getAllByText(subject).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(5);

    await user.type(name(), "BCA thử");
    const points = screen.getByRole("textbox", { name: /^Điểm của Tự luận Văn/ });
    await user.clear(points);
    await user.type(points, "30");
    expect(screen.queryByText("Tổng điểm các phần khác điểm tối đa của bài.")).toBeNull();
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    // Dòng sửa được, tổng của bài là tổng các phần: 30 + 60 + 15.
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ kind: "BCA", durationMinutes: 180, maxScore: 105 });
  }, 30_000);

  it("kỳ thi một phạm vi (V-ACT): không hiện nhóm chọn phạm vi", async () => {
    createServer().install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^V-ACT/ }));
    expect(screen.queryByRole("group", { name: /Phạm vi bài thi/ })).toBeNull();
    expect(screen.getByText("Kỳ thi này chỉ có phạm vi này")).toBeInTheDocument();
  });

  it("không có mẫu mặc định: báo bài vẫn tạo được, bảng một dòng trống", async () => {
    createServer([BCA_FULL]).install();
    renderWithProviders(<CreateExamPage />);
    await screen.findByRole("form", { name: "Tạo bài thi" });
    expect(await screen.findByText(/Không có mẫu mặc định cho lựa chọn này/)).toBeInTheDocument();
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(1);
  });

  it("ô thời lượng chung giữ đúng chữ đã gõ: xóa thì trống (không hiện 0), chữ không phải số báo lỗi", async () => {
    createServer().install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^SPT/ }));
    const shared = await screen.findByRole("textbox", { name: /^Thời lượng chung/ });
    expect(shared).toHaveValue("");
    await user.type(shared, "90");
    expect(shared).toHaveValue("90");
    await user.clear(shared);
    expect(shared).toHaveValue("");
    await user.type(shared, "abc");
    expect(shared).toHaveValue("abc");
    await user.type(name(), "SPT chữ");
    await user.click(submit());
    await waitFor(() => expect(shared).toHaveAccessibleDescription("Thời lượng chung phải là số phút nguyên dương."));
  }, 20_000);
});

describe("trang Tạo bài thi, nhánh thật: lỗi khi tạo", () => {
  it("409 EXAM_CODE_TAKEN: báo lỗi ở ô Mã, ở lại trang", async () => {
    createServer().on("POST", EXAMS_PATH, { status: 409, body: { code: "EXAM_CODE_TAKEN" } }).install();
    const user = await renderPage();
    await user.type(name(), "Trùng mã");
    await user.click(submit());
    expect((await screen.findAllByText("Mã bài thi đã được dùng. Nhập mã khác.")).length).toBeGreaterThan(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 20_000);

  it("422 (tổng quan không hợp lệ): báo ở đầu biểu mẫu, giữ nguyên số đã nhập", async () => {
    createServer().on("POST", EXAMS_PATH, { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", fieldErrors: [] } }).install();
    const user = await renderPage();
    await user.type(name(), "HSA thử");
    const count = screen.getByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "12");
    await user.click(submit());
    expect(await screen.findByText(/Cấu trúc khởi tạo chưa hợp lệ/)).toBeInTheDocument();
    expect(count).toHaveValue("12");
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 20_000);

  it("lỗi mạng khác (500): báo lỗi chung, thử lại được", async () => {
    createServer().on("POST", EXAMS_PATH, { status: 500, body: { code: "BOOM" } }, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.type(name(), "HSA thử");
    await user.click(submit());
    expect(await screen.findByText("Không tạo được bài thi. Thử lại sau.")).toBeInTheDocument();
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 20_000);
});
