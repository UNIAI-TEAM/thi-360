import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { saveBlockers } from "../allocation";
import { ExamStructureView } from "../components/exam-structure-view";
import { referenceEntries, referenceToMatrix } from "../reference-distribution";
import type { ExamQuota, ExamSection } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE = `${EXAM_PATH}/structure`;
const axes = { grades: ["GRADE_10", "GRADE_11", "GRADE_12"], difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"] };

interface SentMatrix {
  mode: string;
  unit: string;
  byGrade: Array<{ code: string; value: number }>;
  byDifficulty: Array<{ code: string; value: number }>;
  gradeEnforcement?: string;
  difficultyEnforcement?: string;
}
interface Sent {
  parts: Array<{ sections: Array<{ matrix: SentMatrix | null }> }>;
  examQuotas?: Array<{ id: string; enforcement: string }>;
}

const difficultyQuota = (): ExamQuota => ({
  id: "toan-difficulty-reference",
  name: "Độ khó Toán tham khảo",
  dimension: "DIFFICULTY",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["MATH"],
  buckets: axes.difficulties.map((code, index) => ({ codes: [code], percent: [30, 30, 25, 15][index]! })),
});

const separatePercent = (over: Partial<SentMatrix> = {}) => ({
  mode: "SEPARATE",
  unit: "PERCENT",
  byGrade: [{ code: "GRADE_12", value: 100 }],
  byDifficulty: axes.difficulties.map((code, index) => ({ code, value: [25, 50, 25, 0][index]! })),
  ...over,
});

function boot(section: Record<string, unknown>, examQuotas: ExamQuota[] = []) {
  const base = FIXED_PART.sections![0] as object;
  const part = {
    ...FIXED_PART,
    questionCount: 30,
    sections: [{ ...base, questionCount: 30, blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }], ...section }],
  };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "VACT", questionCount: 30 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part], examQuotas } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
}

function open() {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

const sentOf = (server: FakeServer) => server.calls("PUT", STRUCTURE)[0]!.body as Sent;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("Nút của phân bố tham khảo ghi đúng vào bản lưu (R1-19, R1-08)", () => {
  it("R1-19: Dùng ma trận tham khảo chép số của chỉ tiêu cấp bài vào ma trận bắt buộc của môn, chỉ tiêu giữ Tham khảo", async () => {
    const server = boot({}, [difficultyQuota()]);
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("button", { name: /Dùng ma trận tham khảo/ }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = sentOf(server);
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ mode: "SEPARATE", unit: "PERCENT", difficultyEnforcement: "REQUIRED" });
    expect(sent.parts[0]!.sections[0]!.matrix!.byDifficulty.map((item) => item.value)).toEqual([30, 30, 25, 15]);
    expect(sent.examQuotas).toMatchObject([{ id: "toan-difficulty-reference", enforcement: "REFERENCE" }]);
  });

  it("R1-19: Áp dụng phân bổ này đổi chỉ tiêu cấp bài một môn thành Bắt buộc", async () => {
    const server = boot({}, [difficultyQuota()]);
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("button", { name: /Áp dụng phân bổ này/ }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(sentOf(server).examQuotas).toMatchObject([{ id: "toan-difficulty-reference", enforcement: "REQUIRED" }]);
  });

  it("R1-19: Áp dụng phân bổ này ở chiều tham khảo của ma trận môn đổi chiều đó thành Bắt buộc, chiều kia giữ nguyên", async () => {
    const server = boot({ matrix: separatePercent({ gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" }) });
    const user = userEvent.setup();
    open();
    const buttons = await screen.findAllByRole("button", { name: /Áp dụng phân bổ này/ });
    expect(buttons.length).toBeGreaterThanOrEqual(2);
    await user.click(buttons[0]!);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const matrix = sentOf(server).parts[0]!.sections[0]!.matrix!;
    expect([matrix.gradeEnforcement, matrix.difficultyEnforcement].sort()).toEqual(["REFERENCE", "REQUIRED"]);
  });

  it("R1-08: môn chưa có ma trận chép một chiều thì chiều kia chưa khai, không bị chặn lưu", () => {
    const section = { id: "s", subjectCode: "MATH", questionCount: 30, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: null, blocks: [] } as ExamSection;
    const matrix = referenceToMatrix(section, difficultyQuota(), axes);
    expect(matrix).toMatchObject({ byGrade: [], gradeEnforcement: "REFERENCE", difficultyEnforcement: "REQUIRED" });
    const part = { ...FIXED_PART, id: "p", mode: "FIXED", sections: [{ ...section, matrix }], options: [] } as unknown as Parameters<typeof saveBlockers>[0][number];
    expect(saveBlockers([part], axes)).toEqual([]);
  });

  it("R1-12: chỉ tiêu một môn tính theo lệnh hỏi hoặc điểm không được dùng ma trận hay đặt Bắt buộc (H7)", () => {
    const section = { id: "s", subjectCode: "MATH", questionCount: 30, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: null, blocks: [] } as ExamSection;
    const [entry] = referenceEntries(section, [{ ...difficultyQuota(), basis: "PROMPT" }]);
    expect(entry).toMatchObject({ kind: "quota", use: "BASIS", apply: "BASIS" });
    const [question] = referenceEntries(section, [difficultyQuota()]);
    expect(question).toMatchObject({ use: null, apply: null });
  });

  it("R1-08: bấm Dùng ma trận tham khảo trên môn không có ma trận rồi Lưu: máy chủ nhận bản lưu, không bị chặn 'allocationBlocked'", async () => {
    const server = boot({}, [difficultyQuota()]);
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("button", { name: /Dùng ma trận tham khảo/ }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    expect(screen.queryByText(/bảng phân bổ còn lỗi/)).not.toBeInTheDocument();
  });
});

describe("Mức Bắt buộc của khối lớp và độ khó đổi riêng (R1-15)", () => {
  async function toggle(user: ReturnType<typeof userEvent.setup>, caption: RegExp, level: "Bắt buộc" | "Tham khảo") {
    const group = await screen.findByRole("group", { name: caption });
    await user.click(within(group).getByRole("radio", { name: level }));
  }

  it("khối lớp Tham khảo, độ khó vẫn Bắt buộc riêng; rồi ngược lại", async () => {
    const first = boot({ matrix: separatePercent() });
    const user = userEvent.setup();
    const view = open();
    await toggle(user, /Mức của Theo khối lớp/, "Tham khảo");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(first.calls("PUT", STRUCTURE)).toHaveLength(1));
    const grade = sentOf(first).parts[0]!.sections[0]!.matrix!;
    expect(grade.gradeEnforcement).toBe("REFERENCE");
    expect(grade.difficultyEnforcement ?? "REQUIRED").toBe("REQUIRED");
    view.unmount();

    const second = boot({ matrix: separatePercent() });
    open();
    await toggle(user, /Mức của Theo độ khó/, "Tham khảo");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(second.calls("PUT", STRUCTURE)).toHaveLength(1));
    const difficulty = sentOf(second).parts[0]!.sections[0]!.matrix!;
    expect(difficulty.difficultyEnforcement).toBe("REFERENCE");
    expect(difficulty.gradeEnforcement ?? "REQUIRED").toBe("REQUIRED");
  });
});
