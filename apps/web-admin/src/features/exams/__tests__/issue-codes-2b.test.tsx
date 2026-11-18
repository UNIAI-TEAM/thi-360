import type { components } from "@thi360/api-client";
import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useContentReference } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { useIssueText } from "../components/exam-review-shared";
import { mapBlock } from "../api-mapping";
import { getExamCheck } from "../review-api";
import { CHECK_ISSUE_CODES, type CheckIssue, type CheckIssueCode } from "../review-types";
import type { ExamDetail } from "../types";
import { checkResultWire, EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const getContentReference = (locale: string = "vi") =>
    Promise.resolve({
      subjects: [
        { code: "TOAN", name: locale === "en" ? "Mathematics" : "Toán" },
        { code: "HOA", name: locale === "en" ? "Chemistry" : "Hóa học" },
      ],
      grades: [{ code: "G12", name: locale === "en" ? "Grade 12" : "Lớp 12" }],
      difficulties: [{ code: "VDC", name: "VDC" }],
      tags: [],
      competencies: [],
    } as unknown as ContentReference);
  return { ...(await importOriginal()), getContentReference, defaultContentApiDeps: { getContentReference } };
});

/** Mã của đợt 2B mà web phải có câu riêng (FU-FE2-2) cộng hai mã máy chủ phát thêm (SLOT_SOURCE_SHORTAGE, OMITTED_GROUPS_PRESENT). */
const CODES_2B = [
  "ATTACHMENT_MISSING",
  "COMBINATION_SUBJECT_MISSING",
  "QUESTION_NOT_PLAYABLE",
  "REQUIRED_BASIS_UNSUPPORTED",
  "SECTION_INCOMPLETE",
  "SUBJECT_CHOICE_TRIM_IMPOSSIBLE",
  "TOPIC_QUOTA_INFEASIBLE",
  "TOPIC_QUOTA_NESTED",
  "ITEM_SHAPE_SHORTAGE",
  "ANSWER_FORMAT_SHORTAGE",
  "CHILD_CONDITION_SHORTAGE",
  "SLOT_SOURCE_SHORTAGE",
  "DISTRIBUTION_DEVIATION",
  "EXAM_QUOTA_DEVIATION",
  "MANUAL_GRADING_REQUIRED",
  "OMITTED_GROUPS_PRESENT",
  "COMBINATION_LIST_CHANGED",
] as const satisfies readonly CheckIssueCode[];

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "ITEM_SHAPE_SHORTAGE",
    severity: "ERROR",
    group: "SOURCE",
    partId: null,
    partName: "Phần 1",
    sectionId: null,
    subjectCode: "TOAN",
    blockId: null,
    blockName: "Nhóm 5",
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

function IssueLine({ issue, exam }: { issue: CheckIssue; exam?: Pick<ExamDetail, "parts"> }) {
  const issueText = useIssueText(exam);
  const loaded = useContentReference().isSuccess;
  return loaded ? <p data-testid="line">{issueText(issue)}</p> : null;
}

async function textOf(issue: CheckIssue, options?: { exam?: Pick<ExamDetail, "parts">; en?: boolean }) {
  renderWithProviders(
    <IssueLine issue={issue} exam={options?.exam} />,
    options?.en ? { locale: "en", messages: en } : undefined,
  );
  // Câu chỉ hiện sau khi danh mục tải xong, để tên môn là tên theo ngôn ngữ, không phải mã.
  return (await screen.findByTestId("line")).textContent ?? "";
}

/**
 * Cấu trúc tối thiểu có một khối dựng từ JSON của máy chủ qua `mapBlock` của FE-3a, như màn thật: kiểm tra
 * `itemShape`, `answerFormats`, `childConditions` đi từ API tới câu vấn đề.
 */
function examWithBlock(wire: Partial<components["schemas"]["QuestionBlock"]>): Pick<ExamDetail, "parts"> {
  const block = mapBlock({ id: "blk-5", name: "Nhóm 5", questionType: "MATCHING", ...wire });
  return {
    parts: [
      {
        id: "part-1",
        name: "Phần 1",
        sections: [{ id: "sec-1", subjectCode: "TOAN", blocks: [block] }],
        options: [],
      },
    ],
  } as unknown as Pick<ExamDetail, "parts">;
}

describe("danh sách mã vấn đề của đợt 2B", () => {
  it("chứa đủ mười bảy mã máy chủ phát, mỗi mã một lần", () => {
    for (const code of CODES_2B) expect(CHECK_ISSUE_CODES.filter((item) => item === code)).toHaveLength(1);
  });

  it("mỗi mã có câu riêng ở vi và en, khác câu INFO_MISSING và không còn ký tự ICU thô", () => {
    const messages = { vi: viMessages.exams.review.issue as Record<string, string>, en: en.exams.review.issue as Record<string, string> };
    for (const code of CODES_2B) {
      for (const language of ["vi", "en"] as const) {
        const text = messages[language][code];
        expect(text, `${language} ${code}`).toBeTruthy();
        expect(text).not.toBe(messages[language].INFO_MISSING);
      }
    }
  });
});

describe("web nhận mã vấn đề mới từ máy chủ, không rơi về INFO_MISSING", () => {
  it("mọi mã của đợt 2B giữ nguyên mã, vị trí chùm và mã bị ảnh hưởng", async () => {
    const issues = CODES_2B.map((code) => ({
      code,
      severity: "ERROR",
      group: "SOURCE",
      location: { slotIndex: 4, childPosition: 2 },
      affectedCodes: ["QD01", "QD02"],
    }));
    new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body: { ...checkResultWire("INVALID"), paths: [{ pathId: "default", result: "INVALID", issues }] } }).install();

    const result = await getExamCheck({ examId: EXAM_ID });

    const mapped = result?.paths[0]?.issues ?? [];
    expect(mapped.map((issue) => issue.code)).toEqual([...CODES_2B]);
    expect(mapped.every((issue) => issue.slotIndex === 4 && issue.childPosition === 2)).toBe(true);
    expect(mapped[0]?.affectedCodes).toEqual(["QD01", "QD02"]);
  });
});

describe("câu mô tả từng mã của đợt 2B (vi)", () => {
  it.each(CODES_2B)("%s hiện câu riêng, không rơi về INFO_MISSING và không có ký tự ICU thô", async (code) => {
    const text = await textOf(
      issueOf({ code, required: 5, available: 2, questionCodes: ["Q-1"], slotIndex: 4, gradeCode: "G12", dimension: "GRADE" }),
    );

    expect(text).not.toBe(viMessages.exams.review.issue.INFO_MISSING);
    expect(text).not.toMatch(/[{}]|undefined|NaN/);
    expect(text.length).toBeGreaterThan(10);
  });

  it("ITEM_SHAPE_SHORTAGE dựng câu từ số cần, số có và hình dạng của khối (ghép đôi 4 ý trái – 6 lựa chọn phải)", async () => {
    const text = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", blockId: "blk-5", blockName: null, required: 5, available: 3 }), {
      exam: examWithBlock({ itemShape: { leftItems: 4, rightItems: 6 } }),
    });

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 3 câu ghép đôi đủ cấu trúc 4 ý trái – 6 lựa chọn phải, khối cần 5 câu.");
  });

  it("ITEM_SHAPE_SHORTAGE khối Đúng/Sai nêu số ý; khối chưa khai hình dạng thì nêu chung", async () => {
    const statements = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", blockId: "blk-5", required: 5, available: 3 }), {
      exam: examWithBlock({ itemShape: { statements: 4 } }),
    });
    expect(statements).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 3 câu có đủ 4 ý, khối cần 5 câu.");
  });

  it("ITEM_SHAPE_SHORTAGE không biết cấu hình khối thì vẫn nêu số cần và số có", async () => {
    const text = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", required: 5, available: 3 }));

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 3 câu đúng hình dạng yêu cầu, khối cần 5 câu.");
  });

  it("ANSWER_FORMAT_SHORTAGE nêu định dạng đáp án của khối theo tên hiển thị", async () => {
    const text = await textOf(issueOf({ code: "ANSWER_FORMAT_SHORTAGE", blockId: "blk-5", required: 6, available: 2 }), {
      exam: examWithBlock({ answerFormats: ["INTEGER", "DECIMAL"] }),
    });

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 2 câu có định dạng đáp án Số nguyên, Số thập phân, khối cần 6 câu.");
  });

  it("CHILD_CONDITION_SHORTAGE nêu vị trí câu con của điều kiện", async () => {
    const text = await textOf(issueOf({ code: "CHILD_CONDITION_SHORTAGE", blockId: "blk-5", required: 3, available: 1 }), {
      exam: examWithBlock({ childConditions: [{ from: 1, to: 2 }, { from: 5, to: 5 }] }),
    });

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 1 chùm có câu con đúng chủ đề ở vị trí 1–2, 5, khối cần 3 chùm.");
  });

  it("khối đã nạp từ API nhưng không khai điều kiện lọc: mỗi mã trong ba mã dùng câu chung, không in cụm rỗng", async () => {
    const exam = examWithBlock({});
    const matching = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", blockId: "blk-5", required: 5, available: 3 }), { exam });
    expect(matching).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 3 câu đúng hình dạng yêu cầu, khối cần 5 câu.");
    document.body.innerHTML = "";
    const format = await textOf(issueOf({ code: "ANSWER_FORMAT_SHORTAGE", blockId: "blk-5", required: 6, available: 2 }), { exam });
    expect(format).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 2 câu đúng định dạng đáp án yêu cầu, khối cần 6 câu.");
  });

  it("số câu thập phân của câu hình dạng đi qua display-number (dấu phẩy ở vi)", async () => {
    const text = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", blockId: "blk-5", required: 5, available: 2.5 }), {
      exam: examWithBlock({ itemShape: { leftItems: 4, rightItems: 6 } }),
    });

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chỉ có 2,5 câu ghép đôi đủ cấu trúc 4 ý trái – 6 lựa chọn phải, khối cần 5 câu.");
  });

  it("ba mã hình dạng, định dạng đáp án, câu con đọc khối đã ánh xạ ở bản en", async () => {
    const shape = await textOf(issueOf({ code: "ITEM_SHAPE_SHORTAGE", blockId: "blk-5", required: 5, available: 3 }), {
      en: true,
      exam: examWithBlock({ itemShape: { leftItems: 4, rightItems: 6 } }),
    });
    expect(shape).toBe(
      "Block Mathematics · Nhóm 5: the bank has only 3 questions with a matching structure of 4 left items – 6 right options; the block needs 5.",
    );
    document.body.innerHTML = "";
    const format = await textOf(issueOf({ code: "ANSWER_FORMAT_SHORTAGE", blockId: "blk-5", required: 6, available: 2 }), {
      en: true,
      exam: examWithBlock({ answerFormats: ["INTEGER", "FRACTION"] }),
    });
    expect(format).toBe(
      "Block Mathematics · Nhóm 5: the bank has only 2 questions with answer format Integer, Fraction; the block needs 6.",
    );
    document.body.innerHTML = "";
    const child = await textOf(issueOf({ code: "CHILD_CONDITION_SHORTAGE", blockId: "blk-5", required: 3, available: 1 }), {
      en: true,
      exam: examWithBlock({ childConditions: [{ from: 2, to: 3, nodeIds: ["n1"] }] }),
    });
    expect(child).toBe(
      "Block Mathematics · Nhóm 5: the bank has only 1 sets whose child question matches the topic at position 2–3; the block needs 3.",
    );
  });

  it("máy chủ không trả số cần hoặc số có thì không in 'cần 0 câu, hiện có 0 câu'", async () => {
    const text = await textOf(issueOf({ code: "ANSWER_FORMAT_SHORTAGE" }));

    expect(text).toBe("Khối Toán · Nhóm 5: ngân hàng chưa đủ câu đúng định dạng đáp án yêu cầu.");
    expect(text).not.toMatch(/\b0 câu/);
  });

  it("SLOT_SOURCE_SHORTAGE nêu nhóm và chùm (vị trí từ 1)", async () => {
    const text = await textOf(issueOf({ code: "SLOT_SOURCE_SHORTAGE", slotIndex: 4 }));

    expect(text).toBe("Khối Toán · Nhóm 5, chùm 4: chưa đủ nguồn cho điều kiện riêng của chùm.");
  });

  it("TOPIC_QUOTA_INFEASIBLE nêu chỉ tiêu và số còn thiếu, số thập phân theo dấu phẩy", async () => {
    const text = await textOf(issueOf({ code: "TOPIC_QUOTA_INFEASIBLE", required: 5, available: 2.5 }));

    expect(text).toBe("Toán · Nhóm 5: chỉ tiêu chủ đề cần ít nhất 5 câu, nguồn hiện chỉ có 2,5 câu, còn thiếu 2,5 câu.");
  });

  it("EXAM_QUOTA_DEVIATION nối tên nhiều khối lớp của một chỉ tiêu cấp bài", async () => {
    const text = await textOf(
      issueOf({ code: "EXAM_QUOTA_DEVIATION", dimension: "GRADE", gradeCode: "G12+G11", required: 20, available: 18.5 }),
    );

    expect(text).toBe("Chỉ tiêu cấp bài tham khảo Lớp 12 + G11 lệch: tham khảo 20, thực tế 18,5.");
  });

  it("DISTRIBUTION_DEVIATION là cảnh báo nêu ô bị lệch", async () => {
    const text = await textOf(
      issueOf({ code: "DISTRIBUTION_DEVIATION", severity: "WARNING", dimension: "DIFFICULTY", difficultyCode: "VDC", required: 4, available: 6 }),
    );

    expect(text).toBe("Toán · Nhóm 5: phân bố tham khảo VDC lệch chỉ tiêu: tham khảo 4, thực tế 6.");
  });

  it("QUESTION_NOT_PLAYABLE và ATTACHMENT_MISSING nêu mã câu nguồn", async () => {
    const playable = await textOf(issueOf({ code: "QUESTION_NOT_PLAYABLE", questionCodes: ["HOA-17"] }));
    expect(playable).toContain("câu HOA-17 thiếu đáp án");
  });

  it("bản en có câu riêng cho mã mới", async () => {
    const text = await textOf(issueOf({ code: "SLOT_SOURCE_SHORTAGE", slotIndex: 4, required: 5, available: 2 }), { en: true });

    expect(text).toBe("Block Mathematics · Nhóm 5, set 4: not enough source for the set's own conditions (needs 5, has 2).");
  });
});
