import type { ContentReference } from "@/features/content/types";
import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useContentReference } from "@/features/content/queries";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail, mapPart } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { useIssueText } from "../components/exam-review-shared";
import { getExamCheck, mapIssue } from "../review-api";
import type { CheckIssue, CheckIssueCode } from "../review-types";
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

/** Mã kiểm tra của phần bổ sung H-SCA, V-SAT (spec mục 6) và mã phân đoạn của BE-20. */
const NEW_CODES = [
  "AUDIO_SHORTAGE",
  "POINTS_SHORTAGE",
  "BLOCK_COUNT_LOCKED",
  "OMITTED_BLOCK_LOCKED",
  "SCALE_INCLUDES_EXCLUDED_PART",
  "DISPLAY_SEGMENT_MISMATCH",
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

function IssueLine({ issue, exam }: { issue: CheckIssue; exam?: Parameters<typeof useIssueText>[0] }) {
  const issueText = useIssueText(exam);
  // Câu chỉ hiện sau khi danh mục tải xong, để tên môn là tên theo ngôn ngữ, không phải mã.
  return useContentReference().isSuccess ? <p data-testid="line">{issueText(issue)}</p> : null;
}

async function textOf(issue: CheckIssue, language: "vi" | "en" = "vi", exam?: Parameters<typeof useIssueText>[0]) {
  const { unmount } = renderWithProviders(<IssueLine issue={issue} exam={exam} />, language === "en" ? { locale: "en", messages: en } : undefined);
  const text = (await screen.findByTestId("line")).textContent ?? "";
  unmount();
  return text;
}

/** Cấu trúc tối thiểu có `displaySegments` của phần (BE-20), để `useIssueText` tra tên phân đoạn theo `segmentCode`. */
const SEGMENT_EXAM = {
  parts: [{ id: "part-1", name: "Phần Tiếng Anh", displaySegments: [{ code: "P2", name: "Phần II" }] }],
} as unknown as Parameters<typeof useIssueText>[0];

describe("mã kiểm tra mới có câu chữ vi và en", () => {
  it("web biết cả năm mã, mỗi mã một lần, và giữ nguyên khi đọc từ máy chủ", async () => {
    expect(new Set(NEW_CODES).size).toBe(NEW_CODES.length);
    for (const code of NEW_CODES) expect(mapIssue({ code }).code).toBe(code);
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

  it("giữ segmentCode và chiều của DISPLAY_SEGMENT_MISMATCH, partId lấy theo location khi cần", async () => {
    new FakeServer()
      .on("GET", `${EXAM_PATH}/check`, {
        status: 200,
        body: {
          ...checkResultWire("INVALID"),
          paths: [
            {
              pathId: "default",
              result: "INVALID",
              issues: [
                { key: "s1", code: "DISPLAY_SEGMENT_MISMATCH", severity: "ERROR", group: "DISPLAY", dimension: "POINTS", segmentCode: "P2", required: 12.5, available: 10, location: { partId: "part-9" } },
              ],
            },
          ],
        },
      })
      .install();

    const issue = (await getExamCheck({ examId: EXAM_ID }))?.paths[0]?.issues[0];

    expect(issue).toMatchObject({ code: "DISPLAY_SEGMENT_MISMATCH", partId: "part-9", dimension: "POINTS", segmentCode: "P2", required: 12.5, available: 10 });
  });

  it("vi: nêu khối và số cần, số có; không rơi về INFO_MISSING", async () => {
    expect(await textOf(issueOf({ code: "AUDIO_SHORTAGE", required: 3, available: 2 }))).toBe(
      "Khối Toán · Dong 2: nguồn có audio chưa đủ cho dòng: cần 3, hiện ghép được 2.",
    );
    expect(await textOf(issueOf({ code: "POINTS_SHORTAGE", required: 5, available: 2 }))).toBe(
      "Khối Toán · Dong 2: nguồn đúng điểm mỗi câu của dòng chưa đủ: cần 5, hiện chỉ có 2 đúng điểm.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", group: "STRUCTURE", required: 20, available: 22 }))).toBe(
      "Khối Toán · Dong 2: mẫu khóa dạng và số lượng của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.",
    );
    expect(
      await textOf(
        issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "Phần Tiếng Anh", dimension: "COMMANDS", segmentCode: "P2", required: 4, available: 3 }),
        "vi",
        SEGMENT_EXAM,
      ),
    ).toBe("Phần Tiếng Anh: phân đoạn Phần II có 3 lệnh trong khi khai báo 4 lệnh.");
    expect(
      await textOf(
        issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "Phần Tiếng Anh", dimension: "POINTS", segmentCode: "P2", required: 12.5, available: 10 }),
        "vi",
        SEGMENT_EXAM,
      ),
    ).toBe("Phần Tiếng Anh: phân đoạn Phần II có 10 điểm trong khi khai báo 12,5 điểm.");
    expect(await textOf(issueOf({ code: "OMITTED_BLOCK_LOCKED", group: "STRUCTURE" }))).toBe(
      "Khối Toán · Dong 2: nhóm đã lược của mẫu chưa có trong V1 nên không bật lại được. Hãy tắt nhóm này.",
    );
    expect(
      await textOf(issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", group: "POINTS", blockName: null, partName: "Viet", required: 150, available: 120, excludedPoints: 30 })),
    ).toBe("Thang đang là 150, tổng điểm của bài là 120. Chênh 30 điểm, đúng bằng điểm của phần đã lược Viet. Đặt thang 120.");
  });

  it("SCALE_INCLUDES_EXCLUDED_PART: số điểm lược lấy từ excludedPoints, không tính thang trừ tổng điểm", async () => {
    const text = await textOf(
      issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", blockName: null, partName: "Viet", required: 150, available: 110, excludedPoints: 30 }),
    );

    expect(text).toBe("Thang đang là 150, tổng điểm của bài là 110. Chênh 30 điểm, đúng bằng điểm của phần đã lược Viet. Đặt thang 110.");
  });

  it("DISPLAY_SEGMENT_MISMATCH: không tra được tên phân đoạn thì nói chung, không in mã", async () => {
    const unknownSegment = issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "Phần Tiếng Anh", dimension: "COMMANDS", segmentCode: "P9", required: 4, available: 3 });

    expect(await textOf(unknownSegment, "vi", SEGMENT_EXAM)).toBe("Phần Tiếng Anh: một phân đoạn có 3 lệnh trong khi khai báo 4 lệnh.");
    expect(await textOf({ ...unknownSegment, segmentCode: null }, "vi", SEGMENT_EXAM)).toBe("Phần Tiếng Anh: một phân đoạn có 3 lệnh trong khi khai báo 4 lệnh.");
    expect(await textOf(unknownSegment)).toBe("Phần Tiếng Anh: một phân đoạn có 3 lệnh trong khi khai báo 4 lệnh.");
  });
  it("displaySegments của máy chủ qua mapPart: câu in tên phân đoạn, không phải câu chung", async () => {
    const part = mapPart(
      {
        id: "part-1",
        name: "Phần Tiếng Anh",
        participation: { mode: "ALL" },
        displaySegments: [{ code: "P2", name: "Phần II", fromPosition: 6, toPosition: 12, commandCount: 4, maxPoints: 12.5 }],
      },
      0,
    );

    const text = await textOf(
      issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "Phần Tiếng Anh", dimension: "COMMANDS", segmentCode: "P2", required: 4, available: 3 }),
      "vi",
      { parts: [part] },
    );

    expect(part.displaySegments?.[0]?.name).toBe("Phần II");
    expect(text).toBe("Phần Tiếng Anh: phân đoạn Phần II có 3 lệnh trong khi khai báo 4 lệnh.");
  });

  it("BLOCK_COUNT_LOCKED nêu đúng đại lượng bị khóa, kể cả khi máy chủ không trả số", async () => {
    const locked = (dimension: CheckIssue["dimension"], required: number | null = 20, available: number | null = 22) =>
      issueOf({ code: "BLOCK_COUNT_LOCKED", group: "STRUCTURE", dimension, required, available });

    expect(await textOf(locked("COUNT"))).toBe("Khối Toán · Dong 2: mẫu khóa số câu của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("SET_COUNT"))).toBe("Khối Toán · Dong 2: mẫu khóa số chùm của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("SET_SIZE"))).toBe("Khối Toán · Dong 2: mẫu khóa số câu mỗi chùm; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("TYPE"))).toBe("Khối Toán · Dong 2: mẫu khóa dạng của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("COUNT", null, null))).toBe("Khối Toán · Dong 2: mẫu khóa số câu của dòng. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("SET_COUNT", null, null))).toBe("Khối Toán · Dong 2: mẫu khóa số chùm của dòng. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("SET_SIZE", null, null))).toBe("Khối Toán · Dong 2: mẫu khóa số câu mỗi chùm. Đưa dòng về đúng như mẫu.");
    expect(await textOf(locked("TYPE", null, null))).toBe("Khối Toán · Dong 2: mẫu khóa dạng của dòng. Đưa dòng về đúng như mẫu.");
  });

  it("BLOCK_COUNT_LOCKED: chiều lạ (kể cả QUESTION_TYPE cũ và CHILD_TYPE tương lai) về câu chung, không in mã", async () => {
    const wireIssue = (dimension: string) => ({
      code: "BLOCK_COUNT_LOCKED",
      severity: "ERROR",
      group: "STRUCTURE",
      subjectCode: "TOAN",
      blockName: "Dong 2",
      dimension,
      required: 20,
      available: 22,
    });

    for (const dimension of ["CHILD_TYPE", "QUESTION_TYPE"]) {
      new FakeServer()
        .on("GET", `${EXAM_PATH}/check`, {
          status: 200,
          body: { ...checkResultWire("INVALID"), paths: [{ pathId: "default", result: "INVALID", issues: [wireIssue(dimension)] }] },
        })
        .install();

      const issue = (await getExamCheck({ examId: EXAM_ID }))?.paths[0]?.issues[0];

      expect(issue?.dimension).toBeNull();
      const vi = await textOf(issue as CheckIssue);
      const en = await textOf(issue as CheckIssue, "en");

      expect(vi).toBe("Khối Toán · Dong 2: mẫu khóa dạng và số lượng của dòng; mẫu giữ 20, hiện có 22. Đưa dòng về đúng như mẫu.");
      expect(en).toBe("Block Mathematics · Dong 2: the template locks the row's type and counts; the template keeps 20, the row has 22. Set the row back to the template.");
      for (const text of [vi, en]) expect(text).not.toContain(dimension);
    }
  });

  it("en: cùng nội dung bằng tiếng Anh", async () => {
    expect(await textOf(issueOf({ code: "AUDIO_SHORTAGE", required: 3, available: 2 }), "en")).toBe(
      "Block Mathematics · Dong 2: not enough source with audio for the row: needs 3, only 2 can be assembled.",
    );
    expect(await textOf(issueOf({ code: "POINTS_SHORTAGE", required: 5, available: 2 }), "en")).toBe(
      "Block Mathematics · Dong 2: not enough source with the row's points per question: needs 5, only 2 have the right points.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", required: 20, available: 22 }), "en")).toBe(
      "Block Mathematics · Dong 2: the template locks the row's type and counts; the template keeps 20, the row has 22. Set the row back to the template.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", dimension: "SET_COUNT", required: 20, available: 22 }), "en")).toBe(
      "Block Mathematics · Dong 2: the template locks the row's set count; the template keeps 20, the row has 22. Set the row back to the template.",
    );
    expect(await textOf(issueOf({ code: "BLOCK_COUNT_LOCKED", dimension: "TYPE" }), "en")).toBe(
      "Block Mathematics · Dong 2: the template locks the row's question type. Set the row back to the template.",
    );
    expect(
      await textOf(
        issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "English", dimension: "POINTS", segmentCode: "P2", required: 12.5, available: 10 }),
        "en",
        {
          parts: [{ id: "part-1", name: "English", displaySegments: [{ code: "P2", name: "Part II" }] }],
        } as unknown as Parameters<typeof useIssueText>[0],
      ),
    ).toBe("English: segment Part II has 10 points while 12.5 are declared.");
    expect(
      await textOf(
        issueOf({ code: "DISPLAY_SEGMENT_MISMATCH", group: "DISPLAY", blockName: null, subjectCode: null, partId: "part-1", partName: "English", dimension: "COMMANDS", segmentCode: "P2", required: 4, available: 3 }),
        "en",
        {
          parts: [{ id: "part-1", name: "English", displaySegments: [{ code: "P2", name: "Part II" }] }],
        } as unknown as Parameters<typeof useIssueText>[0],
      ),
    ).toBe("English: segment Part II has 3 commands while 4 are declared.");
    expect(await textOf(issueOf({ code: "OMITTED_BLOCK_LOCKED" }), "en")).toBe(
      "Block Mathematics · Dong 2: a group omitted by the template is not in V1 and cannot be turned back on. Turn this group off.",
    );
    expect(
      await textOf(issueOf({ code: "SCALE_INCLUDES_EXCLUDED_PART", blockName: null, partName: "Writing", required: 150, available: 120, excludedPoints: 30 }), "en"),
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
      wireIssue("SCALE_INCLUDES_EXCLUDED_PART", { group: "POINTS", partId: "part-1", partName: "Viet", subjectCode: undefined, required: 150, available: 120, excludedPoints: 30 }),
      wireIssue("DISPLAY_SEGMENT_MISMATCH", { group: "DISPLAY", partName: "Tiếng Anh", subjectCode: undefined, segmentCode: "P1", dimension: "COMMANDS", required: 4, available: 3, location: { partId: "part-1" } }),
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
    expect(errors.getByRole("link", { name: /nguồn có audio chưa đủ/ })).toHaveAttribute("href", `${structure}?block=blk-1&from=review`);
    expect(errors.getByRole("link", { name: /mẫu khóa/ })).toHaveAttribute("href", `${structure}?block=blk-1&from=review`);
    expect(errors.getByRole("link", { name: /Thang đang là 150/ })).toHaveAttribute("href", `${structure}?part=part-1&from=review`);
    expect(errors.getByRole("link", { name: /một phân đoạn có 3 lệnh/ })).toHaveAttribute("href", `${structure}?part=part-1&from=review`);
  });
});

describe("STORED_ID_COLLISION: id đã lưu trùng tên giữ chỗ của chùm trực tiếp (ADV:CR-4:21)", () => {
  it("web biết mã đúng một lần và giữ nguyên khi đọc từ máy chủ, id nằm ở blockId", async () => {
    expect(mapIssue({ code: "STORED_ID_COLLISION" }).code).toBe("STORED_ID_COLLISION");
    new FakeServer()
      .on("GET", `${EXAM_PATH}/check`, {
        status: 200,
        body: {
          ...checkResultWire("INVALID"),
          paths: [
            {
              pathId: "default",
              result: "INVALID",
              issues: [
                { code: "STORED_ID_COLLISION", severity: "ERROR", group: "STRUCTURE", blockId: "a-opt:blocks", location: { blockId: "a-opt:blocks" } },
              ],
            },
          ],
        },
      })
      .install();

    const issue = (await getExamCheck({ examId: EXAM_ID }))?.paths[0]?.issues[0];

    expect(issue).toMatchObject({ code: "STORED_ID_COLLISION", blockId: "a-opt:blocks", partId: null, sectionId: null, optionId: null });
  });

  it("câu chữ nêu id bị trùng và việc phải làm, bằng tiếng Việt và tiếng Anh", async () => {
    const collision = issueOf({ code: "STORED_ID_COLLISION", group: "STRUCTURE", subjectCode: null, blockName: null, blockId: "a-opt:blocks" });

    const vi = await textOf(collision);
    const en = await textOf(collision, "en");

    expect(vi).toContain('"a-opt:blocks"');
    expect(vi).toContain("đổi tên hoặc tạo lại mục");
    expect(en).toContain('"a-opt:blocks"');
    expect(en).toContain("Rename or recreate the item with this id");
  });
});
