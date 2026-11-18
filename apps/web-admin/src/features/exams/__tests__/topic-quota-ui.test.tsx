import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { allocationProgress } from "../allocation";
import { ExamStructureView } from "../components/exam-structure-view";
import { ReferenceDistributions } from "../components/subject-allocation-extras";
import { TopicQuotaTable } from "../components/topic-quota-table";
import { referenceEntries, referenceToMatrix } from "../reference-distribution";
import type { ExamQuota, ExamSection, TopicQuota } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10", "GRADE_11", "GRADE_12"], difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };

function Harness({ initial, total = 30 }: { initial: TopicQuota[]; total?: number }) {
  const [quotas, setQuotas] = useState(initial);
  return (
    <>
      <TopicQuotaTable id="s1" quotas={quotas} onChange={setQuotas} total={total} levelName="Toán học" subjectCode="MATH" reference={reference} axes={axes} labels={labels} />
      <output data-testid="state">{JSON.stringify(quotas)}</output>
    </>
  );
}

const quota = (over: Partial<TopicQuota> = {}): TopicQuota => ({ id: "q1", name: "Xác suất", nodeIds: [], count: 3, enforcement: "REQUIRED", basis: "QUESTION", ...over });
const stateOf = () => JSON.parse(screen.getByTestId("state").textContent ?? "[]") as TopicQuota[];

describe("Chỉ tiêu chủ đề (R-118, R-126)", () => {
  it("nhập tỷ lệ 10 trên 30 câu: số câu 3, còn lại 27, lưu count và percent", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[quota({ count: 1 })]} />);
    await user.click(screen.getByRole("radio", { name: "Tỷ lệ" }));
    const percent = screen.getByRole("textbox", { name: /Tỷ lệ · Xác suất/ });
    await user.clear(percent);
    await user.type(percent, "10");
    expect(stateOf()[0]).toMatchObject({ count: 3, percent: 10 });
    expect(screen.getByText("27 câu")).toBeInTheDocument();
    expect(screen.getAllByText("ít nhất").length).toBeGreaterThan(0);
  });

  it("nhập số câu 12 trên 30: tỷ lệ 40%, không có percent", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial={[quota()]} />);
    const count = screen.getByRole("textbox", { name: /Số câu ít nhất · Xác suất/ });
    await user.clear(count);
    await user.type(count, "12");
    expect(stateOf()[0]!.percent).toBeUndefined();
    expect(screen.getByText("40%")).toBeInTheDocument();
  });

  it("dòng chưa gắn MAPID ghi Chờ mapping; Bắt buộc thì báo chặn kiểm tra, Tham khảo thì ghi chưa có dữ liệu đối chiếu", async () => {
    renderWithProviders(<Harness initial={[quota(), quota({ id: "q2", name: "Hàm số", enforcement: "REFERENCE" })]} />);
    expect(screen.getAllByText("Chờ mapping")).toHaveLength(2);
    expect(screen.getByText(/Dòng Xác suất là Bắt buộc nhưng chưa gắn MAPID/)).toBeInTheDocument();
    expect(screen.getByText("Chưa có dữ liệu đối chiếu")).toBeInTheDocument();
  });

  it("quá 8 chỉ tiêu: lỗi hiện ngay ở khối, nút thêm bị khóa khi đủ 8", async () => {
    const eight = Array.from({ length: 8 }, (_, index) => quota({ id: `q${index}`, name: `Chủ đề ${index}`, nodeIds: ["kn-0D1-1"] }));
    const { unmount } = renderWithProviders(<Harness initial={eight} />);
    expect(screen.getByRole("button", { name: "Thêm chỉ tiêu chủ đề" })).toBeDisabled();
    expect(screen.queryByText(/tối đa 8 chỉ tiêu/)).not.toBeInTheDocument();
    unmount();
    renderWithProviders(<Harness initial={[...eight, quota({ id: "q9", nodeIds: ["kn-0D1-1"] })]} />);
    expect(screen.getByRole("alert")).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề\. Toán học đang có 9/);
  });
});

describe("Phân bố tham khảo (H7, K9, H21)", () => {
  const section = (over: Partial<ExamSection> = {}): ExamSection => ({
    id: "s-anh",
    subjectCode: "ANH",
    questionCount: 50,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [],
    ...over,
  });
  const gradeQuota = (over: Partial<ExamQuota> = {}): ExamQuota => ({
    id: "hsa-english-grade",
    name: "Lớp Tiếng Anh tham khảo",
    dimension: "GRADE",
    basis: "QUESTION",
    enforcement: "REFERENCE",
    subjectCodes: ["ANH"],
    buckets: [{ codes: ["GRADE_10"], percent: 20 }, { codes: ["GRADE_11"], percent: 30 }, { codes: ["GRADE_12"], percent: 50 }],
    ...over,
  });

  it("chép số tham khảo vào ma trận bắt buộc của môn và giữ bản tham khảo", () => {
    const next = referenceToMatrix(section(), gradeQuota(), axes);
    expect(next).toMatchObject({ mode: "SEPARATE", unit: "PERCENT", gradeEnforcement: "REQUIRED", basis: "QUESTION" });
    expect(next.byGrade.map((item) => item.value)).toEqual([20, 30, 50, 0].slice(0, 3));
  });

  it("'Áp dụng phân bổ này' chỉ bật cho đơn vị câu; lệnh hỏi bị khóa kèm lý do, nút còn lại cũng khóa", async () => {
    const promptMatrix = { mode: "SEPARATE" as const, unit: "COUNT" as const, cells: [], byGrade: [{ code: "GRADE_10", value: 4 }], byDifficulty: [], gradeEnforcement: "REFERENCE" as const, basis: "PROMPT" as const };
    const entries = referenceEntries(section({ matrix: promptMatrix }), []);
    renderWithProviders(<ReferenceDistributions entries={entries} labels={labels} subjectName="Hóa học" onUse={vi.fn()} onApply={vi.fn()} />);
    expect(screen.getByRole("button", { name: /Áp dụng phân bổ này/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Dùng ma trận tham khảo/ })).toBeDisabled();
    expect(screen.getByText(/phân bố bắt buộc phải tính theo câu/)).toBeInTheDocument();
  });

  it("chỉ tiêu một môn đơn vị câu: bật hai nút; Áp dụng gọi đúng mục", async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    renderWithProviders(<ReferenceDistributions entries={referenceEntries(section(), [gradeQuota()])} labels={labels} subjectName="Tiếng Anh" onUse={vi.fn()} onApply={onApply} />);
    const apply = screen.getByRole("button", { name: /Áp dụng phân bổ này/ });
    expect(apply).toBeEnabled();
    await user.click(apply);
    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it("tiến độ phân bổ: thiếu, đủ, vượt", () => {
    expect(allocationProgress(27, 30).state).toBe("SHORT");
    expect(allocationProgress(30, 30).state).toBe("MATCH");
    expect(allocationProgress(32, 30)).toMatchObject({ state: "OVER", missing: -2 });
  });
});

describe("Lưu chỉ tiêu chủ đề, nhánh thật (H20)", () => {
  let server: FakeServer;
  const section = {
    ...(FIXED_PART.sections![0] as object),
    topicQuotas: Array.from({ length: 8 }, (_, index) => ({ id: `t${index}`, name: `Chủ đề ${index}`, nodeIds: [], count: 1, enforcement: "REFERENCE" })),
  };
  const part = { ...FIXED_PART, sections: [section] };
  const path = `${EXAM_PATH}/structure`;

  beforeEach(() => {
    server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire() })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", path, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
      .install();
  });

  it("422 quá 8 chỉ tiêu hiện ở khối chỉ tiêu của môn, không chỉ ở thông báo nổi", async () => {
    server.on("PUT", path, {
      status: 422,
      body: { code: "EXAM_STRUCTURE_INVALID", message: "x", fieldErrors: [{ field: "topicQuotas", code: "MAX_TOPIC_QUOTAS" }] },
    });
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
    const first = await screen.findByRole("textbox", { name: "Tên chủ đề của chỉ tiêu 1" });
    await user.type(first, "x");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", path)).toHaveLength(1));
    const block = screen.getByRole("region", { name: /Chỉ tiêu chủ đề của/ }).closest("section")!;
    expect(await within(block).findByRole("alert")).toHaveTextContent(/tối đa 8 chỉ tiêu chủ đề/);
  });
});
