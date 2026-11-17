import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamVersionDetailDialog } from "../components/exam-versions-detail-dialog";
import { defaultExamReviewApiDeps } from "../review-api";
import type { ExamVersionDetail } from "../review-types";
import { ExamsBackend } from "./fake-exams-backend";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

beforeEach(() => {
  const backend = new ExamsBackend();
  backend.install();
  // Bản chụp có phương án chọn môn chưa có tên và một nhóm chưa giới hạn dạng câu.
  const parts = structuredClone(backend.app("ex-hsa-f-001").parts);
  for (const part of parts) {
    for (const option of part.options) if (option.kind === "SUBJECT_CHOICE") option.name = null;
  }
  parts[0]!.sections[0]!.blocks[0]!.questionType = null;
  vi.spyOn(defaultExamReviewApiDeps, "getExamVersion").mockResolvedValue({
    examId: "ex-hsa-f-001",
    row: { version: 1, state: "RETIRED", publishedAt: null, publishedBy: null, attemptCount: 0, note: null, publishedPaths: [] },
    parts,
  } as unknown as ExamVersionDetail);
});

afterEach(() => vi.restoreAllMocks());

describe("chi tiết phiên bản đã công bố (G4)", () => {
  it("phương án chọn môn không có tên thì không lộ mã SCIENCE, nhóm chưa có dạng câu không để dấu phân cách đôi", async () => {
    renderWithProviders(<ExamVersionDetailDialog examId="ex-hsa-f-001" version={1} onOpenChange={() => {}} />);
    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText(/^Phần 1/);
    expect(dialog.textContent).not.toMatch(/\bSCIENCE\b/);
    expect(dialog.textContent).toContain("Chọn môn");
    expect(dialog.textContent).not.toContain("· ·");
  });
});
