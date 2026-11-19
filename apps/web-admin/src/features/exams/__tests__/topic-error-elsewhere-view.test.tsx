import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamOverviewPanel } from "../components/exam-overview-panel";
import { ExamStructureView } from "../components/exam-structure-view";
import type { ExamPart } from "../types";
import { EXAMS_FIXTURE } from "./exam-fixture";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;

const gradeQuota = {
  id: "q-grade",
  name: "Lớp tham khảo",
  dimension: "GRADE",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["MATH"],
  buckets: [
    { codes: ["GRADE_10", "GRADE_11"], percent: 30 },
    { codes: ["GRADE_12"], percent: 70 },
  ],
};

function boot(examQuotas: unknown[], parts: unknown[] = [FIXED_PART]) {
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts, examQuotas } })
    .on("PUT", STRUCTURE, {
      status: 422,
      body: { code: "EXAM_STRUCTURE_INVALID", message: "x", fieldErrors: [{ field: "topicQuotas", code: "MAX_TOPIC_QUOTAS" }] },
    })
    .install();
}

function open(query: string) {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?${query}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Lỗi 422 chỉ tiêu chủ đề ở panel không có bảng chỉ tiêu (R1-21)", () => {
  it("đang mở tổng quan bài: cảnh báo hiện ở khối chỉ tiêu cấp bài; sửa nháp thì mất", async () => {
    const server = boot([gradeQuota]);
    const user = userEvent.setup();
    open("exam=1");
    const percent = (await screen.findAllByRole("textbox", { name: /^Tỷ lệ/ }))[0]!;
    await user.clear(percent);
    await user.type(percent, "40");
    await user.clear(screen.getAllByRole("textbox", { name: /^Tỷ lệ/ })[1]!);
    await user.type(screen.getAllByRole("textbox", { name: /^Tỷ lệ/ })[1]!, "60");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));

    const block = screen.getByRole("heading", { name: "Chỉ tiêu cấp bài" }).closest("section")!;
    const alert = await within(block).findByRole("alert");
    expect(alert).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
    expect(alert).toHaveTextContent(/Mở môn hoặc nhóm có chỉ tiêu chủ đề/);

    await user.clear(screen.getAllByRole("textbox", { name: /^Tỷ lệ/ })[0]!);
    await user.type(screen.getAllByRole("textbox", { name: /^Tỷ lệ/ })[0]!, "30");
    await waitFor(() => expect(screen.queryByText(/tối đa 8 chỉ tiêu chủ đề/)).not.toBeInTheDocument());
  });

  it("đang mở panel của phần: cảnh báo hiện trong panel của phần; sửa nháp thì mất", async () => {
    const server = boot([]);
    const user = userEvent.setup();
    open("part=part-1");
    const name = await screen.findByRole("textbox", { name: /Tên phần/ });
    await user.type(name, "x");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));

    const panel = screen.getByRole("heading", { level: 2, name: /Part 1x/ }).closest("div.gap-5")! as HTMLElement;
    const alert = await within(panel).findByRole("alert");
    expect(alert).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
    expect(alert).toHaveTextContent(/Mở môn hoặc nhóm có chỉ tiêu chủ đề/);

    await user.type(screen.getByRole("textbox", { name: /Tên phần/ }), "y");
    await waitFor(() => expect(screen.queryByText(/tối đa 8 chỉ tiêu chủ đề/)).not.toBeInTheDocument());
  });
});

describe("Lỗi 422 chỉ tiêu chủ đề khi nhóm lấy phần còn lại đang mở (R3-01)", () => {
  // Một phần, một môn 10 câu: nhóm 4 câu cố định và nhóm "lấy phần còn lại" không có bảng chỉ tiêu chủ đề.
  const REMAINDER_PART = {
    ...FIXED_PART,
    sections: [
      {
        ...FIXED_PART.sections![0]!,
        blocks: [
          { id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 4, requiredPointsPerQuestion: 1 },
          { id: "blk-2", name: "Còn lại", questionType: "SINGLE_CHOICE", questionCount: null, requiredPointsPerQuestion: 1 },
        ],
      },
    ],
  } as unknown as typeof FIXED_PART;

  it("đang mở nhóm lấy phần còn lại: cảnh báo hiện trong thẻ nhóm; sửa nháp thì mất", async () => {
    const server = boot([], [REMAINDER_PART]);
    const user = userEvent.setup();
    open("block=blk-2");
    await user.type(await screen.findByRole("textbox", { name: "Tên nhóm" }), " x");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
    expect(alert).toHaveTextContent(/Mở môn hoặc nhóm có chỉ tiêu chủ đề/);

    await user.type(screen.getByRole("textbox", { name: "Tên nhóm" }), "y");
    await waitFor(() => expect(screen.queryByText(/tối đa 8 chỉ tiêu chủ đề/)).not.toBeInTheDocument());
  });

  it("đang mở nhóm có bảng chỉ tiêu: lỗi nằm trong bảng, không có cảnh báo chung", async () => {
    const server = boot([], [REMAINDER_PART]);
    const user = userEvent.setup();
    open("block=blk-1");
    await user.type(await screen.findByRole("textbox", { name: "Tên nhóm" }), " x");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
    // Lỗi của bảng (bỏ bớt một chỉ tiêu), không phải câu "Mở môn hoặc nhóm có chỉ tiêu chủ đề" của cảnh báo chung.
    expect(alert).toHaveTextContent(/Bỏ bớt một chỉ tiêu/);
    expect(alert).not.toHaveTextContent(/Mở môn hoặc nhóm có chỉ tiêu chủ đề/);
  });
});

describe("Chú thích dưới bảng điểm khi phần có lựa chọn (R1-05)", () => {
  const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
  const names = { subject: (code: string) => code, combination: (code: string) => code };
  const parts = (id: string) => EXAMS_FIXTURE.exams.find((exam) => exam.id === id)!.parts;
  const render = (examParts: ExamPart[]) =>
    renderWithProviders(
      <ExamOverviewPanel examName="X" summary="" parts={examParts} quotas={[]} onQuotas={vi.fn()} names={names} labels={labels} />,
    );

  it("HSA có phần chọn Khoa học hoặc Tiếng Anh: hiện chú thích bảng tính theo lựa chọn đầu tiên", () => {
    render(parts("ex-hsa-f-001"));
    expect(screen.getByText("Phần có nhiều lựa chọn: bảng tính theo lựa chọn đầu tiên.")).toBeInTheDocument();
  });

  it("bài chỉ có phần cố định: không có chú thích", () => {
    render(parts("ex-hsa-f-001").filter((part) => part.mode === "FIXED"));
    expect(screen.queryByText(/Phần có nhiều lựa chọn/)).not.toBeInTheDocument();
  });
});
