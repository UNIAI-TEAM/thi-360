import type { ContentReference } from "@/features/content/types";
import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useContentReference } from "@/features/content/queries";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { useIssueText } from "../components/exam-review-shared";
import { getExamCheck } from "../review-api";
import { CHECK_ISSUE_CODES, type CheckIssue, type CheckIssueCode } from "../review-types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

// Nhà máy của `vi.mock` được nâng lên đầu tệp nên không dùng được import tĩnh cho dữ liệu mẫu (cùng cách các test khác).
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const getContentReference = (locale: string = "vi") =>
    Promise.resolve({
      subjects: [{ code: "TOAN", name: locale === "en" ? "Mathematics" : "Toán" }],
      grades: [],
      difficulties: [],
      tags: [],
      competencies: [],
    } as unknown as ContentReference);
  return { ...(await importOriginal()), getContentReference, defaultContentApiDeps: { getContentReference } };
});

/** Năm mã kiểm tra của phần bổ sung H-SCA, V-SAT (spec mục 6). */
const NEW_CODES = [
  "AUDIO_SHORTAGE",
  "POINTS_SHORTAGE",
  "BLOCK_COUNT_LOCKED",
  "OMITTED_BLOCK_LOCKED",
  "SCALE_INCLUDES_EXCLUDED_PART",
] as const satisfies readonly CheckIssueCode[];

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "AUDIO_SHORTAGE",
    severity: "ERROR",
    group: "SOURCE",
    partId: null,
    partName: null,
    sectionId: null,
    subjectCode: "TOAN",
    blockId: null,
    blockName: "Dong 2",
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
  };
}

function IssueLine({ issue }: { issue: CheckIssue }) {
  const issueText = useIssueText();
  // Câu chỉ hiện sau khi danh mục tải xong, để tên môn là tên theo ngôn ngữ, không phải mã.
  return useContentReference().isSuccess ? <p data-testid="line">{issueText(issue)}</p> : null;
}

async function textOf(issue: CheckIssue, language: "vi" | "en" = "vi") {
  const { unmount } = renderWithProviders(<IssueLine issue={issue} />, language === "en" ? { locale: "en", messages: en } : undefined);
  const text = (await screen.findByTestId("line")).textContent ?? "";
  unmount();
  return text;
}

describe("năm mã kiểm tra mới có câu chữ vi và en", () => {
  it("web biết cả năm mã, mỗi mã một lần, và giữ nguyên khi đọc từ máy chủ", async () => {
    for (const code of NEW_CODES) expect(CHECK_ISSUE_CODES.filter((item) => item === code)).toHaveLength(1);
    const issues = NEW_CODES.map((code) => ({ code, severity: "ERROR", group: "SOURCE" }));
    new FakeServer()
      .on("GET", `${EXAM_PATH}/check`, {
        status: 200,
        body: { ...checkResultWire("INVALID"), paths: [{ pathId: "default", result: "INVALID", issues }] },
      })
      .install();

    const result = await getExamCheck({ examId: EXAM_ID });

    expect(result?.paths[0]?.issues.map((issue) => issue.code)).toEqual([...NEW_CODES]);
  });

  it("vi: nêu khối và số cần, số có; không rơi về INFO_MISSING", async () => {
    expect(await textOf(issueOf({ code: "AUDIO_SHORTAGE", required: 3, available: 2 }))).toBe(
      "Khối Toán · Dong 2: nguồn có audio chưa đủ cho dòng: cần 3, hiện ghép được 2.",
    );
    expect(await textOf(issueOf({ code: "POINTS_SHORTAGE", required: 5, available: 2 }))).toBe(
      "Khối Toán · Dong 2: nguồn đúng điểm mỗi câu của dòng chưa đủ: cần 5, hiện chỉ có 2 đúng điểm.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", group: "STRUCTURE", required: 20, available: 22 }))).toBe(
      "Khối Toán · Dong 2: mẫu đã khóa dạng, số câu và số chùm của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.",
    );
    expect(await textOf(issueOf({ code: "OMITTED_BLOCK_LOCKED", group: "STRUCTURE" }))).toBe(
      "Khối Toán · Dong 2: nhóm đã lược của mẫu chưa có trong V1 nên không bật lại được. Hãy tắt nhóm này.",
    );
    expect(
      await textOf(issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", group: "POINTS", blockName: null, partName: "Viet", required: 150, available: 120 })),
    ).toBe("Thang đang là 150, tổng điểm của bài là 120. Chênh 30 điểm, đúng bằng điểm của phần đã lược Viet. Đặt thang 120.");
  });

  it("en: cùng nội dung bằng tiếng Anh", async () => {
    expect(await textOf(issueOf({ code: "AUDIO_SHORTAGE", required: 3, available: 2 }), "en")).toBe(
      "Block Mathematics · Dong 2: not enough source with audio for the row: needs 3, only 2 can be assembled.",
    );
    expect(await textOf(issueOf({ code: "POINTS_SHORTAGE", required: 5, available: 2 }), "en")).toBe(
      "Block Mathematics · Dong 2: not enough source with the row's points per question: needs 5, only 2 have the right points.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", required: 20, available: 22 }), "en")).toBe(
      "Block Mathematics · Dong 2: the template locks the type, question count and set count of the row; the template keeps 20, the row has 22. Set the row back to the template.",
    );
    expect(await textOf(issueOf({ code: "OMITTED_BLOCK_LOCKED" }), "en")).toBe(
      "Block Mathematics · Dong 2: a group omitted by the template is not in V1 and cannot be turned back on. Turn this group off.",
    );
    expect(
      await textOf(issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", blockName: null, partName: "Writing", required: 150, available: 120 }), "en"),
    ).toBe(
      "The scale is 150, the total points of the exam are 120. The 30-point difference equals the points of the omitted part Writing. Set the scale to 120.",
    );
  });

  it("máy chủ không trả số cần và số có thì không in số 0", async () => {
    const audio = await textOf(issueOf({ code: "AUDIO_SHORTAGE" }));
    const scale = await textOf(issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", partName: "Viet" }));

    expect(audio).toBe("Khối Toán · Dong 2: nguồn có audio chưa đủ cho dòng.");
    expect(scale).toBe("Thang điểm đang tính cả điểm của phần đã lược Viet.");
  });

  it("mỗi mã có câu riêng ở cả hai ngôn ngữ", () => {
    const messages = { vi: viMessages.exams.review.issue as Record<string, string>, en: en.exams.review.issue as Record<string, string> };
    for (const code of NEW_CODES) {
      for (const language of ["vi", "en"] as const) {
        expect(messages[language][code], `${language} ${code}`).toBeTruthy();
        expect(messages[language][code]).not.toBe(messages[language].INFO_MISSING);
      }
    }
  });
});

describe("bấm vào lỗi của mã mới dẫn tới đúng dòng ở bước Cấu trúc", () => {
  it("lỗi theo nhóm mở nhóm đó, lỗi theo phần mở phần đó", async () => {
    const wireIssue = (code: string, extra: Record<string, unknown>) => ({
      key: code,
      code,
      severity: "ERROR",
      group: "STRUCTURE",
      subjectCode: "MATH",
      affectedCodes: [],
      ...extra,
    });
    const issues = [
      wireIssue("AUDIO_SHORTAGE", { group: "SOURCE", partId: "part-1", sectionId: "sec-1", blockId: "blk-1", blockName: "Choice", required: 3, available: 2 }),
      wireIssue("BLOCK_COUNT_LOCKED", { partId: "part-1", sectionId: "sec-1", blockId: "blk-1", blockName: "Choice", required: 20, available: 22 }),
      wireIssue("SCALE_INCLUDES_EXCLUDED_PART", { group: "POINTS", partId: "part-1", partName: "Viet", subjectCode: undefined, required: 150, available: 120 }),
    ];
    const body = {
      ...checkResultWire("INVALID"),
      issueCount: issues.length,
      groups: [{ group: "STRUCTURE", status: "FAIL", issues }],
      paths: [{ pathId: "default", result: "INVALID", issues: [] }],
    };
    new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body }).install();
    renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);

    const errors = within(await screen.findByTestId("findings-errors"));

    expect(await errors.findByText(/nguồn có audio chưa đủ cho dòng: cần 3, hiện ghép được 2/)).toBeInTheDocument();
    const structure = `/exams/${EXAM_ID}/structure`;
    expect(errors.getByRole("link", { name: /nguồn có audio chưa đủ/ })).toHaveAttribute("href", `${structure}?block=blk-1`);
    expect(errors.getByRole("link", { name: /mẫu đã khóa dạng/ })).toHaveAttribute("href", `${structure}?block=blk-1`);
    expect(errors.getByRole("link", { name: /Thang đang là 150/ })).toHaveAttribute("href", `${structure}?part=part-1`);
  });
});
