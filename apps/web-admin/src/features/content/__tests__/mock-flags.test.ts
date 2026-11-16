import { afterEach, describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api";
import { listExams } from "@/features/exams/api";
import { getGradingAttempt, getGradingFilterOptions, listGradingAttempts } from "@/features/grading/api";
import { listQuestions } from "@/features/questions/api";
import { isContentMockEnabled, isGradingMockEnabled, notWired } from "@/lib/mock/flags";

const get = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ createBrowserApiClient: () => ({ GET: get }) }));

afterEach(() => {
  vi.unstubAllEnvs();
  get.mockReset();
});

const NOT_WIRED = "ch\u01b0a n\u1ed1i API";

describe("cờ dữ liệu mẫu của đợt 2 (plan B7)", () => {
  it("đọc từng cờ: true và false rõ ràng; bỏ trống thì chỉ bật khi chạy dev", () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", " TRUE ");
    expect(isContentMockEnabled()).toBe(false);
    expect(isGradingMockEnabled()).toBe(true);

    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "");
    vi.stubEnv("NODE_ENV", "development");
    expect(isContentMockEnabled()).toBe(true);
    vi.stubEnv("NODE_ENV", "production");
    expect(isContentMockEnabled()).toBe(false);
  });

  it("nhánh API thật chưa nối ném đúng thông báo", () => {
    expect(notWired).toThrow(NOT_WIRED);
  });

  it("CONTENT_MOCK tắt, GRADING_MOCK bật: /grading vẫn chạy trên dữ liệu mẫu, PBI-03 và PBI-04 không còn dữ liệu mẫu", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "true");

    get.mockResolvedValueOnce({ data: undefined, error: { code: "UNAVAILABLE" }, response: { ok: false, status: 503 } });
    await expect(listQuestions({})).rejects.toMatchObject({ status: 503, code: "UNAVAILABLE" });
    // Bài thi đã nối API (UNI-2620): cờ tắt thì gọi máy chủ, không còn NOT_WIRED và không đọc dữ liệu mẫu.
    get.mockResolvedValueOnce({
      data: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 },
      error: undefined,
      response: { ok: true, status: 200 },
    });
    await expect(listExams({})).resolves.toMatchObject({ content: [], totalElements: 0 });
    expect(get).toHaveBeenCalledWith("/api/v1/admin/exams", expect.anything());

    // Danh sách chờ chấm, bộ lọc và màn chấm đọc đề từ kho câu hỏi mẫu (`questionStore`) nên vẫn chạy.
    const page = await listGradingAttempts({});
    expect(page.totalElements).toBeGreaterThan(0);
    const options = await getGradingFilterOptions();
    expect(options.exams.length).toBeGreaterThan(0);
    const detail = await getGradingAttempt(page.content[0]!.id);
    expect(detail.essays.length).toBeGreaterThan(0);
    expect(detail.essays.every((essay) => essay.maxPoints > 0 && essay.rubric !== undefined)).toBe(true);

    // Danh mục dùng chung của màn chấm (bộ lọc môn) vẫn đọc được khi cờ chấm bật.
    expect((await getContentReference()).subjects.length).toBeGreaterThan(0);
  });

  it("GRADING_MOCK tắt: /grading không còn dữ liệu mẫu dù CONTENT_MOCK bật", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "true");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");

    await expect(listGradingAttempts({})).rejects.toThrow(NOT_WIRED);
    expect((await listQuestions({})).totalElements).toBeGreaterThan(0);
    expect((await listExams({})).totalElements).toBeGreaterThan(0);
  });

  it("cả hai cờ tắt: danh mục dùng chung gọi API thật và đổi sang kiểu của web", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");
    get.mockResolvedValueOnce({
      data: {
        subjects: [{ code: "MATH", name: "Mathematics" }],
        grades: [],
        difficulties: [],
        tags: [],
        taskTypes: [],
        questionTypes: [{ code: "ESSAY", name: "Essay", active: true }],
        examKinds: [
          {
            code: "HSA",
            itemId: "kind-1",
            name: "HSA",
            practiceScoring: "RAW",
            profileSet: true,
            scopes: ["FULL", "PART"],
            templateBy: "KIND",
            timing: "PER_PART",
            maxScore: 150,
            trueFalseScoring: { method: "COUNT_TABLE", table: [0.1, 0.25, 0.5, 1] },
            multipleChoiceRule: "PARTIAL",
          },
        ],
        combinations: [],
        competencies: [{ code: "C1", name: "Competency", examKindCodes: ["VACT"] }],
        knowledgeTree: [{ id: "n1", code: "TOAN", name: "Mathematics", level: "SUBJECT", active: true, children: [] }],
      },
      response: { ok: true, status: 200 },
    });

    const reference = await getContentReference();

    expect(get).toHaveBeenCalledWith("/api/v1/admin/content-reference");
    expect(reference.subjects).toEqual([{ code: "MATH", name: "Mathematics" }]);
    expect(reference.examKinds[0]).toMatchObject({
      scopes: ["FULL", "PART"],
      maxScore: 150,
      referenceMax: null,
      scoringDefaults: {
        trueFalse: { method: "COUNT_TABLE", table: [0.1, 0.25, 0.5, 1], statementPoints: null },
        multipleChoiceRule: "PARTIAL",
      },
    });
    expect(reference.competencies[0]).toMatchObject({ subjectCode: null });
    expect(reference.knowledgeTree[0]).toMatchObject({ subjectCode: "", gradeCode: null });
  });
});
