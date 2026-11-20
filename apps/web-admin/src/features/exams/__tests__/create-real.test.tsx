import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { EXAM_ID, examDetailWire, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));
// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để dựng màn.
// Danh mục thật của V-ACT có Theo môn (bổ sung V-ACT, D8); bản mẫu cũ chỉ có FULL: test bài ôn V-ACT mở thêm phạm vi.
const catalogue = vi.hoisted(() => ({ vactScopes: null as string[] | null }));
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const reference: typeof getContentReference = async (...args) => {
    const data = await getContentReference(...args);
    if (catalogue.vactScopes) {
      data.examKinds = data.examKinds.map((kind) => (kind.code === "VACT" ? { ...kind, scopes: catalogue.vactScopes as typeof kind.scopes } : kind));
    }
    return data;
  };
  return { ...(await importOriginal()), getContentReference: reference, defaultContentApiDeps: { getContentReference: reference } };
});

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";
const SUBMIT = "Lưu và tiếp tục";

/** Kỳ thi chọn bằng ô chọn (không còn thẻ radio): mở danh sách rồi chọn mục có mã trong ngoặc. */
async function pickKind(user: UserEvent, code: string) {
  await user.click(screen.getByRole("combobox", { name: /^Kỳ thi/ }));
  await user.click(await screen.findByRole("option", { name: new RegExp(`\\(${code}\\)$`) }));
}
const kindSelect = () => screen.getByRole("combobox", { name: /^Kỳ thi/ });

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
  return { active: true, scope: "SUBJECT", timing: "PER_PART", buildMode: "STANDARD", isDefault: true, ...fields, code, structure: { parts }, parts: [] };
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
  part(1, "Phần I", 16, 20, [section("GEOGRAPHY", 16)]),
  part(2, "Phần II", 4, 20, [section("GEOGRAPHY", 4)]),
  part(3, "Phần III", 6, 20, [section("GEOGRAPHY", 6)]),
]);
// Văn SP2E: mặc định là đề minh họa cũ (K15), dù mẫu 2026 mới hơn.
const SP2E_LIT_2026 = template("SP2E_LITERATURE_2026", { kindCode: "SP2E", subjectCode: "LITERATURE", structureYear: 2026, isDefault: false }, [
  part(1, "Văn 2026", 22, 90, [section("LITERATURE", 22)]),
]);
const SP2E_LIT_OLD = template("SP2E_LITERATURE_2025", { kindCode: "SP2E", subjectCode: "LITERATURE", structureYear: 2025, isDefault: true }, [
  part(1, "Văn đề minh họa cũ", 20, 80, [section("LITERATURE", 20)]),
]);
// Hình thật của mẫu bài ôn: không ghi môn (`subjectCode` null), dùng chung cho mọi môn; phần có id.
const SPT_REVIEW = template("SPT_REVIEW", { kindCode: "SPT", subjectCode: null, buildMode: "REVIEW", structureYear: 2025, timing: "PER_PART", maxScore: null }, [
  part(1, "Bài ôn", 10, 15, [section("MATH", 10)], { id: "SPT_REVIEW_PART" }),
]);

const ALL_TEMPLATES = [HSA_FULL, BCA_FULL, SPT_MATH, SPT_REVIEW, SP2E_GEO_SUBJECT, SP2E_LIT_2026, SP2E_LIT_OLD];

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
    await pickKind(user, "SP2E");
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: "Ngữ văn" }));
    expect(await screen.findByText("Văn đề minh họa cũ")).toBeInTheDocument();
    expect(screen.queryByText("Văn 2026")).toBeNull();
  }, 20_000);

  it("SP2E Theo phần: chọn môn rồi phần của mẫu Theo môn mặc định; gửi môn và mã P<thứ tự>, không gửi build_mode", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await pickKind(user, "SP2E");
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(screen.queryByRole("radio", { name: /Tự thiết lập bài ôn/ })).toBeNull();
    expect(screen.getByText("Chọn môn trước, rồi chọn phần của môn đó")).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: "Địa lí" }));
    const parts = within(await screen.findByRole("group", { name: /^Phần thi/ })).getAllByRole("radio");
    expect(parts.map((radio) => radio.closest("label")?.textContent)).toEqual(["Phần I", "Phần II", "Phần III"]);
    expect(parts[0]).toBeChecked();
    expect(screen.getByRole("textbox", { name: /^Số câu của/ })).toHaveValue("16");
    await user.click(parts[1]!);
    await waitFor(() => expect(parts[1]).toBeChecked());
    expect(screen.getByRole("textbox", { name: "Số câu của Phần II" })).toHaveValue("4");
    await user.type(name(), "SP2E Địa lí Phần II");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    const body = server.calls("POST", EXAMS_PATH)[0]!.body as Wire;
    expect(body).toMatchObject({ kind: "SP2E", scope: "PART", partCode: "P2", subjectCode: "GEOGRAPHY" });
    expect(body).not.toHaveProperty("buildMode");
    expect(body).not.toHaveProperty("overview");
  }, 20_000);

  it("SPT Theo phần: phần lấy từ mẫu Theo môn mặc định của môn (P1..P3), không từ mẫu Theo phần", async () => {
    const SPT_MATH_THREE = template("SPT_MATH_V1", { kindCode: "SPT", subjectCode: "MATH", structureYear: 2025, timing: "SHARED", maxScore: 10 }, [
      part(1, "Phần I · Trắc nghiệm", 16, null, [section("MATH", 16)], { maxPoints: 4 }),
      part(2, "Phần II · Đúng/Sai", 2, null, [section("MATH", 2)], { maxPoints: 2 }),
      part(3, "Phần III · Trả lời ngắn", 4, null, [section("MATH", 4)], { maxPoints: 1 }),
    ]);
    const server = createServer([HSA_FULL, SPT_MATH_THREE])
      .on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() })
      .install();
    const user = await renderPage();
    await pickKind(user, "SPT");
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    const group = await screen.findByRole("group", { name: /^Phần thi/ });
    expect(within(group).getAllByRole("radio").map((radio) => radio.closest("label")?.textContent)).toEqual([
      "Phần I · Trắc nghiệm",
      "Phần II · Đúng/Sai",
      "Phần III · Trả lời ngắn",
    ]);
    await user.click(within(group).getByRole("radio", { name: "Phần II · Đúng/Sai" }));
    expect(await screen.findByRole("textbox", { name: "Số câu của Phần II · Đúng/Sai" })).toHaveValue("2");
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(1);
    await user.type(screen.getByRole("textbox", { name: /^Thời lượng chung/ }), "30");
    await user.type(name(), "SPT Toán Phần II");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({
      kind: "SPT",
      scope: "PART",
      subjectCode: "MATH",
      partCode: "P2",
      maxScore: 2,
    });
  }, 20_000);

  it("bài ôn có mẫu <KỲ>_REVIEW: nạp mẫu bài ôn, gửi build_mode REVIEW", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await pickKind(user, "SPT");
    expect(await screen.findByRole("radio", { name: /Dùng cấu trúc môn SPT/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Tự thiết lập bài ôn/ }));
    expect(await screen.findByText("Bài ôn")).toBeInTheDocument();
    // Mẫu bài ôn khớp môn bất kỳ: không báo "không có mẫu", dòng phần mang partId của mẫu.
    expect(screen.queryByText(/Không có mẫu/)).toBeNull();
    const count = screen.getByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "8");
    await user.type(name(), "SPT ôn Toán");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({
      kind: "SPT",
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      overview: [{ partId: "SPT_REVIEW_PART", questionCount: 8 }],
    });
  }, 20_000);

  it("bài ôn HSA: số câu, thời lượng và thang người dùng nhập đi cùng partId của mẫu HSA_REVIEW", async () => {
    const HSA_MATH = template("HSA_MATH", { kindCode: "HSA", subjectCode: "MATH", structureYear: 2025, maxScore: 150 }, [part(1, "Toán", 50, 75, [section("MATH", 50)])]);
    const HSA_REVIEW = template("HSA_REVIEW", { kindCode: "HSA", subjectCode: null, buildMode: "REVIEW", maxScore: null }, [
      part(1, "Bài ôn", null as unknown as number, null, [section("MATH", 0)], { id: "HSA_REVIEW_PART" }),
    ]);
    const server = createServer([...ALL_TEMPLATES, HSA_MATH, HSA_REVIEW]).on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^Theo môn/ }));
    await user.click(await screen.findByRole("radio", { name: /Tự thiết lập bài ôn/ }));
    expect(screen.queryByText(/Không có mẫu/)).toBeNull();
    await user.type(await screen.findByRole("textbox", { name: /^Số câu của/ }), "10");
    await user.type(screen.getByRole("textbox", { name: /^Thời lượng của/ }), "20");
    const scale = screen.getByRole("textbox", { name: /^Điểm tối đa/ });
    await user.clear(scale);
    await user.type(scale, "100");
    await user.type(name(), "HSA ôn Toán");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({
      kind: "HSA",
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      maxScore: 100,
      overview: [{ partId: "HSA_REVIEW_PART", questionCount: 10, durationMinutes: 20 }],
    });
  }, 20_000);

  describe("bài ôn: thang chỉ có khi người dùng nhập (F2, ADV:FIX-QA:3)", () => {
    const HSA_MATH = template("HSA_MATH", { kindCode: "HSA", subjectCode: "MATH", structureYear: 2025, maxScore: 150 }, [part(1, "Toán", 50, 75, [section("MATH", 50)])]);
    const HSA_REVIEW = template("HSA_REVIEW", { kindCode: "HSA", subjectCode: null, buildMode: "REVIEW", maxScore: null }, [
      part(1, "Bài ôn", null as unknown as number, null, [section("MATH", 0)], { id: "HSA_REVIEW_PART" }),
    ]);
    const VACT_MATH = template("VACT_SUBJECT_TOAN", { kindCode: "VACT", subjectCode: "MATH", timing: "PER_PART", maxScore: 300 }, [part(1, "Toán", 30, 60, [section("MATH", 30)])]);
    const VACT_REVIEW = template("VACT_REVIEW", { kindCode: "VACT", subjectCode: null, buildMode: "REVIEW", timing: "SHARED", durationMinutes: null, maxScore: null }, [
      part(1, "Bài ôn", null as unknown as number, null, [section("MATH", 0)], { id: "VACT_REVIEW_PART" }),
    ]);
    const SERVER_TEMPLATES = [HSA_MATH, HSA_REVIEW, VACT_MATH, VACT_REVIEW];

    async function fillReview(user: UserEvent, scale: string | null) {
      await user.click(screen.getByRole("radio", { name: /^Theo môn/ }));
      await user.click(await screen.findByRole("radio", { name: /Tự thiết lập bài ôn/ }));
      await user.type(await screen.findByRole("textbox", { name: /^Số câu của/ }), "10");
      // Mẫu SHARED (V-ACT) chỉ có thời lượng chung; mẫu PER_PART có thời lượng từng dòng.
      const shared = screen.queryByRole("textbox", { name: /^Thời lượng chung/ });
      await user.type(shared ?? screen.getByRole("textbox", { name: /^Thời lượng của/ }), "20");
      const field = screen.getByRole("textbox", { name: /^Điểm tối đa/ });
      expect(field).toHaveValue("");
      if (scale !== null) await user.type(field, scale);
      await user.type(name(), "Bài ôn");
      await user.click(submit());
      await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    }

    it("HSA không nhập thang: ô thang để trống, bài ôn gửi không maxScore, không điểm mỗi câu; về lại Dùng cấu trúc môn thì thang 150", async () => {
      const server = createServer(SERVER_TEMPLATES).on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
      const user = await renderPage();
      await user.click(screen.getByRole("radio", { name: /^Theo môn/ }));
      expect(await screen.findByRole("textbox", { name: /^Điểm tối đa/ })).toHaveValue("150");
      // Qua bài ôn thang để trống, về lại Dùng cấu trúc môn thì thang 150 của mẫu môn (ADV:BE-13:5).
      await user.click(await screen.findByRole("radio", { name: /Tự thiết lập bài ôn/ }));
      expect(screen.getByRole("textbox", { name: /^Điểm tối đa/ })).toHaveValue("");
      await user.click(screen.getByRole("radio", { name: /Dùng cấu trúc môn/ }));
      expect(screen.getByRole("textbox", { name: /^Điểm tối đa/ })).toHaveValue("150");
      await fillReview(user, null);
      const body = server.calls("POST", EXAMS_PATH)[0]!.body as Wire;
      expect(body).toMatchObject({ kind: "HSA", scope: "SUBJECT", buildMode: "REVIEW", overview: [{ partId: "HSA_REVIEW_PART", questionCount: 10, durationMinutes: 20 }] });
      expect(body.maxScore ?? null).toBeNull();
      expect(JSON.stringify(body)).not.toContain("requiredPointsPerQuestion");
    }, 30_000);

    it("HSA nhập thang 100: gửi maxScore 100, không điểm mỗi câu", async () => {
      const server = createServer(SERVER_TEMPLATES).on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
      const user = await renderPage();
      await fillReview(user, "100");
      const body = server.calls("POST", EXAMS_PATH)[0]!.body as Wire;
      expect(body).toMatchObject({ buildMode: "REVIEW", maxScore: 100, overview: [{ partId: "HSA_REVIEW_PART", questionCount: 10, durationMinutes: 20 }] });
      expect(JSON.stringify(body)).not.toContain("requiredPointsPerQuestion");
    }, 30_000);

    it.each([
      ["không nhập thang", null],
      ["nhập thang 300", "300"],
    ])("V-ACT %s: không lấy thang tối đa của kỳ thi, chỉ gửi thang đã nhập", async (_label, scale) => {
      catalogue.vactScopes = ["FULL", "PART", "SUBJECT"];
      try {
        const server = createServer(SERVER_TEMPLATES).on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
        const user = await renderPage();
        await pickKind(user, "V-ACT");
        await fillReview(user, scale);
        const body = server.calls("POST", EXAMS_PATH)[0]!.body as Wire;
        expect(body).toMatchObject({ kind: "VACT", buildMode: "REVIEW", durationMinutes: 20 });
        expect(body.maxScore ?? null).toBe(scale === null ? null : Number(scale));
        expect(JSON.stringify(body)).not.toContain("requiredPointsPerQuestion");
      } finally {
        catalogue.vactScopes = null;
      }
    }, 30_000);
  });

  it("ô Môn chỉ chào môn có mẫu; Theo phần của SPT chỉ chào môn mà mẫu Theo môn STANDARD có phần (không tính bài ôn)", async () => {
    // Vật lí có mẫu Theo môn nhưng chưa có phần: chào ở Theo môn, không chào ở Theo phần. Mẫu bài ôn (không ghi môn) không tính.
    const SPT_PHYSICS = template("SPT_PHYSICS_V1", { kindCode: "SPT", subjectCode: "PHYSICS", structureYear: 2025, maxScore: 10 }, []);
    createServer([...ALL_TEMPLATES, SPT_PHYSICS]).install();
    const user = await renderPage();
    await pickKind(user, "SPT");
    const subjects = async () => {
      await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
      const names = within(await screen.findByRole("listbox")).getAllByRole("option").map((option) => option.textContent);
      await user.keyboard("{Escape}");
      return names;
    };
    // Theo môn: môn có mẫu (kể cả chỉ có bài ôn), không có Hóa học hay môn khác không có mẫu.
    expect(await subjects()).toEqual(["Toán", "Vật lí"]);
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(await subjects()).toEqual(["Toán"]);
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
    ["kỳ thi", (user: UserEvent) => pickKind(user, "QDA")],
    ["phần", (user: UserEvent) => user.click(screen.getByRole("radio", { name: "Văn" }))],
  ])("đổi %s sau khi sửa bảng: hỏi xác nhận, Giữ lựa chọn cũ không mất số đã sửa", async (_label, change) => {
    createServer([...ALL_TEMPLATES, HSA_PART_MATH, HSA_PART_LIT]).install();
    const user = await renderPage();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    expect(await screen.findByRole("radio", { name: "Toán" })).toBeChecked();
    const count = screen.getByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "7");
    await change(user);
    const dialog = await screen.findByRole("alert");
    expect(within(dialog).getByText(/Đổi sang/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Giữ lựa chọn cũ" }));
    expect(kindSelect()).toHaveTextContent(/\(HSA\)/);
    expect(screen.getByRole("radio", { name: "Toán" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: /^Số câu của/ })).toHaveValue("7");
  }, 20_000);
});

describe("trang Tạo bài thi, nhánh thật: bảng tổng quan theo kỳ thi", () => {
  it("SPT Toán: nhóm Tự luận tạm bỏ qua hiện mờ, không có ô nhập, không tính vào tổng; thời lượng chung phải nhập", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await pickKind(user, "SPT");
    const omitted = (await screen.findByText("Tự luận")).closest("tr")!;
    expect(within(omitted).getByText("tạm bỏ qua")).toBeInTheDocument();
    expect(within(omitted).queryByRole("textbox")).toBeNull();
    expect(omitted.className).not.toContain("opacity");
    expect(screen.getAllByRole("textbox", { name: /^Số câu của/ })).toHaveLength(1);
    expect(within(screen.getByRole("region", { name: "Tóm tắt" })).getByText("Tổng số câu").parentElement).toHaveTextContent("22");

    // Mẫu V1 chưa ghi thời lượng: một đồng hồ chung, quản trị viên nhập; ô trống báo câu F-1 của bản vẽ.
    await user.type(name(), "SPT Toán V1");
    await user.click(submit());
    expect(await screen.findAllByText("Chưa nhập thời lượng. Môn này không có thời lượng mặc định.")).not.toHaveLength(0);
    expect(server.calls("POST", EXAMS_PATH)).toHaveLength(0);
    const shared = screen.getByRole("textbox", { name: /^Thời lượng chung/ });
    expect(shared).toBeInvalid();
    expect(shared).toHaveAccessibleDescription("Chưa nhập thời lượng. Môn này không có thời lượng mặc định.");
    await user.type(shared, "90");
    await user.click(submit());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(server.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ durationMinutes: 90 });
  }, 20_000);

  it("BCA: thời lượng chung, cột Điểm 25/60/15, phần nhiều môn có dòng môn thụt vào; sửa điểm thì thang của bài là tổng các phần", async () => {
    const server = createServer().on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() }).install();
    const user = await renderPage();
    await pickKind(user, "BCA");
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
    await pickKind(user, "V-ACT");
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
    await pickKind(user, "SPT");
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

  it("422 (tổng quan không hợp lệ): báo ở dưới biểu mẫu, giữ nguyên số đã nhập", async () => {
    createServer().on("POST", EXAMS_PATH, { status: 422, body: { code: "EXAM_STRUCTURE_INVALID", fieldErrors: [] } }).install();
    const user = await renderPage();
    await user.type(name(), "HSA thử");
    const count = screen.getByRole("textbox", { name: /^Số câu của/ });
    await user.clear(count);
    await user.type(count, "12");
    await user.click(submit());
    expect(await screen.findByText(/Cấu trúc tổng quan chưa hợp lệ/)).toBeInTheDocument();
    expect(count).toHaveValue("12");
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 20_000);

  it("422 theo mã: hiện câu đã dịch, không in thân lỗi của máy chủ; mã lạ thì câu chung", async () => {
    const body = { code: "EXAM_TEMPLATE_MISMATCH", message: "RAW-SERVER-TEXT", fieldErrors: [{ field: "subjectCode", code: "X" }] };
    createServer()
      .on("POST", EXAMS_PATH, { status: 422, body }, { status: 422, body: { code: "SOMETHING_NEW", message: "RAW-SERVER-TEXT" } })
      .install();
    const user = await renderPage();
    await user.type(name(), "HSA thử");
    await user.click(submit());
    expect(await screen.findByText("Chưa có mẫu mặc định khớp với lựa chọn này. Chọn môn hoặc phần khác.")).toBeInTheDocument();
    expect(screen.queryByText(/RAW-SERVER-TEXT|fieldErrors|EXAM_TEMPLATE_MISMATCH/)).toBeNull();
    await user.click(submit());
    expect(await screen.findByText(/Cấu trúc khởi tạo chưa hợp lệ/)).toBeInTheDocument();
    expect(screen.queryByText(/RAW-SERVER-TEXT/)).toBeNull();
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
