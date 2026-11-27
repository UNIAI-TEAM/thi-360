import { QueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NewExamPage from "@/app/(shell)/exams/new/page";
import { ExamEditorLayout } from "../components/exam-editor-layout";
import { ExamGeneralForm } from "../components/exam-general-form";
import { EXAMS_QUERY_KEYS } from "../keys";
import type { ExamDetail, ExamPart, QuestionBlock } from "../types";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireExam, toWireTemplate } from "./fake-exams-backend";
import { examDetailWire } from "./fake-exams-server";
import { renderExamsUi } from "./render-exams-ui";
import { byTextContent } from "./text-content";

/**
 * UNI-2779 (hợp nhất từ UNI-2751): sửa thông tin chung của bài đã lưu trực tiếp trên `/exams/<id>` bằng khung chung
 * `ExamEditorLayout` (5 tab) + `ExamGeneralForm` (`mode="edit"`), lưu xong ở nguyên trên `/exams/<id>`; đường dẫn cũ
 * `/exams/new?exam=<id>` chuyển hướng về `/exams/<id>`, còn `?exam=` rỗng mở màn tạo mới.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn(), redirect: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => window.location.pathname,
  redirect: routerMock.redirect,
}));

const EXAM_ID = "ex-edit-step1";
const EXAM_PATH = `/api/v1/admin/exams/${EXAM_ID}`;
const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const FORM_NAME = "Thông tin bài thi";
const SAVE = "Tiếp tục: Cấu trúc và ma trận";
const MATH = "Toán học và xử lí số liệu";
const LITERATURE = "Ngôn ngữ – Văn học";
const SCIENCE = "Khoa học tự nhiên";

const block = (id: string, count: number): QuestionBlock => ({
  id,
  name: `Khối ${id}`,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

const hsaPart = (order: number, name: string, count: number, minutes: number | null, subjectCode: string): ExamPart => ({
  id: `part-${order}`,
  order,
  name,
  durationMinutes: minutes,
  questionCount: count,
  questionCountOnPaper: 50,
  questionCountRequired: count,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [
    {
      id: `sec-${order}`,
      subjectCode,
      questionCount: count,
      knowledgeScope: null,
      answerQuotas: null,
      matrix: null,
      choiceGroup: null,
      blocks: [block(`blk-${order}`, 50)],
    },
  ],
  options: [],
});

let backend: ExamsBackend;
let seeded: ExamDetail;

function setup(override: Partial<ExamDetail> = {}, configure?: (backend: ExamsBackend) => void) {
  routerMock.push.mockReset();
  routerMock.redirect.mockReset();
  backend = new ExamsBackend();
  configure?.(backend);
  backend.install();
  seeded = {
    ...backend.app("ex-hsa-f-001"),
    id: EXAM_ID,
    code: "HSA-EDIT-001",
    name: "HSA quay lại bước 1",
    kind: "HSA",
    scope: "FULL",
    timing: "PER_PART",
    templateCode: "HSA_FULL",
    servingVersion: null,
    parts: [hsaPart(1, MATH, 45, 75, "MATH"), hsaPart(2, LITERATURE, 50, 60, "LITERATURE"), hsaPart(3, SCIENCE, 50, 60, "PHYSICS")],
    ...override,
  };
  backend.seedExam(seeded);
}

function renderEditLayout(client?: QueryClient) {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
  return renderExamsUi(
    <ExamEditorLayout examId={EXAM_ID}>
      <ExamGeneralForm mode="edit" examId={EXAM_ID} />
    </ExamEditorLayout>,
    client ? { client } : {},
  );
}

async function open(client?: QueryClient) {
  const view = renderEditLayout(client);
  await screen.findByRole("form", { name: FORM_NAME });
  await screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
  return view;
}

const countOf = (name: string) => screen.getByRole("textbox", { name: `Số câu của ${name}` });

/** Mẫu Theo môn (STANDARD) của Toán và mẫu bài ôn HSA như máy chủ trả: có mẫu thì môn đã lưu được giữ khi đổi cách dựng bài. */
function subjectTemplates(server: ExamsBackend) {
  const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
  const standard = toWireTemplate({ ...structuredClone(hsaFull), code: "HSA_SUBJECT_MATH", scope: "SUBJECT", subjectCode: "MATH", parts: [structuredClone(hsaFull.parts[0]!)] });
  const review = {
    ...toWireTemplate({
      ...structuredClone(hsaFull),
      code: "HSA_REVIEW",
      scope: "SUBJECT",
      subjectCode: null,
      maxScore: null,
      parts: [{ ...structuredClone(hsaFull.parts[0]!), name: "Review", questionCount: 0, durationMinutes: null, sections: [] }],
    }),
    buildMode: "REVIEW" as const,
  };
  server.server.on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [...FIXTURE_TEMPLATES.map(toWireTemplate), standard, review] });
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Tab Thông tin chung hợp nhất ở chế độ sửa (/exams/<id>, UNI-2779)", () => {
  it("AC-1697-1: màn sửa hợp nhất (thanh 5 tab ở Thông tin chung, ba khối đánh số, Tóm tắt + Tình trạng) điền sẵn theo bài đã lưu", async () => {
    setup();
    await open();
    expect(screen.getByRole("heading", { name: "HSA quay lại bước 1", level: 1 })).toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: "Các phần của bài thi" });
    expect(within(nav).getByRole("link", { name: "1. Thông tin chung" })).toHaveAttribute("aria-current", "page");
    const headings = screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent);
    expect(headings).toEqual(expect.arrayContaining(["Thông tin chung", "Phạm vi bài thi", "Cấu trúc tổng quan", "Tóm tắt", "Tình trạng"]));
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("HSA quay lại bước 1");
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-EDIT-001");
    const table = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    expect(within(table).getAllByRole("textbox", { name: /^Số câu của/ }).map((input) => (input as HTMLInputElement).value)).toEqual(["45", "50", "50"]);
    expect(screen.queryByText(/Bài được tạo ở trạng thái Nháp/)).not.toBeInTheDocument();
  }, 30_000);

  it("R1-16: chế độ tạo mới vẫn hiện ghi chú trạng thái Nháp trong Tóm tắt", async () => {
    setup();
    window.history.replaceState(null, "", "/exams/new");
    renderExamsUi(
      <ExamEditorLayout>
        <ExamGeneralForm mode="create" />
      </ExamEditorLayout>,
    );
    await screen.findByRole("form", { name: "Tạo bài thi" });
    expect(within(screen.getByRole("region", { name: "Tóm tắt" })).getByText(/Bài được tạo ở trạng thái Nháp/)).toBeInTheDocument();
  }, 30_000);

  it("V1: chế độ sửa giữ mã bài thi đã lưu ngay cả khi QueryClient đã lưu mã gợi ý từ lượt tạo bài trước", async () => {
    setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(EXAMS_QUERY_KEYS.suggestedCode({ kind: "HSA", scope: "FULL", partCode: null, subjectCode: null }), "HSA-F-001");
    await open(client);
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-EDIT-001");
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).not.toHaveValue("HSA-F-001");
  }, 30_000);

  it("chế độ sửa vẫn cho phép bấm Tự sinh mã để lấy mã mới khi người dùng chủ động yêu cầu", async () => {
    setup({}, (server) => {
      server.server.on("GET", "/api/v1/admin/exams/suggested-code", { status: 200, body: { code: "HSA-F-999" } });
    });
    const user = userEvent.setup();
    await open();
    expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-EDIT-001");
    await user.click(screen.getByRole("button", { name: "Tự sinh mã" }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Mã bài thi/ })).toHaveValue("HSA-F-999"));
  }, 30_000);

  it("AC-1697-2: sửa số câu phần 1 rồi Lưu thông tin ghi cấu trúc của bài đó (không tạo bài mới) và ở nguyên tại tab 1", async () => {
    setup();
    const user = userEvent.setup();
    await open();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "40");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1));
    const body = backend.calls("PUT", STRUCTURE_PATH)[0]!.body as { expectedRevision: number; parts: ExamPart[] };
    expect(body.expectedRevision).toBe(seeded.revision);
    expect(body.parts[0]).toMatchObject({ id: "part-1", questionCount: 40 });
    expect(backend.calls("POST", "/api/v1/admin/exams")).toHaveLength(0);
    expect(backend.app(EXAM_ID).parts[0]!.questionCount).toBe(40);
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("không sửa gì: bấm Tiếp tục không gửi ghi và chuyển sang bước 2", async () => {
    setup();
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("button", { name: SAVE }));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`);
  }, 30_000);

  it("AC-1697-4: đổi phạm vi sang Theo phần thì hỏi nạp lại cấu trúc, xác nhận mới gửi confirmReplace rồi sang bước 2", async () => {
    const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
    const englishPart = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_SINGLE")!;
    setup({}, (server) =>
      server.server.on("GET", "/api/v1/admin/exam-templates", {
        status: 200,
        body: [toWireTemplate(hsaFull), toWireTemplate({ ...structuredClone(englishPart), code: "HSA_PART_ANH", partCode: "ANH" })],
      }),
    );
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Theo phần/ }));
    await user.click(screen.getByRole("button", { name: SAVE }));

    const dialog = await screen.findByRole("dialog");
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ kind: "HSA", scope: "PART", confirmReplace: true, expectedRevision: seeded.revision });
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("AC-1697-5a: bài chưa công bố đổi được cách dựng bài; Lưu thông tin hỏi xác nhận, xác nhận gửi buildMode + confirmReplace và máy chủ dựng lại cấu trúc", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, subjectTemplates);
    const user = userEvent.setup();
    await open();
    const buildModeGroup = screen.getByRole("group", { name: /Cách dựng bài/ });
    expect(buildModeGroup).toBeEnabled();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));

    // R1-1 / R1-2 / R1-3..5 / R1-22: bảng xem trước bài ôn (0 câu) chỉ đọc toàn bộ, không báo lỗi ở ô và ở Tóm tắt.
    expect(screen.queryByText("Số câu phải là số nguyên dương.")).toBeNull();
    const summaryPanel = screen.getByRole("region", { name: "Tóm tắt" });
    expect(within(summaryPanel).getByText("Không còn mục cần xử lý")).toBeInTheDocument();
    expect(within(within(summaryPanel).getByRole("heading", { name: /Cần xử lý/ })).getByText("0")).toBeInTheDocument();
    const table = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    expect(within(table).getByRole("textbox", { name: /^Số câu của/ })).toHaveAttribute("readonly");
    expect(within(table).getByRole("textbox", { name: /^Thời lượng của/ })).toHaveAttribute("readonly");
    expect(within(table).getByRole("textbox", { name: "Điểm tối đa" })).toHaveAttribute("readonly");

    await user.click(screen.getByRole("button", { name: SAVE }));

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("Đổi cách dựng bài sẽ tạo lại cấu trúc như đề mới. Cấu trúc, ma trận và nhóm lấy câu hiện tại sẽ bị thay."),
    ).toBeInTheDocument();
    expect(within(dialog).getByText(byTextContent("1. Toán"))).toBeInTheDocument();
    expect(within(dialog).queryByText(/Review/)).not.toBeInTheDocument();
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Tạo lại cấu trúc" }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "REVIEW",
      confirmReplace: true,
      expectedRevision: seeded.revision,
    });
    expect(backend.app(EXAM_ID).buildMode).toBe("REVIEW");
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("AC-1697-5b: Quay lại ở hộp xác nhận đổi cách dựng bài không gửi gì, cách dựng bài đã lưu giữ nguyên", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, subjectTemplates);
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));
    await user.click(screen.getByRole("button", { name: SAVE }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Quay lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.app(EXAM_ID).buildMode).toBe("STANDARD");
    expect(routerMock.push).not.toHaveBeenCalled();
  }, 30_000);

  it("AC-1697-5c (R-08 / AC-12): bài đã công bố khóa phạm vi, môn và cách dựng bài nhưng vẫn cho sửa bảng tổng quan trên bản đang soạn", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", buildMode: "REVIEW", servingVersion: 1, parts: [hsaPart(1, "Review", 50, 75, "MATH")] });
    await open();
    expect(screen.getByRole("combobox", { name: /^Kỳ thi/ })).toBeDisabled();
    expect(screen.getByRole("group", { name: /Phạm vi bài thi/ })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: /^Môn/ })).toBeDisabled();
    expect(screen.getByRole("group", { name: /Cách dựng bài/ })).toBeDisabled();
    expect(screen.getByText("Bài đã có phiên bản công bố; muốn đổi cách dựng bài hãy tạo bài mới")).toBeInTheDocument();
    const table = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    expect(within(table).getByRole("textbox", { name: /^Số câu của/ })).not.toHaveAttribute("readonly");
    expect(within(table).getByRole("textbox", { name: /^Thời lượng của/ })).not.toHaveAttribute("readonly");
    expect(within(table).getByRole("textbox", { name: "Điểm tối đa" })).not.toHaveAttribute("readonly");
    expect(screen.queryByText(/Bài được tạo ở trạng thái Nháp/)).not.toBeInTheDocument();
  }, 30_000);

  it("máy chủ từ chối đổi cách dựng bài (409 EXAM_SCOPE_LOCKED): câu lỗi khóa phạm vi hiện ra, không treo màn hình", async () => {
    setup({ scope: "SUBJECT", subjectCode: "MATH", parts: [hsaPart(1, "Toán", 50, 75, "MATH")] }, (server) => {
      subjectTemplates(server);
      server.server.on("PATCH", EXAM_PATH, {
        status: 409,
        body: { code: "EXAM_SCOPE_LOCKED", message: "Phạm vi bài thi đã bị khóa vì bài đã có phiên bản công bố." },
      });
    });
    const user = userEvent.setup();
    await open();
    await user.click(screen.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }));
    await user.click(screen.getByRole("button", { name: SAVE }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Tạo lại cấu trúc" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Bài đã công bố nên không đổi được kỳ thi, phạm vi, phần và môn");
    expect(backend.app(EXAM_ID).buildMode).toBe("STANDARD");
  }, 30_000);

  it("R1-8: nhập chữ không phải số vào ô gốc null (durationMinutes: null) chặn gửi và focus ô lỗi", async () => {
    setup({ parts: [hsaPart(1, MATH, 45, null, "MATH"), hsaPart(2, LITERATURE, 50, 60, "LITERATURE")] });
    const user = userEvent.setup();
    await open();
    const form = screen.getByRole("form", { name: FORM_NAME });
    const duration = screen.getByRole("textbox", { name: `Thời lượng của ${MATH}` });
    await user.type(duration, "abc");
    expect(await within(form).findByText("Thời lượng phải là số phút nguyên dương.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: SAVE }));
    expect(routerMock.push).not.toHaveBeenCalled();
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(duration).toHaveFocus();
  }, 30_000);

  it("R1-6/R1-7: PUT structure thành công rồi PATCH lỗi 409/422: sửa và thử lại chỉ gửi PATCH (không gửi lại PUT)", async () => {
    setup();
    backend.seedExam({ ...backend.app("ex-hsa-f-001"), id: "ex-taken", code: "TAKEN-CODE" });
    const user = userEvent.setup();
    await open();

    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "40");
    const code = screen.getByRole("textbox", { name: /Mã bài thi/ });
    await user.clear(code);
    await user.type(code, "TAKEN-CODE");
    const form = screen.getByRole("form", { name: FORM_NAME });
    await user.click(screen.getByRole("button", { name: SAVE }));

    expect(await within(form).findByText("Mã bài thi đã được dùng. Nhập mã khác.")).toBeInTheDocument();
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1);
    expect(routerMock.push).not.toHaveBeenCalled();

    await user.clear(code);
    await user.type(code, "UNIQUE-CODE");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(2));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(1);
    expect(backend.calls("PATCH", EXAM_PATH)[1]!.body).toMatchObject({
      code: "UNIQUE-CODE",
      expectedRevision: seeded.revision + 1,
    });
    expect(backend.app(EXAM_ID).parts[0]!.questionCount).toBe(40);
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("R1-9..11/R1-23: bài đã công bố gặp 409 EXAM_CODE_TAKEN vẫn báo lỗi và focus ô Mã; 409 khác không gán nhầm vào ô Mã", async () => {
    setup({ servingVersion: 1 }, (server) => {
      server.server.on(
        "PATCH",
        EXAM_PATH,
        { status: 409, body: { code: "EXAM_CODE_TAKEN", message: "taken" } },
        { status: 409, body: { code: "EXAM_STRUCTURE_REPLACE_CONFIRM", message: "confirm" } },
      );
    });
    const user = userEvent.setup();
    await open();
    const form = screen.getByRole("form", { name: FORM_NAME });
    const code = screen.getByRole("textbox", { name: /Mã bài thi/ });
    await user.clear(code);
    await user.type(code, "DUP-PUBLISHED");
    await user.click(screen.getByRole("button", { name: SAVE }));

    expect(await within(form).findByText("Mã bài thi đã được dùng. Nhập mã khác.")).toBeInTheDocument();
    expect(code).toBeInvalid();
    expect(code).toHaveFocus();

    await user.clear(code);
    await user.type(code, "OTHER-CONFLICT");
    await user.click(screen.getByRole("button", { name: SAVE }));

    expect(await screen.findByText(/Không lưu được thông tin chung/)).toBeInTheDocument();
    expect(code).not.toBeInvalid();
    expect(within(form).queryByText("Mã bài thi đã được dùng. Nhập mã khác.")).not.toBeInTheDocument();
  }, 30_000);

  it("R1-12: mẫu của bài không có trong danh sách templates và năm cấu trúc khác năm ma trận: giữ yearsConfirmed=false", async () => {
    setup({ templateCode: "RETIRED_TPL", structureYear: 2026, matrixYear: 2025, yearsConfirmed: false });
    const user = userEvent.setup();
    await open();
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Đổi tên khi mất mẫu");
    await user.click(screen.getByRole("button", { name: SAVE }));

    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ yearsConfirmed: false });
  }, 30_000);

  it("R1-20: chỉ sửa thông tin chỉ gửi PATCH; sửa cả bảng và thông tin gửi PUT trước rồi PATCH với revision mới", async () => {
    setup();
    const user = userEvent.setup();
    const first = await open();
    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Chỉ sửa tên");
    await user.click(screen.getByRole("button", { name: SAVE }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ name: "Chỉ sửa tên", expectedRevision: seeded.revision });
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
    first.unmount();

    routerMock.push.mockReset();
    setup();
    await open();
    await user.clear(countOf(MATH));
    await user.type(countOf(MATH), "42");
    const name2 = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name2);
    await user.type(name2, "Sửa cả hai");
    await user.click(screen.getByRole("button", { name: SAVE }));
    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    const writes = backend.server.requests
      .filter((request) => request.method !== "GET")
      .map((request) => `${request.method} ${request.url.pathname}`);
    expect(writes).toEqual([`PUT ${STRUCTURE_PATH}`, `PATCH ${EXAM_PATH}`]);
    expect(backend.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({
      name: "Sửa cả hai",
      expectedRevision: seeded.revision + 1,
    });
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`));
  }, 30_000);

  it("R1-21: GET /exam-templates về sau khi biểu mẫu dựng giữ nguyên phạm vi đã lưu và áp khóa mẫu", async () => {
    const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
    setup({}, (server) => {
      server.server.on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [{ ...toWireTemplate(hsaFull), fixedCounts: true }] });
      server.server.hold("GET", "/api/v1/admin/exam-templates");
    });
    const user = userEvent.setup();
    renderEditLayout();
    await screen.findByRole("form", { name: FORM_NAME });
    expect(screen.getByRole("radio", { name: /^Toàn bài/ })).toBeChecked();
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("HSA quay lại bước 1");
    expect(screen.getByRole("button", { name: SAVE })).toBeDisabled();

    backend.server.release("GET", "/api/v1/admin/exam-templates");
    expect(await screen.findByText("Mẫu đã khóa số câu")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /^Toàn bài/ })).toBeChecked();
    const table = screen.getByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
    const inputs = within(table).getAllByRole("textbox", { name: /^Số câu của/ });
    expect(inputs.map((input) => (input as HTMLInputElement).value)).toEqual(["45", "50", "50"]);
    for (const input of inputs) expect(input).toHaveAttribute("readonly");

    await user.click(screen.getByRole("button", { name: SAVE }));
    expect(backend.calls("PUT", STRUCTURE_PATH)).toHaveLength(0);
    expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(0);
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`);
  }, 30_000);

  it("R1-15: chế độ sửa với cấu trúc rỗng (parts: []) hiện câu thông báo rỗng thay vì bảng trống", async () => {
    setup({ parts: [] });
    renderEditLayout();
    await screen.findByRole("form", { name: FORM_NAME });
    expect(await screen.findByText("Bài chưa có phần nào. Thêm phần ở bước Cấu trúc.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  }, 30_000);

  it("R1-27/R1-28: tham số ?exam= rỗng không redirect mà mở màn tạo mới", async () => {
    setup();
    window.history.replaceState(null, "", "/exams/new?exam=%20%20");
    const element = await NewExamPage({ searchParams: Promise.resolve({ exam: "  " }) });
    expect(routerMock.redirect).not.toHaveBeenCalled();
    renderExamsUi(element);
    await screen.findByRole("form", { name: "Tạo bài thi" });
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("href", "/exams");
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("");
    expect(backend.calls("GET", "/api/v1/admin/exams/")).toHaveLength(0);
  }, 30_000);

  it("R1-13/R1-14: lỗi 404 khi tải bài hiện trạng thái không tìm thấy, ẩn các tab và trỏ về /exams", async () => {
    setup({}, (server) => {
      server.server.on("GET", EXAM_PATH, { status: 404, body: { code: "EXAM_NOT_FOUND" } });
    });
    renderEditLayout();
    expect(await screen.findByRole("heading", { name: "Không tìm thấy bài thi" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Các phần của bài thi" })).not.toBeInTheDocument();
    for (const link of screen.getAllByRole("link", { name: "Danh sách bài thi" })) {
      expect(link).toHaveAttribute("href", "/exams");
    }
  }, 30_000);

  it("R1-13/R1-14: lỗi 403 khi tải bài hiện trạng thái thiếu quyền, ẩn các tab và trỏ về /exams", async () => {
    setup({}, (server) => {
      server.server.on("GET", EXAM_PATH, { status: 403, body: { code: "FORBIDDEN" } });
    });
    renderEditLayout();
    expect(await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Các phần của bài thi" })).not.toBeInTheDocument();
    for (const link of screen.getAllByRole("link", { name: "Danh sách bài thi" })) {
      expect(link).toHaveAttribute("href", "/exams");
    }
  }, 30_000);

  it("R1-13/R1-14: lỗi 500 khi tải bài ẩn các tab, trỏ về /exams và nút Thử lại báo đang tải rồi khôi phục biểu mẫu", async () => {
    const recovered = examDetailWire({ id: EXAM_ID, code: "HSA-EDIT-001", name: "HSA sau retry" });
    setup({}, (server) => {
      server.server.on("GET", EXAM_PATH, { status: 500, body: { code: "INTERNAL" } }, { status: 200, body: recovered });
    });
    const user = userEvent.setup();
    renderEditLayout();
    expect(await screen.findByRole("heading", { name: "Không tải được bài thi" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Các phần của bài thi" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("href", "/exams");

    backend.server.hold("GET", EXAM_PATH);
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Thử lại" })).toHaveAttribute("aria-busy", "true"));
    backend.server.release("GET", EXAM_PATH);

    await screen.findByRole("form", { name: FORM_NAME });
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("HSA sau retry");
  }, 30_000);

  it("R2-1/R2-2: sau khi Thử lại tải bài thành công, lưu không làm hiện lại lỗi tải bài", async () => {
    setup({}, (server) => {
      server.server.on(
        "GET",
        EXAM_PATH,
        { status: 500, body: { code: "INTERNAL" } },
        () => ({ status: 200, body: toWireExam(server.app(EXAM_ID)) }),
      );
    });
    const user = userEvent.setup();
    renderEditLayout();
    expect(await screen.findByRole("heading", { name: "Không tải được bài thi" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("form", { name: FORM_NAME });

    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "HSA sau retry vẫn lưu được");

    backend.server.hold("GET", EXAM_PATH);
    try {
      await user.click(screen.getByRole("button", { name: SAVE }));
      await waitFor(() => expect(backend.calls("GET", EXAM_PATH)).toHaveLength(3));
      expect(screen.queryByRole("heading", { name: "Không tải được bài thi" })).not.toBeInTheDocument();
      expect(screen.getByRole("form", { name: FORM_NAME })).toBeInTheDocument();
    } finally {
      backend.server.release("GET", EXAM_PATH);
    }

    await waitFor(() => expect(backend.calls("PATCH", EXAM_PATH)).toHaveLength(1));
    expect(screen.queryByRole("heading", { name: "Không tải được bài thi" })).not.toBeInTheDocument();
    expect(backend.app(EXAM_ID).name).toBe("HSA sau retry vẫn lưu được");
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/structure`);
  }, 30_000);

  it("R2-3: tải lại nền gặp lỗi 500 khi đã có dữ liệu giữ nguyên biểu mẫu và nội dung đang sửa dở", async () => {
    setup({}, (server) => {
      server.server.on(
        "GET",
        EXAM_PATH,
        () => ({ status: 200, body: toWireExam(server.app(EXAM_ID)) }),
        { status: 500, body: { code: "INTERNAL" } },
      );
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const user = userEvent.setup();
    await open(client);

    const name = screen.getByRole("textbox", { name: /Tên bài thi/ });
    await user.clear(name);
    await user.type(name, "Tên đang sửa dở");

    await client.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.detail(EXAM_ID) });
    await waitFor(() => expect(backend.calls("GET", EXAM_PATH)).toHaveLength(2));
    await waitFor(() => expect(client.getQueryState(EXAMS_QUERY_KEYS.detail(EXAM_ID))?.status).toBe("error"));

    expect(screen.queryByRole("heading", { name: "Không tải được bài thi" })).not.toBeInTheDocument();
    expect(screen.getByRole("form", { name: FORM_NAME })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Tên bài thi/ })).toHaveValue("Tên đang sửa dở");
  }, 30_000);
});
