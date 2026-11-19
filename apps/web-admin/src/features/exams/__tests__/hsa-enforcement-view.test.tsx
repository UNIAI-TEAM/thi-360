import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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

const referenceMatrix = {
  mode: "SEPARATE",
  unit: "COUNT",
  byGrade: [{ code: "GRADE_12", value: 30 }],
  byDifficulty: [{ code: "RECOGNITION", value: 30 }],
  gradeEnforcement: "REFERENCE",
  difficultyEnforcement: "REFERENCE",
};

/** Bài một phần, một môn 30 câu có ma trận môn ở mức Tham khảo; `kind` chọn kỳ thi. */
function boot(kind: string) {
  const base = FIXED_PART.sections![0] as object;
  const section = {
    ...base,
    questionCount: 30,
    matrix: referenceMatrix,
    blocks: [{ id: "blk-1", name: "Choice", questionType: "SINGLE_CHOICE", questionCount: 30, requiredPointsPerQuestion: 1 }],
  };
  const part = { ...FIXED_PART, questionCount: 30, sections: [section] };
  return new FakeServer()
    .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind, questionCount: 30 }) })
    .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
    .on("GET", STRUCTURE, { status: 200, body: { examId: EXAM_ID, revision: 7, parts: [part] } })
    .on("PUT", STRUCTURE, { status: 200, body: examDetailWire({ revision: 8 }) })
    .install();
}

function open() {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
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
    await user.click(screen.getByRole("button", { name: "Lưu" }));
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
