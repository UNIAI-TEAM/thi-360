import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { useContentReference } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { issueRuleHref } from "../check";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamStructureCheckResult } from "../components/exam-structure-check-result";
import { PreviewIssueItem } from "../components/exam-preview-panel";
import { useIssueText } from "../components/exam-review-shared";
import { getExamCheck } from "../review-api";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamDetail, ExamPart, ExamSection, MapidRule, QuestionBlock } from "../types";
import { checkResultWire, EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";
import { examDetailWire } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const node = (id: string, code: string, name: string) => ({
    id,
    code,
    name,
    level: "KNOWLEDGE",
    subjectCode: "TOAN",
    gradeCode: "G12",
    active: true,
    children: [],
  });
  const getContentReference = (locale: string = "vi") =>
    Promise.resolve({
      subjects: [{ code: "TOAN", name: locale === "en" ? "Mathematics" : "Toán" }],
      grades: [],
      difficulties: [],
      tags: [],
      competencies: [],
      knowledgeTree: [
        node("n-hs", "M12-HS", locale === "en" ? "Functions" : "Hàm số"),
        node("n-xs", "M12-XS", locale === "en" ? "Probability" : "Xác suất"),
      ],
    } as unknown as ContentReference);
  return { ...(await importOriginal()), getContentReference, defaultContentApiDeps: { getContentReference } };
});

const block = (over: Partial<QuestionBlock>): QuestionBlock => ({
  id: "short",
  name: "Trả lời ngắn",
  questionType: "SHORT_ANSWER",
  questionCount: 15,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: null,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

const RULES: MapidRule[] = [
  { id: "r1", type: "PRESENT", groupId: null, nodeIds: ["n-hs"] },
  { id: "r2", type: "COUNT", groupId: "short", nodeIds: ["n-hs"], comparator: "AT_LEAST", count: 3 },
  { id: "r3", type: "PRESENT", groupId: "gone", nodeIds: ["n-xs"] },
];

const section: ExamSection = {
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 50,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block({}), block({ id: "set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 })],
  mapidMode: "MATRIX",
  mapidRules: RULES,
};

const part = { id: "p1", name: "Phần 1", order: 1, sections: [section], options: [] } as unknown as ExamPart;
const exam = { parts: [part] } as Pick<ExamDetail, "parts">;

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "MAPID_RULE_CONFLICT",
    severity: "ERROR",
    group: "MATRIX",
    partId: "p1",
    partName: "Phần 1",
    sectionId: "s1",
    subjectCode: "TOAN",
    blockId: null,
    blockName: null,
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

function IssueLine({ issue, withExam }: { issue: CheckIssue; withExam: boolean }) {
  const issueText = useIssueText(withExam ? exam : undefined);
  const loaded = useContentReference().isSuccess;
  return loaded ? <p data-testid="line">{issueText(issue)}</p> : null;
}

async function textOf(issue: CheckIssue, options: { en?: boolean; withExam?: boolean } = {}) {
  renderWithProviders(<IssueLine issue={issue} withExam={options.withExam ?? true} />, options.en ? { locale: "en", messages: en } : undefined);
  return (await screen.findByTestId("line")).textContent ?? "";
}

const R1 = "Toàn môn · Phải có Hàm số";
const R2 = "Trả lời ngắn · Ít nhất 3/15 câu thuộc Hàm số";

describe("MAPID_RULE_CONFLICT nêu cả hai quy tắc bằng câu mô tả của chúng", () => {
  it.each([
    ["GROUP_MINIMUM_ABOVE_GROUP_UNITS", ["r2"], `Quy tắc «${R2}» cần ít nhất 20 câu nhưng nhóm chỉ có 15 câu.`],
    ["SUBJECT_EXACT_BELOW_GROUP_MINIMUM", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: quy tắc toàn môn chỉ cho đúng 15 câu, quy tắc của nhóm cần ít nhất 20 câu.`],
    ["GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: quy tắc toàn môn chỉ cho đúng 15 câu, các nhóm cần tổng cộng 20 câu.`],
    ["EXACT_COUNTS_DIFFER", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: cùng phạm vi nhưng một quy tắc đòi đúng 15 câu, quy tắc kia đòi đúng 20 câu.`],
    ["EXACT_BELOW_MINIMUM", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: cùng phạm vi nhưng một quy tắc chỉ cho đúng 15 câu, quy tắc kia cần ít nhất 20 câu.`],
    ["ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP_RULE", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: MAPID chỉ được xuất hiện ở một nhóm nên nhóm còn lại không thể có ít nhất 20 câu thuộc MAPID đó.`],
    // UNI-2824: nguồn toàn môn là biên ngoài; MAPID chỉ ở một nhóm giới hạn số toàn môn.
    ["GROUP_RULE_OUTSIDE_SUBJECT_SOURCE", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: MAPID của quy tắc nhóm nằm ngoài nguồn của toàn môn.`],
    ["SUBJECT_MINIMUM_ABOVE_ONLY_GROUP_UNITS", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột: MAPID chỉ được xuất hiện ở một nhóm có 15 câu, nhưng toàn môn cần ít nhất 20 câu thuộc MAPID đó.`],
    ["KIND_FROM_THE_FUTURE", ["r1", "r2"], `Quy tắc «${R1}» và «${R2}» xung đột, không thể cùng đúng.`],
  ])("vi %s", async (conflictKind, ruleIds, expected) => {
    const text = await textOf(issueOf({ ruleId: ruleIds[0]!, ruleIds, conflictKind, required: 20, available: 15 }));

    expect(text).toBe(expected);
  });

  it("en: hai quy tắc nối bằng 'and', câu theo loại xung đột", async () => {
    const text = await textOf(
      issueOf({ ruleId: "r1", ruleIds: ["r1", "r2"], conflictKind: "SUBJECT_EXACT_BELOW_GROUP_MINIMUM", required: 3, available: 2 }),
      { en: true },
    );

    expect(text).toContain("Rules «Whole subject · Must include Functions» and «");
    expect(text).toContain("the whole-subject rule allows exactly 2 questions while the group rule needs at least 3.");
  });
});

describe("MAPID_RULE_GROUP_MISSING", () => {
  it("vi: nêu quy tắc trỏ tới nhóm đã xóa", async () => {
    const text = await textOf(issueOf({ code: "MAPID_RULE_GROUP_MISSING", ruleId: "r3" }));

    expect(text).toBe("Quy tắc «Nhóm đã xóa · Phải có Xác suất» trỏ tới nhóm không còn. Chọn lại phạm vi áp dụng hoặc xóa quy tắc.");
  });

  it("en", async () => {
    const text = await textOf(issueOf({ code: "MAPID_RULE_GROUP_MISSING", ruleId: "r3" }), { en: true });

    expect(text).toContain("points to a group that no longer exists");
  });
});

describe("thiếu nguồn của quy tắc", () => {
  const shortage = issueOf({
    code: "TOPIC_QUOTA_INFEASIBLE",
    group: "SOURCE",
    ruleId: "r1",
    nodeIds: ["n-hs", "n-xs"],
    required: 3,
    available: 1,
  });

  it("vi: nêu quy tắc bằng câu, số thiếu và mã MAPID của quy tắc", async () => {
    expect(await textOf(shortage)).toBe(`Quy tắc «${R1}» thiếu nguồn: cần 3 câu, hiện có 1 câu, còn thiếu 2 câu (MAPID M12-HS, M12-XS).`);
  });

  it("vi: chỉ tiêu cấp môn (giới hạn trên) có ruleId cũng nêu quy tắc", async () => {
    const text = await textOf({ ...shortage, code: "SUBJECT_QUOTA_INFEASIBLE", ruleId: "r2", required: null, available: null });

    expect(text).toBe(`Quy tắc «${R2}» thiếu nguồn (MAPID M12-HS, M12-XS).`);
  });

  it("en", async () => {
    const text = await textOf(shortage, { en: true });

    expect(text).toContain("lacks source: it needs 3 questions, 1 are available, 2 short (MAPID M12-HS, M12-XS).");
  });

  it("chưa có cấu trúc thì gọi quy tắc bằng mã của nó", async () => {
    expect(await textOf(shortage, { withExam: false })).toBe(
      "Quy tắc «r1» thiếu nguồn: cần 3 câu, hiện có 1 câu, còn thiếu 2 câu (MAPID M12-HS, M12-XS).",
    );
  });

  it("vấn đề không có ruleId giữ câu cũ", async () => {
    const text = await textOf(issueOf({ code: "TOPIC_QUOTA_INFEASIBLE", group: "SOURCE", blockId: "short", required: 5, available: 2 }));

    expect(text).toBe("Toán · Trả lời ngắn: chỉ tiêu chủ đề cần ít nhất 5 câu, nguồn hiện chỉ có 2 câu, còn thiếu 3 câu.");
  });
});

describe("thiếu chùm của quy tắc nguồn", () => {
  const setShortage = issueOf({
    code: "SET_SHORTAGE",
    group: "SOURCE",
    blockId: "set",
    required: 1,
    available: 0,
    requiredSets: 1,
    availableSets: 0,
    availableSingles: 6,
  });

  it("vi: đúng câu của BA", async () => {
    expect(await textOf(setShortage)).toBe(
      "Chùm cuối cần 1 chùm có 3 câu con cùng chủ đề. Hiện có 6 câu đơn và 0 chùm phù hợp.",
    );
  });

  it("en", async () => {
    expect(await textOf(setShortage, { en: true })).toBe(
      "Chùm cuối needs 1 set with 3 sub-questions on the same topic. There are 6 single questions and 0 matching sets available.",
    );
  });

  it("không có số chùm thì giữ câu SET_SHORTAGE cũ", async () => {
    const text = await textOf({ ...setShortage, requiredSets: null, availableSets: null, availableSingles: null, required: 1, available: 0 });

    expect(text).toContain("yêu cầu 1 bộ");
  });
});

describe("web đọc các trường mới của máy chủ", () => {
  it("ruleId, ruleIds, conflictKind, nodeIds, số chùm và câu đơn đi từ JSON tới vấn đề; vấn đề khác không có trường thừa", async () => {
    const issues = [
      {
        code: "MAPID_RULE_CONFLICT",
        severity: "ERROR",
        group: "MATRIX",
        sectionId: "s1",
        ruleId: "r1",
        ruleIds: ["r1", "r2"],
        conflictKind: "EXACT_COUNTS_DIFFER",
        required: 3,
        available: 2,
      },
      { code: "SET_SHORTAGE", severity: "ERROR", group: "SOURCE", blockId: "set", requiredSets: 1, availableSets: 0, availableSingles: 6 },
      { code: "TOPIC_QUOTA_INFEASIBLE", severity: "ERROR", group: "SOURCE", ruleId: "r1", nodeIds: ["n-hs"] },
      { code: "ITEM_SHAPE_SHORTAGE", severity: "ERROR", group: "SOURCE" },
    ];
    new FakeServer()
      .on("GET", `${EXAM_PATH}/check`, { status: 200, body: { ...checkResultWire("INVALID"), paths: [{ pathId: "default", result: "INVALID", issues }] } })
      .install();

    const mapped = (await getExamCheck({ examId: EXAM_ID }))?.paths[0]?.issues ?? [];

    expect(mapped[0]).toMatchObject({ ruleId: "r1", ruleIds: ["r1", "r2"], conflictKind: "EXACT_COUNTS_DIFFER", required: 3, available: 2 });
    expect(mapped[1]).toMatchObject({ requiredSets: 1, availableSets: 0, availableSingles: 6 });
    expect(mapped[2]).toMatchObject({ ruleId: "r1", nodeIds: ["n-hs"] });
    expect(Object.keys(mapped[3]!)).not.toContain("ruleId");
    expect(mapped.map((issue) => issue.code)).toEqual(["MAPID_RULE_CONFLICT", "SET_SHORTAGE", "TOPIC_QUOTA_INFEASIBLE", "ITEM_SHAPE_SHORTAGE"]);
  });
});

describe("Đi tới quy tắc", () => {
  it("liên kết của trang Kiểm tra mở môn và truyền ruleId; không có quy tắc hoặc môn thì không có liên kết", () => {
    expect(issueRuleHref("e 1", { ruleId: "r1", sectionId: "s1" })).toBe("/exams/e%201/structure?section=s1&rule=r1");
    expect(issueRuleHref("e1", { ruleId: null, sectionId: "s1" })).toBeNull();
    expect(issueRuleHref("e1", { ruleId: "r1", sectionId: null })).toBeNull();
  });

  const result = {
    groups: [
      {
        group: "MATRIX",
        issues: [
          issueOf({ key: "conflict", ruleId: "r1", ruleIds: ["r1", "r2"], conflictKind: "EXACT_COUNTS_DIFFER", required: 3, available: 2 }),
          issueOf({ key: "other", code: "ITEM_SHAPE_SHORTAGE", group: "SOURCE", blockId: "short", sectionId: "s1" }),
        ],
      },
    ],
    paths: [],
  } as unknown as ExamCheckResult;

  it("nút mở môn rồi đưa tiêu điểm vào dòng quy tắc; vấn đề không nêu quy tắc vẫn là 'Đến chỗ sửa'", async () => {
    const onSelect = vi.fn();
    renderWithProviders(
      <>
        <ExamStructureCheckResult result={result} names={{ subject: () => "Toán" }} parts={[part]} onSelect={onSelect} />
        <div data-field="mapid-rule-r1" tabIndex={-1} data-testid="row-r1" />
      </>,
    );

    const go = await screen.findByRole("button", { name: /Đi tới quy tắc/ });
    await waitFor(() => expect(screen.getByTestId("structure-check-errors").querySelector("[data-exam-content]")).toHaveTextContent("Trả lời ngắn"));
    await userEvent.click(go);

    expect(onSelect).toHaveBeenCalledWith({ kind: "section", sectionId: "s1" });
    await waitFor(() => expect(screen.getByTestId("row-r1")).toHaveFocus());
    expect(screen.getByTestId("row-r1")).toHaveAttribute("data-mapid-highlight");
    expect(screen.getByTestId("row-r1")).toHaveAttribute("data-mapid-focus");
    await userEvent.click(screen.getByRole("button", { name: "Đến vị trí cần sửa" }));
    expect(onSelect).toHaveBeenLastCalledWith({ kind: "block", blockId: "short" });
    expect(screen.getAllByRole("button", { name: /Đi tới quy tắc/ })).toHaveLength(1);
  });
});

describe("lỗi sinh thử nêu quy tắc", () => {
  it("dòng có ruleId và môn có liên kết Đi tới quy tắc về trang Cấu trúc; dòng khác không có", () => {
    const failure = issueOf({ code: "SUBJECT_QUOTA_INFEASIBLE", group: "SOURCE", ruleId: "r2", nodeIds: ["n-hs"] });
    renderWithProviders(
      <ul>
        <PreviewIssueItem examId="ex-1" issue={failure} text="Quy tắc A thiếu nguồn" />
        <PreviewIssueItem examId="ex-1" issue={issueOf({ code: "ITEM_SHAPE_SHORTAGE" })} text="Khối B thiếu" />
      </ul>,
    );

    const link = screen.getByRole("link", { name: "Đi tới quy tắc: Quy tắc A thiếu nguồn" });
    expect(link).toHaveAttribute("href", "/exams/ex-1/structure?section=s1&rule=r2&from=review");
    expect(screen.getAllByRole("link")).toHaveLength(1);
  });
});

describe("review summary", () => {
  it("resolves conflict rule names using the exam structure", async () => {
    const conflict = {
      code: "MAPID_RULE_CONFLICT", severity: "ERROR", group: "MATRIX", sectionId: "s1",
      ruleId: "r1", ruleIds: ["r1", "r2"], conflictKind: "EXACT_COUNTS_DIFFER", required: 3, available: 2,
    };
    new FakeServer().on("GET", `${EXAM_PATH}/check`, {
      status: 200,
      body: { ...checkResultWire("INVALID"), groups: [{ group: "MATRIX", status: "FAIL", issues: [conflict] }], paths: [{ pathId: "default", result: "INVALID", issues: [conflict] }] },
    }).install();
    renderWithProviders(<ExamReviewCheck exam={{ ...mapExamDetail(examDetailWire()), parts: [part] }} onPreview={() => {}} />);
    const heading = await screen.findByRole("heading", { name: "Các nhóm kiểm tra" });
    await waitFor(() => expect(heading.parentElement).toHaveTextContent(R2));
    expect(heading.parentElement).toHaveTextContent(R1);
  });
});
