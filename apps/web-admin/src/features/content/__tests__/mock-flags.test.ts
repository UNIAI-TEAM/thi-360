import { afterEach, describe, expect, it, vi } from "vitest";
import { getContentReference } from "@/features/content/api";
import { listExams } from "@/features/exams/api";
import { getGradingAttempt, getGradingFilterOptions, listGradingAttempts } from "@/features/grading/api";
import { listQuestions } from "@/features/questions/api";
import { isContentMockEnabled, isGradingMockEnabled, notWired } from "@/lib/mock/flags";

afterEach(() => {
  vi.unstubAllEnvs();
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

    await expect(listQuestions({})).rejects.toThrow(NOT_WIRED);
    await expect(listExams({})).rejects.toThrow(NOT_WIRED);

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

  it("cả hai cờ tắt: danh mục dùng chung cũng chưa nối", async () => {
    vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
    vi.stubEnv("NEXT_PUBLIC_THI360_GRADING_MOCK", "false");
    await expect(getContentReference()).rejects.toThrow(NOT_WIRED);
  });
});
