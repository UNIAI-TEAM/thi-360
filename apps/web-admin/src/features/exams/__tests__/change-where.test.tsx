import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { parseChangeWhere } from "../change-where";
import { useIssueText } from "../components/exam-review-shared";
import { ExamVersionsView } from "../components/exam-versions-view";
import type { CheckIssue } from "../review-types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex/versions",
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

describe("parseChangeWhere", () => {
  it("tách bộ sưu tập, id phần tử và tên trường; bỏ chỉ số mảng", () => {
    expect(parseChangeWhere("parts/parts/p1/sections/s1/blocks/b1/name")).toEqual({
      config: false,
      steps: [
        { level: "part", id: "p1" },
        { level: "section", id: "s1" },
        { level: "block", id: "b1" },
      ],
      fields: ["name"],
    });
    expect(parseChangeWhere("parts/parts/p1/sections/s1/blocks")?.steps.at(-1)).toEqual({ level: "block", id: null });
    expect(parseChangeWhere("parts/parts/p1/sections/s1/matrix/cells/0/value")?.fields).toEqual(["matrix", "cells", "value"]);
    expect(parseChangeWhere("config/durationMinutes")).toEqual({ config: true, steps: [], fields: ["durationMinutes"] });
  });

  it("chữ mô tả không phải đường dẫn thì trả null để giữ nguyên", () => {
    expect(parseChangeWhere("Phần 1 · Toán")).toBeNull();
  });
});

const PART_ID = "part-1";
const SERVING_PATH = { pathId: "default", choices: [] };

function change(id: string, where: string) {
  return { id, kind: "BLOCK_CHANGED", where, before: "A", after: "B" };
}

describe("màn Phiên bản: vị trí thay đổi", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    new FakeServer()
      .on("GET", EXAM_PATH, {
        status: 200,
        body: examDetailWire({ status: "PUBLISHED", servingVersion: 1 }),
      })
      .on("GET", `${EXAM_PATH}/versions`, {
        status: 200,
        body: {
          examId: EXAM_ID,
          workingCopy: {
            editedBy: { id: "u1", name: "Cô Lan" },
            savedAt: "2026-11-17T02:00:00Z",
            baseVersion: 1,
            check: { status: "UNCHECKED", issueCount: 0 },
            unpublishedPaths: [SERVING_PATH],
          },
          changes: [
            change("c1", `parts/parts/${PART_ID}/sections/sec-1/blocks/blk-1/name`),
            change("c2", "parts/parts/9f1c2d3e-0000-4000-8000-000000000001/sections/9f1c2d3e-0000-4000-8000-000000000002/blocks/9f1c2d3e-0000-4000-8000-000000000003/questionCount"),
            change("c3", "config/durationMinutes"),
          ],
          items: [
            {
              version: 1,
              state: "SERVING",
              publishedAt: "2026-11-10T01:00:00Z",
              attemptCount: 0,
              publishedPaths: [SERVING_PATH],
              servingPaths: [SERVING_PATH],
            },
          ],
        },
      })
      .install();
  });

  it("in tên phần, môn, khối từ cấu trúc; id không còn thì chữ chung; không bao giờ in đường dẫn thô hay id", async () => {
    renderWithProviders(<ExamVersionsView examId={EXAM_ID} />);

    expect(
      await screen.findByText((_, element) => element?.tagName === "SPAN" && element.textContent === "Part 1 · MATH · Choice · Tên"),
    ).toBeInTheDocument();
    expect(screen.getByText("Phần · Môn · Khối · Số câu")).toBeInTheDocument();
    expect(screen.getByText("Cấu hình bài thi · Thời lượng")).toBeInTheDocument();
    expect(screen.queryByText(/parts\/|9f1c2d3e|sections|blk-1/)).not.toBeInTheDocument();
  });
});

function IssueLine({ issue }: { issue: CheckIssue }) {
  const issueText = useIssueText();
  return <p>{issueText(issue)}</p>;
}

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "CELL_SHORTAGE",
    severity: "ERROR",
    group: "MATRIX",
    partId: null,
    partName: null,
    sectionId: null,
    subjectCode: "MATH",
    blockId: null,
    blockName: "Khối A",
    optionId: null,
    gradeCode: null,
    difficultyCode: null,
    dimension: null,
    competencyCode: null,
    answerType: null,
    subjectCount: null,
    required: 8,
    available: 3,
    timeLimitSeconds: null,
    questionCodes: null,
    ...overrides,
  } as CheckIssue;
}

describe("câu mô tả vấn đề thiếu nguồn", () => {
  beforeEach(() => {
  });

  it("ô không có lớp lẫn độ khó thì bỏ cả cụm, không in dấu '/'", async () => {
    renderWithProviders(<IssueLine issue={issueOf({})} />);

    const text = await screen.findByText(/cần 8 câu/);
    expect(text.textContent).toMatch(/ · Khối A cần 8 câu, hiện có 3 câu phù hợp\.$/);
    expect(text.textContent).not.toContain("/");
  });

  it("có đủ lớp và độ khó thì vẫn ghi 'lớp/độ khó'", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ gradeCode: "G12", difficultyCode: "HARD" })} />);

    const text = await screen.findByText(/cần 8 câu/);
    expect(text.textContent).toMatch(/cần 8 câu \S+\/\S+, hiện có/);
  });
});
