import { describe, expect, it, vi } from "vitest";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import {
  claimGradingItem,
  fetchExamKindOptions,
  finalizeEssayGrade,
  getGradingItemCounts,
  getGradingItemDetail,
  GradingApiError,
  listGradingItems,
  reopenEssayGrade,
  resolveGradingErrorMessage,
  saveEssayDraft,
} from "../api";
import {
  EXAM_KIND_CATALOG_FIXTURES,
  ITEM_1_ID,
  makeCounts,
  makeDetail,
  makeEssayGrade,
  makeQueueItem,
} from "./grading-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

describe("grading real API client", () => {
  it("listGradingItems sends query params to /api/v1/admin/grading/items", async () => {
    const page = { items: [makeQueueItem()], page: 1, size: 10, totalItems: 11, totalPages: 2 };
    fakeApi.get("/api/v1/admin/grading/items", ok(page));

    const result = await listGradingItems({
      state: "WAITING",
      exam: "  BCA Literature  ",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
      page: 1,
      size: 10,
    });

    expect(result).toEqual(page);
    expect(fakeApi.calls).toHaveLength(1);
    expect(fakeApi.calls[0]?.path).toBe("/api/v1/admin/grading/items");
    expect(fakeApi.calls[0]?.options?.params).toEqual({
      query: {
        state: "WAITING",
        exam: "BCA Literature",
        kind: "BCA",
        from: "2026-11-01",
        to: "2026-11-20",
        page: 1,
        size: 10,
      },
    });
  });

  it("getGradingItemCounts calls /api/v1/admin/grading/items/counts without state param", async () => {
    const counts = makeCounts();
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(counts));

    const result = await getGradingItemCounts({
      exam: "  BCA ",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
    });

    expect(result).toEqual(counts);
    const query = (fakeApi.calls[0]?.options?.params as { query?: Record<string, unknown> })?.query;
    expect(query).toEqual({
      exam: "BCA",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
    });
    expect(query).not.toHaveProperty("state");
  });

  it("getGradingItemDetail fetches /api/v1/admin/grading/items/{itemId}", async () => {
    const detail = makeDetail();
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", ok(detail));

    const result = await getGradingItemDetail(ITEM_1_ID);
    expect(result).toEqual(detail);
    expect(fakeApi.calls[0]?.options?.params).toEqual({ path: { itemId: ITEM_1_ID } });
  });

  it("claimGradingItem posts claim and maps 409 GRADING_CLAIMED with claimedByName", async () => {
    const grade = makeEssayGrade();
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", ok(grade));
    expect(await claimGradingItem(ITEM_1_ID)).toEqual(grade);

    fakeApi.post("/api/v1/admin/grading/items/{itemId}/claim", {
      data: undefined,
      error: {
        code: "GRADING_CLAIMED",
        message: "Claimed",
        fieldErrors: [],
        traceId: "t1",
        claimedById: "grader-2",
        claimedByName: "Nguyen Van B",
        claimedAt: "2026-11-20T03:00:00Z",
        lastActivityAt: "2026-11-20T03:05:00Z",
      },
      response: new Response(null, { status: 409 }),
    });

    const err = await claimGradingItem(ITEM_1_ID).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(GradingApiError);
    expect((err as GradingApiError).status).toBe(409);
    expect((err as GradingApiError).code).toBe("GRADING_CLAIMED");
    expect((err as GradingApiError).claimedByName).toBe("Nguyen Van B");

    const tErrors = vi.fn((key: string, values?: Record<string, string | number>) =>
      `${key}:${values?.name ?? ""}`,
    );
    expect(resolveGradingErrorMessage(err, tErrors)).toBe("GRADING_CLAIMED_BY:Nguyen Van B");
  });

  it("saveEssayDraft puts draft with expectedVersion and surfaces 412 VERSION_CONFLICT", async () => {
    const updated = makeEssayGrade({ version: 2, points: 7.5 });
    fakeApi.put("/api/v1/admin/grading/items/{itemId}", ok(updated));

    const body = {
      expectedVersion: 1,
      points: 7.5,
      criteria: [{ id: "c1", points: 3.5 }, { id: "c2", points: 4 }],
      comment: "Good draft",
    };
    expect(await saveEssayDraft(ITEM_1_ID, body)).toEqual(updated);
    expect(fakeApi.calls[0]?.options?.body).toEqual(body);

    fakeApi.put(
      "/api/v1/admin/grading/items/{itemId}",
      fail(412, { code: "VERSION_CONFLICT", message: "Stale version" }),
    );
    const err = await saveEssayDraft(ITEM_1_ID, body).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(GradingApiError);
    expect((err as GradingApiError).status).toBe(412);
    expect((err as GradingApiError).code).toBe("VERSION_CONFLICT");
  });

  it("finalizeEssayGrade posts finalize and maps 422 business rule codes", async () => {
    const response = {
      attemptId: "att-1",
      attemptState: "SCORED" as const,
      displayScore: 80,
      rawScore: 80,
      maxScore: 100,
      gradedAt: "2026-11-20T03:20:00Z",
      grade: makeEssayGrade({ state: "FINAL", points: 8, version: 2 }),
    };
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", ok(response));

    const result = await finalizeEssayGrade(ITEM_1_ID, {
      expectedVersion: 1,
      points: 8,
      criteria: [{ id: "c1", points: 3 }, { id: "c2", points: 5 }],
      comment: "Well done",
    });
    expect(result).toEqual(response);

    for (const code of [
      "ESSAY_POINTS_OUT_OF_RANGE",
      "ESSAY_CRITERIA_INVALID",
      "ESSAY_CRITERIA_SUM_MISMATCH",
    ]) {
      fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", fail(422, { code }));
      const err = await finalizeEssayGrade(ITEM_1_ID, { points: 8 }).catch((e: unknown) => e);
      expect(err).toBeInstanceOf(GradingApiError);
      expect((err as GradingApiError).code).toBe(code);
    }
  });

  it("reopenEssayGrade posts reopen reason to /reopen", async () => {
    const reopened = makeEssayGrade({ state: "DRAFT", version: 1, reopenedReason: "Recheck rubric" });
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/reopen", ok(reopened));

    const result = await reopenEssayGrade(ITEM_1_ID, { reason: "Recheck rubric" });
    expect(result).toEqual(reopened);
    expect(fakeApi.calls[0]?.options?.body).toEqual({ reason: "Recheck rubric" });
  });

  it("fetchExamKindOptions reads /api/v1/catalog/options?group=EXAM", async () => {
    fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));

    const kinds = await fetchExamKindOptions();
    expect(fakeApi.calls[0]?.options?.params).toEqual({ query: { group: "EXAM" } });
    expect(kinds).toEqual([
      {
        id: "k-bca",
        code: "BCA",
        nameVi: "Danh gia tuyen sinh Cong an (BCA)",
        nameEn: "Public Security Admission (BCA)",
      },
      {
        id: "k-spt",
        code: "SPT",
        nameVi: "Danh gia nang luc DH Su pham (SPT)",
      },
    ]);
  });
});
