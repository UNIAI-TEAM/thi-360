import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import en from "@/messages/en.json";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { issueQuotaHref } from "../check";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamStructureCheckResult } from "../components/exam-structure-check-result";
import { ExamStructureView } from "../components/exam-structure-view";
import { extractExamQuotaId, findExamQuotaForIssue } from "../exam-overview";
import type { CheckIssue, ExamCheckResult } from "../review-types";
import type { ExamPart, ExamQuota } from "../types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const CHECK_PATH = `${EXAM_PATH}/check`;

const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i;

function vietnameseOutsideExamContent(root: HTMLElement): string[] {
  const leaks: string[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const text = node.textContent ?? "";
    if (VIETNAMESE.test(text) && !node.parentElement?.closest("[data-exam-content]")) leaks.push(text.trim());
  }
  for (const element of root.querySelectorAll("[aria-label],[title],[placeholder],[alt]")) {
    if (element.closest("[data-exam-content]")) continue;
    for (const name of ["aria-label", "title", "placeholder", "alt"]) {
      const value = element.getAttribute(name);
      if (value && VIETNAMESE.test(value)) leaks.push(`${name}=${value}`);
    }
  }
  return leaks;
}

const SINGLE_QUOTA: ExamQuota = {
  id: "q-math-grade",
  name: "Lớp tham khảo Toán",
  dimension: "GRADE",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["MATH"],
  buckets: [
    { codes: ["GRADE_10", "GRADE_11"], percent: 30 },
    { codes: ["GRADE_12"], percent: 70 },
  ],
};

const MULTI_QUOTA: ExamQuota = {
  id: "q-sci-grade",
  name: "Khối lớp Khoa học",
  dimension: "GRADE",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["PHYSICS", "CHEMISTRY", "BIOLOGY"],
  buckets: [
    { codes: ["GRADE_10", "GRADE_11"], percent: 30 },
    { codes: ["GRADE_12"], percent: 70 },
  ],
};

const MULTI_DIFFICULTY_QUOTA: ExamQuota = {
  id: "q-sci-diff",
  name: "",
  dimension: "DIFFICULTY",
  basis: "QUESTION",
  enforcement: "REFERENCE",
  subjectCodes: ["PHYSICS", "CHEMISTRY"],
  buckets: [{ codes: ["EASY"], percent: 40 }],
};

function makeDeviationIssue(quotaId: string, overrides: Partial<CheckIssue> = {}): CheckIssue {
  return {
    key: `EXAM_QUOTA_DEVIATION|exam||GRADE_10+GRADE_11||GRADE||||${quotaId}|opt-sci:sec-phy=17sec-chem=17sec-bio=16`,
    code: "EXAM_QUOTA_DEVIATION",
    severity: "WARNING",
    group: "MATRIX",
    partId: null,
    partName: null,
    sectionId: null,
    subjectCode: null,
    blockId: null,
    blockName: null,
    optionId: null,
    gradeCode: "GRADE_10+GRADE_11",
    difficultyCode: null,
    dimension: "GRADE",
    competencyCode: null,
    answerType: null,
    subjectCount: null,
    required: 3,
    available: 5,
    timeLimitSeconds: null,
    questionCodes: null,
    ...overrides,
  };
}

const MULTI_PART: ExamPart = {
  id: "part-sci",
  order: 1,
  name: "Khoa học",
  durationMinutes: 60,
  questionCount: 50,
  questionCountOnPaper: 50,
  questionCountRequired: 50,
  participation: { mode: "ALL", choose: null },
  mode: "CHOICE",
  sections: [],
  options: [
    {
      id: "opt-sci",
      kind: "SUBJECT_CHOICE",
      code: "SCI",
      name: "Khoa học",
      chooseSubjects: 3,
      countsByPick: [17, 17, 16],
      sections: [
        { id: "sec-phy", subjectCode: "PHYSICS", questionCount: null, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: "NATURAL", blocks: [] },
        { id: "sec-chem", subjectCode: "CHEMISTRY", questionCount: null, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: "NATURAL", blocks: [] },
        { id: "sec-bio", subjectCode: "BIOLOGY", questionCount: null, knowledgeScope: null, answerQuotas: null, matrix: null, choiceGroup: "NATURAL", blocks: [] },
      ],
    },
  ],
};

const SUBJECT_NAMES_VI: Record<string, string> = {
  MATH: "Toán",
  PHYSICS: "Vật lý",
  CHEMISTRY: "Hóa học",
  BIOLOGY: "Sinh học",
};

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("extractExamQuotaId, findExamQuotaForIssue và issueQuotaHref", () => {
  it("tách đúng quota.id từ khóa chuẩn của backend, khóa có chữ ký tổ hợp và hậu tố @pathId", () => {
    expect(extractExamQuotaId(makeDeviationIssue("q-math-grade"))).toBe("q-math-grade");
    expect(
      extractExamQuotaId(
        makeDeviationIssue("q-sci-grade", {
          key: "EXAM_QUOTA_DEVIATION|exam||GRADE_10+GRADE_11||GRADE||||q-sci-grade@path-1",
        }),
      ),
    ).toBe("q-sci-grade");
    expect(
      extractExamQuotaId(
        makeDeviationIssue("q-short", {
          key: "EXAM_QUOTA_DEVIATION|q-short",
        }),
      ),
    ).toBe("q-short");
    expect(extractExamQuotaId({ code: "PATH_TOTAL_MISMATCH", key: "PATH_TOTAL_MISMATCH|exam|||||||" })).toBeNull();
  });

  it("khớp theo quota.id trước, rơi về chiều phân bổ khi khóa không mang id", () => {
    const quotas = [SINGLE_QUOTA, MULTI_DIFFICULTY_QUOTA];
    expect(findExamQuotaForIssue(makeDeviationIssue("q-math-grade"), quotas)?.id).toBe("q-math-grade");
    expect(
      findExamQuotaForIssue(
        makeDeviationIssue("unknown", { key: "k", dimension: "DIFFICULTY", gradeCode: null, difficultyCode: "EASY" }),
        quotas,
      )?.id,
    ).toBe("q-sci-diff");
  });

  it("issueQuotaHref tạo đường dẫn ?exam=1&quota=<id> cho EXAM_QUOTA_DEVIATION và trả null cho mã khác", () => {
    expect(issueQuotaHref("exam 1", makeDeviationIssue("q-sci-grade"))).toBe(
      "/exams/exam%201/structure?exam=1&quota=q-sci-grade",
    );
    expect(
      issueQuotaHref("exam-1", {
        code: "ITEM_SHAPE_SHORTAGE",
        key: "ITEM_SHAPE_SHORTAGE|blk-1|||||||",
        dimension: null,
        gradeCode: null,
        difficultyCode: null,
      }),
    ).toBeNull();
  });
});

describe("ExamStructureCheckResult với EXAM_QUOTA_DEVIATION", () => {
  it("chỉ tiêu đơn môn: hiện phạm vi, gợi ý đơn môn và bấm Đi tới chỉ tiêu mở Tổng quan bài + focus #exam-quota-<id>", async () => {
    const onSelect = vi.fn();
    const scrollSpy = vi.fn();
    const result = {
      groups: [{ group: "MATRIX", status: "WARN", issues: [makeDeviationIssue("q-math-grade")] }],
      paths: [],
    } as unknown as ExamCheckResult;

    renderWithProviders(
      <>
        <ExamStructureCheckResult
          result={result}
          names={{ subject: (code) => SUBJECT_NAMES_VI[code] ?? code }}
          parts={[FIXED_PART as unknown as ExamPart]}
          quotas={[SINGLE_QUOTA]}
          onSelect={onSelect}
        />
        <section id="exam-quota-q-math-grade" data-exam-quota-id="q-math-grade" tabIndex={-1}>
          <input type="radio" name="enf" value="REQUIRED" aria-label="Bắt buộc" />
        </section>
      </>,
    );

    const quotaSection = document.getElementById("exam-quota-q-math-grade")!;
    quotaSection.scrollIntoView = scrollSpy;

    const warnings = await screen.findByTestId("structure-check-warnings");
    expect(warnings).toHaveTextContent("Lớp tham khảo Toán (Toán)");
    expect(within(warnings).getByTestId("exam-quota-deviation-hint")).toHaveTextContent(
      /Bật «Áp dụng khi sinh đề».*Chỉ tiêu cấp bài/,
    );

    const goBtn = within(warnings).getByRole("button", { name: /^Đi tới chỉ tiêu/ });
    await userEvent.click(goBtn);

    expect(onSelect).toHaveBeenCalledWith({ kind: "exam" });
    await waitFor(() => expect(within(quotaSection).getByRole("radio", { name: "Bắt buộc" })).toHaveFocus());
    expect(scrollSpy).toHaveBeenCalledWith({ behavior: "smooth", block: "center" });
    expect(quotaSection).toHaveAttribute("data-quota-highlight");
  });

  it("chỉ tiêu gộp nhiều môn: hiện phạm vi các môn, gợi ý bảng 2A · Khối lớp / 2B · Độ khó, nút mở nhanh môn và Đi tới chỉ tiêu", async () => {
    const onSelect = vi.fn();
    const result = {
      groups: [
        {
          group: "MATRIX",
          status: "WARN",
          issues: [
            makeDeviationIssue("q-sci-grade"),
            makeDeviationIssue("q-sci-diff", {
              key: "EXAM_QUOTA_DEVIATION|exam|||EASY|DIFFICULTY||||q-sci-diff",
              dimension: "DIFFICULTY",
              gradeCode: null,
              difficultyCode: "EASY",
            }),
          ],
        },
      ],
      paths: [],
    } as unknown as ExamCheckResult;

    renderWithProviders(
      <ExamStructureCheckResult
        result={result}
        names={{ subject: (code) => SUBJECT_NAMES_VI[code] ?? code }}
        parts={[MULTI_PART]}
        quotas={[MULTI_QUOTA, MULTI_DIFFICULTY_QUOTA]}
        onSelect={onSelect}
      />,
    );

    const warnings = await screen.findByTestId("structure-check-warnings");
    expect(warnings).toHaveTextContent("Khối lớp Khoa học (Vật lý, Hóa học, Sinh học)");
    expect(warnings).toHaveTextContent("Khoa học (Vật lý, Hóa học)");

    const hints = within(warnings).getAllByTestId("exam-quota-deviation-hint");
    expect(hints[0]).toHaveTextContent("2A · Khối lớp");
    expect(hints[1]).toHaveTextContent("2B · Độ khó");

    await userEvent.click(within(hints[0]!).getByRole("button", { name: "Mở thiết lập môn Vật lý" }));
    expect(onSelect).toHaveBeenCalledWith({ kind: "section", sectionId: "sec-phy" });

    const goButtons = within(warnings).getAllByRole("button", { name: /^Đi tới chỉ tiêu/ });
    expect(goButtons).toHaveLength(2);
    await userEvent.click(goButtons[0]!);
    expect(onSelect).toHaveBeenLastCalledWith({ kind: "exam" });
  });

  it("bản en: tên chỉ tiêu tiếng Việt nằm trong data-exam-content, không lọt dấu tiếng Việt ra ngoài văn bản hay aria-label", async () => {
    const result = {
      groups: [{ group: "MATRIX", status: "WARN", issues: [makeDeviationIssue("q-sci-grade")] }],
      paths: [],
    } as unknown as ExamCheckResult;

    const { container } = renderWithProviders(
      <ExamStructureCheckResult
        result={result}
        names={{ subject: (code) => code }}
        parts={[MULTI_PART]}
        quotas={[MULTI_QUOTA]}
        onSelect={() => undefined}
      />,
      { locale: "en", messages: en },
    );

    await screen.findByRole("button", { name: /^Go to quota/ });
    expect(screen.getByTestId("exam-quota-deviation-hint")).toHaveTextContent("2A · Grade");
    expect(vietnameseOutsideExamContent(container)).toEqual([]);
  });
});

describe("Tích hợp ExamStructureView (Tab 2) và ExamReviewCheck (Tab 3)", () => {
  it("Tab 2: đang xem môn, chạy Kiểm tra toàn bài có EXAM_QUOTA_DEVIATION -> bấm Đi tới chỉ tiêu chuyển sang Tổng quan bài và focus thẻ chỉ tiêu", async () => {
    const checkBody = {
      ...checkResultWire("VALID"),
      issueCount: 1,
      groups: [{ group: "MATRIX", status: "WARN", issues: [makeDeviationIssue("q-math-grade")] }],
      paths: [{ pathId: "default", result: "VALID", issues: [] }],
    };
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "VACT", questionCount: 10 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE_PATH, {
        status: 200,
        body: { examId: EXAM_ID, revision: 1, parts: [FIXED_PART], examQuotas: [SINGLE_QUOTA] },
      })
      .on("POST", CHECK_PATH, { status: 200, body: checkBody })
      .install();

    const user = userEvent.setup();
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-1`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    await user.click(await screen.findByRole("button", { name: "Kiểm tra toàn bài" }));
    const goButton = await screen.findByRole("button", { name: /^Đi tới chỉ tiêu/ });
    await user.click(goButton);

    const quotaSection = await screen.findByRole("region", { name: "Lớp tham khảo Toán" });
    expect(quotaSection).toHaveAttribute("id", "exam-quota-q-math-grade");
    expect(quotaSection).toHaveAttribute("data-exam-quota-id", "q-math-grade");
    await waitFor(() => expect(within(quotaSection).getByRole("radio", { name: "Bắt buộc" })).toHaveFocus());
    expect(window.location.search).toBe("?exam=1");
  });

  it("Tab 2: mở trực tiếp đường dẫn ?exam=1&quota=<id> tự động cuộn và focus ô nhập của chỉ tiêu nhiều môn", async () => {
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "HSA", questionCount: 50 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", STRUCTURE_PATH, {
        status: 200,
        body: { examId: EXAM_ID, revision: 1, parts: [MULTI_PART], examQuotas: [MULTI_QUOTA] },
      })
      .install();

    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?exam=1&quota=q-sci-grade`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    const quotaSection = await screen.findByRole("region", { name: "Khối lớp Khoa học" });
    await waitFor(() => {
      const firstInput = within(quotaSection).getAllByRole("textbox")[0]!;
      expect(firstInput).toHaveFocus();
    });
    expect(quotaSection).toHaveAttribute("data-quota-highlight");
  });

  it("Tab 3 (ExamReviewCheck): hiện phạm vi, gợi ý và liên kết Đi tới chỉ tiêu trỏ tới /exams/<id>/structure?exam=1&quota=<id>", async () => {
    const checkBody = {
      ...checkResultWire("VALID"),
      issueCount: 1,
      groups: [{ group: "MATRIX", status: "WARN", issues: [makeDeviationIssue("q-sci-grade")] }],
      paths: [{ pathId: "default", result: "VALID", issues: [] }],
      listingCheck: { group: "LISTING", status: "PASS", issues: [] },
    };
    new FakeServer()
      .on("GET", CHECK_PATH, { status: 200, body: checkBody })
      .on("GET", STRUCTURE_PATH, {
        status: 200,
        body: { examId: EXAM_ID, revision: 1, parts: [MULTI_PART], examQuotas: [MULTI_QUOTA] },
      })
      .install();

    const { container } = renderWithProviders(
      <ExamReviewCheck exam={{ ...mapExamDetail(examDetailWire()), parts: [MULTI_PART] }} onPreview={() => undefined} active />,
      { locale: "en", messages: en },
    );

    const warnings = within(await screen.findByTestId("findings-warnings"));
    const link = await warnings.findByRole("link", { name: /^Go to quota/ });
    expect(link).toHaveAttribute("href", `/exams/${EXAM_ID}/structure?exam=1&quota=q-sci-grade`);
    await waitFor(() => expect(warnings.getByTestId("exam-quota-deviation-hint")).toHaveTextContent("2A · Grade"));
    expect(vietnameseOutsideExamContent(container)).toEqual([]);
  });
});
