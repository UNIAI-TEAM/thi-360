import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { FIXTURE_TEMPLATES } from "./exam-fixture";
import { toWireTemplate } from "./fake-exams-backend";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;

const gradeQuota = (percents: [number, number] = [30, 70]) => ({
  id: "q-grade",
  name: "Lớp tham khảo",
  dimension: "GRADE",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["MATH"],
  excludeSubjectCodes: ["LITERATURE"],
  buckets: [
    { codes: ["GRADE_10", "GRADE_11"], percent: percents[0] },
    { codes: ["GRADE_12"], percent: percents[1] },
  ],
});

interface Boot {
  /** Chỉ tiêu cấp bài của lần đọc cấu trúc đầu; `later` là của các lần đọc sau (sau khi nạp lại phiên bản). */
  examQuotas: unknown[];
  later?: unknown[];
  templates?: unknown[];
  /** Kỳ thi của bài: mặc định khác HSA, vì bài HSA cố định mức Bắt buộc (UNI-1701). */
  kind?: string;
}

function boot({ examQuotas, later, templates = [], kind = "VACT" }: Boot) {
  const structure = (quotas: unknown[], revision: number) => ({ status: 200, body: { examId: EXAM_ID, revision, parts: [FIXED_PART], examQuotas: quotas } });
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind, questionCount: 10, templateCode: "T_NOW" }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: templates })
    .on("GET", STRUCTURE, structure(examQuotas, 7), ...(later ? [structure(later, 8)] : []))
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .on("POST", `${STRUCTURE}/template`, { status: 200, body: examDetailWire({ revision: 8, templateCode: "T_OTHER" }) })
    .install();
}

function open() {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?exam=1`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

async function setPercent(user: ReturnType<typeof userEvent.setup>, index: number, value: string) {
  const input = (await screen.findAllByRole("textbox", { name: /^Tỷ lệ/ }))[index]!;
  await user.clear(input);
  await user.type(input, value);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Chỉ tiêu cấp bài: lưu và chặn (R1-09, R1-16)", () => {
  it("R1-09: tổng tỷ lệ 90% báo lỗi ở khối chỉ tiêu và không gửi lên máy chủ; về 100% thì lưu được", async () => {
    const server = boot({ examQuotas: [gradeQuota()] });
    const user = userEvent.setup();
    open();
    await setPercent(user, 1, "60");
    expect(await screen.findByRole("alert", { name: "" })).toHaveTextContent("Tổng tỷ lệ là 90%");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    expect(screen.getByText(/Tổng tỷ lệ là 90%/)).toBeInTheDocument();

    await setPercent(user, 0, "40");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
  });

  it("R1-16: bản lưu mang đủ chỉ tiêu cấp bài đã sửa: các mức, mức Bắt buộc, phạm vi môn và đơn vị câu", async () => {
    const server = boot({ examQuotas: [gradeQuota()] });
    const user = userEvent.setup();
    open();
    await setPercent(user, 0, "40");
    await setPercent(user, 1, "60");
    const section = screen.getByRole("region", { name: "Lớp tham khảo" });
    await user.click(within(section).getByRole("radio", { name: "Bắt buộc" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { examQuotas: unknown[] };
    expect(sent.examQuotas).toEqual([
      {
        ...gradeQuota(),
        enforcement: "REQUIRED",
        buckets: [
          { codes: ["GRADE_10", "GRADE_11"], percent: 40 },
          { codes: ["GRADE_12"], percent: 60 },
        ],
      },
    ]);
  });
});

describe("Chỉ tiêu cấp bài khi đổi phiên bản cấu trúc (R1-10)", () => {
  it("sửa chỉ tiêu rồi nạp phiên bản khác: màn hiện chỉ tiêu của phiên bản mới, không giữ số đã sửa", async () => {
    const base = FIXTURE_TEMPLATES.find((template) => template.kind === "HSA" && template.scope === "FULL")!;
    const now = { ...base, code: "T_NOW", label: "HSA hiện tại", structureYear: 2026 };
    const other = { ...base, code: "T_OTHER", label: "HSA bản khác", structureYear: 2025, isDefault: false };
    // Bài HSA luôn Bắt buộc (UNI-1701): chỉ tiêu Tham khảo cũ sẽ được chuyển sang Bắt buộc và làm bản nháp "chưa lưu", chặn nạp lại.
    const required = (percents?: [number, number]) => ({ ...gradeQuota(percents), enforcement: "REQUIRED" });
    const server = boot({ examQuotas: [required()], later: [required([20, 80])], kind: "HSA", templates: [now, other].map(toWireTemplate) });
    const user = userEvent.setup();
    open();
    await setPercent(user, 0, "40");
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click((await screen.findAllByRole("option"))[1]!);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(server.calls("POST", `${STRUCTURE}/template`)).toHaveLength(1));
    await waitFor(() => {
      const inputs = screen.getAllByRole("textbox", { name: /^Tỷ lệ/ }) as HTMLInputElement[];
      expect(inputs.map((input) => input.value)).toEqual(["20", "80"]);
    });
  }, 20_000);
});

describe("Chỉ tiêu cấp bài tính theo đơn vị không phải câu khóa mức Bắt buộc (R2-04)", () => {
  it("chỉ tiêu tính theo điểm: nút Bắt buộc bị khóa kèm lý do, chỉ còn Tham khảo", async () => {
    boot({ examQuotas: [{ ...gradeQuota(), basis: "POINTS" }] });
    open();
    const section = await screen.findByRole("region", { name: "Lớp tham khảo" });
    expect(within(section).getByRole("radio", { name: "Bắt buộc" })).toBeDisabled();
    expect(within(section).getByRole("radio", { name: "Tham khảo" })).toBeChecked();
    expect(within(section).getByText(/Phân bố bắt buộc phải tính theo câu/)).toBeInTheDocument();
  });

  it("đối chứng: chỉ tiêu tính theo câu chọn được Bắt buộc", async () => {
    boot({ examQuotas: [gradeQuota()] });
    open();
    const section = await screen.findByRole("region", { name: "Lớp tham khảo" });
    expect(within(section).getByRole("radio", { name: "Bắt buộc" })).toBeEnabled();
  });
});

describe("Chỉ tiêu cấp bài của kỳ thi HSA có mức cố định (UNI-1701)", () => {
  it("một môn, tính theo câu: Bắt buộc cố định, không còn nút radio, và bản nháp lưu Bắt buộc", async () => {
    const server = boot({ examQuotas: [gradeQuota()], kind: "HSA" });
    const user = userEvent.setup();
    open();
    const section = await screen.findByRole("region", { name: "Lớp tham khảo" });
    expect(within(section).queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    expect(within(section).getByText("Kỳ thi HSA: sinh đề luôn theo ma trận")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { examQuotas: Array<{ enforcement: string }> };
    expect(sent.examQuotas.map((quota) => quota.enforcement)).toEqual(["REQUIRED"]);
  });

  it("H6: trải nhiều môn vẫn Tham khảo cố định kèm lý do", async () => {
    boot({ examQuotas: [{ ...gradeQuota(), subjectCodes: ["MATH", "LITERATURE"], excludeSubjectCodes: [] }], kind: "HSA" });
    open();
    const section = await screen.findByRole("region", { name: "Lớp tham khảo" });
    expect(within(section).queryByRole("radio")).not.toBeInTheDocument();
    expect(within(section).getByText("Chỉ tiêu trải nhiều môn chỉ ở mức Tham khảo.")).toBeInTheDocument();
  });
});
