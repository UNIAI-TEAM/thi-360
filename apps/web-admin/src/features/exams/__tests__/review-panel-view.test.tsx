import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
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

const reviewTemplate = {
  code: "T_REVIEW",
  kindCode: "HSA",
  scope: "FULL",
  timing: "PER_PART",
  label: "Bài ôn",
  active: true,
  isDefault: true,
  buildMode: "REVIEW",
  publishable: true,
  fixedCounts: false,
  structure: { parts: [] },
};

interface SentBlock {
  questionType: string | null;
  questionCount: number | null;
  setCount?: number | null;
  setSize?: number | null;
  slots?: Array<{ index: number; tagCodes?: string[] }>;
}
interface Sent {
  parts: Array<{ questionCount: number; sections: Array<{ questionCount: number | null; blocks: SentBlock[] }> }>;
}

/** Bài ôn: một phần 10 câu, một môn, một nhóm; `block` ghi đè trường của nhóm. */
function boot(block: Record<string, unknown> = {}, templatesStatus = 200) {
  const base = FIXED_PART.sections![0] as object;
  const part = {
    ...FIXED_PART,
    sections: [{ ...base, blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 10, requiredPointsPerQuestion: 1, ...block }] }],
  };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ questionCount: 10, templateCode: "T_REVIEW" }) })
    .on("GET", "/api/v1/admin/exam-templates", templatesStatus === 200 ? { status: 200, body: [reviewTemplate] } : { status: templatesStatus, body: { code: "BOOM" } })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
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

describe("Bài ôn: sửa và lưu (R1-17, R1-30)", () => {
  it("R1-17: đổi dạng câu và số lượng, lưu gửi đúng dạng và số câu của môn lẫn nhóm; lệch bước 1 thì cảnh báo", async () => {
    const server = boot();
    const user = userEvent.setup();
    open();
    await user.click(await screen.findByRole("combobox", { name: "Dạng câu" }));
    await user.click(await screen.findByRole("option", { name: "Trả lời ngắn" }));
    const count = screen.getByRole("textbox", { name: /^Số lượng/ });
    await user.clear(count);
    await user.type(count, "12");
    expect(await screen.findByText(/Bước 1 đặt 10 câu, ở đây đang 12/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const section = sentOf(server).parts[0]!.sections[0]!;
    expect(section.questionCount).toBe(12);
    expect(section.blocks).toHaveLength(1);
    expect(section.blocks[0]).toMatchObject({ questionType: "SHORT_ANSWER", questionCount: 12 });
  });

  it("R1-17: khớp bước 1 thì không có cảnh báo lệch", async () => {
    boot();
    open();
    await screen.findByRole("combobox", { name: "Dạng câu" });
    expect(screen.queryByText(/Bước 1 đặt/)).not.toBeInTheDocument();
  });

  it("R1-27: mẫu cấu trúc không tải được thì chưa cho sửa: bài ôn không hiện màn đầy đủ, chỉ có nút Thử lại", async () => {
    boot({}, 500);
    open();
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /Số câu của mục/ })).not.toBeInTheDocument();
  });

  it("R1-30: đổi số chùm 3 → 2 bỏ điều kiện của chùm 3 và tính lại số câu, như ở nhóm thường", async () => {
    const server = boot({
      questionType: "QUESTION_SET",
      setCount: 3,
      setSize: 2,
      questionCount: 6,
      slots: [{ index: 1, tagCodes: ["X"] }, { index: 3, tagCodes: ["Y"] }],
    });
    const user = userEvent.setup();
    open();
    const sets = await screen.findByRole("textbox", { name: /^Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", STRUCTURE)).toHaveLength(1));
    const block = sentOf(server).parts[0]!.sections[0]!.blocks[0]!;
    expect(block).toMatchObject({ setCount: 2, setSize: 2, questionCount: 4 });
    expect(block.slots?.map((slot) => slot.index)).toEqual([1, 2]);
    expect(block.slots?.[0]?.tagCodes).toEqual(["X"]);
  });
});
