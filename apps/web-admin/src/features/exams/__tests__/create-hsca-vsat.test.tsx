import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ContentReference } from "@/features/content/types";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { CreateExamPage } from "../components/create-exam-page";
import { ExamsBackend } from "./fake-exams-backend";
import { examDetailWire } from "./fake-exams-server";

/**
 * Bước 1 Tạo bài thi cho H-SCA, V-SAT (UNI-2694, FE-6; bản vẽ `TaoBaiThiHscaVsat2B`). Mẫu cấu trúc là dữ liệu giả theo spec
 * H-SCA, V-SAT mục 4, 5: mẫu thật của BE-18, BE-21 chưa có trên nhánh này; QA chạy lại trên máy chủ thật.
 */

const referenceMock = vi.hoisted(() => ({ patch: null as ((reference: ContentReference, locale: string) => void) | null }));
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: "vi" | "en") => {
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

type Wire = Record<string, unknown>;

const block = (name: string, count: number, extra: Wire = {}) => ({ id: null, name, questionType: "SINGLE_CHOICE", questionCount: count, ...extra });
const section = (subjectCode: string, count: number, extra: Wire = {}, blocks: Wire[] = [block("Trắc nghiệm", count)]) => ({
  id: null,
  subjectCode,
  questionCount: count,
  blocks,
  ...extra,
});
const part = (order: number, name: string, count: number, sections: Wire[], extra: Wire = {}) => ({
  id: null,
  order,
  name,
  durationMinutes: null,
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
  fixedBlockCounts: false,
  omittedLocked: false,
  ...fields,
  code,
  structure: { parts },
});
const omittedPart = (order: number, name: string, subjectCode: string) =>
  part(order, name, 0, [section(subjectCode, 0, {}, [block("Viết", 1, { questionType: "ESSAY", omitted: true })])]);

const HSCA = { kindCode: "HSCA", maxScore: 10 };
const VSAT = { kindCode: "VSAT", maxScore: 150 };
const hscaSciencePart = (order: number, name: string, count: number) => part(order, name, count, [section("MATH", count)]);
const vsatPart = (order: number, name: string, count: number, subject = "MATH") => part(order, name, count, [section(subject, count)]);

const TEMPLATES: Wire[] = [
  wireTemplate("HSCA_MATH", { ...HSCA, subjectCode: "MATH", durationMinutes: 90, fixedBlockCounts: true }, [
    hscaSciencePart(1, "Phần 1 · Trắc nghiệm", 25),
    hscaSciencePart(2, "Phần 2 · Dữ liệu tổng hợp", 5),
    hscaSciencePart(3, "Phần 3 · Điền đáp án", 10),
  ]),
  wireTemplate("HSCA_ENGLISH", { ...HSCA, subjectCode: "ENGLISH", durationMinutes: null }, [
    part(1, "Nghe", 35, [section("ENGLISH", 35, { audioRequirement: "REQUIRED" })]),
    part(2, "Đọc", 40, [section("ENGLISH", 40, { audioRequirement: "FORBIDDEN" })]),
  ]),
  wireTemplate("HSCA_LITERATURE", { ...HSCA, subjectCode: "LITERATURE", durationMinutes: null, omittedLocked: true }, [
    part(1, "Đọc hiểu", 20, [section("LITERATURE", 20)]),
    omittedPart(2, "Viết đoạn", "LITERATURE"),
    omittedPart(3, "Viết bài", "LITERATURE"),
  ]),
  wireTemplate("VSAT_MATH", { ...VSAT, subjectCode: "MATH", durationMinutes: 90, fixedBlockCounts: true }, [
    vsatPart(1, "Phần I · Đúng/Sai", 9),
    vsatPart(2, "Phần II · Một đáp án", 6),
    vsatPart(3, "Phần III · Ghép đôi", 5),
    vsatPart(4, "Phần IV · Trả lời ngắn", 5),
  ]),
  wireTemplate("VSAT_LITERATURE", { ...VSAT, subjectCode: "LITERATURE", durationMinutes: null, omittedLocked: true, fixedBlockCounts: true }, [
    vsatPart(1, "Phần I · Đúng/Sai", 9, "LITERATURE"),
    vsatPart(2, "Phần II · Một đáp án", 6, "LITERATURE"),
    vsatPart(3, "Phần III · Ghép đôi", 5, "LITERATURE"),
    omittedPart(4, "Phần IV · Viết", "LITERATURE"),
  ]),
  wireTemplate("VSAT_ENGLISH", { ...VSAT, subjectCode: "ENGLISH", durationMinutes: 60, fixedBlockCounts: true }, [
    part(1, "Tiếng Anh · cả bài", 25, [section("ENGLISH", 25)], {
      displaySegments: [{ code: "I", name: "Phần I", fromPosition: 1, toPosition: 25, commandCount: 67, maxPoints: 150 }],
    }),
  ]),
  ...[
    [2, "Phần II · Một đáp án", 6],
    [3, "Phần III · Ghép đôi", 5],
    [4, "Phần IV · Điền khuyết", 5],
  ].map(([order, name, count]) =>
    wireTemplate(`VSAT_ENGLISH_P${order}`, { ...VSAT, scope: "PART", partCode: `P${order}`, subjectCode: "ENGLISH", durationMinutes: null }, [
      part(1, name as string, count as number, [section("ENGLISH", count as number)]),
    ]),
  ),
  wireTemplate("THPTQG_MATH", { kindCode: "THPTQG", subjectCode: "MATH", maxScore: 10, durationMinutes: 90 }, [
    part(1, "Phần I", 12, [section("MATH", 12)]),
    part(2, "Phần II", 4, [section("MATH", 4)]),
  ]),
  // D25: Ngữ văn và Tiếng Anh THPTQG là mẫu môn một phần tính giờ theo phần (mẫu thật V202611181305).
  wireTemplate("THPTQG_LITERATURE", { kindCode: "THPTQG", subjectCode: "LITERATURE", timing: "PER_PART", maxScore: 10, durationMinutes: 120 }, [
    part(1, "Ngữ văn", 7, [section("LITERATURE", 7, {}, [block("Đọc hiểu", 5, { questionType: "QUESTION_SET" })])], { durationMinutes: 120 }),
  ]),
  wireTemplate("THPTQG_ENGLISH", { kindCode: "THPTQG", subjectCode: "ENGLISH", timing: "PER_PART", maxScore: 10, durationMinutes: 50 }, [
    part(1, "Tiếng Anh", 40, [section("ENGLISH", 40, {}, [block("Đọc điền", 6, { questionType: "QUESTION_SET" })])], { durationMinutes: 50 }),
  ]),
];

/** Hồ sơ kỳ thi của BE-16 (chưa có trong danh mục mẫu): H-SCA, V-SAT một đồng hồ chung cho cả môn; THPTQG thêm Theo phần. */
function patchProfiles(reference: ContentReference, locale: string) {
  const base = reference.examKinds.find((kind) => kind.code === "SPT")!;
  const en = locale === "en";
  const profile = { ...base, templateBy: "SUBJECT" as const, timing: "SHARED" as const, maxScore: null, scopes: ["SUBJECT" as const, "PART" as const] };
  reference.examKinds.push(
    { ...profile, code: "HSCA", name: en ? "H-SCA assessment (HSCA)" : "Đánh giá H-SCA (HSCA)" },
    { ...profile, code: "VSAT", name: en ? "V-SAT assessment (VSAT)" : "Đánh giá V-SAT (VSAT)" },
  );
  reference.examKinds.find((kind) => kind.code === "THPTQG")!.scopes = ["SUBJECT", "PART"];
}

let backend: ExamsBackend;

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
  referenceMock.patch = patchProfiles;
});

async function renderPage(locale: "vi" | "en" = "vi") {
  backend = new ExamsBackend();
  backend.server.on("GET", TEMPLATES_PATH, { status: 200, body: TEMPLATES });
  backend.server.on("POST", EXAMS_PATH, { status: 201, body: examDetailWire() });
  backend.install();
  const user = userEvent.setup();
  renderWithProviders(<CreateExamPage />, locale === "en" ? { locale, messages: en } : {});
  await screen.findByRole("form", { name: locale === "en" ? "Create exam" : "Tạo bài thi" });
  return user;
}

const SUBMIT = "Lưu và tiếp tục";
const submitButton = () => screen.getAllByRole("button", { name: SUBMIT })[0]!;
const nameInput = () => screen.getByRole("textbox", { name: /Tên bài thi/ });
const sharedDuration = () => screen.getByRole("textbox", { name: /^Thời lượng chung/ });
const countInputs = () => screen.getAllByRole("textbox", { name: /^Số câu của/ });
const scopeLabels = () =>
  within(screen.getByRole("group", { name: /Phạm vi bài thi/ }))
    .getAllByRole("radio")
    .map((radio) => radio.closest("label")?.textContent ?? "");
const partLabels = () =>
  within(screen.getByRole("group", { name: /^Phần thi/ }))
    .getAllByRole("radio")
    .map((radio) => radio.closest("label")?.textContent ?? "");

async function pickKind(user: UserEvent, code: string) {
  await user.click(screen.getByRole("combobox", { name: /^Kỳ thi/ }));
  await user.click(await screen.findByRole("option", { name: new RegExp(`\\(${code}\\)$`) }));
}
async function pickSubject(user: UserEvent, label: string) {
  await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
  await user.click(await screen.findByRole("option", { name: label }));
}
const chooseScope = (user: UserEvent, name: RegExp) => user.click(screen.getByRole("radio", { name }));

describe("bước 1 cho H-SCA, V-SAT: phạm vi và môn", () => {
  it("dòng 1 · H-SCA Tiếng Anh: Nghe, Đọc, Nghe và Đọc; tên dòng bảng theo audio, không còn chữ lẫn ngôn ngữ", async () => {
    const user = await renderPage();
    await pickKind(user, "HSCA");
    await pickSubject(user, "Tiếng Anh");
    expect(await screen.findByRole("radio", { name: /^Nghe và Đọc/ })).toBeChecked();
    expect(scopeLabels().map((label) => label.replace(/\d.*$/, ""))).toEqual(["Nghe", "Đọc", "Nghe và Đọc"]);
    expect(scopeLabels()[0]).toContain("35 câu · chỉ lấy câu có audio");
    expect(scopeLabels()[1]).toContain("40 câu · chỉ lấy câu không có audio");
    expect(scopeLabels()[2]).toContain("75 câu · cả hai phần");
    expect(screen.queryByRole("group", { name: /^Phần thi/ })).toBeNull();
    expect(countInputs()).toHaveLength(2);

    await chooseScope(user, /^Nghe(?! và)/);
    await waitFor(() => expect(countInputs()).toHaveLength(1));
    expect(screen.getByRole("textbox", { name: "Số câu của Nghe" })).toHaveValue("35");
    await chooseScope(user, /^Đọc/);
    expect(await screen.findByRole("textbox", { name: "Số câu của Đọc" })).toHaveValue("40");
  }, 30_000);

  it("dòng 1 · tiếng Anh: thẻ phạm vi và tên dòng không có chữ tiếng Việt", async () => {
    const user = await renderPage("en");
    await user.click(screen.getByRole("combobox", { name: /^Exam type/ }));
    await user.click(await screen.findByRole("option", { name: /\(HSCA\)$/ }));
    await user.click(screen.getByRole("combobox", { name: /^Subject/ }));
    await user.click(await screen.findByRole("option", { name: "English" }));
    const group = await screen.findByRole("group", { name: /^Exam scope/ });
    const cards = within(group).getAllByRole("radio").map((radio) => radio.closest("label")?.textContent ?? "");
    expect(cards.map((card) => card.replace(/\d.*$/, ""))).toEqual(["Listening", "Reading", "Listening and Reading"]);
    const table = screen.getByRole("region", { name: "Exam structure overview table" });
    expect(within(table).getByRole("textbox", { name: "Questions of Listening" })).toBeInTheDocument();
    expect(within(table).getByRole("textbox", { name: "Questions of Reading" })).toBeInTheDocument();
    const text = [group.textContent, table.textContent].join(" ");
    expect(text).not.toMatch(/Nghe|Đọc|câu/);
  }, 30_000);

  it("kỳ thi hiện tên H-SCA, V-SAT, không hiện mã thô; phạm vi cả môn gọi là Cả môn", async () => {
    const user = await renderPage();
    await pickKind(user, "VSAT");
    expect(screen.getAllByText(/V-SAT/).length).toBeGreaterThan(0);
    expect(screen.queryByText("VSAT")).toBeNull();
    expect(scopeLabels()).toEqual([
      expect.stringMatching(/^Cả môn/),
      expect.stringMatching(/^Theo phần/),
    ]);
    expect(screen.queryByText("FULL")).toBeNull();
  }, 20_000);

  it("dòng 5 · V-SAT Tiếng Anh theo phần: chỉ có Phần II, III, IV (mẫu Theo phần riêng)", async () => {
    const user = await renderPage();
    await pickKind(user, "VSAT");
    await pickSubject(user, "Tiếng Anh");
    await chooseScope(user, /^Theo phần/);
    await waitFor(() => expect(partLabels()).toEqual(["Phần II · Một đáp án", "Phần III · Ghép đôi", "Phần IV · Điền khuyết"]));
    expect(screen.getByText(/chỉ luyện riêng được các phần trong danh sách/)).toBeInTheDocument();
    expect(countInputs()).toHaveLength(1);
    expect(screen.getByRole("textbox", { name: "Số câu của Phần II · Một đáp án" })).toHaveValue("6");
  }, 30_000);

  it("dòng 10 · Ngữ văn theo phần không có phần viết đã lược: H-SCA chỉ Đọc hiểu, V-SAT Phần I, II, III", async () => {
    const user = await renderPage();
    await pickKind(user, "HSCA");
    await pickSubject(user, "Ngữ văn");
    await chooseScope(user, /^Theo phần/);
    await waitFor(() => expect(partLabels()).toEqual(["Đọc hiểu"]));
    expect(screen.getByText("Phần viết chưa có trong V1 nên không chọn được.")).toBeInTheDocument();

    await pickKind(user, "VSAT");
    await pickSubject(user, "Ngữ văn");
    await waitFor(() => expect(partLabels()).toEqual(["Phần I · Đúng/Sai", "Phần II · Một đáp án", "Phần III · Ghép đôi"]));
  }, 30_000);
});

describe("bước 1 cho H-SCA, V-SAT: thời lượng theo mẫu và phạm vi", () => {
  it("dòng 3 · V-SAT Toán cả môn: thời lượng 90 phút điền sẵn", async () => {
    const user = await renderPage();
    await pickKind(user, "VSAT");
    await pickSubject(user, "Toán");
    await waitFor(() => expect(sharedDuration()).toHaveValue("90"));
  }, 20_000);

  it("dòng 2 · V-SAT Ngữ văn cả môn: mẫu để trống thời lượng nên bắt buộc nhập, bỏ trống không tạo được", async () => {
    const user = await renderPage();
    await pickKind(user, "VSAT");
    await pickSubject(user, "Ngữ văn");
    await waitFor(() => expect(sharedDuration()).toHaveValue(""));
    await user.type(nameInput(), "V-SAT Ngữ văn");
    await user.click(submitButton());
    await waitFor(() => expect(sharedDuration()).toBeInvalid());
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);

    await user.type(sharedDuration(), "120");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(backend.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ kind: "VSAT", scope: "SUBJECT", subjectCode: "LITERATURE", durationMinutes: 120 });
  }, 30_000);

  it("dòng 4 · H-SCA Toán theo phần: bắt buộc nhập thời lượng riêng, không lấy 90 phút của cả môn", async () => {
    const user = await renderPage();
    await pickKind(user, "HSCA");
    await pickSubject(user, "Toán");
    await waitFor(() => expect(sharedDuration()).toHaveValue("90"));
    await chooseScope(user, /^Theo phần/);
    await waitFor(() => expect(sharedDuration()).toHaveValue(""));
    await user.type(nameInput(), "H-SCA Toán Phần 2");
    await user.click(submitButton());
    const message = "Chưa nhập thời lượng. Bài theo phần phải có thời lượng riêng.";
    await screen.findByText(message, { selector: "p" });
    expect(sharedDuration()).toHaveAccessibleDescription(message);
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
  }, 30_000);

  it("dòng 4 · V-SAT Tiếng Anh Phần IV: mẫu Theo phần riêng để trống thời lượng nên bắt buộc nhập", async () => {
    const user = await renderPage();
    await pickKind(user, "VSAT");
    await pickSubject(user, "Tiếng Anh");
    await waitFor(() => expect(sharedDuration()).toHaveValue("60"));
    await chooseScope(user, /^Theo phần/);
    await user.click(await screen.findByRole("radio", { name: /Phần IV/ }));
    await waitFor(() => expect(sharedDuration()).toHaveValue(""));
    await user.type(nameInput(), "V-SAT Anh IV");
    await user.click(submitButton());
    await waitFor(() => expect(sharedDuration()).toBeInvalid());
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
    await user.type(sharedDuration(), "15");
    await user.click(submitButton());
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(backend.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ kind: "VSAT", scope: "PART", partCode: "P4", subjectCode: "ENGLISH", durationMinutes: 15 });
  }, 30_000);

  it("dòng 6 · THPTQG theo phần: thời lượng chung không lấy từ mẫu môn, người tạo nhập (không đổi)", async () => {
    const user = await renderPage();
    await pickKind(user, "THPTQG");
    await waitFor(() => expect(sharedDuration()).toHaveValue("90"));
    await chooseScope(user, /^Theo phần/);
    await waitFor(() => expect(sharedDuration()).toHaveValue(""));
    await user.type(nameInput(), "THPTQG Toán Phần I");
    await user.click(submitButton());
    const message = "Chưa nhập thời lượng. Bài theo phần phải có thời lượng riêng.";
    await screen.findByText(message, { selector: "p" });
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);
  }, 30_000);
  it("dòng 6 (D25) · THPTQG Ngữ văn, Tiếng Anh theo phần: mẫu môn một phần PER_PART giữ thời lượng, ô nhập được", async () => {
    const user = await renderPage();
    await pickKind(user, "THPTQG");
    await pickSubject(user, "Ngữ văn");
    await chooseScope(user, /^Theo phần/);
    // Một phần duy nhất nên chọn sẵn; mẫu PER_PART mang sẵn thời lượng riêng của phần.
    const litDuration = await screen.findByRole("textbox", { name: "Thời lượng của Ngữ văn" });
    await waitFor(() => expect(litDuration).toHaveValue("120"));
    expect(litDuration).not.toHaveAttribute("readonly");
    expect(litDuration).toBeEnabled();
    await user.clear(litDuration);
    await user.type(litDuration, "130");
    await waitFor(() => expect(litDuration).toHaveValue("130"));

    // Đổi môn sau khi sửa ô thì bảng hỏi nạp lại (H35).
    await pickSubject(user, "Tiếng Anh");
    await user.click(await screen.findByRole("button", { name: "Đổi và nạp lại" }));
    const engDuration = await screen.findByRole("textbox", { name: "Thời lượng của Tiếng Anh" });
    await waitFor(() => expect(engDuration).toHaveValue("50"));
    expect(engDuration).toBeEnabled();
  }, 30_000);

});

describe("bước 1 cho H-SCA, V-SAT: khóa dòng của mẫu (H42)", () => {
  it("dòng 11 · bài cả môn từ mẫu fixedBlockCounts: ô số câu chỉ đọc; bài theo phần từ cùng mẫu nhập được", async () => {
    const user = await renderPage();
    await pickKind(user, "HSCA");
    await pickSubject(user, "Toán");
    await waitFor(() => expect(countInputs()).toHaveLength(3));
    for (const input of countInputs()) expect(input).toHaveAttribute("readonly");
    expect(screen.getByText("Mẫu đã khóa số câu")).toBeInTheDocument();
    expect(screen.getByText(/Muốn đổi số lượng, chọn phạm vi Theo phần/)).toBeInTheDocument();

    await chooseScope(user, /^Theo phần/);
    await waitFor(() => expect(countInputs()).toHaveLength(1));
    expect(countInputs()[0]).not.toHaveAttribute("readonly");
    expect(screen.queryByText("Mẫu đã khóa số câu")).toBeNull();
  }, 30_000);

  it("mẫu không khóa dòng (H-SCA Ngữ văn, bài ôn): số câu nhập được", async () => {
    const user = await renderPage();
    await pickKind(user, "HSCA");
    await pickSubject(user, "Ngữ văn");
    const [first] = await screen.findAllByRole("textbox", { name: /^Số câu của/ });
    expect(first).not.toHaveAttribute("readonly");
    expect(screen.queryByText("Mẫu đã khóa số câu")).toBeNull();
  }, 20_000);
});
