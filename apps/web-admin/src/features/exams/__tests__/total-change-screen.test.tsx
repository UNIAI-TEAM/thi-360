import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

/**
 * R-121 ở mức màn (UNI-2753, R1-F6): môn đổi tổng mà bảng nhập theo tỷ lệ chưa chọn "Áp dụng số mới" hoặc "Giữ số cũ" thì
 * Lưu không gửi, mở môn và nêu lý do (chặn `TOTAL_CHANGE` của `saveBlockers`, `exam-structure-view.tsx`).
 *
 * Mọi ô đổi tổng của môn đều đã chỉ đọc ở bước 2, nên không còn thao tác người dùng nào đổi được tổng của môn. Bản nháp được đổi
 * qua `onChange` mà `ExamStructureView` đưa cho `ExamStructureSubjectPanel`: bản bọc dưới đây thêm một nút gọi đúng `onChange`
 * đó, còn môn, thông báo, hộp thoại và đường lưu vẫn là của màn thật.
 */

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock("../components/exam-structure-subject-panel", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../components/exam-structure-subject-panel")>();
  const Panel = actual.ExamStructureSubjectPanel;
  return {
    ...actual,
    ExamStructureSubjectPanel: (props: ComponentProps<typeof Panel>) => (
      <>
        <button type="button" onClick={() => props.onChange((section) => ({ ...section, questionCount: 20 }))}>
          Đặt tổng môn thành 20
        </button>
        <Panel {...props} />
      </>
    ),
  };
});
vi.mock("../subject-forms/matrix-form", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../subject-forms/matrix-form")>();
  const Form = actual.MatrixForm;
  return {
    ...actual,
    MatrixForm: (props: ComponentProps<typeof Form>) => (
      <>
        <button type="button" onClick={() => props.onChange((section) => ({ ...section, questionCount: 20 }))}>
          Đặt tổng môn thành 20 (MT)
        </button>
        <button type="button" onClick={() => props.onChange((section) => ({ ...section, questionCount: 40 }))}>
          Đặt tổng môn thành 40 (MT)
        </button>
        <Form {...props} />
      </>
    ),
  };
});

vi.mock("../subject-forms/row-list-form", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../subject-forms/row-list-form")>();
  const Form = actual.RowListForm;
  return {
    ...actual,
    RowListForm: (props: ComponentProps<typeof Form>) => (
      <>
        <button type="button" onClick={() => props.onChange((section) => ({ ...section, questionCount: 20 }))}>
          Đặt tổng dòng thành 20
        </button>
        <Form {...props} />
      </>
    ),
  };
});

const STRUCTURE = `${EXAM_PATH}/structure`;

/** Bài một phần, một môn 30 câu có ma trận theo tỷ lệ và chỉ tiêu chủ đề 10%. */
function boot() {
  const base = FIXED_PART.sections![0] as object;
  const section = {
    ...base,
    questionCount: 30,
    blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
    matrix: {
      mode: "SEPARATE",
      unit: "PERCENT",
      byGrade: [{ code: "GRADE_12", value: 100 }],
      byDifficulty: [
        { code: "RECOGNITION", value: 25 },
        { code: "COMPREHENSION", value: 50 },
        { code: "APPLICATION", value: 25 },
        { code: "HIGH_APPLICATION", value: 0 },
      ],
    },
    topicQuotas: [{ id: "t1", name: "Xác suất", nodeIds: [], count: 3, percent: 10, enforcement: "REQUIRED", basis: "QUESTION" }],
  };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 30 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 30, sections: [section] }] } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
}

describe("R-121: môn đổi tổng từ 30 xuống 20 chưa xác nhận bảng theo tỷ lệ", () => {
  it("Lưu không gửi, mở môn kèm thông báo; chọn Áp dụng số mới rồi Lưu gửi đúng một lần với chỉ tiêu chủ đề 2 câu, 10%", async () => {
    const server = boot();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 20" }));
    expect(await screen.findByText(/Tổng đổi thành 20 câu \(trước đó 30 câu\)/)).toBeInTheDocument();

    // Sang nhóm câu của cùng môn (không hỏi chuyển mục): nhóm câu không có ô tổng, chặn phải tự mở lại môn khi Lưu.
    await user.click(screen.getAllByRole("button", { name: /Choice/ })[0]!);
    await waitFor(() => expect(window.location.search).toContain("block=blk-1"));
    expect(screen.queryByRole("heading", { level: 2, name: /Toán/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Tổng đổi thành 20 câu/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    expect(await screen.findByRole("heading", { level: 2, name: /Toán/ })).toBeInTheDocument();
    expect(window.location.search).toContain("section=sec-1");
    expect(await screen.findByText(/tổng số câu của môn .* đã đổi/)).toBeInTheDocument();
    expect(screen.getByText(/Tổng đổi thành 20 câu/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Áp dụng số mới" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ questionCount: number; topicQuotas: Array<{ count: number; percent?: number }> }> }> };
    expect(sent.parts[0]!.sections[0]!.questionCount).toBe(20);
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 2, percent: 10 });
  });

  it("chọn Giữ số cũ: Lưu gửi một lần, chỉ tiêu chủ đề giữ 3 câu, bỏ tỷ lệ", async () => {
    const server = boot();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 20" }));
    await user.click(await screen.findByRole("button", { name: "Giữ số cũ" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ topicQuotas: Array<{ count: number; percent?: number }> }> }> };
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 3 });
    expect(sent.parts[0]!.sections[0]!.topicQuotas[0]!.percent).toBeUndefined();
  });
});

/** Bài THPTQG một môn Lý 30 câu (DS thật): một dòng 30 câu, không bảng nhập theo tỷ lệ. */
function bootRows() {
  const base = FIXED_PART.sections![0] as object;
  const section = {
    ...base,
    subjectCode: "LY",
    questionCount: 30,
    blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
  };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "THPTQG", questionCount: 30 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 30, sections: [section] }] } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ kind: "THPTQG", revision: 8 }) })
    .install();
}

describe("R-9 đổi tổng ở Danh sách dòng: dòng nhập theo số câu giữ số, hiện thiếu hoặc vượt", () => {
  it("rowsKeepCountsOnTotalChange: tổng môn 30 xuống 20: không có hộp Áp dụng số mới, đầu môn báo Vượt 10 câu, Lưu gửi một lần và dòng giữ 30 câu", async () => {
    const server = bootRows();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng dòng thành 20" }));
    expect(await screen.findByText("Vượt 10 câu")).toBeInTheDocument();
    expect(screen.queryByText(/Tổng đổi thành 20 câu/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Áp dụng số mới" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ questionCount: number; blocks: Array<{ questionCount: number }> }> }> };
    expect(sent.parts[0]!.sections[0]!.questionCount).toBe(20);
    expect(sent.parts[0]!.sections[0]!.blocks[0]!.questionCount).toBe(30);
  });

  const bootPercentRows = () => {
    const base = FIXED_PART.sections![0] as object;
    const section = {
      ...base,
      subjectCode: "TOAN",
      questionCount: 30,
      blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
      matrix: {
        mode: "SEPARATE",
        unit: "PERCENT",
        byGrade: [],
        byDifficulty: [
          { code: "RECOGNITION", value: 40 },
          { code: "COMPREHENSION", value: 60 },
        ],
        gradeEnforcement: "REFERENCE",
        difficultyEnforcement: "REFERENCE",
      },
      topicQuotas: [{ id: "t1", name: "Ham so", nodeIds: [], count: 3, percent: 10, enforcement: "REFERENCE", basis: "QUESTION" }],
    };
    return new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "SP2E", questionCount: 30 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 30, sections: [section] }] } })
      .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ kind: "SP2E", revision: 8 }) })
      .install();
  };

  it("percentRowsTotalChange: SP2E DS đổi tổng 30 → 20 không hiện dòng Khối lớp rỗng; Áp dụng số mới và Giữ số cũ gửi đúng PUT", async () => {
    const user = userEvent.setup();
    const server1 = bootPercentRows();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    const first = renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng dòng thành 20" }));
    expect(await screen.findByText(/Tổng đổi thành 20 câu \(trước đó 30 câu\)/)).toBeInTheDocument();
    expect(screen.queryByText(/^Khối lớp:/)).toBeNull();
    expect(screen.getByText(/^Độ khó:/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server1.calls("PUT", STRUCTURE)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Áp dụng số mới" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server1.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sentApply = server1.calls("PUT", STRUCTURE)[0]!.body as {
      parts: Array<{ sections: Array<{ questionCount: number; matrix: { unit: string }; topicQuotas: Array<{ count: number; percent?: number }> }> }>;
    };
    expect(sentApply.parts[0]!.sections[0]!.questionCount).toBe(20);
    expect(sentApply.parts[0]!.sections[0]!.matrix.unit).toBe("PERCENT");
    expect(sentApply.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 2, percent: 10 });
    first.unmount();

    const server2 = bootPercentRows();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
    await user.click(await screen.findByRole("button", { name: "Đặt tổng dòng thành 20" }));
    await user.click(await screen.findByRole("button", { name: "Giữ số cũ" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server2.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sentKeep = server2.calls("PUT", STRUCTURE)[0]!.body as {
      parts: Array<{
        sections: Array<{
          questionCount: number;
          matrix: { unit: string; byGrade: unknown[]; byDifficulty: Array<{ code: string; value: number }> };
          topicQuotas: Array<{ count: number; percent?: number }>;
        }>;
      }>;
    };
    expect(sentKeep.parts[0]!.sections[0]!.questionCount).toBe(20);
    expect(sentKeep.parts[0]!.sections[0]!.matrix).toMatchObject({
      unit: "COUNT",
      byGrade: [],
      byDifficulty: [{ code: "RECOGNITION", value: 12 }, { code: "COMPREHENSION", value: 18 }],
    });
    expect(sentKeep.parts[0]!.sections[0]!.topicQuotas[0]).toMatchObject({ count: 3 });
    expect(sentKeep.parts[0]!.sections[0]!.topicQuotas[0]!.percent).toBeUndefined();
  });
});

/**
 * CR-2 (plan B5): ca tương đương cho MT. Môn QDA mở MT (ô chỉ tiêu theo số câu): đổi tổng không có bảng tỷ lệ nào chờ xác nhận, nên Lưu
 * không bị chặn `TOTAL_CHANGE`; số câu đã khai giữ nguyên và "Đã phân bổ" hiện vượt. (Ca có bảng theo tỷ lệ của V-ACT: `subject-matrix-vact.test.tsx`.)
 */
describe("Đổi tổng ở môn MT (QDA)", () => {
  const matrixServer = () => {
    const section = {
      id: "sec-mt",
      subjectCode: "TOAN",
      questionCount: 30,
      blocks: [{ id: "blk-mt", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
      matrix: { mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [{ code: "GRADE_12", value: 30 }], byDifficulty: [] },
    };
    return new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "QDA", questionCount: 30 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 30, sections: [section] }] } })
      .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
      .install();
  };

  it("totalChangeMatrix: đổi tổng 30 → 20 không chặn Lưu, giữ số đã khai (30), hiện đã phân bổ 30/20; PUT gửi tổng 20", async () => {
    const server = matrixServer();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-mt`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 20 (MT)" }));
    expect(await screen.findByText("30 / 20 câu")).toBeInTheDocument();
    expect(screen.queryByText(/Tổng đổi thành 20 câu/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as { parts: Array<{ sections: Array<{ questionCount: number; matrix: { unit: string; byGrade: Array<{ value: number }> } }> }> };
    expect(sent.parts[0]!.sections[0]!.questionCount).toBe(20);
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ unit: "COUNT", byGrade: [{ value: 30 }] });
  });
});

/**
 * CR-2a′ (FTM R-9 "Đổi tổng", AC-54 a): V-ACT Toán độ khó 25/50/25% của 30 câu, tổng đổi thành 40 trong phiên: thông báo nêu 10/20/10, Lưu bị
 * chặn tới khi chọn Áp dụng số mới (hoặc Giữ số cũ); sau đó lưu `unit: PERCENT` giữ 25/50/25. Giữ số cũ đổi về số câu của tổng cũ (8/15/7).
 */
describe("Đổi tổng ở môn MT có bảng theo tỷ lệ (V-ACT Toán)", () => {
  const vactServer = (byGrade: Array<{ code: string; value: number }> = []) => {
    const section = {
      id: "sec-vact",
      subjectCode: "TOAN",
      questionCount: 30,
      blocks: [
        { id: "blk-single", name: "Single", questionType: "SINGLE_CHOICE", questionCount: 12, requiredPointsPerQuestion: 1 },
        { id: "blk-set", name: "Set", questionType: "QUESTION_SET", questionCount: 18, requiredPointsPerQuestion: 1 },
      ],
      matrix: {
        mode: "SEPARATE",
        unit: "PERCENT",
        cells: [],
        byGrade,
        byDifficulty: [
          { code: "RECOGNITION", value: 25 },
          { code: "COMPREHENSION", value: 50 },
          { code: "APPLICATION", value: 25 },
        ],
        gradeEnforcement: "REFERENCE",
        difficultyEnforcement: "REFERENCE",
      },
    };
    return new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "VACT", questionCount: 30 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [{ ...FIXED_PART, questionCount: 30, sections: [section] }] } })
      .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
      .install();
  };
  type Sent = { parts: Array<{ sections: Array<{ questionCount: number; matrix: { unit: string; byDifficulty: Array<{ code: string; value: number }> } }> }> };

  it("totalChangePercentMatrix: 30 → 40 hiện 10/20/10, Lưu chặn tới khi Áp dụng số mới; PUT giữ PERCENT 25/50/25", async () => {
    const server = vactServer();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-vact`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 40 (MT)" }));
    expect(await screen.findByText(/Tổng đổi thành 40 câu \(trước đó 30 câu\)/)).toBeInTheDocument();
    // R1-8: độ khó không kể ô chưa khai (Vận dụng cao) và khối lớp chưa khai không phải điều kiện của MT, nên không có dòng Khối lớp.
    expect(screen.getByText(/^Độ khó: /)).toHaveTextContent(/^Độ khó: Nhận biết 10 · Thông hiểu 20 · Vận dụng 10$/);
    expect(screen.queryByText(/^Khối lớp: /)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(server.calls("PUT", STRUCTURE)).toHaveLength(0);
    expect(await screen.findByText(/tổng số câu của môn .* đã đổi/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Áp dụng số mới" }));
    expect(screen.queryByText(/Tổng đổi thành 40 câu/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as Sent;
    expect(sent.parts[0]!.sections[0]!.questionCount).toBe(40);
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ unit: "PERCENT", byDifficulty: [{ value: 25 }, { value: 50 }, { value: 25 }] });
  });

  it("totalChangePercentMatrix: Giữ số cũ đổi bảng sang số câu của tổng cũ (8/15/7), chiều khối lớp chưa khai giữ mảng rỗng", async () => {
    const server = vactServer();
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-vact`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 40 (MT)" }));
    await user.click(await screen.findByRole("button", { name: "Giữ số cũ" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const sent = server.calls("PUT", STRUCTURE)[0]!.body as Sent & { parts: Array<{ sections: Array<{ matrix: { byGrade: unknown[] } }> }> };
    expect(sent.parts[0]!.sections[0]!.matrix).toMatchObject({ unit: "COUNT", byDifficulty: [{ value: 8 }, { value: 15 }, { value: 7 }], byGrade: [] });
  });

  it("totalChangePercentMatrix: chiều khối lớp đã khai chỉ liệt kê ô đã khai (Lớp 12 40), không liệt kê Lớp 10, 11, Liên lớp", async () => {
    vactServer([{ code: "GRADE_12", value: 100 }]);
    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-vact`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Đặt tổng môn thành 40 (MT)" }));
    const line = await screen.findByText(/^Khối lớp: /);
    expect(line).toHaveTextContent(/^Khối lớp: Lớp 12 40$/);
  });
});
