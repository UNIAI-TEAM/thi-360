import { afterEach, describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api";
import { getGradingAttempt, getGradingFilterOptions, listGradingAttempts } from "@/features/grading/api";
import { isGradingMockEnabled, notWired } from "@/lib/mock/flags";
import { apiContentReference } from "@/test/fake-api";

const get = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ createBrowserApiClient: () => ({ GET: get }) }));

afterEach(() => {
  vi.unstubAllEnvs();
  get.mockReset();
});

const NOT_WIRED = "ch\u01b0a n\u1ed1i API";

describe("cờ dữ liệu mẫu của /grading (plan B7, QA UNI-2621)", () => {
  it("đọc cờ: true và false rõ ràng; bỏ trống thì chỉ bật khi chạy dev", () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", " TRUE ");
    expect(isGradingMockEnabled()).toBe(true);
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");
    expect(isGradingMockEnabled()).toBe(false);

    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "");
    vi.stubEnv("NODE_ENV", "development");
    expect(isGradingMockEnabled()).toBe(true);
    vi.stubEnv("NODE_ENV", "production");
    expect(isGradingMockEnabled()).toBe(false);
  });

  it("nhánh API thật chưa nối ném đúng thông báo", () => {
    expect(notWired).toThrow(NOT_WIRED);
  });

  it("GRADING_MOCK bật: /grading chạy trên kho câu hỏi mẫu; danh mục vẫn gọi API thật (F-02)", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "true");

    const page = await listGradingAttempts({});
    expect(page.totalElements).toBeGreaterThan(0);
    const options = await getGradingFilterOptions();
    expect(options.exams.length).toBeGreaterThan(0);
    const detail = await getGradingAttempt(page.content[0]!.id);
    expect(detail.essays.length).toBeGreaterThan(0);
    expect(detail.essays.every((essay) => essay.maxPoints > 0 && essay.rubric !== undefined)).toBe(true);

    // F-02: cờ chấm không còn đổi danh mục dùng chung sang dữ liệu mẫu.
    get.mockResolvedValueOnce({
      data: apiContentReference({ subjects: [{ code: "MATH", name: "Toán" }] }),
      response: { ok: true, status: 200 },
    });
    const reference = await getContentReference();
    expect(get).toHaveBeenCalledWith("/api/v1/admin/content-reference");
    expect(reference.subjects).toEqual([{ code: "MATH", name: "Toán" }]);
  });

  it("GRADING_MOCK tắt: /grading ném 'chưa nối API'; danh mục gọi API thật và đổi sang kiểu của web", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");

    await expect(listGradingAttempts({})).rejects.toThrow(NOT_WIRED);

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
