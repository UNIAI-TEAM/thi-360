import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;

afterEach(() => vi.restoreAllMocks());

const referenceMatrix = {
  mode: "SEPARATE",
  unit: "COUNT",
  byGrade: [{ code: "GRADE_12", value: 30 }],
  byDifficulty: [{ code: "RECOGNITION", value: 30 }],
  gradeEnforcement: "REFERENCE",
  difficultyEnforcement: "REFERENCE",
};

/** Ma trận môn Tiếng Anh của mẫu HSA_FULL: hai chiều Tham khảo, đơn vị câu, chưa có số (chờ chuyên gia). */
const emptyEnglishMatrix = {
  mode: "SEPARATE",
  unit: "COUNT",
  basis: "QUESTION",
  byGrade: [],
  byDifficulty: [],
  gradeEnforcement: "REFERENCE",
  difficultyEnforcement: "REFERENCE",
};

/** Bài một phần, một môn 30 câu có ma trận môn ở mức Tham khảo; `kind` chọn kỳ thi, `matrix` thay ma trận môn. */
function boot(kind: string, matrix: object = referenceMatrix, subjectCode?: string, examQuotas: unknown[] = []) {
  const base = FIXED_PART.sections![0] as object;
  const section = {
    ...base,
    ...(subjectCode ? { subjectCode } : {}),
    questionCount: 30,
    matrix,
    blocks: [
      {
        id: "blk-1",
        name: "Choice",
        questionType: "SINGLE_CHOICE",
        questionCount: 30,
        requiredPointsPerQuestion: 1,
        ...(subjectCode === "ANH" ? { childConditions: [{ from: 1, to: 1, nodeIds: ["n1"] }] } : {}),
      },
    ],
  };
  const part = { ...FIXED_PART, questionCount: 30, sections: [section] };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind, questionCount: 30 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part], examQuotas } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
}

function open(view = "section=sec-1") {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?${view}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

describe("Mở bài đã lưu mức Tham khảo (UNI-1701 AC-4)", () => {
  it("HSA: nháp đã chuyển sang Bắt buộc, có ghi chú và 'chưa lưu'; lưu gửi Bắt buộc", async () => {
    const server = boot("HSA");
    const user = userEvent.setup();
    open();
    expect(await screen.findByText(/Đã chuyển mức Tham khảo sang Bắt buộc theo quy định HSA/)).toBeInTheDocument();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ matrix: Record<string, unknown> }> }> };
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" });
  });

  it("kỳ thi khác HSA: giữ Tham khảo đã lưu, không ghi chú, không chưa lưu, còn hai nút", async () => {
    boot("VACT");
    open();
    expect((await screen.findAllByRole("radio", { name: "Tham khảo" })).length).toBeGreaterThan(0);
    expect(screen.queryByText(/theo quy định HSA/)).not.toBeInTheDocument();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
  });
});

describe("Bài HSA theo mẫu thật (UNI-1701 R1-2, R1-3)", () => {
  it("R1-2: ma trận Tiếng Anh của mẫu (chiều trống, Tham khảo) mở ra không cảnh báo thiếu câu, không 'chưa lưu', không ghi chú", async () => {
    boot("HSA", emptyEnglishMatrix, "ANH");
    open();
    expect((await screen.findAllByText("Tham khảo")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
    expect(screen.queryByText(/theo quy định HSA/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Thiếu \d+ câu/)).not.toBeInTheDocument();
    expect(screen.queryByText(/khối cần/)).not.toBeInTheDocument();
  });

  it("R1-3: ma trận đơn vị điểm chuyển sang câu thì lưu gửi Bắt buộc, chip và bản lưu không lệch nhau", async () => {
    const server = boot("HSA", { ...referenceMatrix, basis: "POINTS" });
    const user = userEvent.setup();
    open();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
    await user.click(await screen.findByRole("radio", { name: "Câu" }));
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Tham khảo" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ matrix: Record<string, unknown> }> }> };
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ basis: "QUESTION", gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" });
    expect(screen.queryByText(/theo quy định HSA/)).not.toBeInTheDocument();
  });
});

/** Chỉ tiêu của mẫu HSA_FULL, HSA_PART_ENGLISH, HSA_SUBJECT_ANH (migration V202611180652). */
const englishGradeQuota = {
  id: "hsa-english-grade",
  name: "Lớp Tiếng Anh tham khảo",
  dimension: "GRADE",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["ANH"],
  buckets: [
    { codes: ["GRADE_10", "GRADE_11"], percent: 30 },
    { codes: ["GRADE_12"], percent: 70 },
  ],
};

describe("Chỉ tiêu Tiếng Anh chờ chuyên gia của mẫu HSA (UNI-1701 R1-1, AD-29)", () => {
  const reason = /Chờ chuyên gia xác nhận: phân bố khối lớp Tiếng Anh/;

  it("mở bài: ở lại Tham khảo kèm lý do, không nút radio, không ghi chú, không 'chưa lưu'", async () => {
    boot("HSA", emptyEnglishMatrix, "ANH", [englishGradeQuota]);
    open("exam=1");
    const section = await screen.findByRole("region", { name: "Lớp Tiếng Anh tham khảo" });
    expect(within(section).getByText(reason)).toBeInTheDocument();
    expect(within(section).queryByRole("radio")).not.toBeInTheDocument();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
    expect(screen.queryByText(/theo quy định HSA/)).not.toBeInTheDocument();
  });

  it("sửa số rồi hủy thay đổi, hoặc lưu: chỉ tiêu vẫn Tham khảo, bản lưu gửi Tham khảo", async () => {
    const server = boot("HSA", emptyEnglishMatrix, "ANH", [englishGradeQuota]);
    const user = userEvent.setup();
    open("exam=1");
    const input = (await screen.findAllByRole("textbox", { name: /^Tỷ lệ/ }))[0]!;
    await user.clear(input);
    await user.type(input, "40");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument());
    expect(screen.queryByText(/theo quy định HSA/)).not.toBeInTheDocument();
    expect(screen.getByText(reason)).toBeInTheDocument();

    const [grade10, grade12] = await screen.findAllByRole("textbox", { name: /^Tỷ lệ/ });
    await user.clear(grade10!);
    await user.type(grade10!, "40");
    await user.clear(grade12!);
    await user.type(grade12!, "60");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { examQuotas: Array<{ id: string; enforcement: string }> };
    expect(sent.examQuotas).toMatchObject([{ id: "hsa-english-grade", enforcement: "REFERENCE" }]);
  });

  it("R2-1: Áp dụng phân bổ này đặt Bắt buộc rồi lưu: bản lưu gửi Bắt buộc", async () => {
    const server = boot("HSA", emptyEnglishMatrix, "ANH", [englishGradeQuota]);
    const user = userEvent.setup();
    open();
    const apply = (await screen.findAllByRole("button", { name: /Áp dụng phân bổ này/ })).find((button) => !(button as HTMLButtonElement).disabled)!;
    await user.click(apply);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { examQuotas: Array<{ id: string; enforcement: string }> };
    expect(sent.examQuotas).toMatchObject([{ id: "hsa-english-grade", enforcement: "REQUIRED" }]);
  });

  it("R2-1: chỉ tiêu Tiếng Anh đã Bắt buộc (sau khi áp dụng): không còn chip mức, không còn lý do chờ chuyên gia", async () => {
    boot("HSA", emptyEnglishMatrix, "ANH", [{ ...englishGradeQuota, enforcement: "REQUIRED" }]);
    open("exam=1");
    await screen.findByRole("region", { name: /Lớp Tiếng Anh tham khảo/ });
    expect(screen.queryByRole("group", { name: /Mức của Lớp Tiếng Anh tham khảo/ })).not.toBeInTheDocument();
    expect(screen.queryByText(reason)).not.toBeInTheDocument();
    expect(screen.queryByText("Có thay đổi chưa lưu")).not.toBeInTheDocument();
  });

  it("R2-3 (AD-29, phương án B): ma trận Tiếng Anh có số ở chiều khối lớp thành Bắt buộc, chỉ chỉ tiêu cấp bài ở lại Tham khảo", async () => {
    const englishMatrix = { ...emptyEnglishMatrix, byGrade: [{ code: "GRADE_12", value: 30 }] };
    const server = boot("HSA", englishMatrix, "ANH", [englishGradeQuota]);
    const user = userEvent.setup();
    const view = open();
    await screen.findByText("Có thay đổi chưa lưu");
    expect(screen.queryByRole("group", { name: /Mức của Theo khối lớp/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ matrix: Record<string, unknown> }> }>; examQuotas: Array<{ enforcement: string }> };
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ gradeEnforcement: "REQUIRED", difficultyEnforcement: "REFERENCE" });
    expect(sent.examQuotas.map((quota) => quota.enforcement)).toEqual(["REFERENCE"]);
    view.unmount();

    open("exam=1");
    const quota = await screen.findByRole("group", { name: /Lớp Tiếng Anh tham khảo/ });
    expect(quota).toHaveAttribute("data-fixed", "REFERENCE");
    expect(within(quota).getByText(reason)).toBeInTheDocument();
  });
});
