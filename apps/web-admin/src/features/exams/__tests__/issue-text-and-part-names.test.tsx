import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { usePartNames } from "../components/create-exam-fields";
import type { ContentReference } from "@/features/content/types";
import { useIssueText, useIssueWithPath } from "../components/exam-review-shared";
import type { CheckIssue, ExamPathRef } from "../review-types";

// Danh mục theo mã môn của dữ liệu thật (TOAN, VAN) và tên theo ngôn ngữ như máy chủ trả theo Accept-Language.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const getContentReference = (locale: string = "vi") =>
    Promise.resolve({
      subjects: [
        { code: "TOAN", name: locale === "en" ? "Mathematics" : "Toán" },
        { code: "VAN", name: locale === "en" ? "Literature" : "Ngữ văn" },
      ],
      grades: [{ code: "G12", name: locale === "en" ? "Grade 12" : "Lớp 12" }],
      difficulties: [{ code: "VDC", name: "VDC" }],
      tags: [],
      competencies: [],
    } as unknown as ContentReference);
  return { ...(await importOriginal()), getContentReference, defaultContentApiDeps: { getContentReference } };
});

function PartNames({ codes }: { codes: string[] }) {
  const { partName } = usePartNames();
  return <p>{codes.map(partName).join(" | ")}</p>;
}

describe("tên phần của bài Theo phần", () => {
  it("mã môn của danh mục hiện tên theo ngôn ngữ, không hiện mã thô (vi)", async () => {
    renderWithProviders(<PartNames codes={["TOAN", "VAN", "SCIENCE"]} />);

    expect(await screen.findByText("Toán | Ngữ văn | Khoa học")).toBeInTheDocument();
  });

  it("mã môn của danh mục hiện tên tiếng Anh khi giao diện là en", async () => {
    renderWithProviders(<PartNames codes={["TOAN", "VAN", "SCIENCE"]} />, { locale: "en", messages: en });

    expect(await screen.findByText("Mathematics | Literature | Science")).toBeInTheDocument();
  });
});

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "CELL_SHORTAGE",
    severity: "ERROR",
    group: "MATRIX",
    partId: null,
    partName: null,
    sectionId: null,
    subjectCode: "TOAN",
    blockId: null,
    blockName: "Khối A",
    optionId: null,
    gradeCode: null,
    difficultyCode: null,
    dimension: null,
    competencyCode: null,
    answerType: null,
    subjectCount: null,
    required: null,
    available: null,
    timeLimitSeconds: null,
    questionCodes: null,
    ...overrides,
  } as CheckIssue;
}

function IssueLine({ issue }: { issue: CheckIssue }) {
  const issueText = useIssueText();
  return <p>{issueText(issue)}</p>;
}

describe("câu mô tả vấn đề thiếu nguồn khi máy chủ điền hoặc chưa điền số câu", () => {
  it("điền đủ lớp, độ khó, cần, có thì nêu cả bốn", async () => {
    renderWithProviders(
      <IssueLine issue={issueOf({ gradeCode: "G12", difficultyCode: "VDC", required: 12, available: 4 })} />,
    );

    const text = await screen.findByText(/Lớp 12\/VDC/);
    expect(text.textContent).toMatch(/cần 12 câu Lớp 12\/VDC, hiện có 4 câu phù hợp\.$/);
  });

  it("khối nhận số câu thay đổi (phần còn lại) thì ghi số câu môn nhận", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ subjectCount: 17, required: 5, available: 2 })} />);

    const text = await screen.findByText(/môn nhận 17 câu/);
    expect(text.textContent).toMatch(/Khối A \(môn nhận 17 câu\) cần 5 câu, hiện có 2 câu phù hợp\.$/);
  });

  it("thiếu số cần hoặc số có (null) thì không in 'cần 0 câu, hiện có 0 câu'", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ subjectCount: 17 })} />);

    const text = await screen.findByText(/chưa đủ cho chỉ tiêu/);
    expect(text.textContent).toMatch(/Khối A \(môn nhận 17 câu\): nguồn câu hỏi phù hợp chưa đủ cho chỉ tiêu\.$/);
    expect(text.textContent).not.toMatch(/cần 0|hiện có 0/);
  });

  it("bản en cũng có câu không nêu số", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ gradeCode: "G12", difficultyCode: "VDC" })} />, {
      locale: "en",
      messages: en,
    });

    const text = await screen.findByText(/Grade 12\/VDC/);
    expect(text.textContent).toMatch(/Block Mathematics · Khối A \(Grade 12\/VDC\): there are not enough/);
    expect(text.textContent).not.toMatch(/needs 0|0 matching/);
  });

  it("cần 2,5 và có 1,25 (thập phân của BE-5) hiện '2,5' và '1,25' theo dấu phẩy của vi", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ required: 2.5, available: 1.25 })} />);

    const text = await screen.findByText(/cần 2,5 câu/);
    expect(text.textContent).toMatch(/cần 2,5 câu, hiện có 1,25 câu phù hợp\.$/);
  });

  it("tối đa 2 chữ số thập phân, không đệm số 0: 1,2345 → 1,23 và 3,0 → 3", async () => {
    renderWithProviders(
      <IssueLine issue={issueOf({ code: "PART_POINTS_MISMATCH", partName: "Phần 1", required: 3, available: 1.2345 })} />,
    );

    const text = await screen.findByText(/các khối có tổng/);
    expect(text.textContent).toBe("Phần 1: các khối có tổng 1,23 điểm, phần cần 3 điểm.");
  });

  it("bản en dùng dấu chấm: 2.5 và 1.25", async () => {
    renderWithProviders(<IssueLine issue={issueOf({ required: 2.5, available: 1.25 })} />, {
      locale: "en",
      messages: en,
    });

    const text = await screen.findByText(/needs 2\.5/);
    expect(text.textContent).toMatch(/needs 2\.5 .*1\.25 matching/);
  });
});

const SCIENCE_PATH: ExamPathRef = {
  pathId: "p1",
  choices: [
    { partId: "part-1", optionId: "opt-1", kind: "SUBJECT_CHOICE", code: "KHOA_HOC", name: "Khoa học" },
  ],
} as ExamPathRef;

function IssueWithPathLine({ issue }: { issue: CheckIssue }) {
  const issueWithPath = useIssueWithPath([SCIENCE_PATH]);
  return <p>{issueWithPath(issue)}</p>;
}

describe("vấn đề kèm tên đường", () => {
  it("tên phần đã chứa tên lựa chọn thì chỉ ghi một lần", async () => {
    renderWithProviders(
      <IssueWithPathLine
        issue={issueOf({
          code: "SUBJECT_CHOICE_COUNTS_INVALID",
          partName: "Khoa học hoặc Tiếng Anh",
          optionId: "opt-1",
          required: 50,
          available: 51,
        })}
      />,
    );

    const text = await screen.findByText(/cần 50, hiện 51/);
    expect(text.textContent?.startsWith("Khoa học hoặc Tiếng Anh: số câu từng môn")).toBe(true);
    expect(text.textContent).not.toContain("Khoa học: Khoa học");
  });

  it("vấn đề của khối không nêu tên phần thì vẫn có tiền tố tên lựa chọn", async () => {
    renderWithProviders(
      <IssueWithPathLine issue={issueOf({ optionId: "opt-1", required: 8, available: 3 })} />,
    );

    const text = await screen.findByText(/cần 8 câu/);
    expect(text.textContent?.startsWith("Khoa học: ")).toBe(true);
  });
});
