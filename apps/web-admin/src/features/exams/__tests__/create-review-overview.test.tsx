import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExamGeneralForm } from "../components/exam-general-form";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { ExamsBackend, toWireTemplate } from "./fake-exams-backend";
import { renderExamsUi } from "./render-exams-ui";

/**
 * UNI-2752 (BA 1698): Theo môn + Tự thiết lập là một dòng: Số câu, Thời lượng (phút) và Điểm tối đa là ba cột của cùng bảng,
 * không có hàng "Tổng của bài" và không có ô "Điểm tối đa … điểm" tách riêng bên dưới. Tên dòng là tên môn đã chọn.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

const EXAMS_PATH = "/api/v1/admin/exams";
const TEMPLATES_PATH = "/api/v1/admin/exam-templates";

let backend: ExamsBackend;

/** Mẫu bài ôn như máy chủ trả: một phần tên "Review", nhãn mẫu "HSA REVIEW", không có số mặc định. */
function reviewTemplate() {
  const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
  const template = {
    ...structuredClone(hsaFull),
    code: "HSA_REVIEW",
    scope: "SUBJECT" as const,
    subjectCode: null,
    timing: "PER_PART" as const,
    maxScore: null,
    parts: [{ ...structuredClone(hsaFull.parts[0]!), name: "Review", questionCount: 0, durationMinutes: null, sections: [] }],
  };
  return { ...toWireTemplate(template), buildMode: "REVIEW" as const, label: "HSA REVIEW" };
}

/** Mẫu Theo môn của HSA cho một môn (để môn đã chọn được giữ khi bấm Tự thiết lập, như dữ liệu thật). */
function subjectTemplate(subjectCode: string) {
  const hsaFull = FIXTURE_TEMPLATES.find((item) => item.code === "HSA_FULL")!;
  return toWireTemplate({ ...structuredClone(hsaFull), code: `HSA_SUBJECT_${subjectCode}`, scope: "SUBJECT", subjectCode, parts: [structuredClone(hsaFull.parts[0]!)] });
}

beforeEach(() => {
  window.history.replaceState(null, "", "/exams/new");
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.server.on("GET", TEMPLATES_PATH, {
    status: 200,
    body: [...FIXTURE_TEMPLATES.map(toWireTemplate), subjectTemplate("MATH"), subjectTemplate("PHYSICS"), reviewTemplate()],
  });
  backend.install();
});

async function openReview(user: UserEvent) {
  renderExamsUi(<ExamGeneralForm mode="create" />);
  await screen.findByRole("form", { name: "Tạo bài thi" });
  await user.click(screen.getByRole("radio", { name: /^Theo môn/ }));
  await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
  await user.click(await screen.findByRole("option", { name: "Toán" }));
  await user.click(await screen.findByRole("radio", { name: /Tự thiết lập bài ôn/ }));
  return screen.findByRole("region", { name: "Bảng cấu trúc tổng quan của bài thi" });
}

describe("bước 1, Theo môn + Tự thiết lập: một bảng ba cột (UNI-2752)", () => {
  it("Điểm tối đa là cột cạnh Thời lượng; không còn hàng Tổng của bài và ô Điểm tối đa … điểm tách riêng", async () => {
    const user = userEvent.setup();
    const table = await openReview(user);
    expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
      "Phần hoặc môn",
      "Số câu",
      "Thời lượng (phút)",
      "Điểm tối đa",
    ]);
    expect(within(table).queryByText("Tổng của bài")).not.toBeInTheDocument();
    // Ô điểm nằm trong dòng của bảng, không phải hàng riêng bên dưới (không còn đơn vị "điểm" đứng sau ô).
    const row = within(table).getAllByRole("row")[1]!;
    expect(within(row).getByRole("textbox", { name: "Điểm tối đa" })).toBeInTheDocument();
    expect(within(table).getAllByRole("row")).toHaveLength(2);
    expect(screen.queryByText("điểm", { selector: "span" })).not.toBeInTheDocument();
  }, 30_000);

  it("tên dòng là tên môn đang chọn, không phải tên phần của mẫu (Review), và đổi theo môn", async () => {
    const user = userEvent.setup();
    const table = await openReview(user);
    expect(within(table).getByRole("textbox", { name: "Số câu của Toán" })).toBeInTheDocument();
    expect(within(table).queryByText("Review")).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: /^Môn/ }));
    await user.click(await screen.findByRole("option", { name: "Vật lí" }));
    expect(await within(table).findByRole("textbox", { name: "Số câu của Vật lí" })).toBeInTheDocument();
  }, 30_000);

  it("dòng gợi ý gọi mẫu là 'mẫu Bài ôn HSA', không lộ nhãn kỹ thuật HSA REVIEW", async () => {
    const user = userEvent.setup();
    await openReview(user);
    // Dòng gợi ý dưới tiêu đề khối 3 và mục "Mẫu" của Tóm tắt dùng cùng tên.
    expect(screen.getAllByText(/mẫu Bài ôn HSA/)).toHaveLength(2);
    expect(screen.queryByText(/HSA REVIEW/)).not.toBeInTheDocument();
  }, 30_000);

  it("cùng trường maxScore: giá trị gõ ở ô trong bảng được gửi, và ô sai báo lỗi ngay tại ô", async () => {
    const user = userEvent.setup();
    const table = await openReview(user);
    await user.type(within(table).getByRole("textbox", { name: /^Số câu của/ }), "10");
    await user.type(within(table).getByRole("textbox", { name: /^Thời lượng của/ }), "20");
    await user.type(screen.getByRole("textbox", { name: /Mã bài thi/ }), "HSA-REV-1");
    await user.type(screen.getByRole("textbox", { name: /Tên bài thi/ }), "HSA ôn Toán");

    const score = within(table).getByRole("textbox", { name: "Điểm tối đa" });
    await user.type(score, "0");
    await user.click(screen.getAllByRole("button", { name: "Tiếp tục → Cấu trúc và ma trận" })[0]!);
    expect(await within(table).findByText("Thang điểm phải lớn hơn 0.")).toBeInTheDocument();
    expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(0);

    await user.clear(score);
    await user.type(score, "100");
    await user.click(screen.getAllByRole("button", { name: "Tiếp tục → Cấu trúc và ma trận" })[0]!);
    await waitFor(() => expect(backend.calls("POST", EXAMS_PATH)).toHaveLength(1));
    expect(backend.calls("POST", EXAMS_PATH)[0]!.body).toMatchObject({ scope: "SUBJECT", buildMode: "REVIEW", maxScore: 100 });
  }, 30_000);
});
