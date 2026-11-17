import { describe, expect, it } from "vitest";
import {
  applyQuestionFiltersPatch,
  countAdvancedFilters,
  DEFAULT_QUESTION_FILTERS,
  hasActiveQuestionFilters,
  parseQuestionFilters,
  serializeQuestionFilters,
  toListQuestionsQuery,
} from "../components/question-filters-state";

describe("bo loc Ngan hang cau hoi tren URL", () => {
  it("doc URL, bo gia tri khong hop le", () => {
    const filters = parseQuestionFilters(
      "?q=lim&status=DRAFT&subject=MATH&type=BAD&knowledge=kn-1D4-2,kn-1D5&solution=has&createdFrom=2026-11-01&createdTo=x&page=3&size=7&sort=points,asc",
    );
    expect(filters).toMatchObject({
      q: "lim",
      status: "DRAFT",
      subject: "MATH",
      type: null,
      knowledge: ["kn-1D4-2", "kn-1D5"],
      solution: "has",
      createdFrom: "2026-11-01",
      createdTo: null,
      page: 3,
      size: 20,
      sort: "points,asc",
    });
    expect(parseQuestionFilters("?sort=contentPreview,asc").sort).toBeNull();
  });

  it("ghi URL chi gom gia tri khac mac dinh va giu tham so khac", () => {
    const query = serializeQuestionFilters(
      { ...DEFAULT_QUESTION_FILTERS, status: "READY", knowledge: ["a", "b"], page: 2 },
      "?tab=notes&status=DRAFT",
    );
    expect(new URLSearchParams(query).toString()).toBe("tab=notes&status=READY&knowledge=a%2Cb&page=2");
    expect(serializeQuestionFilters(DEFAULT_QUESTION_FILTERS)).toBe("");
  });

  it("doi bo loc thi ve trang 1, doi trang thi giu", () => {
    const current = { ...DEFAULT_QUESTION_FILTERS, page: 4 };
    expect(applyQuestionFiltersPatch(current, { subject: "MATH" }).page).toBe(1);
    expect(applyQuestionFiltersPatch(current, { page: 5 }).page).toBe(5);
  });

  it("chuyen sang tham so API (trang tinh tu 0, mac dinh moi cap nhat truoc)", () => {
    const filters = { ...DEFAULT_QUESTION_FILTERS, author: "u-lan", solution: "none" as const, page: 2 };
    expect(toListQuestionsQuery(filters)).toEqual({
      createdById: "u-lan",
      hasSolution: false,
      page: 1,
      size: 20,
      sort: "updatedAt,desc",
    });
  });

  it("dem bo loc khac va nhan biet co dang loc", () => {
    expect(hasActiveQuestionFilters(DEFAULT_QUESTION_FILTERS)).toBe(false);
    const filters = { ...DEFAULT_QUESTION_FILTERS, knowledge: ["a", "b"], tag: "TRAP", createdFrom: "2026-11-01" };
    expect(countAdvancedFilters(filters)).toBe(3);
    expect(hasActiveQuestionFilters(filters)).toBe(true);
  });

  it("Dang bai di qua URL, tham so API va bo dem Bo loc khac", () => {
    const filters = parseQuestionFilters("?taskType=CALCULATION&createdTo=2026-11-30");
    expect(filters.taskType).toBe("CALCULATION");
    expect(toListQuestionsQuery(filters)).toMatchObject({ taskTypeCode: "CALCULATION", createdTo: "2026-11-30" });
    expect(countAdvancedFilters(filters)).toBe(2);
    expect(new URLSearchParams(serializeQuestionFilters(filters)).get("taskType")).toBe("CALCULATION");
  });
});
