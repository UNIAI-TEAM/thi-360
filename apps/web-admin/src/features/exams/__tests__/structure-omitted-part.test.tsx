import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import type { ExamDetail, ExamPart, QuestionBlock } from "../types";
import { codedExam } from "./coded-fixtures";
import { ExamsBackend } from "./fake-exams-backend";
import { EXAMS_FIXTURE } from "./exam-fixture";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

let backend: ExamsBackend;

beforeEach(() => {
  routerMock.push.mockReset();
  backend = new ExamsBackend();
  backend.install();
});

const block = (overrides: Partial<QuestionBlock>): QuestionBlock => ({
  id: "blk",
  name: "Nhóm",
  questionType: "SINGLE_CHOICE",
  questionCount: 1,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: { nodeIds: [] },
  tagCodes: [],
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  matrix: null,
  ...overrides,
});

/** Hình thật của bài SP2E Ngữ văn vừa tạo từ mẫu mặc định: Phần I 30 câu 6 điểm, Phần II · Viết chỉ có nhóm Viết bài bỏ qua. */
function sp2eVanExam(): ExamDetail {
  const skeleton = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!.parts[0]!);
  const part = (id: string, name: string, order: number, count: number, blocks: QuestionBlock[]): ExamPart => ({
    ...skeleton,
    id,
    name,
    order,
    mode: "FIXED",
    options: [],
    durationMinutes: null,
    questionCount: count,
    questionCountOnPaper: count,
    questionCountRequired: count,
    sections: [{ ...skeleton.sections[0]!, id: `${id}-s`, subjectCode: "LITERATURE", questionCount: count, blocks }],
  });
  return {
    ...codedExam("QDA"),
    id: "ex-sp2e-van",
    code: "SP2E-VAN-001",
    kind: "SP2E",
    timing: "SHARED",
    durationMinutes: 45,
    maxScore: 6,
    totalQuestions: 30,
    parts: [
      part("p1", "Phần I · Đọc hiểu", 1, 30, [block({ id: "b1", name: "Văn bản 1", questionCount: 30, requiredPointsPerQuestion: 0.2 })]),
      part("p2", "Phần II · Viết", 2, 0, [block({ id: "b5", name: "Viết bài", questionType: "ESSAY", questionCount: 1, omitted: true })]),
    ],
  };
}

describe("Màn cấu trúc sau khi tạo SP2E Ngữ văn từ mẫu mặc định (S3-2)", () => {
  it("phần Viết chỉ có nhóm bỏ qua: không có lỗi cần xử lý nào", async () => {
    const exam = sp2eVanExam();
    backend.seedExam(exam);
    window.history.replaceState(null, "", `/exams/${exam.id}/structure`);
    renderWithProviders(<ExamStructureView examId={exam.id} />);
    await screen.findByRole("navigation", { name: "Cấu trúc" });
    expect(await screen.findByRole("heading", { name: "Lỗi cần xử lý (0)" })).toBeInTheDocument();
    expect(screen.queryByText(/số câu phải là số nguyên dương/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Văn học: các nhóm có/i)).not.toBeInTheDocument();
  });
});
